package com.mkwhitacre.streaming.backpressure.structured.streaming;

import kafka.admin.AdminOperationException;
import kafka.admin.AdminUtils;
import kafka.admin.RackAwareMode;
import kafka.utils.VerifiableProperties;
import kafka.utils.ZKConfig;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkConnection;
import org.I0Itec.zkclient.exception.ZkException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Test;
import scala.Tuple2;
import scala.collection.Iterator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KafkaReadExampleITestcase {

  public static final String BOOTSTRAP_SERVERS = "kafka.bootstrap";
  public static final String ZOOKEEPER_QUORUM = "zookeeper.quorum";

  @Test
  public void test() throws Exception {

    Properties props = new Properties();
    props.load(KafkaReadExampleITestcase.class.getResourceAsStream("/itest.properties"));

    String bootstrap = props.getProperty(BOOTSTRAP_SERVERS);
    String zkQuorum = props.getProperty(ZOOKEEPER_QUORUM);
    String topic = "iamatopic";

    createTopic(zkQuorum, bootstrap, topic);

    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);


    WriteDataRunnable writeDataRunnable = new WriteDataRunnable(bootstrap, topic, 10);

    //Schedule a runnable to be executed every 10 seconds at a fixed rate
    // to just dump out some data.
    final ScheduledFuture<?> scheduledFuture =
        executorService.scheduleAtFixedRate(writeDataRunnable, 10, 10, TimeUnit.SECONDS);

    //Let this example run for 60 seconds and then shut down.
    ScheduledFuture<?> shutdownFuture = executorService.schedule((Runnable) () ->
        scheduledFuture.cancel(true), 60, TimeUnit.SECONDS);


    //    int expected = writeData(bootstrap, topic);


    String[] args = new String[]{bootstrap, topic};


    //    readData(bootstrap, topic, expected);

    try {
      StructuredStreamingMain.main(args);

      shutdownFuture.get();

      StructuredStreamingMain.stop();
    } finally {
      executorService.shutdownNow();
    }
  }

  private static ZkUtils getZkUtils(Properties properties) {
    if (properties == null) {
      throw new IllegalArgumentException("properties cannot be null");
    }

    Tuple2<ZkClient, ZkConnection> tuple;
    try {
      ZKConfig zkConfig = new ZKConfig(new VerifiableProperties(properties));
      tuple = ZkUtils.createZkClientAndConnection(zkConfig.zkConnect(),
          zkConfig.zkSessionTimeoutMs(), zkConfig.zkConnectionTimeoutMs());
    } catch (ZkException e) {
      throw new AdminOperationException("Unable to create admin connection", e);
    }

    return new ZkUtils(tuple._1(), tuple._2(), false);
  }

  private static int writeData(String bootstrap, String topic,
                               int startingValue, int numToWrite) throws Exception {
    Map<String, Object> configProps = new HashMap<>();

    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class.getName());
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class.getName());

    System.out.println("Writing out data...");

    int total = 0;

    try (Producer<String, String> producer = new KafkaProducer<>(configProps)) {

      List<Future<RecordMetadata>> recordMetas = IntStream.range(0, 10).mapToObj(i -> {
        return IntStream.range(0, numToWrite).mapToObj(j -> {
          final String key = "key" + (startingValue + i) + ":" + j;
          final String value = "value" + (startingValue + i) + ":" + j;
          return new ProducerRecord<>(topic, key, value);
        }).collect(Collectors.toList());
      })
          .flatMap(Collection::stream).map(producer::send).collect(Collectors.toList());

      total = recordMetas.size();

      System.out.println("Starting flush of data...");
      producer.flush();
      System.out.println("Done writing out data...");

      for (Future<RecordMetadata> future: recordMetas) {
        future.get();
      }
    }

    return total;
  }

  private static void readData(String bootstrap, String topic, int expectedCount) {
    Map<String, Object> configProps = new HashMap<>();

    configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "imagroup");
    configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());
    configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());

    System.out.println("Reading data...");

    try (Consumer<String, String> consumer = new KafkaConsumer<>(configProps)) {
      consumer.subscribe(Collections.singleton(topic));
      int read = 0;
      while (read < expectedCount) {
        System.out.println("Polling data...");
        ConsumerRecords<String, String> poll = consumer.poll(1000);
        System.out.println("Polled data:" + poll.count());

        read += poll.count();
        poll.forEach(r -> System.out.println("Offset: "
            + r.offset() +  " key:" + r.value() + " value:" + r.value()));
      }
    }
  }

  //FIXME this was hacked out of common-kafka-admin b/c of scala 2.12 vs 2.11
  private static void createTopic(String zkQuorum, String bootstrap, String topic) {

    Properties props = new Properties();
    props.setProperty("zookeeper.connect", zkQuorum);

    ZkUtils zkUtils = getZkUtils(props);

    try {
      AdminUtils.createTopic(zkUtils, topic, 1, 1,
          new Properties(), RackAwareMode.Disabled$.MODULE$);
    } catch (ZkException e) {
      throw new AdminOperationException("Unable to create topic: " + topic, e);
    }

    System.out.println("Retrieving all topics");
    try {
      Collections.unmodifiableSet(
          convertToJavaSet(zkUtils.getAllTopics().iterator())).forEach(System.out::println);
    } catch (ZkException e) {
      throw new AdminOperationException("Unable to retrieve all topics", e);
    }

    try {
      Thread.sleep(5000L);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Manually converting to java set to avoid binary compatibility issues between
   * scala versions when using JavaConverters.
   */
  private static <E> Set<E> convertToJavaSet(Iterator<E> iterator) {
    Set<E> set = new HashSet<>();
    while (iterator.hasNext()) {
      set.add(iterator.next());
    }
    return Collections.unmodifiableSet(set);
  }

  /**
   * Runnable class that just writes data into Kafka.
   */
  private static class WriteDataRunnable implements Runnable {

    private final String topic;
    private final String bootstrap;
    private final int numToWrite;
    private int writtenSoFar;

    public WriteDataRunnable(String bootstrap, String topic, int numToWrite) {
      this.topic = topic;
      this.bootstrap = bootstrap;
      this.numToWrite = numToWrite;
      writtenSoFar = 0;
    }

    @Override
    public void run() {
      try {
        writtenSoFar += writeData(bootstrap, topic, writtenSoFar, numToWrite);
        System.out.println("Wrote more data:" + writtenSoFar);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

}
