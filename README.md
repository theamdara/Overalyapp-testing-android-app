# Test overlay app — Android app

---

## Requirements

| Tool | Version |
|------|---------|
| JDK | 17+ (tested with 21) |
| Android SDK | compile/target **34** |
| Android device | **Android 9 (API 28)** or newer |

Point Gradle at the SDK either via `android/local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
```

or via the `ANDROID_HOME` environment variable. (`local.properties` is git-ignored.)

## Build & install

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- `keystore/` is git-ignored; if no local keystore exists the build falls back to the
  auto-generated debug keystore in `~/.android/`.

Toolchain: Gradle **8.5**, Android Gradle Plugin **8.2.2**, Kotlin **1.9.22** (KSP
`1.9.22-1.0.17`).

---

## Screens


---

## Dependencies

- AndroidX: `core-ktx`, `appcompat`, `constraintlayout`, `recyclerview`, `lifecycle-runtime-ktx`,
  `lifecycle-viewmodel-ktx`
- Material Components `1.11.0`
- Room `2.6.1` (KSP compiler)
- Kotlinx Coroutines `1.7.3`
- OkHttp `4.12.0`, Gson `2.10.1`
