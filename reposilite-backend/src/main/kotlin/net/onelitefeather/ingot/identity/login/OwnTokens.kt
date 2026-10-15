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

import net.onelitefeather.ingot.identity.api.NodeDto
import net.onelitefeather.ingot.identity.evaluation.NodeRules
import net.onelitefeather.ingot.identity.evaluation.ScopeRules
import net.onelitefeather.ingot.identity.model.NodeRule

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.token.AccessTokenSecurityProvider
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import net.onelitefeather.ingot.identity.api.TokenDto
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.TokenRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore
import java.time.Clock
import java.time.LocalDate

sealed interface OwnTokenOutcome {

    data class Issued(val dto: CreatedTokenDto) : OwnTokenOutcome

    data class Rotated(val dto: CreatedTokenDto) : OwnTokenOutcome

    data object Deleted : OwnTokenOutcome

    /** No token of this user has that name. Tokens of other users are never found. */
    data object NotFound : OwnTokenOutcome

    data object NameTaken : OwnTokenOutcome

    data class Invalid(val problem: String) : OwnTokenOutcome

}

/**
 * The tokens a user creates for themselves. Only the user's own tokens are visible here. A secret is returned once, when
 * it is created or rotated, and is stored as a bcrypt hash of the whole `ingot_` secret. Every change tells the caller
 * which token name changed, so that cached credentials of that name are dropped.
 */
class OwnTokens internal constructor(
    private val identities: IdentityStore,
    private val onTokenChanged: (String) -> Unit,
    private val audit: (AuditEvent) -> Unit,
    private val clock: Clock,
) {

    fun list(principalId: Int): List<TokenDto> =
        identities.tokensOf(principalId).map { it.toDto() }

    fun issue(principalId: Int, actor: AuditActor, request: SelfTokenRequest): OwnTokenOutcome {
        val name = request.name?.trim().orEmpty()
        nameProblem(name)?.let { return OwnTokenOutcome.Invalid(it) }
        val scopes = request.scopes?.let { names ->
            if (names.isEmpty()) {
                return OwnTokenOutcome.Invalid("Scopes cannot be empty, omit them for an unrestricted token")
            }
            names.map { value ->
                Action.entries.firstOrNull { it.name == value }
                    ?: return OwnTokenOutcome.Invalid("Unknown scope '$value', supported: ${Action.entries.joinToString { it.name }}")
            }.toSet()
        }
        val nodes = request.nodes?.let { raw ->
            if (raw.isEmpty()) return OwnTokenOutcome.Invalid("Nodes cannot be empty, omit them for an unrestricted token")
            val rules = raw.map { NodeRule(it.node, it.allowed) }
            NodeRules.problemsOf(rules).takeIf { it.isNotEmpty() }?.let { return OwnTokenOutcome.Invalid("Invalid permission nodes: ${it.joinToString("; ")}") }
            rules
        }
        if (identities.findToken(name) != null) {
            return OwnTokenOutcome.NameTaken
        }

        // The row is created first because the id is part of the secret. Until the hash is written the row cannot verify
        // anything, which is the safe state to be in if the request is interrupted.
        val created = identities.createToken(
            principalId = principalId,
            name = name,
            secretHash = "",
            scopes = scopes,
            nodes = nodes ?: scopes?.let { ScopeRules.nodesOf(it) },
            expiresAt = request.expiresAt,
            createdAt = LocalDate.now(clock),
            description = request.description.orEmpty(),
        ) ?: return OwnTokenOutcome.NameTaken

        val secret = BearerSecret.create(created.id)
        val withHash = created.copy(secretHash = AccessTokenSecurityProvider.encodeSecret(secret))
        identities.saveToken(withHash)
        onTokenChanged(name)

        val principal = identities.findPrincipalById(principalId)?.name.orEmpty()
        audit(AuditEvent(action = AuditAction.TOKEN_CREATED, actor = actor, details = mapOf("token" to name, "principal" to principal)))
        return OwnTokenOutcome.Issued(CreatedTokenDto(token = withHash.toDto(), secret = secret))
    }

    fun rotate(principalId: Int, tokenName: String, actor: AuditActor): OwnTokenOutcome {
        val token = ownToken(principalId, tokenName) ?: return OwnTokenOutcome.NotFound
        val secret = BearerSecret.create(token.id)
        val rotated = token.copy(secretHash = AccessTokenSecurityProvider.encodeSecret(secret))
        identities.saveToken(rotated)
        onTokenChanged(token.name)

        audit(AuditEvent(action = AuditAction.TOKEN_SECRET_REGENERATED, actor = actor, details = mapOf("token" to token.name)))
        return OwnTokenOutcome.Rotated(CreatedTokenDto(token = rotated.toDto(), secret = secret))
    }

    fun delete(principalId: Int, tokenName: String, actor: AuditActor): OwnTokenOutcome {
        val token = ownToken(principalId, tokenName) ?: return OwnTokenOutcome.NotFound
        identities.deleteToken(token.id)
        onTokenChanged(token.name)

        audit(AuditEvent(action = AuditAction.TOKEN_DELETED, actor = actor, details = mapOf("token" to token.name)))
        return OwnTokenOutcome.Deleted
    }

    /** The name of the token that a Bearer secret names, or `null` when the secret names no token. Verifies nothing. */
    fun nameOfBearer(secret: String): String? =
        BearerSecret.tokenId(secret)?.let { identities.findTokenById(it)?.name }

    private fun ownToken(principalId: Int, tokenName: String): TokenRecord? =
        identities.tokensOf(principalId).firstOrNull { it.name == tokenName }

    private fun nameProblem(name: String): String? = when {
        name.isEmpty() -> "Token name is required"
        name.length > MAX_TOKEN_NAME -> "Token name must not have more than $MAX_TOKEN_NAME characters"
        name.contains(':') -> "Token name must not contain colons"
        else -> null
    }

    private fun TokenRecord.toDto() = TokenDto(
        name = name,
        description = description,
        createdAt = createdAt,
        expiresAt = expiresAt,
        scopes = scopes?.map { it.name },
        nodes = nodes?.map { NodeDto(it.node, it.allowed) },
        lastUsedAt = lastUsedAt,
        lastUsedIp = lastUsedIp,
    )

    private companion object {
        const val MAX_TOKEN_NAME = 255
    }

}
