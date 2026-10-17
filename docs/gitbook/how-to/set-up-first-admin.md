---
description: How to create the first administrator of a new Ingot instance with the one-time setup code from the log, and how to handle each message the setup can return.
---

# Set up the first administrator

An instance without users cannot be used until one person signs in as administrator. This guide creates that administrator with the one-time setup code that the server writes to its log.

## Prerequisites

* A running Ingot instance with no user yet. A new instance, or a data directory that has never had a user, is in this state. Check it with the request in the result section.
* Access to the log output of that instance. With Docker, use `docker logs`. Without Docker, use the terminal the server runs in.
* A browser that can reach the dashboard.
* An administrator name and a password of at least 12 characters.

## Steps

1. Read the setup code from the log. The server prints it at every start while no user exists.

   ```bash
   docker logs ingot 2>&1 | grep "setup code:"
   ```

   The matching line ends with `$ setup code: <code>`. The code has 12 characters. Letters are upper case, and the characters `0`, `O`, `1`, `I` and `L` do not occur in it.

2. Open the dashboard in your browser, for example `https://repo.example.org`.

3. Select **Sign in**. The dialog is titled **Set up Ingot**, because the instance has no administrator yet.

4. Enter the code in **Setup code**. Upper and lower case do not matter, and spaces around the code are ignored.

5. Enter the name of the administrator in **Admin username**. The name may have up to 255 characters. It may not contain a colon, a space or a control character.

6. Enter a **Display name** if you want one. It is optional.

7. Enter a password of 12 to 256 characters in **Password**, then repeat it in **Confirm password**.

8. Select **Set up**.

## Result

The dashboard signs you in as the new administrator. The account has the instance administrator role, so it can open every admin page, including **Access** and **Settings**.

To check from the command line that setup is complete, request the list of login providers:

```bash
curl -s http://localhost:8080/api/auth/providers
```

Before setup, the response contains `"setupRequired":true`. After setup, it contains `"setupRequired":false`. The setup code is also cleared, so the log line no longer works.

## Handle the messages

The server answers a failed setup with one of these messages. The HTTP status is in brackets.

| Server message (status) | Meaning | What to do |
| --- | --- | --- |
| `Invalid setup code` (403) | The code is not the code of this instance. Either you copied an old line, or the instance restarted after you read the code. | Read the newest line from the log again. |
| `Too many failed setup attempts, try again later` (429) | Too many wrong codes came from your address in a short time. | Wait for the time the server gives, then try again with the right code. |
| `Setup is not required, a user already exists` (403) | The instance already has a user. | Sign in with an existing account instead. |
| `The first administrator was created by another instance of this database, sign in instead` (409) | Another instance on the same database created the administrator while you were setting up. | Sign in with that administrator. |
| `This user name is already taken` (409) | The name is already in use. | Choose another name. |
| `User name must not contain colons, spaces or control characters` (400) | The name breaks the naming rules. | Choose a name without these characters. |
| `Password must have at least 12 characters` (400) | The password is too short. | Choose a longer password. |

## Notes

* The code is one-time. Ingot keeps it in memory only. It ends when the first administrator is created. A restart of the instance prints a new code and the old one stops working.
* The code has no expiry time while the instance has no user, so keep the log private until setup is complete. Anyone who can read the log and reach the dashboard can create the first administrator.
* A cluster of instances on one database prints one code per instance. Use the code from the instance you open in the browser. Once one instance has created the administrator, the other codes stop working.

## Related

* [First steps with Docker](../tutorials/first-steps-with-docker.md) uses this setup in a guided walkthrough.
* [Set up users, teams and SSO](../tutorials/set-up-users-teams-and-sso.md) shows how to add more people with their own accounts.
* [Install with Docker](install-with-docker.md) explains the container options that the setup depends on.
