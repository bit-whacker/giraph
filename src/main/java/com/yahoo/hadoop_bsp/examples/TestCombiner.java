package com.yahoo.hadoop_bsp.examples;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;

import com.yahoo.hadoop_bsp.CommunicationsInterface;
import com.yahoo.hadoop_bsp.Combiner;

/**
 * Test whether combiner is called.
 *
 */
public class TestCombiner implements Combiner<LongWritable, IntWritable> {
    public void combine(CommunicationsInterface<LongWritable, IntWritable> comm,
                        LongWritable vertex, List<IntWritable> msgList)
                throws IOException {
        int sum = 0;
        for (IntWritable msg : msgList) {
            sum += msg.get();
        }
        comm.put(vertex, new IntWritable(sum));
    }
}