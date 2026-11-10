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
Fails when a security profile is set together with a security block of the same workload. Both keys are named.
*/}}
{{- define "ingot.securityProfileCheck" -}}
{{- if .Values.deployment.securityProfile -}}
{{- if .Values.deployment.podSecurityContext.enabled -}}
{{- fail "deployment.securityProfile and deployment.podSecurityContext.enabled cannot be set together: unset one of them" -}}
{{- end -}}
{{- if .Values.deployment.containerSecurityContext.enabled -}}
{{- fail "deployment.securityProfile and deployment.containerSecurityContext.enabled cannot be set together: unset one of them" -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/*
Pod security context of a security profile. The restricted profile fixes uid, gid and fsGroup to 977, the
restricted-openshift profile leaves them to the cluster.
*/}}
{{- define "ingot.profilePodSecurityContext" -}}
runAsNonRoot: true
{{- if eq .Values.deployment.securityProfile "restricted" }}
runAsUser: 977
runAsGroup: 977
fsGroup: 977
{{- end }}
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{/*
Container security context of a security profile.
*/}}
{{- define "ingot.profileContainerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
runAsNonRoot: true
{{- if eq .Values.deployment.securityProfile "restricted" }}
runAsUser: 977
runAsGroup: 977
{{- end }}
capabilities:
  drop:
    - ALL
seccompProfile:
  type: RuntimeDefault
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
{{- $jdkJavaOptions := include "ingot.jdkJavaOptions" $root | trim }}
{{- with $jdkJavaOptions }}
- name: JDK_JAVA_OPTIONS
  value: {{ . | quote }}
{{- end }}
{{- if .Values.sharedConfiguration.enabled }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "INGOT_SHARED_CONFIGURATION_RELOAD" "owner" "sharedConfiguration") }}
- name: INGOT_SHARED_CONFIGURATION_RELOAD
  value: "true"
- name: INGOT_OPTS
  value: {{ include "ingot.sharedConfigurationOpts" $root | quote }}
{{- end }}
{{- if .Values.opentelemetry.enabled }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "OTEL_SERVICE_NAME" "owner" "opentelemetry") }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "OTEL_TRACES_EXPORTER" "owner" "opentelemetry") }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "OTEL_METRICS_EXPORTER" "owner" "opentelemetry") }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "OTEL_LOGS_EXPORTER" "owner" "opentelemetry") }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "OTEL_EXPORTER_OTLP_PROTOCOL" "owner" "opentelemetry") }}
- name: OTEL_SERVICE_NAME
  value: {{ default (include "ingot.fullname" $root) .Values.opentelemetry.serviceName | quote }}
- name: OTEL_TRACES_EXPORTER
  value: {{ .Values.opentelemetry.tracesExporter | quote }}
- name: OTEL_METRICS_EXPORTER
  value: {{ .Values.opentelemetry.metricsExporter | quote }}
- name: OTEL_LOGS_EXPORTER
  value: {{ .Values.opentelemetry.logsExporter | quote }}
- name: OTEL_EXPORTER_OTLP_PROTOCOL
  value: {{ .Values.opentelemetry.protocol | quote }}
{{- with .Values.opentelemetry.endpoint }}
{{- include "ingot.checkEnvConflict" (dict "root" $root "name" "OTEL_EXPORTER_OTLP_ENDPOINT" "owner" "opentelemetry.endpoint") }}
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: {{ . | quote }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Value of the server environment variable named by .name, as the last entry of env sets it. Empty when env does not set it.
Fails when that entry takes its value from a reference, because the chart cannot merge its flags into a reference.
Expects the dictionary keys root and name.
*/}}
{{- define "ingot.envEntryValue" -}}
{{- $name := .name -}}
{{- $value := "" -}}
{{- range .root.Values.env -}}
{{- if eq (toString .name) $name -}}
{{- if .valueFrom -}}
{{- fail (printf "env sets %s from a reference, so the chart cannot merge its flags into it: set %s to a plain value" $name $name) -}}
{{- end -}}
{{- $value = default "" .value -}}
{{- end -}}
{{- end -}}
{{- $value -}}
{{- end }}

{{/*
The JVM flags of the jvm block as one space separated string. A key renders a flag only when it is set.
*/}}
{{- define "ingot.jvmFlags" -}}
{{- $flags := list -}}
{{- with .Values.jvm.maxRAMPercentage -}}
{{- $flags = append $flags (printf "-XX:MaxRAMPercentage=%v" .) -}}
{{- end -}}
{{- with .Values.jvm.initialRAMPercentage -}}
{{- $flags = append $flags (printf "-XX:InitialRAMPercentage=%v" .) -}}
{{- end -}}
{{- with .Values.jvm.gc -}}
{{- $flags = append $flags (printf "-XX:+%s" .) -}}
{{- end -}}
{{- with .Values.jvm.activeProcessorCount -}}
{{- $flags = append $flags (printf "-XX:ActiveProcessorCount=%v" .) -}}
{{- end -}}
{{- range .Values.jvm.extraOptions -}}
{{- $flags = append $flags . -}}
{{- end -}}
{{- join " " $flags -}}
{{- end }}

