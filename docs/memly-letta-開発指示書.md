# memly × Letta連携 開発指示書
作成日: 2026-08-17
作成者: 市原崇(Letta agentとの壁打ちより整理)

## 背景
- memlyは既存の自作アプリ。記憶バックエンドとしてmem0(クラウド)を使用中。
- 長期記憶・パーソナライズの実験としてLetta(自宅MacBookにセルフホスト)を導入。
- LettaはOSSの記憶管理エンジン。agent自身が会話文脈を判断してmemoryを自己編集する点がmem0と異なる強み。

## やりたいこと
1. memlyの記憶バックエンドを「mem0」「Letta」で選択可能にする(アダプター化)
2. mem0に既存保存済みの記憶をLettaへマージする
3. スマホ(Pixel 10)から自宅MacBookのLettaに接続できるようにする

## 制約条件
- MacBookは常時起動ではない(スリープすることが多い)
- WireGuardは契約終了済み、使用不可
- Lettaは自宅MacBook上でDocker稼働。Anthropic API(チャット用)+OpenAI API(embedding用)を役割分担で使用中

## 設計方針

### A. Memory Backend Interfaceの抽象化
[memly UI]
↓
[Memory Backend Interface] ← 抽象化レイヤーを新設
↓                ↓
[mem0 Adapter]   [Letta Adapter]
↓                ↓
mem0 API        Letta REST API (http://<tailscale-ip>:8283)
- memly内の「保存」「検索」呼び出し箇所を、共通インターフェース経由に変更
- 設定(環境変数 or config)でバックエンドを切り替え可能にする

### B. mem0 → Letta マージ方針
- mem0から既存レコードを全件エクスポート
- 各レコードは直接archival_memory_insertせず、**Letta agentへの通常メッセージとして送信**し、agent自身に取捨選択・要約させる(記憶の一貫性を保つため)
- 移行期間はmem0とLetta並走、最終的にLettaへ一本化予定

### C. mem0を「即時バッファ」、Lettaを「本記憶」とする役割分担
- 外出先などMacBookが起きていない時 → mem0に即時保存
- MacBook起動時にmem0の新着分をLettaが読み込み、判断して統合(バッチ同期)

### D. スマホからのLetta接続
- WireGuardの代替として**Tailscale**を導入(MacBook・Pixel 10両方にインストール)
- Cloudflare Tunnelも代替候補として検討可
- MacBookのスリープ設定を調整し、極力起動状態を維持

## 次のアクション(Claude Codeへの依頼事項)
1. memlyの既存コードを確認し、mem0呼び出し箇所を洗い出す
2. Memory Backend Interfaceを設計・実装(mem0 Adapter / Letta Adapter)
3. Letta REST APIの仕様確認(passages create/search等のエンドポイント)
4. Tailscaleセットアップ(Mac/Pixel 10)、疎通確認
5. mem0既存データのエクスポート・Letta移行スクリプト作成

## 参考: 現在のLetta構成メモ
- 自宅DockerでLettaサーバー稼働
- Anthropic APIキー: チャット用LLM(claude-sonnet系)
- OpenAI APIキー: embedding専用(archival memory保存時のベクトル化)
- 環境変数: `-e ANTHROPIC_API_KEY`, `-e OPENAI_API_KEY` でコンテナに渡す
