package app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONAware;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.ExecutionCheckpointingOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import utils.DateUtil;
import utils.MyKafkaUtil;

/**
 * @author Akang
 * @create 2023-07-05 23:57
 */
public class LogFromOdsToDwd {
    public static void main(String[] args) throws Exception {
        // TODO 1. 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // TODO 1.1 配置ck
        env.enableCheckpointing(1000 * 60 * 10);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(1000 * 60);
        env.getCheckpointConfig().setCheckpointTimeout(1000 * 60);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(2);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().enableUnalignedCheckpoints();
        env.getCheckpointConfig().setCheckpointStorage("hdfs://hadoop101:8020/gmall_stream_2/ck");
        env.getCheckpointConfig().enableExternalizedCheckpoints(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

        // TODO 消费kafka数据
        DataStreamSource<String> kafkaDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer("ods_base_log", "logfromodstodwd"));

        // TODO 转换数据结构，判断新老用户
        SingleOutputStreamOperator<JSONObject> mapDStream = kafkaDStream
                .keyBy(value -> {
                    JSONObject jsonObject = JSON.parseObject(value);
                    return jsonObject.getJSONObject("common").getString("mid");
                })
                .map(new RichMapFunction<String, JSONObject>() {
                    private ValueState<String> valueState;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        ValueStateDescriptor<String> stateProperties = new ValueStateDescriptor<String>(
                                "state", String.class
                        );
                        valueState = getRuntimeContext().getState(stateProperties);
                    }

                    @Override
                    public JSONObject map(String value) throws Exception {
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject common = jsonObject.getJSONObject("common");
                        String is_new = common.getString("is_new");

                        if ("1".equals(is_new)) {
                            String stateV = valueState.value();
                            String ts = DateUtil.getYMD(jsonObject.getLong("ts"));
                            if (stateV != null) {
                                jsonObject.getJSONObject("common").put("is_new", "0");
                            } else {
                                valueState.update(ts);
                            }
                        }
                        return jsonObject;
                    }
                }).disableChaining();

        // TODO 数据分流
        OutputTag<JSONObject> start_page = new OutputTag<JSONObject>("start_page") {
        };
        OutputTag<JSONObject> display_page = new OutputTag<JSONObject>("display_page") {
        };
        SingleOutputStreamOperator<JSONObject> processDStream = mapDStream.process(new ProcessFunction<JSONObject, JSONObject>() {
            @Override
            public void processElement(JSONObject value, Context ctx, Collector<JSONObject> out) throws Exception {
                JSONObject start = value.getJSONObject("start");
                if (start != null) {
                    ctx.output(start_page, value);
                } else {
                    out.collect(value);
                    JSONArray displays = value.getJSONArray("displays");
                    if (displays != null && displays.size() > 0) {
                        for (int i = 0; i < displays.size(); i++) {
                            JSONObject jsonObject = displays.getJSONObject(i);
                            jsonObject.put("mid", value.getJSONObject("common").getString("mid"));
                            jsonObject.put("page_id", value.getJSONObject("page").getString("page_id"));
                            ctx.output(display_page, value);
                        }
                    }
                }
            }
        }).disableChaining();

        // TODO 获取侧输出流
        DataStream<JSONObject> sideOutputStart = processDStream.getSideOutput(start_page);
        DataStream<JSONObject> sideOutputDisplay = processDStream.getSideOutput(display_page);

        // TODO 打印输出
        sideOutputStart.print("start>>>");
        sideOutputDisplay.print("display>>>");
        processDStream.print("page>>>");

        sideOutputStart
                .map(JSONAware::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer("dwd_start_log"))
                .name("sink_to_dwd_start_log");
        sideOutputDisplay
                .map(JSONAware::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer("dwd_display_log"))
                .name("sink_to_dwd_display_log");
        processDStream
                .map(JSONAware::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer("dwd_page_log"))
                .name("sink_to_dwd_page_log");

        // TODO 启动执行
        env.execute("LogFromOdsToDwd");
    }
}
