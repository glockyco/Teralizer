---
title: NULL_CONCRETE Sampling Audit
type: audit
status: complete
created: 2026-08-10
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# NULL_CONCRETE Sampling Audit

## Finding

`NULL_CONCRETE` is mechanically correct as a description of the persisted artifact in all 20 sampled rows. It is not a reliable semantic description of an output that is independent of generalizable inputs. Eighteen sampled assertions have outputs that plainly vary with a generalizable input, while two have outputs that are independent of the generalizable input under test.

The sample therefore answers the research question in favor of a masking problem. The 13.6% figure should be described as an observed yield produced by this single-path extraction pipeline, not as a property of the Java tests in the corpus.

## What the classifier classifies

`src/main/java/teralizer/jpf/OutputSpecClassifier.java` first returns `EXCEPTION` for captured output kind `THROWN`. For every other output kind it returns `NULL_CONCRETE` when the `Model modelOutput` argument is null. Otherwise it visits the model and returns `CONSTANT` when no variables are present or `SYMBOLIC` when variables are present.

The model passed to this classifier is the JSON artifact at `assertion.output_specification_path`, not the concrete return-value artifact. In `src/main/java/teralizer/processing/task/TestGeneralizationTask.java`, `createBuilderPlan` reads `assertionRecord.getOutputSpecificationPath()`, deserializes it with `JsonToModelTransformer.transform`, and passes the resulting `outputModel` to `OutputSpecClassifier.classify`. The concrete captured result is a separate `CapturedOutput` read from `assertionRecord.getOutputValuePath()` with `SpecificationGson`.

`src/main/java/teralizer/jpf/SpecificationExtractor.java` writes `invocation.getModelOutput()` through `ModelToJsonTransformer.transform` to `outputSpecificationPath`, and writes `invocation.getOutput()` through the typed adapter to `outputValuePath`. `src/main/java/teralizer/processing/task/JpfExecutionTask.java` calls this writer after an extracted JPF invocation and then persists `output_spec_class`. For a normal return, `src/main/java/teralizer/jpf/TestGeneralizationListener.java` obtains the SPF expression from the JVM return attribute and transforms it to `modelOutput`. Its boxed-primitive field fallback only applies when a symbolic attribute is found on the wrapper's `value` field. Thus a literal `null` output-specification file is the direct evidence behind `NULL_CONCRETE` in this audit.

## Sampling method and SQL

The database was `postgres_reporeapers_rq6_v6`, the completed v6 run with variant `IMPROVED_200_TRIES`. The population query returned 4,649 generalization rows joined to assertions whose `assertion.output_spec_class` is `NULL_CONCRETE`. The branch labels reconstruct the refusal-branch order documented in `docs/exclusion-model.md`. The sample used eight rows from `oracle expression not boolean`, eight from `concretization events present`, and four from `parameter coverage or other`. Each stratum used `ORDER BY random()`.

```sql
WITH candidates AS (
    SELECT
        a.id AS assertion_id,
        g.id AS generalization_id,
        a.project_id,
        p.root_path AS project_root_path,
        a.tested_method_qualified_name,
        a.tested_method_return_type,
        a.assertion_relative_path,
        a.tested_method_relative_path,
        a.output_specification_path,
        a.generalization_recipe::jsonb ->> 'oracleExpressionType'
            AS oracle_expression_type,
        a.concretization_events,
        a.post_concretization_divergence_risk,
        CASE
            WHEN coalesce(a.generalization_recipe::jsonb ->> 'oracleExpressionType', '')
                 NOT IN ('boolean', 'java.lang.Boolean')
                THEN 'oracle expression not boolean'
            WHEN coalesce(a.concretization_events, 0) > 0
                THEN 'concretization events present'
            ELSE 'parameter coverage or other'
        END AS branch,
        row_number() OVER (
            PARTITION BY CASE
                WHEN coalesce(a.generalization_recipe::jsonb ->> 'oracleExpressionType', '')
                     NOT IN ('boolean', 'java.lang.Boolean')
                    THEN 'oracle expression not boolean'
                WHEN coalesce(a.concretization_events, 0) > 0
                    THEN 'concretization events present'
                ELSE 'parameter coverage or other'
            END
            ORDER BY random()
        ) AS rn
    FROM generalization g
    JOIN assertion a ON a.id = g.assertion_id
    JOIN project p ON p.id = a.project_id
    WHERE a.output_spec_class = 'NULL_CONCRETE'
)
SELECT assertion_id, generalization_id, project_id, project_root_path,
       tested_method_qualified_name, tested_method_return_type,
       oracle_expression_type, concretization_events,
       post_concretization_divergence_risk, output_specification_path, branch
FROM candidates
WHERE (branch = 'oracle expression not boolean' AND rn <= 8)
   OR (branch = 'concretization events present' AND rn <= 8)
   OR (branch = 'parameter coverage or other' AND rn <= 4)
ORDER BY branch, assertion_id;
```

