#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
BASE_DIR="$ROOT_DIR/data/jarvis-scoreboard"
CACHE_DIR="$BASE_DIR/source-cache"
FIXTURE_DIR="$BASE_DIR/fixtures"

CUTOFF="2017-02-01T12:02:01Z"
MATH_REPO="https://github.com/apache/commons-math.git"
MATH_REF="origin/master before $CUTOFF"
MATH_SHA="657b1b49da5ea1593dd7f950eae99a88a8ada87a"
LANG_REPO="https://github.com/apache/commons-lang.git"
LANG_REF="PR #230 base before the JARVIS-discovered fix"
LANG_SHA="857e0de49293083aae6d3e6c6b76ec0755b1d0fa"

# Census population: the 12 Apache Commons projects of JARVIS's Table 1. The paper does
# not publish its checkout revisions. The Commons Lang fix submitted from the JARVIS
# evaluation on 2017-02-01 provides the reconstruction cutoff. Each row pins the last
# default-branch commit before that instant, except commons-lang, which pins the PR base.
# Rows: <cache-name>|<repo-url>|<selection-basis>|<commit-sha>|<fixture-artifact>
CENSUS_PROJECTS="\
commons-math|$MATH_REPO|$MATH_REF|$MATH_SHA|commons-math-2017-02-01-census
commons-lang|$LANG_REPO|$LANG_REF|$LANG_SHA|commons-lang-2017-02-01-census
commons-cli|https://github.com/apache/commons-cli.git|origin/master before $CUTOFF|b486fbd33d9350b5faf27662f18841ae89128910|commons-cli-2017-02-01-census
commons-codec|https://github.com/apache/commons-codec.git|origin/master before $CUTOFF|1a4d9cc2572d220664f1b7c377cd318cd253052e|commons-codec-2017-02-01-census
commons-collections|https://github.com/apache/commons-collections.git|origin/master before $CUTOFF|3c1867e231093319b1bbbe5b0362d4063517cf24|commons-collections-2017-02-01-census
commons-configuration|https://github.com/apache/commons-configuration.git|origin/master before $CUTOFF|98ae727a34e9460ef625bc072cbbd7f2b0471127|commons-configuration-2017-02-01-census
commons-csv|https://github.com/apache/commons-csv.git|origin/master before $CUTOFF|b1e5d93a2f5309102bf09d4e2b8b9eb15ef3fc8b|commons-csv-2017-02-01-census
commons-email|https://github.com/apache/commons-email.git|origin/master before $CUTOFF|edcd565ab2e77f0ac8c95fa7200ff1a1be7f8e0c|commons-email-2017-02-01-census
commons-io|https://github.com/apache/commons-io.git|origin/master before $CUTOFF|54631643211e2dc29beaecc30e4eedf2928738d7|commons-io-2017-02-01-census
commons-jexl|https://github.com/apache/commons-jexl.git|origin/master before $CUTOFF|b212a407b74687c1f861dd0cb5cff90b9da32082|commons-jexl-2017-02-01-census
commons-pool|https://github.com/apache/commons-pool.git|origin/master before $CUTOFF|6a088d1b8a4aa343362cdf4e749b7c5fca38971b|commons-pool-2017-02-01-census
commons-text|https://github.com/apache/commons-text.git|origin/master before $CUTOFF|d24d8b257135e06fe89d6569a897c44e4466281a|commons-text-2017-02-01-census"

