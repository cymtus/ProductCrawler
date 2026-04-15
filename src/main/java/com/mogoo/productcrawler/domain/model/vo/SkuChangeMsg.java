package com.mogoo.productcrawler.domain.model.vo;

import java.math.BigDecimal;

public record SkuChangeMsg(
    String skuId, 
    String name, 
    ChangeType type, 
    BigDecimal oldPrice, 
    BigDecimal newPrice
) {
    public String toAlertText() {
        return switch (type) {
            case NEW_ARRIVAL -> "🟢 [新上架] %s (ID: %s) 初始价格: %s".formatted(name, skuId, newPrice);
            case OFFLINE -> "🔴 [已下架] %s (ID: %s)".formatted(name, skuId);
            case PRICE_CHANGED -> "🟡 [价格变动] %s (ID: %s) 价格从 %s 变更为 %s".formatted(name, skuId, oldPrice, newPrice);
        };
    }
}