{{/*
The flags that the chart adds to JDK_JAVA_OPTIONS of the server, as one space separated string.
*/}}
{{- define "ingot.chartJdkFlags" -}}
{{- $flags := include "ingot.jvmFlags" . -}}
{{- if .Values.opentelemetry.enabled -}}
{{- $flags = join " " (compact (list $flags "-javaagent:/otel/javaagent.jar")) -}}
{{- end -}}
{{- $flags -}}
{{- end }}

{{/*
The init container that copies the agent jar into the emptyDir of the server. Empty when the agent is off. With a security
profile the init container gets the profile's security context as well, because the restricted level applies to every
container of the pod.
*/}}
{{- define "ingot.otelInitContainer" -}}
{{- if .Values.opentelemetry.enabled -}}
- name: otel-agent
  image: {{ .Values.opentelemetry.agentImage | quote }}
  command: ["cp", "/javaagent.jar", "/otel/javaagent.jar"]
  securityContext:
    {{- if .Values.deployment.securityProfile }}
    {{- include "ingot.profileContainerSecurityContext" . | nindent 4 }}
    {{- else }}
    allowPrivilegeEscalation: false
    readOnlyRootFilesystem: true
    capabilities:
      drop:
        - ALL
    {{- end }}
  resources:
    {{- toYaml .Values.opentelemetry.resources | nindent 4 }}
  volumeMounts:
    - name: otel-agent
      mountPath: /otel
{{- end -}}
{{- end }}

{{/*
The option that a JVM flag sets. -XX:Name=value keys by Name. Every -XX:+UseXxxGC selects the collector, so all of them
share one key and two collectors conflict.
*/}}
{{- define "ingot.jvmFlagKey" -}}
{{- if and (hasPrefix "-XX:+Use" .) (hasSuffix "GC" .) -}}
collector
{{- else if contains "=" . -}}
{{- index (splitList "=" .) 0 -}}
{{- else -}}
{{- . -}}
{{- end -}}
{{- end }}

{{/*
Fails when the operator's JDK_JAVA_OPTIONS and the chart flags set the same option. Two values for one option in one
variable depend on the order of the JVM, so the chart refuses to guess. Expects the keys flags and operator.
*/}}
{{- define "ingot.checkJvmConflicts" -}}
{{- range $chart := splitList " " .flags -}}
{{- if $chart -}}
{{- $chartKey := include "ingot.jvmFlagKey" $chart -}}
{{- range $own := splitList " " $.operator -}}
{{- if $own -}}
{{- if eq (include "ingot.jvmFlagKey" $own) $chartKey -}}
{{- fail (printf "JDK_JAVA_OPTIONS sets %s and the chart sets %s too: remove one of them" $own $chart) -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/*
The merged JDK_JAVA_OPTIONS value: the operator's text first and unchanged, then the chart flags. Empty when the chart sets
no flag, so the operator's entry is left alone.
*/}}
{{- define "ingot.jdkJavaOptions" -}}
{{- $chart := include "ingot.chartJdkFlags" . | trim -}}
{{- if $chart -}}
{{- $own := include "ingot.envEntryValue" (dict "root" . "name" "JDK_JAVA_OPTIONS") | trim -}}
{{- include "ingot.checkJvmConflicts" (dict "flags" $chart "operator" $own) -}}
{{- join " " (compact (list $own $chart)) -}}
{{- end -}}
{{- end }}

{{/*
Comma separated names of the server variables that the chart writes itself with the operator's value merged in. The
operator's entries with these names are not rendered again, so no name appears twice.
*/}}
{{- define "ingot.chartOwnedEnv" -}}
{{- $names := list -}}
{{- if include "ingot.chartJdkFlags" . | trim -}}
{{- $names = append $names "JDK_JAVA_OPTIONS" -}}
{{- end -}}
{{- if .Values.sharedConfiguration.enabled -}}
{{- $names = append $names "INGOT_OPTS" -}}
{{- end -}}
{{- join "," $names -}}
{{- end }}

