# WordFlow

Personal English-to-Russian vocabulary learning app for Android.

## Setup

### Requirements
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Min SDK 26 (Android 8.0)

### local.properties

Add your local overrides to `local.properties` (gitignored). The backend URL
defaults to the hosted Railway backend; override it when testing against a
local server or LAN device.
```properties
WORDFLOW_BACKEND_URL=http://10.0.2.2:8080
OPENROUTER_API_KEY=sk-or-v1-your-key-here
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
AWS_REGION=us-west-2
```

### Build & Run

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Releasing a New Version

### Option 1: Automated (GitHub Actions)

```bash
# Bump version, tag, push — Actions builds APK and creates release
./release.sh 1.1.0 "What changed"
```

### Option 2: Manual

```bash
# 1. Edit app/build.gradle.kts — bump versionName and versionCode
# 2. Build
./gradlew assembleDebug

# 3. Create GitHub release with APK attached
gh release create v1.1.0 \
  --title "WordFlow v1.1.0" \
  --notes "What changed" \
  app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions Secrets

For automated builds, add these repo secrets (`Settings > Secrets > Actions`):
- `OPENROUTER_API_KEY`
- `AWS_ACCESS_KEY`
- `AWS_SECRET_KEY`
- `AWS_REGION`

Optional repo variable:
- `WORDFLOW_BACKEND_URL` defaults to `https://wordflow-production-916f.up.railway.app`

## Self-Update

The app checks `ruskoloma/wordflow` GitHub Releases for new versions.
Go to **Settings > About > Check for updates**. If a newer version exists,
it downloads the APK and launches the Android package installer.

The repo must be **public** for unauthenticated API access from the app.

## Architecture

- **Kotlin** + Jetpack Compose (Material 3)
- **Room** local database, **DataStore** preferences
- **OkHttp** + **Gson** for OpenRouter AI & AWS DynamoDB
- **RemoteViews** home screen widget
- **WorkManager** notification scheduling
- Auto cloud sync via DynamoDB (offline-first)
- Self-update via GitHub Releases
