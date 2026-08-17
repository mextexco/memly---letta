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

# 記憶バックエンドの切り替え（省略時は mem0）
MEMORY_BACKEND=mem0
# MEMORY_BACKEND=letta のときに使用
LETTA_BASE_URL=http://100.x.x.x:8283
LETTA_AGENT_ID=agent-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
# LETTA_TOKEN=  # Letta Cloud を使う場合のみ
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

## 記憶バックエンド

記憶の保存先は `MEMORY_BACKEND` で差し替えられる。UI・ViewModel は
`data/memory/MemoryBackend` インターフェース越しにしか記憶を触らない。

| | mem0（クラウド） | Letta（セルフホスト） |
|---|---|---|
| 一覧 / 検索 / 追加 / 削除 | ✅ | ✅ |
| 編集（id 指定の更新） | ✅ | ❌ API 自体が無い |
| 保存先 | mem0 の memories | エージェントの archival memory (passages) |
| 認証 | API キー | 不要（セルフホスト） |

Letta を選ぶと、更新 API が存在しないため記憶一覧の編集ボタンは自動的に非表示になる
（`MemoryBackend.supportsEdit`）。

### Letta 側の準備

Docker でサーバーを起動し、チャット用に Anthropic、埋め込み用に OpenAI のキーを渡す。

```bash
docker run -d --name letta-server \
  -v ~/.letta/.persist/pgdata:/var/lib/postgresql/data \
  -p 8283:8283 \
  -e ANTHROPIC_API_KEY="..." \
  -e OPENAI_API_KEY="..." \
  letta/letta:latest

curl http://localhost:8283/v1/agents/   # LETTA_AGENT_ID を確認
```

スマホから自宅の Letta に繋ぐには Tailscale を Mac と端末の両方に入れ、
`LETTA_BASE_URL` に Mac の Tailscale IP を指定する。平文 HTTP になるため
`usesCleartextTraffic` を有効にしてある。

### mem0 からの移行

```bash
export MEM0_API_KEY=...
python3 scripts/migrate_mem0_to_letta.py --user-id <id> --agent-id <agent-id> --limit 5
python3 scripts/migrate_mem0_to_letta.py --user-id <id> --agent-id <agent-id> --apply
```

既存記憶を archival memory へ直接書かず、エージェントへのメッセージとして送って
取捨選択・要約させる。既定はドライラン。

## ライセンス

MIT
