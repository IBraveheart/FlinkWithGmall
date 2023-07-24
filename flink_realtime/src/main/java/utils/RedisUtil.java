package utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @author Akang
 * @create 2023-07-12 12:12
 */
public class RedisUtil {
    private static JedisPool jedisPool = null;

    private RedisUtil() {
    }

    public synchronized static Jedis getJedis() {
        if (jedisPool == null) {
            JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
            // TODO 最大可用连接数
            jedisPoolConfig.setMaxTotal(100);
            // TODO 连接耗尽是否等待
            jedisPoolConfig.setBlockWhenExhausted(true);
            // TODO 等待时间
            jedisPoolConfig.setMaxWaitMillis(2000);
            // TODO 最大最小闲置连接数
            jedisPoolConfig.setMaxIdle(5);
            jedisPoolConfig.setMinIdle(5);
            // TODO 取连接的时候进行一下测试
            jedisPoolConfig.setTestOnBorrow(true);

            jedisPool = new JedisPool(jedisPoolConfig, "hadoop101", 6379, 1000) ;

            System.out.println("开辟连接池");
            return jedisPool.getResource();
        } else {
            return jedisPool.getResource();
        }
    }
}
