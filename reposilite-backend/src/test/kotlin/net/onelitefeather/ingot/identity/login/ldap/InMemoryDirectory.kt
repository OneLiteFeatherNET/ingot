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

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import java.net.InetAddress

/**
 * A directory that runs in this process on an ephemeral loopback port. The service account is [SERVICE_DN]; the users are
 * `alice` (password `alice-secret`) and `bob` (password `bob-secret`) in `ou=people`, and the groups of `ou=groups` are
 * `devs` (alice) and `ops` (bob).
 */
internal class InMemoryDirectory : AutoCloseable {

    private val server: InMemoryDirectoryServer

    init {
        val config = InMemoryDirectoryServerConfig(BASE_DN)
        config.addAdditionalBindCredentials(SERVICE_DN, SERVICE_PASSWORD)
        config.listenerConfigs.add(InMemoryListenerConfig.createLDAPConfig("default", InetAddress.getLoopbackAddress(), 0, null))
        config.schema = null
        server = InMemoryDirectoryServer(config)
        server.add("dn: $BASE_DN", "objectClass: top", "objectClass: domain", "dc: example")
        server.add("dn: ou=people,$BASE_DN", "objectClass: organizationalUnit", "ou: people")
        server.add("dn: ou=groups,$BASE_DN", "objectClass: organizationalUnit", "ou: groups")
        server.add(
            "dn: uid=alice,ou=people,$BASE_DN", "objectClass: inetOrgPerson", "uid: alice", "cn: Alice Example",
            "sn: Example", "mail: alice@example.org", "userPassword: alice-secret",
        )
        server.add(
            "dn: uid=bob,ou=people,$BASE_DN", "objectClass: inetOrgPerson", "uid: bob", "cn: Bob Example",
            "sn: Example", "mail: bob@example.org", "userPassword: bob-secret",
        )
        server.add("dn: cn=devs,ou=groups,$BASE_DN", "objectClass: groupOfNames", "cn: devs", "member: uid=alice,ou=people,$BASE_DN")
        server.add("dn: cn=ops,ou=groups,$BASE_DN", "objectClass: groupOfNames", "cn: ops", "member: uid=bob,ou=people,$BASE_DN")
        server.startListening()
    }

    val url: String
        get() = "ldap://127.0.0.1:${server.listenPort}"

    /** The port the directory listens on, for settings that take a host and a port. */
    val port: Int
        get() = server.listenPort

    /** Adds an entry, for tests that need a user or a group of their own. */
    fun add(vararg lines: String) {
        server.add(*lines)
    }

    /** Stops the directory. Later requests to [url] fail as an unreachable directory does. */
    fun stop() {
        server.shutDown(true)
    }

    override fun close() {
        runCatching { server.shutDown(true) }
    }

    companion object {
        const val BASE_DN = "dc=example,dc=org"
        const val SERVICE_DN = "cn=service,dc=example,dc=org"
        const val SERVICE_PASSWORD = "service-secret"
    }

}
