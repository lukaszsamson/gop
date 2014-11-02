'use strict';

angular.module('chooseApp')
  .factory('imageService', function () {
      function resize(file, width, height) {
        var MAX_WIDTH = 400;
        var MAX_HEIGHT = 300;
        var fileDataUrl = file.getAsDataURL();

        var tempW = fileDataUrl.width;
        var tempH = fileDataUrl.height;
        if (tempW > tempH) {
            if (tempW > MAX_WIDTH) {
                tempH *= MAX_WIDTH / tempW;
                tempW = MAX_WIDTH;
            }
        } else {
            if (tempH > MAX_HEIGHT) {
                tempW *= MAX_HEIGHT / tempH;
                tempH = MAX_HEIGHT;
            }
        }

        var canvas = document.createElement('canvas');
        canvas.width = tempW;
        canvas.height = tempH;
        var ctx = canvas.getContext("2d");

        try {
            ctx.drawImage(this, 0, 0, tempW, tempH);
        } catch (e) {
            return false;
        }
        var dataURL = canvas.toDataURL("image/jpeg");

        return data = 'image=' + dataURL;
      }

      return {
          resize: function (file, width, height) {
              return resize(file, width, height);
          }
      };
  });


