# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

yaGen ("yet another Generator") is not a standalone DDL generator — it hooks into Hibernate's
`hbm2ddl`/schema-export machinery and post-processes the DDL Hibernate would otherwise emit, adding
things Hibernate can't: audit/history triggers, cascade-nullable triggers, i18n detail views,
interval partitioning, layered-table views, timeline views, deferrable constraints, etc. It only
does anything when Hibernate is running `org.hibernate.tool.hbm2ddl.SchemaExport` (offline DDL dump)
or actually creating an in-memory/test DB via `hibernate.hbm2ddl.auto`.

It is a multi-module Maven project (the root `build.gradle`/`settings.gradle` are legacy/unused —
always use Maven).

## Build & test commands

Build everything from the repo root:
```
mvn install
```

Run tests for the whole project:
```
mvn test
```

Run a single test class (in `lib/yagen-example-domain`, the module with the actual test suite):
```
mvn -pl lib/yagen-example-domain test -Dtest=HSQLDB_HistoryTest
```

Run a single test method:
```
mvn -pl lib/yagen-example-domain test -Dtest=HSQLDB_HistoryTest#testHistory
```

Notes on the test suite:
- `HSQLDB_HistoryTest` runs against in-memory HSQLDB — fast, no external services, this is the one
  to run by default while iterating.
- `POSTGRESQL_HistoryTest` spins up a real Postgres via `otj-pg-embedded` (`EmbeddedPostgres`) on
  port 9002 — slower, exercises Postgres-specific DDL paths (e.g. deferred-constraint trigger
  functions), only needed when touching Postgres-dialect code.
- Both extend the shared `HistoryTest` (same test methods run against both dialects) which extends
  `TestBase`. `POSTGRESQL_HistoryTest` `@Ignore`s `testHistoryCollectionTableLimitation` since that
  behavior is HSQLDB-specific (see the Javadoc on that test).
- `GeneratedDdlTest` captures the DDL Hibernate prints to stdout during schema creation and asserts
  on the generated trigger/table/procedure text — useful as a template when verifying a DDL-shape
  change.
- Surefire is configured with `forkMode=always` since each test class boots its own
  `EntityManagerFactory`/DB.

Generating a plain DDL script without running tests (see the `ddl-gen` Maven profile, active by
default, in `lib/yagen-example-domain/pom.xml`):
```
mvn -pl lib/yagen-example-domain compile
```
This runs `com.github.gekoh.yagen.ddl.CoreDDLGenerator` via `exec-maven-plugin` and writes
`target/classes/META-INF/schema-objects-generated.ddl.sql`.

History-entity source generation (see `generate-entity-classes` execution in the same pom) runs
`com.github.gekoh.yagen.hst.CreateEntities` in the `generate-sources` phase to produce `*Hst` entity
classes + `example-domain-hst.orm.xml` for any `@TemporalEntity`-annotated class — this is why
`yagen-example-domain` precompiles domain classes in `generate-sources` before the normal compile
phase (it needs them on the classpath already to introspect via reflection).

## Module layout

- `lib/yagen-api` — the public annotation/API surface consumers put on their JPA entities
  (`com.github.gekoh.yagen.api.*`): `@TemporalEntity` (history tracking), `@Auditable`,
  `@CascadeDelete`/`@CascadeNullable`, `@Changelog` (timeline views), `@CheckConstraint`,
  `@Default`, `@Deferrable`, `@Index`, `@UniqueConstraint`, `@Sequence`, `@IntervalPartitioning`,
  `@LayeredTablesView`, `@I18NDetailEntityRelation`, `@NoForeignKeyConstraint`, `@Profile`. Has
  almost no dependencies (slf4j/commons-lang/jakarta-persistence, all optional) since it's meant to
  be on consumers' compile classpath without dragging in Hibernate internals.
- `lib/yagen-generator-lib` — the actual generator. Everything interesting lives under
  `com.github.gekoh.yagen.ddl` (DDL rewriting), `com.github.gekoh.yagen.hibernate` (Hibernate SPI
  integration), `com.github.gekoh.yagen.hst` (history-entity source generation), and
  `com.github.gekoh.yagen.util`.
