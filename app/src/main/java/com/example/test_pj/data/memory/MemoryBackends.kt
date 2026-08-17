package com.example.test_pj.data.memory

import com.example.test_pj.BuildConfig
import com.example.test_pj.data.letta.LettaApi
import com.example.test_pj.data.mem0.Mem0Api

/**
 * 使用する記憶バックエンドの提供元。
 *
 * 切り替えは `local.properties` の `MEMORY_BACKEND`（`mem0` / `letta`）で行う。
 * HTTP クライアントを使い回すため、アプリ全体で 1 インスタンスを共有する。
 */
object MemoryBackends {

    val current: MemoryBackend by lazy {
        when (BuildConfig.MEMORY_BACKEND.lowercase()) {
            "letta" -> LettaBackend(
                LettaApi(
                    baseUrl = BuildConfig.LETTA_BASE_URL,
                    agentId = BuildConfig.LETTA_AGENT_ID,
                    token = BuildConfig.LETTA_TOKEN,
                )
            )
            else -> Mem0Backend(Mem0Api(BuildConfig.MEM0_API_KEY))
        }
    }
}
