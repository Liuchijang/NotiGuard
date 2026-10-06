package com.local.notiguard.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs inside the Shizuku-spawned process (uid shell/2000, or root if Shizuku runs as root).
 * Must have a public no-arg or (Context) constructor — Shizuku instantiates it reflectively.
 */
class UserService : IUserService.Stub() {

    override fun destroy() {
        // Shizuku does not kill the process for us: without this every app restart leaked one.
        System.exit(0)
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
