package com.example.test_pj.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.example.test_pj.data.user.UserInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test_pj.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToMemories: () -> Unit,
    onNavigateToAsk: () -> Unit,
    onNavigateToCoreMemory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val userInfo by viewModel.userInfo.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    // SpeechRecognizer
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                viewModel.onVoiceCancel()
                isListening = false
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) viewModel.onVoiceResult(text) else viewModel.onVoiceCancel()
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (partial != null) viewModel.onVoicePartialResult(partial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { speechRecognizer.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening(speechRecognizer) { isListening = it }
        else viewModel.onVoiceCancel()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse_scale",
    )

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── ユーザーヘッダー ───────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showProfileDialog = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // アバター（イニシャル）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userInfo.initial,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userInfo.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (userInfo.isGoogleUser) {
                        Text(
                            "G  ",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "記憶はエージェントが管理",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Text(
                "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 22.sp,
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Memly",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(8.dp))

        // ── 入力カード ────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                TextField(
                    value = inputText,
                    onValueChange = viewModel::onTextChange,
                    placeholder = {
                        Text(
                            "今日あったことや感じたことを\nテキストか音声で話して...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val micColor by animateColorAsState(
                        if (isListening) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "mic_color",
                    )
                    Box(
                        modifier = if (isListening)
                            Modifier.size(48.dp).scale(pulseScale).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = {
                            if (isListening) {
                                speechRecognizer.stopListening()
                                isListening = false
                            } else {
                                viewModel.onVoiceStart()
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    startListening(speechRecognizer) { isListening = it }
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isListening) "音声停止" else "音声入力",
                                tint = micColor,
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::remember,
                        enabled = inputText.isNotBlank() && uiState !is HomeUiState.Thinking,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (uiState is HomeUiState.Thinking) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                            Text("  記憶する", fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (val s = uiState) {
            is HomeUiState.Thinking -> Text(
                "エージェントが考えている...", color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            // 「保存しました」ではなく、エージェントが実際に何をしたかをそのまま出す
            is HomeUiState.Answered -> AgentReply(
                reply = s.reply,
                actions = s.actions,
                onDismiss = viewModel::dismissResult,
            )
            is HomeUiState.Error -> Text(
                "⚠ ${s.message}", color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            else -> Spacer(Modifier.height(20.dp))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onNavigateToMemories,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        ) {
            Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("  長期記憶を見る", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onNavigateToAsk,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Text("記憶に聞く", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onNavigateToCoreMemory,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Text("私の人物像", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }

    // ── プロフィールダイアログ ─────────────────────
    if (showProfileDialog) {
        ProfileDialog(
            userInfo = userInfo,
            onDismiss = { showProfileDialog = false },
            onSaveName = { name ->
                viewModel.updateDisplayName(name)
                showProfileDialog = false
            },
            onGoogleSignIn = {
                scope.launch {
                    signInWithGoogle(context, viewModel)
                }
                showProfileDialog = false
            },
            onSignOut = {
                viewModel.signOut()
                showProfileDialog = false
            },
        )
    }
}

@Composable
private fun ProfileDialog(
    userInfo: com.example.test_pj.data.user.UserInfo,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    var nameInput by remember { mutableStateOf(if (userInfo.displayName == "ゲスト") "" else userInfo.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("プロフィール", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("表示名", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                if (!userInfo.isGoogleUser) {
                    OutlinedButton(
                        onClick = onGoogleSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    ) {
                        Text("G  Googleでサインイン", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Text(
                        "✓ Googleアカウント連携済み\n${userInfo.email}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    TextButton(onClick = onSignOut) {
                        Text("サインアウト", color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nameInput.isNotBlank()) onSaveName(nameInput) else onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("保存", color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
        },
    )
}

private fun startListening(recognizer: SpeechRecognizer, setListening: (Boolean) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPANESE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    recognizer.startListening(intent)
    setListening(true)
}

private suspend fun signInWithGoogle(context: android.content.Context, viewModel: HomeViewModel) {
    val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    if (webClientId.isBlank() || webClientId == "your_web_client_id_here") return

    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val activity = context.findActivity()
    if (activity == null) {
        viewModel.onGoogleSignInError("Activity が見つかりません")
        return
    }
    try {
        val result = credentialManager.getCredential(activity, request)
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        viewModel.onGoogleSignInSuccess(credential)
    } catch (e: GetCredentialException) {
        viewModel.onGoogleSignInError("${e::class.simpleName}: ${e.message}")
    } catch (e: Exception) {
        viewModel.onGoogleSignInError("${e::class.simpleName}: ${e.message}")
    }
}

/** Compose の LocalContext から Activity を安全に取得する */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * エージェントの返事と、その過程で自分の判断で行った記憶操作を表示する。
 *
 * mem0 では「保存しました」としか言えなかったところ、Letta では
 * 統合したのか新規に書いたのかまで分かるので、それをそのまま見せる。
 */
@Composable
private fun AgentReply(
    reply: String,
    actions: List<com.example.test_pj.data.letta.MemoryAction>,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (reply.isNotBlank()) {
                Text(
                    reply,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            }
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                actions.forEach { action ->
                    Text(
                        "› ${action.label}",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("閉じる", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}
