## MODIFIED Requirements

### Requirement: Eviction stays inside the javadoc cache of its repository

An instance SHALL refuse a received `javadoc.invalidated` event whose path is not a valid repository location or whose resolved directory is not strictly inside the javadoc cache of the named repository. A refused event SHALL delete nothing and SHALL be logged. The javadoc viewer SHALL apply the check, and an instance without the viewer SHALL delete nothing for a received event.

#### Scenario: Path leaves the cache root

- **WHEN** an event carries the path `../../etc`
- **THEN** no directory is removed and the refusal is logged

#### Scenario: Unknown repository

- **WHEN** an event names a repository that the instance does not have
- **THEN** no directory is removed

#### Scenario: Instance without the viewer

- **WHEN** an instance without the javadoc viewer receives an event
- **THEN** no directory is removed and the event is logged at debug level
