package com.arslan.shizuwall.shell

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object ShellExecutorBlocking {
    fun execBlocking(context: Context, command: String): ShellResult {
        return runBlocking(Dispatchers.IO) {
            ShellExecutorProvider.forContext(context).exec(command)
        }
    }

    fun runBlockingSuccess(context: Context, command: String): Boolean {
        return execBlocking(context, command).isEffectivelySuccess
    }

    fun execBatchBlocking(context: Context, commands: List<String>): List<ShellResult> {
        if (commands.isEmpty()) return emptyList()
        return runBlocking(Dispatchers.IO) {
            ShellExecutorProvider.forContext(context).execBatch(commands)
        }
    }
}
