package com.example.flink_realtime_view.Service.impl;

import com.example.flink_realtime_view.Service.ProductStatsService;
import com.example.flink_realtime_view.mapper.ProductStatsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-18 12:02
 */
@Service
public class ProductStatsServiceImpl implements ProductStatsService {
    @Autowired
    ProductStatsMapper productStatsMapper;

    @Override
    public BigDecimal getProductAmount(int date) {
        return productStatsMapper.getProductAmount(date);
    }
}
