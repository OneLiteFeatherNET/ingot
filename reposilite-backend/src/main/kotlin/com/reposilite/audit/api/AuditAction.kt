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

package com.reposilite.audit.api

/**
 * Administrative and artifact actions that the audit log records.
 * The name is stored in the database, so renaming a constant is a data migration.
 */
enum class AuditAction {
    ARTIFACT_DEPLOYED,
    ARTIFACT_DELETED,
    SETTINGS_CHANGED,
    TOKEN_CREATED,
    TOKEN_DELETED,
    TOKEN_SECRET_REGENERATED,
    TOKEN_ROUTES_CHANGED,
    LOGIN_FAILED,
    CLEANUP_RUN,
    PRINCIPAL_CREATED,
    PRINCIPAL_UPDATED,
    PRINCIPAL_DISABLED,
    PRINCIPAL_DELETED,
    TEAM_CREATED,
    TEAM_UPDATED,
    TEAM_DELETED,
    TEAM_MEMBERSHIP_CHANGED,
    TEAM_GRANTS_CHANGED,
    INSTANCE_ADMIN_GRANTED,
    INSTANCE_ADMIN_REVOKED,
    LOGIN_SUCCEEDED,
    LOGOUT,
    SESSION_REFRESH_REUSE_DETECTED,
    SETUP_COMPLETED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
}
