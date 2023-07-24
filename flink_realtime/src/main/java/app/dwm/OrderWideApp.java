package app.dwm;

import bean.OrderDetail;
import bean.OrderInfo;
import bean.OrderWide;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import utils.AsyncDatabaseRequest;
import utils.MyKafkaUtil;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author Akang
 * @create 2023-07-11 11:58
 */
public class OrderWideApp {
    public static void main(String[] args) throws Exception {
        // TODO 构建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // TODO 消费kafka数据
        SingleOutputStreamOperator<OrderInfo> orderInfoDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer("dwd_order_info", "orderwideapp"))
                        .map(value -> {
                            return JSON.parseObject(value, OrderInfo.class);
                        })
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<OrderInfo>forBoundedOutOfOrderness(Duration.ofSeconds(2L))
                                        .withTimestampAssigner(new SerializableTimestampAssigner<OrderInfo>() {
                                            @Override
                                            public long extractTimestamp(OrderInfo element, long recordTimestamp) {
                                                return element.getCreate_time();
                                            }
                                        }));
        SingleOutputStreamOperator<OrderDetail> orderDetailDStream =
                env.addSource(MyKafkaUtil.getKafkaConsumer("dwd_order_detail", "orderwideapp"))
                        .map(value -> {
                            return JSON.parseObject(value, OrderDetail.class);
                        }).assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderDetail>forBoundedOutOfOrderness(Duration.ofSeconds(2L))
                                .withTimestampAssigner(new SerializableTimestampAssigner<OrderDetail>() {
                                    @Override
                                    public long extractTimestamp(OrderDetail element, long recordTimestamp) {
                                        return element.getCreate_time();
                                    }
                                })
                );

        // TODO 双流join
        SingleOutputStreamOperator<OrderWide> orderWideDStream = orderInfoDStream.keyBy(OrderInfo::getId)
                .intervalJoin(orderDetailDStream.keyBy(OrderDetail::getOrder_id))
                .between(Time.seconds(-5), Time.seconds(5))
                .process(new ProcessJoinFunction<OrderInfo, OrderDetail, OrderWide>() {
                    @Override
                    public void processElement(OrderInfo left, OrderDetail right, Context ctx
                            , Collector<OrderWide> out) throws Exception {
                        OrderWide orderWide = new OrderWide(left, right);
                        out.collect(orderWide);
                    }
                });

        //orderWideDStream.print();

        // TODO 维度数据关联