{{/*
Name of the ConfigMap that the chart renders for sharedConfiguration.content.
*/}}
{{- define "ingot.sharedConfigurationName" -}}
{{- printf "%s-shared-configuration" (include "ingot.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Name of the ConfigMap that the shared file volume reads: the existing one, or the one the chart renders.
*/}}
{{- define "ingot.sharedConfigurationSource" -}}
{{- if .Values.sharedConfiguration.existingConfigMap -}}
{{- .Values.sharedConfiguration.existingConfigMap -}}
{{- else -}}
{{- include "ingot.sharedConfigurationName" . -}}
{{- end -}}
{{- end }}

{{/*
Fails when sharedConfiguration is enabled without exactly one source. Both keys are named in the message.
*/}}
{{- define "ingot.sharedConfigurationCheck" -}}
{{- if .Values.sharedConfiguration.enabled -}}
{{- if and (not .Values.sharedConfiguration.content) (not .Values.sharedConfiguration.existingConfigMap) -}}
{{- fail "sharedConfiguration.enabled needs a source: set sharedConfiguration.content or sharedConfiguration.existingConfigMap" -}}
{{- end -}}
{{- if and .Values.sharedConfiguration.content .Values.sharedConfiguration.existingConfigMap -}}
{{- fail "sharedConfiguration.content and sharedConfiguration.existingConfigMap cannot be set together: unset one of them" -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/*
Checksum of the content that the chart renders. Empty for an existing ConfigMap, because the reload applies a change to it
without a restart.
*/}}
{{- define "ingot.sharedConfigurationChecksum" -}}
{{- if and .Values.sharedConfiguration.enabled .Values.sharedConfiguration.content (not .Values.sharedConfiguration.existingConfigMap) -}}
{{- sha256sum .Values.sharedConfiguration.content -}}
{{- end -}}
{{- end }}

{{/*
The INGOT_OPTS value with the --shared-configuration argument added. The entrypoint reads INGOT_OPTS, and REPOSILITE_OPTS only
when INGOT_OPTS is empty, so the value starts from the text the entrypoint would read. An argument that already names a shared
file fails the render, because the chart cannot choose between two.
*/}}
{{- define "ingot.sharedConfigurationOpts" -}}
{{- $opts := include "ingot.envEntryValue" (dict "root" . "name" "INGOT_OPTS") | trim -}}
{{- if not $opts -}}
{{- $opts = include "ingot.envEntryValue" (dict "root" . "name" "REPOSILITE_OPTS") | trim -}}
{{- end -}}
{{- range splitList " " $opts -}}
{{- if or (eq . "--shared-configuration") (eq . "--shared-config") (eq . "-sc") -}}
{{- fail (printf "env sets %s, and sharedConfiguration sets the shared file argument too: remove %s from env or disable sharedConfiguration" . .) -}}
{{- end -}}
{{- end -}}
{{- join " " (compact (list $opts "--shared-configuration /app/shared-config/configuration.shared.json")) -}}
{{- end }}

{{/*
Fails when a namespace or pod selector of networkPolicy.ingress is empty. An empty selector matches every namespace or
every pod, which would open the policy to all sources.
*/}}
{{- define "ingot.networkPolicyCheck" -}}
{{- range .Values.networkPolicy.ingress.fromNamespaces -}}
{{- if not . -}}
{{- fail "networkPolicy.ingress.fromNamespaces must not contain an empty selector: an empty selector matches every namespace" -}}
{{- end -}}
{{- end -}}
{{- range .Values.networkPolicy.ingress.fromPods -}}
{{- if not . -}}
{{- fail "networkPolicy.ingress.fromPods must not contain an empty selector: an empty selector matches every pod" -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/*
Ingress peers of a NetworkPolicy, one YAML list item per peer, without a leading newline. The listed namespaces and pods
come first. With the dictionary key dashboard set to true, the dashboard pods are added as well. Expects the keys root
and dashboard.
*/}}
{{- define "ingot.networkPolicyPeers" -}}
{{- range .root.Values.networkPolicy.ingress.fromNamespaces }}
- namespaceSelector:
    matchLabels:
{{ toYaml . | indent 6 }}
{{- end }}
{{- range .root.Values.networkPolicy.ingress.fromPods }}
- podSelector:
    matchLabels:
{{ toYaml . | indent 6 }}
{{- end }}
{{- if .dashboard }}
- podSelector:
    matchLabels:
{{ include "ingot.dashboardSelectorLabels" .root | indent 6 }}
{{- end }}
{{- end }}

{{/*
Egress rule that allows DNS to any destination. Without it the restricted egress breaks every name lookup.
*/}}
{{- define "ingot.networkPolicyDns" -}}
- ports:
    - port: 53
      protocol: UDP
    - port: 53
      protocol: TCP
{{- end }}
