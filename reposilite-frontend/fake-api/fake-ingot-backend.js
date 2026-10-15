/* eslint-disable no-unused-vars */

/*
 * Copyright (c) 2023 dzikoysk
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

import { createRequire } from "module"
const require = createRequire(import.meta.url)

const express = require("express")
const expressWs = require("express-ws")
const bodyParser = require('body-parser')
const crypto = require("crypto")

const {
  respond,
  authorized,
  invalidCredentials,
  sendMessage,
  createFileDetails,
  createDirectoryDetails,
  generateDayWiseTimeSeries
} = require("./extensions.cjs")

const application = express()
expressWs(application)

let uploadedFiles = []
let mavenSettingsSchema = require('./maven-settings-schema.json')
let mavenSettingsEntity = require('./maven-settings-entity.json')
let frontendSettingsSchema = require('./frontend-settings-schema.json')
// Defaults mirror FrontendSettings and AppearanceSettings on the server.
let frontendSettingsEntity = {
  id: 'ingot-repository',
  title: 'Ingot Repository',
  description: 'Public Maven repository hosted through Ingot',
  organizationWebsite: 'https://github.com/OneLiteFeatherNET',
  organizationLogo: 'https://avatars.githubusercontent.com/u/103827826?s=200&v=4',
  privacyPolicy: 'https://example.com/privacy',
  icpLicense: '国ICP备000000000号',
  appearance: {
    seedColor: '',
    schemeVariant: 'VIBRANT',
    primaryColor: '',
    secondaryColor: '',
    tertiaryColor: '',
    errorColor: '',
    logoDark: '',
    favicon: '',
    footerText: ''
  }
}

// 8887 unless INGOT_FAKE_API_PORT says otherwise, which lets a second copy run beside the first.
// The dashboard in development reads the same variable (see vite.config.js).
const FAKE_API_PORT = Number(process.env.INGOT_FAKE_API_PORT) || 8887

let uptime = 1000
let memory = 20
let threads = 10
// Recorded failures as the server keeps them: newest first in the listing, each with the time it
// happened stored as an age, so the timestamps stay recent however long the stub has been running.
const FAILURE_STACK = (message, type, frames) =>
  [`  by ${type}: ${message}`, ...frames.map(frame => `  at ${frame}`)].join("\n")

let recordedFailures = [
  {
    id: 3,
    ageMs: 2 * 60 * 1000,
    source: "/api/maven/details/releases",
    type: "IllegalStateException",
    message: "Repository releases is not ready",
    count: 1,
    stacktrace: FAILURE_STACK("Repository releases is not ready", "IllegalStateException", [
      "com.reposilite.maven.MavenFacade.details(MavenFacade.kt:118)",
      "com.reposilite.maven.infrastructure.MavenEndpoints.details(MavenEndpoints.kt:64)",
      "io.javalin.http.JavalinServlet.handle(JavalinServlet.kt:38)",
    ]),
  },
  {
    id: 2,
    ageMs: 47 * 60 * 1000,
    source: "/com/reposilite/private/example/1.0/example-1.0.jar",
    type: "NullPointerException",
    message: null,
    count: 4,
    stacktrace: FAILURE_STACK("null", "NullPointerException", [
      "com.reposilite.storage.StorageProvider.read(StorageProvider.kt:27)",
      "com.reposilite.maven.RepositoryProvider.resolve(RepositoryProvider.kt:81)",
      "com.reposilite.maven.infrastructure.MavenEndpoints.findFile(MavenEndpoints.kt:37)",
    ]),
  },
  {
    id: 1,
    ageMs: 26 * 60 * 60 * 1000,
    source: "S3 Storage Provider failed with IO Exception",
    type: "SdkClientException",
    message: "Unable to execute HTTP request: Connection refused",
    count: 1,
    stacktrace: FAILURE_STACK("Unable to execute HTTP request: Connection refused", "SdkClientException", [
      "software.amazon.awssdk.core.exception.SdkClientException.create(SdkClientException.java:44)",
      "com.reposilite.storage.s3.S3StorageProvider.write(S3StorageProvider.kt:121)",
    ]),
  },
]

const todayLocalDate = () => {
  const now = new Date()
  return [now.getFullYear(), now.getMonth() + 1, now.getDate()]
}
const permissionObject = (value) =>
  value === "m" || value === "access-token:manager"
    ? { identifier: "access-token:manager", shortcut: "m" }
    : { identifier: value, shortcut: value }
const routePermissionObject = (shortcut) =>
  shortcut === "w"
    ? { identifier: "route:write", shortcut: "w" }
    : { identifier: "route:read", shortcut: "r" }

let tokenSequence = 2
let accessTokens = [
  {
    identifier: { type: "PERSISTENT", value: 1 },
    name: "admin",
    createdAt: [2023, 1, 1],
    description: "Primary management token",
    expiresAt: null,
    permissions: [{ identifier: "access-token:manager", shortcut: "m" }],
    routes: [],
  },
  {
    identifier: { type: "PERSISTENT", value: 2 },
    name: "ci-deployer",
    createdAt: [2023, 2, 15],
    description: "Used by the release pipeline",
    expiresAt: null,
    permissions: [],
    routes: [
      { path: "/releases", permission: { identifier: "route:read", shortcut: "r" } },
      { path: "/releases", permission: { identifier: "route:write", shortcut: "w" } },
      { path: "/snapshots", permission: { identifier: "route:read", shortcut: "r" } },
    ],
  },
]

// The dashboard now draws meters against maxMemory and maxThreads, so the stub has to stay
// inside its own limits. It used to add to both without bound, which drove every reading
// past 100% within a minute and made the panel impossible to judge.
const MAX_MEMORY_MB = 512
const MAX_THREADS = 64

// A rolling window of snapshots, so the trend charts have a shape to draw rather than the
// two points the stub used to return.
const snapshots = []

const recordSnapshot = () => {
  snapshots.push({ at: new Date().getTime(), memory: Math.round(memory), threads })
  if (snapshots.length > 120) snapshots.shift()
}

for (let step = 60; step > 0; step--) {
  snapshots.push({
    at: new Date().getTime() - step * 30000,
    memory: Math.round(140 + Math.sin(step / 6) * 60 + step / 3),
    threads: Math.round(18 + Math.sin(step / 4) * 6)
  })
}

setInterval(() => {
  memory = Math.min(MAX_MEMORY_MB, Math.max(64, memory + (Math.random() - 0.45) * 20))
  threads = Math.min(MAX_THREADS, Math.max(4, threads + (Math.random() < 0.5 ? -1 : 1)))
  uptime += 5000
  recordSnapshot()
}, 5000)

const statisticsSeries = [
  {
    name: 'Releases',
    data: generateDayWiseTimeSeries(new Date('11 Feb 2022 GMT').getTime(), 20, {
      min: 10,
      max: 60
    })
  },
  {
    name: 'Snapshots',
    data: generateDayWiseTimeSeries(new Date('11 Feb 2022 GMT').getTime(), 20, {
      min: 10,
      max: 20
    })
  },
  {
    name: 'Maven Central',
    data: generateDayWiseTimeSeries(new Date('11 Feb 2022 GMT').getTime(), 20, {
      min: 10,
      max: 15
    })
  }
]

application
  .get("/", (req, res) => res.send("Ingot stub API"))
  .use((req, res, next) => {
    console.log("Requested fake " + req.method + " " + req.url)
    res.setHeader("Access-Control-Allow-Origin", "*")
    res.setHeader("Access-Control-Allow-Headers", "*")
    res.setHeader(
      "Access-Control-Allow-Methods",
      "PUT, PATCH, POST, GET, HEAD, DELETE, OPTIONS"
    )
    next()
  })
  .use(express.text())
  .use(bodyParser.raw({ limit: '100mb', extended: true }))
  .use(bodyParser.json())
  .get('/api/settings/domains', (req, res) => res.send(['maven', 'frontend']))
  .get('/api/settings/schema/maven', (req, res) => res.send(mavenSettingsSchema))
  .get('/api/settings/domain/maven', (req, res) => res.send(mavenSettingsEntity))
  .put('/api/settings/domain/maven', (req, res) => { mavenSettingsEntity = req.body; res.send("") })
  .get('/api/settings/schema/frontend', (req, res) => res.send(frontendSettingsSchema))
  .get('/api/settings/domain/frontend', (req, res) => res.send(frontendSettingsEntity))
  .put('/api/settings/domain/frontend', (req, res) => { frontendSettingsEntity = req.body; res.send("") })
  .get('/api/tokens', (req, res) =>
    authorized(req, () => res.send(accessTokens), () => invalidCredentials(res))
  )
  .get('/api/tokens/:name', (req, res) =>
    authorized(req, () => {
      const token = accessTokens.find(entry => entry.name === req.params.name)
      token ? res.send(token) : res.status(404).send({ status: 404, message: "Token not found" })
    }, () => invalidCredentials(res))
  )
  .put('/api/tokens/:name', (req, res) =>
    authorized(req, () => {
      const name = req.params.name
      const existing = accessTokens.find(entry => entry.name === name)
      const secret = req.body.secret || crypto.randomBytes(24).toString("hex")
      const permissions = (req.body.permissions || []).map(permissionObject)
      const routes = (req.body.routes || []).flatMap(route =>
        (route.permissions || []).map(permission => ({ path: route.path, permission: routePermissionObject(permission) }))
      )
      const token = {
        identifier: existing ? existing.identifier : { type: req.body.type || "PERSISTENT", value: ++tokenSequence },
        name,
        createdAt: todayLocalDate(),
        description: req.body.description || "",
        expiresAt: req.body.expiresAt ? Math.floor(Date.parse(req.body.expiresAt) / 1000) : null,
        permissions,
        routes,
      }
      accessTokens = accessTokens.filter(entry => entry.name !== name).concat(token)
      res.send({ accessToken: token, permissions, routes, secret })
    }, () => invalidCredentials(res))
  )
  .patch('/api/tokens/:name', (req, res) =>
    authorized(req, () => {
      const name = req.params.name
      const existing = accessTokens.find(entry => entry.name === name)
      if (!existing) return res.status(404).send({ status: 404, message: "Token not found" })
      const token = { ...existing }
      if (req.body.description != null) token.description = req.body.description
      if (req.body.permissions != null) token.permissions = req.body.permissions.map(permissionObject)
      if (req.body.routes != null) token.routes = req.body.routes.flatMap(route =>
        (route.permissions || []).map(permission => ({ path: route.path, permission: routePermissionObject(permission) }))
      )
      if ('expiresAt' in req.body) token.expiresAt = req.body.expiresAt ? Math.floor(Date.parse(req.body.expiresAt) / 1000) : null
      accessTokens = accessTokens.filter(entry => entry.name !== name).concat(token)
      res.send(token)
    }, () => invalidCredentials(res))
  )
  .delete('/api/tokens/:name', (req, res) =>
    authorized(req, () => {
      accessTokens = accessTokens.filter(entry => entry.name !== req.params.name)
      res.send("")
    }, () => invalidCredentials(res))
  )
  .post('/api/tokens/:name/secret', (req, res) =>
    authorized(req, () => {
      const token = accessTokens.find(entry => entry.name === req.params.name)
      token
        ? res.send(crypto.randomBytes(24).toString("hex"))
        : res.status(404).send({ status: 404, message: "Token not found" })
    }, () => invalidCredentials(res))
  )
  .get(
    "/api/maven/details/snapshots",
    respond(createDirectoryDetails("/snapshot", []))
  )
  .get("/api/maven/details/private", (req, res) => {
    authorized(
      req,
      () =>
        res.send(
          createDirectoryDetails("/private", [createDirectoryDetails("1.0.0")])
        ),
      () => invalidCredentials(res)
    )
  })
  .get("/private/maven-metadata.xml", (req, res) => {
    authorized(
      req,
      () =>
        res.send(`
      <metadata>
        <groupId>default</groupId>
        <artifactId>private</artifactId>
        <versioning>
          <release>1.0.0</release>
          <versions>
            <version>1.0.0</version>
          </versions>
        </versioning>
      </metadata>
      `),
      () => invalidCredentials(res)
    )
  })
  .get(
    "/api/maven/details/filled",
    respond(
      createDirectoryDetails(
        "/filled",
        Array(80)
          .fill(undefined)
          .map(() => {
            const a = crypto.randomBytes(10).toString('hex').substring(0, 1 + Math.round(1 * Math.random()))
            const b = crypto.randomBytes(10).toString('hex').substring(0, 1 + Math.round(3 * Math.random()))
            const c = crypto.randomBytes(10).toString('hex').substring(0, 1 + Math.round(6 * Math.random()))
            const d = crypto.randomBytes(10).toString('hex').substring(0, 1 + Math.round(9 * Math.random()))
            return createDirectoryDetails(a + '-' + b + '-' + c + '-' + d)
          })
          .concat(
            Array(10)
              .fill(undefined)
              .map(() => createFileDetails(crypto.randomBytes(7).toString('hex'),"text/html", 4096))
          )
      )
    )
  )
  .get(
    "/api/maven/details/releases",
    respond(
      createDirectoryDetails("/releases", [createDirectoryDetails("gav")])
    )
  )
  .get(
    "/api/maven/details/releases/gav",
    respond(
      createDirectoryDetails("/releases/gav", [
        createDirectoryDetails("0.1.0"),
        createDirectoryDetails("1.0.0"),
        createFileDetails("maven-metadata.xml", "text/xml", 4096),
      ])
    )
  )
  .get(
    "/api/maven/details/releases/gav/1.0.0",
    respond(
      createDirectoryDetails("/releases/gav/1.0.0", [
        createFileDetails("gav-1.0.0.jar", "application/jar-archive", 1337),
        createFileDetails("gav-1.0.0.jar.md5", "text/plain", 5),
      ])
    )
  )
  .get(
    "/api/maven/details/releases/gav/0.1.0",
    respond(
      createDirectoryDetails("/releases/gav/0.1.0", [
        createFileDetails("gav-0.1.0.jar", "application/jar-archive", 1337),
      ])
    )
  )
  .get("/releases/gav/1.0.0/gav-1.0.0.jar", respond("content"))
  .get("/releases/gav/0.1.0/gav-0.1.0.jar", respond("content"))
  .get(
    "/releases/gav/maven-metadata.xml",
    respond(`
  <metadata>
    <groupId>g.a.v</groupId>
    <artifactId>gav</artifactId>
    <versioning>
      <release>1.0.0</release>
      <versions>
        <version>0.1.0</version>
        <version>1.0.0</version>
      </versions>
    </versioning>
  </metadata>
  `)
  )
  .get("/api/auth/me", (req, res) => {
    authorized(
      req,
      () =>
        res.send({
          accessToken: {
            id: 1,
            name: "name",
            createdAt: Date.now(),
            description: "Description",
          },
          permissions: [
            {
              identifier: "access-token:manager"
            }
          ],
          routes: [
            {
              path: "/",
              permission: {
                identifier: "route:read",
              },
            },
            {
              path: "/",
              permission: {
                identifier: "route:write",
              },
            },
          ],
        }),
      () => invalidCredentials(res)
    )
  })
  .get("/api/status/instance", (req, res) => {
    authorized(
      req,
      () => {
        res.send({
          version: '1.0.0',
          // Deliberately ahead of `version`, so the "update available" state on the version
          // tile is reachable without editing this file.
          latestVersion: '1.1.0',
          uptime: uptime,
          usedMemory: memory,
          maxMemory: MAX_MEMORY_MB,
          usedThreads: threads,
          maxThreads: MAX_THREADS,
          failuresCount: recordedFailures.length
        })
      },
      () => invalidCredentials(res)
    )
  })
  .get("/api/status/snapshots", (req, res) => {
    authorized(
      req,
      () => {
        res.send(snapshots)
      },
      () => invalidCredentials(res)
    )
  })
  .get("/api/status/failures", (req, res) => {
    authorized(
      req,
      () => {
        res.send({
          failures: [...recordedFailures]
            .sort((a, b) => a.ageMs - b.ageMs)
            .map(({ id, ageMs, source, type, message, count, stacktrace }) => ({
              id,
              timestamp: new Date(Date.now() - ageMs).toISOString(),
              source,
              type,
              message,
              stacktrace,
              count,
            })),
        })
      },
      () => invalidCredentials(res)
    )
  })
  .delete("/api/status/failures", (req, res) => {
    authorized(
      req,
      () => {
        recordedFailures = []
        res.send({})
      },
      () => invalidCredentials(res)
    )
  })
  // Public, like the real endpoint: the dashboard reads it without credentials.
  .get("/api/status/health", (req, res) => {
    res.send({ status: "UP" })
  })
  // Stands in for the Swagger UI plugin, which serves its page at /swagger.
  .get("/swagger", (req, res) => {
    res.type("html").send('<!doctype html><html><head><title>Ingot API</title></head><body><div id="swagger-ui"></div></body></html>')
  })
  // Stands in for the Prometheus plugin, which answers 401 to anonymous scrapes.
  .get("/metrics", (req, res) => {
    authorized(
      req,
      () => {
        res.type("text/plain").send(`# TYPE ingot_failures_recorded gauge\ningot_failures_recorded ${recordedFailures.length}\n`)
      },
      () => invalidCredentials(res)
    )
  })
  // Public on purpose, mirroring the real server: a detached dashboard reads its own
  // settings here before it has any credentials.
  .get("/api/frontend/settings", (req, res) => {
    const settings = frontendSettingsEntity
    res.send({
      placeholders: {
        "{{REPOSILITE.BASE_PATH}}": "/",
        "{{REPOSILITE.VITE_BASE_PATH}}": ".",
        "{{REPOSILITE.ID}}": settings.id,
        "{{REPOSILITE.TITLE}}": settings.title,
        "{{REPOSILITE.DESCRIPTION}}": settings.description,
        "{{REPOSILITE.ORGANIZATION_WEBSITE}}": settings.organizationWebsite,
        "{{REPOSILITE.ORGANIZATION_LOGO}}": settings.organizationLogo,
        "{{REPOSILITE.ICP_LICENSE}}": settings.icpLicense,
        "{{REPOSILITE.PRIVACY_POLICY}}": settings.privacyPolicy,
        "{{REPOSILITE.JAVADOC_SUFFIXES}}": "-javadoc.jar,-groovydoc.jar"
      },
      id: settings.id,
      title: settings.title,
      description: settings.description,
      organizationWebsite: settings.organizationWebsite,
      organizationLogo: settings.organizationLogo,
      privacyPolicy: settings.privacyPolicy,
      icpLicense: settings.icpLicense,
      appearance: settings.appearance
    })
  })
  .get("/api/statistics/resolved/all", (req, res) => {
    authorized(
      req,
      () =>
        res.send({
          statisticsEnabled: true,
          repositories: statisticsSeries
        }),
      () => invalidCredentials(res)
    )
  })
  .ws("/api/console/sock", (connection) => {
    let authenticated = false

    connection.on("message", (message) => {
      if (message == "Authorization:name:secret") {
        sendMessage(connection, "DEBUG | Authorized")
        authenticated = true
      }

      if (!authenticated || message == "stop") {
        sendMessage(connection, "Connection closed")
        connection.close()
        return
      }

      sendMessage(connection, "INFO | Response: " + message)
    })
  })
  .get("/api/maven/details", (req, res) => {
    const repositories = createDirectoryDetails("/", [
      createDirectoryDetails("releases"),
      createDirectoryDetails("snapshots"),
      createDirectoryDetails("filled"),
    ])
    authorized(req, () =>
      repositories.files.push(createDirectoryDetails("private"))
    )
    res.send(repositories)
  })
  .put(/.*/, (req, res) => {
    authorized(
      req,
      () => {
        uploadedFiles.push({
          file: req.url,
          content: req.body
        })
        console.log(`File ${req.url} has been uploaded`)
      },
      () => invalidCredentials(res)
    )
  })
  .delete(/.*/, (req, res) => {
    authorized(
      req,
      () => {
        uploadedFiles = uploadedFiles.filter(entry => entry.file == req.url)
        console.log(`File ${req.url} has been deleted`)
      },
      () => invalidCredentials(res)
    )
  })
  .get(/.*/, (req, res) =>
    res.status(404).send({
      status: 404,
      message: "Not found",
    })
  )
  .listen(FAKE_API_PORT)

console.log(`Ingot stub API started on port ${FAKE_API_PORT}`)
