package io.astrbot.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.astrbot.android.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

class SnowLumaActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        lifecycleScope.launch { RuntimeStore.operationLock.withLock {
            withContext(Dispatchers.IO) { SnowLumaRepository(this@SnowLumaActivity).refresh() } } }
        setContent { ManagerTheme { SnowLumaScreen() } }
    }

    @Composable private fun SnowLumaScreen() {
        val state by SnowLumaStore.state.collectAsStateWithLifecycle()
        val manager by RuntimeStore.state.collectAsStateWithLifecycle()
        val desktop by DesktopStore.state.collectAsStateWithLifecycle()
        val repository = remember { SnowLumaRepository(this) }
        var checked by remember { mutableStateOf(false) }
        var uninstall by remember { mutableStateOf(false) }
        var logs by remember { mutableStateOf<String?>(null) }
        var credential by remember { mutableStateOf(repository.credential()) }
        var reveal by remember { mutableStateOf(false) }
        var changePassword by remember { mutableStateOf(false) }
        var resetPassword by remember { mutableStateOf(false) }
        var newPassword by remember { mutableStateOf("") }
        LaunchedEffect(state.busy) { if (!state.busy) credential = repository.credential() }
        fun command(action: String, password: String? = null) {
            RuntimeService.command(this@SnowLumaActivity, "snowluma-$action", snowlumaPassword = password)
        }
        fun copyPassword() {
            val value = credential ?: return
            val clip = ClipData.newPlainText(AppText.get(R.string.snowluma_password_title), value)
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            Toast.makeText(this@SnowLumaActivity, AppText.get(R.string.ui_copied, AppText.get(R.string.snowluma_password_title)), Toast.LENGTH_SHORT).show()
        }
        fun openDesktop() { startActivity(Intent(this@SnowLumaActivity, DesktopActivity::class.java)) }
        val dependencies = desktop.configuration.installed && desktop.configuration.qqInstalled
        Scaffold(topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { finish() }) { Icon(painterResource(R.drawable.ic_back), AppText.get(R.string.ui_back), Modifier.size(22.dp)) }
                Text(AppText.get(R.string.snowluma_name), style = MaterialTheme.typography.titleLarge)
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ManagerSection(AppText.get(R.string.snowluma_name)) {
                    Text(when {
                        state.busy || manager.busy -> manager.phase
                        state.ready -> AppText.get(R.string.ui_running)
                        state.active -> AppText.get(R.string.ui_waiting_for_webui_readiness)
                        state.installed -> AppText.get(R.string.ui_stopped)
                        else -> AppText.get(R.string.ui_not_installed)
                    }, style = MaterialTheme.typography.titleMedium)
                    state.version?.let { Text("v$it", style = MaterialTheme.typography.bodySmall) }
                    Text(AppText.get(R.string.snowluma_intro), style = MaterialTheme.typography.bodySmall)
                    if (state.busy) {
                        val fraction = if (state.stage == "download" && state.total > 0)
                            state.downloaded.toFloat() / state.total else null
                        if (fraction != null) LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.stage == "download" && state.total > 0)
                            Text("%.1f / %.1f MiB".format(state.downloaded / 1048576.0, state.total / 1048576.0), style = MaterialTheme.typography.bodySmall)
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                if (!state.licenseAccepted) {
                    ManagerSection(AppText.get(R.string.snowluma_license_title)) {
                        Text(AppText.get(R.string.snowluma_license_text), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { checked = it })
                            Text(AppText.get(R.string.snowluma_license_accept), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                        ManagerActionButton(AppText.get(R.string.ui_ok), { command("license-accept") }, Modifier.fillMaxWidth(),
                            enabled = checked && !manager.busy)
                    }
                } else {
                    ManagerSection(AppText.get(R.string.snowluma_name)) {
                        if (!dependencies) {
                            Text(AppText.get(R.string.snowluma_desktop_missing), style = MaterialTheme.typography.bodySmall)
                            ManagerActionButton(AppText.get(R.string.snowluma_desktop_open), { openDesktop() }, Modifier.fillMaxWidth())
                        } else if (!state.installed) {
                            ManagerActionButton(AppText.get(R.string.snowluma_install), { command("install") }, Modifier.fillMaxWidth(),
                                enabled = manager.environmentInstalled && !manager.busy && !state.busy)
                        } else {
                            ManagerActionButton(AppText.get(R.string.snowluma_open), {
                                startActivity(Intent(this@SnowLumaActivity, WebsiteBrowserActivity::class.java).putExtra("snowluma", true))
                            }, Modifier.fillMaxWidth(), enabled = state.ready)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ManagerActionButton(AppText.get(if (state.active) R.string.snowluma_stop else R.string.snowluma_start), {
                                    command(if (state.active) "stop" else "start")
                                }, Modifier.weight(1f), if (state.active) ActionTone.STOP else ActionTone.START, !manager.busy)
                                ManagerActionButton(AppText.get(R.string.snowluma_uninstall), { uninstall = true }, Modifier.weight(1f), ActionTone.NEUTRAL, !manager.busy)
                            }
                        }
                        if (!dependencies) Text(AppText.get(R.string.snowluma_desktop_required), style = MaterialTheme.typography.bodySmall)
                    }
                    ManagerSection(AppText.get(R.string.snowluma_webui_title)) {
                        Text(repository.webUiUrl, style = MaterialTheme.typography.bodyMedium)
                        Text(AppText.get(R.string.snowluma_webui_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.installed) ManagerSection(AppText.get(R.string.snowluma_password_title)) {
                        Text(AppText.get(R.string.snowluma_password_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                            .padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (credential == null) "—" else if (reveal) credential!! else "••••••••",
                                Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { reveal = !reveal }, enabled = credential != null) {
                                Icon(painterResource(if (reveal) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                    contentDescription = "${if (reveal) AppText.get(R.string.ui_hide) else AppText.get(R.string.ui_show)} ${AppText.get(R.string.snowluma_password_title)}")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ManagerActionButton(AppText.get(R.string.ui_copy_f4880, AppText.get(R.string.snowluma_password_title)),
                                { copyPassword() }, Modifier.weight(1f), ActionTone.NEUTRAL, credential != null && !state.busy)
                            ManagerActionButton(AppText.get(R.string.snowluma_password_change),
                                { newPassword = ""; changePassword = true }, Modifier.weight(1f), ActionTone.NEUTRAL, !state.busy)
                        }
                        ManagerActionButton(AppText.get(R.string.snowluma_password_reset),
                            { resetPassword = true }, Modifier.fillMaxWidth(), ActionTone.NEUTRAL, !state.busy)
                    }
                    ManagerSection(AppText.get(R.string.snowluma_connection_title)) {
                        Text(AppText.get(R.string.snowluma_connection_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(AppText.get(R.string.snowluma_workspace), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ManagerActionButton(AppText.get(R.string.ui_project_github), {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ProjectIdentity.REPOSITORY))) }
                }, Modifier.fillMaxWidth(), ActionTone.NEUTRAL)
                ManagerActionButton(AppText.get(R.string.ui_logs_894b8), {
                    lifecycleScope.launch { logs = withContext(Dispatchers.IO) { RuntimeStore.log(this@SnowLumaActivity, LogCategory.SNOWLUMA).tail(100) } }
                }, Modifier.fillMaxWidth(), ActionTone.NEUTRAL)
            }
        }
        if (uninstall) AlertDialog(onDismissRequest = { uninstall = false }, title = { Text(AppText.get(R.string.snowluma_uninstall)) },
            text = { Text(AppText.get(R.string.snowluma_uninstall_notice)) },
            confirmButton = { TextButton(onClick = { uninstall = false; command("uninstall") }) { Text(AppText.get(R.string.ui_ok)) } },
            dismissButton = { TextButton(onClick = { uninstall = false }) { Text(AppText.get(R.string.ui_cancel)) } })
        if (changePassword) AlertDialog(onDismissRequest = { changePassword = false },
            title = { Text(AppText.get(R.string.snowluma_password_change)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newPassword, onValueChange = { newPassword = it },
                        label = { Text(AppText.get(R.string.snowluma_password_new)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    Text(AppText.get(R.string.snowluma_password_weak), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { changePassword = false; command("set-password", newPassword) },
                enabled = repository.isStrongPassword(newPassword) && !state.busy) { Text(AppText.get(R.string.ui_ok)) } },
            dismissButton = { TextButton(onClick = { changePassword = false }) { Text(AppText.get(R.string.ui_cancel)) } })
        if (resetPassword) AlertDialog(onDismissRequest = { resetPassword = false },
            title = { Text(AppText.get(R.string.snowluma_password_reset)) },
            text = { Text(AppText.get(R.string.snowluma_password_reset_notice)) },
            confirmButton = { TextButton(onClick = { resetPassword = false; command("reset-password") }) { Text(AppText.get(R.string.ui_ok)) } },
            dismissButton = { TextButton(onClick = { resetPassword = false }) { Text(AppText.get(R.string.ui_cancel)) } })
        logs?.let { text -> AlertDialog(onDismissRequest = { logs = null }, title = { Text(AppText.get(R.string.ui_logs_894b8)) },
            text = { Text(text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { logs = null }) { Text(AppText.get(R.string.ui_ok)) } }) }
    }
}
