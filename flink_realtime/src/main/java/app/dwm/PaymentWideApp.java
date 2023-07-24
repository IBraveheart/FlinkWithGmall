package app.dwm;

import bean.OrderWide;
import bean.PaymentInfo;
import bean.PaymentWide;
import com.alibaba.fastjson.JSON;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import utils.MyKafkaUtil;

import java.time.Duration;

/**
 * @author Akang
 * @create 2023-07-13 13:12
 */
public class PaymentWideApp {
    public static void main(String[] args) throws Exception {
        // TODO 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // TODO 消费 kafka 数据
        SingleOutputStreamOperator<PaymentInfo> paymentInfoSingleOutputStreamOperator = env
                .addSource(MyKafkaUtil.getKafkaConsumer("dwd_payment_info", "paymentwideapp"))
                .map(value -> {
                    return JSON.parseObject(value, PaymentInfo.class);
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<PaymentInfo>forBoundedOutOfOrderness(Duration.ofSeconds(2L))
                                .withTimestampAssigner(new SerializableTimestampAssigner<PaymentInfo>() {
                                    @Override
                                    public long extractTimestamp(PaymentInfo element, long recordTimestamp) {
                                        return element.getCreate_time();
                                    }
                                })
                );
        paymentInfoSingleOutputStreamOperator.print("payment>>>");

        SingleOutputStreamOperator<OrderWide> orderWideSingleOutputStreamOperator = env
                .addSource(MyKafkaUtil.getKafkaConsumer("dwm_order_wide", "paymentwideapp"))
                .map(value -> {
                    return JSON.parseObject(value, OrderWide.class);
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderWide>forBoundedOutOfOrderness(Duration.ofSeconds(2L))
                                .withTimestampAssigner(new SerializableTimestampAssigner<OrderWide>() {
                                    @Override
                                    public long extractTimestamp(OrderWide element, long recordTimestamp) {
                                        return element.getCreate_time();
                                    }
                                })
                );
        orderWideSingleOutputStreamOperator.print("orderwide>>>");

        // TODO 双流 join
        SingleOutputStreamOperator<PaymentWide> paymenWideDStrem = paymentInfoSingleOutputStreamOperator
                .keyBy(PaymentInfo::getOrder_id)
                .intervalJoin(orderWideSingleOutputStreamOperator.keyBy(OrderWide::getOrder_id))
                .between(Time.minutes(-15), Time.seconds(5))
                .process(new ProcessJoinFunction<PaymentInfo, OrderWide, PaymentWide>() {
                    @Override
                    public void processElement(PaymentInfo left, OrderWide right, Context ctx
                            , Collector<PaymentWide> out) throws Exception {
                        PaymentWide paymentWide = new PaymentWide(left, right);
                        out.collect(paymentWide);
                    }
                });

        // TODO 打印输出
        paymenWideDStrem.print("paymenWideDStrem>>>");
        paymenWideDStrem
                .map(JSON::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer("dwm_payment_wide"));

        env.execute("paymentwideapp");
    }
}
