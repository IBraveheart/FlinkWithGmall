package com.example.gmall_logger.controller;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Akang
 * @create 2023-07-04 10:39
 */
@RestController   // RestController = Controller + ResponseBody
@Slf4j
public class LoggerController {
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate ;

    @RequestMapping("test")
    public String test(){
        return "success,test" ;
    }

    @RequestMapping("test1")
    private String test1(){
        return "success,test1" ;
    }

    @RequestMapping("test2")
    public String test2(@RequestParam(value = "param",defaultValue = "hello spring") String param){
        Logger log = LoggerController.log;
        LoggerController.log.info(param);

        return "success,test2,param = " + param ;
    }

    @RequestMapping("applog")
    public String applog(@RequestParam(value = "param",defaultValue = "请传递请求参数") String param){
        // 日志输出
        log.info(param);

        // kafka 输出
        kafkaTemplate.send("ods_base_log",param) ;

        //System.out.println(param);
        return "success" ;
    }

}
