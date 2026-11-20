import json
from pathlib import Path

from teralizer.release import declaration, inventory, plan


def _write(path: Path, content: str = "x\n") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)


def _sources(tmp_path: Path) -> plan.Sources:
    source = tmp_path / "source"
    _write(source / "README.md")
    _write(source / "jpf-symbc/build.gradle")
    _write(source / "jpf-symbc/jpf-core/build.gradle")
    _write(source / "projects/EqBench/LICENSE.md")
    _write(source / "src/main/resources/evosuite/evosuite-1.2.0.jar")
    _write(source / "analysis/output/original/figures/a.pdf")

    evidence = tmp_path / "evidence"
    _write(evidence / "data/reporeapers-rerun-v7/status.tsv")
    _write(evidence / "data/jarvis-census/fixtures/p/1.values.tsv")

    package = tmp_path / "package"
    corpora = []
    for corpus in ("controlled", "real-world", "jarvis-scenarios", "jarvis-benchmark"):
        _write(package / f"{corpus}.dump")
        corpora.append({"corpus_id": corpus, "dump": {"path": f"{corpus}.dump"}})
    _write(package / "manifest.json", json.dumps({"corpora": corpora}))

    checkout = tmp_path / "checkout"
    for name in plan.CONTROLLED_PROJECTS:
        _write(checkout / "projects" / name / "pom.xml")
        _write(checkout / "projects" / name / "src/test/FooTest.java")
        _write(
            checkout
            / "projects"
            / name
            / "src/test/_FooTest_Generalized_bar_1_Test.java"
        )
        _write(checkout / "projects" / name / "target/classes/Foo.class")
        _write(checkout / "projects" / name / "pom.teralizer.xml")
    for name in plan.SCOREBOARD_FIXTURES:
        _write(checkout / "data/jarvis-scoreboard/fixtures" / name / "pom.xml")
        _write(checkout / "data/jarvis-scoreboard/fixtures" / name / "src/Main.java")
        _write(
            checkout
            / "data/jarvis-scoreboard/fixtures"
            / name
            / "data/run/1.values.tsv"
        )
    _write(
        checkout / "data/jarvis-census/fixtures/commons-cli-2017-02-01-census/pom.xml"
    )
    _write(checkout / "data/jarvis-census/fixtures/commons-cli-1.3.1-census/pom.xml")
    return plan.Sources(source, evidence, package, checkout)


def test_plan_assigns_each_file_to_its_component_and_input(tmp_path: Path):
    plans = {
        item.component: item
        for item in plan.build(declaration.load(), inventory.load(), _sources(tmp_path))
    }

    def names(component: str) -> set[str]:
        root = plans[component].root + "/"
        return {entry.name.removeprefix(root) for entry in plans[component].entries}

    assert "projects/EqBench/LICENSE.md" in names("projects-controlled")
    assert "projects/EqBench/LICENSE.md" not in names("core")
    assert {"jpf-symbc", "jpf-core", "evosuite-jar", "reference-figures"} <= plans[
        "core"
    ].inputs
    assert "replication/corpus-manifest.json" in names("core")
    assert names("corpus-real-world") == {"real-world.dump"}
    controlled = names("projects-controlled")
    assert "projects/commons-utils/src/test/FooTest.java" in controlled
    assert not any("Generalized" in name or "target/" in name for name in controlled)
    assert not any(name.endswith("pom.teralizer.xml") for name in controlled)
    scoreboard = names("projects-jarvis-scoreboard")
    assert not any("/data/run/" in name for name in scoreboard)
    census = names("projects-jarvis-census")
    assert census == {
        "data/jarvis-census/fixtures/commons-cli-2017-02-01-census/pom.xml"
    }


def test_validation_reports_payload_inputs_without_members(tmp_path: Path):
    declared = inventory.load()
    release = declaration.load()
    plans = plan.build(release, declared, _sources(tmp_path))
    required = [
        c.id for c in release.components if not c.id.startswith("projects-real-world")
    ]

    problems = plan.validate(plans, declared, required=required)

    assert "payload input jarvis-run-ledgers contributed no member to core" in problems
    assert not any(
        "owned by" in problem or "outside" in problem for problem in problems
    )
