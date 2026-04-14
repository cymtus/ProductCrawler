package com.mogoo.productcrawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Getter
@Setter
public class YPCSessionManager {
    // 初始值可以从配置文件读取，后续动态更新
    private String currentCookie;
    private final AtomicBoolean isAuthenticating = new AtomicBoolean(false);

    public YPCSessionManager(YPCConfig config) {
        this.currentCookie = config.getCookie();
    }
}
