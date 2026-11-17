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

import type { Request, Response } from "express"
import { instantOnWire } from "./wire"

const respond = (response: unknown) =>
  (_: Request, res: Response) => res.send(response)

const basicAuth = (req: Request) => {
  const b64auth = (req.get('Authorization') || '').split(' ')[1] || ''
  return Buffer.from(b64auth, 'base64').toString().split(':')
}

const authorized = (req: Request, success: () => void, failure?: () => void) => {
  const [login, password] = basicAuth(req)

  if (login === 'name' && password === 'secret') {
    console.log('Authorization successful for request ' + req.url)
    success()
  } else failure && failure()
}

const invalidCredentials = (res: Response) =>
  res.status(401).send({message:'Invalid credentials'})

const sendMessage = (connection: { send(data: string): unknown }, message: string) =>
  connection.send(`${new Date().toDateString()} ${message}`)

// A stored file carries the instant it was stored at, sent as epoch seconds like every Instant (see wire.ts).
const FAKE_STORED_AT = '2026-10-14T09:12:00Z'
const createFileDetails = (name: string, contentType: string, contentLength: number) =>
  ({ type: 'FILE', name, contentType, contentLength, lastModifiedTime: instantOnWire(FAKE_STORED_AT) })

const createDirectoryDetails = <T>(name: string, files?: T[]) =>
  ({ type: 'DIRECTORY', name, files })

const generateDayWiseTimeSeries = (baseval: number, count: number, yrange: { min: number, max: number }) => {
  let i = 0
  const series: { date: number, count: number }[] = []
  while (i < count) {
    series.push({
      date: baseval,
      count: Math.floor(Math.random() * (yrange.max - yrange.min + 1)) + yrange.min
    })
    baseval += 86400000
    i++
  }
  return series
}
   
export {
  respond,
  basicAuth,
  authorized,
  invalidCredentials,
  sendMessage,
  createFileDetails,
  createDirectoryDetails,
  generateDayWiseTimeSeries
}
