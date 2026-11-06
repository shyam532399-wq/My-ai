package com.defulter.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private data class ChatMessage(val text: String, val fromUser: Boolean)
private data class PendingAction(val kind: String, val title: String, val detail: String, val value: String)

private val Navy = Color(0xFF07111F)
private val Panel = Color(0xFF101E30)
private val Cyan = Color(0xFF66D9EF)
private val Muted = Color(0xFF9BAEC3)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("hi", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
            }
        }
        val prefs = getSharedPreferences("defulter_settings", MODE_PRIVATE)

        setContent {
            var screen by remember { mutableStateOf("Chat") }
            val messages = remember {
                mutableStateListOf(
                    ChatMessage("नमस्ते Boss! मैं DEFULTER AI हूँ। Chat करें या mic से बोलें। किसी भी phone action से पहले आपकी approval ली जाएगी।", false)
                )
            }
            val history = remember {
                mutableStateListOf<String>().apply {
                    val saved = prefs.getString("task_history", "[]") ?: "[]"
                    runCatching {
                        val arr = JSONArray(saved)
                        for (i in 0 until arr.length()) add(arr.optString(i))
                    }
                }
            }
            val memories = remember {
                mutableStateListOf<String>().apply {
                    addAll(prefs.getStringSet("memories", emptySet())?.toList().orEmpty())
                }
            }
            var input by remember { mutableStateOf("") }
            var memoryDraft by remember { mutableStateOf("") }
            var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
            var showSettings by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
            var speakReplies by remember { mutableStateOf(prefs.getBoolean("speak_replies", true)) }
            val scope = rememberCoroutineScope()
            val gemini = remember { GeminiClient() }

            fun saveHistory(entry: String) {
                history.add(0, entry)
                while (history.size > 50) history.removeAt(history.lastIndex)
                val arr = JSONArray()
                history.forEach { arr.put(it) }
                prefs.edit().putString("task_history", arr.toString()).apply()
            }

            fun addAssistant(text: String) {
                messages.add(ChatMessage(text, false))
                if (speakReplies) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "defulter_reply")
            }

            val voiceLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spoken.isNullOrBlank()) input = spoken
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    secondary = Color(0xFF8AA8FF),
                    background = Navy,
                    surface = Panel,
                    onSurface = Color.White
                )
            ) {
                Scaffold(
                    containerColor = Navy,
                    bottomBar = {
                        NavigationBar(containerColor = Panel, contentColor = Color.White) {
                            listOf("Chat", "Memory", "History").forEach { tab ->
                                NavigationBarItem(
                                    selected = screen == tab,
                                    onClick = { screen = tab },
                                    icon = { Text(when (tab) { "Chat" -> "◉"; "Memory" -> "✧"; else -> "◷" }) },
                                    label = { Text(tab) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Cyan,
                                        selectedTextColor = Cyan,
                                        indicatorColor = Color(0xFF203B55),
                                        unselectedTextColor = Muted
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .background(Brush.verticalGradient(listOf(Color(0xFF0A1B30), Navy)))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("DEFULTER AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("VOICE  •  CHAT  •  AUTOMATION", style = MaterialTheme.typography.labelSmall, color = Cyan)
                            }
                            TextButton(onClick = { showSettings = true }) { Text("⚙ Settings", color = Cyan) }
                        }

                        when (screen) {
                            "Chat" -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    AnimatedDefulterOrb()
                                    Text("DEFULTER AI", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("YOUR PERSONAL AI CORE", color = Cyan, style = MaterialTheme.typography.labelSmall)
                                }
                                Text("AI BRAIN  •  GEMINI", color = Muted, style = MaterialTheme.typography.labelSmall)
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
                                                color = if (message.fromUser) Color(0xFF17445A) else Panel,
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.widthIn(max = 320.dp)
                                            ) {
                                                Text(message.text, color = Color.White, modifier = Modifier.padding(12.dp))
                                            }
                                        }
                                    }
                                    if (busy) item { Text("DEFULTER सोच रहा है…", color = Cyan) }
                                }

                                pendingAction?.let { action ->
                                    Surface(color = Color(0xFF20334B), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("APPROVAL REQUIRED", color = Cyan, fontWeight = FontWeight.Bold)
                                            Text(action.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                            Text(action.detail, color = Muted)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = {
                                                    val approved = pendingAction
                                                    pendingAction = null
                                                    if (approved != null) {
                                                        val resultText = runCatching { executeApprovedAction(approved) }.getOrElse { "Action शुरू नहीं हो पाया: ${it.message}" }
                                                        addAssistant(resultText)
                                                        saveHistory("${approved.title} — ${if (resultText.startsWith("हो गया")) "Completed" else resultText}")
                                                    }
                                                }) { Text("Approve") }
                                                OutlinedButton(onClick = {
                                                    pendingAction = null
                                                    addAssistant("ठीक है, action cancel कर दिया।")
                                                    saveHistory("${action.title} — Cancelled")
                                                }) { Text("Cancel") }
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = { input = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("Boss, message लिखें…") },
                                        maxLines = 3,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    IconButton(onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "DEFULTER AI को बोलिए")
                                        }
                                        runCatching { voiceLauncher.launch(intent) }.onFailure {
                                            addAssistant("इस device पर speech recognition service उपलब्ध नहीं है।")
                                        }
                                    }) { Text("🎙", color = Cyan) }
                                    Button(
                                        enabled = input.isNotBlank() && !busy && pendingAction == null,
                                        onClick = {
                                            val prompt = input.trim()
                                            input = ""
                                            messages.add(ChatMessage(prompt, true))
                                            val action = detectAction(prompt)
                                            if (action != null) {
                                                pendingAction = action
                                                saveHistory("${action.title} — Waiting for approval")
                                            } else {
                                                busy = true
                                                scope.launch {
                                                    try {
                                                        val memoryContext = memories.joinToString("\n")
                                                        val fullPrompt = """
                                                            You are DEFULTER AI, a concise Hindi/Hinglish Android assistant.
                                                            Reply in the user's language, normally 1-3 useful sentences.
                                                            Never claim a phone action has been performed. Phone actions require explicit in-app approval.
                                                            Saved user preferences:
                                                            $memoryContext

                                                            User: $prompt
                                                        """.trimIndent()
                                                        addAssistant(gemini.reply(apiKey, fullPrompt))
                                                    } catch (error: Exception) {
                                                        addAssistant(error.message ?: "कुछ गलत हुआ। Settings में Gemini API key जाँचें।")
                                                    } finally {
                                                        busy = false
                                                    }
                                                }
                                            }
                                        }
                                    ) { Text("Send") }
                                }
                                Text("Phone actions: Open app • Share • Navigation — हर action से पहले approval", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            "Memory" -> {
                                Text("MEMORY", color = Cyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("DEFULTER की saved preferences यहाँ manage करें।", color = Muted)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = memoryDraft,
                                        onValueChange = { memoryDraft = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("जैसे: मुझे छोटे Hindi जवाब पसंद हैं") },
                                        maxLines = 2
                                    )
                                    Button(enabled = memoryDraft.isNotBlank(), onClick = {
                                        memories.add(memoryDraft.trim())
                                        prefs.edit().putStringSet("memories", memories.toSet()).apply()
                                        memoryDraft = ""
                                    }) { Text("Add") }
                                }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(memories.toList()) { memory ->
                                        Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(memory, color = Color.White, modifier = Modifier.weight(1f))
                                                TextButton(onClick = {
                                                    memories.remove(memory)
                                                    prefs.edit().putStringSet("memories", memories.toSet()).apply()
                                                }) { Text("Delete", color = Color(0xFFFF8A8A)) }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                Text("TASK HISTORY", color = Cyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("हाल के actions और approval status।", color = Muted)
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(history.toList()) { entry ->
                                        Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                                            Text(entry, color = Color.White, modifier = Modifier.fillMaxWidth().padding(12.dp))
                                        }
                                    }
                                }
                                if (history.isEmpty()) Text("अभी कोई task history नहीं है।", color = Muted)
                            }
                        }
                    }
                }

                if (showSettings) {
                    var draft by remember { mutableStateOf(apiKey) }
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        title = { Text("DEFULTER Settings") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Gemini API key")
                                OutlinedTextField(value = draft, onValueChange = { draft = it }, placeholder = { Text("Paste API key") }, singleLine = true)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = speakReplies, onCheckedChange = {
                                        speakReplies = it
                                        prefs.edit().putBoolean("speak_replies", it).apply()
                                    })
                                    Text("Speak replies")
                                }
                                Text("API key इस device पर local preferences में store होती है। इसे public repo या किसी के साथ share न करें।", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                apiKey = draft.trim()
                                prefs.edit().putString("gemini_key", apiKey).apply()
                                showSettings = false
                            }) { Text("Save") }
                        },
                        dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } }
                    )
                }
            }
        }
    }

    private fun detectAction(text: String): PendingAction? {
        val lower = text.lowercase(Locale.ROOT)
        val openPrefix = listOf("open ", "ओपन ", "खोलो ", "खोल दो ")
        val prefix = openPrefix.firstOrNull { lower.startsWith(it) }
        if (prefix != null) {
            val app = text.drop(prefix.length).trim()
            if (app.isNotBlank()) return PendingAction("open", "Open app: $app", "DEFULTER \"$app\" app खोलने की कोशिश करेगा।", app)
        }
        val sharePrefix = listOf("share ", "शेयर ")
        val share = sharePrefix.firstOrNull { lower.startsWith(it) }
        if (share != null) {
            val content = text.drop(share.length).trim()
            if (content.isNotBlank()) return PendingAction("share", "Share message", "यह text Android share sheet में भेजा जाएगा:\n$content", content)
        }
        val navPrefixes = listOf("navigate to ", "directions to ", "रास्ता बताओ ", "नेविगेशन ")
        val nav = navPrefixes.firstOrNull { lower.startsWith(it) }
        if (nav != null) {
            val destination = text.drop(nav.length).trim()
            if (destination.isNotBlank()) return PendingAction("navigate", "Navigate to: $destination", "Google Maps या उपलब्ध maps app में destination खुलेगा।", destination)
        }
        return null
    }

    private fun executeApprovedAction(action: PendingAction): String {
        when (action.kind) {
            "open" -> {
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val matches = packageManager.queryIntentActivities(launcherIntent, 0)
                val match = matches.firstOrNull { it.loadLabel(packageManager).toString().contains(action.value, true) }
                    ?: return "${action.value} नाम का app नहीं मिला।"
                val launchIntent = packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)
                    ?: return "इस app को launch नहीं कर पाया।"
                startActivity(launchIntent)
                return "हो गया — ${action.value} app खोल दिया।"
            }
            "share" -> {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, action.value)
                }
                startActivity(Intent.createChooser(send, "DEFULTER AI — Share"))
                return "हो गया — Android share sheet खोल दी।"
            }
            "navigate" -> {
                val uri = Uri.parse("google.navigation:q=" + Uri.encode(action.value))
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                if (mapIntent.resolveActivity(packageManager) == null) {
                    mapIntent.data = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(action.value))
                }
                startActivity(mapIntent)
                return "हो गया — navigation खोल दिया।"
            }
        }
        return "यह action अभी supported नहीं है।"
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}


