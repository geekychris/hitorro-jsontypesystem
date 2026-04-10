# hitorro-jsontypesystem

A metadata-driven JSON type system that gives structure, multi-lingual text, NLP enrichment, and projection-based transformations to JSON documents. Types are defined as JSON configuration — not Java classes — making the system fully extensible at runtime without recompilation.

This module can be used independently from the rest of HiTorro. It depends only on `hitorro-core` for foundation utilities (Propaccess path navigation, JSON helpers, file I/O).

## Core Concepts

### The Type System

Every JSON document in HiTorro is governed by a **type definition** — a JSON configuration file that declares the document's fields, their data types, whether they are multi-valued or internationalized, and how they should be processed. Types support single inheritance, so you can build a hierarchy of increasingly specialized document structures.

Type definitions live in `config/types/*.json`:

```json
{
    "name": "person",
    "description": "Person profile with contact info and biography",
    "super": "sysobject",
    "fields": [
        {
            "name": "first_name",
            "type": "core_string"
        },
        {
            "name": "email",
            "type": "core_string",
            "vector": true
        },
        {
            "name": "biography",
            "type": "core_mls"
        },
        {
            "name": "full_name",
            "type": "core_string",
            "dynamic": {
                "class": "multivalue-merger",
                "fields": [".first_name", ".last_name"]
            }
        }
    ]
}
```

**Field properties:**

| Property | Description |
|----------|-------------|
| `type` | Reference to another type. Primitives: `core_string`, `core_long`, `core_date`, `core_boolean`. Composites: `core_id`, `core_mls`, `core_url`, or any user-defined type. |
| `vector` | When `true`, the field holds an array of values instead of a single value. |
| `i18n` | When `true`, marks the field as language-specific (used by the MLS pattern). |
| `dynamic` | Declares a computed field — its value is derived from other fields at access time using a mapper class. |
| `groups` | Assigns the field to one or more projection groups (`index`, `enrich`, `remove`) with a processing method and optional tags. |

**Inheritance:** A type with `"super": "sysobject"` inherits all fields from `sysobject`. Child fields with the same name override the parent's definition.

### Multi-Lingual Strings (MLS)

Multi-lingual text is a first-class concept. The `core_mls` type wraps an array of `core_mlselem` entries, each carrying a language code and the text in that language:

```json
{
    "title": {
        "mls": [
            {"lang": "en", "text": "Annual Report"},
            {"lang": "de", "text": "Jahresbericht"},
            {"lang": "fr", "text": "Rapport annuel"}
        ]
    }
}
```

The `mlselem` type is where language-aware NLP processing happens. Each element can carry not just raw text, but a full chain of derived fields — HTML-scrubbed text, POS tags, sentence segments, named entities, and more — all computed on demand through dynamic field mappers. An `IsoLanguageSeeker` allows path-based access by language code (e.g., `title.mls[en].text`) rather than numeric index.

### Dynamic Fields and NLP Enrichment

Dynamic fields are the extensibility mechanism of the type system. A field marked with a `dynamic` block declares a mapper class and the source fields it depends on. The value is computed lazily when accessed through the type-aware property context (`PAContextTyped`).

The `core_mlselem` type definition shows this in action — starting from raw `text`, a pipeline of dynamic fields produces progressively richer annotations:

```
text (raw input)
  └─ clean           (html-scrubber)         strips HTML tags
      ├─ pos          (pos-tokenizer)         part-of-speech tags
      ├─ clean_normhash (normalized-text-hash) deduplication hash
      └─ segmented_span (sentence-segmenter-span)  sentence boundaries
          └─ segmented   (sentence-segmenter)       sentence text
              ├─ segmented_parsed  (chunk-mapper)    syntactic chunks
              ├─ segmented_answers (text-classifier) answer type classification
              ├─ segmented_ner     (ner-markup)      named entity recognition
              └─ segmented_normhash (normalized-text-hash)
```

Each mapper in this chain reads from its declared source fields and produces its output. Because computation is lazy, only the fields actually accessed are computed.

**Built-in dynamic field mappers:**

| Mapper | Purpose |
|--------|---------|
| `multivalue-merger` | Concatenate multiple fields with a separator |
| `dynamic-mapper` | Apply a nested mapper (e.g., `html-scrubber`, `normalized-text-hash`, `fp-hash`) |
| `sentence-segmenter-span` | Detect sentence boundaries (character offsets) via OpenNLP |
| `sentence-segmenter` | Extract sentence text from spans |
| `pos-tokenizer` | Part-of-speech tagging via OpenNLP |
| `chunk-mapper` | Syntactic chunking (noun/verb phrases) |
| `text-classifier` | Answer-type classification via MaxEnt |
| `ner-markup` | Named entity recognition (person, location, organization) |