The population count was checked with:

```sql
SELECT count(*)
FROM generalization g
JOIN assertion a ON a.id = g.assertion_id
WHERE a.output_spec_class = 'NULL_CONCRETE';
```

It returned 4,649. The population branch counts from the same database were 1,829 non-boolean, 1,329 concretization, and 1,491 parameter-coverage-or-other. The SQL returned these 20 rows. Every listed output specification was read from the path in the table and contained the JSON literal `null`.

## Twenty-row evidence table

The `assertion source` expressions are from the database row `assertion.assertion_source_code`. `S1` through `S17` identify the original test and tested-method files in the source index below. The `method parameters` column is the persisted `assertion.tested_method_parameters` and identifies the input varied by the generalization wrapper. A source verdict is one sentence per case.

| assertion id | gen id | branch | project root path | tested method | oracle expression type | concretization events | method parameters | output specification path | assertion source from DB | source | verdict and one-sentence justification |
|---:|---:|---|---|---|---|---:|---|---|---|---|---|
| 18334 | 522 | concretization events present | `projects/github_com_t3t5u_common-util` | `com.github.t3t5u.common.util.Version.isMajorUpdated` | `boolean` | 2 | `_ctor_oldVersion_zero_s`, `_ctor_newVersion_zero_s` | `data/reporeapers-rerun-v6/github_com_t3t5u_common-util/project-id-78/jpf-data/specs/com.github.t3t5u.common.util.VersionTest.testIsMajorUpdated.18334.jpf.symbolic.output.json` | `assertThat(Version.isMajorUpdated(new Version("0.0"), new Version("0.1")), is(false))` | S2 | **EXTRACTION FAILURE.** [S2] `isMajorUpdated` compares the two versions' major components, so changing the generalized constructor strings can change the returned boolean.
| 109932 | 3273 | concretization events present | `projects/github_com_runeflobakk_motif` | `no.motif.f.Predicate.$` | `boolean` | 1 | `value` | `data/reporeapers-rerun-v6/github_com_runeflobakk_motif/project-id-694/jpf-data/specs/no.motif.StringsTest.blank.109932.jpf.symbolic.output.json` | `assertThat(blank.$(" "), is(true))` | S6 | **EXTRACTION FAILURE.** [S6] `blank` tests whether the input is null, empty, or all whitespace, so changing generalized `value` can change the asserted boolean.
| 133014 | 3654 | concretization events present | `projects/github_com_ppke-nlpg_purepos` | `hu.ppke.itk.nlpg.purepos.model.internal.TrieNode.hasChild` | `boolean` | 1 | `id` | `data/reporeapers-rerun-v6/github_com_ppke-nlpg_purepos/project-id-876/jpf-data/specs/hu.ppke.itk.nlpg.purepos.model.internal.NGramModelTest.testAgainstHunPos.133014.jpf.symbolic.output.json` | `Assert.assertTrue(mymodel.root.hasChild(3))` | S8 | **EXTRACTION FAILURE.** [S8] `hasChild(id)` returns whether the child-node map contains the requested id, so changing generalized `id` can change the returned boolean.
| 156752 | 4293 | concretization events present | `projects/github_com_joschi_JadConfig` | `com.github.joschi.jadconfig.util.Duration.compareTo` | `boolean` | 2 | `site0`, `site1` | `data/reporeapers-rerun-v6/github_com_joschi_JadConfig/project-id-975/jpf-data/specs/com.github.joschi.jadconfig.util.DurationTest.isComparable.156752.jpf.symbolic.output.json` | `assertTrue(Duration.microseconds(0).compareTo(Duration.microseconds(1)) < 0)` | S9 | **EXTRACTION FAILURE.** [S9] `Duration.compareTo` compares normalized duration counts, so changing either generalized site value can change the returned ordering integer and the asserted boolean.
| 156822 | 4363 | concretization events present | `projects/github_com_joschi_JadConfig` | `com.github.joschi.jadconfig.util.Duration.compareTo` | `boolean` | 2 | `site0`, `site1` | `data/reporeapers-rerun-v6/github_com_joschi_JadConfig/project-id-975/jpf-data/specs/com.github.joschi.jadconfig.util.DurationTest.isComparable.156822.jpf.symbolic.output.json` | `assertTrue(Duration.minutes(1).compareTo(Duration.microseconds(0)) > 0)` | S9 | **EXTRACTION FAILURE.** [S9] `Duration.compareTo` compares normalized duration counts, so changing either generalized site value can change the returned ordering integer and the asserted boolean.
| 157449 | 4735 | concretization events present | `projects/github_com_joschi_JadConfig` | `com.github.joschi.jadconfig.util.Size.compareTo` | `boolean` | 2 | `site0`, `site1` | `data/reporeapers-rerun-v6/github_com_joschi_JadConfig/project-id-975/jpf-data/specs/com.github.joschi.jadconfig.util.SizeTest.isComparable.157449.jpf.symbolic.output.json` | `assertTrue(Size.kilobytes(1).compareTo(Size.petabytes(2)) < 0)` | S10 | **EXTRACTION FAILURE.** [S10] `Size.compareTo` compares normalized sizes, so changing either generalized site value can change the returned ordering integer and the asserted boolean.
| 190235 | 6127 | concretization events present | `projects/github_com_ManfredTremmel_gwt-commons-validator` | `org.apache.commons.validator.routines.UrlValidator.isValid` | `boolean` | 1 | `value` | `data/reporeapers-rerun-v6/github_com_ManfredTremmel_gwt-commons-validator/project-id-1166/jpf-data/specs/org.apache.commons.validator.routines.UrlValidatorTest.testValidator288.190235.jpf.symbolic.output.json` | `assertTrue("localhost URL should validate", validator.isValid("http://localhost/test/index.html"))` | S15 | **EXTRACTION FAILURE.** [S15] `UrlValidator.isValid(value)` parses and validates the supplied URL, so changing generalized `value` can change the returned boolean.
| 190268 | 6160 | concretization events present | `projects/github_com_ManfredTremmel_gwt-commons-validator` | `org.apache.commons.validator.routines.UrlValidator.isValid` | `boolean` | 1 | `value` | `data/reporeapers-rerun-v6/github_com_ManfredTremmel_gwt-commons-validator/project-id-1166/jpf-data/specs/org.apache.commons.validator.routines.UrlValidatorTest.testValidator290.190268.jpf.symbolic.output.json` | `assertTrue(validator.isValid("http://test.xn--ogbpf8fl"))` | S15 | **EXTRACTION FAILURE.** [S15] `UrlValidator.isValid(value)` parses and validates the supplied URL, so changing generalized `value` can change the returned boolean.
| 17135 | 384 | oracle expression not boolean | `projects/github_com_almondtools_rexlex` | `com.almondtools.rexlex.automaton.LowByteCharClassMapper.getIndex` | `int` | 0 | `ch` | `data/reporeapers-rerun-v6/github_com_almondtools_rexlex/project-id-61/jpf-data/specs/com.almondtools.rexlex.automaton.LowByteCharClassMapperTest.testGetIndex.17135.jpf.symbolic.output.json` | `assertThat(mapper.getIndex('n'), equalTo(2))` | S1 | **EXTRACTION FAILURE.** [S1] `getIndex(ch)` indexes an array with `ch & 0xff`, so changing generalized `ch` can change the returned integer.
| 98155 | 3052 | oracle expression not boolean | `projects/github_com_spotify_sparkey-java` | `com.spotify.sparkey.Util.unsignedVLQSize` | `int` | 91 | `value` | `data/reporeapers-rerun-v6/github_com_spotify_sparkey-java/project-id-630/jpf-data/specs/com.spotify.sparkey.UtilTest.testUnsignedVLQSize.98155.jpf.symbolic.output.json` | `assertEquals(1, Util.unsignedVLQSize(1 << 6))` | S4 | **EXTRACTION FAILURE.** [S4] `unsignedVLQSize(value)` changes at powers-of-128 thresholds, so changing generalized `value` can change the returned integer.
| 107244 | 3166 | oracle expression not boolean | `projects/github_com_bfh-evg_unicrypt` | `ch.bfh.unicrypt.helper.array.abstracts.AbstractImmutableArray.countExcept` | `int` | 0 | `value` | `data/reporeapers-rerun-v6/github_com_bfh-evg_unicrypt/project-id-686/jpf-data/specs/ch.bfh.unicrypt.helper.array.DenseArrayTest.testCount.107244.jpf.symbolic.output.json` | `Assert.assertEquals(6, da.countExcept("a"))` | S5 | **EXTRACTION FAILURE.** [S5] `countExcept(value)` counts array elements not equal to the supplied value, so changing generalized `value` can change the returned integer.
| 126870 | 3466 | oracle expression not boolean | `projects/github_com_uaithne_uaithne-generator` | `org.uaithne.generator.processors.database.myBatis.MyBatisSqlQueryGenerator.getConditionElementValue` | `java.lang.String` | 0 | `rule` | `data/reporeapers-rerun-v6/github_com_uaithne_uaithne-generator/project-id-820/jpf-data/specs/org.uaithne.generator.processors.database.myBatis.MyBatisSqlQueryGeneratorTest.testGetConditionElementValueWithNameRule.126870.jpf.symbolic.output.json` | `assertEquals(expResult, result.toString())` | S7 | **EXTRACTION FAILURE.** [S7] `getConditionElementValue` selects different returned strings for different `rule` values, so the generalized `rule` can change the observed result string.
| 158744 | 4965 | oracle expression not boolean | `projects/github_com_mwanji_migrate4j-maven` | `com.eroi.migrate.generators.GeneratorHelper.getSqlName` | `java.lang.String` | 0 | `type` | `data/reporeapers-rerun-v6/github_com_mwanji_migrate4j-maven/project-id-983/jpf-data/specs/com.eroi.migrate.generators.GeneratorHelperTest.testGetSqlName.158744.jpf.symbolic.output.json` | `assertEquals("TINYINT", GeneratorHelper.getSqlName(Types.TINYINT))` | S11 | **EXTRACTION FAILURE.** [S11] `getSqlName(type)` looks up a type-dependent SQL name, so changing generalized `type` can change the returned string even though this seed expects `TINYINT`.
| 159887 | 4998 | oracle expression not boolean | `projects/github_com_wokier_TEH` | `teh.utils.TEHUtils.hashCode` | `int` | 12 | `_ctor_object_zero_a`, `_ctor_object_one_d` | `data/reporeapers-rerun-v6/github_com_wokier_TEH/project-id-998/jpf-data/specs/teh.utils.TEHUtilsTest.testHashCodePonderateFields.159887.jpf.symbolic.output.json` | `assertThat(TEHUtils.hashCode(new SubPojo(1, 2)), is(not(TEHUtils.hashCode(new SubPojo(2, 1)))))` | S12 | **EXTRACTION FAILURE.** [S12] `TEHUtils.hashCode` hashes annotated fields `a` and `d`, so changing the generalized constructor values can change the returned integer.
| 190791 | 6242 | oracle expression not boolean | `projects/github_com_craigmingtaozhang_leetcode` | `org.mingtaoz.leetcode.map.LRUCache.get` | `int` | 1 | `key` | `data/reporeapers-rerun-v6/github_com_craigmingtaozhang_leetcode/project-id-1167/jpf-data/specs/org.mingtaoz.leetcode.map.LRUCacheTest.testLRUCache3.190791.jpf.symbolic.output.json` | `assertEquals(-1, sut.get(1))` | S16 | **EXTRACTION FAILURE.** [S16] `get(key)` checks the cache map and returns either the stored value or `-1`, so changing generalized `key` can change the returned integer.
| 192157 | 6362 | oracle expression not boolean | `projects/github_com_Hellblazer_DNS-Client` | `org.xbill.DNS.TTL.format` | `java.lang.String` | 9 | `ttl` | `data/reporeapers-rerun-v6/github_com_Hellblazer_DNS-Client/project-id-1168/jpf-data/specs/org.xbill.DNS.TTLTest.test_format.192157.jpf.symbolic.output.json` | `assertEquals("10H1M21S", TTL.format(((10 * H) + M) + 21))` | S17 | **EXTRACTION FAILURE.** [S17] `TTL.format(ttl)` computes unit components from `ttl` and appends them to a string, so changing generalized `ttl` can change the returned string.
| 86976 | 2860 | parameter coverage or other | `projects/github_com_vastus_Soko` | `vastus.sokoban.logic.Level.outOfBounds` | `boolean` | 0 | `_ctor_point_zero_x`, `_ctor_point_one_y` | `data/reporeapers-rerun-v6/github_com_vastus_Soko/project-id-570/jpf-data/specs/vastus.sokoban.logic.ILevelTest.outOfBoundsShouldReturnFalseWhenGivenPointInside2.86976.jpf.symbolic.output.json` | `assertFalse(level.outOfBounds(new Point(3, 0)))` | S3 | **EXTRACTION FAILURE.** [S3] `outOfBounds(point)` compares both coordinates with the level bounds, so changing generalized point coordinates can change the returned boolean.
| 109906 | 3265 | parameter coverage or other | `projects/github_com_runeflobakk_motif` | `no.motif.f.Predicate.$` | `boolean` | 0 | `value` | `data/reporeapers-rerun-v6/github_com_runeflobakk_motif/project-id-694/jpf-data/specs/no.motif.StringsTest.endsWithSuffix.109906.jpf.symbolic.output.json` | `assertThat(endsWith(null).$(""), is(false))` | S6 | **CORRECT.** [S6] `endsWith(null)` returns `Always.no()` before reading `value`, so the asserted false result is independent of the generalizable input.
| 180849 | 5357 | parameter coverage or other | `projects/github_com_TridentSDK_TridentSDK` | `net.tridentsdk.ui.chat.ChatComponent.hasExtra` | `boolean` | 0 | `recursive` | `data/reporeapers-rerun-v6/github_com_TridentSDK_TridentSDK/project-id-1118/jpf-data/specs/net.tridentsdk.ui.chat.ChatComponentTest.testGettersAndSetters.180849.jpf.symbolic.output.json` | `assertFalse(cc.hasExtra(childWith, true))` | S13 | **CORRECT.** [S13] `childWith` is stored in `with` rather than `extra`, so the recursive flag cannot find it and the asserted false result is independent of `recursive`.
| 190052 | 6088 | parameter coverage or other | `projects/github_com_ManfredTremmel_gwt-commons-validator` | `org.apache.commons.validator.routines.RegexValidator.isValid` | `boolean` | 0 | `value` | `data/reporeapers-rerun-v6/github_com_ManfredTremmel_gwt-commons-validator/project-id-1166/jpf-data/specs/org.apache.commons.validator.routines.RegexValidatorTest.testSingle.190052.jpf.symbolic.output.json` | `assertEquals("Insensitive isValid() valid", true, insensitive.isValid("AB-de-1"))` | S14 | **EXTRACTION FAILURE.** [S14] `RegexValidator.isValid(value)` executes the configured pattern against the supplied value, so changing generalized `value` can change the returned boolean.

