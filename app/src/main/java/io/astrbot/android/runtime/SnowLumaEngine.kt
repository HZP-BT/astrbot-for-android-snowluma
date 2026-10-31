package io.astrbot.android.runtime

import android.content.Context
import io.astrbot.android.AppText
import io.astrbot.android.R
import io.astrbot.android.checkLocalized
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

data class SnowLumaState(
    val installed: Boolean = false, val version: String? = null, val licenseAccepted: Boolean = false,
    val desired: Boolean = false, val active: Boolean = false, val ready: Boolean = false,
    val busy: Boolean = false, val error: String? = null,
    val stage: String? = null, val step: Int = 0, val downloaded: Long = 0, val total: Long = 0,
)
object SnowLumaStore { val state = MutableStateFlow(SnowLumaState()) }

/** Official SnowLuma distribution only: fetched on request from the pinned upstream release. */
class SnowLumaRepository(private val context: Context) {
    val directory = File(context.filesDir, "manager/extensions/snowluma")
    private val preferences = context.getSharedPreferences("manager", Context.MODE_PRIVATE)
    private val source by lazy { context.assets.open("runtime/snowluma/sources.json").bufferedReader().use { JSONObject(it.readText()) } }
    private val program get() = File(directory, "program")
    private val webui get() = source.getJSONObject("webui")
    val version: String get() = source.getString("version")
    val webUiPort: Int get() = webui.getInt("port")
    val webUiUrl: String get() = "http://${webui.getString("host")}:$webUiPort"
    private val assetSha256: String get() = source.getJSONObject("asset").getString("sha256")

    fun desired() = preferences.getBoolean("desired_snowluma", false)
    fun setDesired(value: Boolean) { check(preferences.edit().putBoolean("desired_snowluma", value).commit()) }
    fun preferred() = preferences.getBoolean("snowluma_preferred", false)
    fun setPreferred(value: Boolean) { check(preferences.edit().putBoolean("snowluma_preferred", value).commit()) }
    fun licenseAccepted() = preferences.getBoolean("snowluma_license_accepted", false)
    fun setLicenseAccepted() { check(preferences.edit().putBoolean("snowluma_license_accepted", true).commit()) }

    fun refresh() {
        val marker = runCatching { DurableFiles.read(File(directory, "installed.json")) }.getOrNull()
        // Program paths are guest paths; check them through the manager's host directory.
        SnowLumaStore.state.update { it.copy(
            installed = marker?.optInt("schemaVersion") == 1 && marker.optString("archiveSHA256") == assetSha256 &&
                File(program, "node").isFile && File(program, "index.mjs").isFile &&
                File(program, "native/snowluma-linux-arm64.so").isFile,
            version = marker?.optString("version"), licenseAccepted = licenseAccepted(), desired = desired()) }
    }

    fun prepareAssets() {
        val scripts = File(directory, "scripts").apply { mkdirs() }
        for (name in listOf("install.py", "start.sh", "sources.json")) {
            context.assets.open("runtime/snowluma/$name").use { input -> File(scripts, name).outputStream().use { input.copyTo(it) } }
        }
    }

    suspend fun install(progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val runtime = BootstrapRuntime(context)
        checkLocalized(runtime.abi == "arm64-v8a") { AppText.get(R.string.snowluma_arm64) }
        checkLocalized(File(runtime.rootfs, ".astrbot-bootstrap.json").isFile) { AppText.get(R.string.ui_initialize_the_ubuntu_runtime_first) }
        checkLocalized(DesktopRepository(context).configuration().installed) { AppText.get(R.string.snowluma_desktop_required) }
        checkLocalized(context.filesDir.usableSpace >= 768L * 1024 * 1024) { AppText.get(R.string.snowluma_space) }
        prepareAssets()
        SnowLumaStore.state.update { it.copy(stage = "download", step = 1, downloaded = 0, total = 0) }
        progress(AppText.get(R.string.snowluma_downloading))
        val tail = java.util.concurrent.ConcurrentLinkedDeque<String>()
        val exitCode = coroutineScope {
            val process = runtime.processBuilder("/usr/bin/python3", "-u",
                "/manager/extensions/snowluma/scripts/install.py", "install").start()
            val reader = launch(Dispatchers.IO) { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                RuntimeStore.log(context, LogCategory.SNOWLUMA).append(line)
                if (!line.startsWith("ASTRBOT_SNOWLUMA ") && line.isNotBlank()) {
                    tail.addLast(line.trim())
                    while (tail.size > 8) tail.pollFirst()
                }
                if (line.startsWith("ASTRBOT_SNOWLUMA ")) runCatching {
                    val value = JSONObject(line.removePrefix("ASTRBOT_SNOWLUMA "))
                    val stage = value.optString("stage")
                    SnowLumaStore.state.update { it.copy(stage = stage, step = value.optInt("step"),
                        downloaded = if (stage == "download") value.optLong("downloaded") else it.downloaded,
                        total = if (stage == "download") value.optLong("total") else it.total) }
                    progress(AppText.get(when (stage) {
                        "download" -> R.string.snowluma_downloading; "verify" -> R.string.snowluma_verifying
                        "extract" -> R.string.snowluma_extracting; "configure" -> R.string.snowluma_configuring
                        "verify_program" -> R.string.snowluma_checking; else -> R.string.snowluma_finishing
                    }))
                }
            } } }
            try {
                withTimeout(30 * 60_000L) { while (process.isAlive) delay(250); reader.join() }
                process.exitValue()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (process.isAlive) { process.destroy(); if (!process.waitFor(8, TimeUnit.SECONDS)) process.destroyForcibly() }
                    runCatching { process.inputStream.close() }; reader.cancelAndJoin()
                }
            }
        }
        checkLocalized(exitCode == 0) {
            // Keep the installer's last lines visible so network and environment failures are diagnosable.
            val detail = tail.joinToString(" ").replace(Regex("\\s+"), " ").trim().take(300)
            AppText.get(R.string.snowluma_failed) + if (detail.isEmpty()) "" else " $detail"
        }
        refresh()
        checkLocalized(SnowLumaStore.state.value.installed) { AppText.get(R.string.snowluma_failed) }
    }

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        val runtime = BootstrapRuntime(context)
        setDesired(false)
        if (runtime.abi == "arm64-v8a" && File(runtime.rootfs, ".astrbot-bootstrap.json").isFile) {
            prepareAssets()
            val result = runtime.execute("/usr/bin/python3",
                "/manager/extensions/snowluma/scripts/install.py", "uninstall", timeoutSeconds = 180)
            RuntimeStore.log(context, LogCategory.SNOWLUMA).append(result.output.trim().take(8192))
            checkLocalized(result.exitCode == 0) { AppText.get(R.string.snowluma_failed) }
        } else {
            for (name in listOf("program", "program.new", "program.old", "installed.json", "package.tar.gz", "package.tar.part")) {
                DurableFiles.delete(File(directory, name))
            }
        }
        refresh()
    }
}

