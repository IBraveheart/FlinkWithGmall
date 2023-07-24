package app.example;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import utils.MySQLUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

/**
 * @author Akang
 * @create 2023-07-04 18:32
 */
public class FlinkConnectWithJDBC {
    public static void main(String[] args) throws Exception {
        // TODO 1. 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // TODO 2. 获得数据源
        DataStreamSource<String> stringDataStreamSource = env.addSource(new SourceFunctionWithMySQL());

        // TODO 3. 数据转换
        stringDataStreamSource.print();

        env.execute("FlinkConnectWithJDBC");

    }

    /**
     * 自定义 MySqlSource
     */
    private static class SourceFunctionWithMySQL extends RichSourceFunction<String> {
        private boolean flag = true;
        private Connection connection = null;

        @Override
        public void open(Configuration parameters) throws Exception {
            // 获得MySQL连接
            connection = MySQLUtil.getConnection();
        }

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            String sql = "select * from user_info";
            while (flag) {
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                StringBuffer stringBuffer = new StringBuffer();
                while (resultSet.next()) {
                    for (int i = 0; i < columnCount; i++) {
                        Object object = resultSet.getObject(i+1);
                        stringBuffer.append(object).append(",");
                    }
                    ctx.collect(stringBuffer.toString());
                }
                preparedStatement.close();
            }
        }

        @Override
        public void cancel() {
            flag = false;
            MySQLUtil.closeConnection();
        }
    }
}
