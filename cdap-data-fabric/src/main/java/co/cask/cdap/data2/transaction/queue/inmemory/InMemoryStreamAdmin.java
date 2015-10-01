/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.data2.transaction.queue.inmemory;

import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.common.queue.QueueName;
import co.cask.cdap.data.stream.service.StreamMetaStore;
import co.cask.cdap.data.view.ViewAdmin;
import co.cask.cdap.data2.metadata.lineage.AccessType;
import co.cask.cdap.data2.metadata.service.BusinessMetadataStore;
import co.cask.cdap.data2.metadata.writer.LineageWriter;
import co.cask.cdap.data2.registry.DefaultUsageRegistry;
import co.cask.cdap.data2.registry.UsageRegistry;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.StreamProperties;
import co.cask.cdap.proto.ViewSpecification;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;

/**
 * admin for queues in memory.
 */
@Singleton
public class InMemoryStreamAdmin extends InMemoryQueueAdmin implements StreamAdmin {
  private final StreamMetaStore streamMetaStore;
  private final UsageRegistry usageRegistry;
  private final LineageWriter lineageWriter;
  private final BusinessMetadataStore businessMds;
  private final ViewAdmin viewAdmin;

  @Inject
  public InMemoryStreamAdmin(InMemoryQueueService queueService,
                             DefaultUsageRegistry usageRegistry,
                             LineageWriter lineageWriter,
                             StreamMetaStore streamMetaStore,
                             BusinessMetadataStore businessMds,
                             ViewAdmin viewAdmin) {
    super(queueService);
    this.usageRegistry = usageRegistry;
    this.streamMetaStore = streamMetaStore;
    this.lineageWriter = lineageWriter;
    this.businessMds = businessMds;
    this.viewAdmin = viewAdmin;
  }

  @Override
  public void dropAllInNamespace(Id.Namespace namespace) throws Exception {
    queueService.resetStreamsWithPrefix(QueueName.prefixForNamedspacedStream(namespace.getId()));
    for (StreamSpecification spec : streamMetaStore.listStreams(namespace)) {
      // Remove metadata for the stream
      businessMds.removeMetadata(Id.Stream.from(namespace, spec.getName()));
      streamMetaStore.removeStream(Id.Stream.from(namespace, spec.getName()));
    }
  }

  @Override
  public void configureInstances(Id.Stream streamId, long groupId, int instances) throws Exception {
    // No-op
  }

  @Override
  public void configureGroups(Id.Stream streamId, Map<Long, Integer> groupInfo) throws Exception {
    // No-op
  }

  @Override
  public StreamConfig getConfig(Id.Stream streamId) {
    throw new UnsupportedOperationException("Stream config not supported for non-file based stream.");
  }

  @Override
  public void updateConfig(Id.Stream streamId, StreamProperties properties) throws IOException {
    throw new UnsupportedOperationException("Stream config not supported for non-file based stream.");
  }

  @Override
  public boolean exists(Id.Stream streamId) throws Exception {
    return exists(QueueName.fromStream(streamId));
  }

  @Override
  public StreamConfig create(Id.Stream streamId) throws Exception {
    create(QueueName.fromStream(streamId));
    return null;
  }

  @Override
  public StreamConfig create(Id.Stream streamId, @Nullable Properties props) throws Exception {
    create(QueueName.fromStream(streamId), props);
    streamMetaStore.addStream(streamId);
    return null;
  }

  @Override
  public void truncate(Id.Stream streamId) throws Exception {
    Preconditions.checkArgument(exists(streamId), "Stream '%s' does not exist.", streamId);
    truncate(QueueName.fromStream(streamId));
  }

  @Override
  public void drop(Id.Stream streamId) throws Exception {
    Preconditions.checkArgument(exists(streamId), "Stream '%s' does not exist.", streamId);
    // Remove metadata for the stream
    businessMds.removeMetadata(streamId);
    drop(QueueName.fromStream(streamId));
    streamMetaStore.removeStream(streamId);
  }

  @Override
  public boolean createOrUpdateView(Id.Stream.View viewId, ViewSpecification spec) throws Exception {
    Preconditions.checkArgument(exists(viewId.getStream()), "Stream '%s' does not exist.", viewId.getStreamId());
    return viewAdmin.createOrUpdate(viewId, spec);
  }

  @Override
  public void deleteView(Id.Stream.View viewId) throws Exception {
    Preconditions.checkArgument(exists(viewId.getStream()), "Stream '%s' does not exist.", viewId.getStreamId());
    viewAdmin.delete(viewId);
  }

  @Override
  public List<Id.Stream.View> listViews(Id.Stream streamId) throws Exception {
    Preconditions.checkArgument(exists(streamId), "Stream '%s' does not exist.", streamId);
    return viewAdmin.list(streamId);
  }

  @Override
  public ViewSpecification getView(Id.Stream.View viewId) throws Exception {
    Preconditions.checkArgument(exists(viewId.getStream()), "Stream '%s' does not exist.", viewId.getStreamId());
    return viewAdmin.get(viewId);
  }

  @Override
  public void register(Iterable<? extends Id> owners, Id.Stream streamId) {
    usageRegistry.registerAll(owners, streamId);
  }

  @Override
  public void addAccess(Id.Run run, Id.Stream streamId, AccessType accessType) {
    lineageWriter.addAccess(run, streamId, accessType);
  }
}
