'use strict';

angular.module('chooseApp')
  .controller('AskCtrl', ['$scope', '$upload', '$location', 'fileUpload', '$http', function ($scope, $upload, $location, fileUpload, $http) {
    $scope.stateName = 'ask';
    $scope.images = [];
    $scope.ids = [];
    $scope.fr = {};
    $scope.fileType = {};
    $scope.resultId = 18;
 
    $scope.onFileSelect = function ($files) {
        var file = $files[0];

        $scope.fr = new FileReader();
        $scope.fr.onload = convertToImage;
        $scope.fr.readAsBinaryString(file);
        $scope.fileType = file.type;

        $scope.upload = $upload.upload({
            url: API + '/picks', 
            file: file,
        }).progress(function (evt) {
            console.log('percent: ' + parseInt(100.0 * evt.loaded / evt.total));
        }).success(function (data, status, headers, config) {
            $scope.ids.push(data.pickId);
        });
    };

    $scope.ask = function () {
        $http.post(API + '/questions', {
            pick1Id: $scope.ids[0],
            pick2Id: $scope.ids[1]
        }).success(function (data) {
            $scope.resultId = data.questionId;
            $location.path("/results/" + $scope.resultId);
        });
    };

    function convertToImage() {
        var data = "data:" + $scope.fileType + ";base64," + btoa($scope.fr.result);
        $scope.fileType = {};
        $scope.images.push(data);
    }
  }]);