//        // TODO 同步方法 1.1 省份维度
//        orderWideDStream.map(new RichMapFunction<OrderWide, OrderWide>() {
//            private Connection phoenixConnection = null;
//
//            @Override
//            public void open(Configuration parameters) throws Exception {
//                phoenixConnection = PhoenixUtil.getPhoenixConnection();
//            }
//
//            @Override
//            public OrderWide map(OrderWide value) throws Exception {
//                Long province_id = value.getProvince_id();
//                JSONObject jsonObject = DimUtil.getDimInfo(
//                        phoenixConnection
//                        , "DIM_BASE_PROVINCE"
//                        , String.valueOf(province_id));
//
//                value.setProvince_area_code(jsonObject.getString("AREA_CODE"));
//                value.setProvince_iso_code(jsonObject.getString("ISO_CODE"));
//                value.setProvince_3166_2_code(jsonObject.getString("ISO_3166_2"));
//                return value;
//            }
//        }).print();

        // TODO 异步 I/O 获取用户维度信息
        SingleOutputStreamOperator<OrderWide> orderWideWithUser = AsyncDataStream.unorderedWait(
                orderWideDStream
                , new AsyncDatabaseRequest<OrderWide>("DIM_USER_INFO") {
                    @Override
                    public String getId(OrderWide input) {
                        return String.valueOf(input.getUser_id());
                    }

                    @Override
                    public void joinInput(OrderWide input, JSONObject dimInfo) {
                        input.setUser_gender(dimInfo.getString("GENDER"));
                    }
                }
                , 1000
                , TimeUnit.MILLISECONDS
                , 100);

        // TODO 异步 I/O  关联SKU维度
        SingleOutputStreamOperator<OrderWide> orderWideWithSku = AsyncDataStream.unorderedWait(
                orderWideWithUser
                , new AsyncDatabaseRequest<OrderWide>("DIM_SKU_INFO") {
                    @Override
                    public String getId(OrderWide input) {
                        return String.valueOf(input.getSku_id());
                    }

                    @Override
                    public void joinInput(OrderWide input, JSONObject dimInfo) {
                        input.setSpu_id(dimInfo.getLong("SPU_ID"));
                        input.setTm_id(dimInfo.getLong("TM_ID"));
                        input.setCategory3_id(dimInfo.getLong("CATEGORY3_ID"));
                    }
                }
                , 1000
                , TimeUnit.MILLISECONDS
                , 100);

        // TODO 异步 I/O  关联SPU维度
        SingleOutputStreamOperator<OrderWide> orderWideWithSpu = AsyncDataStream.unorderedWait(
                orderWideWithSku
                , new AsyncDatabaseRequest<OrderWide>("DIM_SPU_INFO") {
                    @Override
                    public String getId(OrderWide input) {
                        return String.valueOf(input.getSpu_id());
                    }

                    @Override
                    public void joinInput(OrderWide input, JSONObject dimInfo) {
                        input.setSpu_name(dimInfo.getString("SPU_NAME"));
                    }
                }
                , 1000
                , TimeUnit.MILLISECONDS
                , 100);

        // TODO 异步 I/O 关联TM维度
        SingleOutputStreamOperator<OrderWide> orderWideWithTm = AsyncDataStream.unorderedWait(
                orderWideWithSpu
                , new AsyncDatabaseRequest<OrderWide>("DIM_BASE_TRADEMARK") {
                    @Override
                    public String getId(OrderWide input) {
                        return String.valueOf(input.getTm_id());
                    }

                    @Override
                    public void joinInput(OrderWide input, JSONObject dimInfo) {
                        input.setTm_name(dimInfo.getString("TM_NAME"));
                    }
                }
                , 1000
                , TimeUnit.MILLISECONDS
                , 100);

        // TODO 异步 I/O 关联Category维度
        SingleOutputStreamOperator<OrderWide> orderWideWithCategroy = AsyncDataStream.unorderedWait(
                orderWideWithTm
                , new AsyncDatabaseRequest<OrderWide>("DIM_BASE_CATEGORY3") {
                    @Override
                    public String getId(OrderWide input) {
                        return String.valueOf(input.getCategory3_id());
                    }

                    @Override
                    public void joinInput(OrderWide input, JSONObject dimInfo) {
                        input.setCategory3_name(dimInfo.getString("NAME"));
                    }
                }
                , 1000
                , TimeUnit.MILLISECONDS
                , 100);

        // TODO 异步 I/O 获取省份维度信息
        SingleOutputStreamOperator<OrderWide> orderWideWithProvince = AsyncDataStream.unorderedWait(
                orderWideWithCategroy
                , new AsyncDatabaseRequest<OrderWide>("DIM_BASE_PROVINCE") {
                    @Override
                    public String getId(OrderWide input) {
                        return String.valueOf(input.getProvince_id());
                    }

                    @Override
                    public void joinInput(OrderWide input, JSONObject dimInfo) {
                        input.setProvince_area_code(dimInfo.getString("AREA_CODE"));
                        input.setProvince_iso_code(dimInfo.getString("ISO_CODE"));
                        input.setProvince_3166_2_code(dimInfo.getString("ISO_3166_2"));
                    }
                }
                , 1000
                , TimeUnit.MILLISECONDS
                , 100);

        // TODO 打印输出
        orderWideWithProvince.map(JSON::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer("dwm_order_wide"));
        orderWideWithProvince.print("AsyncDataStream>>>");

        env.execute("OrderWideApp");
    }
}
