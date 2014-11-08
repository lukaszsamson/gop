'use strict';

angular.module('chooseApp')
  .controller('PickCtrl', ['$scope', 'imageService', '$http', function ($scope, imageService, $http) {
      $scope.stateName = 'pick';
      $scope.picks = [];
      $scope.API = window.API;
      $scope.questionId = 0;
$scope.liquidConfigurations =
  [
    {fill: true, horizontalAlign: "center", verticalAlign: "top"},
    {fill: false, horizontalAlign: "center", verticalAlign: "50%"},
    {fill: true, horizontalAlign: "50%", verticalAlign: "top"},
    {fill: false, horizontalAlign: "50%", verticalAlign: "bottom"},
    undefined
  ];

function getNext() {
    $http.get(API + '/questions/next')
      .success(function (data) {
          var p = [];
          if (data && data.pick1Id && data.pick2Id) {
              p.push(data.pick1Id);
              p.push(data.pick2Id);
              $scope.questionId = data.questionId;
          } else {
              $scope.questionId = null;
          }
          $scope.picks = p;
      });
}
getNext();
$scope.select = function (pickId) {
          $http.post(API + '/questions/' + $scope.questionId + '/picks/' + pickId + '/vote')
            .success(function (data) {
                getNext();
            });
      };
  }]);
