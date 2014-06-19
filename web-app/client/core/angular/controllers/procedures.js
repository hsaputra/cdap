'use strict';

define(function () {

  /* Items */

  var Ctrl = ['$rootScope', '$scope', '$http', '$routeParams', '$interval',
    function($rootScope, $scope, $http, $routeParams, $interval) {

    $scope.message = "procedures";

    var ival = $interval(function() {
      $.get('/procedures');
    }, 1000);

    $scope.$on("$destroy", function(){
      if (intervals) {
        helpers.cancelAllIntervals($interval, intervals);  
      }
    });

  }];

  return Ctrl;

});