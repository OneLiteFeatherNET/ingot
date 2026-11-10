{{/*
Expand the name of the chart.
*/}}
{{- define "ingot.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name. Mirrors the helper of the upstream reposilite chart 1.4.5, so that a
release migrated with nameOverride and fullnameOverride keeps the names of the upstream release.
*/}}
{{- define "ingot.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Name of the dashboard workload and its Service in split mode.
*/}}
{{- define "ingot.dashboardFullname" -}}
{{- printf "%s-dashboard" (include "ingot.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "ingot.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Selector labels of the server. They are the same as the upstream chart, in every mode, so that a release moved
from the all-in-one workload to split keeps the immutable selector of its Deployment.
*/}}
{{- define "ingot.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ingot.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Selector labels of the dashboard. The instance differs from the server, so the server Service never selects a
dashboard pod and the dashboard Service never selects a server pod.
*/}}
{{- define "ingot.dashboardSelectorLabels" -}}
app.kubernetes.io/name: {{ include "ingot.name" . }}
app.kubernetes.io/instance: {{ printf "%s-dashboard" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "ingot.labels" -}}
helm.sh/chart: {{ include "ingot.chart" . }}
{{ include "ingot.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Common labels of the dashboard workload and its Service
*/}}
{{- define "ingot.dashboardLabels" -}}
helm.sh/chart: {{ include "ingot.chart" . }}
{{ include "ingot.dashboardSelectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Name of the service account to use
*/}}
{{- define "ingot.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "ingot.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Probe path of the server workload: the server root in allInOne mode, the health endpoint in split mode.
*/}}
{{- define "ingot.serverProbePath" -}}
{{- if eq .Values.mode "split" }}
{{- .Values.probes.serverPath }}
{{- else }}
{{- .Values.probes.path }}
{{- end }}
{{- end }}

{{/*
Path of the readiness probe of the server: probes.readinessPath when it is set, the readiness endpoint in split mode and
the root path in allInOne mode. Liveness is not affected.
*/}}
{{- define "ingot.readinessProbePath" -}}
{{- if .Values.probes.readinessPath }}
{{- .Values.probes.readinessPath }}
{{- else if eq .Values.mode "split" }}
{{- "/api/status/ready" }}
{{- else }}
{{- .Values.probes.path }}
{{- end }}
{{- end }}

{{/*
Whether the startup probe of the server is rendered, as "true" or "false". probes.startup.enabled wins when it is set.
Otherwise the probe is on in split mode and off in allInOne mode.
*/}}
{{- define "ingot.startupProbeEnabled" -}}
{{- if hasKey .Values.probes.startup "enabled" }}
{{- .Values.probes.startup.enabled }}
{{- else }}
{{- eq .Values.mode "split" }}
{{- end }}
{{- end }}

{{/*
One ingress path entry. Expects the dictionary keys path, pathType, service and port.
*/}}
{{- define "ingot.ingressPath" -}}
- path: {{ .path }}
  pathType: {{ .pathType }}
  backend:
    service:
      name: {{ .service }}
      port:
        number: {{ .port }}
{{- end }}

{{/*
Mount path of the cluster bus credentials. The NATS credentials file is read from nats.creds inside it.
*/}}
{{- define "ingot.clusterCredentialsPath" -}}
/etc/ingot/cluster
{{- end }}

{{/*
Image of the server. A digest replaces the tag, so the pinned image is name@digest.
*/}}
{{- define "ingot.serverImage" -}}
{{- if .Values.image.digest -}}
{{ .Values.image.name }}@{{ .Values.image.digest }}
{{- else -}}
{{ .Values.image.name }}:{{ default .Chart.AppVersion .Values.image.tag }}
{{- end -}}
{{- end }}

{{/*
Fails when env sets a variable that a typed value also sets. The message names the typed key and the variable.
Expects the dictionary keys root, name and owner.
*/}}
{{- define "ingot.checkEnvConflict" -}}
{{- $name := .name -}}
{{- $owner := .owner -}}
{{- range .root.Values.env -}}
{{- if eq (toString .name) $name -}}
{{- fail (printf "%s sets %s, so env must not set it too: remove %s from env or unset %s" $owner $name $name $owner) -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/*
Server environment entries that the typed HA values set, one YAML list item per variable. Nothing renders unless a
value is set. Expects the root context.
*/}}
{{- define "ingot.generatedEnv" -}}
{{- $root := . -}}
{{- with .Values.database.existingSecret }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "INGOT_LOCAL_DATABASE" "owner" "database.existingSecret") }}
- name: INGOT_LOCAL_DATABASE
  valueFrom:
    secretKeyRef:
      name: {{ . | quote }}
      key: {{ default "database" $root.Values.database.secretKey | quote }}
{{- end }}
{{- with .Values.objectStorage.existingSecret }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "AWS_ACCESS_KEY_ID" "owner" "objectStorage.existingSecret") }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "AWS_SECRET_ACCESS_KEY" "owner" "objectStorage.existingSecret") }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "AWS_REGION" "owner" "objectStorage.existingSecret") }}
- name: AWS_ACCESS_KEY_ID
  valueFrom:
    secretKeyRef:
      name: {{ . | quote }}
      key: AWS_ACCESS_KEY_ID
- name: AWS_SECRET_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: {{ . | quote }}
      key: AWS_SECRET_ACCESS_KEY
- name: AWS_REGION
  valueFrom:
    secretKeyRef:
      name: {{ . | quote }}
      key: AWS_REGION
      optional: true
{{- end }}
{{- with .Values.clusterBus.url }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "INGOT_CLUSTER_URL" "owner" "clusterBus.url") }}
- name: INGOT_CLUSTER_URL
  value: {{ . | quote }}
{{- end }}
{{- with .Values.clusterBus.passwordSecret }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "INGOT_CLUSTER_PASSWORD" "owner" "clusterBus.passwordSecret") }}
- name: INGOT_CLUSTER_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ . | quote }}
      key: password
{{- end }}
{{- with .Values.clusterBus.credentialsSecret }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "INGOT_CLUSTER_CREDENTIALS" "owner" "clusterBus.credentialsSecret") }}
- name: INGOT_CLUSTER_CREDENTIALS
  value: {{ printf "%s/nats.creds" (include "ingot.clusterCredentialsPath" $root) | quote }}
{{- end }}
{{- end }}
