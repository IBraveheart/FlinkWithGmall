package app.dws;

import bean.VisitorStats;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import utils.DateUtil;
import utils.JdbcSinkUtil;
import utils.MyKafkaUtil;

import java.time.Duration;

/**
 * @author Akang
 * @create 2023-07-13 21:55
 */
public class VisitorStatsApp {
    public static void main(String[] args) throws Exception {
        // TODO 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // TODO 1.1 配置ck
        env.enableCheckpointing(1000 * 60 * 5);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(1000 * 60);
        env.getCheckpointConfig().setCheckpointTimeout(1000 * 60);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(2);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().enableUnalignedCheckpoints();
        env.getCheckpointConfig().setCheckpointStorage("hdfs://hadoop101:8020/gmall_stream_2/ck");
        env.getCheckpointConfig().enableExternalizedCheckpoints(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        System.setProperty("HADOOP_USER_NAME", "fish");

        // TODO 消费kafka 数据
        String groupId = "visitorstatsapp";
        DataStreamSource<String> pageViewDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer("dwd_page_log", groupId));
        DataStreamSource<String> uniqueDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer("dwm_unique_visit", groupId));
        DataStreamSource<String> userJumpDetailDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer("dwm_user_jump_detail", groupId));


        // TODO 转换数据结构
        SingleOutputStreamOperator<VisitorStats> VisitorDStream1 = pageViewDStream.map(value -> {
            JSONObject jsonObject = JSON.parseObject(value);
            String last_page_id = jsonObject.getJSONObject("page").getString("last_page_id");
            long sv_ct = 0L;
            if (last_page_id == null || last_page_id.length() <= 0) {
                sv_ct = 1L;
            }
            return VisitorStats.builder()
                    .vc(jsonObject.getJSONObject("common").getString("vc"))
                    .ch(jsonObject.getJSONObject("common").getString("ch"))
                    .ar(jsonObject.getJSONObject("common").getString("ar"))
                    .is_new(jsonObject.getJSONObject("common").getString("is_new"))
                    .uv_ct(0L)
                    .pv_ct(1L)
                    .sv_ct(sv_ct)
                    .uj_ct(0L)
                    .dur_sum(jsonObject.getJSONObject("page").getLong("during_time"))
                    .ts(jsonObject.getLong("ts"))
                    .build();
        });

        SingleOutputStreamOperator<VisitorStats> VisitorDStream2 = uniqueDStream.map(value -> {
            JSONObject jsonObject = JSON.parseObject(value);
            return VisitorStats.builder()
                    .vc(jsonObject.getJSONObject("common").getString("vc"))
                    .ch(jsonObject.getJSONObject("common").getString("ch"))
                    .ar(jsonObject.getJSONObject("common").getString("ar"))
                    .is_new(jsonObject.getJSONObject("common").getString("is_new"))
                    .uv_ct(1L)
                    .pv_ct(0L)
                    .sv_ct(0L)
                    .uj_ct(0L)
                    .dur_sum(0L)
                    .ts(jsonObject.getLong("ts"))
                    .build();
        });

        SingleOutputStreamOperator<VisitorStats> VisitorDStream3 = userJumpDetailDStream.map(value -> {
            JSONObject jsonObject = JSON.parseObject(value);
            return VisitorStats.builder()
                    .vc(jsonObject.getJSONObject("common").getString("vc"))
                    .ch(jsonObject.getJSONObject("common").getString("ch"))
                    .ar(jsonObject.getJSONObject("common").getString("ar"))
                    .is_new(jsonObject.getJSONObject("common").getString("is_new"))
                    .uv_ct(0L)
                    .pv_ct(0L)
                    .sv_ct(0L)
                    .uj_ct(1L)
                    .dur_sum(0L)
                    .ts(jsonObject.getLong("ts"))
                    .build();
        });

        // TODO 多流union
        DataStream<VisitorStats> unionDStream = VisitorDStream1.union(VisitorDStream2, VisitorDStream3);

        // TODO 提取时间戳生成水位线
        SingleOutputStreamOperator<VisitorStats> visitorStatsSingleOutputStreamOperator =
                unionDStream.assignTimestampsAndWatermarks(
                        WatermarkStrategy.<VisitorStats>forBoundedOutOfOrderness(Duration.ofSeconds(12L))
                                .withTimestampAssigner(new SerializableTimestampAssigner<VisitorStats>() {
                                    @Override
                                    public long extractTimestamp(VisitorStats element, long recordTimestamp) {
                                        return element.getTs();
                                    }
                                })
                );

        // TODO 分组开窗聚合
        SingleOutputStreamOperator<VisitorStats> reduceDStream = visitorStatsSingleOutputStreamOperator
                .keyBy(new KeySelector<VisitorStats, Tuple4<String, String, String, String>>() {
                    @Override
                    public Tuple4<String, String, String, String> getKey(VisitorStats value) throws Exception {
                        return Tuple4.of(
                                value.getAr(),
                                value.getCh(),
                                value.getIs_new(),
                                value.getVc()
                        );
                    }
                }).window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .reduce(new MyReduceFunction(), new MyProcessWindowFunction());


        // TODO 打印输出
        reduceDStream.print();
        reduceDStream.addSink(JdbcSinkUtil.<VisitorStats>getJdbcSink(
                "insert into visitor_stats values(?,?,?,?,?,?,?,?,?,?,?,?)"));

        // TODO 启动执行
        env.execute("VisitorStatsApp");
    }

    private static class MyReduceFunction implements ReduceFunction<VisitorStats> {
        @Override
        public VisitorStats reduce(VisitorStats value1, VisitorStats value2) throws Exception {
            value1.setUj_ct(value1.getUj_ct() + value2.getUj_ct());
            value1.setDur_sum(value1.getDur_sum() + value2.getDur_sum());
            value1.setSv_ct(value1.getSv_ct() + value2.getSv_ct());
            value1.setPv_ct(value1.getPv_ct() + value2.getPv_ct());
            value1.setUv_ct(value1.getUv_ct() + value2.getUv_ct());
            return value1;
        }
    }

    private static class MyProcessWindowFunction extends ProcessWindowFunction<VisitorStats, VisitorStats
            , Tuple4<String, String, String, String>, TimeWindow> {
        @Override
        public void process(Tuple4<String, String, String, String> key, Context context
                , Iterable<VisitorStats> elements, Collector<VisitorStats> out) throws Exception {

            // TODO 获取聚合值
            VisitorStats visitorStats = elements.iterator().next();

            // TODO 补充窗口信息
            long start = context.window().getStart();
            long end = context.window().getEnd();
            visitorStats.setStt(DateUtil.getYMDHMS(start));
            visitorStats.setEdt(DateUtil.getYMDHMS(end));


            // TODO 结果输出
            out.collect(visitorStats);
        }
    }
}
