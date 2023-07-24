package bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-11 12:41
 */
@Data
public class OrderDetail {
    private Long id;
    private Long order_id;
    private Long sku_id;
    private String sku_name;
    private BigDecimal order_price;
    private Long sku_num;
    private Long create_time;
    private String source_type;
    private Long source_id;
    private BigDecimal split_activity_amount;
    private BigDecimal split_coupon_amount;
    private BigDecimal split_total_amount;
}
