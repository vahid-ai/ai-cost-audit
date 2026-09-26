# AI Spend

A Kotlin Multiplatform + Compose Multiplatform app that shows AI token usage and
cost across major LLM API providers on one dashboard.

## What it does

- Dashboard with total spend for a selected range (7d / 30d / 90d / MTD chips),
  per-provider cards (spend, tokens in/out, status: ok / error / not configured /
  unsupported), a per-model table sorted by cost and a daily spend bar chart.
- Settings screen with a masked field per provider for the API key (plus org ID
  for OpenAI), Save/Clear, and a hint describing the required key type.
- Manual entry screen for providers that expose no usage API.
- A "Demo data" toggle in Settings that loads sample records so the dashboard
  can be previewed without any keys.
- Costs from API cost endpoints are used verbatim; everything else is estimated
  with a built-in per-model pricing table (`PricingTable`, longest-prefix match;
  unpriced models are flagged).

## Providers

| Provider       | API used                                              | Key required              |
| -------------- | ----------------------------------------------------- | ------------------------- |
| OpenAI         | `/v1/organization/costs` + `/v1/organization/usage/completions` (Admin API) | Admin key `sk-admin-...` (org ID optional) |
| Anthropic      | `/v1/organizations/usage_report/messages` + `/v1/organizations/cost_report` | Admin key `sk-ant-admin...` |
| OpenRouter     | `/api/v1/activity`, falls back to `/api/v1/credits`   | API key `sk-or-...`       |
| DeepSeek       | `/user/balance` (consumed balance only)               | API key                   |
| Google Gemini  | none for plain API keys                               | manual entry              |
| Mistral        | none for plain API keys                               | manual entry              |
| Groq           | none for API keys                                     | manual entry              |
| xAI            | none for API keys                                     | manual entry              |
| Together       | none for API keys                                     | manual entry              |

## Run

Requires JDK 21 to run Gradle (newer JDKs are not supported by Gradle 8.14).
Set `org.gradle.java.home` in `local.properties` (this file is gitignored) or
pass `-Dorg.gradle.java.home=...`.

- Desktop: `./gradlew :desktopApp:run`
- Desktop package: `./gradlew :desktopApp:packageDistributionForCurrentOS`
- Tests: `./gradlew :shared:desktopTest`
- iOS compile check: `./gradlew :shared:compileKotlinIosSimulatorArm64`
- Android: `./gradlew :androidApp:assembleDebug` (needs an Android SDK; see below)
- iOS app: open `iosApp/iosApp.xcodeproj` in Xcode and run (the Xcode build
  invokes `:shared:embedAndSignAppleFrameworkForXcode`).

### Android SDK note

`androidApp` is only included in the build when an Android SDK is detected
(`ANDROID_HOME`/`ANDROID_SDK_ROOT` env var or `sdk.dir` in `local.properties`).
If you see "Android SDK not detected; skipping :androidApp", install one, e.g.:

```sh
brew install --cask android-commandlinetools
sdkmanager --sdk_root=/opt/homebrew/share/android-commandlinetools \
  "platforms;android-35" "build-tools;35.0.0" "platform-tools"
echo 'sdk.dir=/opt/homebrew/share/android-commandlinetools' >> local.properties
```

## Security note

Credentials and manual entries are stored via `multiplatform-settings`. On
desktop they land in plain `java.util.prefs` (not encrypted). On Android they
use `SharedPreferences`; consider Android Keystore-backed encrypted storage for
real keys. Never commit `local.properties` or any key material.

## Stack

Kotlin 2.3.21, Compose Multiplatform 1.9.3, AGP 8.13.2, Gradle 8.14.3 (wrapper),
Ktor 3.5.0, kotlinx-serialization 1.9.0, kotlinx-datetime 0.7.1,
kotlinx-coroutines 1.10.2, multiplatform-settings 1.3.0.
