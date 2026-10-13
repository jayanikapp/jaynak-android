plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.jaynak.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jaynak.app"
        minSdk = 24                 // أندرويد ٧ وما بعده — يغطّي ما يحمله المندوبون
        targetSdk = 35
        versionCode = 7
        versionName = "2.2"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("JAYNAK_KEYSTORE") ?: "jaynak.keystore")
            storePassword = System.getenv("JAYNAK_STOREPASS")
            keyAlias = "jaynak"
            keyPassword = System.getenv("JAYNAK_KEYPASS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // لاحقة تسمح ببقاء الغلاف القديم مثبّتاً للمقارنة
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val compose = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(compose)
    androidTestImplementation(compose)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // alpha بقصد: 1.0.0 المستقرّة تسقط على بعض الأجهزة عند دوران
    // المفتاح، و1.1.0-alpha06 هي ما تعتمده جوجل عملياً منذ سنوات.
    // تُراجَع عند صدور مستقرّة. تخزّن رمز الجلسة وحده.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // خرائط: osmdroid لا Google Maps بقصد — لا مفتاح API ولا حساب
    // Google Cloud ولا فاتورة تبدأ بعد حدّ مجاني. نفس بلاطات
    // OpenStreetMap التي يستخدمها الموقع، فالخريطتان متطابقتان.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}
