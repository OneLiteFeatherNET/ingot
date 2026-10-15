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

package com.reposilite.frontend.api

import com.reposilite.frontend.application.AppearanceSettings

/**
 * The presentation values a dashboard needs to render itself.
 *
 * [placeholders] is kept for consumers that were written against the token map. Its keys are the
 * `{{REPOSILITE.*}}` placeholder tokens, which is what they are: when the dashboard is served by
 * this instance these same values are substituted straight into the assets. Keeping the token
 * spelling means a detached dashboard consumes exactly what the bundled one is handed, and plugins
 * that register their own placeholders reach both.
 *
 * The remaining fields carry the same values as plain properties, so a dashboard that runs
 * elsewhere does not have to parse tokens to read its own settings.
 */
data class FrontendSettingsResponse(
    val placeholders: Map<String, String>,
    val id: String,
    val title: String,
    val description: String,
    val organizationWebsite: String,
    val organizationLogo: String,
    val privacyPolicy: String,
    val icpLicense: String,
    val appearance: AppearanceSettings,
)
