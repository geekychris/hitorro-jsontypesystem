# A distributed document management system on JVS + KV + Lucene

**Status:** design proposal — no code yet
**Author:** design conversation, 2026-08-30

## Motivation

Hitorro already has a document management system: `hitorro-basedms`. It's
Hibernate-backed, RDBMS-persisted, and models everything a mature DMS
needs — versioning DAGs, containers/folders, inter-document references,
ACLs, multi-part content, mime detection, indexing hooks. It works.

What it doesn't do well:
1. **Scale horizontally.** All writes funnel through one RDBMS. The
   `Container.contained` set already had to be lazy-loaded to survive
   100K-item folders.
2. **Compose with the mesh.** The rest of hitorro (JVS types, KV
   stores, Lucene indexes, mesh pipelines, streaming SQL) is already
   distributed-first. basedms is the outlier.
3. **Avoid schema migrations.** Every new document facet is a Hibernate
   entity change → DDL → Flyway → coordinated deploy.

The question is whether we can build the same *semantics* — versioning,
references, folders, ACLs, search, retrieval — on the primitives we
already ship:

- **JVS type system** for the document schema (evolvable, self-describing)
- **KV store** (RocksDB) as the authoritative store, keyed by document id
- **Lucene index** as the query layer, kept up-to-date via mesh pipelines
- **Mesh partitioning** for horizontal distribution
- **MinIO / S3** (optional) for large content blobs
- **MongoDB** (optional) as a third rendering if JSON-native queries earn it

## Non-goals

- **Cross-document ACID transactions.** basedms gets these from
  Postgres; we won't. Design decisions below explicitly favour
  "make each single-doc op atomic; don't pretend inter-doc ops are."
- **Feature parity with basedms on day one.** Phased delivery. Some
  basedms features (e.g. Lucene search — already there;
  event notifications — already in hitorro-util) plug in cleanly;
  others (per-permission ACL evaluation with role inheritance) are
  full sub-projects.
- **Migration tooling from basedms.** Separate design problem. This
  doc assumes a greenfield deployment.

## Concepts

| Concept | What it is |
|---|---|
| **Document** | The logical entity a user cares about — a wiki page, a spec, an email, a photo record. Identified by a stable **canonical id** (UUID) that never changes across the doc's lifetime. |
| **Version** | An immutable snapshot of a document at one point in time. Every version has its own version id + a `version_label` ("1.0", "1.1", "2.0-alpha") and points at its predecessor. Versions of the same document share a canonical id. |
| **Head** | The current "live" version of a document. Not always the highest-numbered version (branches can diverge). Stored under `d:{canonical_id}` as a pointer. |
| **Reference** | A directed edge from one document (at a specific version, or at "current") to another, with a type ("supersedes", "cites", "attached-to", …) and optional aux data. |
| **Folder** | A named container that lists documents by canonical id. Folders can nest. Modelled as first-class documents themselves — a folder IS a document with a specific type. |
| **ACL** | Access-control entries scoped to a document + a principal (user/group/role) + a permission (read/write/delete/…). Application-evaluated on read/write. |
| **Content ref** | Pointer to a binary blob (in the KV store for small content, MinIO/S3 for large). One document can have multiple content refs (original, thumbnail, extracted-text, …). |

## Type definitions

Live under `config/types/`. Files below are the proposed shape; every
field is optional at the JVS level (the DMS service enforces semantics).

### `core_document.json` — the base

```json
{
  "name": "document",
  "extends": "sysobject",
  "fields": [
    {"name": "canonical_id",   "type": "core_id",     "doc": "Stable across all versions of this document."},
    {"name": "version_label",  "type": "core_string", "doc": "Human tag: '1.0', '1.1', '2.0-alpha'."},
    {"name": "version_major",  "type": "core_long"},
    {"name": "version_minor",  "type": "core_long"},
    {"name": "version_kind",   "type": "core_string", "doc": "'major', 'minor', 'branch', 'draft'."},
    {"name": "parent_version", "type": "core_id",     "doc": "Direct predecessor version id (null for v1)."},
    {"name": "branch_of",      "type": "core_id",     "doc": "Non-null when this is the tip of a branch."},

    {"name": "references",     "type": "array<core_reference>"},
    {"name": "parent_folder",  "type": "core_id",     "doc": "Canonical id of the containing folder; null = root."},
    {"name": "acls",           "type": "array<core_acl>"},
    {"name": "content_refs",   "type": "array<core_content_ref>"},

    {"name": "created_by",     "type": "core_string"},
    {"name": "modified_by",    "type": "core_string"},
    {"name": "content_type",   "type": "core_string", "doc": "'folder', 'wiki-page', 'photo', …"},

    {"name": "checked_out_by", "type": "core_string", "doc": "Non-null while a check-out is active."},
    {"name": "checked_out_at", "type": "core_date"},

    {"name": "tombstoned",     "type": "core_boolean","doc": "Soft-delete marker."}
  ]
}
```

