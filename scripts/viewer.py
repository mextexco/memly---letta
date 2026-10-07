#!/usr/bin/env python3
"""ローカルLettaの記憶ビューア。 python3 scripts/viewer.py → http://localhost:8290
CORS回避のため /v1 を LETTA_BASE_URL へ中継する（GETのみ・embeddingは除去）。"""
import json, os, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = os.environ.get("LETTA_BASE_URL", "http://localhost:8283")
AGENT = os.environ.get("LETTA_AGENT_ID", "agent-2220d8b9-bcb8-410d-bea4-f572bc2b3dfb")
PORT = int(os.environ.get("VIEWER_PORT", "8290"))

HTML = """<!doctype html><html lang=ja><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>Letta Memory</title>
<style>
:root{color-scheme:dark}
body{margin:0;background:#061a0c;color:#c8f5d0;font:14px/1.6 ui-monospace,Menlo,'Hiragino Sans','Hiragino Kaku Gothic ProN','Yu Gothic',Meiryo,monospace}
header{position:sticky;top:0;background:#061a0c;border-bottom:1px solid #00ff4155;padding:10px 16px;display:flex;gap:12px;align-items:center;flex-wrap:wrap}
h1{font-size:15px;margin:0;color:#00ff41}
button,input{font-family:inherit;background:#0a2a12;color:#c8f5d0;border:1px solid #00ff4177;padding:5px 10px;font:inherit}
button.on{background:#00ff41;color:#061a0c}
main{padding:16px;max-width:900px;margin:auto}
.card{border:1px solid #00ff4144;padding:10px 14px;margin-bottom:12px;white-space:pre-wrap;word-break:break-word}
.meta{color:#00ff41aa;font-size:12px;margin-bottom:4px}
.tag{border:1px solid #00ff4166;padding:0 6px;margin-left:6px}
</style>
<header><h1 id=t>Letta Memory</h1>
<button id=b1 class=on>コアメモリ</button><button id=b2>長期記憶</button>
<input id=q placeholder="絞り込み" size=20><span id=n></span></header>
<main id=m></main>
<script>
const A='/v1/agents/%AGENT%';
let mode='core',items=[],done=false,busy=false;
const $=s=>document.querySelector(s);
const esc=s=>String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
async function j(u){const r=await fetch(u);if(!r.ok)throw new Error(r.status);return r.json()}
function render(){
  const q=$('#q').value.toLowerCase();
  const list=items.filter(x=>!q||JSON.stringify(x).toLowerCase().includes(q));
  $('#n').textContent=list.length+'件'+(mode=='arch'&&!done?'（読込中）':'');
  $('#m').innerHTML=list.map(x=>mode=='core'
    ?`<div class=card><div class=meta>${esc(x.label)} / ${x.value.length}文字</div>${esc(x.value)}</div>`
    :`<div class=card><div class=meta>${esc(x.created_at||'')}${(x.tags||[]).map(t=>`<span class=tag>${esc(t)}</span>`).join('')}</div>${esc(x.text)}</div>`).join('');
}
async function loadMore(){
  if(busy||done||mode!='arch')return;busy=true;
  const after=items.length?'&before='+items[items.length-1].id:'';
  const r=await j(`${A}/archival-memory?limit=100&ascending=false${after}`);
  items=items.concat(r);if(r.length<100)done=true;busy=false;render();
  if(!done)loadMore();
}
async function show(m){
  mode=m;items=[];done=false;busy=false;
  $('#b1').className=m=='core'?'on':'';$('#b2').className=m=='arch'?'on':'';
  if(m=='core'){items=await j(A+'/core-memory/blocks');render()}else{render();loadMore()}
}
$('#b1').onclick=()=>show('core');$('#b2').onclick=()=>show('arch');$('#q').oninput=render;
j(A).then(a=>$('#t').textContent='Letta: '+a.name).catch(()=>{});
show('core').catch(e=>$('#m').textContent='取得失敗: '+e);
</script>"""


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, code, body, ctype):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/":
            return self.send(200, HTML.replace("%AGENT%", AGENT).encode(), "text/html; charset=utf-8")
        if self.path.startswith("/v1/"):
            try:
                with urllib.request.urlopen(BASE + self.path, timeout=30) as r:
                    data = json.load(r)
            except Exception as e:
                return self.send(502, str(e).encode(), "text/plain")
            if isinstance(data, list):
                for x in data:
                    if isinstance(x, dict):
                        x.pop("embedding", None)
            return self.send(200, json.dumps(data, ensure_ascii=False).encode(), "application/json")
        self.send(404, b"not found", "text/plain")


if __name__ == "__main__":
    print(f"http://localhost:{PORT}  -> {BASE}")
    ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
