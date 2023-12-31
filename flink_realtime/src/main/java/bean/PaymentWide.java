package bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-13 12:13
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentWide {
    Long payment_id;
    String subject;
    String payment_type;
    Long payment_create_time;
    String callback_time;
    Long detail_id;
    Long order_id;
    Long sku_id;
    BigDecimal order_price;
    Long sku_num;
    String sku_name;
    Long province_id;
    String order_status;
    Long user_id;
    BigDecimal total_amount;
    BigDecimal activity_reduce_amount;
    BigDecimal coupon_reduce_amount;
    BigDecimal original_total_amount;
    BigDecimal feight_fee;
    BigDecimal split_feight_fee;
    BigDecimal split_activity_amount;
    BigDecimal split_coupon_amount;
    BigDecimal split_total_amount;
    Long order_create_time;

    String province_name;   //查询维表得到
    String province_area_code;
    String province_iso_code;
    String province_3166_2_code;

    Integer user_age;       //用户信息
    String user_gender;

    Long spu_id;           //作为维度数据 要关联进来
    Long tm_id;
    Long category3_id;
    String spu_name;
    String tm_name;
    String category3_name;

    public PaymentWide(PaymentInfo paymentInfo, OrderWide orderWide) {
        mergePaymentInfo(paymentInfo);
        mergeOrderWide(orderWide);
    }

    private void mergeOrderWide(OrderWide orderWide) {
        if (orderWide!=null){
            try {
                BeanUtils.copyProperties(this,orderWide);
                this.order_create_time = orderWide.getCreate_time() ;
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }

        }

    }

    private void mergePaymentInfo(PaymentInfo paymentInfo) {
        if (paymentInfo != null) {
            try {
                BeanUtils.copyProperties(this, paymentInfo);
                this.payment_create_time = paymentInfo.getCreate_time();
                this.payment_id = paymentInfo.getId();
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

    }
}
