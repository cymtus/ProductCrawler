package com.mogoo.productcrawler.controller;

import com.mogoo.productcrawler.domain.entity.YPCSkuInfo;
import com.mogoo.productcrawler.domain.entity.YPCSkuPriceHistory;
import com.mogoo.productcrawler.domain.model.SyncStatus;
import com.mogoo.productcrawler.domain.model.dto.PageResult;
import com.mogoo.productcrawler.service.YPCSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("ypc")
@RequiredArgsConstructor
public class YPCSyncController {

    private final YPCSyncService ypcSyncService;

    @PostMapping("/sync")
    public ResponseEntity<String> sync() {
        // 使用虚拟线程异步执行，主线程立即返回 202 Accepted
        Thread.startVirtualThread(ypcSyncService::syncAllCategories);

        return ResponseEntity.accepted().body("全量同步任务已在后台启动 (Virtual Thread)");
    }

    /**
     * 获取当前同步仪表盘数据
     */
    @GetMapping("/status")
    public ResponseEntity<SyncStatus> getStatus() {
        // 直接返回 Service 中维护的 volatile SyncStatus 对象
        return ResponseEntity.ok(ypcSyncService.getCurrentStatus());
    }

    /**
     * 分页查询商品列表
     */
    @GetMapping("/skus")
    public ResponseEntity<PageResult<YPCSkuInfo>> searchSkusPaged(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Integer isProcessed,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ypcSyncService.searchSkusPaged(keyword, status, categoryId, isProcessed, page, size));
    }

    /**
     * 标记某商品为已处理
     */
    @PostMapping("/skus/{skuId}/resolve")
    public ResponseEntity<Void> resolveTask(@PathVariable String skuId) {
        ypcSyncService.markTaskProcessed(skuId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取所有分类字典 (返回 ID -> 中文名称)
     */
    @GetMapping("/categories")
    public ResponseEntity<java.util.Map<String, String>> getCategories() {
        return ResponseEntity.ok(ypcSyncService.getAllCategories());
    }

    /**
     * 查询商品历史价格走势
     */
    @GetMapping("/skus/{skuId}/history")
    public ResponseEntity<List<YPCSkuPriceHistory>> getSkuHistory(@PathVariable String skuId) {
        return ResponseEntity.ok(ypcSyncService.getSkuHistory(skuId));
    }
}