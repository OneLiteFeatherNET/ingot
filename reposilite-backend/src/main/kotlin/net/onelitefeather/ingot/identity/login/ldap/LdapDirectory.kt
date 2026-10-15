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

import java.util.Hashtable
import javax.naming.AuthenticationException
import javax.naming.Context
import javax.naming.NamingException
import javax.naming.directory.Attribute
import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import javax.naming.ldap.InitialLdapContext
import javax.naming.ldap.LdapContext
import javax.naming.ldap.StartTlsRequest
import javax.naming.ldap.StartTlsResponse

/** The directory cannot be used: it is unreachable, or the service bind or a search failed for a reason other than the password. */
class DirectoryUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** One entry of a search: its distinguished name and the values of the requested attributes. */
data class LdapEntry(val dn: String, val attributes: Map<String, List<String>>) {

    fun first(attribute: String): String? =
        attributes[attribute]?.firstOrNull { it.isNotBlank() }

}

/**
 * The JNDI calls of the LDAP login. JNDI ships with the JDK, so no library is added. Connect and read timeouts are five
 * seconds. Searches use the subtree scope and never follow referrals.
 */
internal class LdapDirectory(
    private val url: String,
    private val startTls: Boolean,
) {

    /**
     * Opens a connection. Without [principal] the connection is anonymous. A failed bind throws the JNDI
     * [AuthenticationException]; any other failure is a [DirectoryUnavailableException].
     */
    fun open(principal: String? = null, credential: String? = null): DirContext {
        val environment = Hashtable<String, Any>()
        environment[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
        environment[Context.PROVIDER_URL] = url.trim()
        environment["com.sun.jndi.ldap.connect.timeout"] = TIMEOUT_MILLIS
        environment["com.sun.jndi.ldap.read.timeout"] = TIMEOUT_MILLIS
        return try {
            if (startTls && url.trim().startsWith("ldap://", ignoreCase = true)) {
                startTls(environment, principal, credential)
            } else {
                if (principal != null) {
                    environment[Context.SECURITY_AUTHENTICATION] = "simple"
                    environment[Context.SECURITY_PRINCIPAL] = principal
                    environment[Context.SECURITY_CREDENTIALS] = credential.orEmpty()
                }
                InitialDirContext(environment)
            }
        } catch (exception: AuthenticationException) {
            throw exception
        } catch (exception: NamingException) {
            throw DirectoryUnavailableException("the directory cannot be reached", exception)
        }
    }

    /** Whether [principal] binds with [credential]. A wrong password is `false`; an unreachable directory throws. */
    fun bindSucceeds(principal: String, credential: String): Boolean =
        try {
            open(principal, credential).close()
            true
        } catch (_: AuthenticationException) {
            false
        }

    /**
     * Runs a subtree search. The filter is already escaped (see [LdapFilters]). Referrals are not followed. Throws
     * [DirectoryUnavailableException] when the search fails.
     */
    fun search(context: DirContext, base: String, filter: String, attributes: List<String>): List<LdapEntry> {
        val controls = SearchControls().apply {
            searchScope = SearchControls.SUBTREE_SCOPE
            returningAttributes = attributes.toTypedArray()
            setReturningObjFlag(false)
        }
        return try {
            val results = context.search(base, filter, controls)
            val entries = mutableListOf<LdapEntry>()
            while (results.hasMore()) {
                val result = results.next()
                val values = attributes.associateWith { name -> valuesOf(result.attributes.get(name)) }
                entries.add(LdapEntry(dn = result.nameInNamespace, attributes = values))
            }
            entries
        } catch (exception: NamingException) {
            throw DirectoryUnavailableException("the directory search failed", exception)
        }
    }

    private fun valuesOf(attribute: Attribute?): List<String> {
        if (attribute == null) return emptyList()
        val values = mutableListOf<String>()
        val enumeration = attribute.all
        while (enumeration.hasMore()) {
            values.add(enumeration.next().toString())
        }
        return values
    }

    private fun startTls(environment: Hashtable<String, Any>, principal: String?, credential: String?): DirContext {
        // The connection is opened anonymously and upgraded, so that no password crosses the wire in clear text.
        val context: LdapContext = InitialLdapContext(environment, null)
        (context.extendedOperation(StartTlsRequest()) as StartTlsResponse).negotiate()
        if (principal != null) {
            context.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple")
            context.addToEnvironment(Context.SECURITY_PRINCIPAL, principal)
            context.addToEnvironment(Context.SECURITY_CREDENTIALS, credential.orEmpty())
            context.reconnect(null)
        }
        return context
    }

    companion object {
        private const val TIMEOUT_MILLIS = "5000"
    }

}
