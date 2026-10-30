package com.maya.recovery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

private val Navy = Color(0xFF080D1D)
private val Panel = Color(0xFF111B35)
private val Blue = Color(0xFF4778FF)
private val Muted = Color(0xFF9BA9C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(
            background = Navy, surface = Panel, primary = Blue,
            onBackground = Color.White, onSurface = Color.White
        )) { MayaApp(this) } }
    }
}

private data class ChatLine(val from: String, val text: String)

@Composable
private fun MayaApp(activity: Activity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("maya_local", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var keyInput by remember { mutableStateOf(apiKey) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(!prefs.getBoolean("setup_done", false)) }
    val lines = remember { mutableStateListOf(ChatLine("Maya", "Namaste! Main Maya hoon. Aap text chat shuru kar sakte hain.")) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(Navy).padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("M A Y A", color = Blue, style = MaterialTheme.typography.labelLarge)
        Text(if (showSetup) "Set up Maya" else "Your AI companion", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(if (showSetup) "Allow only the Android access you want Maya to use." else "Private on-device settings • Gemini text connection", color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        if (showSetup) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { PermissionCard("Microphone", "Use voice input when you choose to speak.") { requestPermission(activity, Manifest.permission.RECORD_AUDIO, 101) } }
                item { PermissionCard("Notifications", "Allow Maya notifications on Android 13 and newer.") {
                    if (Build.VERSION.SDK_INT >= 33) requestPermission(activity, Manifest.permission.POST_NOTIFICATIONS, 102)
                    else toast(context, "Notification runtime permission is not required on this Android version.")
                } }
                item { PermissionCard("Contacts", "Only needed for contact-related commands.") { requestPermission(activity, Manifest.permission.READ_CONTACTS, 103) } }
                item { PermissionCard("Camera", "Only needed for camera-based features.") { requestPermission(activity, Manifest.permission.CAMERA, 104) } }
                item { PermissionCard("Display over other apps", "Open Android settings to grant overlay access.") {
                    openSetting(activity, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
                } }
                item { PermissionCard("Battery optimization", "Allow background reliability where Android permits it.") {
                    if (Build.VERSION.SDK_INT >= 23) openSetting(activity, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}")))
                } }
                item { PermissionCard("Accessibility service", "Optional integration. This build does not read screen content.") {
                    openSetting(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                prefs.edit().putBoolean("setup_done", true).apply()
                showSetup = false
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Continue")
            }
            TextButton(onClick = { showSetup = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Skip optional setup", color = Muted)
            }
        } else {
            OutlinedTextField(
                value = keyInput, onValueChange = { keyInput = it },
                label = { Text("Gemini API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    apiKey = keyInput.trim()
                    prefs.edit().putString("gemini_key", apiKey).apply()
                    toast(context, "API key saved on this device.")
                }) { Text("Save key") }
                TextButton(onClick = { showSetup = true }) { Text("Permissions") }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines) { line ->
                    Column(
                        Modifier.fillMaxWidth().background(if (line.from == "You") Blue.copy(alpha = 0.25f) else Panel, RoundedCornerShape(14.dp)).padding(12.dp)
                    ) {
                        Text(line.from, color = if (line.from == "You") Color(0xFF9DB7FF) else Blue, style = MaterialTheme.typography.labelSmall)
                        Text(line.text, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message, onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Maya se kuch poochhein...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    sendToMaya(message, apiKey, lines, { message = "" }, { busy = it }, scope, context)
                })
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { sendToMaya(message, apiKey, lines, { message = "" }, { busy = it }, scope, context) },
                enabled = !busy && message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) { Text(if (busy) "Connecting..." else "Send") }
            Text("Voice wake-word and Gemini Live are not enabled in this recovery milestone.", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PermissionCard(title: String, detail: String, action: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(14.dp)
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = action, modifier = Modifier.align(Alignment.End)) { Text("Enable", color = Blue) }
    }
}

private fun requestPermission(activity: Activity, permission: String, requestCode: Int) {
    if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
        toast(activity, "Permission already enabled.")
    } else {
        if (Build.VERSION.SDK_INT >= 23) activity.requestPermissions(arrayOf(permission), requestCode)
    }
}

private fun openSetting(activity: Activity, intent: Intent) {
    try { activity.startActivity(intent) }
    catch (_: Exception) { toast(activity, "This Android setting is not available on this device.") }
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private fun sendToMaya(
    raw: String,
    key: String,
    lines: MutableList<ChatLine>,
    clearInput: () -> Unit,
    setBusy: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context
) {
    val prompt = raw.trim()
    if (prompt.isEmpty()) return
    if (key.isBlank()) {
        toast(context, "Pehle Gemini API key save karein.")
        return
    }
    lines.add(ChatLine("You", prompt))
    clearInput()
    setBusy(true)
    scope.launch {
        val answer = withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().put("contents", JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )).toString()
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + key)
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                OkHttpClient().newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) "Gemini error ${response.code}: ${rawBody.take(300)}"
                    else {
                        val root = JSONObject(rawBody)
                        root.optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")
                            ?.optJSONObject(0)?.optString("text")
                            ?: "Maya ko is baar readable response nahi mila."
                    }
                }
            } catch (e: Exception) {
                "Connection error: ${e.message ?: "Unknown error"}"
            }
        }
        lines.add(ChatLine("Maya", answer))
        setBusy(false)
    }
}
