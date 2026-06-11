# 4School Android App

Modern Android application for the 4School educational platform built with Jetpack Compose, Kotlin, and modern Android development practices.

## Project Structure

```
4school/
├── app/                          # Main app module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/            # Kotlin source code
│   │   │   ├── res/             # Android resources (layouts, strings, colors)
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   ├── build.gradle.kts         # App-level Gradle configuration
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/                 # Gradle wrapper files
├── build.gradle.kts             # Project-level Gradle configuration
├── settings.gradle.kts          # Gradle settings
├── gradle.properties            # Gradle properties
├── local.properties             # Local SDK path configuration
└── README.md                    # This file
```

## Technology Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Networking:** Retrofit 2 + OkHttp
- **Dependency Injection:** Koin
- **Database:** Android Room
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)
- **Gradle:** 8.2

## Prerequisites

- Android Studio Arctic Fox or later
- JDK 17 or later
- Android SDK 34
- Gradle 8.2 (included via wrapper)

## Setup Instructions

### 1. Open Project in Android Studio

```bash
# Open Android Studio and select "Open an Existing Project"
# Navigate to ./4school directory
```

### 2. Configure Android SDK

Update `local.properties` with your Android SDK path:

```properties
sdk.dir=/path/to/Android/sdk
```

### 3. Build the Project

```bash
# Using Gradle wrapper (Unix/Mac)
./gradlew build

# Using Gradle wrapper (Windows)
gradlew.bat build

# Or use Android Studio: Build > Make Project
```

### 4. Run the App

```bash
# Using Gradle wrapper
./gradlew installDebug

# Or select Run > Run 'app' in Android Studio
```

## Development

### Project Dependencies

#### Core
- `androidx.core:core-ktx` - Kotlin extensions for Android framework
- `androidx.appcompat:appcompat` - Backward compatibility library

#### UI (Jetpack Compose)
- `androidx.compose.ui:ui` - Compose UI primitives
- `androidx.compose.material3:material3` - Material 3 design components
- `androidx.activity:activity-compose` - Compose activity integration

#### Networking
- `com.squareup.retrofit2:retrofit` - Type-safe HTTP client
- `com.squareup.okhttp3:okhttp` - HTTP client library

#### Dependency Injection
- `io.insert-koin:koin-android` - Lightweight DI framework
- `io.insert-koin:koin-androidx-compose` - Koin Compose integration

#### Database
- `androidx.room:room-runtime` - Database abstraction layer

### Adding New Dependencies

Edit `app/build.gradle.kts` and add to the `dependencies` block:

```kotlin
dependencies {
    implementation("group:artifact:version")
}
```

Then sync Gradle files in Android Studio.

## Build Variants

The app supports multiple build types:

- **Debug** - Development build with debugging enabled
- **Release** - Production-ready build with ProGuard obfuscation enabled

## Testing

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

## Backend Connection

The app connects to the 4School Rust backend API. Configure the API endpoint in your configuration:

```kotlin
// Example retrofit setup with custom base URL
val retrofit = Retrofit.Builder()
    .baseUrl("http://your-backend-url/api/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

## Troubleshooting

### Gradle Build Failures

1. Clear Gradle cache: `./gradlew clean`
2. Invalidate Android Studio cache: File > Invalidate Caches > Clear Cache & Restart
3. Update SDK: Android Studio > Tools > SDK Manager

### Compilation Errors

- Ensure JDK 17 is installed and set as project JDK
- Check `local.properties` has correct SDK path
- Verify Gradle JVM settings: File > Settings > Build, Execution, Deployment > Gradle

### Android Emulator Issues

- Create a new emulator via AVD Manager
- Ensure sufficient disk space (at least 5GB)
- Use a recent API level (32+)

## Contributing

1. Create a feature branch: `git checkout -b feature/your-feature`
2. Follow Kotlin style guide
3. Write unit tests for new functionality
4. Submit a pull request

## License

This project is part of the 4School platform.
