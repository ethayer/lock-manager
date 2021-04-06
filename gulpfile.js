var gulp = require('gulp'),
  concat = require('gulp-concat');


const { watch } = require('gulp');
  
  gulp.task('concat', function() {
    return gulp.src([
        'source/main.groovy',
        'source/lock.groovy',
        'source/user.groovy',
        'source/keypad.groovy',
        // 'source/api.groovy'
      ])
      .pipe(concat('lock-manager.groovy'))
      .pipe(gulp.dest('./smartapps/ethayer/lock-manager.src/'));
  });

// Watch Files For Changes
exports.default = function() {
  // You can use a single task
  watch('source/*.groovy', gulp.parallel(['concat']))
};