Inherits `id`, `times`, `title`, `body`, `description` from
`core_sysobject`. `id` here is the *version* id; `canonical_id` is the
*document* id.

### `core_reference.json`

```json
{
  "name": "reference",
  "fields": [
    {"name": "to_canonical", "type": "core_id",     "doc": "Target document (always to canonical, not a specific version)."},
    {"name": "to_version",   "type": "core_id",     "doc": "Optional: pin to a specific target version."},
    {"name": "kind",         "type": "core_string", "doc": "'supersedes' / 'cites' / 'attached-to' / …"},
    {"name": "aux",          "type": "core_string", "doc": "Free-form JSON, per-kind."},
    {"name": "created_at",   "type": "core_date"}
  ]
}
```

### `core_acl.json`

```json
{
  "name": "acl",
  "fields": [
    {"name": "principal",    "type": "core_string", "doc": "'user:alice', 'group:eng', 'role:reviewer', 'public'."},
    {"name": "permission",   "type": "core_string", "doc": "'read', 'write', 'delete', 'share', 'admin'."},
    {"name": "grant",        "type": "core_boolean","doc": "true = allow, false = deny."},
    {"name": "inherit_from", "type": "core_id",     "doc": "Optional: canonical id of ACL source (parent folder)."}
  ]
}
```

### `core_content_ref.json`

```json
{
  "name": "content_ref",
  "fields": [
    {"name": "role",       "type": "core_string", "doc": "'primary' / 'thumbnail' / 'text-extract' / 'preview' / …"},
    {"name": "mime",       "type": "core_string"},
    {"name": "size_bytes", "type": "core_long"},
    {"name": "hash_sha256","type": "core_string"},
    {"name": "url",        "type": "core_url",    "doc": "'kv://{store}/{key}', 's3://…', 'minio://…', 'file://…'."},
    {"name": "inline",     "type": "core_string", "doc": "Base64 blob if size < ~4 KB — cheaper than a KV round-trip."}
  ]
}
```

## Storage layout

The **KV store is authoritative**. Every doc, every version, every
reference, every folder membership entry lives in RocksDB. Lucene is
a derived index — losing Lucene is "rebuild from KV," not "data loss."

### Key encoding

Prefix-namespaced ASCII, `|` as delimiter:

| Key pattern | Value | Purpose |
|---|---|---|
| `d\|{canonical_id}` | version id | Current head version pointer |
| `v\|{canonical_id}\|{version_label}` | JVS doc bytes | The version itself |
| `v\|{canonical_id}\|` (prefix) | — | Prefix scan → all versions of a doc |
| `l\|{canonical_id}\|{version_id}` | version_label | Reverse lookup (version_id → label) |
| `r\|{from_canonical}\|{kind}\|{to_canonical}` | reference metadata | Outbound references from `from` |
| `br\|{to_canonical}\|{kind}\|{from_canonical}` | reference metadata | Inbound (back-references) — dual write |
| `f\|{folder_canonical}\|{child_canonical}` | child metadata (title, kind, mtime) | Folder membership |
| `co\|{canonical_id}` | check-out lease | Distributed check-out lock (see below) |
| `t\|{canonical_id}` | tombstone marker | Soft-delete indicator |

**Prefix scans** cover the common list operations:
- `v|{canonical}|` — version history of one doc
- `r|{canonical}|` — all outbound refs
- `br|{canonical}|` — all inbound refs
- `f|{folder}|` — folder contents

**Composite key discipline** is the DMS service's job — `KVStore` itself
doesn't know about secondary indexes.

### Lucene fields

The mesh pipeline writes every version to a Lucene index with these
fields (via `JvsLuceneSink` + a JVS type json):

