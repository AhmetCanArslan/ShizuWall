package com.arslan.shizuwall.shell

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val success: Boolean get() = exitCode == 0

    private val errorDetails: String by lazy { (stderr + "\n" + stdout).lowercase() }

    val isUidOwnerMapMissing: Boolean by lazy {
        errorDetails.contains(UID_OWNER_MAP_MISSING)
    }

    val isEffectivelySuccess: Boolean get() = success || isUidOwnerMapMissing

    companion object {
        // Keep in sync with SystemDaemon.UID_OWNER_MAP_MISSING.
        const val UID_OWNER_MAP_MISSING = "suidownermap does not have entry for uid"
    }
}

interface ShellExecutor {
    suspend fun exec(command: String): ShellResult

    suspend fun execBatch(commands: List<String>): List<ShellResult> =
        commands.map { exec(it) }
}
