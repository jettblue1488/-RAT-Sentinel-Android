plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kay.ratsentinel"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.kay.ratsentinel"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Release builds use the same private signing key on every CI run.
    // The key is supplied only through GitHub Actions secrets, never committed.
    val signingStoreFile = System.getenv("RAT_SIGNING_STORE_FILE")
    val signingStorePassword = System.getenv("RAT_SIGNING_STORE_PASSWORD")
    val signingKeyAlias = System.getenv("RAT_SIGNING_KEY_ALIAS")
    val signingKeyPassword = System.getenv("RAT_SIGNING_KEY_PASSWORD")

    if (!signingStoreFile.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()
    ) {
        signingConfigs {
            create("stableRelease") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("stableRelease")
                isMinifyEnabled = false
            }
        }
    }
}