| Field | Method | Purpose |
|---|---|---|
| `canonical_id` | identifier | Exact-match filter |
| `version_id` | identifier | Fetch a specific version |
| `version_label` | identifier | Filter by "1.0", "2.0", … |
| `is_head` | boolean | `WHERE is_head = true` for "current only" |
| `title.mls.text_en_m` | fulltext (per language) | Full-text search |
| `body.mls.text_en_m` | fulltext | Same |
| `content_type` | identifier | Facet by kind |
| `parent_folder` | identifier | Facet by folder |
| `created_by`, `modified_by` | identifier | Author filters |
| `times.created_s`, `times.modified_s` | date_s | Range filters |
| `refs_to_kind_{kind}` | identifier (multi-valued) | "all docs that cite X" |
| `_source` | stored JSON | Reconstruct doc without KV round-trip |

## Write paths

### Check-in (new version of an existing doc)

```
client → POST /dms/documents/{canonical}/versions   { body … }
   │
   ▼
DMS service
   1. Load current head from KV: get d|{canonical}
   2. Compute next version_label per policy (major/minor/draft)
   3. Build new version JVS doc
   4. Emit mesh job (or direct write) — one write pipeline:
        node: persist
          source: inline (the one new version)
          steps:  [set-field is_head=true, set-field is_head=false-for-old-head-in-a-follow-up-batch]
          sinks:
            - kvstore  (writes v|canonical|label + updates d|canonical + writes back-refs)
            - jvs-lucene  (indexes the new version)
   5. Return { canonical, version_id, version_label }
```

Consistency: the **KV write and the Lucene write happen inside the same
mesh pipeline node**. Failure of the Lucene sink surfaces as a job
failure — the client sees "check-in failed" and retries. On success,
both are durable. Cross-partition writes (updating references TO other
docs whose back-ref key lives on a different agent) go into a **second
batch** flushed after the primary check-in commits — designed to be
retryable + idempotent, so a crash between the two leaves the primary
consistent and back-refs converge on next retry.

### Add a reference

Single-doc op — always same-partition:
```
put r|A|cites|B → {aux: "sect 3.2", created_at: now}
put br|B|cites|A → {aux: "sect 3.2", created_at: now}   ← different partition if A + B on different agents
```

The `br|` write is fire-and-forget with retry. Missing back-refs are
detected on Lucene indexing (which is broadcast) — the indexer sees
"here's A citing B" and can synthesise the back-ref lazily.

### Delete (soft)

`put t|{canonical} → 1`. All read paths check the tombstone; hard
delete is a background sweep after N days.

## Read paths

### Get current version
```
GET /dms/documents/{canonical}
  → get d|{canonical} → version_label
  → get v|{canonical}|{label}
  → deserialize JVS doc
```
Fast: two KV round-trips on the owning agent (routed by `canonical_id`).

### Get a specific version
```
GET /dms/documents/{canonical}/versions/{label}
  → get v|{canonical}|{label}
```
One round-trip.

### List versions
```
GET /dms/documents/{canonical}/versions
  → scanByPrefix v|{canonical}|
```

### Search
```
POST /mesh/queries
  {"sql": "SELECT canonical_id, title FROM documents WHERE MATCH('body', 'kubernetes') AND is_head=true LIMIT 20"}
```
Runs against the Lucene-backed table registered from the DMS index.
Streaming variant via `/mesh/queries/stream?sql=…` for live search
(see [`streaming-sql.md`](../../hitorro-mesh-pipelines/docs/streaming-sql.md)).

### Follow a reference
```
GET /dms/documents/{canonical}/references
  → scanByPrefix r|{canonical}|
  → for each row, resolve to_canonical (may be on a different agent)
```

### Browse folder
```
GET /dms/folders/{folder_canonical}/contents
  → scanByPrefix f|{folder}|
```

## Versioning DAG

Simple lineage, not full git:

```
v1 ──► v1.1 ──► v1.2 ──► v2 (head)
                  │
                  └──► v1.2-hotfix ──► v1.3 (branch head)
```

- **Major bump** = new `version_major`, `version_minor=0`, `parent_version` = previous head
- **Minor bump** = `version_minor++`, same `version_major`
- **Branch** = new version with `branch_of` = the version being branched from
- **Head pointer** always points at one version per branch; the DMS
  service (not the storage layer) picks which branch is "the" head

Merging branches is out of scope for phase 1 (basedms doesn't do it
either).

## ACL model

Application-evaluated on every read + write. Two-pass:

