package com.mogoo.productcrawler.domain.model;

/**
 * 同步状态模型
 * 使用 Record 自动生成构造函数、Getter、toString、equals 和 hashCode
 */
public record SyncStatus(
        boolean isRunning,           // 是否正在运行
        int totalCategories,         // 总分类数
        int completedCategories,     // 已完成分类数
        String lastMessage,          // 最后一条状态消息
        long startTime               // 任务开始时间戳
) {}