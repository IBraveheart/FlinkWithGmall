package utils;

import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Akang
 * @create 2023-07-16 9:22
 */
public class KeyWordUtil {
    public static void main(String[] args) {
        List<String> strings = keyAnalyze("自由自在的学习大数据flink相关知识点，更好的成长为一个大数据工程师");
        for (String item :
                strings) {
            System.out.println(item);
        }

    }

    public static List<String> keyAnalyze(String text) {
        List<String> result = new ArrayList<>();
        Reader input = new StringReader(text);
        IKSegmenter ikSegmenter = new IKSegmenter(input, false);
        Lexeme lexeme = null;
        while (true) {
            try {
                Lexeme next = ikSegmenter.next();
                if (next != null) {
                    String lexemeText = next.getLexemeText();
                    result.add(lexemeText);
                } else {
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
