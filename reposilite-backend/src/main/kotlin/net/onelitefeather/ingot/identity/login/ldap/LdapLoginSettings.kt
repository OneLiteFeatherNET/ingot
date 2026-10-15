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

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import io.javalin.openapi.JsonSchema

/**
 * The directory of the browser login (`login.ldap`). It is independent of `authentication.ldap`, which serves the Basic
 * authentication of Maven clients. Nothing is copied between the two.
 */
@JsonSchema(requireNonNulls = false)
data class LdapLoginSettings(
    @get:Doc(title = "Enabled", description = "Announces the LDAP login on the login page and accepts LDAP passwords on POST /api/auth/login")
    val enabled: Boolean = false,
    @get:Doc(title = "Label", description = "Text of the LDAP login on the login page")
    val label: String = "LDAP",
    @get:Doc(title = "URL", description = "ldap:// or ldaps:// address of the directory, for example ldaps://ldap.example.org:636")
    val url: String = "",
    @get:Doc(title = "StartTLS", description = "Upgrades an ldap:// connection with StartTLS before the first bind")
    val startTls: Boolean = false,
    @get:Doc(title = "Bind DN", description = "Account of the service bind that searches the directory. Blank for an anonymous search")
    val bindDn: String = "",
    @get:Sensitive
    @get:Doc(title = "Bind Password", description = "Password of the service bind")
    val bindPassword: String = "",
    @get:Doc(title = "User Base DN", description = "Where users are searched")
    val userBaseDn: String = "",
    @get:Doc(title = "User Filter", description = "Filter that finds a user. {username} is replaced by the escaped user name")
    val userFilter: String = "(uid={username})",
    @get:Doc(title = "Username Attribute", description = "Attribute that gives the user name of a new user")
    val usernameAttribute: String = "uid",
    @get:Doc(title = "Display Name Attribute", description = "Attribute that gives the display name")
    val displayNameAttribute: String = "cn",
    @get:Doc(title = "Email Attribute", description = "Attribute that gives the email address")
    val emailAttribute: String = "mail",
    @get:Doc(title = "Group Base DN", description = "Where groups are searched. Blank disables the group synchronisation")
    val groupBaseDn: String = "",
    @get:Doc(title = "Group Filter", description = "Filter that finds the groups of a user. {dn} and {username} are replaced by their escaped values")
    val groupFilter: String = "(member={dn})",
    @get:Doc(title = "Group Name Attribute", description = "Attribute that gives the name of a group")
    val groupNameAttribute: String = "cn",
    @get:Doc(title = "Automatically Create Users", description = "Creates a user at the first login of a directory entry. When off, only linked users can log in")
    val autoCreateUsers: Boolean = true,
    @get:Doc(title = "Admin Groups", description = "Groups whose members become instance administrators through a synchronised team")
    val adminGroups: List<String> = emptyList(),
) {

    /** The first setting the directory login needs and does not have, or `null` when it is usable. */
    fun missingField(): String? = when {
        url.isBlank() -> "url"
        userBaseDn.isBlank() -> "userBaseDn"
        else -> null
    }

}
