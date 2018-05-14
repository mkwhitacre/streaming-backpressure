package com.mkwhitacre.streaming.backpressure.structured.streaming;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;
import org.apache.spark.sql.streaming.Trigger;
import scala.Tuple2;

import java.util.concurrent.TimeUnit;

public class StructuredStreamingMain {

  private static StreamingQuery query;

  /**
   * Satisfying the checkstyle.
   * @param args these are args.
   */
  public static void main(String[] args) throws StreamingQueryException {

    String bootstrapServers = args[0];
    String topics = args[1];
    String outTopic = args[2];
    String checkpointLocation = args[3];

    long slowDown = Long.valueOf(args[4]);

    SparkSession spark = SparkSession
        .builder()
        .master("local[3]")
        .appName("StructuredStreamingMain")
        .config("spark.sql.streaming.checkpointLocation", checkpointLocation)
        .getOrCreate();

    Dataset<Row> rows = spark.readStream().format("kafka")
        .option("kafka.bootstrap.servers", bootstrapServers)
        .option("subscribe", topics)
        //        .option("maxOffsetsPerTrigger", 10)
        .option("startingOffsets", "{\"" + topics + "\":{\"0\":-2}}")
        .load();

    Dataset<Row> rowDataset = rows.selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)");

    Dataset<Tuple2<String, String>> delayedValues = rowDataset
        .map(new SlowDownFunction(slowDown), Encoders.tuple(Encoders.STRING(), Encoders.STRING()));

    query = delayedValues.writeStream()

        .format("kafka")
        .option("kafka.bootstrap.servers", bootstrapServers)
        .option("topic", outTopic)



        //    query = rowDataset.writeStream()
        //        //        .outputMode("complete")
        //        //TODO Can't do complete mode right now as there are no aggregations
        //        .outputMode("append")
        //        .format("console")

        .trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
        .queryName("Read from Kafka")
        .start();

  }

  public static void stop() {
    query.stop();
  }
}
