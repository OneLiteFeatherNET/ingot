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

package com.reposilite.token

/**
 * The repository of [AccessTokenType.PRINCIPAL]: a principal owns no token rows and no legacy rights, so every read is
 * empty and every write is refused. Rights of a principal come from the identity tables, see `AccessSubjectSource`.
 */
internal object NoTokenRepository : AccessTokenRepository {

    private fun refused(): Nothing =
        throw IllegalStateException("A principal identity holds no tokens and no legacy rights")

    override fun saveAccessToken(accessToken: AccessToken): AccessToken = refused()
    override fun deleteAccessToken(id: AccessTokenIdentifier) = refused()
    override fun findAccessTokenById(id: AccessTokenIdentifier): AccessToken? = null
    override fun findAccessTokenByName(name: String): AccessToken? = null
    override fun addPermission(id: AccessTokenIdentifier, permission: AccessTokenPermission): AccessTokenPermission = refused()
    override fun deletePermission(id: AccessTokenIdentifier, permission: AccessTokenPermission) = refused()
    override fun findAccessTokenPermissionsById(id: AccessTokenIdentifier): Set<AccessTokenPermission> = emptySet()
    override fun addRoute(id: AccessTokenIdentifier, route: Route): Route = refused()
    override fun deleteRoute(id: AccessTokenIdentifier, route: Route) = refused()
    override fun deleteRoutesByPath(id: AccessTokenIdentifier, path: String) = refused()
    override fun findAccessTokenRoutesById(id: AccessTokenIdentifier): Set<Route> = emptySet()
    override fun findAll(): Collection<AccessToken> = emptyList()
    override fun countAccessTokens(): Long = 0

}
