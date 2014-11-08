'use strict';

angular.module('chooseApp')
  .controller('ResultsCtrl', ['$scope', '$http', '$timeout', '$routeParams', '$location' , 'imageService', function ($scope, $http, $timeout, $routeParams, $location, $stateParams, imageService) {
      $scope.API = API;
      $scope.stateName = 'results';
      $scope.noVotes = false;
      $scope.listView = false;

      $scope.getQuestionResults = function (id) {
          if (typeof ($routeParams.questionId) != 'undefined') {
              $http.get(API + '/questions/' + $routeParams.questionId).success(function (data) {
                  $scope.results = data;
                  $scope.chartData = [{
                      value: data.pick1Result.votesCount,
                      label: data.pick1Result.pickId,
                      color: "#F7464A",
                      highlight: "#FF5A5E",
                  },
                  {
                      value: data.pick2Result.votesCount,
                      label: data.pick2Result.pickId,
                      color: "#46BFBD",
                      highlight: "#5AD3D1",
                  }];
                  if (data.pick2Result.votesCount + data.pick1Result.votesCount == 0) {
                      $scope.noVotes = true;
                  }

                  if (data.pick2Result.votesCount > data.pick1Result.votesCount) {
                      $scope.winner = data.pick2Result.pickId;
                  } else {
                      $scope.winner = data.pick1Result.pickId;
                  }
                  $scope.winner1 = $scope.winner == data.pick1Result.pickId;
                  $scope.winner2 = $scope.winner == data.pick2Result.pickId;

                  $timeout($scope.getQuestionResults, 3000);
              });
          }
          else {
              $scope.listView = true;
          }
      };

      $scope.chartOptions = {};

      $scope.getQuestionResults();

  }]);
