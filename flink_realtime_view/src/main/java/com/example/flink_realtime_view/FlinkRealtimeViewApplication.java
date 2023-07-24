package com.example.flink_realtime_view;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.flink_realtime_view.mapper")
public class FlinkRealtimeViewApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlinkRealtimeViewApplication.class, args);
    }

}
