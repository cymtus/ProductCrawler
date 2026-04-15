package com.mogoo.productcrawler.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Integer status; // 1: 在售, 0: 下架
    private Integer isProcessed; // 0: 待处理任务, 1: 已处理
    private String changeType; // 变更类型标签
}