1. **Explicit** — check `document.acls` for a matching principal + permission
2. **Inherited** — if no explicit match and `parent_folder` set, load
   parent's ACLs and recurse

Cache principal→doc→permission resolutions in a KV entry keyed by
`ac|{canonical}|{principal_hash}` with a short TTL for hot paths.

Group/role expansion (which users are in group X?) comes from a
separate identity system (LDAP, IdP, whatever) — outside this doc.

## Distribution

Partition key = `canonical_id` in every KV table registration:

```yaml
sinks:
  - kind: kvstore
    name: dms-versions
    keyExpr: canonical_id
    partitionKey: canonical_id     # mesh hash-routes by this
```

Every version of the same document lives on the same agent. The `d|`
head pointer + `v|` versions + `co|` check-out lease + `r|` outbound
refs all live together — most single-doc ops are local.

Back-refs (`br|`) live with the *target* — an inbound-ref lookup on
doc B is local, an outbound write from A to B might cross agents (see
consistency notes above).

Folder membership (`f|`) lives with the folder — folder browse is
local to the folder's owning agent.

Small tables broadcast to every agent for zero-hop reads:
- ACL principals + group memberships
- Folder tree metadata (name + parent → for path resolution)

## Consistency

- **Same-partition writes** are ACID within one KV `batchPut(txn=true)` call — head update + new version + outbound refs all commit atomically.
- **Cross-partition writes** (back-refs, folder membership when folder is on a different agent) are eventually consistent — a retryable follow-up batch, idempotent on the target.
- **Reader consistency** is read-your-writes on the owning agent; cross-agent reads (follow a ref to a doc on a different agent) see whatever is committed there at read time.
- **Rebuild-from-KV** works because the KV `_taskseq` field + Lucene's `updateDocument(Term, doc)` makes reindexing idempotent — losing the Lucene index doesn't lose data.

The tradeoff vs basedms: no "move doc + update parent folder + record audit event" as one transaction. You get single-doc atomicity and eventual consistency for cross-doc; if that's not enough for a specific op, that op has to be idempotent with a compensating undo.

## Content blobs

Small content (≤ 4 KB) inlines directly in the JVS doc via
`core_content_ref.inline`. Above that, the blob lives elsewhere and
the ref points to it:

- `kv://dms-blobs/{sha256}` — dedup by content hash; same file uploaded twice = one KV entry, two refs
- `minio://bucket/{sha256}` — for anything > ~1 MB
- `s3://bucket/{sha256}` — same for cloud

Content is immutable once stored — a new version pointing at new bytes
gets a new `content_refs` entry, not an in-place update. This makes
GC easy (unref-and-sweep) and rebuild trivial (blob store is
authoritative for bytes; KV+Lucene point at it).

## Rebuild-from-KV

Any Lucene index is a materialized view over the KV store. If it's
corrupted, out of sync, or you want a new field:

```yaml
job: rebuild-dms-index
nodes:
  - id: scan
    pipeline:
      source: { kind: kvstore, name: dms-versions, prefix: "v|" }
      sinks:
        - kind: jvs-lucene
          name: documents-idx
          typeJsonResource: config/types/document.json
          storeSource: true
```

Runs across every agent (each scans its own partition). `updateDocument(Term, doc)` in the Lucene sink is idempotent, so re-runs are safe.

## Comparison vs basedms

| Dimension | basedms | Proposed |
|---|---|---|
| Persistence | Hibernate + Postgres | KV (RocksDB) + Lucene, both mesh-distributed |
| Schema evolution | Entity + Flyway migration | Add field to JVS type json, deploy — old versions still deserialize |
| Cross-doc transactions | ACID via Postgres | Single-doc atomic; cross-doc eventually consistent + idempotent |
| Horizontal scale | Vertical (bigger DB) | Add mesh agents |
| Search | Lucene + explicit indexing hook | Lucene as first-class read path, auto-populated via pipeline |
| Versioning | canonical/next/branch pointers | Same — lifted directly |
| Folders | Container/Folder entity hierarchy | First-class documents with `content_type: "folder"` + `f|` membership keys |
| References | Reference entity | `r|` / `br|` KV keys |
| ACLs | Permission entity + Hibernate joins | JVS field on document + inheritance walk |
| Content | Content + ContentType entities | `core_content_ref` array pointing at KV or blob store |
| Auditing | Hibernate interceptor / event listener | Mesh pipeline writes to an append-only NDJSON audit sink |
| Failure recovery | pg_restore | Rebuild Lucene from KV; KV replication via WAL |

