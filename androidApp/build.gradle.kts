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
    implementation(libs.compose.foundation)
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Backend = host:port only (the client adds http:// and /api/v1/ itself), read from a Gradle property, then
// local.properties (outside git), then the default:
// - devApiHost: phone over USB `localhost:43871` + `adb reverse tcp:43871 tcp:43871`; emulator `10.0.2.2:43871`
// - stagingApiHost / prodApiHost: the LAN stacks on the host machine; CI passes the STAGING_API_HOST / PROD_API_HOST repo variables.
// The app can override the host at runtime (Server address screen), so a moved backend needs no rebuild.
val localProperties: List<String> = rootProject.file("local.properties").takeIf { it.exists() }?.readLines().orEmpty()

fun hostProperty(name: String, default: String): String {
    val host = providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.firstOrNull { it.startsWith("$name=") }?.substringAfter("=")?.trim()
        ?: default
    require(!host.contains("/")) {
        "$name must be host:port only (got '$host'); the client adds http:// and /api/v1/ itself."
    }
    return host
}

val apiHosts = mapOf(
    "dev" to hostProperty("devApiHost", "10.0.2.2:8080"),
    "staging" to hostProperty("stagingApiHost", "192.168.1.14:8091"),
    "prod" to hostProperty("prodApiHost", "192.168.1.14:8092"),
)

// Release signing comes from the environment only (CI decodes the keystore from secrets); without it the release APK stays unsigned.
val releaseSigning: Map<String, String>? = listOf("SAVEME_KEYSTORE_FILE", "SAVEME_KEYSTORE_PASSWORD", "SAVEME_KEY_ALIAS", "SAVEME_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull.orEmpty() }
    .takeIf { values -> values.values.all { it.isNotBlank() } }

android {
    namespace = "com.fares.saveme"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.fares.saveme"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI passes a rising versionCode so each APK installs over the previous one.
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        // Must be semver: the API refuses any X-Client-Version not matching ^\d+\.\d+\.\d+$.
        versionName = providers.gradleProperty("versionName").orNull ?: "1.0.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    flavorDimensions += "env"
    productFlavors {
        // Every environment is the LAN backend over plain HTTP, mailed links included.
        fun com.android.build.api.dsl.ApplicationProductFlavor.backend(host: String, environment: String, debugTools: Boolean) {
            dimension = "env"
            val linkHost = host.substringBefore(":")
            val linkPort = host.substringAfter(":", "")
            buildConfigField("String", "API_BASE_URL", "\"http://$host\"")
            buildConfigField("String", "DEFAULT_API_HOST", "\"$host\"")
            buildConfigField("String", "LINK_SCHEME", "\"http\"")
            buildConfigField("String", "LINK_HOST", "\"$linkHost\"")
            buildConfigField("String", "LINK_PORT", "\"$linkPort\"")
            buildConfigField("String", "ENVIRONMENT", "\"$environment\"")
            buildConfigField("boolean", "DEBUG_TOOLS", "$debugTools")
            manifestPlaceholders["linkScheme"] = "http"
            manifestPlaceholders["linkHost"] = linkHost
            manifestPlaceholders["linkPort"] = linkPort
        }
        // Distinct ids so dev, staging and production install side by side, each with its own data.
        create("dev") {
            backend(apiHosts.getValue("dev"), "Dev", debugTools = true)
            applicationIdSuffix = ".dev"
        }
        create("staging") {
            backend(apiHosts.getValue("staging"), "Staging", debugTools = false)
            applicationIdSuffix = ".staging"
        }
        create("prod") {
            backend(apiHosts.getValue("prod"), "Prod", debugTools = false)
        }
    }
    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("SAVEME_KEYSTORE_FILE"))
                storePassword = releaseSigning.getValue("SAVEME_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning.getValue("SAVEME_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("SAVEME_KEY_PASSWORD")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
            // R8: shrink, optimise and obfuscate code, and drop unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
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
