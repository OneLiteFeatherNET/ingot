import {computed, markRaw, ref} from 'vue'
import isEqual from 'lodash/isEqual'
import isPlainObject from 'lodash/isPlainObject'
import { useSession } from './session'
import { createSuccessToast, createErrorToast, errorMessage } from '../helpers/toast'
import { createAjv } from '@jsonforms/core'
import type { JsonFormsRendererRegistryEntry, JsonSchema, TesterContext, UISchemaElement } from '@jsonforms/core'
import type { AxiosResponse } from 'axios'
import { defaultStyles, mergeStyles, vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { default as ObjectRenderer, tester as objectTester } from '../components/renderers/ObjectRenderer.vue'
import { default as AllOfRenderer, tester as allOfTester } from '../components/renderers/AllOfRenderer.vue'
import { default as ArrayListRenderer, tester as arrayListTester } from '../components/renderers/ArrayListRenderer.vue'
import { settingsTabDomains } from '../components/settings/accessDomains'
import { default as OneOfRenderer, tester as oneOfTester } from '../components/renderers/OneOfRenderer.vue'
import { default as ConstantRenderer, tester as constantTester } from '../components/renderers/ConstantRenderer.vue'
import { default as OptionalRenderer, tester as optionalTester } from '../components/renderers/OptionalRenderer.vue'
import { default as SectionRenderer, tester as sectionTester } from '../components/renderers/SectionRenderer.vue'
import { default as BooleanSwitchRenderer, tester as booleanSwitchTester } from '../components/renderers/BooleanSwitchRenderer.vue'
import { default as MdTextFieldRenderer, tester as mdTextFieldTester } from '../components/renderers/MdTextFieldRenderer.vue'
import { collectionsOf, formSchemaOf, idText, validateNewId } from '../components/settings/collections'
import { default as MdSelectRenderer, tester as mdSelectTester, oneOfTester as mdOneOfSelectTester } from '../components/renderers/MdSelectRenderer.vue'
import { default as MdColourFieldRenderer, tester as mdColourFieldTester } from '../components/renderers/MdColourFieldRenderer.vue'
import { default as MdTextAreaRenderer, tester as mdTextAreaTester } from '../components/renderers/MdTextAreaRenderer.vue'
import { default as TagsFieldRenderer, tester as tagsFieldTester } from '../components/renderers/TagsFieldRenderer.vue'
import { default as AppearanceSectionRenderer, tester as appearanceSectionTester } from '../components/renderers/AppearanceSectionRenderer.vue'
import { default as UnsupportedRenderer, tester as unsupportedTester } from '../components/renderers/UnsupportedRenderer.vue'
import { invalidColourFields } from '../theme/settings'
import { setAppearance, applyAppearance } from './appearance'
import { etagOf, isPreconditionFailed } from './client'
import type { createClient } from './client'
import { settingsDomainNames } from './settingsDomains'
import { frontendSecretsHidden } from './placeholders'
import { diffSettings } from '../components/settings/settingsDiff'

type Client = ReturnType<typeof createClient>
/* A holder of the client: the session provides it as a computed ref. */
interface ClientRef { readonly value: Client }
/* The stored settings of one domain. The form reads and writes them by key, so the keys stay open. */
type DomainData = Record<string, unknown>
/* The form schema of a domain as formSchemaOf returns it. Only its title is read here. */
export interface FormSchema { title?: string; [field: string]: unknown }
/* One change of the conflict review, as settingsDiff lists it. Only the fields the review reads are named here. */
export interface SettingsChange { id: string; domain: string; domainTitle: string; kind: string; group: { key: string; title: string; id: string } | null; path: unknown[]; label: string; masked: boolean; before: unknown; after: unknown; index?: number }
/* The domain whose stored settings changed elsewhere, as the conflict dialog holds it. */
export interface SettingsConflict { kind: 'settings'; name: string; title: string; reviewed: boolean; server: DomainData | null }
/* A domain as the initial load receives it, before the schema defaults are applied. */
interface LoadedDomain { domain: string; schema: unknown; configuration: DomainData; etag: string | null }
/* A domain write that failed, with the error the client raised. */
interface DomainFailure { domain: string; error: Parameters<typeof isPreconditionFailed>[0] }

const cloneJson = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
const isRecord = (value: unknown): value is DomainData => isPlainObject(value)
type Tester = (uischema: UISchemaElement, schema: JsonSchema, context: TesterContext) => number
/* The client answers with untyped axios responses. This names the body a settings call returns. */
const bodyAs = <T>(request: Promise<AxiosResponse>): Promise<AxiosResponse<T>> => request as Promise<AxiosResponse<T>>
/* The renderers are .vue components, which eslint's type program cannot resolve, so each entry is named here. */
const rendererOf = (tester: unknown, renderer: unknown): JsonFormsRendererRegistryEntry => ({ tester, renderer } as JsonFormsRendererRegistryEntry)

/*
 * How many values differ between an edited configuration and the server snapshot. Objects are
 * compared key by key, arrays position by position, and a value present on only one side counts
 * once. So an edited field counts as one change, and an added or removed entry as one change too.
 */
export const countDifferences = (current: unknown, saved: unknown): number => {
  if (isEqual(current, saved)) return 0
  if (Array.isArray(current) && Array.isArray(saved)) {
    const length = Math.max(current.length, saved.length)
    let count = 0
    for (let index = 0; index < length; index++) {
      count += index >= current.length || index >= saved.length ? 1 : countDifferences(current[index], saved[index])
    }
    return count
  }
  if (isRecord(current) && isRecord(saved)) {
    const keys = new Set([...Object.keys(current), ...Object.keys(saved)])
    let count = 0
    for (const key of keys) count += countDifferences(current[key], saved[key])
    return count
  }
  return 1
}

/*
 * One store per client. The factory keeps no module state of its own, so a test builds a
 * fresh store and nothing leaks between tests. The application uses the single instance at
 * the bottom of this file.
 *
 * `snapshots` holds what the server last confirmed for each domain. A domain is dirty when
 * its edited value differs from that snapshot, so the flag is derived from the data and
 * survives the settings view being unmounted and mounted again.
 */
export const createConfigurationStore = (client: ClientRef, { secretsHidden: serverHidesSecrets = frontendSecretsHidden }: { secretsHidden?: () => boolean } = {}) => {
  const domains = ref<string[]>([])
  const schemas = ref<Record<string, FormSchema>>({})
  const configurations = ref<Record<string, DomainData>>({})
  const snapshots = ref<Record<string, DomainData>>({})
  const validity = ref<Record<string, boolean>>({})
  const selectedDomain = ref('')
  const loadError = ref<string | null>(null)
  const saveError = ref<string | null>(null)
  const saving = ref(false)
  /* The tag each domain was last read or written with. A write sends it back, so the server can refuse a stale draft. */
  const etags = ref<Record<string, string | null>>({})
  /* The domain whose stored settings changed elsewhere, or null. The conflict dialog renders it. */
  const conflict = ref<SettingsConflict | null>(null)
  // Whether the server returns secrets as ******** instead of their values. Read with the configuration.
  const secretsHidden = ref(false)

  /* markRaw keeps Vue from wrapping the instance in a reactive proxy, which breaks ajv's code generation. */
  const configurationValidator = markRaw(createAjv({
    useDefaults: true,
    removeAdditional: false,
    formats: {
      'repositories.storageProvider.quota': /^([1-9]\d*)([KkMmGg][Bb]|%)$/,
      'repositories.id': {
        type: 'string',
        validate: (name) => name in (configurations.value['maven'] as { repositories: DomainData }).repositories ||name.startsWith(' ') || name.endsWith(' ')
      },
      'repositories.proxied.allowedGroups': /^(\w+\.)*\w+$/,
    }
  }))
  configurationValidator.addFormat("repositories.id", {
    type: "string",
    validate: (value) => !value.endsWith(' ')
  })

  const titleOf = (domain: string): string => schemas.value[domain]?.title ?? domain

  /*
   * Ajv fills in schema defaults, and JsonForms does the same on mount and reports the result
   * as a change. The server snapshot gets the same defaults, or every tab would open dirty.
   * A schema that does not compile is left to the form to report, and the data stays as it is.
   */
  const withDefaults = (domain: string, data: DomainData): DomainData => {
    try {
      configurationValidator.compile(schemas.value[domain])(data)
    } catch {
      // the form reports the schema problem itself
    }
    return data
  }

  const isDirty = (domain: string): boolean => !isEqual(configurations.value[domain], snapshots.value[domain])
  const dirtyDomains = computed(() => domains.value.filter(isDirty))
  const hasChanges = computed(() => dirtyDomains.value.length > 0)
  const invalidDomains = computed(() => domains.value.filter(domain => validity.value[domain] === false))

  /* A domain with a difference always counts at least once, even when the diff finds nothing (for example an added undefined key). */
  const changeCount = computed(() => dirtyDomains.value.reduce(
    (total, domain) => total + Math.max(1, countDifferences(configurations.value[domain], snapshots.value[domain])),
    0
  ))
  const hasUnsavedChanges = computed(() => hasChanges.value)

  /*
   * The one place a domain takes the state the server confirmed: the draft, the saved snapshot and the tag
   * the write will carry move together, so a later save can never pair a tag with a state it does not describe.
   */
  const setSavedState = (domain: string, server: DomainData, etag: string | null): void => {
    configurations.value[domain] = server
    snapshots.value[domain] = cloneJson(server)
    etags.value[domain] = etag
    delete validity.value[domain]
  }

  const fetchConfiguration = async (): Promise<boolean> => {
    try {
      const domainNames = await settingsDomainNames(client.value)
      const loaded = await Promise.all(domainNames.map(async (domain): Promise<LoadedDomain> => {
        const [schema, configuration] = await Promise.all([
          bodyAs<unknown>(client.value.settings.schema(domain)),
          bodyAs<DomainData>(client.value.settings.fetch(domain))
        ])
        return { domain, schema: schema.data, configuration: configuration.data, etag: etagOf(configuration) }
      }))

      domains.value = domainNames
      loaded.forEach(({ domain, schema }) => { schemas.value[domain] = formSchemaOf(schema) as FormSchema })
      loaded.forEach(({ domain, configuration, etag }) => {
        setSavedState(domain, withDefaults(domain, cloneJson(configuration)), etag)
      })
      if (!domainNames.includes(selectedDomain.value)) selectedDomain.value = (settingsTabDomains(domainNames) as string[])[0] ?? domainNames[0]
      secretsHidden.value = serverHidesSecrets() === true

      loadError.value = null
      return true
    } catch (error) {
      loadError.value = `Cannot load configuration: ${errorMessage(error)}`
      createErrorToast(loadError.value)
      return false
    }
  }

  /* The stored state of a domain as the server has it now, with the schema defaults applied as on load, and its tag. */
  const loadServerState = async (domain: string): Promise<{ server: DomainData; etag: string | null }> => {
    const response = await bodyAs<DomainData>(client.value.settings.fetch(domain))
    return { server: withDefaults(domain, cloneJson(response.data)), etag: etagOf(response) }
  }

  /* Makes the server state the draft and the snapshot of a domain, and keeps the tag it came with. */
  const adoptServerState = async (domain: string, server: DomainData, etag: string | null): Promise<void> => {
    setSavedState(domain, server, etag)
    // The dashboard shows its appearance from these settings, so the admin sees the result now.
    if (domain === 'frontend') {
      setAppearance(server.appearance as Record<string, unknown> | undefined)
      await applyAppearance()
    }
  }

  const openSettingsConflict = (domain: string): void => {
    conflict.value = { kind: 'settings', name: domain, title: titleOf(domain), reviewed: false, server: null }
  }

  /*
   * Writes only the domains that differ from the server. Each one is written with the tag it was read with
   * and read back, so the snapshot holds what the server now has. One failing domain does not undo the others.
   * A domain the server refused with 412 changed elsewhere: it stays dirty and opens the conflict dialog.
   * Any other failure stays dirty too and is named in saveError.
   * `onlyDomains` limits the write to the named domains, so a page that edits one domain does not write the drafts of others.
   */
  const saveConfiguration = async (onlyDomains?: string[]): Promise<boolean> => {
    const scope = onlyDomains ?? domains.value
    if (saving.value || invalidDomains.value.some(domain => scope.includes(domain))) return false
    const pending = dirtyDomains.value.filter(domain => scope.includes(domain))
    if (pending.length === 0) return true

    saving.value = true
    saveError.value = null
    const outcomes = await Promise.allSettled(pending.map(async domain => {
      // The payload is copied before the first await, so later edits do not leak into this write.
      await client.value.settings.update(domain, cloneJson(configurations.value[domain]), etags.value[domain])
      const { server, etag } = await loadServerState(domain)
      await adoptServerState(domain, server, etag)
    }))
    saving.value = false

    const failures = outcomes
      .map((outcome, index) => outcome.status === 'rejected' ? { domain: pending[index], error: outcome.reason as DomainFailure['error'] } : null)
      .filter((failure): failure is DomainFailure => failure !== null)
    const stale = failures.filter(failure => isPreconditionFailed(failure.error))
    const others = failures.filter(failure => !isPreconditionFailed(failure.error))

    if (stale.length > 0) openSettingsConflict(stale[0].domain)
    if (others.length > 0) {
      const names = others.map(failure => titleOf(failure.domain)).join(', ')
      saveError.value = `Not saved: ${names}. ${errorMessage(others[0].error)}`
      createErrorToast(saveError.value)
      return false
    }
    if (stale.length > 0) return false

    createSuccessToast('Configuration updated. Other instances pick up saved changes within 10 seconds.')
    return true
  }

  /*
   * Fetches what the server has now and keeps it on the conflict, so the review can list the changes between it and
   * the draft. Neither the draft nor the tag changes: the next save still carries the tag it was read with.
   */
  const reviewConflict = async (): Promise<boolean> => {
    const current = conflict.value
    if (current?.kind !== 'settings') return false
    try {
      const { server } = await loadServerState(current.name)
      conflict.value = { ...current, reviewed: true, server }
      return true
    } catch (error) {
      saveError.value = `Cannot load ${titleOf(current.name)} from the server: ${errorMessage(error)}`
      createErrorToast(saveError.value)
      return false
    }
  }

  /* Takes the server state and drops the local draft of that domain. */
  const reloadConflict = async (): Promise<boolean> => {
    const current = conflict.value
    if (current?.kind !== 'settings') return false
    try {
      const { server, etag } = await loadServerState(current.name)
      await adoptServerState(current.name, server, etag)
      delete validity.value[current.name]
      conflict.value = null
      return true
    } catch (error) {
      saveError.value = `Cannot load ${titleOf(current.name)} from the server: ${errorMessage(error)}`
      createErrorToast(saveError.value)
      return false
    }
  }

  /*
   * Sends the local draft of the conflicting domain without a precondition, which replaces the change made
   * elsewhere. The dialog asks for a second confirmation before this runs.
   */
  const overwriteConflict = async (): Promise<boolean> => {
    const current = conflict.value
    if (current?.kind !== 'settings' || saving.value) return false
    saving.value = true
    saveError.value = null
    try {
      await client.value.settings.update(current.name, cloneJson(configurations.value[current.name]))
      const { server, etag } = await loadServerState(current.name)
      await adoptServerState(current.name, server, etag)
      delete validity.value[current.name]
      conflict.value = null
      createSuccessToast('Configuration updated. Other instances pick up saved changes within 10 seconds.')
      return true
    } catch (error) {
      saveError.value = `Not saved: ${titleOf(current.name)}. ${errorMessage(error)}`
      createErrorToast(saveError.value)
      return false
    } finally {
      saving.value = false
    }
  }

  /* Closes the dialog and keeps the draft. It stays dirty, so the next save meets the same conflict. */
  const dismissConflict = (): void => {
    conflict.value = null
  }

  /* Puts every edited domain back to its server snapshot. Nothing is sent. */
  const discardChanges = (): void => {
    dirtyDomains.value.forEach(domain => {
      configurations.value[domain] = cloneJson(snapshots.value[domain])
      delete validity.value[domain]
    })
    saveError.value = null
  }

  /* Empties every domain and saves it at once. The server treats an empty domain as its defaults. */
  const factoryReset = (): Promise<boolean> => {
    domains.value.forEach(domain => {
      configurations.value[domain] = {}
      delete validity.value[domain]
    })
    return saveConfiguration()
  }

  const setDomainData = (domain: string, data: DomainData): void => {
    configurations.value[domain] = data
  }

  /*
   * The saved state as a password protected file. The server reads the stored values, so the draft is never part of it.
   * The response is the file itself; the caller saves it.
   */
  const exportSaved = async (password: string) =>(await bodyAs<unknown>(client.value.settings.exportSaved(password))).data

  /* Decrypts a password protected file for review. Nothing is stored here: the result goes into the draft like a plain file. */
  const openExport = async (password: string, exported: unknown): Promise<Record<string, DomainData>> =>(await bodyAs<Record<string, DomainData>>(client.value.settings.openExport(password, exported))).data

  /* Puts checked values of the domains into the draft. Nothing is written: the user reviews and saves them like any edit. */
  const importDomains = (byDomain: Record<string, DomainData>): void => {
    Object.entries(byDomain).forEach(([domain, data]) => {
      setDomainData(domain, data)
      validateDomain(domain)
    })
  }

  /*
   * Validity covers the whole domain, not the entry on screen. Only one entry's form is mounted
   * at a time, so a per-form report would forget a broken entry as soon as the user moved on.
   * A schema that does not compile is reported by the form, so it does not block the domain here.
   */
  const validateDomain = (domain: string): void => {
    let valid: boolean
    try {
      valid = configurationValidator.compile(schemas.value[domain])(configurations.value[domain]) === true
    } catch {
      valid = true
    }
    validity.value[domain] = valid && idsAreUsable(domain) && invalidColourFields(configurations.value[domain]).length === 0
  }

  /*
   * Every entry needs an id that the new-entry dialog would accept: present, in the allowed
   * characters, and unique in its collection. An entry without one has no address, so the domain
   * cannot be saved or downloaded until it has one.
   */
  const idsAreUsable = (domain: string): boolean => collectionsOf(schemas.value[domain]).every((collection) => {
    const entries = (configurations.value[domain]?.[collection.key] ?? []) as unknown[]
    return entries.every((entry, index) => {
      const others = entries.filter((_, other) => other !== index).map(idText)
      return validateNewId(String(idText(entry)), others, String(collection.noun)) === ''
    })
  })

  const entriesOf = (domain: string, collection: string): unknown[] => {
    if (!configurations.value[domain]) configurations.value[domain] = {}
    if (!Array.isArray(configurations.value[domain][collection])) configurations.value[domain][collection] = []
    return configurations.value[domain][collection] as unknown[]
  }

  /*
   * The stable key of each entry, aligned with the entries by position. A key belongs to the entry
   * and not to its id, so the list and the detail pane can follow an entry while its fields change.
   * The keys are kept outside the reactive state: they are never sent to the server, and the view
   * only reads them.
   */
  let keySequence = 0
  const keysByCollection = new Map<string, string[]>()
  // The live key list, kept at one key per entry. It is only written by the edits below, never by a read.
  const keysFor = (domain: string, collection: string, count: number): string[] => {
    const name = `${domain}/${collection}`
    if (!keysByCollection.has(name)) keysByCollection.set(name, [])
    const keys = keysByCollection.get(name) ?? []
    while (keys.length < count) keys.push(`entry-${++keySequence}`)
    keys.length = count
    return keys
  }

  // A copy, read during render, so it writes nothing into the reactive state.
  const entryKeysOf = (domain: string, collection: string): string[] => [...keysFor(domain, collection, (configurations.value[domain]?.[collection] as unknown[] | undefined)?.length ?? 0)]

  /* Replaces the entry at `index`. An edit that arrives without an id keeps the id the entry had, so a change of the form can never drop an entry from the list. */
  const setEntry = (domain: string, collection: string, index: number, entry: unknown): void => {
    const entries = entriesOf(domain, collection)
    const previous = entries[index]
    const keepsId = isRecord(entry) && entry.id === undefined && isRecord(previous) && previous.id !== undefined
    entries[index] = keepsId ? { id: previous.id, ...entry } : entry
    validateDomain(domain)
  }

  /* Appends an entry and returns its position in the collection. */
  const addEntry = (domain: string, collection: string, entry: unknown): number => {
    const entries = entriesOf(domain, collection)
    keysFor(domain, collection, entries.length)
    entries.push(entry)
    keysFor(domain, collection, entries.length)
    validateDomain(domain)
    return entries.length - 1
  }

  const removeEntry = (domain: string, collection: string, index: number): void => {
    const entries = entriesOf(domain, collection)
    keysFor(domain, collection, entries.length).splice(index, 1)
    entries.splice(index, 1)
    validateDomain(domain)
  }

  const setValidity = (domain: string, valid: boolean): void => {
    validity.value[domain] = valid
  }

  /* What the server last confirmed for a domain, or undefined before it has loaded. Unsaved edits never show here. */
  const savedSettings = computed(() => snapshots.value)
  const savedConfiguration = (domain: string): DomainData | undefined => savedSettings.value[domain]

  /*
   * The changes between the server state the conflict review fetched and the draft, as the review sheet lists them.
   * Empty until the review has run. Reverting a change in the draft shrinks the list, as it would for unsaved changes.
   */
  const conflictChanges = computed((): SettingsChange[] => {
    const current = conflict.value
    if (current?.kind !== 'settings' || !current.server) return []
    return diffSettings(
      { [current.name]: current.server },
      { [current.name]: configurations.value[current.name] ?? {} },
      schemas.value
    ) as SettingsChange[]
  })

  return {
    domains,
    schemas,
    configurations,
    savedSettings,
    secretsHidden,
    selectedDomain,
    configurationValidator,
    loadError,
    saveError,
    saving,
    dirtyDomains,
    hasChanges,
    invalidDomains,
    isDirty,
    titleOf,
    fetchConfiguration,
    saveConfiguration,
    conflict,
    conflictChanges,
    reviewConflict,
    reloadConflict,
    overwriteConflict,
    dismissConflict,
    discardChanges,
    factoryReset,
    setDomainData,
    importDomains,
    exportSaved,
    openExport,
    setValidity,
    savedConfiguration,
    validateDomain,
    entryKeysOf,
    setEntry,
    addEntry,
    removeEntry,
    changeCount,
    hasUnsavedChanges
  }
}

const store = createConfigurationStore(useSession().client)

/* Lets a view receive another store instead of the shared one, which the tests use for a fresh store per test. */
export const configurationStoreKey = Symbol('configuration-store')

const renderers = markRaw([
  // Outlined fields for text, numbers and enums. They outrank the vanilla renderers, which they replace.
  rendererOf(mdSelectTester, MdSelectRenderer),
  rendererOf(mdOneOfSelectTester, MdSelectRenderer),
  // Colour fields outrank the text field they are strings of. They are not enums, so MdSelect does not take them.
  rendererOf(mdColourFieldTester, MdColourFieldRenderer),
  // The tags of a repository are chips; they outrank the generic list of strings, which they replace.
  rendererOf(tagsFieldTester, TagsFieldRenderer),
  // Prose with a length limit is a multi-line field with a counter, above the single line field.
  rendererOf(mdTextAreaTester, MdTextAreaRenderer),
  rendererOf(mdTextFieldTester, MdTextFieldRenderer),
  rendererOf(booleanSwitchTester, BooleanSwitchRenderer),
  rendererOf(arrayListTester, ArrayListRenderer),
  rendererOf(allOfTester, AllOfRenderer),
  rendererOf(oneOfTester, OneOfRenderer),
  rendererOf(constantTester, ConstantRenderer),
  rendererOf(optionalTester, OptionalRenderer),
  // The Appearance group of the dashboard settings, with a live preview of its colours under it.
  rendererOf(appearanceSectionTester, AppearanceSectionRenderer),
  // Groups become titled sections, replacing the fieldset of the vanilla group.
  rendererOf(sectionTester, SectionRenderer),
  // needed because without it hangs TODO find out why
  rendererOf((uischema: UISchemaElement, schema: JsonSchema, context: TesterContext) => {
    const rank = (objectTester as Tester)(uischema, schema, context)
    return rank === -1 || schema.title === 'Proxied Maven Repository' ? -1 : rank
  }, ObjectRenderer),
  ...(vanillaRenderers as JsonFormsRendererRegistryEntry[]),
  // Last of all: a schema node that no renderer above can draw shows a pointer to the JSON download, not nothing.
  rendererOf(unsupportedTester, UnsupportedRenderer),
])

/*
 * The renderers of @dzikoysk/vue-vanilla take every class name they emit from a styles
 * object that SettingsView provides, which is the only supported way to style them from
 * the outside. Utilities written here therefore replace what used to be a stylesheet
 * reaching into the library's DOM with selectors like `.control .input`.
 *
 * mergeStyles appends to the defaults instead of replacing them, so each element keeps its
 * original class name as well. The few rules that cannot live here still select on those
 * names.
 */
const configurationStyles = mergeStyles(defaultStyles, {
  control: {
    /* Field chrome (outline, height, corner) is applied to text inputs in SettingsView, because a checkbox shares this key. */
    description: 'md-body-small text-on-surface-variant px-2 pb-1',
    error: 'md-body-small text-error px-2 pt-1',
    /* Focus is drawn by the outlined field rules in SettingsView: a primary border, 2px in total. */
    input: 'md-body-large text-on-surface',
    /* No background image: the forms plugin draws a chevron there, on top of the one the field shows. */
    select: 'md-body-large block w-full max-w-xl min-h-14 rounded-corner-extra-small border border-outline bg-surface bg-none appearance-none px-4 text-on-surface',
    wrapper: 'flex flex-col items-start py-1'
  },
  verticalLayout: {
    root: 'flex flex-col gap-4 py-2'
  },
  group: {
    /* An outlined card: the same surface, outline and corner as MdCard, applied through the styles object. */
    root: 'flex flex-col gap-4 rounded-corner-medium border border-outline-variant bg-surface p-4 min-w-0',
    label: 'md-title-medium text-on-surface px-1'
  },
  arrayList: {
    /* The fieldset carries the list; the legend holds the add button. Browser fieldset chrome is reset. */
    root: 'flex flex-col gap-4 p-0 m-0 min-w-0 border-0',
    legend: 'flex flex-row flex-wrap items-center justify-between gap-2 w-full mb-0',
    label: 'md-title-medium text-on-surface',
    /* Spelled out because the defaults leave this one unset, unlike every other key here. */
    description: 'description md-body-small text-on-surface-variant',
    noData: 'md-body-medium p-4 rounded-corner-medium bg-surface-container text-on-surface-variant',
    itemToolbar: 'flex flex-row items-center justify-end min-h-12',
    itemLabel: 'mr-auto hidden',
    /* The tab bar already moves entries around, so the two arrows stay out of the way. */
    itemMoveUp: 'hidden p-2',
    itemMoveDown: 'hidden p-2',
    itemDelete: 'shrink-0'
  },
  oneOf: {
    root: 'one-of-container flex flex-col gap-4'
  }
})

export function useConfiguration() {
  return {
    store,
    renderers,
    configurationStyles
  }
}
