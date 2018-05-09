package com.mkwhitacre.streaming.backpressure.flink;

import static org.apache.flink.streaming.api.windowing.time.Time.seconds;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;

import java.util.Properties;

public class FlinkKafkaStreamingMain {

  private static FlinkKafkaConsumer010<String> kafkaConsumer;

  /**
   * Mains.
   * @param args args
   * @throws Exception an exception
   */
  public static void main(String[] args) throws Exception {

    String bootstrapServers = args[0];
    String topics = args[1];

    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", bootstrapServers);
    properties.setProperty("group.id", "flinkTest");

    kafkaConsumer = new FlinkKafkaConsumer010<>(topics, new SimpleStringSchema(), properties);

    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    DataStream<Tuple2<String, Integer>> stream = env
        .addSource(kafkaConsumer)
        .map(new AddCountFn())
        .keyBy(0)
        .timeWindow(seconds(5))
        .sum(1);

    stream.print();

    env.execute("Window WordCount");
  }

  public static void stop() throws Exception {
    kafkaConsumer.close();
  }


}
