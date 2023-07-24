package app.dws;

import bean.KeyWordStats;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import utils.JdbcSinkUtil;
import utils.MyKafkaUtil;
import utils.SplitFunction;

/**
 * @author Akang
 * @create 2023-07-16 9:48
 */
public class KeywordStatsApp {
    public static void main(String[] args) throws Exception {
        // TODO 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .inStreamingMode()
                .build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // TODO DDL 定义动态表
        tableEnv.executeSql("CREATE TABLE page_view (" +
                "common MAP<String,String> " +
                ",page MAP<String,String> " +
                ",ts BIGINT " +
                ",rt AS TO_TIMESTAMP_LTZ(ts,3)" +
                ",WATERMARK FOR rt AS rt - INTERVAL '2' SECOND ) with (" +
                MyKafkaUtil.getKafkaDDL("dwd_page_log", "keywordstatsapp") + ")");


        // TODO 注册函数
        tableEnv.createTemporaryFunction("split_words", SplitFunction.class);

        // TODO 过滤数据
        Table table = tableEnv.sqlQuery("select page['item'] as full_word " +
                ",rt " +
                "from page_view where page['last_page_id'] ='search' and page['item'] is not null " +
                "");

        // TODO 进行分词处理
        Table wordTable = tableEnv.sqlQuery("" +
                "select word , rt from " + table + " left join lateral table(split_words(full_word)) on true" +
                "");
        tableEnv.createTemporaryView("wordTable", wordTable);

        // TODO 分组开窗聚合
        Table resultTable = tableEnv.sqlQuery("" +
                "select " +
                "'search' as source " +
                ",DATE_FORMAT(window_start,'yyyy-MM-dd HH:mm:ss') as stt " +
                ",DATE_FORMAT(window_end,'yyyy-MM-dd HH:mm:ss') as edt " +
                ",word as keyword " +
                ",count(*) as ct " +
                ",UNIX_TIMESTAMP()*1000 as ts " +
                "from table(TUMBLE(table wordTable,descriptor(rt),INTERVAL '10' SECOND)) " +
                "GROUP BY " +
                "word " +
                ",window_start" +
                ",window_end ");

        // TODO 将表转换为流
        DataStream<KeyWordStats> keyWordStatsDataStream = tableEnv.toAppendStream(resultTable, KeyWordStats.class);

        // TODO 打印输出
        keyWordStatsDataStream.print();
        keyWordStatsDataStream.addSink(JdbcSinkUtil.getJdbcSink(
                "insert into keyword_stats(keyword,ct,source,stt,edt,ts) values(?,?,?,?,?,?)"));

        // TODO 启动执行
        env.execute("KeywordStatsApp");
    }
}
