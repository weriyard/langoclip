import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Czytamy domyślny klucz Gemini z local.properties — plik jest gitignored.
// Brak klucza nie blokuje buildu; Settings po prostu wystartuje z pustym polem.
val defaultGeminiKey: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(props::load)
    props.getProperty("GEMINI_API_KEY", "")
}

// Domyślne klucze OpenAI/Anthropic z app/.env (KEY=VALUE per linia, bez cudzysłowów).
private val envProps: Properties = run {
    val props = Properties()
    val file = project.file(".env")
    if (file.exists()) file.inputStream().use(props::load)
    props
}
val defaultOpenAiKey: String = envProps.getProperty("OPENAI_API_KEY", "")
val defaultAnthropicKey: String = envProps.getProperty("CLAUDE_API_KEY", "")

android {
    namespace = "com.floatingclipboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.floatingclipboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DEFAULT_GEMINI_API_KEY", "\"$defaultGeminiKey\"")
        buildConfigField("String", "DEFAULT_OPENAI_API_KEY", "\"$defaultOpenAiKey\"")
        buildConfigField("String", "DEFAULT_ANTHROPIC_API_KEY", "\"$defaultAnthropicKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
}
