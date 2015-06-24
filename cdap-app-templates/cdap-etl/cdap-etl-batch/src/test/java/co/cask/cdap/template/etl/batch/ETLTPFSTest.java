/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.template.etl.batch;

import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.lib.FileSetProperties;
import co.cask.cdap.api.dataset.lib.TimePartitionedFileSet;
import co.cask.cdap.proto.AdapterConfig;
import co.cask.cdap.proto.Id;
import co.cask.cdap.template.etl.batch.config.ETLBatchConfig;
import co.cask.cdap.template.etl.common.ETLStage;
import co.cask.cdap.template.etl.common.Properties;
import co.cask.cdap.test.AdapterManager;
import co.cask.cdap.test.DataSetManager;
import co.cask.tephra.Transaction;
import co.cask.tephra.TransactionAware;
import co.cask.tephra.TransactionManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.mapreduce.AvroKeyInputFormat;
import org.apache.avro.mapreduce.AvroKeyOutputFormat;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.twill.filesystem.Location;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ETLTPFSTest extends BaseETLBatchTest {

  private static final Gson GSON = new Gson();
  private static Schema EVENT_SCHEMA_WITHOUT_HEADER;
  @Test
  public void testAvroSourceConversionToAvroSink() throws Exception {

    Schema schema = Schema.recordOf(
      "record",
      Schema.Field.of("int", Schema.of(Schema.Type.INT)));
    EVENT_SCHEMA_WITHOUT_HEADER = schema;

    org.apache.avro.Schema avroSchema = convertSchema(schema);

    GenericRecord record = new GenericRecordBuilder(avroSchema)
      .set("int", Integer.MAX_VALUE)
      .build();

    String filesetName = "tpfs";
    DatasetAdmin datasetAdmin = addDatasetInstance("timePartitionedFileSet", filesetName, FileSetProperties.builder()
      .setInputFormat(AvroKeyInputFormat.class)
      .setOutputFormat(AvroKeyOutputFormat.class)
      .setInputProperty("schema", avroSchema.toString())
      .setOutputProperty("schema", avroSchema.toString())
      .setEnableExploreOnCreate(true)
      .setSerDe("org.apache.hadoop.hive.serde2.avro.AvroSerDe")
      .setExploreInputFormat("org.apache.hadoop.hive.ql.io.avro.AvroContainerInputFormat")
      .setExploreOutputFormat("org.apache.hadoop.hive.ql.io.avro.AvroContainerOutputFormat")
      .setTableProperty("avro.schema.literal", (avroSchema.toString()))
      .build());
    DataSetManager<TimePartitionedFileSet> fileSetManager = getDataset(filesetName);
    TimePartitionedFileSet tpfs = fileSetManager.get();

    TransactionManager txService = getTxService();
    Transaction tx1 = txService.startShort(100);
    TransactionAware txTpfs = (TransactionAware) tpfs;
    txTpfs.startTx(tx1);

    fileSetManager.get().addPartition(System.currentTimeMillis(), "directory", ImmutableMap.of("key1", "value1"));
    Location location = fileSetManager.get().getPartitionByTime(System.currentTimeMillis()).getLocation();
    location = location.append("file.avro");
    FSDataOutputStream outputStream = new FSDataOutputStream(location.getOutputStream(), null);
    DataFileWriter dataFileWriter = new DataFileWriter<>(new GenericDatumWriter<GenericRecord>(avroSchema));
    dataFileWriter.create(avroSchema, outputStream);
    dataFileWriter.append(record);
    dataFileWriter.flush();

    txTpfs.commitTx();
    txService.canCommit(tx1, txTpfs.getTxChanges());
    txService.commit(tx1);
    txTpfs.postTxCommit();

    String newFilesetName = filesetName + "_op";
    ETLBatchConfig etlBatchConfig = constructTPFSETLConfig(filesetName, newFilesetName);

    AdapterConfig newAdapterConfig = new AdapterConfig("description", TEMPLATE_ID.getId(),
                                                       GSON.toJsonTree(etlBatchConfig));
    Id.Adapter newAdapterId = Id.Adapter.from(NAMESPACE, "sconversion1");
    AdapterManager tpfsAdapterManager = createAdapter(newAdapterId, newAdapterConfig);

    tpfsAdapterManager.start();
    tpfsAdapterManager.waitForOneRunToFinish(4, TimeUnit.MINUTES);
    tpfsAdapterManager.stop();

    DataSetManager<TimePartitionedFileSet> newFileSetManager = getDataset(newFilesetName);
    TimePartitionedFileSet newFileSet = newFileSetManager.get();

    List<GenericRecord> newRecords = readOutput(newFileSet, EVENT_SCHEMA_WITHOUT_HEADER);
    Assert.assertEquals(1, newRecords.size());
  }

  private org.apache.avro.Schema convertSchema(Schema cdapSchema) {
    return new org.apache.avro.Schema.Parser().parse(cdapSchema.toString());
  }

  private ETLBatchConfig constructTPFSETLConfig(String filesetName, String newFilesetName) {
    ETLStage source = new ETLStage("TPFSAvro",
                                   ImmutableMap.of(Properties.TimePartitionedFileSetDataset.SCHEMA,
                                                   EVENT_SCHEMA_WITHOUT_HEADER.toString(),
                                                   Properties.TimePartitionedFileSetDataset.TPFS_NAME, filesetName,
                                                   Properties.TimePartitionedFileSetDataset.DELAY, "0d",
                                                   Properties.TimePartitionedFileSetDataset.DURATION, "10m"));
    ETLStage sink = new ETLStage("TPFSAvro",
                                 ImmutableMap.of(Properties.TimePartitionedFileSetDataset.SCHEMA,
                                                 EVENT_SCHEMA_WITHOUT_HEADER.toString(),
                                                 Properties.TimePartitionedFileSetDataset.TPFS_NAME,
                                                 newFilesetName));

    // The header field has been dropped because it contains maps. This map when parsed by the Avro Source which follows
    // leads to an exception because the "values" field precedes the "keys" and the parser thinks that "keys" is not
    // present. Once CDAP-2813 is resolved, we can stop dropping headers.
    ETLStage transform = new ETLStage("Projection", ImmutableMap.<String, String>of());
    return new ETLBatchConfig("* * * * *", source, sink, Lists.newArrayList(transform));
  }
}
