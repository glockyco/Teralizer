## Context

See `proposal.md` for motivation. RQ6 already owns the real-world generalization funnel, typed metrics, aggregate macros, and provenance. The controlled report exposes generated-test filtering and generation-task failures, but its historical `generalization.is_included` value combines conditions and cannot define the shared filtering boundary by itself.

The retained controlled counts are 11,597 retained and 2,207 excluded among 13,804 generalized tests with a filtering result. The retained RepoReapers counts are 1,615 retained and 420 excluded among 2,035 generalized tests with a filtering result. These values are acceptance evidence, not implementation literals. Report construction must derive them from the declared corpus snapshots.

The accepted headline contract rejects 83.8% versus 30.2% as a generalization-success comparison and 84.0% versus the obsolete 78.5% value as a cross-condition effect. This change preserves that prohibition. It introduces a different, shared filtering boundary and keeps it inside RQ6 mechanism evidence.

## Goals / Non-Goals

**Goals:**

- Give both corpus-local observations one explicit entity type and filtering boundary.
- Preserve separate inputs, populations, denominators, and provenance.
- Prove conservation and population compatibility before report publication.
- Publish stable metrics and aggregate macros that the thesis can cite without copying arithmetic.
- Hand off the semantic finding while deferring final reader-facing wording.

**Non-Goals:**

- Reconstruct a current-policy controlled widening verdict.
- Compare all generalization attempts or end-to-end project applicability.
- Produce a combined score, effect size, significance test, causal ranking, or paired-project analysis.
- Add the result to the thesis-wide headline dimensions.
- Edit thesis prose in this producer change.

## Decisions

### 1. Extend RQ6 with one declared controlled input

The registered `rq6` report will keep the real-world corpus as its primary input and add the controlled corpus snapshot as a separate declared input. The comparison belongs to RQ6 because it explains where the real-world applicability loss does and does not occur. A new standalone report would duplicate RQ6 ownership, registration, macro selection, and handoff logic.

Both queries remain corpus-local. The implementation will not join database records across corpora or manufacture a shared project identity.

### 2. Define the denominator as generalized tests with a filtering result

For each corpus, the filtering total is the number of `Generalization` entities for which the source evidence proves a retained or excluded filtering result. Retained and excluded are exhaustive and disjoint within that denominator:

`filtering_total = retained + excluded`

A generated test that fails before a filtering result exists is outside this denominator. It remains represented by its existing failure evidence and is not reclassified for this comparison.

On both sides, the query will use the generalization-level `NonPassingTestFilter` result that decides whether the generated test passes filtering. Other test- and assertion-level filters do not define this denominator. The controlled query will derive the partition from that explicit filter result and generation-task evidence. It will use `generalization.is_included` only as a consistency check where appropriate, never as the sole boundary predicate. On the RepoReapers side, the accepted typed generalization relation already distinguishes this filtering result from earlier creation failures and later validation, reduction, and final-use states.

### 3. Publish eight stable scalar metrics and no combined metric

RQ6 will publish these exact keys:

- `rq6.filtering.controlled.total`
- `rq6.filtering.controlled.retained`
- `rq6.filtering.controlled.excluded`
- `rq6.filtering.controlled.retained_pct`
- `rq6.filtering.realworld.total`
- `rq6.filtering.realworld.retained`
- `rq6.filtering.realworld.excluded`
- `rq6.filtering.realworld.retained_pct`

Count metrics use `Generalization` populations bound to their respective input roles. Each percentage names its corpus-local `total` metric as denominator and its corresponding `retained` metric as numerator. No metric stores the percentage-point difference or represents the two rows as one population.

The aggregate macro renderer will derive one macro from each stable key. A compact RQ6 table may render the two corpus rows from the same metrics; it must not recompute values independently.

### 4. Fail report construction on incomplete or incompatible evidence

Before returning the report, validation will require:

- one supported controlled variant and one supported RepoReapers variant;
- unique entity identities within each corpus-local relation;
- disjoint retained and excluded sets;
- retained plus excluded equal to the filtering total;
- count and percentage operands share corpus, input role, and entity level;
- no missing or unknown filtering result inside either published denominator; and
- the real-world population remains consistent with the existing RQ6 funnel.

