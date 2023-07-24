package app.ods;

import common.CommonUtil;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import utils.MyKafkaUtil;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

/**
 * @author Akang
 * @create 2023-07-22 1:17
 */
public class FromOdsToDwdSinkCheckPointTestWithKafka {
    public static void main(String[] args) throws Exception {
        // TODO 1. 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        System.setProperty("HADOOP_USER_NAME", "fish");

        // TODO 1.1 配置ck
        env.enableCheckpointing(1000 * 60 * 3);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(1000 * 60);
        env.getCheckpointConfig().setCheckpointTimeout(1000 * 60);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(2);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().enableUnalignedCheckpoints();
        env.getCheckpointConfig().setCheckpointStorage("hdfs://hadoop101:8020/gmall_stream_2/test/ck");
        env.getCheckpointConfig().enableExternalizedCheckpoints(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

        DataStreamSource<String> kafkaDStream = env.addSource(
                MyKafkaUtil.getKafkaConsumer("source_topic", "fromodstodwdsinkcheckpointtestwithkafka"));

        SingleOutputStreamOperator<Tuple3<String, Integer, Long>> mapDStream = kafkaDStream
                .map(value -> {
                    String[] split = value.split(",");
                    return Tuple3.of(split[0], 1, Long.valueOf(split[1]));
                }).returns(Types.TUPLE(Types.STRING, Types.INT, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple3<String, Integer, Long>>forMonotonousTimestamps()
                                .withTimestampAssigner(
                                        new SerializableTimestampAssigner<Tuple3<String, Integer, Long>>() {
                                            @Override
                                            public long extractTimestamp(Tuple3<String, Integer, Long> element
                                                    , long recordTimestamp) {
                                                return element.f2;
                                            }
                                        })
                );

        SingleOutputStreamOperator<Tuple4<String, String, String, Integer>> reduceDStream = mapDStream
                .keyBy(value -> {
                    return value.f0;
                })
                .window(TumblingEventTimeWindows.of(Time.seconds(2)))
                .reduce(new ReduceFunction<Tuple3<String, Integer, Long>>() {
                    private Long result = 1L;

                    @Override
                    public Tuple3<String, Integer, Long> reduce(Tuple3<String, Integer, Long> value1
                            , Tuple3<String, Integer, Long> value2)
                            throws Exception {
                        return Tuple3.of(value1.f0, value1.f1 + value2.f1, value1.f2);
                    }
                }, new ProcessWindowFunction<Tuple3<String, Integer, Long>, Tuple4<String, String, String, Integer>
                        , String, TimeWindow>() {

                    @Override
                    public void process(String s, Context context, Iterable<Tuple3<String, Integer, Long>> elements
                            , Collector<Tuple4<String, String, String, Integer>> out) throws Exception {
                        String start = DateFormatUtils.format(
                                new Date(context.window().getStart()), "yyyy-MM-dd HH:mm:ss");
                        String end = DateFormatUtils.format(
                                new Date(context.window().getEnd()), "yyyy-MM-dd HH:mm:ss");

                        Tuple3<String, Integer, Long> next = elements.iterator().next();

                        out.collect(Tuple4.of(start, end, next.f0, next.f1));

                    }
                });


        reduceDStream.print("kafkaDStream>>>");
        reduceDStream
                .map(Tuple4::toString)
                .addSink(MyKafkaUtil.getKafkaProducer("sink_topic"));

        reduceDStream.addSink(JdbcSink.sink(
                "insert into sink_test values(?,?,?,?)"
                , new JdbcStatementBuilder<Tuple4<String, String, String, Integer>>() {
                    @Override
                    public void accept(PreparedStatement statement
                            , Tuple4<String, String, String, Integer> element)
                            throws SQLException {
                        statement.setObject(1, element.f0);
                        statement.setObject(2, element.f1);
                        statement.setObject(3, element.f2);
                        statement.setObject(4, element.f3);
                    }
                }
                , JdbcExecutionOptions.builder()
                        .withBatchSize(5)
                        .build()
                , new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withDriverName(CommonUtil.MysqlDriver)
                        .withUrl(CommonUtil.MysqlUrl)
                        .withUsername("root")
                        .withPassword("root@123")
                        .build()
        ));

        env.execute("FromOdsToDwdSinkCheckPointTestWithKafka");
    }
}
