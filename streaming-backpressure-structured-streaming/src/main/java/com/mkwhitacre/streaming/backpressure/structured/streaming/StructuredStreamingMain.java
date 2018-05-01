package com.mkwhitacre.streaming.backpressure.structured.streaming;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;
import org.apache.spark.sql.streaming.Trigger;

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

    SparkSession spark = SparkSession
        .builder()
        .master("local")
        .appName("VistaStructuredStreamingMain")
        .getOrCreate();

    Dataset<Row> rows = spark.readStream().format("kafka")
        .option("kafka.bootstrap.servers", bootstrapServers)
        .option("subscribe", topics)
        .option("startingOffsets", "{\"" + topics + "\":{\"0\":-2}}")
        .load();

    Dataset<Row> rowDataset = rows.selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)");


    query = rowDataset.writeStream()
        //        .outputMode("complete")
        //TODO Can't do complete mode right now as there are no aggregations
        .outputMode("append")
        .format("console")
        .trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
        .queryName("Read from Kafka")
        .start();

  }

  public static void stop() {
    query.stop();
  }
}
