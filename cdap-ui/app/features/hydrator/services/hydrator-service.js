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

class HydratorService {
  constructor(GLOBALS, MyDAGFactory, uuid, $state, $rootScope, myPipelineApi, $q, ConfigStore, IMPLICIT_SCHEMA, NodesStore) {
    this.GLOBALS = GLOBALS;
    this.MyDAGFactory = MyDAGFactory;
    this.uuid = uuid;
    this.$state = $state;
    this.$rootScope = $rootScope;
    this.myPipelineApi = myPipelineApi;
    this.$q = $q;
    this.ConfigStore = ConfigStore;
    this.IMPLICIT_SCHEMA = IMPLICIT_SCHEMA;
    this.NodesStore = NodesStore;
  }

  getNodesAndConnectionsFromConfig(pipeline) {
    let nodes = [];
    let connections = [];

    let artifact = this.GLOBALS.pluginTypes[pipeline.artifact.name];

    let source = angular.copy(pipeline.config.source);
    let transforms = angular.copy(pipeline.config.transforms)
      .map( node => {
        node.type = artifact.transform;
        node.label = node.label || node.name;
        node.icon = this.MyDAGFactory.getIcon(node.name);
        return node;
      });
    let sinks = angular.copy(pipeline.config.sinks)
      .map( node => {
        node.type = artifact.sink;
        node.icon = this.MyDAGFactory.getIcon(node.name);
        return node;
      });

    source.type = artifact.source;
    source.icon = this.MyDAGFactory.getIcon(source.name);
    // replace with backend id
    nodes.push(source);
    nodes = nodes.concat(transforms);
    nodes = nodes.concat(sinks);

    connections = pipeline.config.connections;

    // Obtaining layout of graph with Dagre
    var graph = this.MyDAGFactory.getGraphLayout(nodes, connections);
    angular.forEach(nodes, function (node) {
      node._uiPosition = {
        'top': graph._nodes[node.name].y + 'px' ,
        'left': graph._nodes[node.name].x + 'px'
      };
    });

    return {
      nodes: nodes,
      connections: connections
    };
  }

  fetchBackendProperties(node, appType) {
    var defer = this.$q.defer();

    // This needs to pass on a scope always. Right now there is no cleanup
    // happening
    var params = {
      namespace: this.$state.params.namespace,
      pipelineType: appType || this.ConfigStore.getAppType(),
      version: (node.artifact && node.artifact.version ) || this.$rootScope.cdapVersion,
      extensionType: node.type,
      pluginName: node.plugin.name
    };

    return this.myPipelineApi.fetchPluginProperties(params)
      .$promise
      .then(function(res) {

        var pluginProperties = (res.length? res[0].properties: {});
        if (res.length && (!node.description || (node.description && !node.description.length))) {
          node.description = res[0].description;
        }
        node._backendProperties = pluginProperties;
        defer.resolve(node);
        return defer.promise;
      });
  }

  formatSchema (node) {

    let isStreamSource = node.name === 'Stream';
    let schema;
    let input;
    let jsonSchema;

    if (isStreamSource) {
      if (node.properties.format === 'clf') {
        jsonSchema = this.IMPLICIT_SCHEMA.clf;
      } else if (node.properties.format === 'syslog') {
        jsonSchema = this.IMPLICIT_SCHEMA.syslog;
      } else {
        jsonSchema = node.outputSchema;
      }
    } else {
      jsonSchema = node.outputSchema;
    }

    try {
      input = JSON.parse(jsonSchema);
    } catch (e) {
      input = null;
    }

    if (isStreamSource) {
      // Must be in this order!!
      if (!input) {
        input = {
          fields: [{ name: 'body', type: 'string' }]
        };
      }

      input.fields.unshift({
        name: 'headers',
        type: {
          type: 'map',
          keys: 'string',
          values: 'string'
        }
      });

      input.fields.unshift({
        name: 'ts',
        type: 'long'
      });

    }

    schema = input ? input.fields : null;
    angular.forEach(schema, function (field) {
      if (angular.isArray(field.type)) {
        field.type = field.type[0];
        field.nullable = true;
      } else {
        field.nullable = false;
      }
    });

    return schema;

  }

  generateSchemaOnEdge(sourceId) {
    var nodes = this.NodesStore.getNodes();
    var sourceNode;

    for (var i = 0; i<nodes.length; i++) {
      if (nodes[i].id === sourceId) {
        sourceNode = nodes[i];
        break;
      }
    }

    return this.formatSchema(sourceNode);
  }

}
HydratorService.$inject = ['GLOBALS', 'MyDAGFactory', 'uuid', '$state', '$rootScope', 'myPipelineApi', '$q', 'ConfigStore', 'IMPLICIT_SCHEMA', 'NodesStore'];
angular.module(`${PKG.name}.feature.hydrator`)
  .service('HydratorService', HydratorService);
