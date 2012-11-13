/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.giraph.comm.messages;

import org.apache.giraph.ImmutableClassesGiraphConfiguration;
import org.apache.giraph.bsp.CentralizedServiceWorker;
import org.apache.giraph.graph.Combiner;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of {@link SimpleMessageStore} where we have a single
 * message per vertex.
 * Used when {@link Combiner} is provided.
 *
 * @param <I> Vertex id
 * @param <M> Message data
 */
public class OneMessagePerVertexStore<I extends WritableComparable,
    M extends Writable> extends SimpleMessageStore<I, M, M> {
  /** Combiner for messages */
  private final Combiner<I, M> combiner;

  /**
   * @param service  Service worker
   * @param combiner Combiner for messages
   * @param config   Hadoop configuration
   */
  OneMessagePerVertexStore(CentralizedServiceWorker<I, ?, ?, M> service,
      Combiner<I, M> combiner,
      ImmutableClassesGiraphConfiguration<I, ?, ?, M> config) {
    super(service, config);
    this.combiner = combiner;
  }

  /**
   * If there is already a message related to the vertex id in the
   * partition map return that message, otherwise create a new one,
   * put it in the map and return it
   *
   * @param vertexId Id of vertex
   * @param partitionMap Partition map
   * @return Message for this vertex
   */
  private M getOrCreateCurrentMessage(I vertexId,
      ConcurrentMap<I, M> partitionMap) {
    M currentMessage = partitionMap.get(vertexId);
    if (currentMessage == null) {
      M newMessage = combiner.createInitialMessage();
      currentMessage = partitionMap.putIfAbsent(vertexId, newMessage);
      if (currentMessage == null) {
        currentMessage = newMessage;
      }
    }
    return currentMessage;
  }

  @Override
  protected void addVertexMessagesToPartition(I vertexId,
      Collection<M> messages,
      ConcurrentMap<I, M> partitionMap) throws IOException {
    M currentMessage = getOrCreateCurrentMessage(vertexId, partitionMap);
    synchronized (currentMessage) {
      for (M message : messages) {
        combiner.combine(vertexId, currentMessage, message);
      }
    }
  }

  @Override
  protected void addVertexMessageToPartition(I vertexId, M message,
      ConcurrentMap<I, M> partitionMap) throws IOException {
    M currentMessage = getOrCreateCurrentMessage(vertexId, partitionMap);
    synchronized (currentMessage) {
      combiner.combine(vertexId, currentMessage, message);
    }
  }

  @Override
  protected Collection<M> getMessagesAsCollection(M message) {
    return Collections.singleton(message);
  }

  @Override
  protected int getNumberOfMessagesIn(ConcurrentMap<I, M> partitionMap) {
    return partitionMap.size();
  }

  @Override
  protected void writeMessages(M messages, DataOutput out) throws IOException {
    messages.write(out);
  }

  @Override
  protected M readFieldsForMessages(DataInput in) throws IOException {
    M message = config.createMessageValue();
    message.readFields(in);
    return message;
  }

  /**
   * Create new factory for this message store
   *
   * @param service Worker service
   * @param config  Hadoop configuration
   * @param <I>     Vertex id
   * @param <M>     Message data
   * @return Factory
   */
  public static <I extends WritableComparable, M extends Writable>
  MessageStoreFactory<I, M, MessageStoreByPartition<I, M>> newFactory(
      CentralizedServiceWorker<I, ?, ?, M> service,
      ImmutableClassesGiraphConfiguration<I, ?, ?, M> config) {
    return new Factory<I, M>(service, config);
  }

  /**
   * Factory for {@link CollectionOfMessagesPerVertexStore}
   *
   * @param <I> Vertex id
   * @param <M> Message data
   */
  private static class Factory<I extends WritableComparable,
      M extends Writable>
      implements MessageStoreFactory<I, M, MessageStoreByPartition<I, M>> {
    /** Service worker */
    private final CentralizedServiceWorker<I, ?, ?, M> service;
    /** Hadoop configuration */
    private final ImmutableClassesGiraphConfiguration<I, ?, ?, M> config;
    /** Combiner for messages */
    private final Combiner<I, M> combiner;

    /**
     * @param service Worker service
     * @param config  Hadoop configuration
     */
    public Factory(CentralizedServiceWorker<I, ?, ?, M> service,
        ImmutableClassesGiraphConfiguration<I, ?, ?, M> config) {
      this.service = service;
      this.config = config;
      combiner = config.createCombiner();
    }

    @Override
    public MessageStoreByPartition<I, M> newStore() {
      return new OneMessagePerVertexStore<I, M>(service, combiner, config);
    }
  }
}
