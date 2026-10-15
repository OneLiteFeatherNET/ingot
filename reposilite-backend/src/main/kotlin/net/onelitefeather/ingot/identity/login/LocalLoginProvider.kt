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

package net.onelitefeather.ingot.identity.login

import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import java.security.SecureRandom
import java.util.Base64

/** Username and password, verified against the argon2id hash in the local credential table. */
internal class LocalLoginProvider(
    private val identities: IdentityStore,
    private val credentials: LoginStore,
    private val hasher: PasswordHasher,
) : LoginProvider {

    override val id: String = ID
    override val type: LoginProviderType = LoginProviderType.LOCAL
    override val label: String = "Username and password"

    /**
     * An unknown user, a user without a password and a wrong password all run one argon2 verification, so the time of
     * the answer does not reveal which of them happened.
     */
    override fun authenticate(username: String, password: String): LoginOutcome {
        val principal = identities.findPrincipal(username)?.takeIf { it.kind == PrincipalKind.USER }
        val credential = principal?.let { credentials.findCredential(it.id) }
        val matches = hasher.matches(password, credential?.passwordHash ?: unusedHash)
        if (principal == null || credential == null || !matches) {
            return LoginOutcome.Rejected
        }
        if (principal.disabled) {
            return LoginOutcome.Disabled
        }
        return LoginOutcome.Succeeded(principal, credential.mustChangePassword)
    }

    private val unusedHash: String by lazy {
        val random = ByteArray(24).also { SecureRandom().nextBytes(it) }
        hasher.hash(Base64.getEncoder().encodeToString(random))
    }

    companion object {
        const val ID = "local"
    }

}
