# Walkthrough - Fixing Build Errors

I have fixed the build errors related to constant values and unresolved references.

## Changes Made

### 1. Fixed Constant Value Initialization
In Kotlin, `const val` must be initialized with a compile-time constant. Using `BuildConfig.APPLICATION_ID` (which is a generated `static final` field in Java) does not count as a compile-time constant for Kotlin's `const val` when used in string interpolation.

- **[MainActivity.kt](file:///D:/Workspace/esp32_hud/esp32-hud/android-app/app/src/main/java/com/maisonsmd/catdrive/MainActivity.kt)**: Changed `SHARED_PREFERENCES_FILE` from `const val` to `val`.
- **[Intents.kt](file:///D:/Workspace/esp32_hud/esp32-hud/android-app/app/src/main/java/com/maisonsmd/catdrive/lib/Intents.kt)**: Changed `APP_ID` and all dependent intent actions from `const val` to `val`.

### 2. Enabled BuildConfig Generation
The project is using a modern version of the Android Gradle Plugin (AGP 9.3.1), which disables `BuildConfig` generation by default. I explicitly enabled it in the build configuration.

- **[app/build.gradle](file:///D:/Workspace/esp32_hud/esp32-hud/android-app/app/build.gradle)**: Added `buildConfig true` to `buildFeatures`.

### 3. Resolved Serialization Issues
The project had many "Unresolved reference 'serializer'" errors in `CustomSerializers.kt`. This was caused by a mismatch between the Kotlin version and the serialization plugin version, as well as outdated dependency versions.

- **[build.gradle](file:///D:/Workspace/esp32_hud/esp32-hud/android-app/build.gradle)**: Added the serialization plugin to the top-level build file with version `2.2.10`.
- **[app/build.gradle](file:///D:/Workspace/esp32_hud/esp32-hud/android-app/app/build.gradle)**:
    - Updated `kotlin_version` to `2.2.10`.
    - Updated `kotlix_serialization_version` to `1.8.0`.
    - Updated `kotlix_coroutines_version` to `1.10.1`.
    - Updated `lifecycle_version` to `2.8.7`.
    - Removed the explicit version from the serialization plugin application to use the one from the top-level file.

## Verification Results

### Automated Tests
- Executed `./gradlew :app:compileDebugKotlin`: **PASSED**

The project now compiles successfully.
