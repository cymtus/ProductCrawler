package com.mogoo.productcrawler.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogoo.productcrawler.component.YPCAuthenticator;
import com.mogoo.productcrawler.config.YPCConfig;
import com.mogoo.productcrawler.dao.YPCSkuMapper;
import com.mogoo.productcrawler.domain.entity.YPCSkuInfo;
import com.mogoo.productcrawler.domain.model.SyncStatus;
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
import java.time.Duration;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class YPCSyncServiceImpl implements YPCSyncService {

    private final YPCSkuMapper skuMapper;
    private final ObjectMapper objectMapper;
    private final YPCConfig ypcConfig;
    private final YPCAuthenticator authenticator;

    private volatile SyncStatus currentStatus = new SyncStatus(false, 0, 0, "Idle", 0);
    private static final String API_URL_PREFIX = "https://bshop.guanmai.cn/product/sku/get?level=1&type=1&category_id=";

    @Override
    public SyncStatus getCurrentStatus() { return currentStatus; }

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
                        processAndSave(json, currentBatchId);
                    }
                    updateProgress(name);
                } catch (Exception e) {
                    log.error("分类 {} 同步异常: {}", name, e.getMessage());
                }
            }));
        }

        skuMapper.deleteObsoleteData(categories.keySet().stream().toList(), currentBatchId);
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
    public void processAndSave(String jsonResponse, long batchId) throws JsonProcessingException {
        var result = objectMapper.readValue(jsonResponse, YPCSkuInfoDTO.class);
        if (result.getCode() != 0 || result.getData() == null) return;

        var skuInfoList = result.getData().stream()
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
                        .build()))
                .toList();

        if (!skuInfoList.isEmpty()) {
            skuMapper.batchUpsert(skuInfoList);
        }
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