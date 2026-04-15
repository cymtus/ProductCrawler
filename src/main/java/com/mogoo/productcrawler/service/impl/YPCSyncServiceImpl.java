package com.mogoo.productcrawler.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogoo.productcrawler.component.YPCAuthenticator;
import com.mogoo.productcrawler.config.YPCConfig;
import com.mogoo.productcrawler.dao.YPCSkuMapper;
import com.mogoo.productcrawler.dao.YPCSkuPriceHistoryMapper;
import com.mogoo.productcrawler.domain.entity.YPCSkuPriceHistory;
import com.mogoo.productcrawler.domain.model.vo.ChangeType;
import com.mogoo.productcrawler.domain.model.vo.SkuChangeMsg;
import com.mogoo.productcrawler.domain.entity.YPCSkuInfo;
import com.mogoo.productcrawler.domain.model.SyncStatus;
import com.mogoo.productcrawler.domain.model.dto.PageResult;
import com.mogoo.productcrawler.domain.model.dto.YPCSkuInfoDTO;
import com.mogoo.productcrawler.service.YPCSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class YPCSyncServiceImpl implements YPCSyncService {

    private final YPCSkuMapper skuMapper;
    private final YPCSkuPriceHistoryMapper skuHistoryMapper;
    private final ObjectMapper objectMapper;
    private final YPCConfig ypcConfig;
    private final YPCAuthenticator authenticator;

    private volatile SyncStatus currentStatus = new SyncStatus(false, 0, 0, "Idle", 0);
    private static final String API_URL_PREFIX = "https://bshop.guanmai.cn/product/sku/get?level=1&type=1&category_id=";

    @PostConstruct
    public void init() {
        try {
            Long lastTime = skuMapper.getLastSyncTime();
            if (lastTime != null) {
                currentStatus = new SyncStatus(false, 0, 0, "Idle", lastTime);
            }
        } catch (Exception e) {
            log.warn("无法获取最近一次同步时间: {}", e.getMessage());
        }
    }

    @Override
    public SyncStatus getCurrentStatus() { return currentStatus; }

    @Override
    public PageResult<YPCSkuInfo> searchSkusPaged(String keyword, Integer status, String categoryId, Integer isProcessed, int page, int size) {
        int offset = (page - 1) * size;
        long total = skuMapper.countSkus(keyword, status, categoryId, isProcessed);
        if (total == 0) return new PageResult<>(0, List.of());
        List<YPCSkuInfo> records = skuMapper.searchSkusPaged(keyword, status, categoryId, isProcessed, offset, size);
        return new PageResult<>(total, records);
    }

    @Override
    public void markTaskProcessed(String skuId) {
        skuMapper.markAsProcessed(skuId);
    }

    @Override
    public Map<String, String> getAllCategories() {
        // 直接返回配置中的分类字典 (ID -> 中文名)
        return ypcConfig.getCategories();
    }

    @Override
    public List<YPCSkuPriceHistory> getSkuHistory(String skuId) {
        return skuHistoryMapper.findHistoryBySkuId(skuId);
    }

    @Override
    public void syncAllCategories() {
        var categories = ypcConfig.getCategories();
        if (categories == null || categories.isEmpty()) return;

        long currentBatchId = System.currentTimeMillis();
        int total = categories.size();
        currentStatus = new SyncStatus(true, total, 0, "任务启动", currentBatchId);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            categories.forEach((id, name) -> executor.submit(() -> {
                try {
                    String json = fetchWithRetry(id, name);
                    if (json != null) {
                        processAndSave(json, id, currentBatchId);
                    }
                    updateProgress(name);
                } catch (Exception e) {
                    log.error("分类 {} 同步异常: {}", name, e.getMessage());
                }
            }));
        }

        // 移除旧的物理删除逻辑，改为在 processAndSave 中基于状态更新
        // skuMapper.deleteObsoleteData(categories.keySet().stream().toList(), currentBatchId);
        currentStatus = new SyncStatus(false, total, total, "同步完成", currentStatus.startTime());
    }

    private String fetchWithRetry(String categoryId, String categoryName) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            String cookie = authenticator.getCookie();

            try (var client = createUnsafeClient()) {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL_PREFIX + categoryId))
                        .header("Cookie", cookie)
                        .header("User-Agent", ypcConfig.getUserAgent())
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                int statusCode = response.statusCode();

                boolean needRefresh = false;

                if (statusCode == 401 || statusCode == 403) {
                    needRefresh = true;
                } else if (statusCode == 200 && body != null) {
                    JsonNode node = objectMapper.readTree(body);
                    int bizCode = node.path("code").asInt();
                    JsonNode dataNode = node.path("data");

                    // 【核心判断】：判定 200 OK 里的伪成功数据
                    if (bizCode != 0 || !dataNode.isArray() || dataNode.isEmpty()) {
                        log.warn("分类 {} 响应无效 (code={}, data为空={})，准备触发 Cookie 刷新",
                                categoryName, bizCode, dataNode.isEmpty());
                        needRefresh = true;
                    }
                }

                if (needRefresh) {
                    authenticator.refresh(cookie);
                    continue; // 刷新后尝试第二次请求
                }

                if (statusCode == 200) return body;
            }
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void processAndSave(String jsonResponse, String categoryId, long batchId) throws JsonProcessingException {
        var result = objectMapper.readValue(jsonResponse, YPCSkuInfoDTO.class);
        if (result.getCode() != 0 || result.getData() == null) return;

        // 解析新抓取的数据
        var newFetchedSkus = result.getData().stream()
                .filter(spu -> spu.getSkus() != null)
                .flatMap(spu -> spu.getSkus().stream().map(sku -> YPCSkuInfo.builder()
                        .id(sku.getId())
                        .spuId(spu.getId())
                        .name(sku.getName())
                        .stdSalePrice(BigDecimal.valueOf(sku.getStdSalePrice())
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP))
                        .stocks(sku.getStocks())
                        .saleUnitName(sku.getSaleUnitName())
                        .categoryId1(sku.getCategoryId1())
                        .categoryId2(sku.getCategoryId2())
                        .rawJson(sku.getOtherFields())
                        .lastSyncTime(batchId)
                        .status(1) // 默认抓取到的都在售
                        .build()))
                .toList();

        // 1. 从数据库查出该分类下所有当前【在售】的旧商品，转为 Map 加速查找
        Map<String, YPCSkuInfo> oldSkuMap = skuMapper.findActiveSkusByCategoryId(categoryId).stream()
                .collect(Collectors.toMap(YPCSkuInfo::getId, sku -> sku));

        var skusToUpdate = new ArrayList<YPCSkuInfo>();
        var priceHistories = new ArrayList<YPCSkuPriceHistory>();
        var alertMsgs = new ArrayList<SkuChangeMsg>();

        // 2. 遍历抓取到的新数据进行比对
        for (var newSku : newFetchedSkus) {
            var oldSku = oldSkuMap.remove(newSku.getId()); // 从 Map 中移除并获取

            if (oldSku == null) {
                // 情况 A：旧数据中没有 -> 【新上架】
                newSku.setIsProcessed(0);
                newSku.setChangeType("新上架");
                skusToUpdate.add(newSku);
                priceHistories.add(createHistoryRecord(newSku));
                alertMsgs.add(new SkuChangeMsg(newSku.getId(), newSku.getName(), ChangeType.NEW_ARRIVAL, null, newSku.getStdSalePrice()));
            } else {
                // 情况 B：旧数据中有 -> 对比价格
                if (newSku.getStdSalePrice().compareTo(oldSku.getStdSalePrice()) != 0) {
                    // 【价格变动】
                    newSku.setIsProcessed(0);
                    newSku.setChangeType("价格变动");
                    skusToUpdate.add(newSku);
                    priceHistories.add(createHistoryRecord(newSku));
                    alertMsgs.add(new SkuChangeMsg(newSku.getId(), newSku.getName(), ChangeType.PRICE_CHANGED, oldSku.getStdSalePrice(), newSku.getStdSalePrice()));
                } else {
                    // 价格没变，保留原来的处理状态
                    newSku.setIsProcessed(oldSku.getIsProcessed() == null ? 1 : oldSku.getIsProcessed());
                    newSku.setChangeType(oldSku.getChangeType());
                    skusToUpdate.add(newSku);
                }
            }
        }

        // 3. 处理剩下的 oldSkuMap：新抓取的数据中没有它了 -> 【已下架】
        for (var offlineSku : oldSkuMap.values()) {
            offlineSku.setStatus(0); // 标记为下架
            offlineSku.setLastSyncTime(batchId);
            offlineSku.setIsProcessed(0);
            offlineSku.setChangeType("已下架");
            skusToUpdate.add(offlineSku);
            alertMsgs.add(new SkuChangeMsg(offlineSku.getId(), offlineSku.getName(), ChangeType.OFFLINE, offlineSku.getStdSalePrice(), null));
        }

        // 4. 执行批量落库动作
        if (!skusToUpdate.isEmpty()) {
            skuMapper.batchUpsert(skusToUpdate);
        }
        if (!priceHistories.isEmpty()) {
            skuHistoryMapper.batchInsert(priceHistories);
        }

        // 5. 触发通知
        notifyUser(alertMsgs);
    }

    private YPCSkuPriceHistory createHistoryRecord(YPCSkuInfo sku) {
        return YPCSkuPriceHistory.builder()
                .skuId(sku.getId())
                .price(sku.getStdSalePrice())
                .createTime(LocalDateTime.now())
                .build();
    }

    private void notifyUser(List<SkuChangeMsg> alerts) {
        if (alerts.isEmpty()) return;
        String msgContent = alerts.stream()
                .map(SkuChangeMsg::toAlertText)
                .collect(Collectors.joining("\n"));
        log.info("\n📢 同步变更通知：\n{}", msgContent);
    }

    private synchronized void updateProgress(String name) {
        currentStatus = new SyncStatus(true, currentStatus.totalCategories(),
                currentStatus.completedCategories() + 1, "完成: " + name, currentStatus.startTime());
    }

    /**
     * 创建绕过 SSL 校验的 HttpClient
     */
    private HttpClient createUnsafeClient() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        }, new java.security.SecureRandom());

        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}