package utils;

import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import java.util.List;

/**
 * @author Akang
 * @create 2023-07-16 9:41
 */
@FunctionHint(output = @DataTypeHint("ROW<word STRING>"))
public class SplitFunction extends TableFunction<Row> {
    public void eval(String str) {
        try {
            List<String> resultList = KeyWordUtil.keyAnalyze(str);
            for (String number :
                    resultList) {
                collect(Row.of(number));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
