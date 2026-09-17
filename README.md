# 予定リンク (ScheduleLink)

行動予定(スケジュール)同士をリンクさせながら作成・管理できる、Android向けのスケジュール管理アプリです。

## 主な機能

- 日付ごとの予定一覧表示(前日/翌日/今日への移動)
- 予定の追加・編集・削除(タイトル、メモ、日付、開始/終了時刻)
- **予定同士のリンク機能**: 予定を作成・編集する際に、既存の他の予定を選択して「関連する行動予定」として紐付けられます。詳細画面ではリンクされた予定の一覧が表示され、タップするとその予定の詳細へ遷移できます(双方向にリンクされます)。

## 技術構成

- Kotlin
- Jetpack Compose (Material3)
- Navigation Compose
- Room (ローカルDB, KSP)
- MVVM (ViewModel + StateFlow)

## プロジェクト構成

```
app/src/main/java/com/example/schedulelink/
├── ScheduleLinkApplication.kt   # DB/Repositoryの初期化
├── MainActivity.kt
├── data/                        # Room: Entity, DAO, Database, Repository
└── ui/
    ├── theme/                   # Compose Material3 テーマ
    ├── navigation/              # NavHost(一覧/詳細/編集)
    ├── list/                    # 予定一覧画面
    ├── detail/                  # 予定詳細画面(リンク一覧を表示)
    └── edit/                    # 予定の追加/編集画面(リンク選択UI含む)
```

## ビルド方法

Android Studio (Hedgehog以降推奨) でこのフォルダを開き、Gradle同期後に実行してください。
コマンドラインの場合:

```
./gradlew assembleDebug
```

動作要件: minSdk 26 / targetSdk・compileSdk 34。

## 補足(開発環境について)

このプロジェクトはサンドボックス環境で作成されました。当環境にはAndroid SDKが導入されておらず、
また `dl.google.com` (Android Gradle Plugin / AndroidX の取得元) へのネットワークアクセスが
ネットワークポリシーによりブロックされていたため、この環境内でのビルド確認は行えていません。
Gradle Wrapper (Gradle 8.7) は同梱済みです。Android Studio または SDK/ネットワークが利用可能な
環境で `./gradlew assembleDebug` を実行し、動作確認をお願いします。
