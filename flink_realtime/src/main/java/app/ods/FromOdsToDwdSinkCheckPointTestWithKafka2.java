package app.ods;

import common.CommonUtil;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.typeinfo.Types;
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
public class FromOdsToDwdSinkCheckPointTestWithKafka2 {
    public static void main(String[] args) throws Exception {
        // TODO 1. 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        System.setProperty("HADOOP_USER_NAME", "fish");

        // TODO 1.1 配置ck
        env.enableCheckpointing(1000 * 60 * 5);
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


        kafkaDStream.print("kafkaDStream>>>");
        kafkaDStream.addSink(MyKafkaUtil.getKafkaProducer("sink_topic"));


        env.execute("FromOdsToDwdSinkCheckPointTestWithKafka");
    }
}
