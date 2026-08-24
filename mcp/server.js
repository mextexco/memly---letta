#!/usr/bin/env node
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";

const BASE_URL = process.env.LETTA_BASE_URL;
const AGENT_ID = process.env.LETTA_AGENT_ID;

if (!BASE_URL || !AGENT_ID) {
  console.error("LETTA_BASE_URL / LETTA_AGENT_ID が未設定（環境変数で渡す）");
  process.exit(1);
}

async function sendMessage(content) {
  const res = await fetch(`${BASE_URL.replace(/\/$/, "")}/v1/agents/${AGENT_ID}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ messages: [{ role: "user", content }] }),
  });
  if (!res.ok) throw new Error(`Letta API error ${res.status}: ${await res.text()}`);
  const data = await res.json();
  const reply = (data.messages ?? [])
    .filter((m) => m.message_type === "assistant_message")
    .map((m) =>
      typeof m.content === "string" ? m.content : (m.content ?? []).map((c) => c.text ?? "").join("\n")
    )
    .join("\n")
    .trim();
  return reply || "(応答なし)";
}

const server = new Server({ name: "memly-letta", version: "0.1.0" }, { capabilities: { tools: {} } });

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "remember",
      description: "Lettaエージェントに記憶させる。何をどこに保存するかはエージェントが判断する。",
      inputSchema: {
        type: "object",
        properties: { content: { type: "string", description: "覚えさせたい内容" } },
        required: ["content"],
      },
    },
    {
      name: "recall",
      description: "Lettaエージェントに記憶を尋ねる。",
      inputSchema: {
        type: "object",
        properties: { question: { type: "string", description: "聞きたいこと" } },
        required: ["question"],
      },
    },
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (req) => {
  const { name, arguments: args } = req.params;
  const content = name === "remember" ? args.content : name === "recall" ? args.question : null;
  if (content == null) throw new Error(`unknown tool: ${name}`);
  const reply = await sendMessage(content);
  return { content: [{ type: "text", text: reply }] };
});

await server.connect(new StdioServerTransport());
