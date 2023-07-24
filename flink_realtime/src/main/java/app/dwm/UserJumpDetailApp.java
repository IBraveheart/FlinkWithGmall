package app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternFlatSelectFunction;
import org.apache.flink.cep.PatternFlatTimeoutFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import utils.MyKafkaUtil;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @author Akang
 * @create 2023-07-11 2:08
 */
public class UserJumpDetailApp {
    public static void main(String[] args) throws Exception {
        // TODO 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//        env.setParallelism(1);

        // TODO 消费kafka流

        KeyedStream<JSONObject, String> kafkaDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer("dwd_page_log", "userjumpdetailapp"))
                        .map(value -> {
                            return JSON.parseObject(value);
                        })
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(2L))
                                        .withTimestampAssigner(new SerializableTimestampAssigner<JSONObject>() {
                                            @Override
                                            public long extractTimestamp(JSONObject element, long recordTimestamp) {
                                                return element.getLong("ts");
                                            }
                                        }))
                        .keyBy(value -> {
                            return value.getJSONObject("common").getString("mid");
                        });


        // TODO 定义匹配模式
        Pattern<JSONObject, JSONObject> pattern = Pattern.<JSONObject>begin("start")
                .times(2)
                .within(Time.seconds(10))
                .consecutive()  // TODO 指定严格模式
                .where(new SimpleCondition<JSONObject>() {
                    @Override
                    public boolean filter(JSONObject value) throws Exception {
                        String last_page_id = value.getJSONObject("page").getString("last_page_id");

                        return last_page_id == null || last_page_id.length() <= 0;
                    }
                });

        // TODO 把模式应用在流上
        PatternStream<JSONObject> patternStream = CEP.pattern(kafkaDStream, pattern);

        // TODO 处理模式流
        OutputTag<String> timeout = new OutputTag<String>("timeout") {
        };
        SingleOutputStreamOperator<String> flatSelect = patternStream.flatSelect(timeout,
                new PatternFlatTimeoutFunction<JSONObject, String>() {
                    @Override
                    public void timeout(Map<String, List<JSONObject>> pattern, long timeoutTimestamp
                            , Collector<String> out) throws Exception {
                        JSONObject jsonObject = pattern.get("start").get(0);
                        out.collect(jsonObject.toJSONString());
                    }
                },
                new PatternFlatSelectFunction<JSONObject, String>() {
                    @Override
                    public void flatSelect(Map<String, List<JSONObject>> pattern
                            , Collector<String> out) throws Exception {
                        JSONObject jsonObject = pattern.get("start").get(0);
                        out.collect(jsonObject.toJSONString());
                    }
                }).setParallelism(2);

        // TODO 获取超时数据
        DataStream<String> sideOutput = flatSelect.getSideOutput(timeout);
        DataStream<String> unionDStream = flatSelect.union(sideOutput);

        // TODO 打印输出
        unionDStream.print();
        unionDStream.addSink(MyKafkaUtil.getKafkaProducer("dwm_user_jump_detail"));

        // TODO 启动执行
        env.execute("UserJumpDetailApp");
    }
}
