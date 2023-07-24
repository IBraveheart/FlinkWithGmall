package com.example.flink_realtime_view.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author Akang
 * @create 2023-07-18 17:52
 */
public interface KeywordStatsMapper {
    @Select("select keyword , sum(ct) ct from keyword_stats where toYYYYMMDD(stt)=#{date} " +
            "group by keyword order by ct desc limit #{limit}")
    List<KeywordCount> getKeywordCount(@Param("date") int date, @Param("limit") int limit);


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    class KeywordCount{
        String keyword ;
        int ct ;
    }
}
