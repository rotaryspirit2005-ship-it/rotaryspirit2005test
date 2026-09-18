# Firebase セットアップ手順(家族共有機能に必要)

このアプリの「家族での共同編集」機能は Firebase(Google の無料クラウドサービス)を使います。
`app/google-services.json` は動作確認用のプレースホルダーなので、実際に共有機能を使うには
以下の手順で家族の代表者が Firebase プロジェクトを作成し、本物の設定ファイルに差し替える必要があります。

## 1. Firebase プロジェクトを作成する

1. ブラウザで https://console.firebase.google.com/ を開き、Google アカウントでログイン
2. 「プロジェクトを追加」→ 好きなプロジェクト名(例: `schedulelink-family`)を入力して作成
   (Google アナリティクスは無効のままで問題ありません)

## 2. Android アプリを登録する

1. 作成したプロジェクトの画面で Android アイコンをクリックして「アプリを追加」
2. パッケージ名に **`com.example.schedulelink`** を入力(必ずこの通りに入力してください)
3. アプリのニックネームは任意、署名証明書 SHA-1 は今回は未入力のままで OK
4. 「アプリを登録」→ **`google-services.json` をダウンロード**
5. ダウンロードした `google-services.json` を、このリポジトリの `app/google-services.json` と
   置き換える(上書きコピー)

## 3. Authentication を有効にする

1. Firebase コンソール左メニュー「Authentication」→「Sign-in method」
2. 「Google」を選択して有効化 → 保存

## 4. Firestore Database を有効にする

1. 左メニュー「Firestore Database」→「データベースの作成」
2. 本番環境モードでよい(後述のセキュリティルールを設定するため)→ ロケーションは
   `asia-northeast1`(東京)がおすすめ
3. 作成後、「ルール」タブで以下のように設定して公開(family のメンバーだけが
   自分の family のデータを読み書きできるようにする最低限のルール):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /families/{familyId} {
      allow read: if request.auth != null &&
        request.auth.uid in resource.data.members;
      allow create: if request.auth != null;
      allow update: if request.auth != null &&
        request.auth.uid in resource.data.members;

      match /{collection}/{docId} {
        allow read, write: if request.auth != null &&
          request.auth.uid in get(/databases/$(database)/documents/families/$(familyId)).data.members;
      }
    }
  }
}
```

## 5. 差し替えたら

`app/google-services.json` を本物に差し替えてコミット・プッシュすれば、GitHub Actions が
自動でビルドし、いつものダウンロードリンクから最新の APK が取得できます。

家族の他のメンバーは、アプリ初回起動時に自分の Google アカウントでサインインし、
最初に家族グループを作った人から共有される「招待コード」を入力すれば、同じ家族グループの
予定をリアルタイムに見る・編集できるようになります。