## Source index used for the verdicts

All source files below were reachable in the project checkouts under the database `project.root_path` values. No sampled project required an unavailable source, so no case was guessed from metadata alone.

- **S1** original test `projects/github_com_almondtools_rexlex/src/test/java/com/almondtools/rexlex/automaton/LowByteCharClassMapperTest.java` and tested method `projects/github_com_almondtools_rexlex/src/main/java/com/almondtools/rexlex/automaton/LowByteCharClassMapper.java`.
- **S2** original test `projects/github_com_t3t5u_common-util/src/test/java/com/github/t3t5u/common/util/VersionTest.java` and tested method `projects/github_com_t3t5u_common-util/src/main/java/com/github/t3t5u/common/util/Version.java`.
- **S3** original test `projects/github_com_vastus_Soko/src/test/java/vastus/sokoban/logic/ILevelTest.java` and tested method `projects/github_com_vastus_Soko/src/main/java/vastus/sokoban/logic/Level.java`.
- **S4** original test `projects/github_com_spotify_sparkey-java/src/test/java/com/spotify/sparkey/UtilTest.java` and tested method `projects/github_com_spotify_sparkey-java/src/main/java/com/spotify/sparkey/Util.java`.
- **S5** original test `projects/github_com_bfh-evg_unicrypt/src/test/java/ch/bfh/unicrypt/helper/array/DenseArrayTest.java` and tested method `projects/github_com_bfh-evg_unicrypt/src/main/java/ch/bfh/unicrypt/helper/array/abstracts/AbstractImmutableArray.java`.
- **S6** original test `projects/github_com_runeflobakk_motif/src/test/java/no/motif/StringsTest.java`, tested interface `projects/github_com_runeflobakk_motif/src/main/java/no/motif/f/Predicate.java`, and predicate construction `projects/github_com_runeflobakk_motif/src/main/java/no/motif/Strings.java`.
- **S7** original test `projects/github_com_uaithne_uaithne-generator/src/test/java/org/uaithne/generator/processors/database/myBatis/MyBatisSqlQueryGeneratorTest.java` and tested method `projects/github_com_uaithne_uaithne-generator/src/main/java/org/uaithne/generator/processors/database/myBatis/MyBatisSqlQueryGenerator.java`.
- **S8** original test `projects/github_com_ppke-nlpg_purepos/src/test/java/hu/ppke/itk/nlpg/purepos/model/internal/NGramModelTest.java` and tested method `projects/github_com_ppke-nlpg_purepos/src/main/java/hu/ppke/itk/nlpg/purepos/model/internal/TrieNode.java`.
- **S9** original test `projects/github_com_joschi_JadConfig/src/test/java/com/github/joschi/jadconfig/util/DurationTest.java` and tested method `projects/github_com_joschi_JadConfig/src/main/java/com/github/joschi/jadconfig/util/Duration.java`.
- **S10** original test `projects/github_com_joschi_JadConfig/src/test/java/com/github/joschi/jadconfig/util/SizeTest.java` and tested method `projects/github_com_joschi_JadConfig/src/main/java/com/github/joschi/jadconfig/util/Size.java`.
- **S11** original test `projects/github_com_mwanji_migrate4j-maven/src/test/java/com/eroi/migrate/generators/GeneratorHelperTest.java` and tested method `projects/github_com_mwanji_migrate4j-maven/src/main/java/com/eroi/migrate/generators/GeneratorHelper.java`.
- **S12** original test `projects/github_com_wokier_TEH/src/test/java/teh/utils/TEHUtilsTest.java` and tested method `projects/github_com_wokier_TEH/src/main/java/teh/utils/TEHUtils.java`.
- **S13** original test `projects/github_com_TridentSDK_TridentSDK/src/test/java/net/tridentsdk/ui/chat/ChatComponentTest.java` and tested method `projects/github_com_TridentSDK_TridentSDK/src/main/java/net/tridentsdk/ui/chat/ChatComponent.java`.
- **S14** original test `projects/github_com_ManfredTremmel_gwt-commons-validator/src/test/java/org/apache/commons/validator/routines/RegexValidatorTest.java` and tested method `projects/github_com_ManfredTremmel_gwt-commons-validator/src/main/java/org/apache/commons/validator/routines/RegexValidator.java`.
- **S15** original test `projects/github_com_ManfredTremmel_gwt-commons-validator/src/test/java/org/apache/commons/validator/routines/UrlValidatorTest.java` and tested method `projects/github_com_ManfredTremmel_gwt-commons-validator/src/main/java/org/apache/commons/validator/routines/UrlValidator.java`.
- **S16** original test `projects/github_com_craigmingtaozhang_leetcode/src/test/java/org/mingtaoz/leetcode/map/LRUCacheTest.java` and tested method `projects/github_com_craigmingtaozhang_leetcode/src/main/java/org/mingtaoz/leetcode/map/LRUCache.java`.
- **S17** original test `projects/github_com_Hellblazer_DNS-Client/src/test/java/org/xbill/DNS/TTLTest.java` and tested method `projects/github_com_Hellblazer_DNS-Client/src/main/java/org/xbill/DNS/TTL.java`.

