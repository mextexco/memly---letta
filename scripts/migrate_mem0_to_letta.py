#!/usr/bin/env python3
"""mem0 に溜まった記憶を Letta エージェントへ移行する。

指示書 §B の方針どおり、archival memory へ直接 insert せず「エージェントへの通常メッセージ」
として送り、残すかどうかをエージェント自身に判断させる。そのため 1 件ごとに LLM 呼び出しが
走り、時間もトークンも消費する。

既定はドライラン。実行するには --apply を付ける。

  export MEM0_API_KEY=...
  python3 scripts/migrate_mem0_to_letta.py \
      --user-id <mem0のuser_id> \
      --agent-id agent-xxxxxxxx \
      --limit 5                      # まず少数で試す
  python3 scripts/migrate_mem0_to_letta.py ... --apply

進捗は state ファイルに記録され、途中で止めても再実行で続きから流せる。
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

MEM0_BASE = "https://api.mem0.ai"


def http(method, url, headers=None, body=None, timeout=180):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raise SystemExit(f"HTTP {e.code} {method} {url}\n{e.read().decode()[:500]}")


def fetch_mem0(api_key, user_id):
    url = f"{MEM0_BASE}/v1/memories/?" + urllib.parse.urlencode({"user_id": user_id})
    return http("GET", url, {"Authorization": f"Token {api_key}"})


def send_to_agent(base_url, agent_id, token, text):
    url = f"{base_url.rstrip('/')}/v1/agents/{agent_id}/messages"
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    prompt = (
        "以下は別の記憶システム(mem0)に保存されていた私に関する記憶です。"
        "内容を確認し、長期的に覚えておく価値があると判断したものだけを archival memory に保存してください。"
        "既に知っている内容と重複する場合は保存せず、統合してください。\n\n"
        f"---\n{text}\n---"
    )
    return http("POST", url, headers, {"messages": [{"role": "user", "content": prompt}]})


def load_state(path):
    if os.path.exists(path):
        with open(path) as f:
            return set(json.load(f))
    return set()


def save_state(path, done):
    with open(path, "w") as f:
        json.dump(sorted(done), f, ensure_ascii=False, indent=1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--user-id", required=True, help="mem0 側の user_id")
    ap.add_argument("--agent-id", required=True, help="移行先の Letta エージェント")
    ap.add_argument("--base-url", default=os.environ.get("LETTA_BASE_URL", "http://localhost:8283"))
    ap.add_argument("--token", default=os.environ.get("LETTA_TOKEN", ""), help="Letta Cloud のみ必要")
    ap.add_argument("--limit", type=int, default=0, help="送る件数の上限（0 で全件）")
    ap.add_argument("--sleep", type=float, default=2.0, help="1 件ごとの待機秒")
    ap.add_argument("--state", default=".migrate_state.json", help="処理済み mem0 id の記録先")
    ap.add_argument("--apply", action="store_true", help="実際に送信する（省略時はドライラン）")
    args = ap.parse_args()

    api_key = os.environ.get("MEM0_API_KEY")
    if not api_key:
        sys.exit("MEM0_API_KEY が未設定")

    memories = fetch_mem0(api_key, args.user_id)
    done = load_state(args.state)
    pending = [m for m in memories if m.get("id") not in done]
    if args.limit:
        pending = pending[: args.limit]

    print(f"mem0 全 {len(memories)} 件 / 処理済み {len(done)} 件 / 今回対象 {len(pending)} 件")
    if not args.apply:
        print("\n--- ドライラン（--apply で実行）---")
        for m in pending:
            print(f"  [{m.get('id')[:8]}] {m.get('memory', '')[:70]}")
        return

    ok = 0
    for i, m in enumerate(pending, 1):
        text = m.get("memory", "")
        if not text.strip():
            continue
        print(f"[{i}/{len(pending)}] 送信中: {text[:50]}...", flush=True)
        try:
            send_to_agent(args.base_url, args.agent_id, args.token, text)
            done.add(m["id"])
            save_state(args.state, done)
            ok += 1
        except SystemExit as e:
            print(f"  失敗: {e}", file=sys.stderr)
            break
        time.sleep(args.sleep)

    print(f"\n完了: {ok} 件送信。エージェント側の archival memory を確認すること。")


if __name__ == "__main__":
    main()
