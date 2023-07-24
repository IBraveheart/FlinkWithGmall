package app.dws;

import bean.OrderWide;
import bean.PaymentWide;
import bean.ProductStats;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import utils.AsyncDatabaseRequest;
import utils.DateUtil;
import utils.MyKafkaUtil;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 * @author Akang
 * @create 2023-07-14 10:08
 */
public class ProductStatsApp {
    public static void main(String[] args) throws Exception {
        // TODO 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // TODO 消费kafka 数据
        String groupId = "productstats";

        String pageViewSourceTopic = "dwd_page_log";
        String orderWideSourceTopic = "dwm_order_wide";
        String paymentWideSourceTopic = "dwm_payment_wide";
        String cartInfoSourceTopic = "dwd_cart_info";
        String favorInfoSourceTopic = "dwd_favor_info";
        String refundInfoSourceTopic = "dwd_order_refund_info";
        String commentInfoSourceTopic = "dwd_comment_info";
        DataStreamSource<String> pageDStream = env
                .addSource(MyKafkaUtil.getKafkaConsumer(pageViewSourceTopic, groupId));
        DataStreamSource<String> orderWideDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer(orderWideSourceTopic, groupId));
        DataStreamSource<String> paymentDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer(paymentWideSourceTopic, groupId));
        DataStreamSource<String> cartInfoDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer(cartInfoSourceTopic, groupId));
        DataStreamSource<String> favorInfoDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer(favorInfoSourceTopic, groupId));
        DataStreamSource<String> refundInfoDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer(refundInfoSourceTopic, groupId));
        DataStreamSource<String> commentInfoDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer(commentInfoSourceTopic, groupId));

        // TODO 转换数据结构
        SingleOutputStreamOperator<ProductStats> DStream1 = pageDStream.flatMap(
                new FlatMapFunction<String, bean.ProductStats>() {
                    @Override
                    public void flatMap(String value, Collector<bean.ProductStats> out) throws Exception {
                        // TOTO 转换为 json
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject page = jsonObject.getJSONObject("page");
                        String page_id = page.getString("page_id");

                        Long ts = jsonObject.getLong("ts");

                        if ("good_detail".equals(page_id) && "sku_id".equals(page.getString("item_type"))) {
                            out.collect(bean.ProductStats.builder()
                                    .sku_id(page.getLong("item"))
                                    .click_ct(1L)
                                    .ts(ts)
                                    .build());
                        }
                        JSONArray displays = jsonObject.getJSONArray("displays");
                        if (displays != null && displays.size() > 0) {
                            for (int i = 0; i < displays.size(); i++) {
                                JSONObject display = displays.getJSONObject(i);
                                if ("sku_id".equals(display.getString("item_type"))) {
                                    out.collect(bean.ProductStats.builder()
                                            .sku_id(display.getLong("item"))
                                            .display_ct(1L)
                                            .ts(ts)
                                            .build()
                                    );
                                }
                            }
                        }

                    }
                });

        SingleOutputStreamOperator<ProductStats> DStream2 = favorInfoDStream.map(value -> {
            JSONObject jsonObject = JSON.parseObject(value);
            return ProductStats.builder()
                    .sku_id(jsonObject.getLong("sku_id"))
                    .favor_ct(1L)
                    .ts(jsonObject.getLong("create_time"))
                    .build();
        });

        SingleOutputStreamOperator<ProductStats> DStream3 = cartInfoDStream.map(value -> {
            JSONObject jsonObject = JSON.parseObject(value);
            return ProductStats.builder()
                    .sku_id(jsonObject.getLong("sku_id"))
                    .cart_ct(1L)
                    .ts(jsonObject.getLong("create_time"))
                    .build();
        });

        SingleOutputStreamOperator<ProductStats> DStream4 = orderWideDStream.map(value -> {
            OrderWide orderWide = JSON.parseObject(value, OrderWide.class);
            Set<Long> orderIdSet = new HashSet<>();
            orderIdSet.add(orderWide.getOrder_id());

            return ProductStats.builder()
                    .sku_id(orderWide.getSku_id())
                    .order_sku_num(orderWide.getSku_num())
                    .order_amount(orderWide.getSplit_total_amount())
                    .orderIdSet(orderIdSet)
                    .ts(orderWide.getCreate_time())
                    .build();
        });

        SingleOutputStreamOperator<ProductStats> DStream5 = paymentDStream.map(value -> {
            PaymentWide paymentWide = JSON.parseObject(value, PaymentWide.class);
            Set<Long> orderIdSet = new HashSet<>();
            orderIdSet.add(paymentWide.getOrder_id());
            return ProductStats.builder()
                    .sku_id(paymentWide.getSku_id())
                    .payment_amount(paymentWide.getSplit_total_amount())
                    .ts(paymentWide.getOrder_create_time())
                    .paidOrderIdSet(orderIdSet)
                    .build();
        });

        SingleOutputStreamOperator<ProductStats> DStream6 = refundInfoDStream.map(value -> {
            JSONObject jsonObject = JSON.parseObject(value);
            Set<Long> orderIdSet = new HashSet<>();
            orderIdSet.add(jsonObject.getLong("order_id"));
            return ProductStats.builder()
                    .sku_id(jsonObject.getLong("sku_id"))
                    .ts(jsonObject.getLong("create_time"))
                    .refund_amount(jsonObject.getBigDecimal("refund_amount"))
                    .refundOrderIdSet(orderIdSet)
                    .build();
        });

        SingleOutputStreamOperator<ProductStats> DStream7 = commentInfoDStream.map(value -> {
            JSONObject jsonObject = JSON.parseObject(value);
            String appraise = jsonObject.getString("appraise");
            Long goodComment = 0L;
            if ("1201".equals(appraise)) {
                goodComment = 1L;
            }
            return ProductStats.builder()
                    .sku_id(jsonObject.getLong("sku_id"))
                    .comment_ct(1L)
                    .good_comment_ct(goodComment)
                    .ts(jsonObject.getLong("create_time"))
                    .build();
        });

        // TODO 多流 union
        DataStream<ProductStats> unionDStream = DStream1.union(
                DStream2, DStream3, DStream4, DStream5, DStream6, DStream7
        );

        // TODO 提取时间戳生成水位线
        SingleOutputStreamOperator<ProductStats> productDStream = unionDStream.assignTimestampsAndWatermarks(
                WatermarkStrategy.<ProductStats>forBoundedOutOfOrderness(Duration.ofSeconds(2L))
                        .withTimestampAssigner(new SerializableTimestampAssigner<ProductStats>() {
                            @Override
                            public long extractTimestamp(ProductStats element, long recordTimestamp) {
                                return element.getTs();
                            }
                        })
        );

        // TODO 分组开窗聚合
        SingleOutputStreamOperator<ProductStats> reduceDStream = productDStream.keyBy(ProductStats::getSku_id)
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .reduce(new MyReduceFunction(), new MyProcessWindowFunction());

        // TODO 关联维度信息
        SingleOutputStreamOperator<ProductStats> productStatsWithSku = AsyncDataStream.unorderedWait(
                reduceDStream,
                new AsyncDatabaseRequest<ProductStats>("DIM_SKU_INFO") {
                    @Override
                    public String getId(ProductStats input) {
                        return input.getSku_id().toString();
                    }

                    @Override
                    public void joinInput(ProductStats input, JSONObject dimInfo) {
                        input.setSku_name(dimInfo.getString("SKU_NAME"));
                        input.setSku_price(dimInfo.getBigDecimal("PRICE"));
                        input.setSpu_id(dimInfo.getLong("SPU_ID"));
                        input.setTm_id(dimInfo.getLong("TM_ID"));
                        input.setCategory3_id(dimInfo.getLong("CATEGORY3_ID"));
                    }
                },
                1000,
                TimeUnit.MILLISECONDS
        );

        SingleOutputStreamOperator<ProductStats> productStatsWithSpu = AsyncDataStream.unorderedWait(
                productStatsWithSku,
                new AsyncDatabaseRequest<ProductStats>("DIM_SPU_INFO") {
                    @Override
                    public String getId(ProductStats input) {
                        return input.getSpu_id().toString();
                    }

                    @Override
                    public void joinInput(ProductStats input, JSONObject dimInfo) {
                        input.setSpu_name(dimInfo.getString("SPU_NAME"));
                    }
                },
                1000,
                TimeUnit.MILLISECONDS
        );

        SingleOutputStreamOperator<ProductStats> productStatsWithCategory3 = AsyncDataStream.unorderedWait(
                productStatsWithSpu,
                new AsyncDatabaseRequest<ProductStats>("DIM_BASE_CATEGORY3") {
                    @Override
                    public String getId(ProductStats input) {
                        return input.getCategory3_id().toString();
                    }

                    @Override
                    public void joinInput(ProductStats input, JSONObject dimInfo) {
                        input.setCategory3_name(dimInfo.getString("NAME"));
                    }
                },
                1000,
                TimeUnit.MILLISECONDS
        );
        SingleOutputStreamOperator<ProductStats> productStatsWithTM = AsyncDataStream.unorderedWait(
                productStatsWithCategory3,
                new AsyncDatabaseRequest<ProductStats>("DIM_BASE_TRADEMARK") {
                    @Override
                    public String getId(ProductStats input) {
                        return input.getTm_id().toString();
                    }

                    @Override
                    public void joinInput(ProductStats input, JSONObject dimInfo) {
                        input.setTm_name(dimInfo.getString("TM_NAME"));
                    }
                },
                1000,
                TimeUnit.MILLISECONDS
        );


        // TODO 打印输出
        productStatsWithTM.print();
        // TODO 输出到分析型数据库 clickhouse
        


        // TODO 启动执行
        env.execute("productstats");
    }

    private static class MyReduceFunction implements ReduceFunction<ProductStats> {
        @Override
        public ProductStats reduce(ProductStats value1, ProductStats value2) throws Exception {
            value1.setDisplay_ct(value1.getDisplay_ct() + value2.getDisplay_ct());
            value1.setClick_ct(value1.getClick_ct() + value2.getClick_ct());
            value1.setCart_ct(value1.getCart_ct() + value2.getCart_ct());
            value1.setFavor_ct(value1.getFavor_ct() + value2.getFavor_ct());
            value1.setOrder_amount(value1.getOrder_amount().add(value2.getOrder_amount()));
            value1.getOrderIdSet().addAll(value2.getOrderIdSet());
            value1.setOrder_sku_num(value1.getOrder_sku_num() + value2.getOrder_sku_num());
            value1.setPayment_amount(value1.getPayment_amount().add(value2.getPayment_amount()));
            value1.getRefundOrderIdSet().addAll(value2.getRefundOrderIdSet());
            value1.setRefund_amount(value1.getRefund_amount().add(value2.getRefund_amount()));
            value1.getPaidOrderIdSet().addAll(value2.getPaidOrderIdSet());
            value1.setComment_ct(value1.getComment_ct() + value2.getComment_ct());
            value1.setGood_comment_ct(value1.getGood_comment_ct() + value2.getGood_comment_ct());
            return value1;
        }
    }

    private static class MyProcessWindowFunction extends ProcessWindowFunction<ProductStats, ProductStats
            , Long, TimeWindow> {
        @Override
        public void process(Long aLong, Context context, Iterable<ProductStats> elements
                , Collector<ProductStats> out) throws Exception {
            ProductStats productStats = elements.iterator().next();

            // TODO 补充窗口信息
            long start = context.window().getStart();
            long end = context.window().getEnd();
            productStats.setStt(DateUtil.getYMDHMS(start));
            productStats.setEdt(DateUtil.getYMDHMS(end));

            // TODO 补充订单数
            productStats.setOrder_ct((long) productStats.getOrderIdSet().size());
            productStats.setPaid_order_ct((long) productStats.getPaidOrderIdSet().size());
            productStats.setRefund_order_ct((long) productStats.getRefundOrderIdSet().size());

            // TODO 结果输出
            out.collect(productStats);
        }
    }
}
