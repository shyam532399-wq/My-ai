package com.defulter.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class GeminiClient {
    private val client = OkHttpClient()

    suspend fun reply(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Settings में Gemini API key जोड़ें।" }
        val parts = org.json.JSONArray().put(JSONObject().put("text", prompt))
        val contents = org.json.JSONArray().put(JSONObject().put("parts", parts))
        val payload = JSONObject().put("contents", contents).toString()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "Gemini request failed (${response.code}).")
            }
            JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text")?.takeIf { it.isNotBlank() }
                ?: "मुझे इस बार जवाब नहीं मिला। फिर कोशिश करें।"
        }
    }
}
