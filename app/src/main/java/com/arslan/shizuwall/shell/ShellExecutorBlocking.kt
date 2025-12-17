package com.arslan.shizuwall.shell

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Small helper for executing shell commands from non-coroutine contexts.
 * Uses blocking calls on IO dispatcher.
 */
object ShellExecutorBlocking {
    fun execBlocking(context: Context, command: String): ShellResult {
        return runBlocking(Dispatchers.IO) {
            ShellExecutorProvider.forContext(context).exec(command)
        }
    }

    fun runBlockingSuccess(context: Context, command: String): Boolean {
        return execBlocking(context, command).success
    }
}
