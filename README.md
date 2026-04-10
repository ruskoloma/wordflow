# WordFlow

Personal English-to-Russian vocabulary learning app for Android.

## Setup

### Requirements
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Min SDK 26 (Android 8.0)

### OpenRouter API Key

The app uses OpenRouter for AI translations. Configure your API key using **one** of these methods:

**Option 1: In-app settings** (recommended)
Open the app -> Settings -> AI Settings -> paste your API key.

**Option 2: Build-time fallback**
Add to `local.properties`:
```properties
OPENROUTER_API_KEY=sk-or-v1-your-key-here
```

### AWS DynamoDB (Cloud Sync)

For cloud backup/sync, configure AWS credentials:

**Option 1: In-app settings**
Open the app -> Settings -> Cloud Sync -> fill in nickname, access key, secret key, region.

**Option 2: Build-time defaults**
Add to `local.properties`:
```properties
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
AWS_REGION=us-east-1
```

**DynamoDB table setup:**
Create a DynamoDB table named `wordflow` with:
- Partition key: `pk` (String)
- Sort key: `sk` (String)

### Build & Run

```bash
# Clone and open in Android Studio, or build from command line:
./gradlew assembleDebug

# Install on connected device:
./gradlew installDebug
```

## Architecture

- **Kotlin** with Jetpack Compose (Material 3)
- **Room** for local database
- **DataStore** for preferences
- **OkHttp** for network requests (OpenRouter AI, AWS DynamoDB)
- **Glance** for home screen widget
- **WorkManager** for notification scheduling
- Manual dependency injection via `AppContainer`

## Package Structure

```
com.rsln.wordflow
├── data
│   ├── local          # Room DB, DAOs, DataStore
│   ├── remote         # OpenRouter AI, DynamoDB
│   └── repository     # Word & Collection repos
├── ui
│   ├── theme          # Material 3 theming
│   ├── components     # Shared UI components
│   ├── navigation     # Navigation graph
│   └── screens        # All app screens
├── widget             # Glance home screen widget
├── notification       # WorkManager notifications
└── di                 # AppContainer (DI)
```
