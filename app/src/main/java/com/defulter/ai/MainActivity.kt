package com.defulter.ai

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

private data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
            }
        }
        val prefs = getSharedPreferences("defulter_settings", MODE_PRIVATE)

        setContent {
            val messages = remember {
                mutableStateListOf(
                    ChatMessage("नमस्ते! मैं DEFULTER AI हूँ। Chat शुरू करें या mic दबाकर बोलें।", false)
                )
            }
            var input by remember { mutableStateOf("") }
            var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
            var showSettings by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var pendingApp by remember { mutableStateOf<String?>(null) }
            var speakReplies by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()
            val gemini = remember { GeminiClient() }

            val voiceLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val spoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) input = spoken
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF66D9EF),
                    background = Color(0xFF07111F),
                    surface = Color(0xFF101E30)
                )
            ) {
                Scaffold(containerColor = Color(0xFF07111F)) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DEFULTER AI", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(
                                    "VOICE  •  CHAT  •  APPROVAL AUTOMATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF66D9EF)
                                )
                            }
                            TextButton(onClick = { showSettings = true }) { Text("Settings") }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages) { message ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        color = if (message.fromUser) Color(0xFF17445A) else Color(0xFF142338),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.widthIn(max = 310.dp)
                                    ) {
                                        Text(
                                            message.text,
                                            color = Color.White,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                            if (busy) item { Text("DEFULTER सोच रहा है…", color = Color(0xFF66D9EF)) }
                        }

                        pendingApp?.let { appName ->
                            Surface(color = Color(0xFF28344A), shape = RoundedCornerShape(14.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Approval required", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    Text("क्या आप \"$appName\" app खोलना चाहते हैं?", color = Color.White)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            val target = pendingApp
                                            pendingApp = null
                                            if (target != null) {
                                                val launched = launchInstalledApp(target)
                                                messages.add(
                                                    ChatMessage(
                                                        if (launched) "$target खोल दिया गया।" else "$target app नहीं मिला।",
                                                        false
                                                    )
                                                )
                                            }
                                        }) { Text("Approve") }
                                        OutlinedButton(onClick = {
                                            pendingApp = null
                                            messages.add(ChatMessage("Action cancelled.", false))
                                        }) { Text("Cancel") }
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Message DEFULTER…") },
                                maxLines = 3
                            )
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलिए")
                                }
                                runCatching { voiceLauncher.launch(intent) }.onFailure {
                                    messages.add(ChatMessage("इस device पर speech recognition उपलब्ध नहीं है।", false))
                                }
                            }) { Text("🎙", color = Color(0xFF66D9EF)) }

                            Button(
                                enabled = input.isNotBlank() && !busy && pendingApp == null,
                                onClick = {
                                    val prompt = input.trim()
                                    input = ""
                                    messages.add(ChatMessage(prompt, true))
                                    val isOpenRequest = prompt.startsWith("open ", true) || prompt.startsWith("ओपन ")
                                    if (isOpenRequest) {
                                        val target = if (prompt.startsWith("ओपन ")) {
                                            prompt.removePrefix("ओपन ").trim()
                                        } else {
                                            prompt.substringAfter(" ", "").trim()
                                        }
                                        if (target.isBlank()) {
                                            messages.add(ChatMessage("किस app को खोलना है?", false))
                                        } else {
                                            pendingApp = target
                                        }
                                    } else {
                                        busy = true
                                        scope.launch {
                                            try {
                                                val answer = gemini.reply(apiKey, prompt)
                                                messages.add(ChatMessage(answer, false))
                                                if (speakReplies) {
                                                    tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "defulter_reply")
                                                }
                                            } catch (error: Exception) {
                                                messages.add(ChatMessage(error.message ?: "कुछ गलत हुआ।", false))
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                }
                            ) { Text("Send") }
                        }
                        Text(
                            "Actions execute only after your approval. App launch is the first supported action.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (showSettings) {
                    var draft by remember { mutableStateOf(apiKey) }
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        title = { Text("Settings") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Gemini API key")
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    placeholder = { Text("Paste API key") },
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = speakReplies, onCheckedChange = { speakReplies = it })
                                    Text("Speak replies")
                                }
                                Text(
                                    "Key इस device की app preferences में रखी जाती है। इसे public या shared device पर न डालें।",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                apiKey = draft.trim()
                                prefs.edit().putString("gemini_key", apiKey).apply()
                                showSettings = false
                            }) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSettings = false }) { Text("Close") }
                        }
                    )
                }
            }
        }
    }

    private fun launchInstalledApp(query: String): Boolean {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = packageManager.queryIntentActivities(launcherIntent, 0)
        val match = matches.firstOrNull { info ->
            info.loadLabel(packageManager).toString().contains(query, ignoreCase = true)
        } ?: return false
        val launchIntent = packageManager.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        startActivity(launchIntent)
        return true
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
