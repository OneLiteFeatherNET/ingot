# Spec Delta

## Purpose

The account control in the top bar names the signed-in person once, its menu explains who the person is in one identity block,
the menu's heading and items share one inset, and the keyboard and screen reader behaviour of the menu stays as it is.

## ADDED Requirements

### Requirement: Name shown once in the account menu

The account menu SHALL show the display name of the signed-in principal once, in the trigger. The menu header SHALL NOT repeat the
display name. A signed-in principal with no display name SHALL show its username in the trigger.

#### Scenario: Name in the menu

- **WHEN** a signed-in user with display name "Alice Example" and username "alice" opens the account menu
- **THEN** the trigger shows "Alice Example", and no element of the menu shows "Alice Example" a second time

#### Scenario: No display name

- **WHEN** a signed-in principal has no display name and its username is "ci-deploy"
- **THEN** the trigger shows "ci-deploy", and the menu header does not repeat it

### Requirement: Identity block in the menu header

The menu header SHALL show the username only when it differs from the display name, and SHALL show a line with the kind of the principal
("User" or "Machine") and its sign-in source. The sources SHALL be the provider ids of the access summary (`local`, `ldap`, `oidc`),
shown in a readable form, with an unknown id shown as the id itself. A principal with no provider id SHALL show "Local" when its kind is
"User".

#### Scenario: Username differs

- **WHEN** the display name is "Alice Example" and the username is "alice", and the provider is LDAP
- **THEN** the header shows "alice" on its own line and "User, LDAP" on the next line

#### Scenario: Username equals display name

- **WHEN** the display name and the username are both "alice"
- **THEN** the header shows no username line, only the kind and source line

### Requirement: Menu header and items share one inset

The menu header and every menu item SHALL use the same horizontal inset, so the text of the header and the labels of the items start on
one edge. The header SHALL NOT be a menu item and SHALL NOT receive focus.

#### Scenario: Header aligned with items

- **WHEN** the account menu is open at 1440 pixels
- **THEN** the left edge of the header text and the left edge of the item labels are the same distance from the menu's left edge

#### Scenario: Header is not focusable

- **WHEN** the account menu is opened from the keyboard
- **THEN** the first menu item has the focus, and the header is not in the focus order

### Requirement: Long names truncate with the full name available

A display name longer than the trigger SHALL be truncated visually. The full display name SHALL be available as the `title` of the
name text and as part of the trigger's accessible name. A long name SHALL NOT widen the top bar or push other top bar controls out of
view.

#### Scenario: Long name in the bar

- **WHEN** the display name is 80 characters long at 900 pixels
- **THEN** the trigger shows a truncated name, the full name is its `title`, and the top bar still shows the theme and the other controls
  without a horizontal scroll

### Requirement: Trigger name says what the button does

The trigger's accessible name SHALL be "Account menu, <display name>" so a screen reader announces that the button opens a menu. The
name SHALL be given by the account menu through a `triggerLabel` on the overflow menu; other callers of the overflow menu keep their
current naming.

#### Scenario: Screen reader name

- **WHEN** a screen reader reads the account button before it is opened
- **THEN** it announces "Account menu, Alice Example" and that the button has a popup menu

### Requirement: Keyboard behaviour unchanged

The account menu SHALL keep its keyboard behaviour: Enter or Space opens it, ArrowDown opens it and moves to the first item, arrow keys
move between items and wrap, Home and End go to the first and last items, Escape closes it and returns the focus to the trigger, and a
focus that leaves the menu closes it.

#### Scenario: Escape returns focus

- **WHEN** a keyboard user opens the account menu with ArrowDown and presses Escape
- **THEN** the menu closes and the trigger has the focus

### Requirement: Stable anchors

The account menu SHALL keep `data-ingot-component="menu-panel"` on its root and `data-ingot-component="md-overflow-menu"` on the
overflow menu. The header block SHALL carry `data-ingot-component="account-identity"`, registered in the anchor registry as
`experimental` until it settles.

#### Scenario: Anchor kept

- **WHEN** a plugin or a test looks up the account menu by its anchor
- **THEN** the element with `data-ingot-component="menu-panel"` exists in the top bar of a signed-in page
