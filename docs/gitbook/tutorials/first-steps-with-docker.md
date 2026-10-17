---
description: Tutorial. Run Ingot in Docker, create the first administrator with the setup code, create a repository with a deploy token, publish a library with Maven or Gradle and consume it.
---

# First steps with Docker

In this tutorial you start Ingot in a container and create its first administrator. You then create a repository with a deploy token, publish a small library to it, and use that library in a second project. At the end you have a working repository on your own machine.

Plan for about 30 minutes.

## What you need

* Docker, with the `docker` and `curl` commands on your `PATH`.
* A browser.
* Maven or Gradle. Steps 5 and 6 show both, so one is enough.
* A terminal. The examples use a POSIX shell.

Nothing else is needed. The tutorial uses port 8080 on your own machine, so stop any other service that uses that port first.

## 1. Start Ingot

Run this command. It starts the server in the background, publishes port 8080 and keeps the data in a Docker volume named `ingot-data`.

```bash
docker run -d \
  --name ingot \
  -p 8080:8080 \
  -v ingot-data:/app/data \
  ghcr.io/onelitefeathernet/ingot:latest
```

Ask the server for its health. The first start can take a few seconds, so repeat the command after ten seconds if it fails at first.

```bash
curl -s http://localhost:8080/api/status/health
```

You should see:

```json
{"status":"UP"}
```

## 2. Read the setup code

A new instance has no users. It prints a one-time setup code to its log. Show the line that holds the code:

```bash
docker logs ingot 2>&1 | grep "setup code:"
```

The last part of the line is the code, twelve characters long. Copy it. The code is valid until the first administrator exists, or until the container restarts. A restart prints a new code.

## 3. Create the first administrator

1. Open `http://localhost:8080` in your browser.
2. Select **Sign in**. Because no user exists yet, the dialog is titled **Set up Ingot**.
3. Enter the code in **Setup code**.
4. Enter a name in **Admin username**. The name may not contain spaces or colons.
5. Enter a **Display name** if you want one. It is optional.
6. Enter a password of at least 12 characters in **Password**, then repeat it in **Confirm password**.
7. Select **Set up**.

You should see your name in the header after the word **Welcome**. The account is an instance administrator, so it can open every admin page.

## 4. Create a repository with a deploy token

The repository wizard creates three things in one run: the repository, a team that can deploy to it, and a machine account with a token that belongs to that team.

1. Select **Settings**. On a narrow screen, **Settings** is under **More**.
2. Open the **Maven** domain.
3. Select the **Repositories** collection, then select **New repository**. The wizard opens.

Work through the wizard steps:

**Basics**

1. Enter `tutorial` in **Id**.
2. Keep **Public** as the visibility. Anyone can download from a public repository. Publishing always needs a token.
3. Select **Next**.

**Storage**

1. Keep **Filesystem** and the default quota.
2. Select **Next**.

**Access**

1. Select **Create a team for this repository**. The wizard suggests the team name `tutorial-team` and gives it the **Deploy** role.
2. Tick **Also create a machine account with a token**. The wizard suggests the name `tutorial-deploy`.
3. Keep **Read and write** in **What the token may do**.
4. Select **Next**.

**Review**

1. Read the summary table.
2. Select **Create**.

The wizard works through its steps one by one and shows the progress of each. When it finishes, it shows the secret of the token.

{% hint style="warning" %}
Ingot shows the token secret once. Copy it now and keep it somewhere safe. If you lose it, create a new token for the machine account under **Access**, then **Machine accounts**.
{% endhint %}

1. Select the copy button next to the secret.
2. Select **Done**. The repository opens.

Set the two variables in the terminal you use for the next steps. Replace the placeholder with the secret you copied:

```bash
export INGOT_DEPLOY_USERNAME="tutorial-deploy"
export INGOT_DEPLOY_SECRET="<the secret you copied>"
```

Maven and Gradle send the token name as the user name and the secret as the password. The token name is the name of the machine account.

## 5. Publish a library

Create a new folder for the library and change into it:

```bash
mkdir hello && cd hello
```

Choose one of the two tabs.

