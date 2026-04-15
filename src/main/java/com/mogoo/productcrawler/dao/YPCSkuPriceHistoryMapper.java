package com.mogoo.productcrawler.dao;

import com.mogoo.productcrawler.domain.entity.YPCSkuPriceHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface YPCSkuPriceHistoryMapper {
    @Insert("""
            <script>
            INSERT INTO ypc_sku_price_history (sku_id, price, create_time)
            VALUES 
            <foreach collection="list" item="item" separator=",">
                (#{item.skuId}, #{item.price}, #{item.createTime})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<YPCSkuPriceHistory> list);

    @Select("""
            SELECT * FROM ypc_sku_price_history 
            WHERE sku_id = #{skuId}
            ORDER BY create_time ASC
            """)
    @org.apache.ibatis.annotations.Results({
            @org.apache.ibatis.annotations.Result(property="skuId", column="sku_id"),
            @org.apache.ibatis.annotations.Result(property="createTime", column="create_time")
    })
    List<YPCSkuPriceHistory> findHistoryBySkuId(@Param("skuId") String skuId);
}
