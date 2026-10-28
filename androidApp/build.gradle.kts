plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.compose") }
android {
    namespace = "pe.upeu.agrosierra"
    compileSdk = 36
    defaultConfig { applicationId = "pe.upeu.agrosierra"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug { buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080/api/v1/\"") }
        release { buildConfigField("String", "API_URL", "\"https://localhost/api/v1/\"") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("io.insert-koin:koin-android:4.1.1")
    implementation("io.ktor:ktor-client-okhttp:3.3.0")
    implementation("app.cash.sqldelight:android-driver:2.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.3")
}

