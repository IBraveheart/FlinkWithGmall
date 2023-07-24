package bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Akang
 * @create 2023-07-16 12:23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyWordStats {
    private String keyword;
    private Long ct;
    private String source;
    private String stt;
    private String edt;
    private Long ts;
}
