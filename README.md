# hitorro-jsontypesystem

The HiTorro JSON Type System (JVS) -- a standalone library for type-aware JSON document processing with projections, data mapping, and NLP support.

This module can be used independently from the rest of HiTorro. It depends only on `hitorro-core` for foundation utilities.

## Architecture

```
hitorro-core (foundation)
     |
hitorro-jsontypesystem (this module)
     |
     +-- com.hitorro.jsontypesystem   Type system, JVS documents, projections, schema
     +-- com.hitorro.language         NLP: OpenNLP, stemming, POS tagging, WordNet
     +-- com.hitorro.basetext         Text classifiers (MaxEnt, answer type)
```

### Design Principles

- **Type definitions as data**: Types are defined in JSON config files, not Java classes. This allows runtime type loading, hot-reload, and external tooling.
- **Projections over mutations**: The executor framework projects data between source and target documents rather than mutating in place. Three phases: index, enrich, remove.
- **Configuration-driven transformations**: Groovy DSL scripts define data mappings. No hardcoded business logic in the transform pipeline.
- **Multi-language by default**: The MLS (Multi-Language String) pattern is built into core types. NLP operations are language-aware.

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.hitorro</groupId>
    <artifactId>hitorro-jsontypesystem</artifactId>
    <version>3.0.1</version>
</dependency>
```

### Creating and Navigating JVS Documents

JVS is the central document type -- a typed wrapper around Jackson `JsonNode` with path-based property access.

```java
import com.hitorro.jsontypesystem.JVS;

// Create from JSON string
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
boolean exists = doc.exists("id.domain");                 // true

// Set nested values (creates intermediate nodes automatically)
JVS doc = new JVS();
doc.set("id.did", "doc-002");
doc.set("metadata.version", 1);
doc.set("metadata.active", true);

// Clone independently
JVS clone = doc.clone();
clone.set("id.did", "doc-003");  // original unchanged

// Merge documents
doc.merge(overlay);  // overlay values win on conflict
```

### Working with Types

Types define the structure and behavior of JVS documents. They support inheritance, computed fields, and field grouping.

```java
import com.hitorro.jsontypesystem.JsonTypeSystem;
import com.hitorro.jsontypesystem.Type;
import com.hitorro.jsontypesystem.Field;

// Access the type registry (loads from config/types/*.json)
JsonTypeSystem ts = JsonTypeSystem.getMe();

// Look up a type
Type personType = ts.getType("demo_person");
Type superType = personType.getSuper();  // "sysobject"

// Create a typed document
JVS person = new JVS(personType);
person.set("first_name", "Chris");
person.set("last_name", "Collins");
// Dynamic field "full_name" is computed from first_name + last_name

