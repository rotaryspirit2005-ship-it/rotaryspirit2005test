plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.schedulelink"
    compileSdk = 35

    // GitHub Actionsが自動で連番を振ってくれるGITHUB_RUN_NUMBERをversionCodeに使う。
    // ローカルビルドなど未設定の場合は1のまま。
    // versionCodeが毎回変わらないと、一部のランチャー(ホーム画面アプリ)が
    // 「同じバージョンへの更新」とみなしてウィジェット一覧の再スキャンを省略し、
    // ウィジェット選択画面から自作ウィジェットが消えてしまうことがあったための対応。
    val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.example.schedulelink"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 「今インストールされているのはどのコミットのビルドか」をアプリ内で
        // 確認できるようにする(GitHub Actionsが自動で設定するGITHUB_SHAを使う。
        // ローカルビルドなど未設定の場合は"local"にする)。
        buildConfigField(
            "String",
            "GIT_SHA",
            "\"${(System.getenv("GITHUB_SHA") ?: "local").take(7)}\""
        )
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    signingConfigs {
        getByName("debug") {
            // GitHub Actions は毎回まっさらな環境でビルドするため、標準のデバッグ用
            // キーストアだと署名(=SHA-1)がビルドのたびに変わってしまい、
            // Firebaseに登録したSHA-1とズレてGoogleサインインが失敗する。
            // 常に同じ鍵で署名されるよう、リポジトリに同梱した専用のデバッグ用
            // キーストアを使う。
            storeFile = file("debug-keystore.jks")
            storePassword = "android"
            keyAlias = "schedulelink-debug"
            keyPassword = "android"
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val navVersion = "2.8.4"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:$navVersion")

    // Firebase (Auth + Firestore) for family sharing / realtime sync
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // 目的/中日程/小日程に添付する写真の非同期読み込み用
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ホーム画面ウィジェット(Jetpack Glance)と、その定期更新用
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
