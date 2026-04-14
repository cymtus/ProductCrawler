package com.mogoo.productcrawler.scheduler;

import com.mogoo.productcrawler.service.YPCSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class YPCSyncScheduler {

    private final YPCSyncService ypcSyncService;

    /**
     * 每隔 1 小时触发一次同步
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduleHourlySync() {
        log.info(">>> 定时任务触发：开始执行每小时数据同步...");
        try {
            ypcSyncService.syncAllCategories();
            log.info(">>> 定时任务结束：每小时同步已完成。");
        } catch (Exception e) {
            log.error(">>> 定时任务异常：同步过程中发生错误", e);
        }
    }
}