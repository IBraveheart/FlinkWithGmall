package bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-13 12:06
 */
@Data
public class PaymentInfo {
    private Long id;
    private String out_trade_no;
    private Long order_id;
    private Long user_id;
    private String payment_type;
    private String trade_no;
    private BigDecimal total_amount;
    private String subject;
    private String payment_status;
    private Long create_time;
    private String callback_time;
    private String callback_content;
}