## Open questions

1. **How much of basedms's semantic surface do we implement in phase 1?** Proposal: versioning DAG + references + folders + read/write ACLs. Skip: nested content, mime introspection (delegate to a separate pipeline), rich queries (use mesh SQL directly).
2. **Where does the DMS service live?** New module `hitorro-fleet-dms` (Spring Boot app under `hitorro-fleet-retrieval` model) exposing REST + a Java client? Or extend `hitorro-mesh-driver-app` with a `/dms/*` controller? Leaning toward standalone fleet member — separation of concerns.
3. **Do we want MongoDB in the picture?** Not for correctness. Possibly as a JSON-native query layer alongside Lucene when the query pattern is "find all docs with `content_refs[].mime = 'video/*' AND size_bytes > 100MB'`" — Lucene handles this but Mongo's dot-notation feels more natural. Defer.
4. **Migration from basedms?** Almost certainly a separate one-shot pipeline: `basedms Postgres → JVS docs → mesh pipeline → KV+Lucene`. Not part of the DMS design.
5. **Check-out semantics.** Optimistic vs pessimistic? basedms is pessimistic (`checked_out_by`). Proposed: keep pessimistic; represent as a lease in `co|{canonical}` with a TTL — if the client dies mid-edit, the lease expires and the doc unlocks automatically. Cleaner than basedms's "admin has to manually clear the lock."
6. **Referential integrity.** basedms doesn't enforce it either (references can be dangling). Proposed: same — a periodic "walk r|/br| and check the targets exist" job flags broken refs, doesn't repair.

## Roadmap

### Phase 1 — minimum viable (2-3 weeks)
- `core_document`, `core_reference`, `core_content_ref` types + JVS schemas
- New module `hitorro-dms` (Spring-agnostic core) with:
  - `KvDocumentStore` — single-agent CRUD, versioning, references
  - `LuceneIndexWriter` — dual-write via mesh pipeline
  - `DocumentService` — orchestration
- Integration tests hitting real RocksDB + Lucene
- No REST, no distribution — just the storage semantics

### Phase 2 — Spring Boot service (2 weeks)
- `hitorro-fleet-dms` module (Spring Boot app on the fleet model)
- REST surface: `POST /dms/documents`, `GET /dms/documents/{id}`, versions, refs, search
- Runs standalone (single agent) — proves the API before we distribute

### Phase 3 — distribution (2-3 weeks)
- KV table registration with `partitionKey: canonical_id`
- Cross-partition back-refs and their retry queue
- Broadcast tables for ACL principals + folder tree
- Multi-agent smoke test in `hitorro-mesh-examples`

### Phase 4 — ACLs + folders (2-3 weeks)
- `core_acl` type + inheritance walk
- Folders as documents with `content_type: "folder"` + folder-membership index
- Move/copy operations (single-doc atomic; folder-membership update is the follow-up batch)

### Phase 5 — content blobs (1-2 weeks)
- `core_content_ref` fully supported: KV, MinIO, S3, inline
- Content-dedup by sha256
- GC pipeline (unref-and-sweep)

### Phase 6 — audit + notifications (1 week)
- Append-only audit sink (NDJSON)
- Notification hooks (`hitorro-util.integrationevents`) for "doc created/updated/deleted"

### Phase 7 — check-out leases (1 week)
- TTL-based lease keys in `co|`
- Client heartbeat to renew
- Admin API to force-unlock

Each phase is independently useful; no phase blocks a later one from
being reshuffled. Total ~13-17 weeks of engineering to reach basedms
feature parity for the ops that matter.

## Reference

- [`../../hitorro-mesh-pipelines/docs/authoring-a-pipeline.md`](../../hitorro-mesh-pipelines/docs/authoring-a-pipeline.md) — the write path is a pipeline
- [`../../hitorro-mesh-pipelines/docs/streaming-sql.md`](../../hitorro-mesh-pipelines/docs/streaming-sql.md) — read path for search
- basedms source: `/Users/chris/hitorro/hitorro-basedms/src/main/java/com/hitorro/base/objects/` — the semantic model being ported
- `hitorro-jsontypesystem/src/main/resources/config/types/` — where the new type files land
- `hitorro-kvstore` — the storage primitives
- `hitorro-index` + `hitorro-mesh-pipelines-lucene` — the index primitives
