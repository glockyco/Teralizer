#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
BASE_DIR="$ROOT_DIR/data/jarvis-scoreboard"
CACHE_DIR="$BASE_DIR/source-cache"
FIXTURE_DIR="$BASE_DIR/fixtures"

MATH_REPO="https://github.com/apache/commons-math.git"
MATH_TAG="MATH_3_5"
MATH_SHA="b3c5dae8f253fcb4484e5cd3cc5662587803efc2"
LANG_REPO="https://github.com/apache/commons-lang.git"
LANG_TAG="LANG_3_5"
LANG_SHA="36f98d87b24c2f542b02abbf6ec1ee742f1b158b"

# Census population: the 12 Apache Commons projects of JARVIS's Table 1. Version rule:
# latest stable release as of 2016-10-13 (the LANG_3_5 release date). Exception: commons-text's
# first stable release is 1.0 (2017-03), the nearest release.
# Rows: <cache-name>|<repo-url>|<tag>|<commit-sha>|<fixture-artifact>
CENSUS_PROJECTS="\
commons-math|$MATH_REPO|$MATH_TAG|$MATH_SHA|commons-math-3.5-census
commons-lang|$LANG_REPO|$LANG_TAG|$LANG_SHA|commons-lang-3.5-census
commons-cli|https://github.com/apache/commons-cli.git|cli-1.3.1|41d3dbf00f3e2041f5e407b9b96d8f048ab388d9|commons-cli-1.3.1-census
commons-codec|https://github.com/apache/commons-codec.git|1.10|e9da3d16ae67f2940a0bbdf982ecec19a0481981|commons-codec-1.10-census
commons-collections|https://github.com/apache/commons-collections.git|collections-4.1|cb157163d7543f942a1391f3ef752ebea1e1b349|commons-collections-4.1-census
commons-configuration|https://github.com/apache/commons-configuration.git|CONFIGURATION_2_1|e814fe00bb51256386dd78f3f926aa30d8de6a7c|commons-configuration-2.1-census
commons-csv|https://github.com/apache/commons-csv.git|rel/commons-csv-1.4|640b2f52dca971a977f146a32568ee00d33b45be|commons-csv-1.4-census
commons-email|https://github.com/apache/commons-email.git|EMAIL_1_4|20ab7303a775342dc6ccfc8b0b7eb98b40738ec8|commons-email-1.4-census
commons-io|https://github.com/apache/commons-io.git|commons-io-2.5|4077158829de92987367d3149e4ba71356bb5390|commons-io-2.5-census
commons-jexl|https://github.com/apache/commons-jexl.git|COMMONS_JEXL_3_0|de6c4f3b00af4430f535fcb7833c480d9093fd35|commons-jexl-3.0-census
commons-pool|https://github.com/apache/commons-pool.git|POOL_2_4_2|a187fd494b1d7f486edccb3356a70dd7846445a0|commons-pool-2.4.2-census
commons-text|https://github.com/apache/commons-text.git|commons-text-1.0|e38039a3da2244741f5d33ab1b05bdee51c53c3e|commons-text-1.0-census"

require_git_pin() {
  local repo_dir="$1"
  local tag="$2"
  local sha="$3"
  local url="$4"

  if [[ ! -d "$repo_dir/.git" ]]; then
    rm -rf "$repo_dir"
    git clone --no-checkout "$url" "$repo_dir"
  fi

  git -C "$repo_dir" fetch --tags --force origin "$tag"
  git -C "$repo_dir" checkout --force "$tag"
  local actual
  actual=$(git -C "$repo_dir" rev-parse HEAD)
  if [[ "$actual" != "$sha" ]]; then
    echo "Pin mismatch for $repo_dir: expected $sha, got $actual" >&2
    exit 1
  fi
}

copy_path() {
  local repo_dir="$1"
  local src="$2"
  local dst_root="$3"
  local dst="$dst_root/$src"

  mkdir -p "$(dirname "$dst")"
  cp "$repo_dir/$src" "$dst"
}

write_pom() {
  local artifact_id="$1"
  local dst="$2"

  cat > "$dst/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>teralizer.jarvis</groupId>
  <artifactId>$artifact_id</artifactId>
  <version>1.0-SNAPSHOT</version>
  <properties>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>4.12</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
POM
}

write_census_pom() {
  local artifact_id="$1"
  local dst="$2"
  local deps="$3"
  cat > "$dst/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>teralizer.jarvis</groupId>
  <artifactId>$artifact_id</artifactId>
  <version>1.0-SNAPSHOT</version>
  <properties>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
$deps
  </dependencies>
</project>
POM
}

