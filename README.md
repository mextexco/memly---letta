# Memly

音声・テキストで日々の記憶を気軽に記録する Android アプリ。  
記憶エンジンに自宅セルフホストの [Letta](https://letta.com) を使い、
**何をどう覚えるかはエージェント自身が判断する**。

## スクリーンショット

| ホーム画面 | 記憶一覧 |
|-----------|---------|
| テキスト・音声で記録 | 検索・編集・削除 |

## 機能

- 🎤 **音声入力** — Android 標準の音声認識でハンズフリー入力
- ✍️ **テキスト入力** — キーボードでの直接入力にも対応
- 🧠 **エージェントが記憶を管理** — 保存すると、統合するか新規に書くかをエージェントが自分で決める。何をしたかは画面に出る
- 💬 **記憶に聞く** — 検索窓ではなく質問。エージェントが自分の記憶をたどって答える
- 👤 **私の人物像** — エージェントが持つ core memory（human / persona）を覗ける
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
| 記憶エンジン | Letta（セルフホスト） |

## セットアップ

### 1. リポジトリをクローン

```bash
git clone https://github.com/mextexco/memly.git
cd memly
```

### 2. `local.properties` に API キーを追加

```properties
sdk.dir=/path/to/Android/sdk
GOOGLE_WEB_CLIENT_ID=your_google_web_client_id

LETTA_BASE_URL=http://100.x.x.x:8283
LETTA_AGENT_ID=agent-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
# LETTA_TOKEN=  # Letta Cloud を使う場合のみ
```

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

## 記憶の作り

memly は記憶を自分で操作しない。ユーザーの入力はそのままエージェントへのメッセージとして送られ、
エージェントが `memory_replace` / `archival_memory_insert` などを自分で選んで記憶を書き換える。

| 画面 | やっていること |
|---|---|
| ホーム「記憶する」 | `POST /v1/agents/{id}/messages`。返事と、エージェントが行った記憶操作を表示 |
| 記憶に聞く | 同じく `messages`。エージェントが自分の記憶を検索して答える |
| 私の人物像 | `GET /core-memory/blocks`。human / persona を表示 |
| 長期記憶を見る | `GET /archival-memory`。溜まった passage の閲覧と削除 |

記憶の**編集**機能は持たない。Letta の archival memory に更新 API が無いこともあるが、
本質的には「どう書き換えるか」をエージェントに任せる設計だから。直したいときは話しかける。

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

エミュレータからホストの Letta を見る場合は `LETTA_BASE_URL=http://10.0.2.2:8283`。
実機から自宅の Letta に繋ぐには Tailscale を Mac と端末の両方に入れ、Mac の Tailscale IP を指定する。
平文 HTTP になるため `usesCleartextTraffic` を有効にしてある。

### mem0 からの移行

以前は mem0 を使っていた。既存の記憶は archival memory へ直接書かず、
エージェントへのメッセージとして送って取捨選択・要約させる。既定はドライラン。

```bash
export MEM0_API_KEY=...
python3 scripts/migrate_mem0_to_letta.py --user-id <id> --agent-id <agent-id> --limit 5
python3 scripts/migrate_mem0_to_letta.py --user-id <id> --agent-id <agent-id> --apply
```

## ライセンス

MIT