require_git_pin() {
  local repo_dir="$1"
  local ref="$2"
  local sha="$3"
  local url="$4"

  if [[ ! -d "$repo_dir/.git" ]]; then
    rm -rf "$repo_dir"
    git clone --no-checkout "$url" "$repo_dir"
  fi

  git -C "$repo_dir" fetch --tags --force origin
  git -C "$repo_dir" checkout --force --detach "$sha"
  local actual
  actual=$(git -C "$repo_dir" rev-parse HEAD)
  if [[ "$actual" != "$sha" ]]; then
    echo "Pin mismatch for $repo_dir ($ref): expected $sha, got $actual" >&2
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
  if [[ "$artifact" == "commons-math-2017-02-01-census" ]]; then
    # Teralizer expands testQuinticFunction's large coefficient table into one
    # generated method that exceeds the JVM's 64 KiB bytecode limit. Exclude the
    # containing source class at fixture construction so the remaining generated
    # suite can be compiled and executed; record the exclusion in PROVENANCE.md.
    rm "$dst/src/test/java/org/apache/commons/math4/analysis/integration/SimpsonIntegratorTest.java"
    echo "- excluded SimpsonIntegratorTest: generated testQuinticFunction exceeds the JVM method-size limit"
  fi
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
  local dst="$1/src/test/java/org/apache/commons/math4/jarvis"
  mkdir -p "$dst"

  cat > "$dst/JarvisMathScorecardTest.java" <<'JAVA'
package org.apache.commons.math4.jarvis;

import org.apache.commons.math4.analysis.function.Abs;
import org.apache.commons.math4.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math4.geometry.euclidean.oned.Interval;
import org.apache.commons.math4.util.FastMath;
import org.apache.commons.math4.util.Precision;
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
  local dst="$FIXTURE_DIR/commons-math-2017-02-01"
  local deps
  deps=$(python3 "$ROOT_DIR/scripts/lib/extract-pom-deps.py" "$repo_dir/pom.xml")
  rm -rf "$dst"
  mkdir -p "$dst/src"
  write_census_pom "commons-math-2017-02-01-scoreboard" "$dst" "$deps"
  cp -R "$repo_dir/src/main" "$dst/src/"
  write_math_scorecard_tests "$dst"
}

prepare_lang_fixture() {
  local repo_dir="$CACHE_DIR/commons-lang"
  local dst="$FIXTURE_DIR/commons-lang-2017-02-01"
  local deps
  deps=$(python3 "$ROOT_DIR/scripts/lib/extract-pom-deps.py" "$repo_dir/pom.xml")
  rm -rf "$dst"
  mkdir -p "$dst/src"
  write_census_pom "commons-lang-2017-02-01-scoreboard" "$dst" "$deps"
  cp -R "$repo_dir/src/main" "$dst/src/"
  write_lang_scorecard_tests "$dst"
}

mkdir -p "$CACHE_DIR" "$FIXTURE_DIR"
require_git_pin "$CACHE_DIR/commons-math" "$MATH_REF" "$MATH_SHA" "$MATH_REPO"
require_git_pin "$CACHE_DIR/commons-lang" "$LANG_REF" "$LANG_SHA" "$LANG_REPO"

if [[ "${1:-}" == "--census" ]]; then
  CENSUS_FIXTURE_DIR="$ROOT_DIR/data/jarvis-census/fixtures"
  mkdir -p "$CENSUS_FIXTURE_DIR"
  PROV="$ROOT_DIR/data/jarvis-census/PROVENANCE.md"
  {
    echo "# Census fixture provenance"
    echo
    echo "Population: the 12 Apache Commons projects of JARVIS's Table 1. The paper does"
    echo "not identify checkout revisions. These fixtures use the last default-branch commit"
    echo "before $CUTOFF; commons-lang instead uses PR #230's base immediately before the"
    echo "JARVIS-discovered test fix. Each fixture carries the full upstream src/test and"
    echo "the dependency closure of its pinned POM."
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

- commons-math: $MATH_REPO, $MATH_REF, commit $MATH_SHA, Apache-2.0. Fixture root: data/jarvis-scoreboard/fixtures/commons-math-2017-02-01.
- commons-lang: $LANG_REPO, $LANG_REF, commit $LANG_SHA, Apache-2.0. Fixture root: data/jarvis-scoreboard/fixtures/commons-lang-2017-02-01.

## Reference-only sources

- spf-eval/jarvis-spike outputs are reference evidence only; they are not copied into the Teralizer scorecard fixture.
PROVENANCE

echo "Prepared JARVIS scoreboard fixtures under $FIXTURE_DIR"
