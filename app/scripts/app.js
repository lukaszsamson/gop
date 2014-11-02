'use strict';

angular.module('chooseApp', [
  'ngCookies',
  'ngResource',
  'ngRoute',
  'angularFileUpload',
        'ngAnimate',
  'tc.chartjs'
])
.config(['$routeProvider', '$locationProvider',
    function ($routeProvider, $locationProvider) {
    $routeProvider
      .when('/ask', {
          templateUrl: 'views/ask.html',
          controller: 'AskCtrl'
      })
          .when("/pick", {
              templateUrl: 'views/pick.html',
              controller: 'PickCtrl'
          }).when('/results', {
              templateUrl: 'views/results.html',
              controller: 'ResultsCtrl'
          })
        .when('/results/:questionId', {
            templateUrl: 'views/results.html',
            controller: 'ResultsCtrl'
        })
    .when('/home', {
        templateUrl: 'views/home.html',
        controller: 'HomeCtrl'
    })
        .when('/', {
            templateUrl: 'views/home.html',
        controller: 'HomeCtrl'
    })
        .otherwise({ redirectTo: '/home' });
}]);
