package com.mogoo.productcrawler.dao;

import com.mogoo.productcrawler.domain.entity.YPCSkuInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface YPCSkuMapper {

    @Insert("""
            <script>
            INSERT INTO ypc_sku_info (id, spu_id, name, std_sale_price, stocks, sale_unit_name, 
                                     category_id_1, category_id_2, raw_json, last_sync_time, status, is_processed, change_type)
            VALUES 
            <foreach collection="list" item="item" separator=",">
                (#{item.id}, #{item.spuId}, #{item.name}, #{item.stdSalePrice}, 
                 #{item.stocks}, #{item.saleUnitName}, #{item.categoryId1}, #{item.categoryId2},
                 #{item.rawJson, typeHandler=com.mogoo.productcrawler.handler.JacksonTypeHandler},
                 #{item.lastSyncTime}, #{item.status}, #{item.isProcessed}, #{item.changeType})
            </foreach>
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                std_sale_price = VALUES(std_sale_price),
                stocks = VALUES(stocks),
                category_id_1 = VALUES(category_id_1),
                category_id_2 = VALUES(category_id_2),
                raw_json = VALUES(raw_json),
                last_sync_time = VALUES(last_sync_time),
                status = VALUES(status),
                is_processed = VALUES(is_processed),
                change_type = VALUES(change_type)
            </script>
            """)
    int batchUpsert(@Param("list") List<YPCSkuInfo> list);

    @Select("""
            SELECT * FROM ypc_sku_info 
            WHERE category_id_1 = #{categoryId} AND status = 1
            """)
    @Results(id="skuResultMap", value={
            @Result(property="stdSalePrice", column="std_sale_price"),
            @Result(property="categoryId1", column="category_id_1"),
            @Result(property="categoryId2", column="category_id_2"),
            @Result(property="lastSyncTime", column="last_sync_time"),
            @Result(property="isProcessed", column="is_processed"),
            @Result(property="changeType", column="change_type"),
            @Result(property="rawJson", column="raw_json", typeHandler=com.mogoo.productcrawler.handler.JacksonTypeHandler.class)
    })
    List<YPCSkuInfo> findActiveSkusByCategoryId(@Param("categoryId") String categoryId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM ypc_sku_info
            <where>
                <if test="keyword != null and keyword != ''">
                    AND name LIKE CONCAT('%', #{keyword}, '%')
                </if>
                <if test="status != null">
                    AND status = #{status}
                </if>
                <if test="categoryId != null and categoryId != ''">
                    AND category_id_1 = #{categoryId}
                </if>
                <if test="isProcessed != null">
                    AND is_processed = #{isProcessed}
                </if>
            </where>
            </script>
            """)
    long countSkus(@Param("keyword") String keyword, @Param("status") Integer status, @Param("categoryId") String categoryId, @Param("isProcessed") Integer isProcessed);

    @Select("""
            <script>
            SELECT * FROM ypc_sku_info
            <where>
                <if test="keyword != null and keyword != ''">
                    AND name LIKE CONCAT('%', #{keyword}, '%')
                </if>
                <if test="status != null">
                    AND status = #{status}
                </if>
                <if test="categoryId != null and categoryId != ''">
                    AND category_id_1 = #{categoryId}
                </if>
                <if test="isProcessed != null">
                    AND is_processed = #{isProcessed}
                </if>
            </where>
            ORDER BY status DESC, last_sync_time DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    @ResultMap("skuResultMap")
    List<YPCSkuInfo> searchSkusPaged(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("categoryId") String categoryId,
            @Param("isProcessed") Integer isProcessed,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Update("UPDATE ypc_sku_info SET is_processed = 1 WHERE id = #{skuId}")
    int markAsProcessed(@Param("skuId") String skuId);

    @Select("""
            SELECT DISTINCT category_id_1 FROM ypc_sku_info WHERE category_id_1 IS NOT NULL
            """)
    List<String> findAllCategories();

    @Select("SELECT MAX(last_sync_time) FROM ypc_sku_info")
    Long getLastSyncTime();
}