{% tabs %}
{% tab title="Maven" %}
1. Create `pom.xml` in the `hello` folder:

   ```xml
   <project xmlns="http://maven.apache.org/POM/4.0.0">
     <modelVersion>4.0.0</modelVersion>
     <groupId>com.example</groupId>
     <artifactId>hello</artifactId>
     <version>1.0.0</version>
     <packaging>jar</packaging>

     <distributionManagement>
       <repository>
         <id>tutorial</id>
         <url>http://localhost:8080/tutorial</url>
       </repository>
     </distributionManagement>
   </project>
   ```

2. Maven reads the credentials from `~/.m2/settings.xml`. If the file does not exist, create it with this content:

   ```xml
   <settings>
     <servers>
       <server>
         <id>tutorial</id>
         <username>${env.INGOT_DEPLOY_USERNAME}</username>
         <password>${env.INGOT_DEPLOY_SECRET}</password>
       </server>
     </servers>
   </settings>
   ```

   If the file already exists, add only the `<server>` element inside its `<servers>` element.

3. Deploy the library:

   ```bash
   mvn -B deploy
   ```

   The build ends with `BUILD SUCCESS`.
{% endtab %}
{% tab title="Gradle" %}
1. Create `settings.gradle.kts` in the `hello` folder:

   ```kotlin
   rootProject.name = "hello"
   ```

2. Create `build.gradle.kts` in the same folder:

   ```kotlin
   plugins {
       `java-library`
       `maven-publish`
   }

   group = "com.example"
   version = "1.0.0"

   publishing {
       publications {
           create<MavenPublication>("maven") {
               from(components["java"])
           }
       }
       repositories {
           maven {
               name = "tutorial"
               url = uri("http://localhost:8080/tutorial")
               credentials {
                   username = System.getenv("INGOT_DEPLOY_USERNAME")
                   password = System.getenv("INGOT_DEPLOY_SECRET")
               }
           }
       }
   }
   ```

3. Deploy the library:

   ```bash
   gradle publish
   ```

   The build ends with `BUILD SUCCESS`.
{% endtab %}
{% endtabs %}

Check that the file is in the repository. This request needs no token, because the repository is public:

```bash
curl -I http://localhost:8080/tutorial/com/example/hello/1.0.0/hello-1.0.0.jar
```

You should see `HTTP/1.1 200 OK`.

{% hint style="info" %}
The repository does not accept a second upload of the same version unless you enable redeployment for it. If you change the library and deploy again, raise the version in the build file first.
{% endhint %}

## 6. Consume the library

Create a second project in another folder. It is the project that uses the library.

{% tabs %}
{% tab title="Maven" %}
1. Create `pom.xml` in a new folder named `consumer`:

   ```xml
   <project xmlns="http://maven.apache.org/POM/4.0.0">
     <modelVersion>4.0.0</modelVersion>
     <groupId>com.example</groupId>
     <artifactId>consumer</artifactId>
     <version>1.0.0</version>

     <repositories>
       <repository>
         <id>tutorial</id>
         <url>http://localhost:8080/tutorial</url>
       </repository>
     </repositories>

     <dependencies>
       <dependency>
         <groupId>com.example</groupId>
         <artifactId>hello</artifactId>
         <version>1.0.0</version>
       </dependency>
     </dependencies>
   </project>
   ```

2. Resolve the dependencies:

   ```bash
   mvn -B dependency:resolve
   ```

   The output lists `com.example:hello:jar:1.0.0` and ends with `BUILD SUCCESS`.
{% endtab %}
{% tab title="Gradle" %}
1. Create `build.gradle.kts` in a new folder named `consumer`:

   ```kotlin
   plugins {
       `java-library`
   }

   repositories {
       maven {
           url = uri("http://localhost:8080/tutorial")
       }
   }

   dependencies {
       implementation("com.example:hello:1.0.0")
   }
   ```

2. Show the resolved dependencies:

   ```bash
   gradle dependencies --configuration runtimeClasspath
   ```

   The output contains `com.example:hello:1.0.0`.
{% endtab %}
{% endtabs %}

You have now published a library to Ingot and consumed it from another project.

## Next steps

* [Install with Docker](../how-to/install-with-docker.md) explains the image tags, the user ids, the data volume and the environment variables for a real deployment.
* [Set up the first administrator](../how-to/set-up-first-admin.md) lists every message the setup can give you.
* [Set up users, teams and SSO](set-up-users-teams-and-sso.md) gives people access with their own accounts, so they do not need the deploy token.