## Proximate-cause check

The tested-method return types persisted in the 20 database rows are `int`, `boolean`, and `java.lang.String`. No sampled row has a boxed primitive return type such as `java.lang.Integer`, so the output-boxing candidate is not supported by this sample. The `java.lang.String` returns are ordinary reference returns rather than boxed primitive returns.

A native or unmodeled boundary is supported as a proximate cause in 12 rows because `assertion.concretized_methods` is nonempty: 18334 records `java.lang.String.split`, 98155 records `java.lang.StringBuilder` operations, 109932 records `java.lang.String.charAt`, 133014 records `java.lang.Integer.valueOf`, 156752, 156822, and 157449 each record `java.lang.Long.valueOf`, 159887 records `java.lang.StringBuilder` operations, 190235 and 190268 each record `java.lang.StringBuffer.append`, 190791 records `java.lang.Integer.valueOf`, and 192157 records `java.lang.StringBuffer` or `java.lang.StringBuilder` operations. This telemetry identifies a boundary, but it does not always prove that the named call is inside the tested method. For example, `unsignedVLQSize` in S4 has no string-builder operation in its method source. The six input-dependent failures with zero concretization events show that concretization is not a necessary condition for the null model.

None of the sampled assertions is a void-return case. The persisted return types are all non-void, and each tested-method call source in the database identifies a return value that is then asserted, with the `result.toString()` wrapper in assertion 126870 inspected in S7. No sampled case is an assertion that compares a field instead of the tested method result. The `ChatComponent.hasExtra` case observes object state internally, but its boolean return is directly asserted and is classified correct because the selected component is not in the `extra` tree.

