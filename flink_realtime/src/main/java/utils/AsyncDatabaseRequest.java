package utils;

import com.alibaba.fastjson.JSONObject;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;

import java.sql.Connection;
import java.util.Collections;

/**
 * @author Akang
 * @create 2023-07-12 18:42
 */
public abstract class AsyncDatabaseRequest<T> extends RichAsyncFunction<T, T>
        implements AsyncDataBaseRequestInterface<T> {

    private String tableName;

    private Connection phoenixConnection = null;

    public AsyncDatabaseRequest(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        phoenixConnection = PhoenixUtil.getPhoenixConnection();
    }

    @Override
    public void close() throws Exception {
        PhoenixUtil.closePhoenix();
    }

    @Override
    public void asyncInvoke(T input, ResultFuture<T> resultFuture) throws Exception {
        // TODO 获取查询条件
        String id = getId(input);
        JSONObject dimInfo = DimUtil.getDimInfo(phoenixConnection, tableName, id);
        if (dimInfo != null && dimInfo.size() > 0) {
            joinInput(input, dimInfo);
        }
        resultFuture.complete(Collections.singleton(input));
    }

    @Override
    public void timeout(T input, ResultFuture<T> resultFuture) throws Exception {
        System.out.println("请求phoenixQuery超时>>>" + input);
    }
}
