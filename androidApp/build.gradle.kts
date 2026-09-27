import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Dev backend = host:port only, set outside git in local.properties or ~/.gradle/gradle.properties:
// - phone over USB: devApiHost=localhost:43871, then `adb reverse tcp:43871 tcp:43871`
// - emulator: devApiHost=10.0.2.2:43871
// - phone over Wi-Fi: devApiHost=192.168.1.20:8080 (backend port must be published beyond 127.0.0.1)
val devApiHost: String = providers.gradleProperty("devApiHost").orNull
    ?: rootProject.file("local.properties").takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("devApiHost=") }
        ?.substringAfter("=")
        ?.trim()
    ?: "10.0.2.2:8080"
require(!devApiHost.contains("/")) {
    "devApiHost must be host:port only (got '$devApiHost'); the client adds http:// and /api/v1/ itself."
}
val devLinkHost = devApiHost.substringBefore(":")
val devLinkPort = devApiHost.substringAfter(":", "")

android {
    namespace = "com.fares.saveme"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.fares.saveme"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        // Must be semver: the API refuses any X-Client-Version not matching ^\d+\.\d+\.\d+$.
        versionName = "1.0.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"http://$devApiHost\"")
            buildConfigField("String", "LINK_HOST", "\"$devLinkHost\"")
            buildConfigField("String", "ENVIRONMENT", "\"Dev\"")
            buildConfigField("boolean", "DEBUG_TOOLS", "true")
            manifestPlaceholders["linkHost"] = devLinkHost
            manifestPlaceholders["linkPort"] = devLinkPort
        }
        create("staging") {
            dimension = "env"
            // TODO(base-url): staging host not defined yet.
            buildConfigField("String", "API_BASE_URL", "\"https://staging.api.example\"")
            buildConfigField("String", "LINK_HOST", "\"staging.api.example\"")
            buildConfigField("String", "ENVIRONMENT", "\"Staging\"")
            buildConfigField("boolean", "DEBUG_TOOLS", "false")
            manifestPlaceholders["linkHost"] = "staging.api.example"
            manifestPlaceholders["linkPort"] = ""
        }
        create("prod") {
            dimension = "env"
            // TODO(base-url): production host not defined yet.
            buildConfigField("String", "API_BASE_URL", "\"https://api.example\"")
            buildConfigField("String", "LINK_HOST", "\"api.example\"")
            buildConfigField("String", "ENVIRONMENT", "\"Prod\"")
            buildConfigField("boolean", "DEBUG_TOOLS", "false")
            manifestPlaceholders["linkHost"] = "api.example"
            manifestPlaceholders["linkPort"] = ""
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
