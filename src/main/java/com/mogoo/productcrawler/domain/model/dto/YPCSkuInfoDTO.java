package com.mogoo.productcrawler.domain.model.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YPCSkuInfoDTO {
    private int code;
    private String msg;
    private List<SpuData> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpuData {
        private String id;
        private List<SkuData> skus;

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkuData {
        private String id;
        private String name;
        @JsonProperty("std_sale_price")
        private double stdSalePrice;
        @JsonProperty("sale_unit_name")
        private String saleUnitName;
        private int stocks;
        @JsonProperty("category_id_1")
        private String categoryId1;
        @JsonProperty("category_id_2")
        private String categoryId2;

        // 捕获 JSON 中所有未定义的字段
        private Map<String, Object> otherFields = new HashMap<>();

        @JsonAnySetter
        public void setOtherField(String key, Object value) {
            this.otherFields.put(key, value);
        }
    }
}
