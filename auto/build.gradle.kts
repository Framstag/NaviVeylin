plugins {
    id("com.android.library")
}

android {
    namespace = "com.naviveylin.auto"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":osmscout-client-java"))
    api("androidx.car.app:app:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.dagger:hilt-android:2.59")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
