package com.defulter.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun reply(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Settings में Gemini API key जोड़ें।" }

        val parts = org.json.JSONArray().put(JSONObject().put("text", prompt))
        val contents = org.json.JSONArray().put(JSONObject().put("parts", parts))
        val payload = JSONObject().put("contents", contents).toString()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()

            if (!response.isSuccessful) {
                val providerMessage = json
                    ?.optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                val hint = when (response.code) {
                    400 -> "Request सही नहीं है।"
                    401, 403 -> "Gemini API key या उसकी permissions जाँचें।"
                    404 -> "Gemini model/API उपलब्ध नहीं है। Google AI Studio में key और model access जाँचें।"
                    429 -> "Gemini की usage limit पूरी हुई है। कुछ समय बाद फिर कोशिश करें।"
                    in 500..599 -> "Google Gemini server में अस्थायी समस्या है।"
                    else -> "Gemini request असफल हुई।"
                }
                throw IOException(providerMessage ?: "$hint (HTTP ${response.code})")
            }

            val candidates = json?.optJSONArray("candidates")
            val responseParts = candidates
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")

            val answer = buildString {
                if (responseParts != null) {
                    for (i in 0 until responseParts.length()) {
                        val text = responseParts.optJSONObject(i)?.optString("text").orEmpty()
                        if (text.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(text)
                        }
                    }
                }
            }.trim()

            answer.takeIf { it.isNotBlank() }
                ?: "इस बार Gemini से जवाब नहीं मिला। कृपया फिर कोशिश करें।"
        }
    }
}
