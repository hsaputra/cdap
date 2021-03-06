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

angular.module(`${PKG.name}.feature.pins`)
  .config(function($stateProvider) {
    $stateProvider
      .state('pins', {
        url: '/pins',
        abstract: true,
        template: '<div ui-view></div>'
      })
        .state('pins.list', {
          url: '',
          templateUrl: '/assets/features/pins/templates/pins-list.html',
          controller: 'PinsListController',
          controllerAs: 'PinsListController',
          ncyBreadcrumb: {
            label: 'Pins'
          }
        });
  });
