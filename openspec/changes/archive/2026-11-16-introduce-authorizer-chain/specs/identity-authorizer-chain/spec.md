# Spec Delta

## Purpose

An internal authorizer chain that every check decision passes through. Phase A places the existing evaluator in it unchanged and leaves the empty slots that later changes fill.

## ADDED Requirements

### Requirement: Check decisions pass through one chain

Every check decision of the identity and token facades SHALL be taken by the authorization chain. A check decision is the answer to a single request for an action on a resource or an admin area, or the holds check of a guarded write.

#### Scenario: A repository check goes through the chain

- **WHEN** a token asks to upload to a repository path
- **THEN** the decision is taken by the chain, and the built-in authorizer is one of its stages

### Requirement: The chain order is fixed in phase A

The chain SHALL evaluate, in order: the disabled principal check, the restriction stages, the built-in authorizer, the binding stages and the plugin stages, and then the default deny. Stages with no entries SHALL answer NoOpinion.

#### Scenario: A disabled principal is refused before any stage

- **WHEN** the principal is disabled
- **THEN** the decision is Deny and no stage is consulted

#### Scenario: Empty stages do not change the result

- **WHEN** the restriction, binding and plugin stages are empty
- **THEN** the decision equals the decision of the built-in authorizer, with the default deny for NoOpinion

### Requirement: The built-in authorizer keeps its decisions

The built-in authorizer SHALL return the decision of the existing evaluator. A request with no matching rule SHALL return NoOpinion. An explicit deny SHALL return Deny. The instance-admin shortcut SHALL apply as it does today.

#### Scenario: No matching rule is NoOpinion

- **WHEN** no node or grant covers the request
- **THEN** the built-in authorizer answers NoOpinion, and the chain answers Deny

#### Scenario: The instance-admin shortcut is preserved

- **WHEN** an instance admin's token does not deny a path request that a repository deny covers
- **THEN** the request is allowed, as it is today

### Requirement: The explain output is unchanged

The trace of the permission check endpoint SHALL keep its shape and its entries. The chain SHALL pass the trace of the deciding stage through unchanged.

#### Scenario: The trace of a check is the same

- **WHEN** the permission check endpoint explains a decision before and after this change, for the same fixture
- **THEN** the response bodies are equal

### Requirement: The change adds no operator-visible behaviour

This change SHALL NOT add a configuration key, an environment variable, a manifest field or a public API. The chain SHALL be internal.

#### Scenario: No configuration is needed

- **WHEN** an installation upgrades to a build with this change
- **THEN** it starts with its existing configuration and every decision is the one it was before
