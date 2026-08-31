package com.arslan.shizuwall.firewall

object FirewallCommands {

    private const val CHAIN3 = "cmd connectivity set-chain3-enabled"
    private const val NETWORKING = "cmd connectivity set-package-networking-enabled"

    const val CHAIN3_ENABLE = "$CHAIN3 true"
    const val CHAIN3_DISABLE = "$CHAIN3 false"

    private val NETWORKING_COMMAND = Regex("$NETWORKING (true|false) (\\S+)")

    data class Networking(val key: String, val networkingEnabled: Boolean)

    fun chain3(enabled: Boolean): String = if (enabled) CHAIN3_ENABLE else CHAIN3_DISABLE

    fun networking(key: String, networkingEnabled: Boolean): String = "$NETWORKING $networkingEnabled $key"

    fun block(key: String): String = networking(key, false)

    fun unblock(key: String): String = networking(key, true)

    fun networkingAll(keys: List<String>, networkingEnabled: Boolean): List<String> =
        keys.map { networking(it, networkingEnabled) }

    fun blockAll(keys: List<String>): List<String> = networkingAll(keys, false)

    fun unblockAll(keys: List<String>): List<String> = networkingAll(keys, true)

    fun isNetworkingCommand(command: String): Boolean = NETWORKING_COMMAND.matches(command)

    fun parseNetworking(command: String): Networking? {
        val match = NETWORKING_COMMAND.matchEntire(command) ?: return null
        return Networking(match.groupValues[2], match.groupValues[1].toBooleanStrict())
    }
}
