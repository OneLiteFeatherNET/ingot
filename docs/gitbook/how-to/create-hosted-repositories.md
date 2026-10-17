---
description: Create a hosted repository in Ingot with the New repository wizard or the settings API, give a team or a build account access to it, and check that it accepts an upload.
---

# Create hosted repositories

A hosted repository stores the artifacts that you upload to it. This guide creates one with the dashboard wizard, or with the settings API, and ends with a test upload. For the idea behind repository types, read the [repository types reference](../reference/repository-types.md).

## Before you begin

* Sign in as an instance admin. Only an instance admin can change the maven settings.
* Pick an id for the repository. It becomes the address, for example `https://repo.example.org/internal-releases`. Use letters, digits, dots, underscores and hyphens, and fewer than 64 characters. Do not use a name of a dashboard page such as `search` or `settings`.
* Decide who may read it: `PUBLIC`, `HIDDEN` or `PRIVATE`. The [repository settings reference](../reference/repository-settings.md#visibility) lists what each value allows.
* Decide where the files live: on the disk of the server, or in an S3 bucket. An S3 repository needs the bucket name, and often the endpoint, region and keys.
* For the access step: know which team should publish to the repository, or let the wizard create a team for it.

## Create the repository with the wizard

1. Open **Settings** in the navigation and select the **Maven** tab. Select **New repository** in the list of repositories.
2. On **Basics**, enter the **Id**, choose **Who can read it** (**Public**, **Hidden** or **Private**), and optionally add a **Description** and **Tags**. Separate tags with commas. Tags use lowercase letters, digits and hyphens.
3. Select **Next**. On **Storage**, choose **Filesystem** and set the **Quota**, or choose **S3 bucket** and fill in **Bucket** and, if needed, **Region**, **Endpoint**, **Access key** and **Secret key**.
    * The quota is a size such as `10GB` or `500MB`, or a percentage of the usable disk space such as `90%`. The default is `100%`.
    * An S3 repository has no quota. Ingot does not limit its size.
4. Select **Next**. On **Access**, choose one option:
    * **Give a team access**: select the **Team** and the **Role** that the team gets on this repository.
    * **Create a team for this repository**: enter the **Team name**. The new team is empty. You add its members later on the Access page.
    * **Skip for now**: create the repository without access. Instance admins can still use it.
5. For a build or a script that publishes here, select **Also create a machine account with a token**. Enter the **Machine account name** and choose **What the token may do**: read only, or read and write. The account joins the team, and its token can never do more than the role of the team allows.
6. Select **Next** to open **Review**. Check the summary and the list under **What Create does**.
7. Select **Create**. The wizard saves the repository first. Then it creates the team, the grant, the machine account and the token that you asked for, one after the other. If a step fails, select **Retry this step**. Steps that are done are not repeated.
8. If you created a machine account, copy the secret from the final page. Ingot shows it only once. Select **Done**.

The wizard writes only the id, the visibility, the storage provider, the description and the tags. The repository has no proxied entries, so Ingot reports it as a `hosted` repository.

{% hint style="info" %}
If other settings have unsaved changes when you select **Create**, the wizard saves them together with the repository.
{% endhint %}

## Change other settings

The wizard does not set `redeployment`, `preserveSnapshots` or the optional `type`. To set them, open the repository from the list in **Settings**, **Maven**, change the field and save.

* **Redeployment** allows an upload to replace a file that already exists. It is off by default, so a second upload of the same file is refused.
* **Preserved snapshots** keeps all older builds of a snapshot. It is off by default.
* **Type** set to `HOSTED` declares the repository as hosted. Ingot then refuses proxied entries on it.

The [repository settings reference](../reference/repository-settings.md) describes every field.

## Create the repository with the API

The settings API replaces the whole `maven` domain on every `PUT`. Read it first, add your repository to the `repositories` list and send the result back.

1. Save the current settings to a file:

    ```bash
    curl -u <admin>:<secret> -o maven.json https://<your-host>/api/settings/domain/maven
    ```

2. Add an entry to the `repositories` list in `maven.json`:

    ```json
    {
      "id": "internal-releases",
      "visibility": "PRIVATE",
      "type": "HOSTED",
      "description": "Releases of internal libraries",
      "tags": ["internal"],
      "redeployment": false,
      "storageProvider": { "type": "fs", "quota": "10GB" }
    }
    ```

    Leave out `type` for an untyped repository.

3. Send the file back:

    ```bash
    curl -u <admin>:<secret> -X PUT https://<your-host>/api/settings/domain/maven \
      -H 'Content-Type: application/json' \
      --data-binary @maven.json
    ```

    A rule that is broken answers `400` with a message that starts with `Invalid maven settings: `, and nothing is stored. A saved change applies without a restart.

4. Give an account the right to publish. Open the Access page in the dashboard, add a grant on the repository to a team, and add the account to the team.

## Verify the result

1. List the repositories. A repository you may see appears with its visibility:

    ```bash
    curl -u <admin>:<secret> "https://<your-host>/api/maven/repositories?q=internal-releases"
    ```

    The answer contains an entry with `"id": "internal-releases"` and your visibility. It reports the type `hosted`, see the [repository types reference](../reference/repository-types.md#derived-type).

2. Upload a test file with an account that has the deploy role. For a machine account made by the wizard, the user name is the machine account name and the password is the secret:

    ```bash
    curl -u <account>:<secret> -X PUT --data-binary @app-1.0.jar \
      https://<your-host>/internal-releases/com/example/app/1.0/app-1.0.jar
    ```

    The upload answers `200`.

3. Download it again:

    ```bash
    curl -u <account>:<secret> -O \
      https://<your-host>/internal-releases/com/example/app/1.0/app-1.0.jar
    ```

    A `PRIVATE` repository answers `401` to a request without credentials. A `PUBLIC` repository answers without them.

4. Upload the same file a second time. With the default settings the answer is `409` with the message `Redeployment is not allowed`. This shows that the repository protects released files.

## If the repository does not work

| Symptom | Check |
| --- | --- |
| The repository is missing from the list | Reload the page. A `HIDDEN` or `PRIVATE` repository only appears for callers who may see it. |
| The upload answers `401` | The request has no valid credentials. |
| The upload answers `403` | The account has no deploy permission on the repository. Give its team the **Deploy** role. |
| The upload answers `507` | The filesystem quota is full. Raise the quota or run a [cleanup policy](set-cleanup-policies.md). |
| The upload answers `405` | The repository is typed `PROXY` or `GROUP`. Upload to a `HOSTED` repository. |
| The repository fails to load | The server log names the reason, for example an id of 64 or more characters, or an S3 bucket that cannot be reached. |

## Related

* [Repository settings](../reference/repository-settings.md) lists every field of a repository.
* [Repository types](../reference/repository-types.md) explains hosted, proxy and group repositories.
* [Set cleanup policies](set-cleanup-policies.md) removes old versions from a repository.
