package com.example.flink_realtime_view.Service;

import com.example.flink_realtime_view.mapper.VisitorStatsMapper;

import java.util.List;

/**
 * @author Akang
 * @create 2023-07-21 13:00
 */
public interface VisitorStatsService {
    List<VisitorStatsMapper.VisitorStatsView> getVisitorStatsView(int date, int limit);
}
