## REMOVED Requirements

### Requirement: The consumer declares which figures it takes

**Reason**: The rule was never specific to figures. `reporting/artifact-delivery` states it for every
kind of generated artifact, so a figure-shaped copy here would say the same thing twice and let the two
statements drift apart.

**Migration**: No behaviour is withdrawn. A consumer that declares figures declares them in the same
file and receives the same artifacts. The governing requirement is now *A consuming repository declares
every artifact it takes*, which adds the other kinds beside figures.

### Requirement: A declaration that disagrees with the emitted set fails the publish

**Reason**: The disagreement this describes is a property of a declaration, not of a figure. It is
restated for every artifact kind in `reporting/artifact-delivery`, including the renamed-artifact case
this requirement covered for figure keys.

**Migration**: None. A declared figure that no report emits still fails the publish, and the failure
still names the figure.

### Requirement: A figure key identifies one figure across the whole report set

**Reason**: Uniqueness is required of every artifact name, for the same reason: a declaration names an
artifact and cannot express which report produced it. `reporting/artifact-delivery` states it per kind,
which this requirement could not, and a name shared between a table and its data file is therefore not
a collision.

**Migration**: None for figures. Two reports emitting one figure key still fail the run.

### Requirement: Publishing a figure is subject to the same guards as publishing a table

**Reason**: This requirement existed only to point at guards defined elsewhere, and it pointed at them
from the wrong side: it made a figure's guards derivative of a table's, when both derive from the same
rule. `reporting/artifact-delivery` states the clean-tree and consumer-edit guards once, for every
kind.

**Migration**: None. A dirty generator tree and an uncommitted change to a delivered figure both still
refuse the publish.
