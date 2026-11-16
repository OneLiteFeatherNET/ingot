---
description: Get back into an Ingot instance when no administrator can sign in, using the setup code, a temporary admin token started with --token, or the wait for a lockout to end.
---

# Recover admin access

Use this guide when nobody can sign in as an instance administrator: the password is lost, the only admin left through a provider group change, or the sign-in is locked after failed attempts. The steps below change the instance, so keep a backup of the data first, see [Back up before upgrading](../upgrade/upgrade-ingot.md#back-up-before-upgrading).

## Find your case

| Situation | Go to |
| --- | --- |
| The instance is new and has no user yet | [Use the setup code](#use-the-setup-code) |
| An administrator's password is lost, or every administrator is locked out of the dashboard | [Start with a temporary admin token](#start-with-a-temporary-admin-token) |
| The last instance admin was removed by an SSO or LDAP group sync | [After a provider removed the last admin](#after-a-provider-removed-the-last-admin) |
| A second factor policy covers the administrators, and none of them can finish a sign-in | [A policy locks out every administrator](#a-policy-locks-out-every-administrator) |
| Sign-in is refused with a lock message after failed attempts | [Wait out a lockout](#wait-out-a-lockout) |

## Use the setup code

The setup code lets the first administrator be created, and only while no user exists in the instance.

1. Start Ingot and read the log. While no user exists, Ingot prints a block that starts with `--- First administrator`, followed by a line `$ setup code: <code>`.
2. Open the dashboard. While no user exists, the sign-in page asks for the setup code, a user name, a password and optionally a display name.
3. Enter the code, then create the administrator. A password has 12 to 256 characters.

Some facts that matter when the code is lost or refused:

* The code lives in memory of the instance. It stays the same while the instance runs, and Ingot prints it at each startup while no user exists. A restart issues a new code, so read the log of the current run.
* Using the code once, or any user appearing in the instance, ends it for good. It does not come back.
* Every instance behind a load balancer has its own code, so use the code printed by the instance you reach.
* Wrong codes count towards the failure limit of the address.

The setup code does not help when a user already exists. Use the temporary token below.

## Start with a temporary admin token

The `--token` startup flag creates a temporary token with all permissions, which means the instance admin right. It is the way back in when no administrator can sign in.

### Start Ingot with the token

Use a long random secret without spaces. The container entrypoint splits its options on spaces.

For a plain start:

```bash
java -jar ingot-<version>.jar --token recovery:<long-random-secret>
```

In a container, put the flag in the startup options. The entrypoint reads `INGOT_OPTS`, and it also reads `REPOSILITE_OPTS` when `INGOT_OPTS` is not set:

```yaml
environment:
  INGOT_OPTS: "--token recovery:<long-random-secret>"
```

The short form `-t name:secret` works the same way.

{% hint style="warning" %}
The secret is visible in the process list and in the compose file. Remove the flag again after the recovery (see the last step below), and do not leave the secret in a file that is committed or shared.
{% endhint %}

### What the token can do

* It is kept in memory. It exists only while the process runs with the flag, and it is created again at every start that has the flag.
* It works with HTTP Basic authentication, as `name:secret`. The `Bearer` header does not accept it.
* It is not a dashboard sign-in. Use it with the API, for example with `curl`.

### Reset a password

An instance admin can set a new password for a user. The user must change it at the next sign-in, and every session of the user ends. The call works only for user accounts, not for machine accounts.

```bash
curl -u recovery:<long-random-secret> \
  -X POST https://repo.example.org/api/identity/principals/<user-name>/password \
  -H "Content-Type: application/json" \
  -d '{"newPassword": "<new password, at least 12 characters>"}'
```

### Grant the instance admin right to a user

```bash
curl -u recovery:<long-random-secret> \
  -X PUT https://repo.example.org/api/identity/principals/<user-name>/instance-admin
```

The call is idempotent. Granting the right to someone who already has it changes nothing. The request accepts an optional `If-Match` header with an ETag, and it is not required.

### Finish the recovery

1. Sign in to the dashboard with the user that you repaired. If you reset a password, the dashboard asks for a new one.
2. Restart Ingot without `--token`. Remove the flag from `INGOT_OPTS`, `REPOSILITE_OPTS` or the command line, or the temporary token comes back at the next start.
3. Check that the admin account signs in and can open **Access**.

## A policy locks out every administrator

The second factor policy covers the administrators when `login.mfa.requiredForAdmins` is on, or when a team in `login.mfa.requiredForTeams` holds them. An administrator who has no factor keeps signing in during the grace period. After it, the session is restricted to enrolment. A lockout happens when no covered administrator can finish a sign-in: the factor is lost and no recovery code is left, the passkey is bound to an address that has changed, or the authenticator app is needed but the settings key is not set.

Try these steps first:

1. **Enrol at once.** Sign in with the password and start the enrolment inside the step-up window, five minutes by default. A restricted session may enrol. Authenticator codes need the settings key, and passkeys need the public address in `login.publicUrl`. The calls are in [Configure second factors](../../configure/authentication/configure-second-factors.md#enrol-a-factor).
2. **Ask another administrator** who can still sign in to reset the factors of the locked user, see the next section. Their own step-up applies.

If no administrator can sign in, start with the temporary token:

3. Start Ingot with the `--token` flag, as in [Start with a temporary admin token](#start-with-a-temporary-admin-token).
4. Switch the policy off, or reset the factors of the locked user, using the token as the Basic credential. The token bypasses the login gate, the policy and the step-up, as it did before this release.

   ```bash
   curl -u recovery:<long-random-secret> -X DELETE \
     https://repo.example.org/api/identity/principals/<user-name>/factors
   ```

5. Restart Ingot without `--token`, and check that the policy is set the way you want it.

## Reset the factors of a user

An instance admin removes all factors and recovery codes of a user, or one factor. The sessions of the user end at once. The policy then applies as before: a covered user keeps the grace period that already began, or is restricted to enrolment once it has ended.

```bash
curl -u recovery:<long-random-secret> -X DELETE \
  https://repo.example.org/api/identity/principals/<user-name>/factors
```

The answer is `{"removed": <count>}`. To remove one factor, list the ids with `GET /api/identity/principals/<user-name>/factors`, then send `DELETE` to `.../factors/<factor-id>`. The reset is audited as `MFA_FACTORS_RESET` with the user name and the number of removed factors. It never holds a code or a secret.

## After a provider removed the last admin

When a sync from an OIDC or LDAP provider removes the last usable instance admin, Ingot logs a warning and writes an audit event `LAST_ADMIN_REVOKED_BY_PROVIDER`. The warning names the way back in. There are two ways to recover:

* **Restore the group at the provider.** The next login of a member of the admin group gives the admin right back, because the sync runs at each login.
* **Start with a temporary token** as described above, then grant the instance admin right to a local user with the `instance-admin` call.

Ingot does not refuse the provider's change. A directory that removes its admins is always followed.

## Wait out a lockout

After failed password sign-ins, Ingot locks the user name for the address the attempts came from, and it locks the address itself. The defaults are 5 failed attempts and a lock of 15 minutes. Each further lock without a quiet period doubles the lock, up to 24 hours. The values are the `login` settings `maxFailedAttempts` and `lockoutMinutes`.

* The lock state is stored in the database. A restart of Ingot does not clear it.
* A successful sign-in clears the lock of the user name for that address. It does not clear the counter of the address.
* The lock applies only to dashboard password sign-ins. The API with HTTP Basic authentication, including `--token`, does not use it.
* From another address, a user name is not locked, unless that address is locked too.

If you can wait, wait for the lock to end. If you cannot, sign in from another address, or use the temporary token from the previous section.

## Related

* [Configure generic OIDC login](../../configure/authentication/configure-oidc-login.md)
* [Configure LDAP login](../../configure/authentication/configure-ldap-login.md)
* [Set up the first administrator](../../get-started/set-up-first-admin.md)
