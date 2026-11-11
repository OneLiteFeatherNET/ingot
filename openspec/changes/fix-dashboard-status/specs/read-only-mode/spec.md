# Spec Delta

## Purpose

The dashboard is where an administrator reads the state of the instance. Read-only mode is a switch that changes the write path of
every replica, so it is changed on the Maintenance page, behind a confirmation, and the dashboard shows only whether it is on. The
switch is shown only to principals with the maintenance area, which is the area the API checks.

This delta applies to `read-only-mode` as defined by `add-read-only-mode`. It replaces the requirement "Visibility" of that change,
and it adds the requirements for the switch on the Maintenance page.

## MODIFIED Requirements

### Requirement: Visibility

Each instance SHALL expose a gauge `ingot_read_only` that is `1` while read-only is on and `0` otherwise. The instance status SHALL
carry a `readOnly` field and the source of the state. `GET /api/status/health` SHALL keep answering `UP` while read-only is on. The
dashboard SHALL show a banner while the state is on and SHALL NOT show the switch. The switch SHALL be shown only on the Maintenance
page, and only to principals with the maintenance area.

#### Scenario: Gauge while read-only

- **WHEN** read-only is on
- **THEN** the gauge `ingot_read_only` reports `1`

#### Scenario: Health unchanged

- **WHEN** read-only is on and a client calls `GET /api/status/health`
- **THEN** the response is `200` with status `UP`

#### Scenario: Banner on the dashboard

- **WHEN** read-only is on and a visitor opens the dashboard
- **THEN** the dashboard shows a banner that names read-only mode as on, says whether it is set for the cluster or by the environment of this instance, and shows no switch

#### Scenario: Banner link for a visitor with the maintenance area

- **WHEN** read-only is on and a principal with the maintenance area opens the dashboard
- **THEN** the banner links to the Maintenance page, where the state is changed

#### Scenario: Banner without the maintenance area

- **WHEN** read-only is on and a visitor without the maintenance area opens the dashboard
- **THEN** the banner is shown with no link, and the text says that a maintenance administrator can turn it off

#### Scenario: Switch hidden from a visitor without the area

- **WHEN** a visitor without the maintenance area opens the Maintenance page
- **THEN** no read-only switch is shown, because the probe `GET /api/admin/read-only` answers `403`

## ADDED Requirements

### Requirement: Switch on the Maintenance page

The Maintenance page SHALL show the read-only switch in its own section labelled "Writes", above the other panels. The switch SHALL show the stored state. While `INGOT_READ_ONLY` forces the state, the switch SHALL be disabled and its text SHALL name the variable, so the reason is shown before a change is attempted.

#### Scenario: Switch shows the state

- **WHEN** a principal with the maintenance area opens the Maintenance page while read-only is on
- **THEN** the switch is on and labelled "Read-only mode"

#### Scenario: Forced by the environment

- **WHEN** `INGOT_READ_ONLY=true` is set and a principal with the maintenance area opens the Maintenance page
- **THEN** the switch is on and disabled, and its text names `INGOT_READ_ONLY`

### Requirement: Confirmation before turning on

Turning read-only mode on SHALL open a dialog before any request is sent. The dialog SHALL state that every upload, deploy and delete is refused with `503` on every instance until read-only mode is turned off, that reads continue, and that scheduled cleanup and index runs wait. The request SHALL be sent only when the confirm button is activated. Cancel and Escape SHALL send no request and SHALL leave the switch off. Turning read-only mode off SHALL NOT open a dialog.

#### Scenario: Turning on asks first

- **WHEN** a principal with the maintenance area activates the switch while read-only is off
- **THEN** the dialog opens, and no `POST` is sent until its confirm button is activated

#### Scenario: Cancel sends nothing

- **WHEN** the dialog is open and the principal cancels it
- **THEN** no request is sent, the switch stays off, and focus returns to the switch

#### Scenario: Turning off needs no dialog

- **WHEN** read-only mode is on and a principal with the maintenance area activates the switch
- **THEN** a `DELETE` is sent without a dialog

### Requirement: Accessible description of the switch

The switch SHALL be a control with the role `switch` whose state is announced through `aria-checked`. Its consequence SHALL be given as an accessible description, so that a screen reader user hears what turning the switch on does when it takes focus. The description SHALL be the same text as the first sentence of the confirmation dialog.

#### Scenario: Description is read with the switch

- **WHEN** the switch on the Maintenance page receives focus
- **THEN** its accessible description names the refusal of writes on every instance and the continuing reads

#### Scenario: State is announced

- **WHEN** read-only mode is on
- **THEN** the switch has `aria-checked="true"`
