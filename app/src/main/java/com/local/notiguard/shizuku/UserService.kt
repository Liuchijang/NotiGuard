package com.local.notiguard.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs inside the Shizuku-spawned process (uid shell/2000, or root if Shizuku runs as root).
 * Must have a public no-arg or (Context) constructor — Shizuku instantiates it reflectively.
 */
class UserService : IUserService.Stub() {

    override fun destroy() {
        // Nothing to clean up; let the process be killed.
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exit = process.waitFor()
            "$exit\n$output"
        } catch (t: Throwable) {
            "-1\n${t.message ?: t.javaClass.simpleName}"
        }
    }
}
