# Room schema provenance

Current schema: `com.jumlo.app.data.local.AppDatabase/2.json`, exported by Room
(`room.schemaLocation = $projectDir/schemas`) at schema version 2 with entities
`managed_files`, `download_tasks`, `sources`, `jobs`, `outputs`, `drafts`.

`legacy-v1-structure.sql` is a structural fixture only. It reproduces the
shape of the pre-2 tables (`managed_files`, `download_tasks`) so migration
paths can be exercised in-memory with sqlite3; it is not exported schema
identity and carries no row data.

No `1.json` is committed: inventing a historical export to satisfy an
existence assertion is forbidden. Historical exported identity is validated
by release gates against real user exports, not by fixtures in this tree.

When the schema version bumps, Room re-exports the JSON here; update this
file with the new version and entity list in the same commit.