## Split and uncertainty

| stratum | sampled | correct null model | extraction failure |
|---|---:|---:|---:|
| oracle expression not boolean | 8 | 0 | 8 |
| concretization events present | 8 | 0 | 8 |
| parameter coverage or other | 4 | 2 | 2 |
| **total** | **20** | **2** | **18** |

The audited split is 18/20 extraction failures and 2/20 correct cases, or 90% versus 10%. A 95% Wilson interval for the unweighted 18/20 sample proportion is approximately 69.9% to 97.2%. Because the sample was deliberately stratified rather than drawn as 20 independent rows from the 4,649-row population, that interval is only a small-sample uncertainty caveat for this audit. It is not a corpus prevalence interval, and the table must not be extrapolated to claim that 90% of all `NULL_CONCRETE` attempts are extraction failures.

## Recommendation for the thesis

Retain the measured 13.6% numerator as the fraction of v6 generalization attempts for which this extractor persisted a non-null symbolic output model. Do not phrase it as evidence that only 13.6% of real Java tests have symbolic outputs. State that `NULL_CONCRETE` conflates genuinely input-independent outputs with input-dependent outputs whose single-path SPF return attribute was not serialized as a symbolic output model. The 20-row source audit found 18 clear input-dependent cases, including six without any recorded concretization event, so the 13.6% number should be framed as an observed pipeline yield and an extraction ceiling. A stronger claim about the corpus requires a broader independently randomized source audit or a second-path and return-attribute extraction measurement.
