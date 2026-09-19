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
3. アプリのニックネームは任意
4. **「デバッグ署名証明書 SHA-1」に、下記の値を必ず入力してください**(これが空だと
   Google サインインが「コード: 10」というエラーで失敗します):

   ```
   5F:C7:34:21:1A:89:19:DA:50:48:03:03:32:8D:46:DE:82:2C:DC:0D
   ```

   この値は、GitHub Actions が APK をビルドする際に常に同じ専用の署名鍵
   (`app/debug-keystore.jks`、リポジトリに同梱済み)を使うようにしたことで、
   毎回変わらない固定値になっています。
5. 「アプリを登録」→ **`google-services.json` をダウンロード**
6. ダウンロードした `google-services.json` を、このリポジトリの `app/google-services.json` と
   置き換える(上書きコピー)

### すでにアプリを登録してしまった場合

SHA-1 を入力し忘れて登録してしまっても大丈夫です。Firebase コンソールの
「プロジェクトの設定」→ 対象の Android アプリ →「SHA証明書フィンガープリント」の
「フィンガープリントを追加」から、上記の SHA-1 を後から追加できます。
`google-services.json` を再ダウンロードし直す必要はありません。

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

    // サインイン中のユーザー本人の「どの家族に所属しているか」を保存する場所。
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }

    match /families/{familyId} {
      // 招待コードで検索して参加する際、まだメンバーになっていない状態でも
      // familyドキュメント自体(名前・招待コード)は読める必要があるため、
      // サインインしていれば読み取りは許可する(実際の予定データは下の
      // サブコレクションのルールで、メンバーだけに制限している)。
      allow read: if request.auth != null;
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

> **重要**: 上記は最初に案内したものから修正しています。もしすでにルールを保存済みの場合は、
> 上記の内容で上書きして再度「公開」してください。(`users` コレクションのルールが
> 抜けていたため、サインイン直後にアプリが落ちる不具合がありました。)

## 5. Cloud Storage を有効にする(写真添付機能に必要)

大目的・中日程・小日程に添付する写真は Firebase Storage に保存されます(家族のメンバー
間で共有されます)。

1. 左メニュー「Storage」→「始める」
2. 本番環境モードでよい(後述のセキュリティルールを設定するため)→ ロケーションは
   Firestore と同じ `asia-northeast1`(東京)がおすすめ
3. 作成後、「Rules」タブで以下のように設定して公開(Firestore の family ルールと同じ考え方で、
   自分が所属する family のメンバーだけが、その family の写真を読み書きできるようにする
   最低限のルール):

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /families/{familyId}/{allPaths=**} {
      allow read: if request.auth != null &&
        request.auth.uid in firestore.get(/databases/(default)/documents/families/$(familyId)).data.members;

      allow create, update: if request.auth != null &&
        request.auth.uid in firestore.get(/databases/(default)/documents/families/$(familyId)).data.members &&
        request.resource.size < 10 * 1024 * 1024 &&
        request.resource.contentType.matches('image/.*');

      allow delete: if request.auth != null &&
        request.auth.uid in firestore.get(/databases/(default)/documents/families/$(familyId)).data.members;
    }
  }
}
```

補足:
- `firestore.get(...)` で Storage のルールから Firestore の `families/{familyId}` ドキュメントを
  参照し、その `members` 配列に自分の uid が含まれているかを確認しています(Firestore側の
  ルールと同じ仕組みなので、招待コードで参加した家族メンバーだけがアクセスできます)。
- アップロード(`create`/`update`)には、誤って大きすぎるファイルや画像以外のファイルが
  上がらないよう、10MB以下・`image/*` のみという制限を付けています。
- `delete`(写真の削除)だけは制限を分けています。Storageのルールでは削除時に
  `request.resource` が存在しない(nullになる)ため、サイズや形式のチェックを
  `delete`と同じ条件に入れてしまうと、削除そのものが常に拒否されてしまいます。

## 6. 差し替えたら

`app/google-services.json` を本物に差し替えてコミット・プッシュすれば、GitHub Actions が
自動でビルドし、いつものダウンロードリンクから最新の APK が取得できます。

家族の他のメンバーは、アプリ初回起動時に自分の Google アカウントでサインインし、
最初に家族グループを作った人から共有される「招待コード」を入力すれば、同じ家族グループの
予定をリアルタイムに見る・編集できるようになります。
