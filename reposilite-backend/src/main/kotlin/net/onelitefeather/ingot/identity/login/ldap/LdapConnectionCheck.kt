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

package net.onelitefeather.ingot.identity.login.ldap

import javax.naming.AuthenticationException

/**
 * The answer of the LDAP connection test. [userDn] and [groups] are set only when a user name was given and found. The
 * password of a test is used for one bind and is never part of the answer.
 */
data class LdapConnectionReport(
    val ok: Boolean,
    val userDn: String? = null,
    val groups: List<String>? = null,
    val error: String? = null,
)

/**
 * The connection test of the LDAP login for administrators: the service bind, then, when a user name is given, the user
 * search, the user bind when a password is given, and the groups. Nothing is written to the directory.
 */
internal class LdapConnectionCheck(private val settings: LdapLoginSettings) {

    fun check(username: String?, password: String?): LdapConnectionReport {
        settings.missingField()?.let { missing ->
            return LdapConnectionReport(ok = false, error = "The setting $missing is missing")
        }
        val directory = LdapDirectory(settings.url, settings.startTls)
        val bindDn = settings.bindDn.trim().takeIf { it.isNotEmpty() }
        val service = try {
            directory.open(bindDn, settings.bindPassword.takeIf { bindDn != null })
        } catch (_: AuthenticationException) {
            return LdapConnectionReport(ok = false, error = "The service bind was refused")
        } catch (_: DirectoryUnavailableException) {
            return LdapConnectionReport(ok = false, error = "The directory cannot be reached")
        }
        return try {
            if (username.isNullOrBlank()) {
                LdapConnectionReport(ok = true)
            } else {
                lookUp(directory, service, username.trim(), password)
            }
        } catch (_: DirectoryUnavailableException) {
            LdapConnectionReport(ok = false, error = "The directory search failed")
        } finally {
            service.close()
        }
    }

    private fun lookUp(directory: LdapDirectory, service: javax.naming.directory.DirContext, username: String, password: String?): LdapConnectionReport {
        val matches = directory.search(
            context = service,
            base = settings.userBaseDn,
            filter = LdapFilters.fill(settings.userFilter, mapOf("username" to username)),
            attributes = listOf(settings.usernameAttribute),
        )
        if (matches.isEmpty()) {
            return LdapConnectionReport(ok = false, error = "No entry matches the user name")
        }
        if (matches.size > 1) {
            return LdapConnectionReport(ok = false, error = "${matches.size} entries match the user name, one is required")
        }
        val entry = matches.single()
        if (!password.isNullOrEmpty() && !directory.bindSucceeds(entry.dn, password)) {
            return LdapConnectionReport(ok = false, userDn = entry.dn, error = "The user bind with this password was refused")
        }
        val groups = if (settings.groupBaseDn.isBlank()) {
            emptyList()
        } else {
            directory.search(
                context = service,
                base = settings.groupBaseDn,
                filter = LdapFilters.fill(settings.groupFilter, mapOf("dn" to entry.dn, "username" to username)),
                attributes = listOf(settings.groupNameAttribute),
            ).mapNotNull { it.first(settings.groupNameAttribute) }
        }
        return LdapConnectionReport(ok = true, userDn = entry.dn, groups = groups)
    }

}
