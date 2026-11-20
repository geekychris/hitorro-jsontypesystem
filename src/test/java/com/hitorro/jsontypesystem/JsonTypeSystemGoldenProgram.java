/*
 * Copyright (c) 2006-2025 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.jsontypesystem;

import tools.jackson.databind.*; import tools.jackson.databind.node.*;
import com.hitorro.jsontypesystem.*; import com.hitorro.jsontypesystem.executors.*;
import com.hitorro.jsontypesystem.schema.*; import com.hitorro.jsontypesystem.grouppredicates.GroupNameFilter;
import com.hitorro.jsontypesystem.datamapper.*;
import com.hitorro.util.json.String2JsonMapper; import com.hitorro.util.json.keys.propaccess.Propaccess;
import java.io.*; import java.nio.file.*; import java.util.*; import java.util.function.Predicate;
import java.security.MessageDigest;
/**
 * The program behind {@link Jackson2FormatGoldenTest}: prints {@code key=value} lines for what
 * JVS, the type system, schema conversion, validation, TypeDiff, projections, the Groovy DSL and
 * TypeJsonLoader produce. The same program, compiled against 3.0.2 and Jackson 2.18, produced
 * {@code jackson2-jsontypesystem.txt} (generator: ~/hitorro-j3/tools/Jackson2JsonTypeSystemGolden.java.txt).
 * It runs in its own JVM because the type system is a singleton anchored on HT_BIN.
 */
