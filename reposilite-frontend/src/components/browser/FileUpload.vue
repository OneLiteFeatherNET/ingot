<script setup>
import { ref, computed, watchEffect } from 'vue'
import { createSuccessToast, createErrorToast } from '../../helpers/toast'
import { useSession } from '../../store/session'
import useQualifier from '../../store/qualifier'
import FileUpload from 'vue-upload-component'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdTextField from '../md/MdTextField.vue'
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

const uploadFiles = () => {
  if (stubPomEnabled.value && (artifactId.value == "" || groupId.value == "" || version.value == "")) {
    createErrorToast(`Cannot upload files, one of artifactId/groupId/version is empty`)
    return
  }

  files.value.forEach(vueFile =>
    client.value.maven.deploy(`${destination.value}/${vueFile.name}`, vueFile.file, checksumsEnabled.value)
      .then(() => createSuccessToast(`File ${vueFile.name} has been uploaded`))
      .then(() => removeFile(vueFile))
      .then(() => refreshQualifier())
      .catch(error => createErrorToast(`Cannot upload file ${vueFile.name} - ${error.response.status}: ${error.response.data.message}`))
      .catch(error => createErrorToast(error))
  )

  if (stubPomEnabled.value) {
    client.value.maven.generatePom(destination.value, groupId.value, artifactId.value, version.value)
      .then(() => createSuccessToast(`Stub POM.xml file has been generated`))
      .catch(error => createErrorToast(`Cannot generate stub POM file - ${error.response.status}: ${error.response.data.message}`))
      .catch(error => createErrorToast(error))
  }
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
            Drop files here, or click to choose them
          </p>
          <p class="md-body-medium text-on-surface-variant">
            Whole directories work too. Nothing is sent until you confirm.
          </p>
        </template>
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
          class="flex min-h-12 items-center gap-3 px-3"
        >
          <BrowserIcon
            name="file"
            class="text-on-surface-variant"
          />
          <span class="min-w-0 flex-1 truncate md-body-large">{{ file.name }}</span>
          <MdIconButton
            :aria-label="`Remove ${file.name}`"
            @click="removeFile(file)"
          >
            <BrowserIcon name="close" />
          </MdIconButton>
        </li>
      </ul>

      <div class="mt-4 space-y-1">
        <label class="flex min-h-12 cursor-pointer items-center gap-3">
          <input
            v-model="checksumsEnabled"
            type="checkbox"
            class="size-5 accent-primary"
          >
          <span class="md-body-large">Generate default checksums</span>
        </label>
        <label class="flex min-h-12 cursor-pointer items-center gap-3">
          <input
            v-model="stubPomEnabled"
            type="checkbox"
            class="size-5 accent-primary"
          >
          <span class="md-body-large">Generate stub POM file</span>
        </label>
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
