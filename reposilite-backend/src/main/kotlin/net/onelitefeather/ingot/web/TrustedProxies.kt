/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.onelitefeather.ingot.web

import java.net.InetAddress
import java.util.Properties

/**
 * The reverse proxies whose forwarded header is believed. Without any entry nothing changes: the forwarded header is
 * taken as it comes, like in Reposilite. With entries, the header is only read when the direct peer is a trusted proxy,
 * and the client is the rightmost address of the chain that is not a trusted proxy itself, which is the last address a
 * proxy of ours has seen and therefore the last one the client cannot have made up.
 */
class TrustedProxies private constructor(private val networks: List<Network>) {

    val enabled: Boolean
        get() = networks.isNotEmpty()

    /** The address of the client, given the [peer] that connected and the value of the forwarded header, if any. */
    fun resolve(peer: String, forwardedValue: String?): String {
        if (!enabled) {
            return forwardedValue ?: peer
        }
        if (!isTrusted(peer) || forwardedValue.isNullOrBlank()) {
            return peer
        }
        val chain = forwardedValue.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return chain.lastOrNull { !isTrusted(it) } ?: chain.firstOrNull() ?: peer
    }

    private fun isTrusted(address: String): Boolean {
        val bytes = literalBytes(address) ?: return false
        return networks.any { it.contains(bytes) }
    }

    private class Network(private val base: ByteArray, private val prefixLength: Int) {

        fun contains(address: ByteArray): Boolean {
            if (address.size != base.size) {
                return false
            }
            val wholeBytes = prefixLength / 8
            for (index in 0 until wholeBytes) {
                if (address[index] != base[index]) {
                    return false
                }
            }
            val restBits = prefixLength % 8
            if (restBits == 0) {
                return true
            }
            val mask = (0xFF shl (8 - restBits)) and 0xFF
            return (address[wholeBytes].toInt() and mask) == (base[wholeBytes].toInt() and mask)
        }

    }

    companion object {

        const val ENVIRONMENT_VARIABLE = "INGOT_TRUSTED_PROXIES"
        const val SYSTEM_PROPERTY = "ingot.trustedProxies"

        val NONE = TrustedProxies(emptyList())

        /**
         * Reads comma separated IPv4 or IPv6 addresses and CIDR ranges. A malformed entry fails with an
         * [IllegalArgumentException] that names it, so a typo stops the start instead of silently trusting less or more.
         */
        fun parse(raw: String?): TrustedProxies {
            val entries = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return if (entries.isEmpty()) NONE else TrustedProxies(entries.map { parseEntry(it) })
        }

        /** The system property wins over the environment variable. */
        fun fromEnvironment(
            environment: Map<String, String> = System.getenv(),
            properties: Properties = System.getProperties(),
        ): TrustedProxies =
            parse(properties.getProperty(SYSTEM_PROPERTY) ?: environment[ENVIRONMENT_VARIABLE])

        /** The warning for a server that reads a forwarded header from anybody, or `null` when there is nothing to warn about. */
        fun startupWarning(trustedProxies: TrustedProxies, forwardedHeader: String?): String? =
            if (trustedProxies.enabled || forwardedHeader.isNullOrBlank()) {
                null
            } else {
                "The header '$forwardedHeader' is read from every client, so the address used for login lockout, " +
                    "brute-force protection and the audit log can be made up. Set $ENVIRONMENT_VARIABLE " +
                    "(or -D$SYSTEM_PROPERTY) to the addresses of your reverse proxies to trust only them."
            }

        private fun parseEntry(entry: String): Network {
            val address = entry.substringBefore('/')
            val bytes = literalBytes(address)
                ?: throw IllegalArgumentException("Invalid trusted proxy '$entry' in $ENVIRONMENT_VARIABLE: not an IP address or CIDR range")
            val maxPrefix = bytes.size * 8
            val prefixLength = if ('/' in entry) {
                entry.substringAfter('/').toIntOrNull()?.takeIf { it in 0..maxPrefix }
                    ?: throw IllegalArgumentException("Invalid trusted proxy '$entry' in $ENVIRONMENT_VARIABLE: prefix length must be 0 to $maxPrefix")
            } else {
                maxPrefix
            }
            return Network(bytes, prefixLength)
        }

        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val IPV6 = Regex("""[0-9a-fA-F:.]+""")

        /** The bytes of an IP literal, or `null`. Only literals reach the resolver, so no name is ever looked up. */
        private fun literalBytes(value: String): ByteArray? {
            val text = value.trim().removeSurrounding("[", "]").substringBefore('%')
            val literal = when {
                IPV4.matches(text) -> text
                ':' in text && IPV6.matches(text) -> text
                else -> return null
            }
            return try {
                InetAddress.getByName(literal).address
            } catch (_: Exception) {
                null
            }
        }

    }

}