#### Extending with Custom Mappers

To add a new dynamic field mapper:

1. Extend `DynamicFieldMapper`:

```java
public class MyMapper extends DynamicFieldMapper {
    @Override
    public JsonNode map(JVS jvs, Propaccess pa, int depth) {
        JsonNode[] values = getValues(jvs, pa, depth);
        // transform values[0], values[1], etc.
        return result;
    }
}
```

2. Reference it in a type definition:

```json
{
    "name": "sentiment_score",
    "type": "core_string",
    "dynamic": {
        "class": "com.mycompany.MyMapper",
        "fields": [".lang", ".clean"]
    },
    "groups": [
        {"name": "enrich", "method": "text", "tags": ["sentiment"]}
    ]
}
```

The mapper will be instantiated and called whenever the field is accessed on a typed document.

## The JVS Document Wrapper

`JVS` is the universal document wrapper — a typed `JsonNode` with dot-notation path access powered by Propaccess.

```java
// Create from JSON
JVS doc = JVS.read("""
    {
        "id": {"domain": "blog", "did": "article-001"},
        "title": {"mls": [{"lang": "en", "text": "Hello World"}]},
        "tags": ["java", "nlp"]
    }
    """);

// Path-based access (dot notation, array indexing)
String did = doc.getString("id.did");                    // "article-001"
String title = doc.getString("title.mls[0].text");       // "Hello World"
List<String> tags = doc.getStringList("tags");            // ["java", "nlp"]

// Set nested values (intermediate nodes created automatically)
JVS doc = new JVS();
doc.set("id.did", "doc-002");
doc.set("metadata.version", 1);
doc.set("metadata.active", true);

// Multi-lingual text
doc.addLangText(JVS.titleKey, "Hello World", "en");
doc.addLangText(JVS.titleKey, "Hallo Welt", "de");

// Create a typed document — enables dynamic field computation
Type personType = JsonTypeSystem.getMe().getType("demo_person");
JVS person = new JVS(personType);
person.set("first_name", "Chris");
person.set("last_name", "Collins");
// Accessing "full_name" now triggers the multivalue-merger dynamic mapper

// Clone and merge
JVS clone = doc.clone();
doc.merge(overlay);  // overlay values win on conflict
```

**Well-known path constants** on `JVS`:

| Constant | Path | Description |
|----------|------|-------------|
| `typeKey` | `type` | Document type name |
| `idKey` | `id.id` | Computed identifier |
| `didKey` | `id.did` | Document ID |
| `domainKey` | `id.domain` | Domain/namespace |
| `titleKey` | `title.mls` | Title MLS array |
| `bodyKey` | `body.mls` | Body MLS array |
| `createdKey` | `times.created` | Creation timestamp |
| `modifiedKey` | `times.modified` | Modification timestamp |

## The Projection Framework

Projections are type-driven transformations that process a JVS document's fields based on their **group** assignments. Each field in a type definition can belong to one or more groups, and each group declares a processing method and optional tags.

```json
{
    "name": "clean",
    "type": "core_string",
    "dynamic": { "class": "dynamic-mapper", "mapper": {"class": "html-scrubber"}, "fields": [".text"] },
    "groups": [
        {"name": "index", "method": "text", "tags": ["basic"]}
    ]
}
```

### Three Projection Phases

| Phase | Action | Purpose |
|-------|--------|---------|
| **Index** | `IndexerAction` | Extracts field values from a JVS document into a flat target structure suitable for search indexing. Applies field-type method mapping (e.g., `text`, `identifier`, `long`). |
| **Enrich** | `EnrichAction` | Touches field paths to trigger lazy computation of dynamic fields. After enrichment, NLP-derived fields like POS tags, sentence segments, and NER markup are materialized in the document. |
| **Remove** | `RemoveAction` | Strips fields from a document — used to remove internal or sensitive data before export. |

### How Projections Work

The framework uses a visitor pattern to build an **execution tree** from a type definition. The `ExecutionBuilder` walks the type hierarchy, and for each field whose group matches the requested phase and tags, it creates an action node. The tree is built once, cached, and reused.

