package utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;
import java.util.Properties;

/**
 * @author Akang
 * @create 2023-07-05 22:04
 */
public class MyKafkaUtil {
    private static String brokerList = "hadoop101:9092,hadoop102:9092,hadoop103:9092";
    private static String defaultTopic = "default";

    public static FlinkKafkaProducer<String> getKafkaProducer(String topicId) {
        Properties producerConfig = new Properties();
        producerConfig.put("bootstrap.servers", brokerList);
        FlinkKafkaProducer<String> flinkKafkaProducer =
                new FlinkKafkaProducer<String>(
                        topicId
                        , new SimpleStringSchema()
                        , producerConfig
                );
        return flinkKafkaProducer;
    }

    public static FlinkKafkaProducer<String> getKafkaProducer() {

        Properties producerConfig = new Properties();
        producerConfig.put("bootstrap.servers", brokerList);
        FlinkKafkaProducer<String> flinkKafkaProducer = new FlinkKafkaProducer<>(
                defaultTopic,
                new KafkaSerializationSchema<String>() {
                    @Override
                    public ProducerRecord<byte[], byte[]> serialize(String element, @Nullable Long timestamp) {
                        JSONObject jsonObject = JSON.parseObject(element);
                        String sinktable = jsonObject.getString("sinktable");
                        return new ProducerRecord<byte[], byte[]>(sinktable
                                , jsonObject.getString("after").getBytes());
                    }
                },
                producerConfig,
                FlinkKafkaProducer.Semantic.NONE
        );
        return flinkKafkaProducer;
    }

    public static FlinkKafkaConsumer<String> getKafkaConsumer(String topic, String groupId) {
        Properties props = new Properties();
        props.put("bootstrap.servers", brokerList);
        props.put("group.id", groupId);
        // earliest,latest,none
        props.put("auto.offset.reset", "latest");
        FlinkKafkaConsumer<String> flinkKafkaConsumer = new FlinkKafkaConsumer<String>(
                topic
                , new SimpleStringSchema()
                , props
        );
        return flinkKafkaConsumer;
    }

    public static String getKafkaDDL(String topicName, String groupId) {
        return "  'connector' = 'kafka'," +
                "  'topic' = '" + topicName + "'," +
                "  'properties.bootstrap.servers' = 'hadoop101:9092'," +
                "  'properties.group.id' = '" + groupId + "'," +
                "  'scan.startup.mode' = 'latest-offset'," +
                "  'format' = 'json'";
    }
}
