package com.mkwhitacre.streaming.backpressure.flink;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;

public class AddCountFn implements MapFunction<String, Tuple2<String, Integer>> {
  @Override
  public Tuple2<String, Integer> map(String string) throws Exception {
    return new Tuple2<>(string, 1);
  }
}
