plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "uk.co.fireburn.raiform"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.co.fireburn.raiform"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // Kotlin 2.1.0 compiler extension version
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // --- Core Android ---
    implementation("androidx.core:core-ktx:1.17.0") // Updated
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0") // Updated
    implementation("androidx.activity:activity-compose:1.12.2") // Updated

    // --- Jetpack Compose (UI) ---
    // Updated BOM to late 2025 version
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Note: This library hasn't had major updates recently but is still widely used
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    implementation("androidx.compose.material:material-icons-extended")

    // --- Navigation ---
    implementation("androidx.navigation:navigation-compose:2.9.6") // Updated

    // --- Dependency Injection (Hilt) ---
    implementation("com.google.dagger:hilt-android:2.57.2") // Updated
    kapt("com.google.dagger:hilt-android-compiler:2.57.2") // Updated
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0") // Updated

    // --- Firebase ---
    // Updated BOM
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore") // KTX is technically deprecated in favor of main artifact, but still works
    implementation("com.google.firebase:firebase-auth")

    // --- Widgets (Jetpack Glance) ---
    implementation("androidx.glance:glance-appwidget:1.1.1") // Updated
    implementation("androidx.glance:glance-material3:1.1.1") // Updated

    // --- DataStore ---
    implementation("androidx.datastore:datastore-preferences:1.2.0") // Updated

    // --- Graphs (MPAndroidChart) ---
    // No official newer version, v3.1.0 is stable.
    // Alternatives like Vico exist if you need Compose-native charts.
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2") // Updated

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1") // Updated
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1") // Updated
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
