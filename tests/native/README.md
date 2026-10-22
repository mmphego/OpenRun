# Native planning renders

`NativePlanningTest` runs the actual Android View code through Robolectric 4.17 Native Graphics, API 35. These are host-side native renders and interaction checks, **not emulator or S23 screenshots**. No HTML recreation is used.

First build the app with JDK 21 and the standalone builder, then resolve these host test dependencies with Gradle 8.11.1: `org.robolectric:robolectric:4.17`, `junit:junit:4.13.2` and `org.robolectric:android-all:15-robolectric-12650502`. Extract `classes.jar` from transitive AndroidX AARs. Compile the test against those jars, `build/standalone/classes` and `build/runtime-deps/*.jar`.

Put `com/android/tools/test_config.properties` on the test classpath with absolute `android_merged_manifest`, `android_merged_resources`, `android_merged_assets`, `android_resource_apk` paths pointing at the standalone manifest/resources.apk and app/src/main resources/assets. Set `android_custom_package=app.rwun`.

Run JUnitCore `app.rwun.NativePlanningTest` with `-Drwun.evidence=/absolute/output/path` and `-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2`. An initial run downloads instrumented Android framework/native graphics dependencies. Use a trusted HTTPS proxy if your environment requires one. Do not disable certificate checks.

Physical acceptance remains in `../s23/CHECKLIST.md`.

Automated reproduction after the standalone APK build:

```sh
JAVA_HOME=/path/to/jdk21 python3 tests/native/run.py
```

The runner uses the retained Gradle wrapper for host-only test dependencies. `RWUN_NATIVE_QA_LIB` can reuse an already-resolved dependency folder; `RWUN_NATIVE_QA_OUTPUT` selects the output folder. It does not install an APK or operate a device.


## GPS recorder regression (1.4.2)

The runner now also executes `GpsRecordingTest` against the actual Android service. Its ten methods cover delayed and fresh 2 km sequences, provider timestamp boundaries, duplicate callbacks, pause/resume, accuracy/jump/gap protection, a visible zero-distance warning and a known-distance walk save. These are controlled Android-framework host tests, not a physical GPS measurement or a Samsung background-delivery test.
