<script setup>
import { ref, computed, onMounted, watchEffect } from 'vue'
import { createSuccessToast, createErrorToast, errorMessage } from '../../helpers/toast'
import { createUploadQueue } from './uploadQueue'
import { useSession } from '../../store/session'
import useQualifier from '../../store/qualifier'
import FileUpload from 'vue-upload-component'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdTextField from '../md/MdTextField.vue'
import MdLinearProgress from '../md/MdLinearProgress.vue'
import MdCheckbox from '../md/MdCheckbox.vue'
import BrowserIcon from './BrowserIcon.vue'

const { client } = useSession()

const { qualifier, refreshQualifier } = useQualifier()
const repository = computed(() => qualifier.path.split("/")[0])
const defaultTo = qualifier.path.substring(repository.value.length + 1)
const to = ref(defaultTo)
const destination = computed(() => `${repository.value}/${to.value.replace(/(^\/+)|(\/+$)/g, '')}`)
const customDestination = ref(false)

const checksumsEnabled = ref(false)
const stubPomEnabled = ref(false)
const artifactId = ref('')
const groupId = ref('')
const version = ref('')

const stubPomGeneratedPath = computed(() => {
  let path = ''

  if (groupId.value) {
    path = groupId.value.replaceAll('.', '/')

    if (!path.endsWith('/')) {
      path = path + '/'
    }
  }

  if (artifactId.value) {
    path = path + artifactId.value + '/'
  }

  if (version.value) {
    path = path + version.value
  }

  return path
})

watchEffect(() => {
  if (stubPomEnabled.value && !customDestination.value) {
    to.value = defaultTo
      ? defaultTo + '/' + version.value
      : stubPomGeneratedPath.value
    }
  }
)

const pathMatchesPom = computed(() => {
  if (!stubPomEnabled.value) {
    return true
  }

  const generatedPath = stubPomGeneratedPath.value.endsWith('/')
    ? stubPomGeneratedPath.value.slice(0, -1)
    : stubPomGeneratedPath.value

  const currentPath = to.value.endsWith('/')
    ? to.value.slice(0, -1)
    : to.value

  return generatedPath === currentPath
})

const files = ref([])
const isEnabled = computed(() => files.value.length > 0)

const removeFile = (file) =>
  files.value = files.value.filter(element => element !== file)

// One batch at a time: a second press while files are in flight is ignored by the queue.
const queue = createUploadQueue()
const uploading = computed(() => queue.state.busy)

const STATUS_LABELS = {
  uploading: 'Uploading',
  failed: 'Upload failed. Press Upload files to try again.',
  done: 'Uploaded'
}
const statusLabel = (name) =>
  STATUS_LABELS[queue.state.statuses[name]] ?? 'Ready to upload'

// The drop zone is the vue-upload-component, which renders a hidden file input and a label
// that covers the zone for mouse users. The button below is the keyboard route to the picker.
const upload = ref(null)
const pickerInput = () => upload.value?.$el?.querySelector('input[type="file"]')
const choosePicker = () => pickerInput()?.click()

// The input stays in the DOM for the component to work, but a focus stop on an invisible
// element would strand keyboard users, so it leaves the tab order. The button is the stop.
onMounted(() => {
  const input = pickerInput()
  if (input) {
    input.tabIndex = -1
    input.setAttribute('aria-hidden', 'true')
  }
})

const uploadFiles = () => {
  if (uploading.value) {
    return
  }

  if (stubPomEnabled.value && (artifactId.value == "" || groupId.value == "" || version.value == "")) {
    createErrorToast(`Cannot upload files, one of artifactId/groupId/version is empty`)
    return
  }

  const tasks = files.value.map(vueFile => ({
    key: vueFile.name,
    run: () => client.value.maven.deploy(`${destination.value}/${vueFile.name}`, vueFile.file, checksumsEnabled.value)
      .then(() => {
        createSuccessToast(`File ${vueFile.name} has been uploaded`)
        removeFile(vueFile)
        refreshQualifier()
      })
      .catch(error => {
        createErrorToast(`Cannot upload file ${vueFile.name}: ${errorMessage(error)}`)
        throw error
      })
  }))

  if (stubPomEnabled.value) {
    tasks.push({
      key: 'stub POM',
      run: () => client.value.maven.generatePom(destination.value, groupId.value, artifactId.value, version.value)
        .then(() => createSuccessToast(`Stub POM.xml file has been generated`))
        .catch(error => {
          createErrorToast(`Cannot generate stub POM file: ${errorMessage(error)}`)
          throw error
        })
    })
  }

  queue.start(tasks)
}
</script>

