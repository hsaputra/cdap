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

package co.cask.cdap.data2.datafabric.dataset.service;

import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.lib.CloseableIterator;
import co.cask.cdap.api.dataset.table.Table;
import co.cask.cdap.common.BadRequestException;
import co.cask.cdap.common.DatasetAlreadyExistsException;
import co.cask.cdap.common.DatasetTypeNotFoundException;
import co.cask.cdap.common.HandlerException;
import co.cask.cdap.common.NotFoundException;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.data2.datafabric.dataset.service.executor.DatasetAdminOpResponse;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.DatasetManagementException;
import co.cask.cdap.data2.transaction.queue.QueueConstants;
import co.cask.cdap.proto.DatasetInstanceConfiguration;
import co.cask.cdap.proto.DatasetMeta;
import co.cask.cdap.proto.DatasetSpecificationSummary;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.dataset.DatasetMethodRequest;
import co.cask.cdap.proto.dataset.DatasetMethodResponse;
import co.cask.http.AbstractHttpHandler;
import co.cask.http.HttpResponder;
import co.cask.tephra.TransactionAware;
import co.cask.tephra.TransactionExecutor;
import co.cask.tephra.TransactionExecutorFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.inject.Inject;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * Handles dataset instance management calls.
 */
// todo: do we want to make it authenticated? or do we treat it always as "internal" piece?
@Path(Constants.Gateway.API_VERSION_3 + "/namespaces/{namespace-id}")
public class DatasetInstanceHandler extends AbstractHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetInstanceHandler.class);
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(DatasetSpecification.class, new DatasetSpecificationAdapter())
    .create();

  private final DatasetInstanceService instanceService;
  private final DatasetFramework framework;
  private final TransactionExecutorFactory txFactory;

  @Inject
  public DatasetInstanceHandler(DatasetInstanceService instanceService, DatasetFramework framework,
                                TransactionExecutorFactory txFactory) {
    this.instanceService = instanceService;
    this.framework = framework;
    this.txFactory = txFactory;
  }

  @GET
  @Path("/data/datasets/")
  public void list(HttpRequest request, HttpResponder responder,
                   @PathParam("namespace-id") String namespaceId) throws Exception {
    responder.sendJson(HttpResponseStatus.OK, spec2Summary(instanceService.list(Id.Namespace.from(namespaceId))));
  }

  /**
   * Gets the {@link DatasetMeta} for a dataset instance.
   *
   * @param namespaceId namespace of the dataset instance
   * @param name name of the dataset instance
   * @param owners a list of owners of the dataset instance, in the form @{code <type>::<id>}
   *               (e.g. "program::namespace:default/application:PurchaseHistory/program:flow:PurchaseFlow")
   * @throws NotFoundException if the dataset instance was not found
   */
  @GET
  @Path("/data/datasets/{name}")
  public void get(HttpRequest request, HttpResponder responder,
                  @PathParam("namespace-id") String namespaceId,
                  @PathParam("name") String name,
                  @QueryParam("owner") List<String> owners) throws Exception {
    Id.DatasetInstance instance = Id.DatasetInstance.from(namespaceId, name);
    responder.sendJson(HttpResponseStatus.OK,
                       instanceService.get(instance, strings2Ids(owners)),
                       DatasetMeta.class, GSON);
  }

  /**
   * Creates a new dataset instance.
   *
   * @param namespaceId namespace of the new dataset instance
   * @param name name of the new dataset instance
   */
  @PUT
  @Path("/data/datasets/{name}")
  public void create(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                     @PathParam("name") String name) throws Exception {
    DatasetInstanceConfiguration creationProperties = getInstanceConfiguration(request);
    Id.Namespace namespace = Id.Namespace.from(namespaceId);

    LOG.info("Creating dataset {}.{}, type name: {}, typeAndProps: {}",
             namespaceId, name, creationProperties.getTypeName(), creationProperties.getProperties());
    try {
      instanceService.create(namespace, name, creationProperties);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (DatasetAlreadyExistsException e) {
      responder.sendString(HttpResponseStatus.CONFLICT, e.getMessage());
    } catch (DatasetTypeNotFoundException e) {
      responder.sendString(HttpResponseStatus.NOT_FOUND, e.getMessage());
    } catch (HandlerException e) {
      responder.sendString(e.getFailureStatus(), e.getMessage());
    }
  }

  /**
   * Updates an existing dataset specification properties.
   *
   * @param namespaceId namespace of the dataset instance
   * @param name name of the dataset instance
   * @throws Exception
   */
  @PUT
  @Path("/data/datasets/{name}/properties")
  public void update(HttpRequest request, HttpResponder responder,
                     @PathParam("namespace-id") String namespaceId,
                     @PathParam("name") String name) throws Exception {
    Id.DatasetInstance instance = Id.DatasetInstance.from(namespaceId, name);
    Map<String, String> properties = getProperties(request);

    LOG.info("Update dataset {}, type name: {}, props: {}", name, GSON.toJson(properties));
    instanceService.update(instance, properties);
    responder.sendStatus(HttpResponseStatus.OK);
  }

  /**
   * Deletes a dataset instance, which also deletes the data owned by it.
   *
   * @param namespaceId namespace of the dataset instance
   * @param name name of the dataset instance
   * @throws Exception
   */
  @DELETE
  @Path("/data/datasets/{name}")
  public void drop(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                   @PathParam("name") String name) throws Exception {
    LOG.info("Deleting dataset {}.{}", namespaceId, name);
    Id.DatasetInstance instance = Id.DatasetInstance.from(namespaceId, name);
    instanceService.drop(instance);
    responder.sendStatus(HttpResponseStatus.OK);
  }

  /**
   * Executes an admin operation on a dataset instance.
   *
   * @param namespaceId namespace of the dataset instance
   * @param name name of the dataset instance
   * @param method the admin operation to execute (e.g. "exists", "truncate", "upgrade")
   * @throws Exception
   */
  @POST
  @Path("/data/datasets/{name}/admin/{method}")
  public void executeAdmin(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                           @PathParam("name") String name,
                           @PathParam("method") String method) throws Exception {
    Id.DatasetInstance instance = Id.DatasetInstance.from(namespaceId, name);
    try {
      DatasetAdminOpResponse response = instanceService.executeAdmin(instance, method);
      responder.sendJson(HttpResponseStatus.OK, response);
    } catch (HandlerException e) {
      responder.sendStatus(e.getFailureStatus());
    }
  }

  /**
   * Executes a data operation on a dataset instance using reflection.
   *
   * @param namespaceId namespace of the dataset instance
   * @param name name of the dataset instance
   */
  @POST
  @Path("/data/datasets/{name}/execute")
  public void executeDataOpWithReflection(
    HttpRequest request, HttpResponder responder,
    @PathParam("namespace-id") String namespaceId,
    @PathParam("name") String name) throws Throwable {

    Id.DatasetInstance instance = Id.DatasetInstance.from(namespaceId, name);
    Dataset dataset;
    try {
      // TODO: use proper classloader
      dataset = framework.getDataset(instance, DatasetDefinition.NO_ARGUMENTS, null);
    } catch (DatasetManagementException | IOException e) {
      LOG.error("Error getting dataset {}", name, e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
      return;
    }

    if (dataset == null) {
      throw new NotFoundException(instance);
    }

    try (Reader reader = new InputStreamReader(new ChannelBufferInputStream(request.getContent()))) {
      final DatasetMethodRequest methodRequest = GSON.fromJson(reader, DatasetMethodRequest.class);
      final ClassLoader cl = getClass().getClassLoader();

      final MethodHandle handle;
      try {
        MethodType type = MethodType.methodType(
          methodRequest.getReturnTypeClass(cl), methodRequest.getArgumentClasses(cl));
        handle = MethodHandles.lookup().findVirtual(dataset.getClass(), methodRequest.getMethod(), type);
      } catch (NoSuchMethodException e) {
        LOG.error("Error finding method", e);
        throw new BadRequestException(
          String.format("Method %s with return type %s and arguments %s does not exist for dataset type %s",
                        methodRequest.getMethod(), methodRequest.getReturnType(),
                        methodRequest.getArgumentTypes(), dataset.getClass().getName()), e);
      }

      Object response = txFactory.createExecutor(ImmutableList.of((TransactionAware) dataset))
        .execute(
          new TransactionExecutor.Function<Dataset, Object>() {
            @Override
            public Object apply(Dataset o) throws Exception {
              try {
                // TODO: allow nullable arguments
                return handle.invokeWithArguments(
                  Lists.newArrayList(
                    Iterables.concat(
                      ImmutableList.<Object>of(o),
                      methodRequest.getArgumentList(GSON, cl)
                    )
                  ));
              } catch (Throwable t) {
                // TODO: exception handling
                throw Throwables.propagate(t);
              }
            }
          }, dataset);

      if (response instanceof Iterator) {
        // transform response from iterator to list, for GSON serialization
        // TODO: limit
        Iterator<?> it = (Iterator<?>) response;
        try {
          List<Object> newResponse = new ArrayList<>();
          while (it.hasNext()) {
            newResponse.add(it.next());
          }
          response = newResponse;
        } finally {
          if (it instanceof CloseableIterator) {
            ((CloseableIterator) it).close();
          }
        }
      }

      DatasetMethodResponse methodResponse = new DatasetMethodResponse(response);
      responder.sendJson(HttpResponseStatus.OK, methodResponse, methodResponse.getClass(), GSON);
    } catch (ClassNotFoundException e) {
      throw new BadRequestException(String.format("Class not found: %s", e.getMessage()), e);
    } catch (IOException e) {
      LOG.error("Failed to read request body", e);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

  }

  /**
   * Executes a data operation on a dataset instance. Not yet implemented.
   *
   * @param namespaceId namespace of the dataset instance
   * @param name name of the dataset instance
   * @param method the data operation to execute
   */
  @POST
  @Path("/data/datasets/{name}/data/{method}")
  public void executeDataOp(HttpRequest request, HttpResponder responder, @PathParam("namespace-id") String namespaceId,
                            @PathParam("name") String name, @PathParam("method") String method) {
    // todo: execute data operation
    responder.sendStatus(HttpResponseStatus.NOT_IMPLEMENTED);
  }

  private List<? extends Id> strings2Ids(List<String> strings) {
    return Lists.transform(strings, new Function<String, Id>() {
      @Nullable
      @Override
      public Id apply(@Nullable String input) {
        if (input == null) {
          return null;
        }

        String[] parts = input.split("::", 2);
        Preconditions.checkArgument(parts.length == 2);
        String ownerType = parts[0];
        String ownerId = parts[1];
        if (ownerType.equals(Id.getType(Id.Program.class))) {
          return Id.Program.fromStrings(ownerId.split("/"));
        } else {
          return null;
        }
      }
    });
  }

  private Collection<DatasetSpecificationSummary> spec2Summary(Collection<DatasetSpecification> specs) {
    List<DatasetSpecificationSummary> datasetSummaries = Lists.newArrayList();
    for (DatasetSpecification spec : specs) {
      // TODO: (CDAP-3097) handle system datasets specially within a namespace instead of filtering them out
      // by the handler. This filter is only in the list endpoint because the other endpoints are used by
      // HBaseQueueAdmin through DatasetFramework.
      if (QueueConstants.STATE_STORE_NAME.equals(spec.getName())) {
        continue;
      }
      datasetSummaries.add(new DatasetSpecificationSummary(spec.getName(), spec.getType(), spec.getProperties()));
    }
    return datasetSummaries;
  }

  private DatasetInstanceConfiguration  getInstanceConfiguration(HttpRequest request) {
    Reader reader = new InputStreamReader(new ChannelBufferInputStream(request.getContent()), Charsets.UTF_8);
    DatasetInstanceConfiguration creationProperties = GSON.fromJson(reader, DatasetInstanceConfiguration.class);
    fixProperties(creationProperties.getProperties());
    return creationProperties;
  }

  private Map<String, String> getProperties(HttpRequest request) {
    Reader reader = new InputStreamReader(new ChannelBufferInputStream(request.getContent()), Charsets.UTF_8);
    Map<String, String> properties = GSON.fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
    fixProperties(properties);
    return properties;
  }

  private void fixProperties(Map<String, String> properties) {
    if (properties.containsKey(Table.PROPERTY_TTL)) {
      long ttl = TimeUnit.SECONDS.toMillis(Long.parseLong(properties.get(Table.PROPERTY_TTL)));
      properties.put(Table.PROPERTY_TTL, String.valueOf(ttl));
    }
  }

  /**
   * Adapter for {@link DatasetSpecification}
   */
  private static final class DatasetSpecificationAdapter implements JsonSerializer<DatasetSpecification> {

    private static final Type MAP_STRING_STRING_TYPE = new TypeToken<SortedMap<String, String>>() { }.getType();
    private static final Maps.EntryTransformer<String, String, String> TRANSFORM_DATASET_PROPERTIES =
      new Maps.EntryTransformer<String, String, String>() {
        @Override
        public String transformEntry(String key, String value) {
          if (key.equals(Table.PROPERTY_TTL)) {
            return String.valueOf(TimeUnit.MILLISECONDS.toSeconds(Long.parseLong(value)));
          } else {
            return value;
          }
        }
      };

    @Override
    public JsonElement serialize(DatasetSpecification src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject jsonObject = new JsonObject();
      jsonObject.addProperty("name", src.getName());
      jsonObject.addProperty("type", src.getType());
      jsonObject.add("properties", context.serialize(Maps.transformEntries(src.getProperties(),
                                                     TRANSFORM_DATASET_PROPERTIES), MAP_STRING_STRING_TYPE));
      Type specsType = new TypeToken<SortedMap<String, DatasetSpecification>>() { }.getType();
      jsonObject.add("datasetSpecs", context.serialize(src.getSpecifications(), specsType));
      return jsonObject;
    }
  }
}