@Composable
private fun AnimatedDefulterOrb() {
    val transition = rememberInfiniteTransition(label = "defulter_orb")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val pulse = transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    Canvas(Modifier.size(190.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.39f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x553D3BFF), Color(0x33210C52), Color.Transparent),
                center = center,
                radius = radius * 1.75f
            ),
            radius = radius * 1.75f,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF141B42), Color(0xFF08091C), Color(0xFF03030D)),
                center = center,
                radius = radius * 1.2f
            ),
            radius = radius * 1.08f,
            center = center
        )
        rotate(rotation.value) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, Color(0xFFE8F7FF), Color(0xFF9D7BFF), Color(0xFF52CFFF), Color.Transparent),
                    center
                ),
                startAngle = 18f,
                sweepAngle = 306f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = Stroke(width = size.minDimension * 0.026f * pulse.value, cap = StrokeCap.Round)
            )
        }
        rotate(-rotation.value * 1.35f) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color(0x00FFFFFF), Color(0xFFB5A0FF), Color(0xFF42C9FF), Color(0x00FFFFFF)),
                    center
                ),
                startAngle = 205f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius * 0.56f, center.y - radius * 0.56f),
                size = androidx.compose.ui.geometry.Size(radius * 1.12f, radius * 1.12f),
                style = Stroke(width = size.minDimension * 0.045f, cap = StrokeCap.Round)
            )
        }
        drawCircle(
            color = Color(0xFF9E80FF).copy(alpha = 0.12f * pulse.value),
            radius = radius * 0.35f,
            center = center
        )
    }
}
