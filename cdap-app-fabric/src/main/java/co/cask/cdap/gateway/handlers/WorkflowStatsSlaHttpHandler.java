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

package co.cask.cdap.gateway.handlers;

import co.cask.cdap.api.metrics.MetricStore;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.common.BadRequestException;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.utils.TimeMathParser;
import co.cask.cdap.internal.app.store.WorkflowDataset;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramType;
import co.cask.http.AbstractHttpHandler;
import co.cask.http.HttpResponder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * Workflow Statistics Handler
 */
@Singleton
@Path(Constants.Gateway.API_VERSION_3 + "/namespaces/{namespace-id}")
public class WorkflowStatsSLAHttpHandler extends AbstractHttpHandler {

  private static final Logger LOG = LoggerFactory.getLogger(WorkflowStatsSLAHttpHandler.class);
  protected final Store store;
  private final MetricStore metricStore;

  @Inject
  WorkflowStatsSLAHttpHandler(MetricStore metricStore, Store store) {
    this.metricStore = metricStore;
    this.store = store;
  }

  @GET
  @Path("apps/{app-id}/workflows/{workflow-id}/statistics")
  public void workflowStats(HttpRequest request, HttpResponder responder,
                            @PathParam("namespace-id") String namespaceId,
                            @PathParam("app-id") String appId,
                            @PathParam("workflow-id") String workflowId,
                            @QueryParam("start") String start,
                            @QueryParam("end") String end,
                            @QueryParam("percentile") List<Double> percentiles) throws Exception {

    long startTime = TimeMathParser.parseTimeInSeconds(start);
    long endTime = TimeMathParser.parseTimeInSeconds(end);

    if (endTime < startTime || startTime < 0 || endTime < 0) {
      throw new BadRequestException("Wrong start or end time.");
    }

    for (double i : percentiles) {
      if (i < 0 || i > 100) {
        throw new BadRequestException("Percentile values have to be greater than 0 and less than 100");
      }
    }

    WorkflowDataset.BasicStatistics basicStatistics;

    Id.Workflow workflow = Id.Workflow.from(Id.Namespace.from(namespaceId), appId, workflowId);
    try {
      try {
        basicStatistics = store.getWorkflowStatistics(workflow, startTime, endTime, percentiles);

      } catch (IllegalArgumentException exception) {
        responder.sendJson(HttpResponseStatus.BAD_REQUEST, "The input that was provided was not correct");
        return;
      }
    } catch (SecurityException exception) {
      responder.sendJson(HttpResponseStatus.BAD_REQUEST, "You're not allowed to view this");
      return;
    }









    responder.sendJson(HttpResponseStatus.OK, basicStatistics);
  }

}
