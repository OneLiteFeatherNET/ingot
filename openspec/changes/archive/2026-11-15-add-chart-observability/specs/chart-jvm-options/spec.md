# Spec Delta

## Purpose

The heap and the garbage collector of the server follow the container's memory limit when an operator asks for it, and
nothing changes when the operator does not.

## ADDED Requirements

### Requirement: JVM flags only when set

The chart SHALL read the optional `jvm` block with `maxRAMPercentage`, `initialRAMPercentage`, `gc`, `activeProcessorCount`
and `extraOptions`. It SHALL render a flag only for a key that is set, and it SHALL render the flags into the merged
`JDK_JAVA_OPTIONS` entry of the server.

#### Scenario: Default render has no JVM flags

- **WHEN** the chart is rendered with no `jvm` value
- **THEN** no `JDK_JAVA_OPTIONS` entry is rendered by the JVM block, and `JAVA_OPTS` is unchanged

#### Scenario: Percentage is rendered

- **WHEN** the chart is rendered with `jvm.maxRAMPercentage: 70`
- **THEN** `JDK_JAVA_OPTIONS` contains `-XX:MaxRAMPercentage=70` once and no other `-XX:MaxRAMPercentage`

### Requirement: Conflicting flags fail the render

When the operator's `JDK_JAVA_OPTIONS` value and the JVM block set the same flag, the chart MUST fail the render and name
the flag.

#### Scenario: Same flag in two places

- **WHEN** `env` sets `JDK_JAVA_OPTIONS` to `-XX:MaxRAMPercentage=50` and `jvm.maxRAMPercentage` is `70`
- **THEN** `helm template` fails and its message names `-XX:MaxRAMPercentage`

### Requirement: Warning when JAVA_OPTS fixes the heap

When `jvm.maxRAMPercentage` is set and `JAVA_OPTS` contains an `-Xmx` option, NOTES.txt SHALL print a warning that the
explicit `-Xmx` overrides the percentage. The render SHALL still succeed.

#### Scenario: Default JAVA_OPTS with a percentage

- **WHEN** the chart is installed with `jvm.maxRAMPercentage: 70` and the default `JAVA_OPTS=-Xmx64M`
- **THEN** the install notes contain the warning and the render does not fail
