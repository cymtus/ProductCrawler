package com.mogoo.productcrawler.controller;

import com.mogoo.productcrawler.domain.model.SyncStatus;
import com.mogoo.productcrawler.service.YPCSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}