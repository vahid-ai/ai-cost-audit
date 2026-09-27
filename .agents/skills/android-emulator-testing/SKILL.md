---
name: ai-spend-android-runtime
description: Run and visually verify AI Spend Android on an API 35 emulator using deterministic demo and manual data.
---

# Android runtime testing

## Environment
- Follow the repo blueprint for JDK 21, Android SDK and the `test` Pixel 6/API 35 AVD.
- Preserve the Gradle Maven Central GCS mirror initialization when present.
- Build with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :androidApp:assembleDebug`.
- For recording, replace any headless emulator (`adb emu kill`) with
  `emulator -avd test -gpu swiftshader_indirect -no-snapshot -no-audio`.
- Wait for `adb shell getprop sys.boot_completed` to return `1`.
- Install `androidApp/build/outputs/apk/debug/androidApp-debug.apk`; launch
  `adb shell am start -n com.aispend.android/.MainActivity`.
- Maximize the window with wmctrl; some emulator versions retain their own
  scaled viewport despite window-manager maximization. Capture crisp app-only
  evidence with `adb exec-out screencap -p`.

## Interaction
- Prefer desktop clicks on Dashboard / Manual / Settings.
- If desktop text paste does not reach Android fields, focus the field visually
  and use `adb shell input text VALUE`. Dismiss any Android keyboard onboarding.
- `adb shell input keyevent KEYCODE_BACK` reliably tests system Back.
- Capture continuous logcat from before launch; inspect FATAL EXCEPTION,
  native fatal signals and package-specific ANRs.

## Deterministic local checks
- Only clear package data on a disposable emulator when a clean baseline is needed.
- Demo data lives under Settings. With no manual data, 7d=$21.57, 30d=$87.93,
  90d=$240.10 for the current deterministic demo/pricing implementation.
  MTD depends on today's day-of-month; recalculate when implementation changes.
- Manual defaults to Google Gemini. Model `gemini-2.5-pro`, input `1000000`,
  output `100000` costs $2.25 with current pricing.
- Use fake OpenAI key `sk-test`, Save, Dashboard > Refresh. Expect an error
  status (normally HTTP 401), not a crash. Clear the fake key afterward.
- Force-stop/relaunch without clearing data checks persistence of demo mode,
  manual records and credential clearing. Range/screen selection may reset.

## Devin Secrets Needed
None for demo/manual/fake-key error checks. Live provider data requires suitable
provider credentials and is a separate scope.
