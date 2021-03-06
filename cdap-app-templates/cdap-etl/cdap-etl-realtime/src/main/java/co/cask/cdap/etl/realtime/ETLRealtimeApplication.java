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

package co.cask.cdap.etl.realtime;

import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.etl.realtime.config.ETLRealtimeConfig;

/**
 * ETL Realtime Application.
 */
public class ETLRealtimeApplication extends AbstractApplication<ETLRealtimeConfig> {
  public static final String STATE_TABLE = "etlrealtimesourcestate";
  public static final String DEFAULT_DESCRIPTION = "Extract-Transform-Load (ETL) Real-time Application";

  @Override
  public void configure() {
    ETLRealtimeConfig config = getConfig();
    setDescription(DEFAULT_DESCRIPTION);
    addWorker(new ETLWorker(config));
    createDataset(STATE_TABLE, KeyValueTable.class, DatasetProperties.EMPTY);
  }

}
