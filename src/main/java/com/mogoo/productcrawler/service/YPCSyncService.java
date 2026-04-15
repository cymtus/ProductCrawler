package com.mogoo.productcrawler.service;

import com.mogoo.productcrawler.domain.entity.YPCSkuInfo;
import com.mogoo.productcrawler.domain.entity.YPCSkuPriceHistory;
import com.mogoo.productcrawler.domain.model.SyncStatus;
import com.mogoo.productcrawler.domain.model.dto.PageResult;

import java.util.List;

public interface YPCSyncService {
    // 异步执行同步任务
    void syncAllCategories();

    // 获取当前同步状态
    SyncStatus getCurrentStatus();

    // 分页查询商品列表
    PageResult<YPCSkuInfo> searchSkusPaged(String keyword, Integer status, String categoryId, Integer isProcessed, int page, int size);

    // 标记任务为已处理
    void markTaskProcessed(String skuId);

    // 获取所有分类字典 (返回 ID -> 中文名称 的映射)
    java.util.Map<String, String> getAllCategories();

    // 查询商品历史价格
    List<YPCSkuPriceHistory> getSkuHistory(String skuId);
}