An input that cannot prove the controlled filtering partition fails by naming the missing or contradictory evidence. It does not fall back to `is_included`, a copied table value, or a partial result.

### 5. Keep the comparison out of headline selection

The eight keys are approved RQ6 supporting evidence, not members of the headline key set. The headline publication integration test will assert their absence from that set while the complete aggregate macro artifact still contains them for citation.

The producer handoff will state only the approved semantic frame: filtering retains a similar proportion of controlled and RepoReapers generalized tests that reach filtering, so filtering is not the main source of the real-world applicability loss; the larger loss occurs during generalized test creation. It will also record the prohibited interpretations and require a later thesis semantic review for exact wording and placement.

### 6. Verify behavior at query, report, publication, and complete-run levels

Focused query fixtures will cover retained, excluded, pre-filter failure, duplicate identity, missing evidence, and conservation mismatch cases. Report tests will verify keys, values, types, populations, operand relations, provenance, table cells, and macro generation. Publication integration will verify that all eight keys occur once in macros and provenance but remain outside headline selection.

A complete registered report run against the declared controlled and real-world snapshots is the acceptance proof for the observed values. The final provenance manifest and aggregate macro artifact will be archived with their producer revision before the thesis handoff is marked ready.

## Risks / Trade-offs

- **Controlled evidence does not cleanly distinguish pre-filter failures.** Report construction fails and names the conflicting entities. It must not widen the denominator to recover the expected value.
- **Adding a controlled input makes RQ6 depend on two corpus snapshots.** This is intentional because the comparison consumes both; provenance and run completeness must expose both inputs.
- **The similar percentages are mistaken for similar applicability.** Keep the filtering denominator beside every share and retain the explicit non-goals in the handoff.
- **A concise sentence overstates causality.** Producer artifacts state the observed funnel boundary only; the thesis semantic review owns final wording and must not claim that one mechanism alone explains every earlier loss.
- **Internal lifecycle vocabulary leaks into the thesis.** Generated labels and handoff text use only the established filtering vocabulary, even if implementation types retain existing internal names.

## Migration Plan

1. Add the controlled RQ6 input and corpus-local filtering queries without changing existing RQ6 outputs.
2. Add the eight typed metrics, conservation checks, table rendering, aggregate macros, and provenance.
3. Run focused query, report, macro, provenance, and headline-selection tests.
4. Run the complete registered report set against the declared snapshots and inspect the generated values and provenance.
5. Record the producer revision and generated artifact identities in the thesis reconciliation change.
6. Defer thesis prose edits until the separate RQ6 semantic review approves exact wording and placement.

Rollback removes the controlled RQ6 input and the eight dependent metrics, table cells, macros, and handoff entries together. Existing real-world RQ6 evidence remains unchanged.

## Thesis Handoff

The clean producer run used implementation revision `9f18b49c27c992ed54ae0345cf38f43e795b2f3c`. Its inputs were controlled corpus `controlled` in `postgres_dev` with 13 of 13 projects and real-world corpus `real-world` in `postgres_reporeapers_rq6_v7` with 1,161 of 1,161 projects, `data/reporeapers-rerun-v7`, and `project-configs/replication/extended`.

The generated evidence is:

- controlled: 11,597 retained, 2,207 excluded, 13,804 total, 84.0%;
- RepoReapers: 1,615 retained, 420 excluded, 2,035 total, 79.4%;
- `analysis/reports/provenance.json`: SHA-256 `c4e854923dd22a47ee077ee26ace08b11bc58c9119309af663df5feb668d250f`;
- `analysis/build/macros.tex`: SHA-256 `7a160af54e1e7511f84811ad644c75faa73ec6e93eb84086bdcb76ee72b94ae1`.

The downstream thesis change may use the eight stable keys in Decision 3. The approved argument-level frame is that filtering retains a similar proportion in both settings and the larger real-world loss occurs during generalized test creation. The handoff does not approve final prose, a thesis-wide headline, overall generalization success, project applicability, a paired-project effect, or causal attribution. The RQ6 semantic review must approve exact wording and placement before thesis prose changes.