<template>
  <section
    id="browser-upload"
    class="pt-8"
  >
    <h2 class="md-title-small text-on-surface-variant">
      Upload to <span class="font-mono normal-case text-on-surface">{{ '/' + destination }}</span>
    </h2>

    <FileUpload
      ref="upload"
      v-model="files"
      class="mt-3 block cursor-pointer rounded-corner-large border border-dashed border-outline bg-surface-container-low px-6 py-8 text-center transition-colors duration-(--md-sys-motion-duration-short3) hover:bg-surface-container-high"
      post-action="/upload/post"
      :multiple="true"
      :drop="true"
      :drop-directory="true"
    >
      <div class="flex flex-col items-center gap-2">
        <BrowserIcon
          name="upload"
          class="text-primary"
        />
        <p
          v-if="isEnabled"
          class="md-title-medium text-on-surface"
        >
          Deploy selected files to
          <span class="text-on-surface-variant">{{ '/' + destination }}</span>
        </p>
        <template v-else>
          <p class="md-title-medium text-on-surface">
            Drop files here, or choose them
          </p>
          <p class="md-body-medium text-on-surface-variant">
            Whole directories work too. Nothing is sent until you confirm.
          </p>
        </template>
        <!--
          Sits above the component's label overlay (z-10), so the button stays clickable and
          keeps its hover state for mouse users too.
        -->
        <MdButton
          variant="tonal"
          class="relative z-10 mt-2"
          :disabled="uploading"
          @click.prevent="choosePicker"
        >
          Choose files
        </MdButton>
      </div>
    </FileUpload>

    <div
      v-if="isEnabled"
      class="mt-4"
    >
      <ul class="divide-y divide-outline-variant overflow-hidden rounded-corner-medium bg-surface-container-low">
        <li
          v-for="file in files"
          :key="file.name"
          class="flex min-h-12 items-center gap-3 px-3 py-2"
        >
          <BrowserIcon
            name="file"
            class="text-on-surface-variant"
          />
          <span class="flex min-w-0 flex-1 flex-col">
            <span class="truncate md-body-large">{{ file.name }}</span>
            <span class="md-body-small text-on-surface-variant">{{ statusLabel(file.name) }}</span>
            <MdLinearProgress
              v-if="queue.state.statuses[file.name] === 'uploading'"
              indeterminate
              :label="`Uploading ${file.name}`"
              class="mt-1"
            />
          </span>
          <MdIconButton
            :aria-label="`Remove ${file.name}`"
            :disabled="uploading"
            @click="removeFile(file)"
          >
            <BrowserIcon name="close" />
          </MdIconButton>
        </li>
      </ul>

      <div class="mt-4 space-y-1">
        <MdCheckbox v-model="checksumsEnabled">
          Generate default checksums
        </MdCheckbox>
        <MdCheckbox v-model="stubPomEnabled">
          Generate stub POM file
        </MdCheckbox>
      </div>

      <div
        v-if="stubPomEnabled"
        class="mt-3 grid gap-3 rounded-corner-large bg-surface-container-low p-4 sm:grid-cols-3"
      >
        <MdTextField
          v-model="groupId"
          label="Group"
          supporting-text="For example com.dzikoysk"
          required
        />
        <MdTextField
          v-model="artifactId"
          label="Artifact"
          supporting-text="For example reposilite"
          required
        />
        <MdTextField
          v-model="version"
          label="Version"
          supporting-text="For example 3.0.0"
          required
        />
      </div>

      <div class="mt-4">
        <MdTextField
          v-model="to"
          label="Destination"
          variant="outlined"
          supporting-text="Relative to the repository"
          @update:model-value="customDestination = true"
        />
        <p
          v-if="!pathMatchesPom"
          class="mt-2 md-body-small text-warning"
        >
          ⚠ Warning: Path does not match artifact coordinates
        </p>
      </div>

      <div class="mt-4 flex justify-end">
        <MdButton
          variant="filled"
          :disabled="uploading"
          :aria-busy="uploading ? 'true' : undefined"
          @click.prevent="uploadFiles"
        >
          Upload files
        </MdButton>
      </div>
    </div>
  </section>
</template>

<style>
.file-uploads {
  display: block !important;
}
</style>
