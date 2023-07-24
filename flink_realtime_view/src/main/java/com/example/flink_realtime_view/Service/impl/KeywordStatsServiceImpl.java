package com.example.flink_realtime_view.Service.impl;

import com.example.flink_realtime_view.Service.KeywordStatsService;
import com.example.flink_realtime_view.mapper.KeywordStatsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Akang
 * @create 2023-07-18 17:59
 */
@Service
public class KeywordStatsServiceImpl implements KeywordStatsService {
    @Autowired
    KeywordStatsMapper keywordStatsMapper;


    @Override
    public Map getKeywordCount(int date, int limit) {
        Map<String, Integer> result = new HashMap<>();
        List<KeywordStatsMapper.KeywordCount> keywordCount = keywordStatsMapper.getKeywordCount(date, limit);
        for (KeywordStatsMapper.KeywordCount map :
                keywordCount) {
            result.put(map.getKeyword(), map.getCt());
        }
        return result;
    }
}
