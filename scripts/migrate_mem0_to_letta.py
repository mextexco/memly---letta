#!/usr/bin/env python3
"""mem0 に溜まった記憶を Letta エージェントへ移行する。

指示書 §B の方針どおり、archival memory へ直接 insert せず「エージェントへの通常メッセージ」
として送り、残すかどうか・core と archival のどちらに入れるかをエージェント自身に判断させる。
そのため 1 件ごとに LLM 呼び出しが走り、時間もトークンも消費する（実測 1件あたり 10〜20秒）。

mem0 側は読むだけで、削除も変更もしない。

【日付について】エージェント経由（既定）だと、エージェント自身が archival_memory_insert を
呼ぶため created_at を指定できず、**実行時刻**が入る。元の記録日時を保ちたい場合は
--direct を使う（archive 直下へ投入し created_at を明示的に渡す）。
既にエージェント経由で入れてしまった分は、メッセージ履歴に残る「記録日」と
そのターンの insert 本文を突き合わせれば後から復元できる（2026-08-20 に実施）。

既定はドライラン。実行するには --apply を付ける。

  export MEM0_API_KEY=...   # 省略時は ~/.claude.json の mem0 MCP 設定から拾う
  python3 scripts/migrate_mem0_to_letta.py --user-id <id> --agent-id <agent-id> --limit 3
  python3 scripts/migrate_mem0_to_letta.py --user-id <id> --agent-id <agent-id> --limit 3 --apply

進捗は state ファイルに記録され、途中で止めても再実行で続きから流せる。
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

MEM0_BASE = "https://api.mem0.ai"


def resolve_key() -> str:
    key = os.environ.get("MEM0_API_KEY")
    if key:
        return key
    # Claude の mem0 MCP 設定から拾う（このマシンでの利便のため）
    path = os.path.expanduser("~/.claude.json")
    if os.path.exists(path):
        m = re.search(r"(m0-[A-Za-z0-9_\-]{16,})", open(path).read())
        if m:
            return m.group(1)
    sys.exit("MEM0_API_KEY が見つからない")


def http(method, url, headers=None, body=None, timeout=300):
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
        raise RuntimeError(f"HTTP {e.code} {method} {url}\n{e.read().decode()[:400]}")


def fetch_mem0(api_key, user_id):
    url = f"{MEM0_BASE}/v1/memories/?" + urllib.parse.urlencode({"user_id": user_id})
    return http("GET", url, {"Authorization": f"Token {api_key}"})


def is_session_index(text: str) -> bool:
    """CLAUDE.md の運用で mem0 側に置いている「どのClaudeで話したか」の索引。
    mem0 を検索して使う前提のものなので Letta には移さない。"""
    return text.startswith("セッション索引")


def insert_direct(base_url, archive_id, token, text, created_at):
    """エージェントを通さず archive へ直接 passage を作る。

    `/v1/agents/{id}/archival-memory` は内部で LLM を呼ぶため Anthropic のクレジットを消費するが、
    archive 直下のこの経路は埋め込み(OpenAI)だけで済む。取捨選択・重複排除・翻訳は行われないため、
    後から選別できるよう tag を付ける。

    注意: archive 側の embedding_config がエージェントと揃っている必要がある。
    エージェントの埋め込みモデルを変えても archive には自動追従しないため、
    ずれていると OpenAI が 404 を返す。
    """
    url = f"{base_url.rstrip('/')}/v1/archives/{archive_id}/passages"
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    body = {"text": text, "tags": ["mem0-import"]}
    if created_at:
        body["created_at"] = created_at  # 元の記録日を保つ
    return http("POST", url, headers, body)


def send_to_agent(base_url, agent_id, token, text, created_at):
    url = f"{base_url.rstrip('/')}/v1/agents/{agent_id}/messages"
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    day = (created_at or "")[:10]
    prompt = (
        "【過去記憶の一括インポート】\n"
        "以前使っていた記憶システム(mem0)に保存されていた私に関する記憶です。"
        f"記録日: {day}\n"
        "内容を確認し、長期的に覚えておく価値があると判断したものだけを保存してください。\n"
        "- 既に知っている内容と重複する場合は保存せず、必要なら統合してください\n"
        "- 英語で書かれているものは mem0 が自動英訳したものです。日本語で保存してください\n"
        "- 人物像そのものに関わることは core memory、個別の出来事は archival memory へ\n"
        "- 返事は短くて構いません\n\n"
        f"---\n{text}\n---"
    )
    return http("POST", url, headers, {"messages": [{"role": "user", "content": prompt}]})


def summarize_turn(resp):
    """エージェントが何をしたかを1行にする。"""
    tools, reply = [], ""
    for m in (resp or {}).get("messages", []):
        t = m.get("message_type")
        if t == "tool_call_message":
            n = (m.get("tool_call") or {}).get("name")
            if n:
                tools.append(n)
        elif t == "assistant_message":
            c = m.get("content")
            if isinstance(c, str):
                reply = c
    return (",".join(tools) or "保存なし"), reply.replace("\n", " ")[:60]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--user-id", required=True, help="mem0 側の user_id")
    ap.add_argument("--agent-id", required=True, help="移行先の Letta エージェント")
    ap.add_argument("--base-url", default=os.environ.get("LETTA_BASE_URL", "http://localhost:8283"))
    ap.add_argument("--token", default=os.environ.get("LETTA_TOKEN", ""), help="Letta Cloud のみ必要")
    ap.add_argument("--limit", type=int, default=0, help="送る件数の上限（0 で全件）")
    ap.add_argument("--sleep", type=float, default=1.5, help="1 件ごとの待機秒")
    ap.add_argument("--state", default=".migrate_state.json", help="処理済み mem0 id の記録先")
    ap.add_argument("--apply", action="store_true", help="実際に送信する（省略時はドライラン）")
    ap.add_argument("--direct", action="store_true",
                    help="エージェントに判断させず archive へ直接入れる（LLM不使用・高速）")
    ap.add_argument("--archive-id", default="", help="--direct のときの投入先 archive")
    args = ap.parse_args()

    memories = fetch_mem0(resolve_key(), args.user_id)
    # 古い順に送り、エージェントが時系列で理解できるようにする
    memories.sort(key=lambda m: m.get("created_at", ""))

    done = set(json.load(open(args.state))) if os.path.exists(args.state) else set()
    skipped = [m for m in memories if is_session_index(m.get("memory", ""))]
    target = [m for m in memories
              if not is_session_index(m.get("memory", ""))
              and m.get("id") not in done
              and m.get("memory", "").strip()]
    if args.limit:
        target = target[: args.limit]

    print(f"mem0 全 {len(memories)} 件 / セッション索引 {len(skipped)} 件は対象外 / "
          f"処理済み {len(done)} 件 / 今回 {len(target)} 件")

    if not args.apply:
        print("\n--- ドライラン（--apply で実行）---")
        for m in target:
            print(f"  [{m.get('created_at','')[:10]}] {m.get('memory','')[:64]}")
        return

    if args.direct:
        if not args.archive_id:
            sys.exit("--direct には --archive-id が必要")
        print("モード: archive へ直接投入（エージェントの判断なし / tag=mem0-import）")

    ok = 0
    for i, m in enumerate(target, 1):
        text = m["memory"]
        print(f"[{i}/{len(target)}] {m.get('created_at','')[:10]} {text[:40]}...", flush=True)
        try:
            if args.direct:
                insert_direct(args.base_url, args.archive_id, args.token, text, m.get("created_at"))
                print("     → archival_memory に投入", flush=True)
            else:
                resp = send_to_agent(args.base_url, args.agent_id, args.token, text, m.get("created_at"))
                tools, reply = summarize_turn(resp)
                print(f"     → {tools} | {reply}", flush=True)
            done.add(m["id"])
            json.dump(sorted(done), open(args.state, "w"), ensure_ascii=False, indent=1)
            ok += 1
        except Exception as e:
            print(f"     失敗: {e}", file=sys.stderr, flush=True)
            break
        time.sleep(args.sleep)

    print(f"\n完了: {ok} 件送信。残り {len(target)-ok} 件は再実行で続きから流せる。")


if __name__ == "__main__":
    main()
