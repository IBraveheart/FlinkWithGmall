package com.example.flink_realtime_view.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigInteger;
import java.util.List;

/**
 * @author Akang
 * @create 2023-07-21 12:55
 */
public interface VisitorStatsMapper {
    @Select("select ch ,sum(pv_ct) pv_ct from visitor_stats where toYYYYMMDD(stt)= #{date} " +
            "group by ch order by pv_ct desc limit #{limit}")
    List<VisitorStatsView> getVisitorStatsView(@Param("date") int date, @Param("limit") int limit);

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    class VisitorStatsView {
        String ch;
        BigInteger pv_ct;
    }
}
