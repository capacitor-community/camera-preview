# Changelog

## [3.1.0](https://github.com/capacitor-community/camera-preview/compare/v3.0.0...v3.1.0) (2022-05-27)

### Added

*  Add code formatters. This is an early release and there are issues with the Swift and Typescript formatters. [Can you help?](https://github.com/capacitor-community/camera-preview/issues/209) Thank you to contributor [@pbowyer](https://github.com/pbowyer)! ([#208](https://github.com/capacitor-community/camera-preview/pull/208))

### Changed

* chore(deps): bump async from 2.6.3 to 2.6.4 in /demo ([#217](https://github.com/capacitor-community/camera-preview/pull/217))
* chore(deps): bump minimist from 1.2.5 to 1.2.6 ([#225](https://github.com/capacitor-community/camera-preview/pull/225)) 

### Fixed

* [iOS]  Fix camera display on iOS when rotated after opening in landscape. Thank you to contributor [@mattczech](https://github.com/mattczech) for the patch ([#130](https://github.com/capacitor-community/camera-preview/pull/130)) and [@riderx](https://github.com/riderx) who resolved the merge conflict ([#216](https://github.com/capacitor-community/camera-preview/pull/216)).

* [iOS] Fixed microphone permissions request on iOS. Thank you to contributor [@mstichweh](https://github.com/mstichweh)! ([#219](https://github.com/capacitor-community/camera-preview/pull/219))

* [Android] Fixex prevent camera is not running error. Thank you to contributor [@ryaa](https://github.com/ryaa)! ([#223](https://github.com/capacitor-community/camera-preview/pull/223))

## [3.0.0](https://github.com/capacitor-community/camera-preview/compare/v2.1.0...v3.0.0) (2022-03-27)
The version number has increased in line with Semver as there's one backwards-incompatible change for Android.

### Features

* [Android] Require Gradle 7. Thank you to contributor [@riderx](https://github.com/riderx)! ([#195](https://github.com/capacitor-community/camera-preview/pull/195))

## [2.1.0](https://github.com/capacitor-community/camera-preview/compare/v2.0.0...v2.1.0) (2022-03-06)

### Features

* Add pinch and zoom support on iOS. Thank you to contributors @luckyboykg and @guischulz! ([#204](https://github.com/capacitor-community/camera-preview/pull/204))

### Documentation
* Add info on styling when the camera preview doesn't display because it's behind elements. Thank you to contributor @dhayanth-dharma! 
* Fix deprecated imports in README. Thank you to contributor @koen20!
* Document the iOS-only `enableHighResolution` option. Thank you to contributor @bfinleyui! ([#207](https://github.com/capacitor-community/camera-preview/pull/207))