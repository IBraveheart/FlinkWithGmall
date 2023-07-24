import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

/**
 * @author Akang
 * @create 2023-07-08 0:21
 */
public class TestJSON {
    public static void main(String[] args) {
        String jsonStr = "{\"database\":\"gmall\",\"sinktable\":\"dwd_comment_info\",\"after\":{\"create_time\":1685578483000,\"user_id\":71,\"appraise\":\"1204\",\"comment_txt\":\"评论内容：25175744728987479344484385638361216516353289386638\",\"sku_id\":20,\"id\":1677350440883843096,\"spu_id\":6,\"order_id\":7615},\"type\":\"insert\",\"table\":\"comment_info\",\"ts_ms\":\"1688746484885\"}";
        JSONObject jsonObject = JSON.parseObject(jsonStr);
        System.out.println(jsonObject.toString());

        filterColumn(jsonObject.getJSONObject("after"),"create_time,user_id");


        System.out.println(jsonObject.toString());




    }

    public static void filterColumn(JSONObject json, String columns) {
        String[] split = columns.split(",");
        List<String> strings = Arrays.asList(split);

        Set<Map.Entry<String, Object>> entries = json.entrySet();
        Iterator<Map.Entry<String, Object>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> next = iterator.next();
            if (!strings.contains(next.getKey())) {
                iterator.remove();
            }
        }
    }
}
