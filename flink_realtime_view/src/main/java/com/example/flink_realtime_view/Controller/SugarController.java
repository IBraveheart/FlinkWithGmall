package com.example.flink_realtime_view.Controller;

import com.example.flink_realtime_view.Service.KeywordStatsService;
import com.example.flink_realtime_view.Service.ProductStatsService;
import com.example.flink_realtime_view.Service.VisitorStatsService;
import com.example.flink_realtime_view.mapper.VisitorStatsMapper;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

/**
 * @author Akang
 * @create 2023-07-18 12:16
 */
@RestController
public class SugarController {
    @Autowired
    ProductStatsService productStatsService;
    @Autowired
    KeywordStatsService keywordStatsService;

    @Autowired
    VisitorStatsService visitorStatsService;

    @RequestMapping("getProductAmount")
    public String getProductAmount(@RequestParam(value = "date", defaultValue = "0") int date) {
        if (date == 0) {
            date = getNow();
        }

        BigDecimal productAmount = productStatsService.getProductAmount(date);

        System.out.println(productAmount);
        return "" + productAmount;
    }

    @RequestMapping("getKeywordCount")
    public String getKeywordCount(@RequestParam(value = "date", defaultValue = "0") int date,
                                  @RequestParam(value = "limit", defaultValue = "5") int limit) {
        List<String> resultList = new ArrayList<>();
        Map keywordCount = keywordStatsService.getKeywordCount(date, limit);
        return keywordCount.toString();
    }

    @RequestMapping("getVisitorStatsView")
    public String getVisitorStatsView(@RequestParam(value = "date", defaultValue = "0") int date
            , @RequestParam(value = "limit", defaultValue = "5") int limit) {

        if ("0".equals(date)) {
            date = getNow();
        }

        List<VisitorStatsMapper.VisitorStatsView> visitorStatsView =
                visitorStatsService.getVisitorStatsView(date, limit);
        StringBuffer stringBuffer = new StringBuffer();
        for (VisitorStatsMapper.VisitorStatsView value :
                visitorStatsView) {
            String str = value.getCh() + "->" + value.getPv_ct();
            stringBuffer.append(str).append("<br>");
        }
        return stringBuffer.toString();
    }

    @RequestMapping("test")
    public String test() {
        return "success";
    }


    private int getNow() {
        String yyyyMMdd = DateFormatUtils.format(new Date(), "yyyyMMdd");
        return Integer.parseInt(yyyyMMdd);
    }
}
