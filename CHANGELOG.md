# Changelog

## [4.2.0] (2023-07-19)

### Changed 
* [Android] Not sending error if we try to open the camera when its already started.
* [Android] Not sending error if we try to stop the camera when its already stopped.
* [iOS] Not sending error if we try to open the camera when its already started.

## [4.1.5] (2023-03-03)

### Fixed 
* [Android] Fix when tapping too fast in change orientation button it crashed the app.

## [4.1.4] (2023-02-20)

### Fixed 
* [Android] Fix camera not showing when returning from background and closing and opening it again.

## [4.1.3] (2023-02-20)
### Changed 
* [iOS] position (x,y) now its ignored in iOS devices and its automatically centered in the middle of the screen

## [4.1.0] (2023-02-13)
### Changed 
* Added to @cotecna NPM as a public library
### Fixed 
* [Android] Fix image gets rotated on portrait mode
* [Android] Flash Mode On now triggers the lantern of the phone to fix the flash not appearing on some devices
* [iOS] Fix camera closed when selecting image from gallery

## [4.0.0](https://github.com/capacitor-community/camera-preview/compare/v3.1.2...v4.0.0) (2022-09-20)

### Changed
* Capacitor 4 is **required**. Thanks to @rdlabo for the patch and @EinfachHans for testing.

## [3.1.2](https://github.com/capacitor-community/camera-preview/compare/v3.1.1...v3.1.2) (2022-08-29)

### Fixed 
* [Android] preview not resized and displayed correctly when orientation changes by @ryaa in https://github.com/capacitor-community/camera-preview/pull/238

## [3.1.1](https://github.com/capacitor-community/camera-preview/compare/v3.1.0...v3.1.1) (2022-08-13)

### Added
* feat: respect width, height and quality options on the web implementation by @julian-baumann in https://github.com/capacitor-community/camera-preview/pull/231

### Changed
* chore(deps): bump eventsource from 1.0.7 to 1.1.1 in /demo by @dependabot in https://github.com/capacitor-community/camera-preview/pull/226
* chore(deps): bump terser from 4.8.0 to 4.8.1 in /demo by @dependabot in https://github.com/capacitor-community/camera-preview/pull/239

### Fixed
* bugfix: fixed resource android:attr/lStar not found build error by @ryaa in https://github.com/capacitor-community/camera-preview/pull/229
* Fix wrong return type on `flip` and `setFlashMode` by @diesieben07 in https://github.com/capacitor-community/camera-preview/pull/234
* doc: update the web instructions by @riderx in https://github.com/capacitor-community/camera-preview/pull/245
* bugfix: fixed the problem when photos taken in the landscape orientation are off by @ryaa in https://github.com/capacitor-community/camera-preview/pull/235

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