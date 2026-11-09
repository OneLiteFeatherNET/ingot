# Design

## Context

The integration suite already has one concrete test class per database and feature area. An abstract specification class holds
the behaviour, and each subclass is annotated with a JUnit extension that sets the database token and the storage settings
before the test runs. MariaDB has its own extension with one database per test; PostgreSQL has one with a filesystem store.

## Decisions

### Matrix as subclasses, not parameterised tests

The matrix is expressed as one concrete subclass per database and feature area, the way the suite already does it, instead of
a `@ParameterizedTest` over the databases. The existing pattern keeps each database in its own class name, so a failure in
the report names the database and the feature area without reading the output. It also keeps the extensions as the only place
that knows about containers. A parameterised test would need a second way to inject the database field, and would make one
database's failure hide the others' results in the same method.

### Storage follows the MariaDB sibling

The new PostgreSQL extension uses the same S3 settings as the MariaDB extension, not the filesystem. The database is then the only
axis that changes between siblings, and a failure can be attributed to the database. The three existing PostgreSQL classes keep
their filesystem storage, because they predate the matrix and their coverage of the filesystem is still wanted. The new S3
subclasses sit beside them.

### Naming and the experimental label stay for now

The new extension is called `PostgresSpecificationJunitExtension`, and its subclasses `Postgres<Name>IntegrationTest`. The
existing `ExperimentalRemote` names stay on the three classes that already had them. Renaming them now would be churn with no
effect on the product, and the label is a product claim. The claim is dropped in a later change, once the matrix has been green on
main, and that change decides the names of the remaining classes.

### Schema per test, not database per test

PostgreSQL has no cheap equivalent of a MariaDB database per test in the existing helpers, but schemas are cheap and isolated
on one server. A schema name with a random suffix, dropped in `afterEach` with `CASCADE`, gives the same independence the MariaDB
extension gets from its database per test. The shared container is started once for the JVM and never stopped by a test.

## Not decided here

- Galera behaviour (certification failure, deadlock 1213) is not part of this matrix. The single standalone MariaDB container in
  CI cannot show it, and the lease changes own those tests.
- Whether H2 and MySQL join the matrix. Neither is supported in the docs, and the decision belongs with the label change.
