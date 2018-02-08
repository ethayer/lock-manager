var gulp = require('gulp'),
  gulpMerge = require('gulp-merge'),
  concat = require('gulp-concat');

gulp.task('default', function () {
  return gulpMerge(
      gulp.src([
        'source/main.groovy',
        'source/lock.groovy',
        // 'source/user.groovy',
        // 'source/keypad.groovy',
        // 'source/api.groovy',
      ])
    )
    .pipe(concat('new-lock-manager.groovy'))
    .pipe(gulp.dest('smartapps/ethayer/new-lock-manager.src/'));
});
