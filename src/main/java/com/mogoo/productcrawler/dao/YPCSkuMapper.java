package com.mogoo.productcrawler.dao;

import com.mogoo.productcrawler.domain.entity.YPCSkuInfo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface YPCSkuMapper {

    @Insert("""
            <script>
            INSERT INTO ypc_sku_info (id, spu_id, name, std_sale_price, stocks, sale_unit_name, 
                                     category_id_1, category_id_2, raw_json, last_sync_time)
            VALUES 
            <foreach collection="list" item="item" separator=",">
                (#{item.id}, #{item.spuId}, #{item.name}, #{item.stdSalePrice}, 
                 #{item.stocks}, #{item.saleUnitName}, #{item.categoryId1}, #{item.categoryId2},
                 #{item.rawJson, typeHandler=com.mogoo.productcrawler.handler.JacksonTypeHandler},
                 #{item.lastSyncTime})
            </foreach>
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                std_sale_price = VALUES(std_sale_price),
                stocks = VALUES(stocks),
                category_id_1 = VALUES(category_id_1),
                category_id_2 = VALUES(category_id_2),
                raw_json = VALUES(raw_json),
                last_sync_time = VALUES(last_sync_time)
            </script>
            """)
    int batchUpsert(@Param("list") List<YPCSkuInfo> list);

    @Delete("""
            <script>
            DELETE FROM ypc_sku_info 
            WHERE last_sync_time &lt; #{currentBatchId}
            AND category_id_1 IN 
            <foreach collection="categoryIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    int deleteObsoleteData(@Param("categoryIds") List<String> categoryIds, @Param("currentBatchId") long currentBatchId);
}