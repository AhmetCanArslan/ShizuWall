package com.arslan.shizuwall.ladb

import com.arslan.shizuwall.shell.ShellResult

/**
 * Basic LADB manager stub. Implements pairing/connect/execution using libadb-android in future.
 * For now provides a simple stubbed interface so other modules can depend on it.
 */
object LadbManager {
    enum class Status {
        UNCONFIGURED,
        PAIRED,
        CONNECTED,
        DISCONNECTED
    }

    @Volatile
    var status: Status = Status.UNCONFIGURED

    suspend fun pair(): Boolean {
        // TODO: implement libadb pairing flow
        status = Status.PAIRED
        return true
    }

    suspend fun connect(): Boolean {
        // TODO: implement connection establishment
        status = Status.CONNECTED
        return true
    }

    suspend fun disconnect() {
        // TODO: close connections
        status = Status.DISCONNECTED
    }

    suspend fun exec(command: String): ShellResult {
        // TODO: execute via libadb and return real output
        return ShellResult(-1, "", "LADB not implemented")
    }
}
