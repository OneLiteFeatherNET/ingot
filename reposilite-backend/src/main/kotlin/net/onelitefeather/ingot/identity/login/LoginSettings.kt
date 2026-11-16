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

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.ValidatedSettings
import io.javalin.openapi.JsonSchema
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings

@JsonSchema(requireNonNulls = false)
@Doc(title = "Login", description = "Browser login settings")
data class LoginSettings(
    @get:Doc(title = "Session Lifetime Minutes", description = "Lifetime of a browser session token. Logout and a disabled user take effect at the latest when it expires")
    val sessionLifetimeMinutes: Long = 15,
    @get:Doc(title = "Refresh Lifetime Hours", description = "Lifetime of a refresh token. Every renewal issues a new one, so this bounds the idle time of a browser session")
    val refreshLifetimeHours: Long = 168,
    @get:Doc(title = "Refresh Absolute Days", description = "Longest time a login stays renewable, counted from the login itself. Renewals cannot extend it, so a browser must log in again after this many days")
    val refreshAbsoluteDays: Long = 30,
    @get:Doc(title = "Maximum Failed Attempts", description = "Failed password logins that lock a user name from one address, and the same number of failures that lock an address. Applies by default, independently of the brute force protection of Basic and token authentication")
    val maxFailedAttempts: Int = 5,
    @get:Doc(title = "Lockout Minutes", description = "How long a locked user name or address stays locked after the first lockout. Every further lockout without a quiet period doubles it, up to 24 hours")
    val lockoutMinutes: Long = 15,
    @get:Doc(title = "OpenID Connect Providers", description = "External identity providers. Each entry is disabled by default; see the identity model, section 14")
    val oidcProviders: List<OidcProviderSettings> = emptyList(),
    @get:Doc(title = "LDAP", description = "Directory for the browser login with a password. Independent of the LDAP Basic authentication of Maven clients")
    val ldap: LdapLoginSettings = LdapLoginSettings(),
    @get:Doc(title = "Public URL", description = "Public address of the dashboard, for example https://repo.example.org/ (http:// only for localhost). The redirect URIs of the OpenID Connect providers and the callback URL shown to admins are built from it. Blank: built from the Host header and X-Forwarded-Proto of the request")
    val publicUrl: String = "",
    @get:Doc(title = "Account Throttle", description = "Delay of password and token guesses for one user name from addresses the name has not signed in from. Only applies while the switch INGOT_LOGIN_ACCOUNT_THROTTLE is on; the thresholds here have no effect without it")
    val accountThrottle: AccountThrottleSettings = AccountThrottleSettings(),
    @get:Doc(title = "Password Policy", description = "Length of a new password and the check against lists of breached passwords. The defaults keep the rules Reposilite has always enforced: at least 12 characters and no breach check")
    val passwordPolicy: PasswordPolicySettings = PasswordPolicySettings(),
    @get:Doc(title = "Idle Timeout Minutes", description = "Minutes a browser session may stay idle before its refresh token expires. 0 keeps the refresh lifetime as the only idle limit. Otherwise at least 5, and at most the refresh lifetime in minutes")
    val idleTimeoutMinutes: Long = 0,
) : ValidatedSettings {

    override fun violations(): List<String> =
        listOfNotNull(PublicUrl.problemOf(publicUrl)?.let { "publicUrl: $it" }, idleTimeoutViolation()) + accountThrottle.violations() +
            passwordPolicy.violations()

    private fun idleTimeoutViolation(): String? {
        if (idleTimeoutMinutes == 0L) {
            return null
        }
        val maximum = refreshLifetimeHours * 60
        return "idleTimeoutMinutes: must be 0 or between 5 and $maximum (the refresh lifetime in minutes)".takeIf { idleTimeoutMinutes !in 5..maximum }
    }

}

@JsonSchema(requireNonNulls = false)
@Doc(title = "Account Throttle", description = "Thresholds of the per-account delay")
data class AccountThrottleSettings(
    @get:Doc(title = "Threshold", description = "Failed credentials for one user name, from any address, after which the next guess from a new address is delayed. The first delay is one second")
    val threshold: Int = 10,
    @get:Doc(title = "Quiet Minutes", description = "Minutes without a failure after which the count of failures of a user name starts again at zero")
    val quietMinutes: Long = 60,
    @get:Doc(title = "Maximum Delay Seconds", description = "Longest delay of one request. The delay doubles with every failure beyond the threshold and ends by itself; a success clears it")
    val maxDelaySeconds: Long = 300,
    @get:Doc(title = "Known Address Days", description = "Days after its last sign-in during which an address counts as known for a user name. A known address is not delayed by the account key")
    val knownAddressDays: Long = 30,
) {

    fun violations(): List<String> = listOfNotNull(
        "accountThrottle.threshold: must be at least 1".takeIf { threshold < 1 },
        "accountThrottle.quietMinutes: must be at least 1".takeIf { quietMinutes < 1 },
        "accountThrottle.maxDelaySeconds: must be between 1 and 86400".takeIf { maxDelaySeconds !in 1..86_400 },
        "accountThrottle.knownAddressDays: must be at least 1".takeIf { knownAddressDays < 1 },
    )

}

@JsonSchema(requireNonNulls = false)
@Doc(title = "Password Policy", description = "Rules for a password that a user or an administrator sets. None of them applies at sign-in")
data class PasswordPolicySettings(
    @get:Doc(title = "Minimum Length", description = "Fewest characters of a new password, counted as before in UTF-16 code units. Between 8 and 256. A higher value applies to the next password that is set; stored passwords are not checked again")
    val minLength: Int = 12,
    @get:Doc(title = "Breached Password Check", description = "Check of a new password against lists of breached passwords. Off by default")
    val breachCheck: BreachCheckSettings = BreachCheckSettings(),
) {

    fun violations(): List<String> =
        listOfNotNull("passwordPolicy.minLength: must be between 8 and 256".takeIf { minLength !in 8..256 }) + breachCheck.violations()

}

@JsonSchema(requireNonNulls = false)
@Doc(title = "Breached Password Check", description = "Where a new password is compared with breached passwords")
data class BreachCheckSettings(
    @get:Doc(title = "Mode", description = "OFF makes no check. RANGE sends the first five characters of the SHA-1 of a new password to the range service and compares the rest here. OFFLINE compares with the file below. RANGE is an outside call")
    val mode: BreachCheckMode = BreachCheckMode.OFF,
    @get:Doc(title = "File", description = "OFFLINE only: a file with one SHA-1 hash in hexadecimal per line. It is read once at start, and the start fails when it cannot be read")
    val file: String = "",
    @get:Doc(title = "Range URL", description = "RANGE only: base address of the range service, ending in a slash. The five characters are appended to it. Must be an https address")
    val rangeUrl: String = "https://api.pwnedpasswords.com/range/",
    @get:Doc(title = "Fail Closed", description = "RANGE only: when the service does not answer, refuse the change with 503 and Retry-After. When off, the change is allowed and an audit event records the outage")
    val failClosed: Boolean = false,
) {

    fun violations(): List<String> =
        listOfNotNull("passwordPolicy.breachCheck.rangeUrl: must be an https:// address".takeIf { !rangeUrl.startsWith("https://") })

}

/** How a new password is compared with breached passwords. See [BreachCheckSettings.mode]. */
enum class BreachCheckMode { OFF, RANGE, OFFLINE }