// Access field definitions
Field nameField = personType.getField("first_name");
boolean isVector = nameField.isVector();
boolean isDynamic = nameField.isDynamic();
```

### Type Definition Format

Type definitions live in `config/types/*.json`:

```json
{
    "name": "person",
    "description": "Person profile",
    "super": "sysobject",
    "fields": [
        {
            "name": "first_name",
            "type": "core_string",
            "groups": [{"name": "index", "method": "text", "tags": ["basic"]}]
        },
        {
            "name": "email",
            "type": "core_string",
            "vector": true
        },
        {
            "name": "biography",
            "type": "core_mls",
            "i18n": true
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
- `type` - reference to another type (primitives: `core_string`, `core_long`, `core_date`, `core_boolean`; composites: `core_id`, `core_mls`, etc.)
- `vector: true` - multi-valued (array) field
- `i18n: true` - internationalized field
- `dynamic` - computed from other fields using a mapper class
- `groups` - field groupings for projections (index, enrich, remove) with methods and tags

**Inheritance:** Types can extend a `super` type. Fields are merged -- child fields override parent fields with the same name.

### Projections Framework

Projections transform documents by selectively processing fields based on their group definitions.

```java
import com.hitorro.jsontypesystem.executors.*;

// Build an execution plan from a type
Type articleType = JsonTypeSystem.getMe().getType("demo_article");

// Index projection -- extracts fields tagged for indexing
ExecutionBuilder<IndexerAction> indexBuilder =
    new IndexExecutionBuilderMapper().apply(articleType);
ExecutionNode indexPlan = indexBuilder.getCurrentNode();

// Execute projection
ProjectionContext pc = new ProjectionContext();
pc.source = sourceDoc;
pc.target = new JVS();
indexPlan.project(pc);
// pc.target now contains only the indexed fields

// Enrich projection -- initializes/expands fields
ExecutionBuilder<EnrichAction> enrichBuilder =
    new EnrichExecutionBuilderMapper().apply(articleType);

// Execution tracing for debugging
ExecutionTrace trace = new ExecutionTrace();
pc.trace = trace;
indexPlan.project(pc);
System.out.println(trace.summary());
```

**Three projection phases:**
| Phase | Factory | Action | Purpose |
|-------|---------|--------|---------|
| Index | `IndexerFactory` | `IndexerAction` | Extract fields for search indexing with Solr field type mapping |
| Enrich | `EnrichFactory` | `EnrichAction` | Initialize and expand field structures |
| Remove | `RemoveFactory` | `RemoveAction` | Remove specified fields |

### Data Mapping (Groovy DSL)

Transform documents using Groovy scripts with a rich DSL.

```java
import com.hitorro.jsontypesystem.datamapper.*;

// Load and execute a transform script
GroovyTransformMapper mapper = new GroovyTransformMapper("article_transform.groovy");
MappingContext ctx = new MappingContext(sourceDoc, new DataGenerators());
JVS result = mapper.map(ctx);
```

**MappingContext registers:**
- `source` - input document (read-only)
- `target` - output document being built
- `work` - scratch space for intermediate values
- `gen` - data generators (`gen.firstName()`, `gen.email()`, `gen.uuid()`, etc.)

**Groovy transform example** (`config/transforms/article_transform.groovy`):
```groovy
target.set("id.did", gen.uuid())
target.set("title.mls[0].lang", "en")
target.set("title.mls[0].text", source.getString("headline"))
target.set("dates.created", gen.timestamp())
```

### Schema Support

Convert between HiTorro type definitions and JSON Schema (draft 2020-12).

```java
import com.hitorro.jsontypesystem.schema.*;

// Type to JSON Schema
Type2JsonSchemaConverter converter = new Type2JsonSchemaConverter();
JsonNode schema = converter.convert(articleType, ReferenceStyle.BUNDLED);

// JSON Schema to Type
JsonSchema2TypeConverter reverse = new JsonSchema2TypeConverter();
Type roundTripped = reverse.convert(schema);

// Validate documents
JVSValidator validator = new JVSValidator();
List<JVSValidator.Violation> violations = JVSValidator.validate(doc, articleType);
```

### Validation

```java
import com.hitorro.jsontypesystem.JVSValidator;

List<JVSValidator.Violation> violations = JVSValidator.validate(doc, type);
for (JVSValidator.Violation v : violations) {
    System.out.printf("[%s] %s: %s%n", v.level(), v.path(), v.message());
}
// Levels: INFO, WARNING, ERROR

// Human-readable report
String report = JVSValidator.report(doc, type);
```

### Type Diffing

Compare type definitions and generate migration scripts.

```java
import com.hitorro.jsontypesystem.TypeDiff;

TypeDiff diff = new TypeDiff();
List<TypeDiff.Change> changes = diff.diff(oldType, newType);
// Changes: ADDED, REMOVED, MODIFIED
String report = diff.report(oldType, newType);
```

## NLP Support

### Snowball Stemming

```java
import com.hitorro.language.SnowballSimpleStemmer;
import org.tartarus.snowball.ext.EnglishStemmer;

SnowballSimpleStemmer stemmer = new SnowballSimpleStemmer(new EnglishStemmer());
// Available: EnglishStemmer, GermanStemmer, FrenchStemmer, SpanishStemmer, etc.
```

### Sentence Detection

```java
import com.hitorro.language.SentenceDetectorSingleton;
import com.hitorro.language.SentenceSegmenter;
import com.hitorro.language.Sentences;

// Requires OpenNLP model files at ${ht_bin}/data/opennlpmodels1.5/
SentenceSegmenter segmenter = SentenceDetectorSingleton.singleton
    .get(Iso639Table.english).get();
Sentences sentences = segmenter.segment("Hello world. How are you?");
```

### POS Tagging

```java
import com.hitorro.language.PartOfSpeech;
import com.hitorro.language.POS;

PartOfSpeech pos = PartOfSpeechSingletonMapper.singleton
    .get(Iso639Table.english).get();
POS result = pos.getPOS("The quick brown fox jumped.");
String[] tokens = result.getTokenizedText();
List<String>[] tags = result.getTags();
```

### Dynamic Field Mappers (NLP)

These are registered in type definitions and compute fields automatically:

| Mapper | Purpose |
|--------|---------|
| `SentenceSegmenter` | Segment text into sentences |
| `SentenceSegmenterSpan` | Segment with character offset spans |
| `POSTokenizer` | POS-tag tokenized text |
| `ChunkMapper` | Extract noun/verb phrases |
| `TextClassifier` | Classify text (answer type detection) |
| `FPHashMapper` | Fingerprint hash for deduplication |
| `HTMLParserMapper` | Extract text from HTML |
| `NormalizedTextHashMapper` | Hash normalized text |
| `UrlNormalizerMapper` | Normalize URLs |

### OpenNLP Models

NLP operations require OpenNLP model files. Default location: `${ht_bin}/data/opennlpmodels1.5/`

Required models by language (e.g., English):
- `en-sent.bin` - sentence detection
- `en-token.bin` - tokenization
- `en-pos-maxent.bin` - POS tagging
- `en-chunker.bin` - chunking
- `en-ner-person.bin`, `en-ner-location.bin`, `en-ner-organization.bin` - NER

Configure the model path:
```
-Dopennlp.rootpath=/path/to/models
```

## Spring Boot Example

A standalone Spring Boot application demonstrating the type system and NLP:

```bash
cd hitorro-jvs-example-springboot
mvn spring-boot:run
# Open http://localhost:8080
```

**REST API:**
```
GET  /api/jvs/types              List available type names
GET  /api/jvs/types/{name}       Get type definition with fields
POST /api/jvs/documents          Create a typed JVS document
POST /api/jvs/merge              Merge two documents
POST /api/jvs/validate           Validate against type definition
POST /api/jvs/stem               Stem text (en/de/fr/es)
POST /api/jvs/enrich             Enrich MLS fields with stems
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| hitorro-core | 3.0.1 | Foundation (Propaccess, JSON, file I/O) |
| Jackson | 2.18.2 | JSON processing |
| Groovy | 3.0.23 | Data mapping DSL |
| OpenNLP | 2.5.0 | NLP (sentences, POS, NER, parsing) |
| Lucene Analysis | 9.12.0 | Snowball stemming |
| extJWNL | 2.0.5 | WordNet dictionary |

## Build

```bash
# Build this module
mvn clean install -pl hitorro-core,hitorro-jsontypesystem

# Run tests
mvn test -pl hitorro-jsontypesystem

# Build Spring Boot example
cd hitorro-jvs-example-springboot && mvn clean package
```

Note: Some data mapper and schema tests require `HT_HOME` set to the project root and config files present at `config/types/` and `config/generators/`.

## Package Structure

```
com.hitorro.jsontypesystem/
    JVS.java                    Core document type (typed JsonNode wrapper)
    Type.java                   Type definition with fields, inheritance
    Field.java                  Field definition with groups, dynamic mappers
    Group.java                  Field grouping for projections
    JsonTypeSystem.java         Type registry singleton
    JVSValidator.java           Document validation against types
    JVSMerger.java              Document merging
    JVSVariableResolver.java    ${variable} resolution
    CSV2JVSIterator.java        CSV to JVS conversion
    TypeDiff.java               Type comparison and migration
    executors/                  Projection framework (index, enrich, remove)
    datamapper/                 Groovy DSL data mapping
    dynamic/                    Dynamic field mappers (NLP, hashing, etc.)
    schema/                     JSON Schema conversion and validation
    propreaders/                Configuration loading and hot-reload
    predicates/                 JVS predicate filters

com.hitorro.language/
    IsoLanguage.java            Language representation with model loading
    Iso639Table.java            ISO 639 language table
    SnowballSimpleStemmer.java  Snowball stemmer wrapper
    SentenceSegmenter.java      OpenNLP sentence segmentation
    SentenceDetectorSingleton   Model caching singleton
    PartOfSpeech.java           POS tagger with pooling
    NameFinder.java             Named entity recognition
    HTJWNLDictionary.java       WordNet integration
    Models.java                 NLP model singleton registry
    mappers/                    String-to-NLP-result mappers

com.hitorro.basetext/
    classifier/                 Classifier interface
    maxentclassifier/           MaxEnt classifiers, OpenNLP closures, chunk parser
```
