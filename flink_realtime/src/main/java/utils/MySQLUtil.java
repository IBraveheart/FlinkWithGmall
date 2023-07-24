package utils;

import com.alibaba.fastjson2.JSONObject;
import common.CommonUtil;

import java.sql.*;
import java.util.List;

/**
 * @author Akang
 * @create 2023-07-04 18:45
 */
public class MySQLUtil {
    private static Connection connection = null;

    private MySQLUtil() {
    }

    public static synchronized Connection getConnection() throws Exception {
        if (connection != null) {
            return connection;
        }
        try {
            // 加载驱动
            Class.forName(CommonUtil.MysqlDriver);
            connection = DriverManager.getConnection(
                    CommonUtil.MysqlUrl
                    , CommonUtil.MysqlUser
                    , CommonUtil.MysqlPasswd
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }

    public static void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static <T> List<T> mysqlQuery(Connection connection, String sql, Class<T> cls) {
        List<T> result = JdbcUtil.jdbcQuery(connection, sql, cls);
        return result;
    }

    public static void main(String[] args) throws Exception {
        Connection connection = getConnection();
        String sql = "select * from user_info limit 10";

        List<JSONObject> jsonObjects = mysqlQuery(connection, sql, JSONObject.class);
        for (int i = 0; i < jsonObjects.size(); i++) {
            System.out.println(jsonObjects.get(i));
        }
    }
}