/** The foreground runtime service owns this process tree; closing its page keeps it running. */
class SnowLumaEngine(private val context: Context, private val scope: CoroutineScope) {
    private val repository = SnowLumaRepository(context)
    private val runtime = BootstrapRuntime(context)
    private val client = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS).followRedirects(false).build()
    private var process: Process? = null
    private var output: Job? = null
    private var monitor: Job? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        if (process?.isAlive == true && SnowLumaStore.state.value.ready) return@withContext
        if (process != null) stop()
        repository.refresh()
        checkLocalized(SnowLumaStore.state.value.installed) { AppText.get(R.string.snowluma_install_first) }
        checkLocalized(repository.licenseAccepted()) { AppText.get(R.string.snowluma_license_required) }
        checkLocalized(DesktopStore.state.value.qqRunning) { AppText.get(R.string.snowluma_qq_required) }
        val port = repository.webUiPort
        checkLocalized(runCatching { ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { true } }.getOrDefault(false)) {
            AppText.get(R.string.snowluma_port_busy, port)
        }
        repository.prepareAssets()
        val owned = runtime.processBuilder("/bin/sh", "/manager/extensions/snowluma/scripts/start.sh").start()
        process = owned
        SnowLumaStore.state.update { it.copy(active = true, ready = false, error = null) }
        output = scope.launch(Dispatchers.IO) { runCatching { owned.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line -> RuntimeStore.log(context, LogCategory.SNOWLUMA).append(line) }
        } } }
        monitor = scope.launch(Dispatchers.IO) {
            while (isActive && process === owned && owned.isAlive) {
                val ready = runCatching { client.newCall(Request.Builder().url(repository.webUiUrl).build())
                    .execute().use { it.isSuccessful || it.code in 300..399 } }.getOrDefault(false)
                if (process === owned) SnowLumaStore.state.update { it.copy(ready = ready) }
                if (!DesktopStore.state.value.qqRunning) {
                    // The hooked QQ session is gone; leaving the runtime alone would keep a dead WebUI.
                    scope.launch(Dispatchers.IO) {
                        stop()
                        SnowLumaStore.state.update { it.copy(error = AppText.get(R.string.snowluma_qq_stopped)) }
                    }
                    return@launch
                }
                delay(2000)
            }
            if (process === owned) {
                SnowLumaStore.state.update { it.copy(active = false, ready = false, error = AppText.get(R.string.snowluma_failed)) }
            }
        }
        try {
            withTimeout(120_000) { while (!SnowLumaStore.state.value.ready) {
                checkLocalized(owned.isAlive) { AppText.get(R.string.snowluma_failed) }; delay(250)
            } }
        } catch (failure: Throwable) { stop(); throw failure }
    }

    suspend fun stop() = withContext(NonCancellable + Dispatchers.IO) {
        val owned = process; process = null
        monitor?.cancelAndJoin(); monitor = null
        if (owned != null) {
            owned.destroy()
            if (!owned.waitFor(8, TimeUnit.SECONDS)) { owned.destroyForcibly(); owned.waitFor(3, TimeUnit.SECONDS) }
            runCatching { owned.inputStream.close() }
        }
        output?.cancelAndJoin(); output = null
        SnowLumaStore.state.update { it.copy(active = false, ready = false) }
    }

    suspend fun close() { stop(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
