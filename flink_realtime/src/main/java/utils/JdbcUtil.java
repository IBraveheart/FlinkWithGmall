package utils;

import org.apache.commons.beanutils.BeanUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Akang
 * @create 2023-07-11 23:34
 */
public class JdbcUtil {
    public static <T> List<T> jdbcQuery(Connection connection, String sql, Class<T> cls) {
        List<T> result = new ArrayList<>();

        // TODO 预编译 SQL
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            ResultSetMetaData metaData = resultSet.getMetaData();

            while (resultSet.next()) {
                T t = cls.newInstance();
                for (int i = 0; i < metaData.getColumnCount(); i++) {
                    // TODO 获取列名
                    String columnName = metaData.getColumnName(i + 1);
                    BeanUtils.setProperty(t, columnName, resultSet.getObject(i + 1));
                }
                result.add(t);
            }
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
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }
}
