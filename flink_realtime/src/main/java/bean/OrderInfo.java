package bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-11 12:15
 */
@Data
public class OrderInfo {
    private Long id;
    private String consignee;
    private String consignee_tel;
    private BigDecimal total_amount;
    private String order_status;
    private Long user_id;
    private String payment_way;
    private String delivery_address;
    private String order_comment;
    private String out_trade_no;
    private String trade_body;
    private Long create_time;
    private String operate_time;
    private String expire_time;
    private String process_status;
    private String tracking_no;
    private Long parent_order_id;
    private String img_url;
    private Long province_id;
    private BigDecimal activity_reduce_amount;
    private BigDecimal coupon_reduce_amount;
    private BigDecimal original_total_amount;
    private BigDecimal feight_fee;
    private BigDecimal feight_fee_reduce;
    private String refundable_time;
}
