package app.cdc;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONAware;
import com.alibaba.fastjson.JSONObject;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import common.CommonUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.connect.json.DecimalFormat;
import org.apache.kafka.connect.json.JsonConverterConfig;
import utils.MyKafkaUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Akang
 * @create 2023-07-04 18:31
 */
public class FlinkcdcFromMysqlToKafka {
    public static void main(String[] args) throws Exception {
        // TODO 1. 获得执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // TODO 1.1 开启状态
        //env.enableCheckpointing(3000);

        Map config = new HashMap();
        config.put(JsonConverterConfig.DECIMAL_FORMAT_CONFIG, DecimalFormat.NUMERIC.name());
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname("hadoop101")
                .port(3306)
                .databaseList("gmall")
                .tableList("gmall.*")
                .startupOptions(StartupOptions.latest())
                .username(CommonUtil.MysqlUser)
                .password(CommonUtil.MysqlPasswd)
                .serverTimeZone("Asia/Shanghai")
                .deserializer(new JsonDebeziumDeserializationSchema(false, config))
                .build();


        SingleOutputStreamOperator<JSONObject> jsonDStream = env
                .fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "MySQL Source")
                .map(data -> {
                    JSONObject result = new JSONObject();
                    JSONObject jsonObject = JSON.parseObject(data);
                    JSONObject source = jsonObject.getJSONObject("source");
                    String dbName = source.getString("db");
                    String tableName = source.getString("table");
                    JSONObject before = jsonObject.getJSONObject("before");
                    JSONObject after = jsonObject.getJSONObject("after");
                    String op = jsonObject.getString("op");
                    String ts_ms = jsonObject.getString("ts_ms");

                    switch (op) {
                        case "r":
                        case "c":
                            op = "insert";
                            break;
                        case "u":
                            op = "update";
                            break;
                        case "d":
                            op = "delete";
                            break;
                    }

                    result.put("database", dbName);
                    result.put("table", tableName);
                    result.put("before", before);
                    result.put("after", after);
                    result.put("type", op);
                    result.put("ts_ms", ts_ms);
                    return result;
                });

        // TODO 打印输出
        jsonDStream.print();
        jsonDStream.
                map(JSONAware::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer("ods_base_db"));

        env.execute("FlinkcdcFromMysqlToKafka");
    }
}
