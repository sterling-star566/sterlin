plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.sterling.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.sterling.app"; minSdk = 26; targetSdk = 34; versionCode = 2; versionName = "2.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    androidResources { noCompress.add("cact") }
}
