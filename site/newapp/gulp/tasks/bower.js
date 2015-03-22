var gulp = require('gulp');
var gutil = require('gulp-util');
var source = require('vinyl-source-stream');
var buffer = require('vinyl-buffer');
var bower = require('gulp-bower');
var watchify = require('watchify');
var connect = require('gulp-connect');
var config = require('../config').browserify;

var bundler = (bower(config.src, watchify.args));
//config.settings.transform.forEach(function(t) {
//  bundler.transform(t);
//});

gulp.task('bower', bundle);
bundler.on('update', bundle);

function bundle() {
  return bower()
  // log errors if they happen
  //.on('error', gutil.log.bind(gutil, 'Bower Error'))
  //.pipe(source(config.outputName))
  .pipe(gulp.dest(config.dest))
  .pipe(connect.reload());
}
