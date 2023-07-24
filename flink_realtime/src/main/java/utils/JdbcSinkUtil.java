package utils;

import common.CommonUtil;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Akang
 * @create 2023-07-17 12:10
 */
public class JdbcSinkUtil {
    public static <T> SinkFunction<T> getJdbcSink(String sql) {

        return JdbcSink.<T>sink(sql
                , new JdbcStatementBuilder<T>() {
                    @Override
                    public void accept(PreparedStatement statement, T t) throws SQLException {
                        try {
                            Field[] fields = t.getClass().getDeclaredFields();

                            // TODO 遍历字段
                            for (int i = 0; i < fields.length; i++) {
                                // TODO 获取字段
                                Field field = fields[i];

                                // TODO 设置私有属性可访问
                                field.setAccessible(true);

                                // TODO 获取字段值
                                Object value = field.get(t);

                                // TODO
                                statement.setObject(i + 1, value);
                            }
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
                , JdbcExecutionOptions.builder()
                        .withBatchSize(5)
                        .build()
                , new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(CommonUtil.ClickHouseUrl)
                        .withDriverName(CommonUtil.ClickHouseDriver)
                        .build());
    }
}
