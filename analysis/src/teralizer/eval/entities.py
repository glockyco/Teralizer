"""Shared report entities and target-specific names."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Entity:
    key: str
    plain: str
    latex: str
    csv: str | None = None
    csv_aliases: tuple[str, ...] = ()


@dataclass(frozen=True)
class EntityRef:
    key: str


_ENTITIES = {
    entity.key: entity
    for entity in (
        Entity("variant.all", "All", r"\VariantAll{}", "All"),
        Entity("variant.baseline", "Baseline", r"\VariantBaseline{}", "BASELINE"),
        Entity(
            "variant.naive_a", "Naive (10 tries)", r"\VariantNaiveA{}", "NAIVE_10_TRIES"
        ),
        Entity(
            "variant.naive_b", "Naive (50 tries)", r"\VariantNaiveB{}", "NAIVE_50_TRIES"
        ),
        Entity(
            "variant.naive_c",
            "Naive (200 tries)",
            r"\VariantNaiveC{}",
            "NAIVE_200_TRIES",
        ),
        Entity("variant.improved", "Improved", r"\VariantImproved{}", "IMPROVED"),
        Entity(
            "variant.improved_a",
            "Improved (10 tries)",
            r"\VariantImprovedA{}",
            "IMPROVED_10_TRIES",
        ),
        Entity(
            "variant.improved_b",
            "Improved (50 tries)",
            r"\VariantImprovedB{}",
            "IMPROVED_50_TRIES",
        ),
        Entity(
            "variant.improved_c",
            "Improved (200 tries)",
            r"\VariantImprovedC{}",
            "IMPROVED_200_TRIES",
        ),
        Entity("variant.original", "Original", r"\VariantOriginal{}", "ORIGINAL"),
        Entity("variant.initial", "Initial", r"\VariantInitial{}", "INITIAL"),
        Entity("budget.improved_100", "100 tries", "100 tries", "IMPROVED_100_TRIES"),
        Entity("budget.improved_200", "200 tries", "200 tries", "IMPROVED_200_TRIES"),
        Entity(
            "budget.improved_1000", "1,000 tries", "1,000 tries", "IMPROVED_1000_TRIES"
        ),
        Entity(
            "scenario.char_utils_ascii",
            "isAscii",
            r"\texttt{isAscii}",
            "CharUtilsTest::isAscii",
        ),
        Entity(
            "scenario.char_utils_printable",
            "isPrintable",
            r"\texttt{isPrintable}",
            "CharUtilsTest::isPrintable",
        ),
        Entity(
            "scenario.fast_math_min_max",
            "testMinMaxDouble",
            r"\texttt{testMinMaxDouble}",
            "FastMathTest::testMinMaxDouble",
        ),
        Entity(
            "scenario.fast_math_int",
            "toIntExact",
            r"\texttt{toIntExact}",
            "FastMathTest::toIntExact",
        ),
        Entity(
            "scenario.interval",
            "IntervalTest",
            r"\texttt{IntervalTest}",
            "IntervalTest",
        ),
        Entity(
            "scenario.polynomial_constants",
            "testConstants",
            r"\texttt{testConstants}",
            "PolynomialFunctionTest::testConstants",
        ),
        Entity(
            "scenario.polynomial_derivative",
            "testfirstDerivativeComparison",
            r"\texttt{testfirstDerivativeComparison}",
            "PolynomialFunctionTest::testfirstDerivativeComparison",
        ),
        Entity(
            "scenario.polynomial_linear",
            "testLinear",
            r"\texttt{testLinear}",
            "PolynomialFunctionTest::testLinear",
        ),
        Entity(
            "scenario.precision",
            "PrecisionTest",
            r"\texttt{PrecisionTest}",
            "PrecisionTest",
        ),
        Entity(
            "scenario.univariate_abs",
            "testAbs",
            r"\texttt{testAbs}",
            "UnivariateFunctionTest::testAbs",
        ),
        Entity("tool.teralizer", "Teralizer", r"\ToolTeralizer{}"),
        Entity("tool.evosuite", "EvoSuite", r"\ToolEvoSuite{}"),
        Entity("tool.pit", "PIT", r"\ToolPit{}"),
        Entity("tool.jpf", "JPF", r"\ToolJPF{}"),
        Entity("tool.spf", "SPF", r"\ToolSPF{}"),
        Entity("tool.jacoco", "JaCoCo", r"\ToolJacoco{}"),
        Entity("dataset.commons", "Commons", r"\DatasetsCommons{}"),
        Entity("dataset.eqbench", "EqBench", r"\DatasetEqBench{}"),
        Entity("dataset.eqbench_es", "EqBench-ES", r"\DatasetsEqBenchEs{}"),
        Entity(
            "dataset.eqbench_a",
            "EqBench-ES (1 s)",
            r"\DatasetEqBenchA{}",
            "eqbench-es-default-1s",
            ("eqbench-es-1s",),
        ),
        Entity(
            "dataset.eqbench_b",
            "EqBench-ES (10 s)",
            r"\DatasetEqBenchB{}",
            "eqbench-es-default-10s",
            ("eqbench-es-10s",),
        ),
        Entity(
            "dataset.eqbench_c",
            "EqBench-ES (60 s)",
            r"\DatasetEqBenchC{}",
            "eqbench-es-default-60s",
            ("eqbench-es-60s",),
        ),
        Entity(
            "dataset.commons_a",
            "Commons-ES (1 s)",
            r"\DatasetCommonsA{}",
            "commons-utils-es-default-1s",
            ("commons-utils-es-1s",),
        ),
        Entity(
            "dataset.commons_b",
            "Commons-ES (10 s)",
            r"\DatasetCommonsB{}",
            "commons-utils-es-default-10s",
            ("commons-utils-es-10s",),
        ),
        Entity(
            "dataset.commons_c",
            "Commons-ES (60 s)",
            r"\DatasetCommonsC{}",
            "commons-utils-es-default-60s",
            ("commons-utils-es-60s",),
        ),
        Entity(
            "dataset.commons_dev",
            "Commons Utils",
            r"\DatasetCommonsDev{}",
            "commons-utils",
            ("commons-utils-dev",),
        ),
        Entity(
            "dataset.repo_reapers",
            "RepoReapers",
            r"\DatasetRepoReapers{}",
            "repo-reapers",
        ),
        Entity(
            "dataset.repo_reapers_total",
            "RepoReapers (total)",
            r"\DatasetRepoReapers{} (total)",
            "repo-reapers (total)",
        ),
        Entity(
            "dataset.repo_reapers_mean",
            "RepoReapers (mean)",
            r"\DatasetRepoReapers{} (mean)",
            "repo-reapers (mean)",
        ),
        Entity(
            "dataset.repo_reapers_median",
            "RepoReapers (median)",
            r"\DatasetRepoReapers{} (median)",
            "repo-reapers (median)",
        ),
    )
}


def ref(key: str) -> EntityRef:
    if key not in _ENTITIES:
        raise KeyError(f"unknown entity: {key}")
    return EntityRef(key)


def ref_for_csv(prefix: str, value: object) -> EntityRef:
    name = str(value)
    for entity in _ENTITIES.values():
        if entity.key.startswith(f"{prefix}.") and (
            entity.csv == name or name in entity.csv_aliases
        ):
            return EntityRef(entity.key)
    raise KeyError(f"unknown {prefix} entity: {name}")


def variant_ref(value: object) -> EntityRef:
    return ref_for_csv("variant", value)


def render(reference: EntityRef, target: str) -> str:
    entity = _ENTITIES.get(reference.key)
    if entity is None:
        raise KeyError(f"unknown entity: {reference.key}")
    if target == "latex":
        return entity.latex
    if target == "markdown":
        return entity.plain
    if target == "csv":
        return entity.plain if entity.csv is None else entity.csv
    raise KeyError(f"unknown entity target: {target}")


def substitute(text: str, target: str) -> str:
    """Replace only explicit ``{entity.<key>}`` placeholders."""
    out = text
    for key in _ENTITIES:
        out = out.replace("{entity." + key + "}", render(EntityRef(key), target))
    return out
