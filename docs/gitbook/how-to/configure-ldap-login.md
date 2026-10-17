---
description: Let people sign in to the Ingot dashboard with an LDAP directory password, map directory groups to teams and admin groups, and test the directory.
---

# Configure LDAP login

Ingot can check the password of a dashboard sign-in against an LDAP directory. The directory is configured in **Access**, then **LDAP**, which writes the `login.ldap` settings. This login is separate from the LDAP authentication that Maven clients use over Basic authentication, which is the `authentication.ldap` setting. Nothing is copied between the two.

## Before you begin

* An instance administrator account and at least one local user, see [Set up the first administrator](set-up-first-admin.md).
* The address of the directory, `ldap://` or `ldaps://`, and, if the directory needs one, a service account that can search the user and group entries.
* Know how your directory names users and groups, for example `uid` and `cn` in a typical OpenLDAP tree.

## How the login works

1. Ingot binds to the directory with the service account. An empty bind DN means an anonymous search.
2. Ingot searches the user base for the entry that matches the user filter. Exactly one entry must match. Zero or several matches are refused.
3. Ingot binds as that entry with the password that was typed. The directory checks the password, not Ingot.
4. If a group base DN is set, Ingot searches the groups of the user and reads their names.
5. The result goes through the same account rules as the other providers: the subject is the distinguished name of the entry, and a new account is created when automatic creation is on.

An empty password is never a login. Ingot refuses it before it asks the directory.

## Configure the directory

1. Open **Access**, then **LDAP**.
2. Switch on **Enabled: offer LDAP sign in**. Leave it off while you set up the rest, because an enabled but incomplete directory serves no logins.
3. Enter the **Label on the sign in form**, for example `Corporate LDAP`.
4. Enter the **Server address**. Use `ldaps://ldap.example.org:636` for TLS from the start. For `ldap://`, switch on **Upgrade the connection with StartTLS**.
5. Under **Bind**, enter the **Bind DN** and the **Bind password** of the service account. Leave both empty for an anonymous search.
6. Under **Users**, enter the **User base DN**, for example `ou=people,dc=example,dc=org`.
7. Set the **User filter**. `{username}` is replaced by the escaped user name. The default is `(uid={username})`.
8. Set the **Username attribute**, **Display name attribute** and **Email attribute**. The defaults are `uid`, `cn` and `mail`.
9. Under **Groups**, enter the **Group base DN** if you want group synchronisation. Leave it empty to skip groups.
10. Set the **Group filter**. `{dn}` and `{username}` are replaced by their escaped values. The default is `(member={dn})`, which fits groups that list their members by DN.
11. Set the **Group name attribute**. The default is `cn`.
12. Select **Save LDAP settings**.

The directory serves logins only when the server address and the user base DN are set. While one of them is empty, an enabled directory serves no logins at all, and Ingot does not fall back to the legacy settings.

## Test the directory

Use **Test connection** before you enable the login. The test uses the saved settings, not the values on the screen.

* Leave the **Test username** and **Test password** empty to test the service bind only.
* Enter a test user and password to also test the user search, the bind as that user and the group lookup. The answer lists the distinguished name of the user and the groups it found.

The test is for instance administrators. It stores nothing, and it does not show the password in the answer.

## Map groups to teams and admin groups

Each Ingot team that should follow a directory group needs the external reference `ldap:<group>`, where `<group>` is the value of the group name attribute, for example `ldap:developers`. Set it in **Access**, then **Teams**, under **External reference**.

At each login, the sync adds the user to the teams whose group the user is in, and removes it from the teams whose group it is no longer in. Memberships that were added by hand are not changed.

To make a directory group an instance admin group, add its name under **Admin groups** in the LDAP settings. Ingot then creates the team `ldap:<group>` and gives it the instance admin right, which follows the list at each login. Keep at least one local instance admin, see [Configure generic OIDC login](configure-oidc-login.md#map-groups-to-teams-and-admin-groups).

## Enable the login

1. Check that the test succeeds for a real account.
2. Switch on **Enabled: offer LDAP sign in** and save.
3. Open the dashboard in a private browser window. The sign-in form offers the label you chose.

## Migrate from the legacy `authentication.ldap` settings

Ingot 1.1 used the `authentication.ldap` settings for the dashboard sign-in too. These settings still serve the dashboard only while `login.ldap` is off. Once you enable `login.ldap`, it takes precedence, and the legacy settings are ignored by the dashboard. Ingot logs a message at startup when the legacy settings are the ones that serve the dashboard.

The legacy settings are not copied to `login.ldap`. Enter the directory again in **Access**, then **LDAP**, test it, and then enable it. Keep `authentication.ldap` for the Maven clients if you use them. A legacy dashboard login signs in as the machine account of the directory user and does not create a person account. The `login.ldap` login creates a person account.

## When a login fails

The login is subject to the same failure limit as the local password. A wrong password counts towards the lock of the user name and of the address, see [Configure generic OIDC login](configure-oidc-login.md#when-a-login-fails) for the shared codes.

| Symptom | What to check |
| --- | --- |
| The sign-in form does not show LDAP | The directory is off, or the server address or the user base DN is empty. |
| The test fails at the service bind | The bind DN or the bind password is wrong, or the account is locked. |
| The test finds no user | The user base DN or the user filter does not match the entry. |
| The test finds several users | The user filter is too broad. Narrow it so that exactly one entry matches. |
| The login fails with `username_taken` | A local user already has that name. |
| The login fails with `user_disabled` | The linked account is disabled. |

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Recover admin access](recover-admin-access.md)
