import { describe, expect, it } from 'vitest'
import {
  exportRequest,
  importRequest,
  isEncryptedExport,
  passwordProblem
} from './settingsEncryption'

describe('password rules of the settings file', () => {
  it('refuses a password shorter than twelve characters', () => {
    expect(passwordProblem({ password: 'eleven-char', confirmation: 'eleven-char' })).toBe('short')
  })

  it('refuses a confirmation that differs from the password', () => {
    expect(passwordProblem({ password: 'twelve-chars!', confirmation: 'twelve-chars?' })).toBe('mismatch')
  })

  it('accepts a password of twelve characters that the confirmation repeats', () => {
    expect(passwordProblem({ password: 'twelve-chars!', confirmation: 'twelve-chars!' })).toBeNull()
  })

  it('checks only the length on import, where no confirmation is asked', () => {
    expect(passwordProblem({ password: 'twelve-chars!' })).toBeNull()
  })
})

describe('recognising an encrypted export', () => {
  it('recognises a file with the export format header', () => {
    expect(isEncryptedExport({ format: 'ingot-settings-export', version: 1 })).toBe(true)
  })

  it('treats a plain settings file as a plain file', () => {
    expect(isEncryptedExport({ maven: { id: 'releases' } })).toBe(false)
  })

  it('does not treat a list or a missing value as an export', () => {
    expect(isEncryptedExport([{ format: 'ingot-settings-export' }])).toBe(false)
    expect(isEncryptedExport(null)).toBe(false)
  })
})

describe('request bodies', () => {
  it('sends the password alone for an export', () => {
    expect(exportRequest('twelve-chars!')).toEqual({ password: 'twelve-chars!' })
  })

  it('sends the password with the file for an import', () => {
    const file = { format: 'ingot-settings-export', version: 1 }
    expect(importRequest('twelve-chars!', file)).toEqual({ password: 'twelve-chars!', export: file })
  })
})
