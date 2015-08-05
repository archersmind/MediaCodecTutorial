# MediaCodecTutorial
Sample tutorial of how to use MediaCodec in Anroid

## Before Test

Put your testing clip in the external storage of your target and name it `TestVideo.mp4` like:

```
/storage/sdcard0/TestVideo.mp4
```

## How to build

Thanks `gradlew` all we have to do to build this application is:

On *Linux* Host, and for a debug version of this `apk`
```
$cd <path_to_MediaCodecTutorial>
$./gradlew assembleDebug
```

You can found more informaion about the build tool [here](https://developer.android.com/tools/building/plugin-for-gradle.html).

## How to test

Run this testing application is as simple as launch the `apk`, then the decoded frames will be rendered to the screen.


