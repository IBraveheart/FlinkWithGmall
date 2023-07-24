package bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Akang
 * @create 2023-07-11 17:56
 */
@Data
public class OrderWide {
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

    private Long detail_id;
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

    // TODO 省份维度
    private String province_area_code;
    private String province_iso_code;
    private String province_3166_2_code;

    // TODO 用户信息
    private String user_gender;

    // TODO 作为维度数据 要关联进来
    private Long spu_id;
    private Long tm_id;
    private Long category3_id;
    private String spu_name;
    private String tm_name;
    private String category3_name;


    public OrderWide(OrderInfo orderInfo, OrderDetail orderDetail) {
        mergeOrderinfo(orderInfo);
        mergeOrderdetail(orderDetail);
    }

    private void mergeOrderdetail(OrderDetail orderDetail) {
        if (orderDetail != null) {
            this.detail_id = orderDetail.getId();
            this.order_id = orderDetail.getOrder_id();
            this.sku_id = orderDetail.getSku_id();
            this.sku_name = orderDetail.getSku_name();
            this.order_price = orderDetail.getOrder_price();
            this.sku_num = orderDetail.getSku_num();
            this.source_type = orderDetail.getSource_type();
            this.source_id = orderDetail.getSource_id();
            this.split_activity_amount = orderDetail.getSplit_activity_amount();
            this.split_coupon_amount = orderDetail.getSplit_coupon_amount();
            this.split_total_amount = orderDetail.getSplit_total_amount();
        }

    }

    private void mergeOrderinfo(OrderInfo orderInfo) {
        if (orderInfo != null) {
            this.consignee = orderInfo.getConsignee();
            this.consignee_tel = orderInfo.getConsignee_tel();
            this.total_amount = orderInfo.getTotal_amount();
            this.order_status = orderInfo.getOrder_status();
            this.user_id = orderInfo.getUser_id();
            this.payment_way = orderInfo.getPayment_way();
            this.delivery_address = orderInfo.getDelivery_address();
            this.order_comment = orderInfo.getOrder_comment();
            this.out_trade_no = orderInfo.getOut_trade_no();
            this.trade_body = orderInfo.getTrade_body();
            this.operate_time = orderInfo.getOperate_time();
            this.create_time = orderInfo.getCreate_time();
            this.expire_time = orderInfo.getExpire_time();
            this.process_status = orderInfo.getProcess_status();
            this.tracking_no = orderInfo.getTracking_no();
            this.parent_order_id = orderInfo.getParent_order_id();
            this.img_url = orderInfo.getImg_url();
            this.province_id = orderInfo.getProvince_id();
            this.activity_reduce_amount = orderInfo.getActivity_reduce_amount();
            this.coupon_reduce_amount = orderInfo.getCoupon_reduce_amount();
            this.original_total_amount = orderInfo.getOriginal_total_amount();
            this.feight_fee = orderInfo.getFeight_fee();
            this.feight_fee_reduce = orderInfo.getFeight_fee_reduce();
            this.refundable_time = orderInfo.getRefundable_time();
        }

    }
}
