package com.arslan.shizuwall.vpn

import android.content.Context
import android.net.VpnService
import com.arslan.shizuwall.firewall.FirewallCommands
import com.arslan.shizuwall.shell.ShellExecutor
import com.arslan.shizuwall.shell.ShellResult
import com.arslan.shizuwall.shizuku.ShizukuShellExecutor

class VpnShellExecutor(private val context: Context) : ShellExecutor {

    private val shizuku = ShizukuShellExecutor()

    override suspend fun exec(command: String): ShellResult = when (command.trim()) {
        FirewallCommands.CHAIN3_ENABLE -> if (VpnService.prepare(context) == null) OK else FAILED
        FirewallCommands.CHAIN3_DISABLE -> update { it.clear() }
        else -> if (FirewallCommands.isNetworkingCommand(command.trim())) execBatch(listOf(command)).first() else shizuku.exec(command)
    }

    override suspend fun execBatch(commands: List<String>): List<ShellResult> {
        val parsed = commands.mapNotNull { FirewallCommands.parseNetworking(it.trim()) }
        if (parsed.size != commands.size) return commands.map { exec(it) }
        val result = update { keys ->
            parsed.forEach { if (it.networkingEnabled) keys.remove(it.key) else keys.add(it.key) }
        }
        return commands.map { result }
    }

    private fun update(transform: (MutableSet<String>) -> Unit): ShellResult =
        if (FirewallVpnService.apply(context, transform)) OK else FAILED

    private companion object {
        val OK = ShellResult(0, "", "")
        val FAILED = ShellResult(1, "", "VPN permission is missing or the VPN could not start")
    }
}