- `lib/yagen-example-domain` — a sample domain model (Aircraft/BoardBookEntry/PilotLogEntry) used as
  the integration-test fixture and as a worked example of the annotations in `yagen-api`.

## How the integration into Hibernate works

1. Consumers set `hibernate.schema_management_tool` to
   `com.github.gekoh.yagen.hibernate.schema.SchemaManagementToolWrapper`
   (`lib/yagen-generator-lib/.../hibernate/schema/SchemaManagementToolWrapper.java`). This wraps
   Hibernate's own `HibernateSchemaManagementTool` and, before delegating creation/drop/migration/
   validation, calls `DdlPatchHelper.initDialect(metadata)`.
2. `DdlPatchHelper.initDialect` builds/looks up a `DDLGenerator.Profile` (optionally supplied by a
   consumer-provided `ProfileProvider`, configured via the persistence-unit property
   `yagen.generator.profile.providerClass`) and stores it on the active `Dialect`, provided that
   dialect implements `DDLEnhancerAware`. `YagenServiceContributor`/`YagenDialectFactory` are what
   make sure the runtime `Dialect` instance actually implements `DDLEnhancerAware` (wrapping/
   extending the real dialect).
3. As Hibernate emits `CREATE TABLE`/`CREATE INDEX`/`CREATE SEQUENCE`/constraint DDL, the exporter
   wrappers in `com.github.gekoh.yagen.hibernate.exporter` intercept each generated SQL string and
   pass it through `DdlPatchHelper.after*SqlString(...)`, which hands it to `CreateDDL` (the actual
   DDL rewriter/enhancer, `lib/yagen-generator-lib/.../ddl/CreateDDL.java`) to regex-parse and
   rewrite/augment it (e.g. append triggers, history tables, i18n views) before it's executed/
   written out.
4. `CreateDDL` renders SQL/PLpgSQL fragments from Velocity templates
   (`lib/yagen-generator-lib/src/main/resources/com/github/gekoh/yagen/ddl/*.vm.*`, with dialect-
   specific overrides under `hsqldb/` and `postgres/` subdirectories) and tracks generated objects
   by `ObjectType` (table/trigger/index/sequence/etc.) inside the `Profile`.
5. Runtime trigger bypass (used heavily by tests) is enabled via the persistence-unit property
   `yagen.generator.bypass.implement=true`, then controlled at runtime with
   `com.github.gekoh.yagen.util.DBHelper.setBypass(regex)` or the `yagen.bypass` system property
   (HSQLDB only).

`com.github.gekoh.yagen.ddl.CoreDDLGenerator` is the entry point for offline DDL dumping (no live
DB needed beyond a working JPA/Hibernate classpath) — it builds Hibernate `Metadata` from a named
persistence unit and calls `DDLGenerator.writeDDL`.

## Working with generated/templated DDL

- SQL/PL templates live in `lib/yagen-generator-lib/src/main/resources/com/github/gekoh/yagen/ddl/`
  and are Velocity (`.vm`) files — dialect-specific variants override the default by living in a
  `hsqldb/` or `postgres/` sibling directory of the same filename.
- `CreateDDL` parses raw `CREATE TABLE`/column/constraint/index SQL text with regexes (see the
  `*_PATTERN` constants near the top of the class) rather than working purely off Hibernate's
  `Metadata` model — when a generated DDL shape doesn't match what a new test expects, check these
  patterns before assuming the template is wrong.
- When adding or changing DDL shape, `GeneratedDdlTest`
  (`lib/yagen-example-domain/src/test/java/.../test/GeneratedDdlTest.java`) shows the pattern for
  asserting against both the captured stdout DDL dump and the per-object DDL map
  (`TestBase.ddlMap`, keyed by `ObjectType` then object name) that `ExampleProfileProvider.Profile`
  records during generation.