# The census fixture carries the full upstream test suite. The pipeline's filter funnel,
# not fixture curation, decides what generalizes. The POM is synthetic (upstream-era build
# plugins would fight the pipeline's managed build) with the dependency closure extracted
# verbatim from the pinned upstream POM.
prepare_census_fixture() {
  local repo_dir="$1"
  local dst="$2"
  local artifact="$3"
  local deps
  deps=$(python3 "$ROOT_DIR/scripts/lib/extract-pom-deps.py" "$repo_dir/pom.xml")
  rm -rf "$dst"
  mkdir -p "$dst/src"
  write_census_pom "$artifact" "$dst" "$deps"
  cp -R "$repo_dir/src/main" "$dst/src/"
  cp -R "$repo_dir/src/test" "$dst/src/"
  materialize_generated_sources "$repo_dir" "$dst"
  prune_benchmarks "$dst"
  local test_files
  test_files=$(find "$dst/src/test" -name '*.java' | wc -l | tr -d ' ')
  echo "- full upstream src/test (sources and resources): $test_files java files"
}

# Projects with source generation (javacc parsers in configuration and jexl) need the
# generated classes in the fixture, since the synthetic POM carries no build plugins.
# Generate once via the upstream POM and copy the results into src/main/java.
materialize_generated_sources() {
  local repo_dir="$1"
  local dst="$2"
  grep -q 'javacc-maven-plugin' "$repo_dir/pom.xml" || return 0
  echo "- generating sources via the upstream POM (javacc)"
  if ! (cd "$repo_dir" && mvn -q generate-sources); then
    echo "generate-sources failed in $repo_dir" >&2
    exit 1
  fi
  local gen_root="$repo_dir/target/generated-sources"
  local gen_dir
  for gen_dir in "$gen_root"/*/; do
    [[ -d "$gen_dir" ]] || continue
    find "$gen_dir" -name '*.java' | while read -r f; do
      rel="${f#"$gen_dir"}"
      mkdir -p "$dst/src/main/java/$(dirname "$rel")"
      # Generated parsers may duplicate hand-maintained classes. Keep the upstream original.
      [[ -f "$dst/src/main/java/$rel" ]] || cp "$f" "$dst/src/main/java/$rel"
    done
  done
}

# JMH benchmarks under src/test are not JUnit tests. Upstream compiles them only under a
# dedicated benchmark profile whose dependencies are not part of the test-suite closure.
prune_benchmarks() {
  local dst="$1"
  local f
  while read -r f; do
    echo "- pruned JMH benchmark: ${f#"$dst/"}"
    rm "$f"
  done < <(grep -rl 'org\.openjdk\.jmh' "$dst/src/test" --include='*.java' 2>/dev/null || true)
}

# A missing test-scope dependency only surfaces at compile time. Gate every fixture.
census_compile_gate() {
  local dst="$1"
  echo "==> mvn test-compile gate: $dst"
  if ! (cd "$dst" && mvn -q test-compile); then
    echo "Census fixture failed the test-compile gate: $dst" >&2
    exit 1
  fi
}

write_math_scorecard_tests() {
  local dst="$1/src/test/java/org/apache/commons/math3/jarvis"
  mkdir -p "$dst"

  cat > "$dst/JarvisMathScorecardTest.java" <<'JAVA'
package org.apache.commons.math3.jarvis;

import org.apache.commons.math3.analysis.function.Abs;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.geometry.euclidean.oned.Interval;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JarvisMathScorecardTest {
  @Test
  public void minDouble() {
    assertEquals(1.0, FastMath.min(1.0, 2.0), 0.0);
  }

  @Test
  public void maxDouble() {
    assertEquals(2.0, FastMath.max(1.0, 2.0), 0.0);
  }

  @Test
  public void toIntExact() {
    assertEquals(7, FastMath.toIntExact(7L));
  }

  @Test
  public void intervalGetSize() {
    assertEquals(9.0, new Interval(1.0, 10.0).getSize(), 0.0);
  }

  @Test
  public void polynomialConstant() {
    assertEquals(2.0, new PolynomialFunction(new double[] {2.0}).value(7.0), 0.0);
  }

  @Test
  public void polynomialDerivative() {
    PolynomialFunction polynomial = new PolynomialFunction(new double[] {1.0, 2.0, 3.0});
    assertEquals(8.0, polynomial.polynomialDerivative().value(1.0), 0.0);
  }

  @Test
  public void polynomialLinear() {
    assertEquals(5.0, new PolynomialFunction(new double[] {1.0, 2.0}).value(2.0), 0.0);
  }

  @Test
  public void precisionEquals() {
    assertTrue(Precision.equals(1.0, 1.0 + 1e-12, 1e-9));
    assertFalse(Precision.equals(1.0, 1.1, 1e-9));
  }

  @Test
  public void precisionEqualsMaxUlps() {
    // Extra diagnostic fixture for the raw-bits/ULP investigation — not a JARVIS Table-2 case.
    // The Table-2 PrecisionTest row is the eps overload (double, double, double);
    // this is the maxUlps overload (double, double, int), parameter space double^2 + int.
    assertTrue(Precision.equals(1.0, 1.0, 1));
    assertFalse(Precision.equals(1.0, 1.1, 1));
  }

  @Test
  public void absValue() {
    assertEquals(3.0, new Abs().value(-3.0), 0.0);
  }
}
JAVA
}

write_lang_scorecard_tests() {
  local dst="$1/src/test/java/org/apache/commons/lang3/jarvis"
  mkdir -p "$dst"

  cat > "$dst/JarvisLangScorecardTest.java" <<'JAVA'
package org.apache.commons.lang3.jarvis;

import org.apache.commons.lang3.CharUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JarvisLangScorecardTest {
  @Test
  public void isAscii() {
    assertTrue(CharUtils.isAscii('A'));
    assertFalse(CharUtils.isAscii((char) 128));
  }

  @Test
  public void isAsciiPrintable() {
    assertTrue(CharUtils.isAsciiPrintable('A'));
    assertFalse(CharUtils.isAsciiPrintable('\n'));
  }
}
JAVA
}

prepare_math_fixture() {
  local repo_dir="$CACHE_DIR/commons-math"
  local dst="$FIXTURE_DIR/commons-math-3.5"
  rm -rf "$dst"
  mkdir -p "$dst/src"
  write_pom "commons-math-3.5-scoreboard" "$dst"
  cp -R "$repo_dir/src/main" "$dst/src/"
  write_math_scorecard_tests "$dst"
}

prepare_lang_fixture() {
  local repo_dir="$CACHE_DIR/commons-lang"
  local dst="$FIXTURE_DIR/commons-lang-3.5"
  rm -rf "$dst"
  mkdir -p "$dst/src"
  write_pom "commons-lang-3.5-scoreboard" "$dst"
  cp -R "$repo_dir/src/main" "$dst/src/"
  write_lang_scorecard_tests "$dst"
}

mkdir -p "$CACHE_DIR" "$FIXTURE_DIR"
require_git_pin "$CACHE_DIR/commons-math" "$MATH_TAG" "$MATH_SHA" "$MATH_REPO"
require_git_pin "$CACHE_DIR/commons-lang" "$LANG_TAG" "$LANG_SHA" "$LANG_REPO"

if [[ "${1:-}" == "--census" ]]; then
  CENSUS_FIXTURE_DIR="$ROOT_DIR/data/jarvis-census/fixtures"
  mkdir -p "$CENSUS_FIXTURE_DIR"
  PROV="$ROOT_DIR/data/jarvis-census/PROVENANCE.md"
  {
    echo "# Census fixture provenance"
    echo
    echo "Population: the 12 Apache Commons projects of JARVIS's Table 1, pinned to the"
    echo "latest stable release as of 2016-10-13 (LANG_3_5's release date); commons-text has"
    echo "no release in the window, so it pins the first stable 1.0 (2017-03). Each fixture"
    echo "carries the full upstream src/test and the dependency closure of its pinned POM."
    echo
    while IFS='|' read -r name url tag sha artifact; do
      require_git_pin "$CACHE_DIR/$name" "$tag" "$sha" "$url" >/dev/null
      echo "### $artifact"
      echo "- pin: $url $tag $sha"
      prepare_census_fixture "$CACHE_DIR/$name" "$CENSUS_FIXTURE_DIR/$artifact" "$artifact"
    done <<< "$CENSUS_PROJECTS"
  } | tee "$PROV"
  while IFS='|' read -r name url tag sha artifact; do
    census_compile_gate "$CENSUS_FIXTURE_DIR/$artifact"
  done <<< "$CENSUS_PROJECTS"
  echo "Prepared census fixtures under $CENSUS_FIXTURE_DIR. Provenance at $PROV."
  exit 0
fi
prepare_math_fixture
prepare_lang_fixture

cat > "$BASE_DIR/PROVENANCE.md" <<PROVENANCE
# JARVIS scoreboard fixture provenance

Generated by scripts/prepare-jarvis-scoreboard-fixtures.sh.

## Execution inputs

- commons-math: $MATH_REPO, tag $MATH_TAG, commit $MATH_SHA, Apache-2.0. Fixture root: data/jarvis-scoreboard/fixtures/commons-math-3.5.
- commons-lang: $LANG_REPO, tag $LANG_TAG, commit $LANG_SHA, Apache-2.0. Fixture root: data/jarvis-scoreboard/fixtures/commons-lang-3.5.

## Reference-only sources

- spf-eval/jarvis-spike outputs are reference evidence only; they are not copied into the Teralizer scorecard fixture.
PROVENANCE

echo "Prepared JARVIS scoreboard fixtures under $FIXTURE_DIR"
