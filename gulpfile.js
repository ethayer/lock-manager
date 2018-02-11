var gulp = require('gulp'),
  gulpMerge = require('gulp-merge'),
  concat = require('gulp-concat'),
  watch = require('gulp-watch');

gulp.task('concat', function () {
  return gulpMerge(
      gulp.src([
        'source/main.groovy',
        'source/lock.groovy',
        'source/user.groovy',
        'source/keypad.groovy',
        'source/api.groovy'
      ])
    )
    .pipe(concat('new-lock-manager.groovy'))
    .pipe(gulp.dest('smartapps/ethayer/new-lock-manager.src/'));
});

// Watch Files For Changes
gulp.task('watch', function() {
    gulp.watch('source/*.groovy', ['concat']);
    // prevent stupid edits
    gulp.watch('smartapps/ethayer/new-lock-manager.src/*.groovy', ['concat']);
});

gulp.task('default', ['concat', 'watch']);
