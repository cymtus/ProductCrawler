package com.mogoo.productcrawler.domain.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class YPCSkuInfo {
    private String id;
    private String spuId;
    private String name;
    private BigDecimal stdSalePrice;
    private Integer stocks;
    private String saleUnitName;
    private String categoryId1;
    private String categoryId2;
    private Map<String, Object> rawJson; // 对应数据库的 JSON 字段
    private Long lastSyncTime;
}
