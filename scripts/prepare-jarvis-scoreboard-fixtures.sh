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

# Curated allowlist of upstream numeric/char test classes promoted into the census fixture.
# Prioritizes JARVIS's own Table-2 source classes; refined by the mvn test-compile gate.
MATH_CENSUS_TESTS="src/test/java/org/apache/commons/math3/util/FastMathTest.java \
src/test/java/org/apache/commons/math3/util/PrecisionTest.java \
src/test/java/org/apache/commons/math3/util/ArithmeticUtilsTest.java \
src/test/java/org/apache/commons/math3/util/MathArraysTest.java \
src/test/java/org/apache/commons/math3/geometry/euclidean/oned/IntervalTest.java \
src/test/java/org/apache/commons/math3/analysis/polynomials/PolynomialFunctionTest.java \
src/test/java/org/apache/commons/math3/TestUtils.java"
LANG_CENSUS_TESTS="src/test/java/org/apache/commons/lang3/CharUtilsTest.java \
src/test/java/org/apache/commons/lang3/BooleanUtilsTest.java \
src/test/java/org/apache/commons/lang3/math/NumberUtilsTest.java"

prepare_census_fixture() {
  local repo_dir="$1"
  local dst="$2"
  local artifact="$3"
  shift 3
  rm -rf "$dst"
  mkdir -p "$dst/src"
  write_census_pom "$artifact" "$dst"
  cp -R "$repo_dir/src/main" "$dst/src/"
  echo "### $artifact"
  for t in "$@"; do
    if [[ -f "$repo_dir/$t" ]]; then
      copy_path "$repo_dir" "$t" "$dst"
      echo "- KEEP $t"
    else
      echo "- DROP(missing) $t"
    fi
  done
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
    echo "Pinned: commons-math $MATH_SHA ($MATH_TAG), commons-lang $LANG_SHA ($LANG_TAG)."
    echo
    prepare_census_fixture "$CACHE_DIR/commons-math" "$CENSUS_FIXTURE_DIR/commons-math-3.5-census" "commons-math-3.5-census" $MATH_CENSUS_TESTS
    prepare_census_fixture "$CACHE_DIR/commons-lang" "$CENSUS_FIXTURE_DIR/commons-lang-3.5-census" "commons-lang-3.5-census" $LANG_CENSUS_TESTS
  } | tee "$PROV"
  echo "Prepared census fixtures under $CENSUS_FIXTURE_DIR; provenance at $PROV"
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
