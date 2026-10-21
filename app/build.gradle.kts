plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.shadowrp.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.shadowrp.app"; minSdk = 26; targetSdk = 35; versionCode = 12; versionName = "0.9.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}