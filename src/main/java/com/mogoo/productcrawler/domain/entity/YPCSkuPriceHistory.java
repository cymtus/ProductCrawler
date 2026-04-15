package com.mogoo.productcrawler.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YPCSkuPriceHistory {
    private Long id;
    private String skuId;
    private BigDecimal price;
    private LocalDateTime createTime;
}
