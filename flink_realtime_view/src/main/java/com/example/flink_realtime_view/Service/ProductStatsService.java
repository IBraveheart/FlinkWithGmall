package com.example.flink_realtime_view.Service;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-18 12:00
 */
public interface ProductStatsService {
     BigDecimal getProductAmount(int date);
}
