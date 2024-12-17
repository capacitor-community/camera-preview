# Changelog

## [v6.0.1](https://github.com/capacitor-community/camera-preview/compare/v6.0.0...v6.0.1) (2024-12-17)

### BREAKING CHANGES
- The minimal required Node version is now 18.0.0

### Features

- [FEATURE] add feature to check or detect when camera has been started ([#357](https://github.com/capacitor-community/camera-preview/pull/357)), ([f958173](https://github.com/capacitor-community/camera-preview/commit/f95817359906657d005e47c21b98006829206a3f)), closes [#356](https://github.com/capacitor-community/camera-preview/issues/356)

### Chores

- chore: update dependencies and eslint fixes ([#358](https://github.com/capacitor-community/camera-preview/pull/358)), ([871b115](https://github.com/capacitor-community/camera-preview/commit/871b115652ed9ebc8f9849693d7d11c2bab04c3b))

## [v6.0.0](https://github.com/capacitor-community/camera-preview/compare/v5.0.0...v6.0.0) (2024-05-06)

### Chores

- feat: Capacitor 6 Support ([#332](https://github.com/capacitor-community/camera-preview/pull/332)), ([0356db0](https://github.com/capacitor-community/camera-preview/commit/0356db04fb9dcc7f257028ca1b5134a7a21c6dd3)), closes [#330](https://github.com/capacitor-community/camera-preview/issues/330)

## [5.0.0](https://github.com/capacitor-community/camera-preview/compare/v4.0.0...v5.0.0) (2022-06-09)

The plugin has been updated to Capacitor 5, it's not compatible with older versions of Capacitor, see README.md for information about what versions of the plugin to use in previous Capacitor releases.

### Fixes

* [fix(web): Remove deprecated registerWebPlugin (](https://github.com/capacitor-community/camera-preview/commit/1bcb0e38cb63aafe0ba56e67e89ee09ab4582468)https://github.com/capacitor-community/camera-preview/pull/288[)](https://github.com/capacitor-community/camera-preview/commit/1bcb0e38cb63aafe0ba56e67e89ee09ab4582468)
* [fix: Add types for startRecordVideo and stopRecordVideo (](https://github.com/capacitor-community/camera-preview/commit/2fdbc4b881e7a7858679f460457276fd68915e46)https://github.com/capacitor-community/camera-preview/pull/295[)](https://github.com/capacitor-community/camera-preview/commit/2fdbc4b881e7a7858679f460457276fd68915e46)

### Features

* [feat!: Add Capacitor 5 support (](https://github.com/capacitor-community/camera-preview/commit/9de932700b89ccbb3dba22f75fc49f3f8243f4b4)https://github.com/capacitor-community/camera-preview/pull/283[)](https://github.com/capacitor-community/camera-preview/commit/9de932700b89ccbb3dba22f75fc49f3f8243f4b4)
* [feat(android): Add androidxExifInterfaceVersion variable (](https://github.com/capacitor-community/camera-preview/commit/1c18015dd347cbdcb20f3fef3d9f0f264d9d633f)https://github.com/capacitor-community/camera-preview/pull/291[)](https://github.com/capacitor-community/camera-preview/commit/1c18015dd347cbdcb20f3fef3d9f0f264d9d633f)
* [feat: add commonjs output format (](https://github.com/capacitor-community/camera-preview/commit/a88351453b4298278156751df8fb0fa9807d5e4d)https://github.com/capacitor-community/camera-preview/pull/287[)](https://github.com/capacitor-community/camera-preview/commit/a88351453b4298278156751df8fb0fa9807d5e4d)



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
