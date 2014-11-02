'use strict';

angular.module('chooseApp')
  .factory('fileUpload', ['$http', function ($http) {
      var uploadApiUrl = '/Choose.Api/file/upload'

      return {
          upload: function (file) {
              var xhr = new XMLHttpRequest();
              xhr.open('POST', uploadApiUrl, true);
              var fd = new FormData();
              fd.append('filename', file);
              xhr.send(fd);

              xhr.onreadystatechange = function () {
                  if (xhr.readyState == 4 && xhr.status == 200) {
                      return xhr.responseText;
                  }
              }
          }
      }
  }]);
