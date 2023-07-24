package com.example.flink_realtime_view.Service;

import java.util.Map;

/**
 * @author Akang
 * @create 2023-07-18 17:58
 */
public interface KeywordStatsService {
    Map getKeywordCount(int date, int limit);
}
