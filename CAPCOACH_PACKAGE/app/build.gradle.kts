plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.codenection2026_package"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.codenection2026_package"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // CRITICAL: Prevents Android from compressing ML model
    androidResources {
        noCompress += "tflite"
    }

    buildFeatures {
        mlModelBinding = false
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    // CapCoach hosts every screen as a Fragment inside MainActivity.
    implementation(libs.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // 1. Android Room (Using annotationProcessor because of Java usage)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // 2. Health Connect API
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

    // 3. TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // 4. Glide (UI Assets)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
}