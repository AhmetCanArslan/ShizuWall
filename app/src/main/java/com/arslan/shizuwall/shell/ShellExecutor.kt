package com.arslan.shizuwall.shell

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val success: Boolean get() = exitCode == 0

    private val errorDetails: String by lazy { (stderr + "\n" + stdout).lowercase() }

    val isUidOwnerMapMissing: Boolean by lazy {
        errorDetails.contains("suidownermap does not have entry for uid")
    }

    val isEffectivelySuccess: Boolean get() = success || isUidOwnerMapMissing
}

interface ShellExecutor {
    suspend fun exec(command: String): ShellResult
}
