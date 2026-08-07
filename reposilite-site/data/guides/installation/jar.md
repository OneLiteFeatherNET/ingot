---
id: jar
title: JAR
---

You can download standalone (JAR) version of Ingot from the GitHub releases page:

- [GitHub / Ingot :: Releases](https://github.com/OneLiteFeatherNET/ingot/releases)

Requirements: `System: Windows, Linux AMD/ARM`, `JVM: 17+`, `Memory: 20MB+`

Recommended memory ranges:

|  Amount  | Description                                      |
|:--------:|--------------------------------------------------|
|  _20MB_  | Minimal requirements for basic setup             |
|  _32MB_  | Repository for personal projects + CI + Proxy    |
|  _64MB_  | General use public repository                    |
| _128MB+_ | Huge repositories with high traffic & throughput |                                    

Our **safe recommendation is to use 32MB-64MB** if you don't care that much about those few MBs. 
You can handle millions of requests per month with it and use various plugins at once and you will always have resources in reserve.

If you'd like to make the most of Ingot, feel free to use values between 20MB - 32MB, 
but keep in mind that reducing memory may lead to more frequent gc calls that can increase usage of CPU.

### Running

To launch Ingot with a defined amount of RAM, use `-Xmx` parameter, for instance:

```bash
$ java -Xmx32M -jar ingot.jar
```

### Interactive CLI

Ingot exposes an interactive console directly in a terminal and it awaits for an input.
Type `help` and learn more about available commands.

![Interactive CLI](/images/guides/interactive-cli.gif)

`Note` Your first access token has to be generated through the terminal or provided as a command line argument.  
Read more about tokens and token management commands in [Guide / Tokens](/guide/tokens).

### Web interface

If Ingot has been launched properly,
you should be able to see its frontend located under the default http://localhost:8080/#/ address.

![Web Interface Preview](/images/guides/web-interface-preview.png)

### Data structure

Ingot stores data in the working directory,
by default it is a place where you've launched it.

```shell-session
user@host ~/workspace: java -jar ingot.jar
```

```bash
~workspace/
+--logs/              List of 10 latest log files
+--plugins/           Directory with all external plugins to load
+--repositories/      The root directory for all declared repositories
   +--private/        Default private repository
   +--releases/       Default repository for releases
   +--snapshots/      Default repository for snapshot releases
+--static/            Static website content
+--configuration.cdn  Configuration file
+--latest.log         Log from the latest launch of the Ingot instance
+--ingot.jar          Application file
+--reposilite.db      Data file containing stats and tokens (only if embedded database enabled).
                     The name is inherited from Ingot so an existing instance keeps its data.
```

To separate data files and configuration from application, use [parameters](general#parameters).
