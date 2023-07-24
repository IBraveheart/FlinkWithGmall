package app.dwd;

import bean.TableProcess;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import common.CommonUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import utils.DimUtil;
import utils.MyKafkaUtil;
import utils.PhoenixUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Akang
 * @create 2023-07-06 13:44
 */
public class DbFromOdsToDwd {
    private static OutputTag<String> outputHbase = new OutputTag<String>("hbase") {
    };
    private static MapStateDescriptor<String, TableProcess> mapStateDescriptor = new MapStateDescriptor<>(
            "tableprocess"
            , BasicTypeInfo.STRING_TYPE_INFO
            , TypeInformation.of(new TypeHint<TableProcess>() {
    }));

    public static void main(String[] args) throws Exception {
        // TODO 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // TODO  获得数据源 , 过滤删除数据
        SingleOutputStreamOperator<String> kafkaDStream = env
                .addSource(MyKafkaUtil.getKafkaConsumer("ods_base_db", "dbfromodstodwd"))
                .filter(new FilterFunction<String>() {
                    @Override
                    public boolean filter(String value) throws Exception {
                        JSONObject jsonObject = JSON.parseObject(value);
                        String type = jsonObject.getString("type");

                        return !"delete".equals(type);
                    }
                });

        // TODO cdc消费配置数据，做成广播流
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname("hadoop101")
                .port(3306)
                .databaseList("gmall_realtime")
                .tableList("gmall_realtime.table_process")
                .startupOptions(StartupOptions.initial())
                .username(CommonUtil.MysqlUser)
                .password(CommonUtil.MysqlPasswd)
                .serverTimeZone("Asia/Shanghai")
                .deserializer(new JsonDebeziumDeserializationSchema())
                .build();


        SingleOutputStreamOperator<String> mysqlDStream =
                env.fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "mysqlsource")
                        .map(value -> {
                            JSONObject result = new JSONObject();
                            JSONObject jsonObject = JSON.parseObject(value);
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
                            }

                            result.put("database", dbName);
                            result.put("table", tableName);
                            result.put("before", before);
                            result.put("after", after);
                            result.put("type", op);
                            result.put("ts_ms", ts_ms);

                            return result.toJSONString();
                        })
                        .filter(value -> {
                            JSONObject jsonObject = JSON.parseObject(value);
                            String type = jsonObject.getString("type");
                            return !"delete".equals(type);
                        });

        BroadcastStream<String> broadcastStream = mysqlDStream.broadcast(mapStateDescriptor);


        // 主流与广播流连接
        SingleOutputStreamOperator<String> processDStream = kafkaDStream.connect(broadcastStream)
                .process(new BroadcastProcessFunction<String, String, String>() {
                    //private BroadcastState<String,TableProcess> mapState ;
                    private Connection phoenixConnection = null;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        // TODO 初始化 phoenix 连接
                        phoenixConnection = PhoenixUtil.getPhoenixConnection();
                    }

                    @Override
                    public void processBroadcastElement(String value, Context ctx, Collector<String> out)
                            throws Exception {
                        // TODO 转换数据
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject after = jsonObject.getJSONObject("after");
                        TableProcess tableProcess = JSON.parseObject(after.toString(), TableProcess.class);

                        // TODO 检查 phoenix 表是否存在
                        if ("hbase".equals(tableProcess.getSink_type())) {
                            String createSql = getSql(
                                    tableProcess.getSink_table()
                                    , tableProcess.getSink_columns()
                                    , tableProcess.getSink_pk()
                                    , tableProcess.getSink_extend());
                            System.out.println("打印建表语句sql>>>" + createSql);

                            // TODO sql预编译
                            PreparedStatement preparedStatement = null;
                            try {
                                preparedStatement = phoenixConnection.prepareStatement(createSql);
                                preparedStatement.execute();
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                if (preparedStatement != null) {
                                    try {
                                        preparedStatement.close();
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        // TODO 更新状态值
                        BroadcastState<String, TableProcess> mapState = ctx.getBroadcastState(mapStateDescriptor);
                        String mapKey = tableProcess.getSource_table() + "_" + tableProcess.getOperate_type();
                        if (!mapState.contains(mapKey)) {
                            mapState.put(mapKey, tableProcess);
                        }

                        //out.collect(value);
                    }

                    @Override
                    public void processElement(String value, ReadOnlyContext ctx, Collector<String> out)
                            throws Exception {
                        // TODO 转换数据结构
                        JSONObject jsonObject = JSON.parseObject(value);
                        String key = jsonObject.getString("table") + "_" + jsonObject.getString("type");

                        // TODO 获取状态,分流
                        ReadOnlyBroadcastState<String, TableProcess> state = ctx.getBroadcastState(mapStateDescriptor);
                        if (state.get(key) != null) {
                            TableProcess table = state.get(key);
                            // TODO 过滤字段
                            filterColumn(jsonObject.getJSONObject("after"), table.getSink_columns());

                            // TODO 添加sinktable值
                            jsonObject.put("sinktable", table.getSink_table());
                            // TODO 分流
                            if ("hbase".equals(table.getSink_type())) {
                                ctx.output(outputHbase, jsonObject.toString());
                            } else if ("kafka".equals(table.getSink_type())) {
                                out.collect(jsonObject.toJSONString());
                            }
                        } else {
                            System.out.println("没有相匹配的状态值");
                        }
                    }
                });
        // TODO 获取侧输出流
        DataStream<String> hbaseDStream = processDStream.getSideOutput(outputHbase);

        // TODO 打印输出
        hbaseDStream.print("hbaseSink>>>");
        processDStream.print("kafkaSink>>>");

        hbaseDStream.addSink(new DimSinkFunction());
        processDStream.addSink(MyKafkaUtil.getKafkaProducer());

        // TODO 启动执行
        env.execute("DbFromOdsToDwd");
    }

    private static void filterColumn(JSONObject after, String sink_columns) {
        String[] columns = sink_columns.split(",");
        List<String> strings = Arrays.asList(columns);

        Set<Map.Entry<String, Object>> entries = after.entrySet();
        entries.removeIf(next -> !strings.contains(next.getKey()));

    }

    private static String getSql(String tableName, String columns, String pk, String extend) {

        if ("".equals(pk) || pk == null) {
            pk = "id";
        }

        if (extend == null) {
            extend = "";
        }

        StringBuffer strSql = new StringBuffer("CREATE TABLE IF NOT EXISTS GMALL_REALTIME." + tableName + "(");
        String[] column = columns.split(",");
        for (int i = 0; i < column.length; i++) {
            String s = column[i];
            if (pk.equals(s)) {
                strSql.append(s).append(" VARCHAR PRIMARY KEY");
            } else {
                strSql.append(s).append(" VARCHAR");
            }

            if (i != column.length - 1) {
                strSql.append(",");
            } else {
                strSql.append(")").append(extend);
            }
        }
        return strSql.toString();
    }

    private static class DimSinkFunction extends RichSinkFunction<String> {
        private Connection phoenixConnection = null;

        @Override
        public void open(Configuration parameters) throws Exception {
            phoenixConnection = PhoenixUtil.getPhoenixConnection();
        }

        @Override
        public void close() throws Exception {
            if (phoenixConnection != null) {
                phoenixConnection.close();
            }
        }

        @Override
        public void invoke(String value, Context context) {
            PreparedStatement preparedStatement = null;
            try {

                // TODO 转换数据结构
                JSONObject jsonObject = JSON.parseObject(value);
                String sinktable = jsonObject.getString("sinktable");

                // TODO 获取数据
                JSONObject data = jsonObject.getJSONObject("after");
                Set<String> keys = data.keySet();
                Collection<Object> values = data.values();


                // TODO 创建插入数据的SQL
                String upsetSql = getUpsetSql(sinktable, keys, values);
                System.out.println("插入数据sql>>>" + upsetSql);

                // TODO 预编译sql
                preparedStatement = phoenixConnection.prepareStatement(upsetSql);
                preparedStatement.execute();

                // TODO 判断如果是更新数据删除 redis 中的缓存
                if ("update".equals(jsonObject.getString("type"))) {
                    String redisKey = sinktable + data.getString("id");
                    System.out.println("打印需要删除的redis key>>>" + redisKey);
                    DimUtil.delCache(redisKey);
                }
                // TODO 提交  PhoenixConnection 类默认的 isAutoCommit = false
                phoenixConnection.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (preparedStatement != null) {
                    try {
                        preparedStatement.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private String getUpsetSql(String sinktable, Set<String> keys, Collection<Object> values) {
            String sql = "upsert into GMALL_REALTIME." + sinktable + "(" + StringUtils.join(keys, ",") + ")"
                    + " values('" + StringUtils.join(values, "','") + "')";
            return sql;
        }

    }
}