public class JsonTypeSystemGoldenProgram {
  static Map<String,String> out = new LinkedHashMap<>();
  static void p(String k, Object v){ out.put(k, String.valueOf(v).replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r")); }
  static String tryIt(java.util.concurrent.Callable<Object> c){ try { Object o = c.call(); return o instanceof Date ? "Date:" + ((Date) o).getTime() : o instanceof long[] ? Arrays.toString((long[]) o) : String.valueOf(o); } catch(Throwable t){ return "!"+t.getClass().getSimpleName(); } }
  static String types(JsonNode n){ StringBuilder t=new StringBuilder(); if (n == null || !n.isObject()) return ""; Iterator<String> it=n.propertyNames().iterator(); while(it.hasNext()){String k=it.next(); t.append(k+":"+n.get(k).getClass().getSimpleName()+",");} return t.toString(); }
  static String node(JsonNode n){ return n == null ? "null" : n.getClass().getSimpleName() + " " + n; }
  static final String2JsonMapper JM = new String2JsonMapper();
  static Type buildType(String json){ Type t = new Type(); t.init(JM.apply(json)); return t; }
  static <A extends ExecutorAction<ExecutionBuilder>> ExecutionBuilder<A> plan(Type type, Predicate<BaseT> predicate, ExecutorFactory<A> factory) {
    ExecutionBuilder<A> builder = new ExecutionBuilder<>(factory); type.visit(builder, predicate, new Propaccess("")); builder.finalizeNode(); return builder; }

  static final String LOOSE = "{\"s\":\"abc\",\"n\":42,\"neg\":-7,\"d\":3.7,\"big\":12345678901234567890,\"dec\":1e30,\"bt\":true,\"bf\":false,"
    + "\"nul\":null,\"ns\":\"123\",\"nd\":\"2.5\",\"nb\":\"true\",\"nbad\":\"x1\",\"arr\":[1,\"2\",3.5,null,true],\"sarr\":[\"a\",\"b\"],\"larr\":[1,2,3],"
    + "\"obj\":{\"k\":\"v\",\"n\":1,\"z\":{\"deep\":[{\"q\":1}]}},\"empty\":\"\",\"date\":\"2024-01-02T03:04:05Z\",\"datel\":1700000000000,\"dates\":\"2024-01-02\","
    + "\"title\":{\"mls\":[{\"lang\":\"en\",\"text\":\"Hello\"},{\"lang\":\"fr\",\"text\":\"Bonjour\"}]},\"id\":{\"domain\":\"dom\",\"did\":\"d1\",\"id\":\"dom~d1\"}}";
  static final String DOC_JSON = "{\"filename\":\"annual-report.pdf\",\"file_type\":\"application/pdf\",\"file_size\":12345,\"version\":\"2.1\","
    + "\"author\":\"chris.collins@hitorro.com\",\"department\":\"engineering\",\"content\":{\"mls\":[{\"lang\":\"en\",\"text\":\"The quick brown fox.\"},"
    + "{\"lang\":\"fr\",\"text\":\"Le renard.\"}]},\"keywords\":[\"quarterly\",\"report\"],\"classification\":\"internal\",\"checksum\":\"abc123\"}";

  public static void main(String[] a) throws Exception {
    // ---- JVS accessors over loosely typed JSON
    String[] paths = {"s","n","neg","d","big","dec","bt","bf","nul","ns","nd","nb","nbad","arr","sarr","larr","obj","obj.k","obj.n","obj.z.deep","obj.z.deep.q",
      "empty","date","datel","dates","title","title.mls","title.mls.text","id.id","id.domain","missing","obj.missing","arr[1]","title.mls[fr].text","title.mls[en].text","sarr[0]"};
    for (String path : paths) {
      JVS j = JVS.read(LOOSE);
      String k = "acc." + path + ".";
      p(k + "get", tryIt(() -> node(j.get(path))));
      p(k + "getString", tryIt(() -> j.getString(path)));
      p(k + "getLong", tryIt(() -> j.getLong(path)));
      p(k + "getDouble", tryIt(() -> j.getDouble(path)));
      p(k + "getBoolean", tryIt(() -> j.getBoolean(path)));
      p(k + "getDate", tryIt(() -> j.getDate(path)));
      p(k + "getStringList", tryIt(() -> j.getStringList(path)));
      p(k + "getLongArray", tryIt(() -> j.getLongArray(path)));
      p(k + "getKeys", tryIt(() -> j.getKeys(path)));
      p(k + "getSize", tryIt(() -> j.getSize(path)));
      p(k + "exists", tryIt(() -> j.exists(path)));
      p(k + "containsK", tryIt(() -> j.pathContainsKey(path, "k")));
      p(k + "after", j.getJsonNode());
    }
    { JVS j = JVS.read(LOOSE);
      p("jvs.getId", tryIt(() -> j.getId()));
      p("jvs.isEmpty", j.isEmpty());
      p("jvs.stringRep", j.getStringRepresentation());
      p("jvs.props", tryIt(() -> new TreeMap<>(j.getAsProperties())));
      p("jvs.subMaps", tryIt(() -> j.getSubMaps("title.mls").size()));
      p("jvs.resolve", tryIt(() -> j.resolveJsonVariable("x-${s}-${n}-${obj.k}-${missing}-${d}")));
      p("jvs.types", types(j.getJsonNode()));
    }
    // ---- set with Java values
    Object[] vals = {"str", 7, 7L, 2.5d, 2.5f, true, new Date(1700000000123L), new java.math.BigDecimal("1.50"), new java.math.BigInteger("123456789012345678901"),
      Arrays.asList("x","y"), Arrays.asList(1, 2), new LinkedHashMap<>(Map.of("m", 1)), null, JM.apply("{\"o\":[1,{\"p\":null}]}"), new long[]{1,2}, new String[]{"a","b"}, 'c', (short) 3, (byte) 4};
    for (int i = 0; i < vals.length; i++) {
      Object v = vals[i];
      for (String path : new String[]{"a", "x.y.z", "title.mls[de].text", "obj.k", "arr"}) {
        JVS j = JVS.read(LOOSE);
        p("set." + i + "." + path, tryIt(() -> { j.set(path, v); return node(j.get(path)) + " | " + types(j.getJsonNode()); }));
      }
      JVS j2 = new JVS();
      p("set.empty." + i, tryIt(() -> { j2.set("a.b", v); return j2.getJsonNode().toString(); }));
      JVS j3 = JVS.read(LOOSE);
      p("append." + i, tryIt(() -> { j3.append(new Propaccess("sarr"), v); j3.append(new Propaccess("newarr"), v); return j3.get("sarr") + " " + j3.get("newarr"); }));
      JVS j4 = JVS.read(LOOSE);
      p("setIfNotNull." + i, tryIt(() -> { j4.setIfNotNull(new Propaccess("q"), v); return String.valueOf(j4.get("q")); }));
    }
    { JVS j = JVS.read(LOOSE);
      p("remove", tryIt(() -> { j.remove("obj.k"); j.remove("arr"); j.remove("missing.x"); return j.getJsonNode(); }));
      JVS t = new JVS();
      p("addLangText", tryIt(() -> { t.addLangText(JVS.titleKey, "Hi", "en"); t.addLangText(JVS.titleKey, "Salut", "fr"); t.addLangText(JVS.bodyKey, "B", null); return t.getJsonNode(); }));
      JVS ty = new JVS(JsonTypeSystem.getMe().getType("core_sysobject"));
      p("addLangText.typed", tryIt(() -> { ty.addLangText(JVS.titleKey, "Hi", "en"); ty.addLangText(JVS.titleKey, "Salut", "fr"); return ty.getJsonNode() + " en=" + ty.getString("title.mls[en].text") + " fr=" + ty.getString("title.mls[fr].text") + " de=" + ty.getString("title.mls[de].text"); }));
      JVS t2 = new JVS();
      p("setDates", tryIt(() -> { t2.setDates(new Date(1700000000123L), new Date(1700000999000L)); return t2.getJsonNode() + " " + types(t2.getJsonNode().get("times")); }));
      JVS t3 = new JVS();
      p("setId", tryIt(() -> { t3.setId("dom", "abc"); t3.setType("core_sysobject"); return t3.getJsonNode() + " " + t3.getId(); }));
      JVS t4 = new JVS();
      p("addMap", tryIt(() -> { t4.addMap(new LinkedHashMap<>(Map.of("k1", "v1"))); return t4.getJsonNode(); }));
    }
    // ---- merge
    String[][] merges = {
      {"{\"a\":1,\"b\":{\"c\":2,\"d\":[1,2]},\"e\":\"x\"}", "{\"b\":{\"c\":3,\"f\":4},\"e\":null,\"g\":[3]}"},
      {"{\"a\":[1,2]}", "{\"a\":[3]}"}, {"{\"a\":{\"b\":1}}", "{\"a\":5}"}, {"{\"a\":5}", "{\"a\":{\"b\":1}}"},
      {"{\"title\":{\"mls\":[{\"lang\":\"en\",\"text\":\"A\"}]}}", "{\"title\":{\"mls\":[{\"lang\":\"fr\",\"text\":\"B\"}]}}"}};
    for (int i = 0; i < merges.length; i++) {
      String[] m = merges[i];
      p("merge.jvs." + i, tryIt(() -> { JVS b = JVS.read(m[0]); b.merge(JVS.read(m[1])); return b.getJsonNode(); }));
      p("merge.static." + i, tryIt(() -> { JsonNode b = JM.apply(m[0]); JVSMerger.merge(b, JM.apply(m[1])); return b; }));
    }
    // ---- variable resolution
    { JVS master = JVS.read(LOOSE);
      p("resolveVars", tryIt(() -> { JVS d = JVS.read("{\"x\":\"${s}\",\"y\":[\"${n}\",\"lit\",\"${obj}\"],\"z\":{\"w\":\"${missing}\"},\"q\":\"pre ${d} post\"}"); d.resolveVariables(master); return d.getJsonNode() + " " + types(d.getJsonNode()); }));
      p("resolveTextAux", tryIt(() -> JVSVariableResolver.resolveTextVariableAux("${s}/${obj.k}", master, null)));
    }
    // ---- types and schema
    String[] typeNames = {"core_string","core_long","core_boolean","core_date","core_id","core_mls","core_mlselem","core_dates","core_sysobject","core_url",
      "core_query","core_result","core_qanda","core_port","core_capability","core_cme","demo_article","demo_document","demo_person","demo_product","demo_event","dm_versionable_object"};
    for (String tn : typeNames) {
      Type t = JsonTypeSystem.getMe().getType(tn);
      p("type." + tn, t == null ? "null" : t.getNode() + " prim=" + t.isPrimitiveType() + "/" + t.getPrimitiveType() + " super=" + (t.getSuper() == null ? null : t.getSuper().getName()));
      if (t != null) {
        for (Type2JsonSchemaConverter.RefStyle rs : Type2JsonSchemaConverter.RefStyle.values())
          p("schema." + tn + "." + rs, tryIt(() -> new Type2JsonSchemaConverter(rs).convert(t)));
        p("typeVersion." + tn, tryIt(() -> TypeVersion.fromDefinition(t.getNode())));
      }
    }
    // ---- validator
    Type sys = JsonTypeSystem.getMe().getType("core_sysobject");
    Type art = JsonTypeSystem.getMe().getType("demo_article");
    String[] vdocs = {LOOSE, "{}", "{\"id\":{\"domain\":5},\"title\":\"notmls\",\"times\":{\"created\":\"yesterday\"}}", DOC_JSON,
      "{\"title\":{\"mls\":[{\"lang\":\"en\",\"text\":1}]},\"author\":\"x\",\"word_count\":\"12\",\"tags\":\"a\"}"};
    for (int i = 0; i < vdocs.length; i++) {
      String d = vdocs[i];
      p("validate.sys." + i, tryIt(() -> JVSValidator.validate(JVS.read(d), sys)));
      if (art != null) p("validate.article." + i, tryIt(() -> JVSValidator.validate(JVS.read(d), art)));
      p("validate.def." + i, tryIt(() -> JVSValidator.reportAgainstDefinition(JVS.read(d), JM.apply(
        "{\"name\":\"t\",\"fields\":[{\"name\":\"s\",\"type\":\"core_string\",\"required\":true},{\"name\":\"n\",\"type\":\"core_long\",\"minimum\":0,\"maximum\":10},"
        + "{\"name\":\"bt\",\"type\":\"core_boolean\"},{\"name\":\"author\",\"type\":\"core_string\",\"format\":\"email\"},{\"name\":\"sarr\",\"type\":\"core_string\",\"vector\":true,\"enum\":[\"a\"]}]}"))));
    }
    // ---- TypeDiff / TypeVersion
    String oldDef = "{\"name\":\"t\",\"version\":\"1.2.3\",\"fields\":[{\"name\":\"a\",\"type\":\"core_string\"},{\"name\":\"b\",\"type\":\"core_long\"},{\"name\":\"c\",\"type\":\"core_mls\",\"vector\":true}]}";
    String newDef = "{\"name\":\"t\",\"version\":\"1.3.0\",\"fields\":[{\"name\":\"a\",\"type\":\"core_mls\"},{\"name\":\"c\",\"type\":\"core_mls\"},{\"name\":\"d\",\"type\":\"core_date\",\"required\":true}]}";
    p("typeDiff.diff", tryIt(() -> TypeDiff.diff(JM.apply(oldDef), JM.apply(newDef))));
    p("typeDiff.report", tryIt(() -> TypeDiff.report(JM.apply(oldDef), JM.apply(newDef))));
    p("typeDiff.script", tryIt(() -> TypeDiff.generateMigrationScript(JM.apply(oldDef), JM.apply(newDef))));
    p("typeVersion.bump", tryIt(() -> TypeVersion.suggestedBump(JM.apply(oldDef), JM.apply(newDef))));
    p("typeVersion.parse", tryIt(() -> TypeVersion.fromDefinition(JM.apply("{\"name\":\"x\",\"version\":2}")) + " " + TypeVersion.fromDefinition(JM.apply("{\"name\":\"x\"}"))));
    // ---- projections
    { Type t = buildType("{\"name\":\"document_redact_test\",\"fields\":[{\"name\":\"author\",\"type\":\"core_string\",\"groups\":[{\"name\":\"redact\",\"method\":\"mask\"}]},"
        + "{\"name\":\"checksum\",\"type\":\"core_string\",\"groups\":[{\"name\":\"redact\",\"method\":\"hash\"}]},{\"name\":\"file_size\",\"type\":\"core_long\",\"groups\":[{\"name\":\"redact\",\"method\":\"hash\"}]},{\"name\":\"filename\",\"type\":\"core_string\"}]}");
      ExecutionBuilder<RedactAction> pl = plan(t, (Predicate<BaseT>) GroupNameFilter.redactFilter, new RedactFactory());
      p("proj.redact", tryIt(() -> { JVS doc = JVS.read(DOC_JSON); ProjectionContext pc = new ProjectionContext(); pc.source = doc; pc.target = new JVS(); pl.getExecutor().project(pc); return doc.getJsonNode() + " | " + pc.target.getJsonNode(); })); }
    { Type t = buildType("{\"name\":\"document_fp_test\",\"fields\":[{\"name\":\"filename\",\"type\":\"core_string\",\"groups\":[{\"name\":\"fingerprint\",\"method\":\"sha256\"}]},"
        + "{\"name\":\"version\",\"type\":\"core_string\",\"groups\":[{\"name\":\"fingerprint\",\"method\":\"sha256\"}]},{\"name\":\"file_size\",\"type\":\"core_long\",\"groups\":[{\"name\":\"fingerprint\",\"method\":\"sha256\"}]},"
        + "{\"name\":\"content\",\"type\":\"core_mls\",\"groups\":[{\"name\":\"fingerprint\"}]},{\"name\":\"keywords\",\"type\":\"core_string\",\"vector\":true,\"groups\":[{\"name\":\"fingerprint\"}]}]}");
      ExecutionBuilder<FingerprintAction> pl = plan(t, (Predicate<BaseT>) GroupNameFilter.fingerprintFilter, new FingerprintFactory());
      for (String d : new String[]{DOC_JSON, DOC_JSON.replace("12345", "\"12345\""), DOC_JSON.replace("\"2.1\"", "2.1"), "{}"})
        p("proj.fingerprint." + d.hashCode(), tryIt(() -> { ProjectionContext pc = new ProjectionContext(); pc.source = JVS.read(d); pc.target = new JVS(); pc.fingerprint = MessageDigest.getInstance("SHA-256"); pl.getExecutor().project(pc); return HexFormat.of().formatHex(pc.fingerprint.digest()); })); }
    { Type t = buildType("{\"name\":\"document_validate_test\",\"fields\":[{\"name\":\"author\",\"type\":\"core_string\",\"format\":\"email\",\"groups\":[{\"name\":\"validate\",\"method\":\"check\"}]},"
        + "{\"name\":\"file_size\",\"type\":\"core_long\",\"minimum\":0,\"maximum\":1000000,\"groups\":[{\"name\":\"validate\",\"method\":\"check\"}]},"
        + "{\"name\":\"classification\",\"type\":\"core_string\",\"enum\":[\"public\",\"internal\"],\"groups\":[{\"name\":\"validate\",\"method\":\"check\"}]},{\"name\":\"filename\",\"type\":\"core_string\"}]}");
      ExecutionBuilder<ValidateAction> pl = plan(t, (Predicate<BaseT>) GroupNameFilter.validateFilter, new ValidateFactory());
      String[] ds = {DOC_JSON, "{\"author\":\"not-an-email\",\"file_size\":5000000,\"classification\":\"top-secret\"}", "{\"author\":5,\"file_size\":\"12\",\"classification\":null}", "{\"file_size\":1.5,\"author\":[\"a@b.c\"]}"};
      for (int i = 0; i < ds.length; i++) { String d = ds[i];
        p("proj.validate." + i, tryIt(() -> { ProjectionContext pc = new ProjectionContext(); pc.source = JVS.read(d); pc.target = new JVS(); pc.violations = new ArrayList<>(); pl.getExecutor().project(pc); return pc.violations; })); } }
    // ---- Groovy DSL
    File gens = new File(System.getProperty("HT_BIN"), "config/generators");
    String[] scripts = {"copyAll()", "set 'target.status', 'published'", "copy \"source.name\" to \"target.title\"",
      "copyAll()\ndelete \"target.secret\"", "set \"target.id.domain\", \"synthetic\"\nset \"target.n\", 5\nset \"target.f\", 2.5\nset \"target.b\", true",
      "mls \"target.title\", text: \"Hello World\", lang: \"en\"", "when(sourceString(\"kind\") == \"article\") {\n set \"target.matched\", \"yes\"\n}",
      "loop(\"source.tags\") { tag ->\n append \"target.categories\", tag\n}", "set \"target.v\", source(\"value\")\nset \"target.s\", sourceString(\"value\")\nset \"target.o\", source(\"obj\")",
      "set \"target.l\", [1, 'two', 3.5]\nset \"target.m\", [a: 1, b: [c: 'd']]"};
    String src = "{\"name\":\"test\",\"value\":42,\"secret\":\"s\",\"kind\":\"article\",\"tags\":[\"t1\",\"t2\"],\"obj\":{\"x\":[1,null]}}";
    for (int i = 0; i < scripts.length; i++) { String sc = scripts[i];
      p("dsl." + i, tryIt(() -> GroovyTransformMapper.fromString(sc, gens).apply(JVS.read(src)).getJsonNode())); }
    // ---- JVS file I/O
    Path dir = Files.createTempDirectory("jg");
    p("jvs.write", tryIt(() -> { com.hitorro.util.basefile.fs.file.FileFileSystem fs = new com.hitorro.util.basefile.fs.file.FileFileSystem(dir.toFile());
      com.hitorro.util.basefile.fs.BaseFile bf = fs.getFile("w.json"); JVS.read(LOOSE).write(bf); return Files.readString(dir.resolve("w.json")); }));
    Files.writeString(dir.resolve("r.json"), LOOSE); Files.writeString(dir.resolve("bad.json"), "{\"a\": ]"); Files.writeString(dir.resolve("empty.json"), "");
    for (String f : new String[]{"r.json", "bad.json", "empty.json", "nope.json"})
      p("jvs.readFile." + f, tryIt(() -> { JVS j = JVS.read(dir.resolve(f).toFile()); return j == null ? "null" : node(j.getJsonNode()); }));
    for (String s : new String[]{"", "{\"a\": ]", "[1,2]", "42", "{\"a\":1} {\"b\":2}", "{\"a\":1} x", "null"})
      p("jvs.readString." + s, tryIt(() -> { JVS j = JVS.read(s); return j == null ? "null" : node(j.getJsonNode()); }));
    for (String s : new String[]{"classpath:config/types/core_id.json", "classpath:nope.json", dir.resolve("bad.json").toString(), dir.resolve("empty.json").toString(), "file:" + dir.resolve("r.json")})
      p("typeJsonLoader." + s.replace(dir.toString(), "TMP"), tryIt(() -> node(com.hitorro.jsontypesystem.resources.TypeJsonLoader.load(s))));

    StringBuilder sb = new StringBuilder();
    out.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
    if (a.length > 0) Files.writeString(Paths.get(a[0]), sb, java.nio.charset.StandardCharsets.UTF_8); // logging may share stdout
    else System.out.print(sb);
  }
}
