package com.mogoo.productcrawler.domain.model.vo;

public enum ChangeType {
    NEW_ARRIVAL("上架"), 
    OFFLINE("下架"), 
    PRICE_CHANGED("价格变动");
    
    public final String desc;
    
    ChangeType(String desc) { 
        this.desc = desc; 
    }
}
