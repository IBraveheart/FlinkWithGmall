package utils;

import com.alibaba.fastjson.JSONObject;

/**
 * @author Akang
 * @create 2023-07-12 23:18
 */
public interface AsyncDataBaseRequestInterface<T> {
    String getId(T input);

    void joinInput(T input, JSONObject dimInfo);
}
