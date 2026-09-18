# 予定リンク (ScheduleLink)

行動予定(スケジュール)同士をリンクさせながら作成・管理できる、Android向けのスケジュール管理アプリです。
大目的→中日程→小日程の階層管理、家族での共同編集、既存カレンダーからの取り込みに対応しています。

## 主な機能

- 日付ごとの予定一覧・フロー表示(枝分岐+ピンチズーム)、前日/翌日/今日への移動
- 予定の追加・編集・削除(タイトル、メモ、日付、開始/終了時刻)
- **予定同士のリンク機能**: 予定を作成・編集する際に、既存の他の予定を選択して「関連する行動予定」として紐付けられます。詳細画面ではリンクされた予定の一覧が表示され、タップするとその予定の詳細へ遷移できます(双方向にリンクされます)。
- **目的マップ**: 大目的(Goal)→中日程(Milestone)→小日程(Schedule)の3階層で計画を管理し、進捗を可視化
- **家族での共同編集**: Googleアカウントでサインインし、招待コードで家族グループに参加すると、Firestoreを通じてリアルタイムに予定を共有・共同編集できます
- **既存カレンダーの取り込み**: `.ics`(iCalendar)ファイル、または端末に同期済みのカレンダー(Googleカレンダーなど)から予定を取り込めます

## 技術構成

- Kotlin / Jetpack Compose (Material3) / Navigation Compose / MVVM (ViewModel + StateFlow)
- Firebase Authentication(Googleサインイン)+ Cloud Firestore(家族単位のリアルタイム共有データストア)

## プロジェクト構成

```
app/src/main/java/com/example/schedulelink/
├── ScheduleLinkApplication.kt   # Firebase関連リポジトリの初期化
├── MainActivity.kt
├── importing/                   # .ics解析・端末カレンダー読み取り
├── data/                        # Firestoreベースのモデル・リポジトリ(Schedule/Goal/Milestone/Family/Auth)
└── ui/
    ├── theme/                   # Compose Material3 テーマ
    ├── navigation/              # NavHost(一覧/詳細/編集/目的/中日程/インポート)
    ├── auth/                    # サインイン・家族グループ作成/参加/設定
    ├── list/                    # 予定一覧・フロー切替画面
    ├── detail/edit/             # 予定詳細・追加/編集画面(リンク選択UI含む)
    ├── goal/milestone/          # 目的マップ関連画面
    ├── flow/                    # 枝分岐フロー表示・ピンチズームコンテナ
    ├── importing/               # インポート画面(.ics/端末カレンダー)
    └── AppRoot.kt                # サインイン→家族選択→本編の画面切り替え
```

## Firebaseのセットアップ(家族共有機能を使う場合に必須)

`app/google-services.json` は動作確認用のプレースホルダーです。実際に家族共有機能を使うには、
Firebaseプロジェクトを作成して本物の設定ファイルに差し替える必要があります。手順は
[FIREBASE_SETUP.md](./FIREBASE_SETUP.md) を参照してください。

## ビルド方法

Android Studio (Hedgehog以降推奨) でこのフォルダを開き、Gradle同期後に実行してください。
コマンドラインの場合:

```
./gradlew assembleDebug
```

動作要件: minSdk 26 / targetSdk・compileSdk 34。カレンダー取り込み機能は端末のカレンダー
アクセス権限(READ_CALENDAR)を使用します。

## 補足(開発環境について)

このプロジェクトはサンドボックス環境で作成されました。当環境にはAndroid SDKが導入されておらず、
また `dl.google.com` (Android Gradle Plugin / AndroidX の取得元) へのネットワークアクセスが
ネットワークポリシーによりブロックされていたため、この環境内でのビルド確認は行えていません。
Gradle Wrapper (Gradle 8.7) は同梱済みです。Android Studio または SDK/ネットワークが利用可能な
環境で `./gradlew assembleDebug` を実行し、動作確認をお願いします(このリポジトリでは
GitHub Actions上で自動ビルドし、APKをダウンロードできるようにしています)。
