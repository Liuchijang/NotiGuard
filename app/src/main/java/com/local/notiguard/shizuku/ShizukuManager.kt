package com.local.notiguard.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.local.notiguard.BuildConfig
import com.local.notiguard.tr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * Central place for Shizuku: tracks availability/permission and runs shell commands
 * through a bound [UserService]. Call [init] once from Application.onCreate.
 */
object ShizukuManager {

    enum class State {
        /** Shizuku app not installed or service not started. */
        UNAVAILABLE,

        /** Shizuku is running but this app has not been granted permission yet. */
        NEEDS_PERMISSION,

        /** Ready to run commands. */
        READY,
    }

    const val PERMISSION_REQUEST_CODE = 4210

    private val _state = MutableStateFlow(State.UNAVAILABLE)
    val state = _state.asStateFlow()

    @Volatile
    private var service: IUserService? = null
    private var connecting: CompletableDeferred<IUserService>? = null
    private val bindMutex = Mutex()

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("svc")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = if (binder != null && binder.pingBinder()) {
                IUserService.Stub.asInterface(binder)
            } else null
            service = svc
            connecting?.let { if (svc != null) it.complete(svc) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connecting = null
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        refresh()
    }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        _state.value = when {
            !pingSafe() -> State.UNAVAILABLE
            !hasPermission() -> State.NEEDS_PERMISSION
            else -> State.READY
        }
    }

    fun requestPermission() {
        if (pingSafe() && !hasPermission()) {
            runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
        }
    }

    private fun pingSafe(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun hasPermission(): Boolean {
        if (!pingSafe()) return false
        return runCatching {
            if (Shizuku.isPreV11()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    private suspend fun ensureService(): IUserService {
        service?.let { return it }
        return bindMutex.withLock {
            service?.let { return it }
            val deferred = CompletableDeferred<IUserService>()
            connecting = deferred
            Shizuku.bindUserService(serviceArgs, connection)
            withTimeout(15_000) { deferred.await() }
        }
    }

    /** Result of running one shell command. */
    data class ExecResult(val exitCode: Int, val output: String) {
        val ok get() = exitCode == 0
    }

    /** Result of an explicit connection test (see [diagnose]). */
    data class Diagnostics(
        val binder: Boolean,
        val permission: Boolean,
        /** Shizuku server API version, null when unreachable. */
        val version: Int?,
        /** uid the server runs as: 0 = root, 2000 = adb/shell. */
        val uid: Int?,
        val execOk: Boolean,
        val execMs: Long?,
        val execOutput: String,
    ) {
        val mode get() = when (uid) {
            0 -> "root"
            2000 -> "adb (shell)"
            null -> "—"
            else -> "uid $uid"
        }
    }

    /**
     * Full connection check: binder alive → permission → server version/uid → round-trip a
     * real command (`id`) through the bound UserService and time it.
     */
    suspend fun diagnose(): Diagnostics {
        refresh()
        val binder = pingSafe()
        val permission = hasPermission()
        val version = if (binder) runCatching { Shizuku.getVersion() }.getOrNull() else null
        val uid = if (binder) runCatching { Shizuku.getUid() }.getOrNull() else null
        if (!binder || !permission) {
            return Diagnostics(binder, permission, version, uid, false, null, "")
        }
        val t0 = System.nanoTime()
        val r = exec("id")
        val ms = (System.nanoTime() - t0) / 1_000_000
        return Diagnostics(binder, permission, version, uid, r.ok, ms, r.output.trim())
    }

    suspend fun exec(command: String): ExecResult = withContext(Dispatchers.IO) {
        if (_state.value != State.READY) {
            return@withContext ExecResult(-1, tr("Shizuku chưa sẵn sàng", "Shizuku is not ready"))
        }
        runCatching {
            val raw = ensureService().exec(command)
            val nl = raw.indexOf('\n')
            if (nl < 0) ExecResult(raw.trim().toIntOrNull() ?: -1, "")
            else ExecResult(raw.substring(0, nl).trim().toIntOrNull() ?: -1, raw.substring(nl + 1))
        }.getOrElse { ExecResult(-1, it.message ?: it.javaClass.simpleName) }
    }
}
