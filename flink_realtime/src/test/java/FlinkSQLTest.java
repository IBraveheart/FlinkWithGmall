
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * @author Akang
 * @create 2023-07-15 15:57
 */
public class FlinkSQLTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
//        tableEnv.getConfig().getConfiguration().setString("table.exec.state.ttl", "20s");

        tableEnv.executeSql("" +
                "CREATE TABLE table1 (" +
                "  `user_id` BIGINT," +
                "  `item_id` BIGINT," +
                "  `behavior` STRING," +
                "  `ts` TIMESTAMP(3) METADATA FROM 'timestamp'" +
                ") WITH (" +
                "  'connector' = 'kafka'," +
                "  'topic' = 'user_behavior'," +
                "  'properties.bootstrap.servers' = 'hadoop101:9092'," +
                "  'properties.group.id' = 'testGroup'," +
                "  'scan.startup.mode' = 'latest-offset'," +
                "  'format' = 'json'" +
                ")");

        tableEnv.executeSql("" +
                "CREATE TABLE table2 (" +
                "  `user_id` BIGINT," +
                "  `item_id` BIGINT," +
                "  `behavior` STRING," +
                "  `ts` TIMESTAMP(3) METADATA FROM 'timestamp'" +
                ") WITH (" +
                "  'connector' = 'kafka'," +
                "  'topic' = 'user_behavior2'," +
                "  'properties.bootstrap.servers' = 'hadoop101:9092'," +
                "  'properties.group.id' = 'testGroup'," +
                "  'scan.startup.mode' = 'latest-offset'," +
                "  'format' = 'json'" +
                ")");

        tableEnv.executeSql("select * from table1 a inner join table2 b on a.user_id = b.user_id " +
                "and a.ts between b.ts - INTERVAL '20' SECOND and b.ts + INTERVAL '20' SECOND").print();


        env.execute();
    }
}
