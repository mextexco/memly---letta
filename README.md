# Memly

音声・テキストで日々の記憶を気軽に記録する Android アプリ。  
バックエンドに [mem0](https://mem0.ai) を使い、AI が文脈を理解して記憶を管理します。

## スクリーンショット

| ホーム画面 | 記憶一覧 |
|-----------|---------|
| テキスト・音声で記録 | 検索・編集・削除 |

## 機能

- 🎤 **音声入力** — Android 標準の音声認識でハンズフリー入力
- ✍️ **テキスト入力** — キーボードでの直接入力にも対応
- 🧠 **mem0 連携** — 記録した内容を AI が意味のある記憶として保存
- 🔍 **検索** — キーワードで過去の記憶をセマンティック検索
- ✏️ **編集・削除** — 記憶の修正・削除が可能
- 👤 **Google サインイン** — アカウントでユーザーを識別
- 📊 **ステータス表示** — 記憶の件数をホーム画面に表示

## 技術スタック

| 分類 | 技術 |
|------|------|
| 言語 | Kotlin 2.3 |
| UI | Jetpack Compose + Material3 |
| ナビゲーション | Navigation3 |
| アーキテクチャ | MVVM + StateFlow |
| 通信 | OkHttp |
| シリアライゼーション | kotlinx.serialization |
| ストレージ | DataStore Preferences |
| 認証 | Credential Manager (Google Sign-In) |
| バックエンド | mem0 Cloud API |

## セットアップ

### 1. リポジトリをクローン

```bash
git clone https://github.com/mextexco/memly.git
cd memly
```

### 2. `local.properties` に API キーを追加

```properties
sdk.dir=/path/to/Android/sdk
MEM0_API_KEY=your_mem0_api_key
GOOGLE_WEB_CLIENT_ID=your_google_web_client_id
```

- **mem0 API キー** → [app.mem0.ai](https://app.mem0.ai) で取得
- **Google Web Client ID** → [Google Cloud Console](https://console.cloud.google.com/) で OAuth 2.0 クライアント ID（ウェブアプリケーション）を作成

### 3. Google Cloud Console に Android アプリを登録

1. APIs & Services → 認証情報 → OAuth 2.0 クライアント ID
2. アプリケーションの種類：**Android**
3. パッケージ名：`com.example.test_pj`
4. SHA-1：デバッグキーは以下で取得

```bash
keytool -keystore ~/.android/debug.keystore -list -v \
  -alias androiddebugkey -storepass android -keypass android
```

### 4. ビルド＆インストール

```bash
./gradlew installDebug
```

## ライセンス

MIT
