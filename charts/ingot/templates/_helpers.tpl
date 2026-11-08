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
Selector labels. They are the same as the upstream chart, so the all-in-one workload keeps its selector.
In split mode the workloads add app.kubernetes.io/component, so that the server Service never selects a dashboard pod.
*/}}
{{- define "ingot.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ingot.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
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
