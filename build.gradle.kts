plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Compose中の「別画面をまたいだ要素の滑らかな変形アニメーション」(SharedTransitionLayout)
    // を使うには、Kotlin 2.0系のCompose Compiler Gradleプラグインへの移行が必要。
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
}
