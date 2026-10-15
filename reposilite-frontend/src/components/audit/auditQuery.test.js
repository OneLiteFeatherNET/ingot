import { describe, it, expect } from 'vitest'
import {
  ACTION_OPTIONS,
  actionLabel,
  actorLocationOf,
  auditFiltersToQuery,
  auditParams,
  auditRangeBounds,
  isoToLocalInput,
  localInputToIso,
  readAuditFilters,
  targetLabelOf,
  targetLocationOf,
} from './auditQuery'

// A fixed reference time, so the presets are the same on every day the suite runs.
const NOW = Date.parse('2026-10-15T12:00:00Z')

describe('readAuditFilters', () => {
  it('reads an empty address as the unfiltered view', () => {
    expect(readAuditFilters({})).toEqual({
      action: '', actor: '', repository: '', text: '', range: 'all', from: '', to: '',
    })
  })

  it('drops an action that the log does not know instead of sending it', () => {
    expect(readAuditFilters({ action: 'DROP_EVERYTHING' }).action).toBe('')
  })

  it('keeps a known action and trims the text fields', () => {
    const filters = readAuditFilters({ action: 'TOKEN_CREATED', actor: '  ci  ', q: ' bot ' })

    expect(filters.action).toBe('TOKEN_CREATED')
    expect(filters.actor).toBe('ci')
    expect(filters.text).toBe('bot')
  })

  it('falls back to all time for a range that is not a preset', () => {
    expect(readAuditFilters({ range: '90d' }).range).toBe('all')
    expect(readAuditFilters({ range: '24h' }).range).toBe('24h')
  })
})

describe('auditFiltersToQuery', () => {
  it('leaves the default values out of the address', () => {
    expect(auditFiltersToQuery(readAuditFilters({}))).toEqual({})
  })

  it('writes every filter that is set under the name the address uses', () => {
    const query = auditFiltersToQuery({
      action: 'ARTIFACT_DEPLOYED', actor: 'ci', repository: 'releases', text: 'app', range: '7d', from: '', to: '',
    })

    expect(query).toEqual({ action: 'ARTIFACT_DEPLOYED', actor: 'ci', repository: 'releases', q: 'app', range: '7d' })
  })

  it('keeps the custom bounds only when the custom range is chosen', () => {
    const custom = { action: '', actor: '', repository: '', text: '', range: 'custom', from: '2026-10-01T00:00:00.000Z', to: '' }
    expect(auditFiltersToQuery(custom)).toEqual({ range: 'custom', from: '2026-10-01T00:00:00.000Z' })

    expect(auditFiltersToQuery({ ...custom, range: '24h' })).toEqual({ range: '24h' })
  })

  it('round-trips through the address', () => {
    const query = { action: 'TOKEN_ROUTES_CHANGED', actor: 'admin', q: 'private', range: '30d' }

    expect(auditFiltersToQuery(readAuditFilters(query))).toEqual(query)
  })
})

describe('auditRangeBounds', () => {
  it('sends no bounds for all time', () => {
    expect(auditRangeBounds(readAuditFilters({}), NOW)).toEqual({})
  })

  it('counts a preset back from the reference time', () => {
    expect(auditRangeBounds(readAuditFilters({ range: '24h' }), NOW))
      .toEqual({ from: '2026-10-14T12:00:00.000Z' })
    expect(auditRangeBounds(readAuditFilters({ range: '7d' }), NOW))
      .toEqual({ from: '2026-10-08T12:00:00.000Z' })
    expect(auditRangeBounds(readAuditFilters({ range: '30d' }), NOW))
      .toEqual({ from: '2026-09-15T12:00:00.000Z' })
  })

  it('sends the custom bounds as ISO instants', () => {
    const filters = readAuditFilters({ range: 'custom', from: '2026-10-01T00:00:00Z', to: '2026-10-02T00:00:00Z' })

    expect(auditRangeBounds(filters, NOW)).toEqual({
      from: '2026-10-01T00:00:00.000Z',
      to: '2026-10-02T00:00:00.000Z',
    })
  })
})

describe('auditParams', () => {
  it('builds the call for the first page with only the filters that are set', () => {
    expect(auditParams(readAuditFilters({ action: 'LOGIN_FAILED' }), NOW)).toEqual({ action: 'LOGIN_FAILED' })
  })

  it('adds the cursor for a later page', () => {
    expect(auditParams(readAuditFilters({}), NOW, 'abc')).toEqual({ cursor: 'abc' })
  })

  it('maps the text search to the q parameter', () => {
    expect(auditParams(readAuditFilters({ q: 'bot' }), NOW)).toEqual({ q: 'bot' })
  })
})

describe('datetime-local conversion', () => {
  it('turns an ISO instant into the local value a datetime-local input shows', () => {
    const iso = new Date(2026, 9, 3, 14, 5).toISOString()

    expect(isoToLocalInput(iso)).toBe('2026-10-03T14:05')
  })

  it('turns a local value back into the same instant', () => {
    const value = '2026-10-03T14:05'

    expect(localInputToIso(value)).toBe(new Date(2026, 9, 3, 14, 5).toISOString())
  })

  it('returns an empty string for a blank or invalid value', () => {
    expect(isoToLocalInput('')).toBe('')
    expect(isoToLocalInput('not a date')).toBe('')
    expect(localInputToIso('')).toBe('')
    expect(localInputToIso('not a date')).toBe('')
  })
})

describe('labels and links', () => {
  it('offers every action with a label, after the all-actions option', () => {
    expect(ACTION_OPTIONS[0]).toEqual({ value: '', label: 'All actions' })
    expect(ACTION_OPTIONS.find(option => option.value === 'TOKEN_SECRET_REGENERATED').label).toBe('Secret regenerated')
    expect(actionLabel('CLEANUP_RUN')).toBe('Cleanup run')
  })

  it('links a repository path to the browser', () => {
    const event = { action: 'ARTIFACT_DEPLOYED', actor: 'ci', repository: 'releases', path: 'org/example/app-1.0.jar', details: null }

    expect(targetLocationOf(event)).toEqual({ name: 'browse', params: { qualifier: ['releases', 'org', 'example', 'app-1.0.jar'] } })
    expect(targetLabelOf(event)).toBe('releases/org/example/app-1.0.jar')
  })

  it('links a token change to the token editor', () => {
    const event = { action: 'TOKEN_ROUTES_CHANGED', actor: 'admin', details: { token: 'deploy-bot' } }

    expect(targetLocationOf(event)).toEqual({ name: 'token', params: { name: 'deploy-bot' } })
    expect(targetLabelOf(event)).toBe('Token deploy-bot')
  })

  it('names a changed settings domain without linking it', () => {
    const event = { action: 'SETTINGS_CHANGED', actor: 'admin', details: { domain: 'frontend', fields: ['title'] } }

    expect(targetLocationOf(event)).toBeNull()
    expect(targetLabelOf(event)).toBe('Settings frontend')
  })

  it('links the actor of an authenticated token to its editor', () => {
    expect(actorLocationOf({ action: 'ARTIFACT_DEPLOYED', actor: 'ci' })).toEqual({ name: 'token', params: { name: 'ci' } })
  })

  it('does not link the actor when it is not a token', () => {
    expect(actorLocationOf({ action: 'CLEANUP_RUN', actor: 'system' })).toBeNull()
    expect(actorLocationOf({ action: 'ARTIFACT_DEPLOYED', actor: 'anonymous' })).toBeNull()
    expect(actorLocationOf({ action: 'LOGIN_FAILED', actor: 'guessed-name' })).toBeNull()
  })
})
