# Design

## Decisions

### Temporary file beside the target

An atomic rename is only possible inside one filesystem, so the temporary file must sit on the same filesystem as the
target. The directory of the target is the simplest place that guarantees it, and it is already created before the write.

The alternative was to keep the file in the system temporary directory and copy it to a sibling file before the rename.
That writes every upload twice and keeps the copy window that the change removes, so it is rejected.

### Reserved name

The name `.ingot-*.part` is reserved, and the rule is enforced in both directions: such files are hidden, and an upload to
such a name is refused with `400`. Hiding without refusing would let a client store a file that vanishes from listings, so
the two rules come together. Maven, Gradle and npm never send a name with a leading dot for an artifact.

### Stale threshold of 24 hours by modification time

An upload that is still running updates the modification time of its temporary file with every chunk. The threshold
therefore measures silence, not age. A 24-hour silence is longer than any request timeout in front of Ingot, so a
running upload is never removed by another replica's start. The threshold is a constant, not a setting, because a new
environment variable would add configuration for no operator need.

### Clock injection

The sweep reads the time through a `java.time.Clock` given to the provider constructor, with the system clock as default
for production. Tests pass a fixed clock and create files with explicit modification times in a temporary directory. No
test waits for time to pass.

### Startup sweep

The sweep runs once when the provider starts. It walks the repository tree, which costs one traversal at boot. Usage is
measured lazily on the first call, so the sweep adds boot time that the lazy path does not have. The cost must be measured
on a tree of staging size before merge. If it is too high, the sweep can run inside the first usage measurement, which
walks the same tree, without changing any other behaviour.

### Atomic move fallback

`Path.moveTo` with overwrite does not ask for `ATOMIC_MOVE`. The commit calls `Files.move` with `ATOMIC_MOVE` and replace.
Only `AtomicMoveNotSupportedException` leads to the plain replacing move, with one warning. The move is behind a small
internal function so that a test can make the atomic move report "not supported" without a special filesystem.

## Known limits

- Writers on different replicas are still not serialised. Two uploads to the same file on two replicas remain last-writer-
  wins, as before. Only readers are protected from partial content.
- Deleting a repository directory while an upload to it is in progress can still make the commit fail. This race exists
  today and is not changed here.
- On Windows, an atomic replace fails while another process has the target open. The current code has the same problem in
  a different form, and no Windows deployment is documented.

## Rollback

The temporary files are in the data tree. An upstream Reposilite started on the same volume after a rollback lists them
until they are committed or removed. Committed artifacts are unaffected, and the container rollback test checks that the
upstream image starts and serves them while a partial file is present.
