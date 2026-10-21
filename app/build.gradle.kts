plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.shadowrp.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.shadowrp.app"; minSdk = 26; targetSdk = 35; versionCode = 21; versionName = "0.11.0" }
 val keystorePath = System.getenv("SHADOWRP_KEYSTORE_PATH")
 signingConfigs {
  create("permanentRelease") {
   if (!keystorePath.isNullOrBlank()) {
    storeFile = file(keystorePath)
    storePassword = System.getenv("SHADOWRP_KEYSTORE_PASSWORD")
    keyAlias = System.getenv("SHADOWRP_KEY_ALIAS")
    keyPassword = System.getenv("SHADOWRP_KEY_PASSWORD")
   }
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = false
   if (!keystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("permanentRelease")
  }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
