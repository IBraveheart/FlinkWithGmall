package utils;

import com.alibaba.fastjson2.JSONObject;
import common.CommonUtil;

import java.sql.*;
import java.util.List;

/**
 * @author Akang
 * @create 2023-07-07 11:42
 */
public class PhoenixUtil {
    private static Connection phoenixConnection = null;

    private PhoenixUtil() {
    }

    public static synchronized Connection getPhoenixConnection() {
        if (phoenixConnection != null) {
            return phoenixConnection;
        }

        try {
            Class.forName(CommonUtil.PhoenixDriver);
            phoenixConnection = DriverManager.getConnection(CommonUtil.PhoenixUrl);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return phoenixConnection;
    }

    public static void closePhoenix() {
        try {
            phoenixConnection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static <T> List<T> phoenixQuery(Connection connection, String sql, Class<T> cls) {
        List<T> result = JdbcUtil.jdbcQuery(connection, sql, cls);
        return result;
    }

    public static void main(String[] args) throws Exception {
        List<JSONObject> jsonObjects = phoenixQuery(
                getPhoenixConnection(), "select * from GMALL_REALTIME.DIM_BASE_PROVINCE"
                , JSONObject.class);
        for (JSONObject jsonObject : jsonObjects) {
            System.out.println(jsonObject.toString());
        }
    }


}