```java
// Build and execute an index projection
Type articleType = JsonTypeSystem.getMe().getType("demo_article");
ExecutionBuilder<IndexerAction> builder =
    new IndexExecutionBuilderMapper().apply(articleType);

ProjectionContext pc = new ProjectionContext();
pc.source = sourceDoc;
pc.target = new JVS();
builder.getCurrentNode().project(pc);
// pc.target now contains the indexed field representation

// Enrich — materialize dynamic fields
ExecutionBuilder<EnrichAction> enrichBuilder =
    new EnrichExecutionBuilderMapper().apply(articleType);
ProjectionContext epc = new ProjectionContext();
epc.source = myDoc;
epc.target = new JVS();
enrichBuilder.getCurrentNode().project(epc);
// myDoc now has NLP fields computed (POS, sentences, NER, etc.)

// Debugging with execution tracing
ExecutionTrace trace = new ExecutionTrace();
pc.trace = trace;
builder.getCurrentNode().project(pc);
System.out.println(trace.summary());
```

Tag-based filtering lets you control which fields are included. Groups with a `tags` array (e.g., `["basic"]`, `["pos"]`, `["ner"]`) are only processed when those tags are requested. This allows selective enrichment — compute only sentence segmentation, or only NER, rather than the full NLP pipeline.

### Adding Custom Projection Phases

The factory pattern makes it straightforward to add new projection types:

1. Implement `ExecutorAction` — define what happens at each field
2. Implement `ExecutorFactory` — create action instances
3. Extend `BaseProjectionFactoryMapper` — wire the factory to the type visitor
4. Register the new mapper in `ExecutionBuilderCacheMapper`

## Groovy Data Mapping and Enrichment

The Groovy DSL provides a scripting layer for transforming JVS documents. Transform scripts live in `config/transforms/*.groovy` and have access to a rich set of DSL operations.

```java
// Load and execute a transform
GroovyTransformMapper mapper = GroovyTransformMapper.fromFile(
    new File("config/transforms/enrich_person.groovy"),
    new File("config/generators")
);
JVS result = mapper.apply(inputJVS);
```

### DSL Operations

**Document access and copying:**
```groovy
copyAll()                                    // deep-copy source → target
copy "source.author" to "target.writer"      // selective field copy
set "target.status", "published"             // set a value
delete "target.internal_notes"               // remove a field
```

**Multi-lingual text:**
```groovy
mls "target.title", text: "Hello", lang: "en"
mlsAppend "target.title", text: "Hallo", lang: "de"
```

**Arrays:**
```groovy
append "target.tags", "java"
```

**Conditionals and loops:**
```groovy
when(source("type") == "article") {
    copy "source.headline" to "target.title"
}

loop("source.tags[]") { tag ->
    append "target.categories", tag
}

times(5) { i ->
    append "target.items", gen.uuid()
}
```

**Data generators** — produce realistic synthetic data:
```groovy
set "target.id.did", gen.uuid()
set "target.first_name", gen.firstName()
set "target.email", gen.email()
set "target.company", gen.company()
set "target.created", gen.timestamp()
```

**Custom generators** can be defined inline:
```groovy
generator "age", type: "int", min: 18, max: 75
generator "emp_id", type: "sequence", prefix: "EMP-"
generator "dept", type: "pick", values: ["Engineering", "Sales", "HR"]
generator "dob", type: "date", from: "1955-01-01", to: "2005-12-31"
generator "sku", type: "pattern", pattern: "SKU-####-??"
generator "byline", type: "template", template: "{first_names} {last_names}, {company_names}"

set "target.age", gen.next("age")
set "target.employee_id", gen.next("emp_id")
```

Generator types: `int`, `long`, `double`, `bool`, `uuid`, `date`, `timestamp`, `sequence`, `pattern`, `pick`, `items`, `constant`, `template`, and CSV file-based generators loaded from `config/generators/`.

**Type system enrichment** — trigger dynamic field computation from a script:
```groovy
enrich("segmented", "pos")    // compute specific tagged enrichments
def value = dynamic("path")   // read a dynamically computed field
```

**AI/LLM operations** — when an AI service is configured:
```groovy
when(aiAvailable()) {
    translate "source.title.mls[0].text", from: "en", to: "de", into: "target.title.mls"
    translateMls "target.title", from: "en", to: ["de", "fr", "ja"]
    summarize "source.body.mls[0].text", maxWords: 30, into: "target.summary"
    ask "source.body.mls[0].text",
        question: "What is the main topic?",
        into: "target.topic"
}
```

