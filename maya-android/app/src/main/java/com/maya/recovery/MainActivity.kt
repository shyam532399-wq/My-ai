package com.maya.recovery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Background = Color(0xFF050912)
private val Panel = Color(0xD9101B30)
private val Cyan = Color(0xFF00C8FF)
private val Blue = Color(0xFF4778FF)
private val Muted = Color(0xFF91A7C5)
private val httpClient = OkHttpClient.Builder()
    .callTimeout(90, TimeUnit.SECONDS)
    .build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 9, 18)
        window.navigationBarColor = android.graphics.Color.rgb(5, 9, 18)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Background,
                    surface = Panel,
                    primary = Cyan,
                    secondary = Blue,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) { DefulterApp() }
        }
    }
}

private data class ChatLine(val from: String, val text: String)

@Composable
private fun DefulterApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("defulter_local", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var keyInput by remember { mutableStateOf(apiKey) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val lines = remember {
        mutableStateListOf<ChatLine>().apply {
            val saved = prefs.getString("chat_history", null)
            if (!saved.isNullOrBlank()) runCatching {
                val array = JSONArray(saved)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(ChatLine(item.optString("from"), item.optString("text")))
                }
            }
            if (isEmpty()) add(ChatLine("DEFULTER AI", "Namaste! Main DEFULTER AI hoon. API key save karke chat shuru karein."))
        }
    }
    var listening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val speechEngine = remember { TextToSpeech(context) { } }
    DisposableEffect(speechEngine) {
        onDispose { speechEngine.stop(); speechEngine.shutdown() }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeechRecognition(context, { message = it; listening = false }, { listening = false; Toast.makeText(context, it, Toast.LENGTH_SHORT).show() })
        else Toast.makeText(context, "Voice ke liye microphone permission zaroori hai.", Toast.LENGTH_LONG).show()
    }
    val speakReply: (String) -> Unit = { reply ->
        runCatching {
            speechEngine.language = Locale("hi", "IN")
            speechEngine.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "defulter-reply")
        }
    }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF071426), Background, Color(0xFF03060D)))
        ).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("D E F U L T E R   A I", color = Cyan, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("PERSONAL INTELLIGENCE SYSTEM", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
            StatusPill(if (busy) "PROCESSING" else if (apiKey.isBlank()) "CORE STANDBY" else "CORE READY")
        }

        Spacer(Modifier.height(10.dp))
        HudOrb()
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("SYSTEM", "READY", Modifier.weight(1f))
            MetricCard("VOICE", if (listening) "LISTENING" else "TAP TO TALK", Modifier.weight(1f))
            MetricCard("MEMORY", "${lines.size} SAVED", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AI COMMUNICATION", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            Text("VOICE + TEXT MODE", color = Muted, fontSize = 9.sp)
        }
        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Gemini API key") },
            placeholder = { Text("Paste your API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Color(0xFF29415D),
                focusedLabelColor = Cyan
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                apiKey = keyInput.trim()
                prefs.edit().putString("gemini_key", apiKey).apply()
                Toast.makeText(context, "API key saved on this device", Toast.LENGTH_SHORT).show()
            }) { Text("SAVE KEY", color = Cyan, fontSize = 11.sp) }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x99101B30))
                .border(1.dp, Color(0x553A7198), RoundedCornerShape(14.dp))
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                val mine = line.from == "YOU"
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (mine) Color(0x443C70D8) else Color(0xAA0A1425), RoundedCornerShape(11.dp))
                        .padding(10.dp)
                ) {
                    Text(line.from, color = if (mine) Color(0xFF9DB7FF) else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(line.text, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Message DEFULTER AI...", color = Muted) },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                sendToDefulter(message, apiKey, lines, prefs, { message = "" }, { busy = it }, speakReply, scope, context)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Color(0xFF29415D)
            )
        )
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                enabled = !busy && !listening,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(13.dp)
            ) { Text(if (listening) "LISTENING..." else "🎙 VOICE", color = Cyan) }
            Button(
                onClick = { sendToDefulter(message, apiKey, lines, prefs, { message = "" }, { busy = it }, speakReply, scope, context) },
                enabled = !busy && message.isNotBlank(),
                modifier = Modifier.weight(1.3f).height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12618A))
            ) {
                Text(if (busy) "THINKING..." else "SEND MESSAGE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        TextButton(onClick = {
            lines.clear()
            lines.add(ChatLine("DEFULTER AI", "Chat memory cleared. Nayi conversation shuru karein."))
            persistChat(lines, prefs)
        }) { Text("CLEAR CHAT MEMORY", color = Muted, fontSize = 9.sp) }
        Text(
            "MAYA-INSPIRED HUD  •  HINDI VOICE INPUT  •  SPOKEN AI REPLIES",
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            color = Muted, fontSize = 8.sp, letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun HudOrb() {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.91f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000)),
        label = "rotation"
    )
    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(188.dp)) {
            val c = center
            val radius = size.minDimension * 0.37f * pulse
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xAA00D9FF), Color(0x553B70FF), Color.Transparent),
                    center = c,
                    radius = radius * 1.65f
                ),
                radius = radius * 1.65f
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFB9F8FF), Color(0xFF168DCC), Color(0xFF07152F)),
                    center = c,
                    radius = radius
                ),
                radius = radius
            )
            drawCircle(Color(0x9900E5FF), radius * 1.18f, style = Stroke(width = 1.5.dp.toPx()))
            drawArc(
                color = Color(0xFF9AF4FF),
                startAngle = rotation,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - radius * 1.35f, c.y - radius * 1.35f),
                size = androidx.compose.ui.geometry.Size(radius * 2.7f, radius * 2.7f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0x887B9CFF),
                startAngle = rotation + 180f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - radius * 1.48f, c.y - radius * 1.48f),
                size = androidx.compose.ui.geometry.Size(radius * 2.96f, radius * 2.96f),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DEFULTER", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("AI CORE", color = Color(0xFFB8F5FF), fontSize = 9.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun StatusPill(value: String) {
    Text(
        "● $value",
        modifier = Modifier.clip(CircleShape).background(Color(0x3322DDAA)).padding(horizontal = 10.dp, vertical = 7.dp),
        color = Color(0xFF62E6C0), fontSize = 9.sp, fontWeight = FontWeight.Bold
    )
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .border(1.dp, Color(0x553A7198), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 10.dp)
    ) {
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun sendToDefulter(
    raw: String,
    key: String,
    lines: MutableList<ChatLine>,
    prefs: android.content.SharedPreferences,
    clearInput: () -> Unit,
    setBusy: (Boolean) -> Unit,
    speakReply: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context
) {
    val prompt = raw.trim()
    if (prompt.isEmpty()) return
    if (key.isBlank()) {
        Toast.makeText(context, "Pehle Gemini API key save karein.", Toast.LENGTH_SHORT).show()
        return
    }
    lines.add(ChatLine("YOU", prompt))
    persistChat(lines, prefs)
    clearInput()
    setBusy(true)
    scope.launch {
        val answer = withContext(Dispatchers.IO) {
            try {
                val contents = JSONArray()
                lines.takeLast(24).forEach { line ->
                    contents.put(JSONObject().put("role", if (line.from == "YOU") "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", line.text))))
                }
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put(
                        "text", "You are DEFULTER AI, a helpful and honest personal assistant. Understand Hindi, Hinglish and English. Reply in the user's language. Never claim an action was done unless it was actually done."
                    ))))
                    .put("contents", contents)
                    .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 8192))
                    .toString()
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                    .header("x-goog-api-key", key)
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        "AI service error ${response.code}: ${responseText.take(240)}"
                    } else {
                        val root = JSONObject(responseText)
                        root.optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")
                            ?.optJSONObject(0)?.optString("text")
                            ?: "AI service ne is baar readable response nahi diya."
                    }
                }
            } catch (error: Exception) {
                "Connection error: ${error.message ?: "Unknown error"}"
            }
        }
        lines.add(ChatLine("DEFULTER AI", answer))
        persistChat(lines, prefs)
        setBusy(false)
        if (!answer.startsWith("AI service error") && !answer.startsWith("Connection error")) speakReply(answer)
    }
}


private fun persistChat(lines: List<ChatLine>, prefs: android.content.SharedPreferences) {
    val array = JSONArray()
    lines.forEach { array.put(JSONObject().put("from", it.from).put("text", it.text)) }
    prefs.edit().putString("chat_history", array.toString()).apply()
}

private fun startSpeechRecognition(context: Context, onResult: (String) -> Unit, onFailure: (String) -> Unit) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onFailure("Is device par speech recognition available nahi hai.")
        return
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) { recognizer.destroy(); onFailure("Voice input error. Dobara try karein.") }
        override fun onResults(results: Bundle?) {
            val result = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            recognizer.destroy()
            if (!result.isNullOrBlank()) onResult(result) else onFailure("Koi speech samajh nahi aayi.")
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
    runCatching { recognizer.startListening(intent) }.onFailure {
        recognizer.destroy()
        onFailure("Microphone start nahi ho paya.")
    }
}
