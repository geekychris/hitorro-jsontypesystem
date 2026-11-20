/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.jsontypesystem.executors;

import com.hitorro.jsontypesystem.Group;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fingerprints and redaction hashes are computed over a value's compact JSON. They must stay what
 * 3.0.2 (Jackson 2) computed: the expected lines were produced by running the same code against
 * hitorro-jsontypesystem 3.0.2. That includes a value nested 600 levels deep: Jackson 2 allowed
 * 1000, while Jackson 3's {@code JsonNode.toString()} throws past 500.
 */
class ValueTextDeepNestingTest {
  static final String[] VALUES = {"\"s\"", "1.10", "12345678901234567890", "{\"b\":1,\"a\":[true,null,\"\\u00e9\"]}", "-0.0", "[\"x\",{\"k\":{}}]", "DEEP"};
  static String v(String s) { return s.equals("DEEP") ? "[".repeat(600) + "]".repeat(600) : s; }
  static Group group(String method) throws Exception { Group g = new Group(); g.init(com.hitorro.util.json.JsonMappers.json().readTree("{\"name\":\"redact\",\"method\":\"" + method + "\"}")); return g; }
  static String generate() throws Exception {
    StringBuilder out = new StringBuilder();
    Propaccess path = new Propaccess("f");
    for (int i = 0; i < VALUES.length; i++) {
      String json = "{\"f\":" + v(VALUES[i]) + "}";
      String r;
      try {
        ProjectionContext pc = new ProjectionContext();
        pc.source = new JVS(com.hitorro.util.json.JsonMappers.json().readTree(json)); pc.target = new JVS();
        pc.fingerprint = MessageDigest.getInstance("SHA-256");
        new FingerprintAction(null, null, path).project(pc, path, false, null);
        r = HexFormat.of().formatHex(pc.fingerprint.digest());
      } catch (Throwable t) { r = "!" + t.getClass().getSimpleName(); }
      out.append("fingerprint.").append(i).append('=').append(r).append('\n');
      for (String mode : new String[]{"hash", "hmac"}) {
        try {
          ProjectionContext pc = new ProjectionContext();
          pc.source = new JVS(com.hitorro.util.json.JsonMappers.json().readTree(json)); pc.target = new JVS();
          pc.redactionKey = new javax.crypto.spec.SecretKeySpec("k".getBytes(), "HmacSHA256");
          new RedactAction(null, group(mode), path).project(pc, path, false, null);
          r = pc.source.getJsonNode().get("f").toString();
        } catch (Throwable t) { r = "!" + t.getClass().getSimpleName(); }
        out.append("redact.").append(mode).append('.').append(i).append('=').append(r).append('\n');
      }
    }
    return out.toString();
  }

  @Test
  void fingerprintsAndRedactionHashesMatchJackson2() throws Exception {
    List<String> expected = List.of(
            "fingerprint.0=c04bd9d24289fbe4b36ed7d20de218ffa1aae47f03b12e6d1007ae1c12468438",
            "redact.hash.0=\"0f4657778ae14ff7aeeb2c2190ede79099aa9c4e8a9e54829462f0e92af659e5\"",
            "redact.hmac.0=\"fccf56055edbd22cc1c741d43e59d362e193e7c409ddebc8dd50c977dd68cc6b\"",
            "fingerprint.1=5be08f1a4bdae8dd5e6fb97aec86469707e7d5794bf8eb81c68a71cd41ea66f7",
            "redact.hash.1=\"b05e244762b1e472be89a93800cc3ee326743cecb55984bf12813addb8de66d0\"",
            "redact.hmac.1=\"671eb1ba67fb4b4977c9cb78f978dc403acaf3ce63d229f9418bd959c1340653\"",
            "fingerprint.2=3e16c6d8ef9d57d8b62ac3cf6aa9947859b532fbbf0558cfeed6ba3bbdc942c0",
            "redact.hash.2=\"6ed645ef0e1abea1bf1e4e935ff04f9e18d39812387f63cda3415b46240f0405\"",
            "redact.hmac.2=\"c43ea99eb6dea035ab10f6bed8ec7d54717950ee712a4870005088386d3ec007\"",
            "fingerprint.3=e996e65636a54fb8425f239fb27ebc064a7c24f7dd2155cfab8e38ea84dc782b",
            "redact.hash.3=\"9cd14cf2a84c65b51da5158284140aa6809621b7b9c3667deca511cdf1ca7013\"",
            "redact.hmac.3=\"b0f7f75ed7f697654be0314c45beec1e7693670c0fc0f69f50216ae080244000\"",
            "fingerprint.4=70aad0b5f48a1f3b63b5afef213c27bd8d29029b3c480f8cd3542790bffe734e",
            "redact.hash.4=\"c26617c7ccbcaa6631b45d851b8cf56e21d2ca624bdb1193afdbd4b560702cec\"",
            "redact.hmac.4=\"c853d9c23bbb55ecdc8c2d667d075c14df9e95dafc4e9e573c3c3cb922338756\"",
            "fingerprint.5=efd9692d4010d80589b88f32d62076d5ad84085130131ea0421ad02201ff8174",
            "redact.hash.5=\"19bb92ff49a98c50eb53e602f1a15ff65fc1c6784ca09400b9112046119a0ec8\"",
            "redact.hmac.5=\"c49caf048de27e73a2ca8ee08afd71c96ad47eea8d2004712186570c46ca5aed\"",
            "fingerprint.6=cf3509efdda4f99651720460bf0b5212863e42a34181d7018d929c057b57afc8",
            "redact.hash.6=\"51ac9c4e57882319c1920f3d0133582aa196d7533c77268b9203490f6be07504\"",
            "redact.hmac.6=\"4ece4584af8551cc592a1c085ce62487ccadca197ff5ba1556fe8399f5f09013\"");
    assertEquals(String.join("\n", expected), generate().strip());
  }
}
