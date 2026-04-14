package com.mogoo.productcrawler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.mogoo.productcrawler.dao")
@SpringBootApplication
public class ProductCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductCrawlerApplication.class, args);
    }

}
