package com.example.flink_realtime_view.Service.impl;

import com.example.flink_realtime_view.Service.VisitorStatsService;
import com.example.flink_realtime_view.mapper.VisitorStatsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Akang
 * @create 2023-07-21 13:01
 */
@Service
public class VisitorStatsViewServiceImpl implements VisitorStatsService {
    @Autowired
    private VisitorStatsMapper visitorStatsMapper;

    @Override
    public List<VisitorStatsMapper.VisitorStatsView> getVisitorStatsView(int date, int limit) {
        List<VisitorStatsMapper.VisitorStatsView> visitorStatsView =
                visitorStatsMapper.getVisitorStatsView(date, limit);
        return visitorStatsView;
    }
}