**Script libraries:**
```groovy
def lib = load("common")    // loads config/transforms/lib/common.groovy
set "target.slug", lib.slugify("Hello World")
```

### Full Transform Example

```groovy
// enrich_person.groovy
generator "age", type: "int", min: 18, max: 75
generator "emp_id", type: "sequence", prefix: "EMP-"
generator "dept", type: "pick", values: ["Engineering", "Product", "Sales", "Marketing"]

def titleCase = { String text ->
    text.split(/\s+/).collect { it.capitalize() }.join(' ')
}

copyAll()

def first = gen.firstName()
def last = gen.lastName()

set "target.type", "person"
set "target.id.did", gen.uuid()
set "target.first_name", first
set "target.last_name", last

mls "target.title", text: titleCase("${first} ${last}"), lang: "en"
mls "target.body", text: gen.lorem(), lang: "en"

set "target.email", gen.email()
set "target.department", gen.next("dept")
set "target.employee_id", gen.next("emp_id")

times(gen.intBetween(2, 6)) { i ->
    append "target.skills", gen.pick("Java", "Python", "Go", "Rust", "TypeScript",
            "SQL", "Kubernetes", "Machine Learning", "NLP")
}
```

## Using JVS in Data Processing

A typical data processing pipeline combines these pieces:

1. **Ingest** — parse raw data (JSON, CSV, XML) into JVS documents with a type assignment
2. **Transform** — apply Groovy scripts to reshape, merge, or enrich documents
3. **Enrich** — run the enrich projection to compute NLP-derived fields (POS, sentences, NER)
4. **Index** — run the index projection to extract a flat field map for search indexing
5. **Remove** — strip internal fields before storage or export

```java
// 1. Load typed document
JVS doc = JVS.read(rawJson);
doc.setType("demo_article");

// 2. Transform with Groovy
GroovyTransformMapper transform = GroovyTransformMapper.fromFile(
    new File("config/transforms/article_transform.groovy"),
    new File("config/generators")
);
JVS enriched = transform.apply(doc);

// 3. Enrich — compute NLP fields
ExecutionBuilder<EnrichAction> enrichBuilder =
    new EnrichExecutionBuilderMapper("segmented", "ner").apply(enriched.getType());
ProjectionContext epc = new ProjectionContext();
epc.source = enriched;
epc.target = new JVS();
enrichBuilder.getCurrentNode().project(epc);

// 4. Index — extract fields for search
ExecutionBuilder<IndexerAction> indexBuilder =
    new IndexExecutionBuilderMapper().apply(enriched.getType());
ProjectionContext ipc = new ProjectionContext();
ipc.source = enriched;
ipc.target = new JVS();
indexBuilder.getCurrentNode().project(ipc);
JVS indexDoc = ipc.target;  // flat field map ready for Solr/Lucene
```

## Configuration

### Type Registry

Types are loaded by the `JsonTypeSystem` singleton from `config/types/*.json`:

```java
JsonTypeSystem ts = JsonTypeSystem.getMe();
Type personType = ts.getType("demo_person");
```

Types and execution plans are cached. The system also supports loading from JSON Schema files (`config/schemas/*.schema.json`) when `JsonTypeSystem.setUseJsonSchema(true)` is enabled.

### OpenNLP Models

NLP operations require OpenNLP model files. Default location: `${ht_bin}/data/opennlpmodels1.5/`

Required models per language (e.g., English):
- `en-sent.bin` — sentence detection
- `en-token.bin` — tokenization
- `en-pos-maxent.bin` — POS tagging
- `en-chunker.bin` — chunking
- `en-ner-person.bin`, `en-ner-location.bin`, `en-ner-organization.bin` — NER

## Maven Dependency

```xml
<dependency>
    <groupId>com.hitorro</groupId>
    <artifactId>hitorro-jsontypesystem</artifactId>
    <version>3.0.1</version>
</dependency>
```

## Build

```bash
mvn clean install -pl hitorro-core,hitorro-jsontypesystem
mvn test -pl hitorro-jsontypesystem
```

Some tests require `HT_HOME` set to the project root and config files present at `config/types/` and `config/generators/`.

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| hitorro-core | 3.0.1 | Foundation (Propaccess, JSON, file I/O) |
| Jackson | 2.18.2 | JSON processing |
| Groovy | 3.0.23 | Data mapping DSL |
| OpenNLP | 2.5.0 | Sentence detection, POS tagging, NER, chunking |
| extJWNL | 2.0.5 | WordNet dictionary |
