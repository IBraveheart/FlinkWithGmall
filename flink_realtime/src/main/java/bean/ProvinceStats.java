package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-15 10:22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProvinceStats {
    private String stt;
    private String edt;
    private Long province_id;
    @Builder.Default
    private BigDecimal order_amount = BigDecimal.ZERO;
    private Long order_count;
    private Long ts;
}
