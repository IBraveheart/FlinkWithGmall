package utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.java.tuple.Tuple2;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.util.List;

/**
 * 用户查询维度信息，加入独立缓存 redis
 *
 * @author Akang
 * @create 2023-07-12 10:20
 */
public class DimUtil {
    public static JSONObject getDimInfo(Connection connection, String tableName, Tuple2<String, String>... value) {
        JSONObject jsonObject = null;
        if (value.length <= 0) {
            throw new RuntimeException("查询维度数时，至少设置一个查询条件");
        }
        // TODO 拼接 where字句
        StringBuffer where = new StringBuffer(" where ");
        // TODO 设置redis key
        StringBuffer redisKey = new StringBuffer(tableName).append(":");

        for (int i = 0; i < value.length; i++) {
            String column = value[i].f0;
            String data = value[i].f1;
            where.append(column).append("='").append(data).append("'");
            redisKey.append(data);

            if (i < value.length - 1) {
                where.append(" and ");
                redisKey.append(":");
            }
        }
        // TODO 获得 redis 连接
        Jedis jedis = RedisUtil.getJedis();
        String jsonStr = jedis.get(redisKey.toString());
        if (jsonStr != null && jsonStr.length() > 0) {
            jedis.close();
            return JSON.parseObject(jsonStr, JSONObject.class);
        }

        // TODO 拼接SQL
        String sql = "select * from GMALL_REALTIME." + tableName + where.toString();
        System.out.println("打印查询sql>>>>" + sql);

        List<JSONObject> jsonObjects =
                PhoenixUtil.phoenixQuery(connection, sql, JSONObject.class);
        if (jsonObjects.size() > 0) {
            jsonObject = jsonObjects.get(0);
        }
        // TODO 把数据缓存到 redis
        jedis.set(redisKey.toString(), jsonObject.toString());
        jedis.expire(redisKey.toString(), 60 * 60 * 24);
        jedis.close();

        // TODO 返回结果
        return jsonObject;
    }

    public static JSONObject getDimInfo(Connection connection, String tableName, String id) {
        return getDimInfo(connection, tableName, Tuple2.of("id", id));
    }

    public static void delCache(String key) {
        Jedis jedis = RedisUtil.getJedis();
        jedis.del(key);
        jedis.close();
    }


    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        System.out.println(DimUtil.getDimInfo(PhoenixUtil.getPhoenixConnection(), "DIM_BASE_PROVINCE", "25"));
        long end = System.currentTimeMillis();
        System.out.println(DimUtil.getDimInfo(PhoenixUtil.getPhoenixConnection(), "DIM_BASE_PROVINCE", "25"));
        long end1 = System.currentTimeMillis();
        System.out.println((end - start));
        System.out.println(end1 - end);
    }
}
