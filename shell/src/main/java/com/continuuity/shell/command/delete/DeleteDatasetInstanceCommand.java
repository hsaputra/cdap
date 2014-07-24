/*
 * Copyright 2014 Continuuity, Inc.
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

package com.continuuity.shell.command.delete;

import com.continuuity.client.DatasetClient;
import com.continuuity.shell.ElementType;
import com.continuuity.shell.command.AbstractCommand;
import com.continuuity.shell.completer.Completable;
import com.continuuity.shell.completer.reactor.DatasetNameCompleter;
import com.google.common.collect.Lists;
import jline.console.completer.Completer;

import java.io.PrintStream;
import java.util.List;
import javax.inject.Inject;

/**
 * Deletes a dataset.
 */
public class DeleteDatasetInstanceCommand extends AbstractCommand implements Completable {

  private final DatasetClient datasetClient;
  private final DatasetNameCompleter completer;

  @Inject
  public DeleteDatasetInstanceCommand(DatasetNameCompleter completer,
                                      DatasetClient datasetClient) {
    super("instance", "<dataset-name>", "Deletes a " + ElementType.DATASET.getPrettyName());
    this.completer = completer;
    this.datasetClient = datasetClient;
  }

  @Override
  public void process(String[] args, PrintStream output) throws Exception {
    super.process(args, output);

    String datasetName = args[0];
    datasetClient.delete(datasetName);
  }

  @Override
  public List<? extends Completer> getCompleters(String prefix) {
    return Lists.newArrayList(prefixCompleter(prefix, completer));
  }
}
