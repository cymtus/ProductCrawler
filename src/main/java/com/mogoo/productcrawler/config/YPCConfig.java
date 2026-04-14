package com.mogoo.productcrawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "ypc")
public class YPCConfig {
    private String cookie;
    private String userAgent; // 对应 yml 的 User-Agent
    private String accept;
    private String username;
    private String password;
    private Map<String, String> categories;
}
