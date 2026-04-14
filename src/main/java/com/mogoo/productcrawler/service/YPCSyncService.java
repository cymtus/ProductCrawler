package com.mogoo.productcrawler.service;

import com.mogoo.productcrawler.domain.model.SyncStatus;

public interface YPCSyncService {
    // 异步执行同步任务
    void syncAllCategories();

    // 获取当前同步状态
    SyncStatus getCurrentStatus();
}