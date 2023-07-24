package com.example.flink_realtime_view.mapper;

import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-18 11:40
 */
public interface ProductStatsMapper {
    @Select("select sum(order_amount) from product_stats where toYYYYMMDD(stt)=#{date}")
    public BigDecimal getProductAmount(int date);
}
