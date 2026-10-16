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
| 長期記憶を見る | `GET /archival-memory`。溜まった passage をタグ付きで表示。本文・タグで絞り込み、削除も可 |

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

### 構成図

```
   Androidアプリ (memly-letta)          ブラウザ (自作ビューア)
   +-----------------------+         +---------------------------+
   | 記録 / 記憶に聞く      |         | http://localhost:8290      |
   | 記憶一覧 / 人物像      |         | scripts/viewer.py          |
   +-----------+-----------+         | (読み取り専用・/v1を中継)    |
               |                     +-------------+-------------+
               | Tailscale経由                      | localhost
               | http://100.x.x.x:8283              |
               v                                    v
+-------------------------------------------------------------------+
|  MacBook (ローカル)                                                  |
|                                                                     |
|  +---------------------------------------------------------------+ |
|  |  Docker Container: letta-server  (ポート 8283)                  | |
|  |                                                                 | |
|  |   +--------------------+                                       | |
|  |   |   test-agent        |                                      | |
|  |   |                      |                                     | |
|  |   |  -- メモリ(2種類) --                                          | |
|  |   |                      |                                     | |
|  |   |  [1] Core Memory     |  短期・常時プロンプトに乗る               | |
|  |   |      - human block   |  (ユーザーの情報)                       | |
|  |   |      - persona block |  (エージェントの人格)                    | |
|  |   |                      |                                     | |
|  |   |  [2] Archival Memory |  長期・検索型(vector search)             | |
|  |   |      - passages      |  必要な時だけ引っ張り出す                  | |
|  |   |        (本文+タグ)    |                                       | |
|  |   +--------------------+                                       | |
|  |            |                     |                              | |
|  |            |                     +--- embedding変換 --+          | |
|  |            v                                          |          | |
|  |   +--------------------+                               |          | |
|  |   | Postgres 15+pgvector|<--- archival memoryの保存先 --+          | |
|  |   | (永続化volume)       |                                       | |
|  |   +--------------------+                                       | |
|  +---------------------+--------------------------+----------------+ |
|                         |                          |                  |
+-------------------------|--------------------------|------------------+
                           |                          |
                 チャット用 |                          | embedding用
                           v                          v
             +---------------------+     +--------------------------+
             |   Anthropic API      |     |      OpenAI API           |
             |  claude-haiku-4-5    |     | text-embedding-3-small    |
             |  (会話の生成)          |     | (テキスト→ベクトル変換)       |
             +---------------------+     +--------------------------+
```

- Core Memory (human/persona) はプロンプトに常時載る軽量メモリ。Anthropicの会話生成にそのまま使われる。
- Archival Memory は別枠の長期記憶。書き込み時にOpenAIでベクトル化 -> Postgres(pgvector)に保存。
  検索時も同じくOpenAIでクエリをベクトル化して類似検索する。
- サーバー本体(Docker)と実データ(Postgres)はローカル、LLMの「頭脳」部分だけクラウド(Anthropic/OpenAI)を借りている構成。
- データ永続化: ~/.letta/.persist/pgdata をコンテナにマウントしているため、docker rm しても
  Core Memory / Archival Memory ともに消えない。
- 課金はAnthropic(会話)とOpenAI(embedding)の2社。Letta本体は無料。
- Web画面: 公式ADE(app.letta.com)は chat.letta.com に統合され、セルフホストのサーバーには
  接続できない。代わりに自作ビューア(scripts/viewer.py)とAndroidアプリで記憶を見る。
- バックアップ: docker exec letta-server pg_dump -U letta -Fc letta > ~/letta-backups/letta-日時.dump

### ブラウザで記憶を見る

公式 ADE（app.letta.com）は chat.letta.com に統合され、セルフホストのサーバーには繋げない。
代わりに読み取り専用のビューアを同梱している。

```bash
python3 scripts/viewer.py   # http://localhost:8290
```

### バックアップ

```bash
docker exec letta-server pg_dump -U letta -Fc letta > ~/letta-backups/letta-$(date +%Y%m%d-%H%M).dump
```

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
