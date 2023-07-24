package common;

/**
 * @author Akang
 * @create 2023-07-04 18:51
 */
public class CommonUtil {


    // TODO MySQL
    public static String MysqlDriver = "com.mysql.jdbc.Driver";
    public static String MysqlUrl = "jdbc:mysql://hadoop101:3306/gmall";
    public static String MysqlUser = "root";
    public static String MysqlPasswd = "root@123";

    // TODO phoenix
    public static String PhoenixDriver = "org.apache.phoenix.jdbc.PhoenixDriver";
    public static String PhoenixUrl = "jdbc:phoenix:hadoop101:2181";

    // TODO clickhouse
    public static String ClickHouseDriver = "com.clickhouse.jdbc.ClickHouseDriver";
    public static String ClickHouseUrl = "jdbc:ch://hadoop101:8123/default" ;

    private CommonUtil() {
    }
}
