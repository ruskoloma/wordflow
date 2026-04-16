import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun configValue(name: String, defaultValue: String): String =
    providers.gradleProperty(name).orNull
        ?: localProps.getProperty(name)
        ?: System.getenv(name)
        ?: defaultValue

fun buildConfigString(value: String): String =
    "\"${value.trim().replace("\\", "\\\\").replace("\"", "\\\"")}\""

val releaseStoreFile = configValue("WORDFLOW_SIGNING_STORE_FILE", "")
val releaseStorePassword = configValue("WORDFLOW_SIGNING_STORE_PASSWORD", "")
val releaseKeyAlias = configValue("WORDFLOW_SIGNING_KEY_ALIAS", "")
val releaseKeyPassword = configValue("WORDFLOW_SIGNING_KEY_PASSWORD", "")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }

android {
    namespace = "com.rsln.wordflow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rsln.wordflow"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.0.5"

        val openRouterKey = localProps.getProperty("OPENROUTER_API_KEY", "")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterKey\"")

        // Backend base URL for the Go API. Release builds should use the
        // hosted Railway URL. Override locally with WORDFLOW_BACKEND_URL in
        // local.properties, a Gradle property, or an environment variable.
        val backendUrl = configValue(
            "WORDFLOW_BACKEND_URL",
            "https://wordflow-production-916f.up.railway.app"
        )
        buildConfigField("String", "BACKEND_URL", buildConfigString(backendUrl))
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // EncryptedSharedPreferences for stashing the WordFlow app JWT securely.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.core:core-ktx:1.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
