package app.dws;

import bean.ProvinceStats;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import utils.MyKafkaUtil;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-15 8:36
 */
public class ProvinceStatsSqlApp {
    public static void main(String[] args) throws Exception {
        // TODO 构建表执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // TODO DDL 定义数据源
        String dwdPageLogDDL = "CREATE TABLE dwd_page_log (" +
                "  `common` MAP<STRING,STRING>," +
                "  `page` MAP<STRING,STRING>," +
                "  `ts` BIGINT, " +
                "  `rt` AS TO_TIMESTAMP_LTZ(ts,3)," +
                "  WATERMARK FOR rt AS rt - INTERVAL '2' SECOND" +
                ") WITH (" + MyKafkaUtil.getKafkaDDL("dwd_page_log", "provincestatssqlapp") + ")";
        tableEnv.executeSql(dwdPageLogDDL);

        tableEnv.executeSql("CREATE TABLE dwm_unique_visit (" +
                "  `common` MAP<STRING,STRING>," +
                "  `page` MAP<STRING,STRING>," +
                "  `ts` BIGINT, " +
                "  `rt` AS TO_TIMESTAMP_LTZ(ts,3)," +
                "  WATERMARK FOR rt AS rt - INTERVAL '2' SECOND" +
                ") WITH ("
                + MyKafkaUtil.getKafkaDDL("dwm_unique_visit", "provincestatssqlapp") + ")");

        tableEnv.executeSql("CREATE TABLE order_wide ( " +
                "  `province_id` BIGINT, " +
                "  `province_area_code` STRING, " +
                "  `province_iso_code` STRING, " +
                "  `province_3166_2_code` STRING, " +
                "  `order_id` BIGINT, " +
                "  `split_total_amount` DECIMAL, " +
                "  `create_time` BIGINT, " +
                "  `rt` AS TO_TIMESTAMP_LTZ(create_time,3)," +
                "  WATERMARK FOR rt AS rt - INTERVAL '2' SECOND" +
                ") with(" +
                MyKafkaUtil.getKafkaDDL("dwm_order_wide", "provincestatssqlapp") + ")");


        tableEnv.executeSql("CREATE TEMPORARY TABLE dim_base_province (" +
                "  id BIGINT," +
                "  name STRING," +
                "  area_code STRING," +
                "  iso_code STRING" +
                ") WITH (" +
                "  'connector' = 'jdbc'," +
                "  'url' = 'jdbc:mysql://hadoop101:3306/gmall'," +
                "  'table-name' = 'base_province'," +
                "  'username' = 'root' ," +
                "  'password' = 'root@123'" +
                ")");

//        TableResult pTable = tableEnv.executeSql("" +
//                "SELECT common['area_code'] as province_id" +
//                ",DATE_FORMAT(window_start, 'yyyy-MM-dd HH:mm:ss') stt" +
//                ",DATE_FORMAT(window_end, 'yyyy-MM-dd HH:mm:ss') edt" +
//                ",count(1) as order_count " +
//                ",UNIX_TIMESTAMP()*1000 ts" +
//                "   FROM TABLE(" +
//                "   TUMBLE(TABLE dwd_page_log, DESCRIPTOR(rt), INTERVAL '10' SECONDS))" +
//                " GROUP BY common['area_code'],window_start, window_end") ;

//        tableEnv.executeSql("select * from order_wide").print();


        // TODO 查询数据，分组、开窗、聚合
        Table table1 = tableEnv.sqlQuery("" +
                "SELECT province_id" +
                ",DATE_FORMAT(window_start, 'yyyy-MM-dd HH:mm:ss') stt" +
                ",DATE_FORMAT(window_end, 'yyyy-MM-dd HH:mm:ss') edt" +
                ", SUM(split_total_amount) as order_amount" +
                ",count(distinct order_id) as order_count ," +
                " UNIX_TIMESTAMP()*1000 ts" +
                "   FROM TABLE(" +
                "   TUMBLE(TABLE order_wide, DESCRIPTOR(rt), INTERVAL '10' SECONDS))" +
                " GROUP BY province_id,window_start, window_end");
        tableEnv.createTemporaryView("table1", table1);

        Table table2 = tableEnv.sqlQuery("" +
                "SELECT province_id" +
                ",DATE_FORMAT(window_start, 'yyyy-MM-dd HH:mm:ss') stt" +
                ",DATE_FORMAT(window_end, 'yyyy-MM-dd HH:mm:ss') edt" +
                ", SUM(split_total_amount) as order_amount" +
                ",count(1) as order_count ," +
                " UNIX_TIMESTAMP()*1000 ts" +
                "   FROM TABLE(" +
                "   TUMBLE(TABLE order_wide, DESCRIPTOR(rt), INTERVAL '10' SECONDS))" +
                " GROUP BY province_id,window_start, window_end");
        tableEnv.createTemporaryView("table2", table2);


//        tableEnv.sqlQuery("select " +
//                "table1.province_id" +
//                ",table1.stt " +
//                ",table1.edt " +
//                ",table1.order_amount" +
//                ",table1.order_count" +
//                ",table2.order_count from table1 inner join table2 on " +
//                "table1.province_id = table2.province_id and " +
//                "table1.stt = table2.stt and " +
//                "table1.edt = table2.edt ").execute().print();



        // TODO 将动态表数据转换为流
        DataStream<ProvinceStats> tableDStream = tableEnv.toAppendStream(table1, ProvinceStats.class);

        // TODO 打印输出到clickhouse
        tableDStream.print();



        env.execute("ProvinceStatsSqlApp");
    }
}
