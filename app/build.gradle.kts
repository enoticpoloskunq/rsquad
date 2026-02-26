plugins {
    // AGP 9.0 has built-in Kotlin support
    id("com.android.application")
    // Compose Compiler plugin required for Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.raccoonsquad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.raccoonsquad"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    // Persistent debug signing for consistent updates
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // Faster debug builds
            isCrunchPngs = false
            signingConfig = signingConfigs.getByName("debug")
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // R8 full mode for better optimization
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use debug signing for release too (for easy updates)
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    
    // Split APKs by ABI for smaller downloads
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
        // Disable unused features for faster builds
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }
    
    // Configuration Cache support (Gradle 9.3)
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
    
    // Build time optimizations
    lint {
        // Disable lint for faster debug builds
        checkReleaseBuilds = true
        abortOnError = false
    }
}

// Enable Configuration Cache (persisted between builds)
// Add to gradle.properties: org.gradle.configuration-cache=true

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    
    // Splash Screen API
    implementation("androidx.core:core-splashscreen:1.2.0")
    
    // Compose BOM 2026.02.00
    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.6")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    
    // DataStore (for saving nodes)
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    
    // OkHttp (for network)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON
    implementation("com.google.code.gson:gson:2.12.1")
    
    // VPN Service
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    
    // Xray-core via libv2ray.aar (downloaded from AndroidLibXrayLite)
    implementation(files("libs/libv2ray.aar"))
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
