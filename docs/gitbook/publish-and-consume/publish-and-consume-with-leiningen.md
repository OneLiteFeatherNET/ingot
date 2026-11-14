---
description: Deploy and consume Ingot artifacts from a Leiningen project with :repositories and :deploy-repositories in project.clj, token credentials from environment variables, and the release signing setting.
---

# Publish and consume with Leiningen

Leiningen declares the repositories in `project.clj`. `:repositories` is used to resolve dependencies, and `:deploy-repositories` is used by `lein deploy`. This page covers Leiningen 2.x. The URL scheme, the tokens and the redeployment rules are in [Publish and consume artifacts](publish-and-consume-artifacts.md).

## Before you begin

* The URL of the instance, for example `https://repo.example.org`.
* A token with the `deploy` permission on the repository to publish to, and for a private repository a token with `read`. The token name is the user name and the token secret is the password. See [Publish and consume artifacts](publish-and-consume-artifacts.md#before-you-begin).

{% hint style="warning" %}
Do not write the token into `project.clj`, because the file is usually committed. The example reads it from the environment with the `:env/` keyword. You can also keep it in `~/.lein/credentials.clj.gpg`, which Leiningen reads for the repository URLs that match.
{% endhint %}

## Publish

1. Export the token in the shell or in the CI job.

   ```bash
   export INGOT_TOKEN_NAME=<token-name>
   export INGOT_TOKEN_SECRET=<token-secret>
   ```

2. Declare the target in `project.clj`. The `:env/` keyword reads an environment variable with the same name in upper case, so `:env/ingot_token_name` reads `INGOT_TOKEN_NAME`.

   ```clojure
   (defproject com.example/library "1.0.0"
     :deploy-repositories [["ingot-releases" {:url "https://repo.example.org/releases"
                                              :username :env/ingot_token_name
                                              :password :env/ingot_token_secret
                                              :sign-releases false}]
                           ["ingot-snapshots" {:url "https://repo.example.org/snapshots"
                                               :username :env/ingot_token_name
                                               :password :env/ingot_token_secret}]])
   ```

   Leiningen signs release jars by default, and signing needs GPG. `:sign-releases false` turns that off for the `ingot-releases` repository only.

3. Deploy to the repository by name. Without a name, `lein deploy` uses Clojars.

   ```bash
   lein deploy ingot-releases
   ```

   A `-SNAPSHOT` version goes to `ingot-snapshots` in the same way: `lein deploy ingot-snapshots`. The layout is described in [Snapshots](publish-and-consume-artifacts.md#snapshots).

## Consume

Add the repository to `:repositories`. A public repository needs no credentials.

```clojure
(defproject com.example/app "0.1.0"
  :repositories [["ingot-releases" {:url "https://repo.example.org/releases"}]]
  :dependencies [[com.example/library "1.0.0"]])
```

For a private repository, add the same `:username` and `:password` entries as in the publish example:

```clojure
:repositories [["ingot-private" {:url "https://repo.example.org/private"
                                 :username :env/ingot_token_name
                                 :password :env/ingot_token_secret}]]
```

Then run the build as usual, for example `lein deps` or `lein uberjar`.

## Result

After `lein deploy ingot-releases`, the artifact is listed under its path in the repository, and a project with the repository in `:repositories` resolves it. A second deploy of a release file returns `409`, as described in [Redeployment rules](publish-and-consume-artifacts.md#redeployment-rules).
