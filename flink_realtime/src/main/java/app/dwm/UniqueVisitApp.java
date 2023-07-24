package app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONAware;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import utils.DateUtil;
import utils.MyKafkaUtil;

/**
 * @author Akang
 * @create 2023-07-11 1:25
 */
public class UniqueVisitApp {
    public static void main(String[] args) throws Exception {
        // TODO 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // TODO 消费kafka流
        DataStreamSource<String> kafkaDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer("dwd_page_log", "uniquevisitapp"));

        // TODO 过滤数据
        SingleOutputStreamOperator<JSONObject> filterDStream = kafkaDStream
                .map(value -> {
                    return JSON.parseObject(value);
                })
                .keyBy(value -> value.getJSONObject("common").getString("mid"))
                .filter(new RichFilterFunction<JSONObject>() {
                    private ValueState<String> firstValue;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        // TODO 创建状态配置
                        ValueStateDescriptor<String> stateProperties =
                                new ValueStateDescriptor<String>("state", String.class);
                        StateTtlConfig ttlConfig = StateTtlConfig
                                .newBuilder(Time.days(1))
                                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                                .build();
                        stateProperties.enableTimeToLive(ttlConfig);
                        firstValue = getRuntimeContext().getState(stateProperties);
                    }

                    @Override
                    public boolean filter(JSONObject value) throws Exception {
                        String last_page_id = value.getJSONObject("page").getString("last_page_id");
                        if (last_page_id == null || last_page_id.length() <= 0) {
                            // TODO 获取状态值
                            String stateValue = firstValue.value();

                            // TODO 取出时间数据
                            Long ts = value.getLong("ts");
                            String curDate = DateUtil.getYMD(ts);

                            if (stateValue == null || !stateValue.equals(curDate)) {
                                firstValue.update(curDate);
                                return true;
                            } else {
                                return false;
                            }
                        }
                        return false;
                    }
                });
        // TODO 打印输出
        filterDStream.print();
        filterDStream
                .map(JSONAware::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer("dwm_unique_visit"));

        // TODO 启动执行
        env.execute("UniqueVisitApp");
    }
}
