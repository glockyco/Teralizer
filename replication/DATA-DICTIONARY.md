# Data dictionary

Generated from `replication/data-dictionary.toml`, the restored corpora and the reference
result files by
`uv run --directory analysis python -m teralizer.release data-dictionary`.
Edit the descriptions there.

## Corpus tables and views

The corpora were collected with different pipeline versions. A table or
column that not every corpus has names the corpora that have it. The reports
read the views, which `src/main/resources/db/create-views.sql` defines over
the tables. PostgreSQL reports every view column as nullable.

### `project`

Table. One project workspace processed by the pipeline, with its run configuration and paths. A resumed run reuses the project row.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `type` | text | no | Build system identified from the build file: MAVEN, GRADLE, JAIGANTIC, ANT, or UNKNOWN. Only Maven and Gradle are supported by setup. |
| `test_framework` | text | yes | JUnit family detected from dependency JAR names: JUNIT_3, JUNIT_4, JUNIT_5, or UNKNOWN. Null if framework detection did not run or its result was not saved. |
| `test_framework_version` | text | yes | Version extracted from the detected JUnit JAR name. Null if detection did not record a JUnit version. |
| `root_path` | text | no | Path to the project workspace. |
| `data_path` | text | no | Base directory for pipeline artifacts for this project. |
| `main_source_path` | text | yes | Directory of production Java sources, configured explicitly or set by build-system setup. Null if neither supplied nor saved during setup. |
| `test_source_path` | text | yes | Directory of test Java sources, configured explicitly or set by build-system setup. Null if neither supplied nor saved during setup. |
| `main_compiled_path` | text | yes | Directory for compiled production classes, configured explicitly or set by build-system setup. Null if neither supplied nor saved during setup. |
| `test_compiled_path` | text | yes | Directory for compiled test classes, configured explicitly or set by build-system setup. Null if neither supplied nor saved during setup. |
| `test_reports_path` | text | yes | Directory for JUnit test execution reports, configured explicitly or set by build-system setup. Null if neither supplied nor saved during setup. |
| `coverage_reports_path` | text | yes | Directory for JaCoCo coverage reports, configured explicitly or set by build-system setup. Null if neither supplied nor saved during setup. |
| `mutation_reports_path` | text | yes | Directory for PIT mutation reports, configured explicitly or set by build-system setup. Null if neither supplied nor saved during setup. |
| `classpath` | text | yes | Resolved classpath for the project, including production and test class directories and dependency JARs. Null if classpath setup did not save a result. |
| `git_version` | text | yes | Git HEAD commit hash of the project workspace at setup. Null if setup did not save a hash or could not find HEAD in a local .git directory. |
| `tool_git_version` | text | yes | Git HEAD commit hash of the Teralizer working directory at setup. Null if setup did not save a hash or could not find HEAD in a local .git directory. |
| `use_test_generation` | boolean | no | Whether the configuration requests EvoSuite test generation. |
| `use_test_generalization` | boolean | no | Whether the configuration requests test generalization. |
| `configuration` | text | no | Effective Teralizer configuration rendered as formatted text. |
| `runtime` | real | yes | Wall-clock time in seconds for the last run that saved its elapsed time, including all pipeline phases executed in that invocation. Null until a run reaches the runtime write. |
| `use_test_reduction` | boolean | no | Whether the configuration requests the reduction phase after generalization. Only in real-world, jarvis-benchmark, jarvis-scenarios. |

### `test`

Table. One test method identified from an execution report or recorded directly when EvoSuite postprocessing excludes it.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project containing the test, project.id. |
| `test_file_path` | text | no | Path to the Java source file for the reported test class or the postprocessed EvoSuite class. |
| `test_class_qualified_name` | text | no | Fully qualified name of the executing class from the test report, or of the generated class for a test excluded during EvoSuite postprocessing. |
| `test_method_qualified_name` | text | no | Normalized executing class name plus the method name without invocation arguments, or the qualified generated class name plus the method name for a test excluded during EvoSuite postprocessing. |
| `test_package_name` | text | no | Package of the reported or generated test class. |
| `test_class_name` | text | no | Simple name of the reported or generated test class. |
| `test_method_name` | text | no | Simple method name, with invocation arguments removed from report names. |
| `test_method_absolute_path` | text | yes | Spoon AST path from the model root to the test method declaration. Null if source mapping did not record a method. |
| `test_method_relative_path` | text | yes | Spoon AST path to the test method relative to its declaring class, which can differ from the executing class. Null if source mapping did not record a method. |
| `test_annotation_name` | text | yes | Test marker: Test, RepeatedTest, ParameterizedTest, TestFactory, TestTemplate, Property, Example, TestCase for JUnit 3, or TestNG. Null if no recognized marker was recorded. |
| `test_annotations_source_code` | text | yes | Newline-separated Java renderings of the method annotations, or an empty string if there are none. Null if source mapping did not reach the annotation write. |
| `line_count` | integer | yes | Number of lines in Spoon's Java rendering of the test method. Null if source mapping did not reach the line count. |
| `is_included` | boolean | no | Whether the pipeline still considers the test eligible for downstream processing. |
| `exclusion_info` | text | yes | Text describing why the pipeline excluded the test. Null if no exclusion reason was recorded. |

### `assertion`

Table. One assertion call detected by source analysis in a test method, with its selected method under test and extraction artifacts when available.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project containing the assertion, project.id. |
| `test_id` | bigint | no | Test containing the assertion, test.id. |
| `assertion_name` | text | no | Simple name of the assertion method called. |
| `assertion_arguments` | text | no | JSON array of assertion arguments in call order, with each expression's type and rendered Java text. |
| `assertion_source_code` | text | no | Java rendering of the assertion call from the Spoon source model. |
| `assertion_absolute_path` | text | no | Spoon AST path from the model root to the assertion call. |
| `assertion_relative_path` | text | no | Spoon AST path to the assertion call relative to the test method declaration. |
| `tested_file_path` | text | yes | Source file of the declaring type of the selected method, relative to the tool working directory. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_class_qualified_name` | text | yes | Fully qualified name of the selected method's declaring type. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_method_qualified_name` | text | yes | Qualified declaring type and simple name of the selected method, without a parameter signature. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_package_name` | text | yes | Package of the selected method's declaring type. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_class_name` | text | yes | Simple name of the selected method's declaring type. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_method_name` | text | yes | Simple name of the selected method, taken from the invocation when the declaration is unavailable. Null if no method call was selected. |
| `tested_method_parameters` | text | yes | JSON array of generalizable input names and types, or of formal parameter names and types if no inputs were derived. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_method_return_type` | text | yes | Declared return type of the selected method. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_method_absolute_path` | text | yes | Spoon AST path from the model root to the selected method declaration. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_method_relative_path` | text | yes | Spoon AST path to the selected method declaration relative to its declaring type. Null if analysis could not record a source declaration and both call and declaration paths. |
| `tested_method_call_arguments` | text | yes | JSON array of types and Java expressions for the derived generalizable inputs, or for the invocation arguments when no inputs were derived. Null if no method call was selected. |
| `tested_method_call_source_code` | text | yes | Java rendering of the selected method invocation. Null if no method call was selected. |
| `tested_method_call_absolute_path` | text | yes | Spoon AST path from the model root to the selected method invocation. Null if no call was selected or either invocation path could not be obtained. |
| `tested_method_call_relative_path` | text | yes | Spoon AST path to the selected method invocation relative to the test method. Null if no call was selected or either invocation path could not be obtained. |
| `instrumented_file_path` | text | yes | Java source path for the instrumented class. Null if JPF instrumentation did not record it. |
| `instrumented_class_qualified_name` | text | yes | Fully qualified name of the generated instrumented class. Null if JPF instrumentation did not record it. |
| `instrumented_method_qualified_name` | text | yes | Fully qualified instrumented class name plus the generated method name. Null if JPF instrumentation did not record it. |
| `instrumented_package_name` | text | yes | Package of the instrumented class, copied from the test. Null if JPF instrumentation did not record it. |
| `instrumented_class_name` | text | yes | Simple name of the generated instrumented class. Null if JPF instrumentation did not record it. |
| `instrumented_method_name` | text | yes | Name of the instrumented method, formed from the selected method name and assertion ID. Null if JPF instrumentation did not record it. |
| `driver_file_path` | text | yes | Java source path for the generated JPF driver. Null if JPF instrumentation did not record it. |
| `driver_class_qualified_name` | text | yes | Fully qualified name of the generated JPF driver class. Null if JPF instrumentation did not record it. |
| `driver_package_name` | text | yes | Package of the generated JPF driver, copied from the test. Null if JPF instrumentation did not record it. |
| `driver_class_name` | text | yes | Simple name of the generated JPF driver class. Null if JPF instrumentation did not record it. |
| `jpf_config_path` | text | yes | Path to the assertion's JPF configuration file. Null if JPF instrumentation did not record it. |
| `input_values_path` | text | yes | Path assigned during instrumentation for the JSON file of concrete inputs captured by JPF. Null if JPF instrumentation did not record it. |
| `output_value_path` | text | yes | Path assigned during instrumentation for the JSON file of the concrete result captured by JPF. Null if JPF instrumentation did not record it. |
| `input_specification_path` | text | yes | Path assigned during instrumentation for the JSON file of the symbolic input constraint. Null if JPF instrumentation did not record it. |
| `output_specification_path` | text | yes | Path assigned during instrumentation for the JSON file of the symbolic output model. Null if JPF instrumentation did not record it. |
| `equivalent_assertions` | text | yes | JSON array of sorted assertion.id values, including this assertion, for included assertions in the project with the same tested method qualified name and input specification file hash. Null if JPF analysis did not assign this assertion to a group. |
| `input_model_statistics` | text | yes | JSON object with rendered input model length in UTF-16 code units and counts of operations, operation shapes, operators, and operand types. Null if JPF analysis did not record statistics for the assertion. |
| `output_model_statistics` | text | yes | JSON object with rendered output model length in UTF-16 code units and counts of operations, operation shapes, operators, and operand types. Null if JPF analysis did not record statistics for the assertion. |
| `is_included` | boolean | no | Whether the pipeline still considers the assertion eligible for downstream processing. |
| `exclusion_info` | text | yes | Text describing why the pipeline excluded the assertion. Null if no exclusion reason was recorded. |
| `generalization_recipe` | text | yes | JSON recipe with Spoon paths and types for the selected oracle expression and method, and the input sites to vary. Null if analysis did not record a recipe backed by a resolvable source declaration and paths. Only in real-world, jarvis-benchmark, jarvis-scenarios. |
| `output_spec_class` | text | yes | Shape of the captured output model: SYMBOLIC with model variables, CONSTANT without them, NULL_CONCRETE with no model, or EXCEPTION for a thrown result. Null if JPF extraction did not save the classification. Only in real-world, jarvis-benchmark, jarvis-scenarios. |
| `output_is_literal` | boolean | yes | Whether JPF detected a value return immediately following a bytecode constant push, ignoring GOTO instructions. Null if JPF extraction did not save the flag. Only in real-world. |
| `concretization_events` | integer | yes | Count of native calls receiving at least one symbolic argument during JPF extraction. Null if extraction did not save the count. Only in real-world, jarvis-benchmark, jarvis-scenarios. |
| `post_concretization_divergence_risk` | boolean | yes | Whether JPF saw a native call with a symbolic argument and either a later application branch without symbolic operands or a throw from outside application code. Null if extraction did not save the flag. Only in real-world, jarvis-benchmark, jarvis-scenarios. |
| `concretized_methods` | text | yes | JSON object mapping fully qualified native method names to counts of calls that received symbolic arguments. Null if JPF extraction did not save a nonempty map. Only in real-world, jarvis-benchmark, jarvis-scenarios. |

### `generalization`

Table. One attempt to generate a test from an assertion for a configured variant, whether or not source was produced.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project of the generalization attempt, project.id. |
| `test_id` | bigint | no | Test from which the generalization was derived, test.id. |
| `assertion_id` | bigint | no | Assertion from which the generalization was derived, assertion.id. |
| `variant` | text | no | Configuration key for the generalization algorithm and jqwik try limit, such as IMPROVED_200_TRIES. |
| `file_path` | text | no | Path where the generated Java file is written, next to the source test file. |
| `class_qualified_name` | text | no | Generated class name including its package, if any. |
| `method_qualified_name` | text | no | Qualified generated class name followed by the test method name, without a parameter signature. |
| `package_name` | text | no | Package of the source test, reused for the generated class. |
| `class_name` | text | no | Simple name of the generated class, derived from the source test class, test method, and generalization ID. |
| `method_name` | text | no | Simple name of the generated test method, carried over from the source test. |
| `total_constraint_count` | integer | yes | Number of flattened input path condition clauses in the IMPROVED generation plan, after dropping clauses that reference only inputs not generated. Null if the pipeline did not reach the step that records that plan. |
| `used_constraint_count` | integer | yes | Number of distinct plan clauses incorporated into input generators, not the number checked by the input filter. Null if the pipeline did not record an IMPROVED generation plan. |
| `line_count` | integer | no | Number of lines in the whole generated Java file, including blank lines. Initialized to zero before the file is written and counted. |
| `is_included` | boolean | no | Whether the pipeline has kept the attempt eligible for subsequent processing. It starts true and can be cleared by a gate, filter rejection, or task failure. |
| `exclusion_info` | text | yes | Reason the pipeline excluded this attempt, such as a gate code, filter stage, or task error. Null if no exclusion was recorded. |
| `widening_refusal_code` | text | yes | Specific reason a captured oracle did not support widening inputs: EXCEPTION_CONCRETIZATION_DIVERGENCE_RISK, EXCEPTION_PATH_CONDITION_NOT_COVERING_PARAMETERS, NULL_CONCRETE_OUTPUT_NOT_LITERAL, NULL_CONCRETE_CONCRETIZATION_EVENTS, NULL_CONCRETE_PARAMETERS_EMPTY, or NULL_CONCRETE_PATH_CONDITION_NOT_COVERING_PARAMETERS. Null if no widening refusal was recorded. Only in real-world. |

### `generalization_lifecycle`

Table. Only in real-world, jarvis-benchmark, jarvis-scenarios. One status record for a generalization that produced source, updated as the build, test, and validation stages report outcomes.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `generalization_id` | bigint | no | Generalization tracked by this record, generalization.id. |
| `generated_source_created` | boolean | no | Whether generated test source was written and archived. This is true when the row is created. |
| `generated_project_compiled` | boolean | no | Whether the build succeeded for the project and variant containing this generalization. |
| `generated_tests_executed` | boolean | no | Whether the generated-test execution stage succeeded for the project and variant. This does not mean that this test passed. |
| `generated_report_collected` | boolean | no | Whether the generated-test report collection stage succeeded for this generalization. |
| `generated_filter_passed` | boolean | no | Whether no generated-test filter rejected this generalization after its report was collected. |
| `generated_pit_collected` | boolean | no | Whether the generated PIT collection stage completed for the project and variant. Also true when the stage was skipped because PIT was disabled. |
| `final_usable` | boolean | no | Whether all six lifecycle flags are true. |
| `final_failure_stage` | text | yes | First stage whose lifecycle flag is false: BUILD_PROJECT_GENERALIZED, EXECUTE_TESTS_GENERALIZED, COLLECT_JUNIT_REPORTS_GENERALIZED, FILTER_GENERALIZATIONS, or COLLECT_PIT_DATA_GENERALIZED. Null when all flags are true. |
| `final_failure_code` | text | yes | Diagnostic or filter reason code retained for the failing stage. Null when all flags are true or no code was retained for that stage. |

### `filter_result`

Table. One decision from a filter checking a test, assertion, or generalization, or from compile validation excluding generated code.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project in which the decision was made, project.id. |
| `test_id` | bigint | yes | Test evaluated by a test-level check, test.id. Null for assertion or generalization checks. |
| `assertion_id` | bigint | yes | Assertion evaluated by an assertion-level check, assertion.id. Null for test or generalization checks. |
| `generalization_id` | bigint | yes | Generalization evaluated by a generalization-level check, generalization.id. Null for test or assertion checks. |
| `filter_name` | text | no | Fully qualified Java class name of the filter, or GeneratedTestValidator for compile validation. |
| `decision` | text | no | ACCEPT passes the check, REJECT excludes the item, and DEFER does not by itself exclude the item. |
| `reason` | text | no | Human-readable explanation or compiler error text. Empty if the check supplied no explanation. |
| `reason_code` | text | yes | Machine-readable reason for the decision. Null when the check did not assign a code. Only in real-world, jarvis-benchmark, jarvis-scenarios. |
| `depends_on` | text | yes | Related exclusion or precondition code: EXCLUDED_TEST, EXCLUDED_ASSERTION, MISSING_MUT, or UNSUPPORTED_ASSERTION. Null when the check did not record one. Only in real-world, jarvis-benchmark, jarvis-scenarios. |
| `detail_json` | jsonb | yes | Additional decision details as a JSON object, such as all_reason_codes for missing method metadata or return_type for an unsupported return type. Null when the check did not supply details. Only in real-world, jarvis-benchmark, jarvis-scenarios. |

### `evosuite_runtime`

Table. One timed phase from the EvoSuite console output for a target class.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project for which EvoSuite generated tests, project.id. |
| `class_name` | text | no | Fully qualified name of the class passed to EvoSuite as the generation target. |
| `step` | integer | no | Sequential position of the timing record for this target class, starting at 1. |
| `phase_name` | text | no | Phase name taken from the EvoSuite status update that started the timed interval. |
| `runtime` | real | no | Elapsed seconds from the phase's status update to the next phase update or the finish marker. |

### `evosuite_report`

Table. One row of EvoSuite's generation statistics for a target class.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project for which EvoSuite generated tests, project.id. |
| `class_name` | text | no | Fully qualified name of the target class in EvoSuite's statistics. |
| `criterion` | text | no | Coverage criteria as reported by EvoSuite, stored as a single string that can contain multiple criteria. |
| `coverage` | real | no | Overall coverage reported by EvoSuite as a fraction from 0 to 1. |
| `total_goals` | integer | no | Count of coverage goals reported by EvoSuite. |
| `covered_goals` | integer | no | Count of coverage goals met by the generated suite. |

### `junit_test_report`

Table. One reported JUnit test case from an original, initial, or generalized suite. Reports for a generated property also include other cases from its generated class.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project whose test suite ran, project.id. |
| `test_id` | bigint | yes | Test selected for report collection, test.id. Null when the report is not linked to a test, including when collected for a generalization. |
| `generalization_id` | bigint | yes | Generalization selected for report collection, generalization.id. Null when the report was not collected for a generalization. |
| `step` | integer | no | Numeric pipeline step for JUnit report collection. |
| `stage` | text | no | JUnit report collection stage: COLLECT_JUNIT_REPORTS_ORIGINAL, COLLECT_JUNIT_REPORTS_INITIAL, or COLLECT_JUNIT_REPORTS_GENERALIZED. |
| `variant` | text | yes | Configured generalization variant for the suite. Null for the original and initial suites. |
| `test_package_name` | text | no | Package of the test or generalization selected for report collection. |
| `test_class_name` | text | no | Unqualified class name of the test or generalization selected for report collection. |
| `test_method_name` | text | no | Method name of the test or generalization selected for report collection. This can differ from the case that produced the row. |
| `test_case_name` | text | no | Test case name as reported in the XML file. |
| `result` | text | no | Outcome derived from the XML report: PASSED, FAILED, SKIPPED, or ERROR. |
| `runtime` | real | no | Test case duration in seconds from the XML report, or 0 if the time attribute is absent. |
| `failure_message` | text | yes | Message parsed from the nonpassing outcome in the XML report. Null when the parser supplies no message. |
| `failure_type` | text | yes | Failure or error type reported by the XML parser, which can also supply a skip marker. Null when the parser supplies no type. |
| `failure_error_line` | text | yes | Source line number extracted as text from a matching test-class stack frame, or an empty string if none is found. Null when no failure or error trace was processed. |
| `failure_detail` | text | yes | Text contents of the XML failure or error element, typically the stack trace. Null when there is no such element. |
| `report_file_path` | text | no | Path to the preserved copy of the source XML report. |

### `jacoco_coverage_report`

Table. One class's JaCoCo CSV coverage counters for an original, initial, or generalized test suite.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project whose suite produced the coverage report, project.id. |
| `step` | integer | no | Numeric pipeline step for JaCoCo coverage collection. |
| `stage` | text | no | JaCoCo collection stage: COLLECT_JACOCO_DATA_ORIGINAL, COLLECT_JACOCO_DATA_INITIAL, or COLLECT_JACOCO_DATA_GENERALIZED. |
| `variant` | text | yes | Configured generalization variant for the suite. Null for the original and initial suites. |
| `covered_package` | text | no | Package name of the measured class from the JaCoCo report. |
| `covered_class` | text | no | Class name from the JaCoCo report, stored separately from the package. |
| `instruction_missed` | integer | no | Count of missed bytecode instructions. |
| `instruction_covered` | integer | no | Count of covered bytecode instructions. |
| `branch_missed` | integer | no | Count of missed branches. |
| `branch_covered` | integer | no | Count of covered branches. |
| `line_missed` | integer | no | Count of source lines marked missed by JaCoCo. |
| `line_covered` | integer | no | Count of source lines marked covered by JaCoCo, including partially covered lines. |
| `complexity_missed` | integer | no | Missed cyclomatic complexity in JaCoCo complexity units. |
| `complexity_covered` | integer | no | Covered cyclomatic complexity in JaCoCo complexity units. |
| `method_missed` | integer | no | Count of methods marked missed by JaCoCo. |
| `method_covered` | integer | no | Count of methods marked covered by JaCoCo. |

### `pit_coverage_report`

Table. One pairing of a covered code block and a covering test entry in PIT's coverage XML for a suite.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project whose suite produced the PIT report, project.id. |
| `test_id` | bigint | yes | Covering test, test.id. Null when PIT's test name does not resolve to a registered test. |
| `generalization_id` | bigint | yes | Covering generalization, generalization.id. Null when PIT's test name does not resolve to a registered generalization. |
| `step` | integer | no | Numeric pipeline step for PIT data collection. |
| `stage` | text | no | PIT collection stage: COLLECT_PIT_DATA_ORIGINAL, COLLECT_PIT_DATA_INITIAL, or COLLECT_PIT_DATA_GENERALIZED. |
| `variant` | text | yes | Configured generalization variant for the suite. Null for the original and initial suites. |
| `covered_package_name` | text | no | Package of the class containing the covered block. |
| `covered_class_name` | text | no | Unqualified name of the class containing the covered block. |
| `covered_method_name` | text | no | Name of the method containing the covered block. |
| `covered_method_description` | text | no | JVM method descriptor, including parameter and return types, split from PIT's method signature. |
| `covered_block_number` | integer | no | Numeric identifier of the covered bytecode block within the method, as assigned by PIT. |
| `test_package_name` | text | no | Package parsed from the covering test's PIT name, or an empty string for an unqualified test class. |
| `test_class_name` | text | no | Unqualified class name parsed from the covering test's PIT name. |
| `test_method_name` | text | yes | Method name parsed from the covering test's PIT name. Null when no method is identified. |

### `pit_mutation_report`

Table. One mutant and its outcome in PIT's mutation report for an original, initial, or generalized test suite.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project whose code was mutated, project.id. |
| `killing_test_id` | bigint | yes | Test identified by PIT as killing the mutant, test.id. Null when the report does not identify a killer that resolves to a registered test. |
| `killing_generalization_id` | bigint | yes | Generalization identified by PIT as killing the mutant, generalization.id. Null when the report does not identify a killer that resolves to a registered generalization. |
| `step` | integer | no | Numeric pipeline step for PIT data collection. |
| `stage` | text | no | PIT collection stage: COLLECT_PIT_DATA_ORIGINAL, COLLECT_PIT_DATA_INITIAL, or COLLECT_PIT_DATA_GENERALIZED. |
| `variant` | text | yes | Configured generalization variant for the suite. Null for the original and initial suites. |
| `is_detected` | boolean | no | Whether PIT marked the mutant as detected. Detection does not require a recorded killing test. |
| `status` | text | no | PIT outcome: KILLED, SURVIVED, TIMED_OUT, NON_VIABLE, MEMORY_ERROR, RUN_ERROR, or NO_COVERAGE. |
| `number_of_tests_run` | integer | no | Count of tests that PIT reports running against the mutant. |
| `source_file` | text | no | Source file name reported by PIT for the mutated class. |
| `mutated_class` | text | no | Unqualified name of the mutated class. |
| `mutated_method` | text | no | Name of the method containing the mutation. |
| `method_description` | text | no | JVM descriptor of the mutated method as reported by PIT. |
| `line_number` | integer | no | Source line number of the mutation as reported by PIT. |
| `mutator` | text | no | Name of the PIT mutation operator that produced the mutant. |
| `indexes` | text | no | Sorted JSON array of PIT's mutation instruction indexes, stored as text. An empty array means none were reported. |
| `blocks` | text | no | Sorted JSON array of PIT's bytecode block numbers for the mutant, stored as text. An empty array means none were reported. |
| `killing_package_name` | text | yes | Package parsed from the killing test's PIT name, or an empty string for an unqualified class. Null when no killing test name could be parsed. |
| `killing_class_name` | text | yes | Unqualified class name parsed from the killing test's PIT name. Null when no killing test name could be parsed. |
| `killing_method_name` | text | yes | Method name parsed from the killing test's PIT name. Null when no killing test method could be identified. |
| `description` | text | no | PIT's textual description of the change made by the mutation. |
| `mutated_package` | text | no | Package of the mutated class. |

### `task`

Table. One execution attempt of a pipeline task at project, test, assertion, generalization, or shared scope.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | yes | Project associated with the task, project.id. Null if the task has no known project. |
| `test_id` | bigint | yes | Test associated with the task, test.id. Null if the task has no known test. |
| `assertion_id` | bigint | yes | Assertion associated with the task, assertion.id. Null if the task has no known assertion. |
| `generalization_id` | bigint | yes | Generalization associated with the task, generalization.id. Null if the task has no known generalization. |
| `step` | integer | yes | Integer ordering index assigned to the processing stage, not a count of attempts. Null for task records with no assigned stage index. |
| `stage` | text | no | Processing stage name, such as ANALYZE_TESTS or EXECUTE_JPF. |
| `variant` | text | yes | Configured test generalization variant name. Null for tasks not scoped to a single variant. |
| `status` | text | no | Execution state: IN_PROGRESS, SUCCEEDED, or FAILED. |
| `runtime` | real | yes | Wall-clock seconds spent executing the task, excluding writes to its task row. Null if timing was not recorded, including tasks still in progress. |
| `info` | text | yes | Newline-separated messages reported by the task, with the exception stack trace prepended on failure. Null if no message or exception was recorded. |

### `assertion_semantics`

Table. Only in real-world, jarvis-benchmark, jarvis-scenarios. One static classification of an assertion call, its selected argument, and any fail or matcher context.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `assertion_id` | bigint | no | Assertion classified by this record, assertion.id. |
| `semantic_kind` | text | no | Category assigned from the assertion call: EQUALITY, INEQUALITY, BOOLEAN_TRUE, BOOLEAN_FALSE, NULLNESS_NULL, NULLNESS_NOT_NULL, SAMENESS, ARRAY_EQUALITY, HAMCREST_MATCHER, ASSERTJ_MATCHER, FAIL_SENTINEL, or UNKNOWN. |
| `argument_shape` | text | no | Syntax category of the selected assertion argument: METHOD_CALL, LITERAL, VARIABLE_OR_FIELD, OTHER, or NONE if no argument was selected. |
| `fail_context` | text | yes | Enclosing context of a fail() call: CATCH_BLOCK_SHOULD_NOT_REACH, GUARD_BRANCH, TRY_BLOCK_EXPECTING_EXCEPTION, or UNKNOWN. Null for other assertions. |
| `matcher_family` | text | yes | Matcher library assigned to assertThat: ASSERTJ for an AssertJ declaring type, otherwise HAMCREST. Null for other assertion kinds. |
| `matcher_name` | text | yes | Simple method name of the matcher call. Null if the assertion has no matcher or the selected matcher argument is not a method call. |

### `build_environment_observation`

Table. Only in real-world, jarvis-benchmark, jarvis-scenarios. One snapshot of build settings and generated test syntax recorded before a project build attempt.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project whose build environment was observed, project.id. |
| `stage` | text | no | Build stage: BUILD_PROJECT_ORIGINAL, BUILD_PROJECT_INSTRUMENTED, BUILD_PROJECT_INITIAL, BUILD_PROJECT_GENERALIZED, or RESTORE_ORIGINAL_BUILD. |
| `build_tool` | text | no | Build tool category: MAVEN, GRADLE, or UNKNOWN for any other project type. |
| `compiler_source` | text | yes | First matching Java source compatibility setting in the inspected build file, stored as written. Null when no matching setting was found. |
| `compiler_target` | text | yes | First matching Java target compatibility setting in the inspected build file, stored as written. Null when no matching setting was found. |
| `compiler_release` | text | yes | First matching Maven Java release setting in the inspected build file, stored as written. Null for other build tools or when no matching Maven setting was found. |
| `generated_source_required_level` | text | yes | Minimum language level recorded for generated test syntax, 1.8 when the scan finds a lambda or method reference marker. Null when neither marker was found or the scan did not finish. |
| `generated_uses_lambdas` | boolean | yes | Whether a text scan of generated test files found the lambda operator surrounded by spaces. Null if not recorded. |
| `generated_uses_method_references` | boolean | yes | Whether a text scan of generated test files found the double-colon operator. Null if not recorded. |
| `generated_uses_diamond` | boolean | yes | Whether a text scan of generated test files found empty angle brackets. Null if not recorded. |
| `test_source_floor` | text | yes | Build-file outcome for the Java 8 test compiler floor: APPLIED, NOT_NEEDED, or SKIPPED_UNRESOLVABLE. Null for other build tools or when the Maven build file could not be read or parsed. |
| `surefire_floor` | text | yes | Outcome of checking the Maven Surefire version floor for running generated tests: APPLIED, NOT_NEEDED, or SKIPPED_UNRESOLVABLE. Gradle records NOT_NEEDED, and the value is null if either Maven build file could not be read or parsed or the project uses another build tool. |

### `jpf_extraction_summary`

Table. Only in real-world, jarvis-benchmark, jarvis-scenarios. One snapshot of Java PathFinder (JPF) extraction tasks, specifications, and diagnostics for a project or a test within it.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `project_id` | bigint | no | Project summarized by this record, project.id. |
| `test_id` | bigint | yes | Test summarized by this record, test.id. Null for the project-wide summary. |
| `assertions_scheduled` | integer | no | Number of EXECUTE_JPF task records in scope, regardless of status. |
| `assertions_instrumented` | integer | no | Number of ADD_JPF_INSTRUMENTATION task records in scope with status SUCCEEDED. |
| `assertions_jpf_succeeded` | integer | no | Number of EXECUTE_JPF task records in scope with status SUCCEEDED. |
| `assertions_jpf_failed` | integer | no | Number of EXECUTE_JPF task records in scope with status FAILED. |
| `assertions_with_input_spec` | integer | no | Number of assertions in scope whose stored input model statistics report a rendered Java size greater than zero when the summary is written. |
| `assertions_with_output_spec` | integer | no | Number of assertions in scope with a recorded output specification class other than NULL_CONCRETE. |
| `assertions_with_complete_spec` | integer | no | Number of assertions in scope that meet both the input and output specification checks. |
| `failure_counts` | jsonb | no | JSON object mapping reason codes to counts of instrumentation, JPF execution, and JPF analysis diagnostic rows in scope. Empty when no such diagnostics were recorded. |

### `task_diagnostic`

Table. Only in real-world, jarvis-benchmark, jarvis-scenarios. One classified failure of a task at a stage where structured diagnostics are collected.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `task_id` | bigint | no | Failed task, task.id. |
| `project_id` | bigint | yes | Project association copied from the failed task, project.id. Null if the task has no known project. |
| `test_id` | bigint | yes | Test association copied from the failed task, test.id. Null if the task has no known test. |
| `assertion_id` | bigint | yes | Assertion association copied from the failed task, assertion.id. Null if the task has no known assertion. |
| `generalization_id` | bigint | yes | Generalization association copied from the failed task, generalization.id. Null if the task has no known generalization. |
| `stage` | text | no | Processing stage name copied from the failed task. |
| `reason_code` | text | no | Failure category assigned from the stage and exception or command output, such as NO_INPUT_SPEC, EXECUTION_TIMEOUT, or MISSING_DEPENDENCY. |
| `detail_json` | jsonb | yes | Structured classifier evidence such as an exception type and message or an extraction outcome and detail. Null if the writer has no details to store. |
| `first_error_file` | text | yes | Unused by the task diagnostic writer. Always null. |
| `first_error_line` | integer | yes | Unused by the task diagnostic writer. Always null. |
| `first_error_message` | text | yes | Unused by the task diagnostic writer. Always null. |

### `jqwik_execution_run`

Table. One registered JUnit run of a generalized suite with persisted jqwik diagnostics.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `execution_id` | text | no | Random UUID shared with the test process to identify the run's diagnostic files. |
| `project_id` | bigint | no | Project whose generalized suite ran, project.id. |
| `task_id` | bigint | yes | Optional pipeline task link, task.id. Null because run registration does not set a task ID. |
| `step` | integer | no | Numeric pipeline step for generalized JUnit test execution. |
| `stage` | text | no | Execution stage, EXECUTE_TESTS_GENERALIZED. |
| `variant` | text | yes | Configured generalization variant for the run. Null if the execution task was given no variant. |
| `execution_kind` | text | no | Execution kind, set to JUNIT by generalized test execution. |

### `jqwik_property_execution`

Table. One generalization's imported jqwik outcome for a registered execution, or an explicit record that its outcome file is missing.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `jqwik_execution_run_id` | bigint | no | Registered jqwik diagnostic run, jqwik_execution_run.id. |
| `project_id` | bigint | no | Project containing the property, project.id. |
| `generalization_id` | bigint | no | Generalization whose property outcome was imported, generalization.id. |
| `junit_test_report_id` | bigint | yes | Latest junit_test_report.id for the generalization and collection stage by row ID. Null when there is no matching JUnit report row. |
| `test_case_name` | text | no | Test case name from the outcome file. The importer falls back to the generalization's method name if no name is available. |
| `diagnostic_kind` | text | no | Outcome classification: FULL, LIMITED_TOO_MANY_FILTER_MISSES, FILTER_EXHAUSTED_SEED_ONLY, ASSERTION_FAILED, EXECUTION_ERROR, or DIAGNOSTIC_MISSING. |
| `raw_status` | text | no | jqwik status before the filter-exhaustion hook remaps it: SUCCESSFUL, ABORTED, or FAILED. UNKNOWN means the outcome file is missing. |
| `final_status` | text | no | Status after the hook maps a filter-exhaustion failure to success if a distinct non-seed tuple was recorded: SUCCESSFUL, ABORTED, or FAILED. UNKNOWN means the outcome file is missing. |
| `throwable_type` | text | yes | Class name of the throwable from the raw result, or an empty string if there was no throwable. Null if no outcome file was found. |
| `throwable_message` | text | yes | Raw throwable message, or an empty string if there was no throwable or message. Null if no outcome file was found. |
| `tries` | integer | yes | Count of tries reported by jqwik before status remapping. Null if no outcome file was found. |
| `checks` | integer | yes | Count of property checks reported by jqwik before status remapping. Null if no outcome file was found. |
| `distinct_tuples` | integer | yes | Count of distinct serialized input tuples recorded by the property, including the first recorded tuple if present. Null if no outcome file was found. |
| `distinct_new_tuples` | integer | yes | Count of distinct serialized input tuples other than the first recorded tuple. Null if no outcome file was found. |
| `seed` | text | yes | jqwik random seed as text, or an empty string if jqwik supplied no seed. Null if no outcome file was found. |
| `selected_value_log_path` | text | yes | Path to the TSV log of recorded input tuples beside the outcome file. Null if no outcome file was found. |
| `diagnostic_sidecar_path` | text | yes | Path where the importer looked for the JSON outcome file. The importer sets it even if the file is missing and never writes null. |

### `mut_resolution_observation`

Table. One static method-under-test resolution outcome for an assertion, including cases with no usable candidate.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `assertion_id` | bigint | no | Assertion for which the resolver looked for a method under test, assertion.id. |
| `project_id` | bigint | no | Project containing the assertion, project.id. |
| `test_id` | bigint | no | Test containing the assertion, test.id. |
| `status` | text | no | RESOLVED means a call was picked with a source method on a nonlocal, nonanonymous declaring type. CHARACTERIZATION_ONLY means a call was picked without such a declaration, and NONE means no call was picked. |
| `confidence_tier` | text | no | Resolver confidence after applying corroborating signals: T1_PROVEN, T2_CORROBORATED, T3_SINGLE_WEAK, T4_GUESS, or T5_NONE. |
| `deciding_signal` | text | no | Evidence used to make the pick: DIRECT_ACTUAL_CALL, LOCAL_VARIABLE_PRODUCER, FIELD_PRODUCER, SUBEXPRESSION_PRODUCER, INSPECTOR_UNWRAP, UNIQUE_PRODUCER_ELIMINATION, ASSERT_THROWS_LAMBDA, RANKED_GUESS, or NONE. |
| `corroborating_signals` | text | yes | JSON array stored as text with any additional identity evidence: NAME_MATCH for a matching test method name and FOCAL_CLASS_MEMBER for a matching focal type. Null when neither signal applies. |
| `no_pick_reason` | text | yes | Why no call usable for generalization was picked: LIBRARY_DECLARATION, UNRESOLVED_SOURCE_DECLARATION, UNPATHABLE_SOURCE_DECLARATION, NO_VISIBLE_CALL, or UNSUPPORTED_ASSERTION_SHAPE. Null when status is RESOLVED. |
| `candidate_count` | integer | no | Number of candidates retained by the resolver, including the pick and any ranked alternatives. Zero when there is no pick. |
| `resolved_call_source` | text | yes | Source rendering of the picked call as represented in the Java syntax tree. Null when no call was picked. |
| `resolved_method_name` | text | yes | Simple method name on the picked call. Null when no call was picked. |
| `resolved_declaring_type` | text | yes | Qualified declaring type name on the picked call. Null when there is no pick or it has no declaring type reference. |
| `resolved_parameter_types` | text | yes | JSON array stored as text with qualified parameter type names in declaration order. Null when there is no picked source method declaration. |
| `resolved_return_type` | text | yes | Qualified return type name from the picked method declaration. Null when there is no picked source method declaration. |
| `inspector_unwrapped` | boolean | no | Whether the resolver traced through an inspector call to a call that produced its receiver. |
| `shallow_inspector_pick` | boolean | no | Whether the resolver picked an inspector call because it could not trace its variable or field receiver to a producer. |
| `focal_type` | text | yes | Qualified name of the inferred class under test, based on the test class name or the mirrored source path. Null when no focal type was found. |
| `focal_type_source` | text | yes | Evidence used to infer the focal type: PATH_AND_NAME, NAME_ONLY, PATH_ONLY, or NONE when neither identifies one. Null if not recorded, distinct from NONE. |
| `focal_agreement` | boolean | yes | Whether the picked call's declaring type matches the inferred focal type. Null when there is no pick or no inferred focal type. |
| `candidate_param_count` | integer | yes | Number of declared parameters of the picked method. Null when there is no picked source method declaration. |
| `candidate_param_supported` | boolean | yes | Whether at least one declared parameter type of the picked method supports input generation. Null when there is no picked source method declaration. |
| `candidate_return_supported` | boolean | yes | Whether the picked method's declared return type is supported for output values. Null when there is no picked source method declaration. |
| `oracle_agreement` | text | yes | Reserved for agreement with original PIT evidence. Always null because the pipeline does not populate it. |
| `candidate_details` | text | yes | Ranked candidates not picked, stored as a JSON array of objects with methodName, declaringType, and callSource. Null when no alternatives were retained. |
| `actual_shape` | text | yes | Syntax category of the asserted actual expression: LITERAL, VARIABLE, FIELD_ACCESS, SINGLE_CALL, CHAINED_CALLS_END0ARG, CHAINED_CALLS_ENDNARG, CTOR_ONLY, CTOR_RECEIVER_CALL, OPERATOR_COMPOSITE, ARRAY_INDEX, LAMBDA_OR_METHODREF, or NONE. Null if not recorded, distinct from NONE. |
| `receiver_provenance` | text | yes | Origin of the root receiver of the asserted call: INLINE_CTOR, LOCAL_CTOR, LOCAL_CTOR_MUTATED, LOCAL_OTHER, FIELD, PARAM_OR_STATIC, or NONE. Null if not recorded, distinct from NONE. |

### `generation_clause`

Table. Only in real-world, jarvis-benchmark, jarvis-scenarios. One flattened input path condition clause in an IMPROVED generation plan.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `generalization_id` | bigint | no | Generalization whose input generation plan contains this clause, generalization.id. |
| `parameter_name` | text | no | First parameter in plan order referenced by the clause. If none matches, stores the first referenced variable name, or an empty string if there are no references. |
| `type_domain` | text | no | Type category of the attributed parameter: INTEGER, REAL, BOOLEAN, CHAR, or STRING. OBJECT is used when the parameter type is unavailable. |
| `shape` | text | no | Canonical structure of the clause expression, with operators and operand types but without variable names or literal values. |
| `consumed` | boolean | no | Whether an input generator incorporates this clause into how it produces values. All plan clauses remain in the generated input filter. |

### `generation_parameter`

Table. Only in real-world, jarvis-benchmark, jarvis-scenarios. One input parameter planned for generation by the IMPROVED algorithm, including parameters derived from symbolic temporaries.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `id` | bigint | no | Primary key. |
| `generalization_id` | bigint | no | Generalization whose input generation plan contains this parameter, generalization.id. |
| `name` | text | no | Name of the generated input parameter in the plan. |
| `declared_type` | text | no | Java type name of the input parameter before mapping it to a generator domain. |
| `type_domain` | text | no | Generator domain assigned from the Java type: INTEGER, REAL, BOOLEAN, CHAR, or STRING. |
| `symbolic_spec_present` | boolean | no | Whether any retained input path condition clause refers to this parameter. |
| `representation` | text | no | encoded if the parameter's own generator incorporates a clause. Otherwise, residual if any clause not incorporated by any generator refers to the parameter, or none if no such clause does. |

### `teralizer_corpus_metadata`

Table. Only in controlled. One named metadata entry written during database preparation to identify the installed derived schema.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `key` | text | no | Metadata entry name and primary key. The preparation code writes derived_view_revision. |
| `value` | text | no | Hexadecimal SHA-256 digest of the SQL file used to install the derived views. |

### `mv_efficiency_comparison_evosuite_vs_teralizer`

Materialized view. Only in controlled. One comparison of EvoSuite's initial suite with a Teralizer generalization variant for a project. EvoSuite timings are matched by project name.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_name` | text | yes | Name of the final directory in the project root path. |
| `evosuite_variant` | text | yes | Mutation analysis variant for EvoSuite's generated tests, always INITIAL. |
| `teralizer_variant` | text | yes | Generalization variant used for the Teralizer timing and mutation results. |
| `evosuite_runtime` | double precision | yes | Sum of all logged EvoSuite phase durations across the project's target classes, in seconds. |
| `teralizer_runtime` | double precision | yes | Sum of shared and variant-specific task runtimes for specification extraction, generalization, and the selected validation stages, in seconds. |
| `e_no_validation` | double precision | yes | Sum of EvoSuite phase durations other than JUNIT_CHECK, in seconds. |
| `t_no_validation` | double precision | yes | Sum of shared and variant-specific task runtimes for specification extraction and GENERALIZE_TESTS, in seconds. |
| `e_validation` | double precision | yes | EvoSuite total runtime minus runtime without JUNIT_CHECK, in seconds. |
| `t_validation` | double precision | yes | Teralizer total runtime minus runtime for specification extraction and generalization, in seconds. This includes initial and generalized test validation and generalized PIT collection. |
| `evosuite_detected` | numeric | yes | Percentage of covered mutants marked detected by PIT for INITIAL, from mv_mutation_results_by_project_variant. |
| `teralizer_detected` | numeric | yes | Percentage of covered mutants marked detected by PIT for the Teralizer variant, from mv_mutation_results_by_project_variant. |

### `mv_evosuite_runtime_pivoted`

Materialized view. Only in controlled. One target class in one project with EvoSuite timings from mv_evosuite_runtime, summed overall and pivoted by logged phase.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `class_id` | integer | yes | Generated class identifier from mv_evosuite_runtime, assigned by dense ranking on project ID and class name. |
| `project_id` | integer | yes | Project containing the target class, project.id. |
| `class_name` | text | yes | Fully qualified name of the class targeted by EvoSuite. |
| `total` | double precision | yes | Sum of all recorded EvoSuite phase durations for the class, in seconds. |
| `search` | numeric | yes | Total time in the SEARCH phase for the class, in seconds. |
| `inlining` | numeric | yes | Total time in the INLINING phase for the class, in seconds. |
| `minimization` | numeric | yes | Total time in the MINIMIZATION phase for the class, in seconds. |
| `coverage_analysis` | numeric | yes | Total time in the COVERAGE_ANALYSIS phase for the class, in seconds. |
| `assertion_generation` | numeric | yes | Total time in the ASSERTION_GENERATION phase for the class, in seconds. |
| `junit_check` | numeric | yes | Total time in the JUNIT_CHECK phase for the class, in seconds. |
| `writing_tests` | numeric | yes | Total time in the WRITING_TESTS phase for the class, in seconds. |
| `writing_statistics` | numeric | yes | Total time in the WRITING_STATISTICS phase for the class, in seconds. |
| `done` | numeric | yes | Total time assigned to the logged DONE status for the class, in seconds. |
| `finished` | numeric | yes | Total time assigned to the logged FINISHED status for the class, in seconds. |

### `mv_exclusions_all`

Materialized view. Only in controlled. One aggregate count across projects with generalization enabled, grouped by variant, item type, inclusion flag, and the task named in the exclusion message.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `variant` | text | yes | Generalization variant for generalization records, or SHARED for test and assertion records. |
| `level` | text | yes | Type of record counted: 1-TEST, 2-ASSERTION, or 3-GENERALIZATION. |
| `is_included` | boolean | yes | Recorded inclusion flag on the test, assertion, or generalization. True means retained and false means excluded. |
| `excluded_by` | text | yes | Task name extracted from exclusion_info between the 'Excluded by ' prefix and the first opening brace. |
| `count` | bigint | yes | Number of test, assertion, or generalization records in the group. |

### `mv_generalization_effects`

Materialized view. Only in controlled. One project and ordered variant pair with at least one newly killed mutation. The view estimates suite size and runtime after adding the killing generalizations and removing replaceable tests.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_id` | bigint | yes | Project of the variant comparison, project.id. |
| `project_name` | text | yes | Name of the final directory in the project root path. |
| `a_variant` | text | yes | Reference test suite in the mutation comparison: ORIGINAL or INITIAL. |
| `b_variant` | text | yes | Comparison variant in which the mutations were killed. It differs from a_variant and is not ORIGINAL. |
| `a_variant_order` | integer | yes | Numeric sort key for a_variant returned by variant_order(). |
| `b_variant_order` | integer | yes | Numeric sort key for b_variant returned by variant_order(). |
| `killed_mutations` | bigint | yes | Count of rows in mv_mutation_status_changes where a_variant did not detect the mutation and b_variant killed it. |
| `tests_before` | bigint | yes | Count of tests with an ORIGINAL entry in mv_test_extension, regardless of a_variant. |
| `added_tests` | bigint | yes | Count of distinct generalizations identified as killing at least one of the newly killed mutants. |
| `removed_tests` | bigint | yes | Count of a_variant tests eligible for removal. Every assertion recorded for a test must have a killing generalization for the variant pair. |
| `tests_after` | bigint | yes | Estimated test count: tests_before plus added_tests minus removed_tests. |
| `tests_delta` | bigint | yes | Signed change in test count, calculated as tests_after minus tests_before. |
| `tests_delta_pct` | numeric | yes | Signed percentage change in test count relative to tests_before. |
| `lines_before` | bigint | yes | Total source lines of the ORIGINAL test methods, based on test.line_count. |
| `added_lines` | bigint | yes | Total source lines in the generated Java files for the killing generalizations. |
| `removed_lines` | bigint | yes | Total source lines of the replaceable test methods, based on test.line_count. |
| `lines_after` | bigint | yes | Estimated source line count: lines_before plus added_lines minus removed_lines. |
| `lines_delta` | bigint | yes | Signed change in source line count, calculated as lines_after minus lines_before. |
| `lines_delta_pct` | numeric | yes | Signed percentage change in source line count relative to lines_before. |
| `runtime_before` | double precision | yes | Sum of ORIGINAL test runtimes from mv_test_extension, in seconds. |
| `added_runtime` | double precision | yes | Sum of the killing generalizations' runtimes from mv_generalization_extension, in seconds. |
| `removed_runtime` | double precision | yes | Sum of replaceable test runtimes for a_variant from mv_test_extension, in seconds. |
| `runtime_after` | double precision | yes | Estimated suite execution time, in seconds: runtime_before plus added_runtime minus removed_runtime. |
| `runtime_delta` | double precision | yes | Signed change in suite execution time, in seconds, calculated as runtime_after minus runtime_before. |
| `runtime_delta_pct` | double precision | yes | Signed percentage change in suite execution time relative to runtime_before. |

### `mv_mutation_detection_comparison`

Materialized view. Only in controlled. One project and PIT detection outcome for covered mutants in IMPROVED_200_TRIES. Averages and medians use the joined rows of covering tests, assertions, and generalizations rather than one row per mutant.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_id` | bigint | yes | Project containing the analyzed mutants, project.id. |
| `project_name` | text | yes | Name of the final directory in the project root path. |
| `variant` | text | yes | Mutation analysis variant, always IMPROVED_200_TRIES. |
| `is_detected` | boolean | yes | PIT's recorded detection flag for the mutants in the group, not a check for KILLED status. |
| `count` | bigint | yes | Number of distinct mutation report rows in mv_pit_mutation_report in the group. |
| `avg_test_runtime` | double precision | yes | Mean covering test runtime from mv_mutation_covering_tests, in seconds. |
| `median_test_runtime` | double precision | yes | Median covering test runtime from mv_mutation_covering_tests, in seconds. |
| `avg_model_java_size` | numeric | yes | Mean length of the covering assertions' Java input model expressions, measured in UTF-16 code units. |
| `median_model_java_size` | double precision | yes | Median length of the covering assertions' Java input model expressions, measured in UTF-16 code units. |
| `avg_model_operation_count` | numeric | yes | Mean count of operation nodes in the covering assertions' input models. |
| `median_model_operation_count` | double precision | yes | Median count of operation nodes in the covering assertions' input models. |
| `avg_generalization_runtime` | double precision | yes | Mean covering generalization runtime from mv_mutation_covering_generalizations, in seconds. |
| `median_generalization_runtime` | double precision | yes | Median covering generalization runtime from mv_mutation_covering_generalizations, in seconds. |
| `avg_total_constraint_count` | numeric | yes | Mean count of input constraint clauses for covering generalizations. |
| `median_total_constraint_count` | double precision | yes | Median count of input constraint clauses for covering generalizations. |
| `avg_used_constraint_count` | numeric | yes | Mean count of input constraint clauses incorporated into input generation for covering generalizations. |
| `median_used_constraint_count` | double precision | yes | Median count of input constraint clauses incorporated into input generation for covering generalizations. |
| `avg_used_constraint_pct` | numeric | yes | Mean percentage of input constraints used, dividing used by total with integer division before averaging. Zero total counts as 100% and partial use counts as 0%. |
| `median_used_constraint_pct` | double precision | yes | Median percentage of input constraints used, dividing used by total with floating-point division. Zero total counts as 100%. |

### `mv_mutation_results_by_project_variant`

Materialized view. Only in controlled. One summary of PIT mutation report records for a project, report stage, and test suite variant. Each summary is compared with the same project's INITIAL suite, and projects without an INITIAL summary are omitted.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_id` | bigint | yes | Project that produced the PIT mutation report records, project.id. |
| `project_name` | text | yes | Last path component of project.root_path. |
| `variant` | text | yes | Test suite label from pit_mutation_report.variant, or ORIGINAL or INITIAL from the report stage when no variant is set. |
| `total` | bigint | yes | Count of pit_mutation_report records in the group. |
| `covered` | bigint | yes | Count of mutants with any PIT status other than 'NO_COVERAGE'. |
| `uncovered` | bigint | yes | Count of mutants with PIT status 'NO_COVERAGE'. |
| `survived` | bigint | yes | Count of mutants with PIT status 'SURVIVED'. |
| `detected` | bigint | yes | Count of mutants flagged as detected in pit_mutation_report.is_detected, regardless of status. |
| `killed` | bigint | yes | Count of mutants with PIT status 'KILLED'. |
| `timed_out` | bigint | yes | Count of mutants with PIT status 'TIMED_OUT'. |
| `memory_error` | bigint | yes | Count of mutants with PIT status 'MEMORY_ERROR'. |
| `run_error` | bigint | yes | Count of mutants with PIT status 'RUN_ERROR'. |
| `covered_diff` | bigint | yes | Variant covered count minus the INITIAL covered count. |
| `uncovered_diff` | bigint | yes | Variant uncovered count minus the INITIAL uncovered count. |
| `survived_diff` | bigint | yes | Variant survived count minus the INITIAL survived count. |
| `detected_diff` | bigint | yes | Variant detected count minus the INITIAL detected count. |
| `killed_diff` | bigint | yes | Variant killed count minus the INITIAL killed count. |
| `timed_out_diff` | bigint | yes | Variant timed-out count minus the INITIAL timed-out count. |
| `memory_error_diff` | bigint | yes | Variant memory-error count minus the INITIAL memory-error count. |
| `run_error_diff` | bigint | yes | Variant run-error count minus the INITIAL run-error count. |
| `covered_pct` | numeric | yes | Covered count as a percent of all reported mutants, rounded to two decimal places. |
| `uncovered_pct` | numeric | yes | Uncovered count as a percent of all reported mutants, rounded to two decimal places. |
| `survived_pct` | numeric | yes | Survived count as a percent of all reported mutants, rounded to two decimal places. |
| `detected_pct` | numeric | yes | Detected count as a percent of all reported mutants, rounded to two decimal places. |
| `killed_pct` | numeric | yes | Killed count as a percent of all reported mutants, rounded to two decimal places. |
| `timed_out_pct` | numeric | yes | Timed-out count as a percent of all reported mutants, rounded to two decimal places. |
| `memory_error_pct` | numeric | yes | Memory-error count as a percent of all reported mutants, rounded to two decimal places. |
| `run_error_pct` | numeric | yes | Run-error count as a percent of all reported mutants, rounded to two decimal places. |
| `survived_of_covered_pct` | numeric | yes | Survived count divided by the covered count, as a percent rounded to two decimal places. |
| `detected_of_covered_pct` | numeric | yes | Detected count divided by the covered count, as a percent rounded to two decimal places. |
| `killed_of_covered_pct` | numeric | yes | Killed count divided by the covered count, as a percent rounded to two decimal places. |
| `timed_out_of_covered_pct` | numeric | yes | Timed-out count divided by the covered count, as a percent rounded to two decimal places. |
| `memory_error_of_covered_pct` | numeric | yes | Memory-error count divided by the covered count, as a percent rounded to two decimal places. |
| `run_error_of_covered_pct` | numeric | yes | Run-error count divided by the covered count, as a percent rounded to two decimal places. |
| `covered_pct_diff` | numeric | yes | Variant covered percent minus the INITIAL covered percent, in percentage points. |
| `uncovered_pct_diff` | numeric | yes | Variant uncovered percent minus the INITIAL uncovered percent, in percentage points. |
| `survived_pct_diff` | numeric | yes | Variant survived percent minus the INITIAL survived percent, in percentage points. |
| `detected_pct_diff` | numeric | yes | Variant detected percent minus the INITIAL detected percent, in percentage points. |
| `killed_pct_diff` | numeric | yes | Variant killed percent minus the INITIAL killed percent, in percentage points. |
| `timed_out_pct_diff` | numeric | yes | Variant timed-out percent minus the INITIAL timed-out percent, in percentage points. |
| `memory_error_pct_diff` | numeric | yes | Variant memory-error percent minus the INITIAL memory-error percent, in percentage points. |
| `run_error_pct_diff` | numeric | yes | Variant run-error percent minus the INITIAL run-error percent, in percentage points. |
| `survived_of_covered_pct_diff` | numeric | yes | Variant survived percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `detected_of_covered_pct_diff` | numeric | yes | Variant detected percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `killed_of_covered_pct_diff` | numeric | yes | Variant killed percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `timed_out_of_covered_pct_diff` | numeric | yes | Variant timed-out percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `memory_error_of_covered_pct_diff` | numeric | yes | Variant memory-error percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `run_error_of_covered_pct_diff` | numeric | yes | Variant run-error percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |

### `mv_mutation_results_by_project_variant_mutator`

Materialized view. Only in controlled. One summary of PIT mutation report records for a project, report stage, test suite variant, and full mutator name. Only summaries with an INITIAL match for the same project and short mutator name are included.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_id` | bigint | yes | Project that produced the PIT mutation report records, project.id. |
| `project_name` | text | yes | Last path component of project.root_path. |
| `variant` | text | yes | Test suite label from pit_mutation_report.variant, or ORIGINAL or INITIAL from the report stage when no variant is set. |
| `mutator` | text | yes | Last dot-separated component of pit_mutation_report.mutator, the PIT mutation operator name. The view groups by the full name but matches the INITIAL summary by this short name. |
| `total` | bigint | yes | Count of pit_mutation_report records in the group. |
| `covered` | bigint | yes | Count of mutants with any PIT status other than 'NO_COVERAGE'. |
| `uncovered` | bigint | yes | Count of mutants with PIT status 'NO_COVERAGE'. |
| `survived` | bigint | yes | Count of mutants with PIT status 'SURVIVED'. |
| `detected` | bigint | yes | Count of mutants flagged as detected in pit_mutation_report.is_detected, regardless of status. |
| `killed` | bigint | yes | Count of mutants with PIT status 'KILLED'. |
| `timed_out` | bigint | yes | Count of mutants with PIT status 'TIMED_OUT'. |
| `memory_error` | bigint | yes | Count of mutants with PIT status 'MEMORY_ERROR'. |
| `run_error` | bigint | yes | Count of mutants with PIT status 'RUN_ERROR'. |
| `covered_diff` | bigint | yes | Variant covered count minus the INITIAL covered count. |
| `uncovered_diff` | bigint | yes | Variant uncovered count minus the INITIAL uncovered count. |
| `survived_diff` | bigint | yes | Variant survived count minus the INITIAL survived count. |
| `detected_diff` | bigint | yes | Variant detected count minus the INITIAL detected count. |
| `killed_diff` | bigint | yes | Variant killed count minus the INITIAL killed count. |
| `timed_out_diff` | bigint | yes | Variant timed-out count minus the INITIAL timed-out count. |
| `memory_error_diff` | bigint | yes | Variant memory-error count minus the INITIAL memory-error count. |
| `run_error_diff` | bigint | yes | Variant run-error count minus the INITIAL run-error count. |
| `covered_pct` | numeric | yes | Covered count as a percent of all reported mutants in the group, rounded to two decimal places. |
| `uncovered_pct` | numeric | yes | Uncovered count as a percent of all reported mutants in the group, rounded to two decimal places. |
| `survived_pct` | numeric | yes | Survived count as a percent of all reported mutants in the group, rounded to two decimal places. |
| `detected_pct` | numeric | yes | Detected count as a percent of all reported mutants in the group, rounded to two decimal places. |
| `killed_pct` | numeric | yes | Killed count as a percent of all reported mutants in the group, rounded to two decimal places. |
| `timed_out_pct` | numeric | yes | Timed-out count as a percent of all reported mutants in the group, rounded to two decimal places. |
| `memory_error_pct` | numeric | yes | Memory-error count as a percent of all reported mutants in the group, rounded to two decimal places. |
| `run_error_pct` | numeric | yes | Run-error count as a percent of all reported mutants in the group, rounded to two decimal places. |
| `survived_of_covered_pct` | numeric | yes | Survived count divided by the covered count, as a percent rounded to two decimal places. Zero if no mutants were covered. |
| `detected_of_covered_pct` | numeric | yes | Detected count divided by the covered count, as a percent rounded to two decimal places. Zero if no mutants were covered. |
| `killed_of_covered_pct` | numeric | yes | Killed count divided by the covered count, as a percent rounded to two decimal places. Zero if no mutants were covered. |
| `timed_out_of_covered_pct` | numeric | yes | Timed-out count divided by the covered count, as a percent rounded to two decimal places. Zero if no mutants were covered. |
| `memory_error_of_covered_pct` | numeric | yes | Memory-error count divided by the covered count, as a percent rounded to two decimal places. Zero if no mutants were covered. |
| `run_error_of_covered_pct` | numeric | yes | Run-error count divided by the covered count, as a percent rounded to two decimal places. Zero if no mutants were covered. |
| `covered_pct_diff` | numeric | yes | Variant covered percent minus the INITIAL covered percent, in percentage points. |
| `uncovered_pct_diff` | numeric | yes | Variant uncovered percent minus the INITIAL uncovered percent, in percentage points. |
| `survived_pct_diff` | numeric | yes | Variant survived percent minus the INITIAL survived percent, in percentage points. |
| `detected_pct_diff` | numeric | yes | Variant detected percent minus the INITIAL detected percent, in percentage points. |
| `killed_pct_diff` | numeric | yes | Variant killed percent minus the INITIAL killed percent, in percentage points. |
| `timed_out_pct_diff` | numeric | yes | Variant timed-out percent minus the INITIAL timed-out percent, in percentage points. |
| `memory_error_pct_diff` | numeric | yes | Variant memory-error percent minus the INITIAL memory-error percent, in percentage points. |
| `run_error_pct_diff` | numeric | yes | Variant run-error percent minus the INITIAL run-error percent, in percentage points. |
| `survived_of_covered_pct_diff` | numeric | yes | Variant survived percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `detected_of_covered_pct_diff` | numeric | yes | Variant detected percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `killed_of_covered_pct_diff` | numeric | yes | Variant killed percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `timed_out_of_covered_pct_diff` | numeric | yes | Variant timed-out percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `memory_error_of_covered_pct_diff` | numeric | yes | Variant memory-error percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |
| `run_error_of_covered_pct_diff` | numeric | yes | Variant run-error percent relative to covered mutants minus the same percent for INITIAL, in percentage points. |

### `mv_runtime_comparison_test_vs_generalization`

Materialized view. Only in controlled. One included generalization paired with its included source test, comparing the generalization's execution time with the test's INITIAL execution time.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_id` | bigint | yes | Project of the source test, project.id. |
| `project_name` | text | yes | Name of the final directory in the project root path. |
| `test_id` | bigint | yes | Source test of the generalization, test.id. |
| `generalization_id` | bigint | yes | Generalization compared with the source test, generalization.id. |
| `variant` | text | yes | Configured generalization variant of the generated test. |
| `variant_order` | integer | yes | Numeric sort key for the generalization variant returned by variant_order(). |
| `t_runtime` | double precision | yes | Sum of the source test's INITIAL JUnit report runtimes from mv_test_extension, in seconds. |
| `g_runtime` | double precision | yes | Sum of the generalization's JUnit report runtimes from mv_generalization_extension, in seconds. |
| `runtime_diff` | double precision | yes | Generalization runtime minus INITIAL source test runtime, in seconds. |
| `tries` | integer | yes | Configured number of jqwik tries for the project and variant, from mv_variant. |
| `runtime_diff_per_try` | double precision | yes | Runtime difference divided by the configured try count, in seconds per try. |

### `mv_teralizer_runtime_by_stage`

Materialized view. Only in controlled. One stage group and variant with recorded task time for a project in v_projects_successes with test generalization enabled.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_id` | bigint | yes | Project whose tasks contributed runtime, project.id. |
| `project_name` | text | yes | Name of the final directory in the project root path, returned by project_name(). |
| `base_project_name` | text | yes | Final slash-delimited component of project.root_path, computed with split_part(). |
| `stage_group` | text | yes | Pipeline grouping of task stages: Stage 1 + 2 for setup and test analysis, Stage 3 for JPF work, Stage 4 for generalization, and Stage 5 for validation and evaluation. |
| `variant` | text | yes | Variant recorded on the task, or SHARED for tasks not assigned to a variant. |
| `variant_order` | integer | yes | Numeric sort key returned by variant_order() for the task's variant. SHARED sorts at 0. |
| `total_runtime` | double precision | yes | Sum of recorded task execution times for the project, stage group, and variant, in seconds. |

### `v_project_failures`

View. Only in controlled. One project with a non-successful project-level task. The view selects one such task at the lowest stage step and ignores tasks tied to a test, assertion, or generalization.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_name` | text | yes | Name of the final directory in the project root path. |
| `project_id` | bigint | yes | Project with the selected task, project.id. |
| `step` | integer | yes | Numeric step assigned to the selected task's processing stage. |
| `stage` | text | yes | Pipeline stage name of the selected task, from task.stage. |
| `status` | text | yes | Recorded status of the selected task: FAILED or IN_PROGRESS. |
| `info` | text | yes | Messages recorded by the selected task, including the exception stack trace on failure. |

### `v_projects_successes`

View. Only in controlled. One project for which all recorded project-level tasks succeeded and at least one reached COLLECT_PIT_DATA_GENERALIZED. Tasks tied to a test, assertion, or generalization are not checked.

| Column | Type | Null | Meaning |
| --- | --- | --- | --- |
| `project_name` | text | yes | Name of the final directory in the project root path. |
| `project_id` | bigint | yes | Project satisfying the success criteria, project.id. |

## Result CSV files

A report run writes each file below its output directory, and
`reports/provenance.json` there records the inputs, the producing commit and the
checksum of every file. The reference copies are in
`analysis/output/original/data/`.

### `tab-dataset-statistics.csv`

Report `dataset`, `build/dataset/tab-dataset-statistics.csv`. The dataset characteristics table uses one row per controlled project and three RepoReapers summary rows. RepoReapers means and medians are truncated to integers.

| Column | Meaning |
| --- | --- |
| `project` | Project identifier or RepoReapers summary label: total, mean, or median. |
| `main_files` | Count of Java source files in the selected implementation directories. |
| `main_classes` | Count of class and enum declarations matched by a text pattern in the selected implementation files. |
| `main_sloc` | Count of implementation source lines classified as code, excluding blank and comment-only lines. |
| `test_files` | Count of Java source files in the selected test directories, including EvoSuite tests but excluding JPF instrumentation and Teralizer generalizations. |
| `test_classes` | Count of class and enum declarations matched by a text pattern in the selected test files. |
| `test_sloc` | Count of test source lines classified as code, excluding blank and comment-only lines. |
| `test_methods` | Count of test records in the corpus, not a count of source declarations. The RepoReapers summaries use per-project counts for selected Maven projects with test generalization enabled and recorded tests. |

### `rq0-breadth-summary.csv`

Report `rq0`, `build/rq0/rq0-breadth-summary.csv`. One row per Apache Commons project in the JARVIS benchmark and a total over available values. Feeds the RQ0 applicability breadth table.

| Column | Meaning |
| --- | --- |
| `project` | Benchmark project name without its snapshot date or census suffix, or the all-projects total label. |
| `jarvis_successful_pbt_pvc` | Sum of published parameter value coverage (PVC) counts for the JARVIS scenarios reported for the project. Empty if JARVIS reports no case for the project. |
| `jarvis_successful_muts` | Count of distinct methods under test represented by generalized tests in the cases JARVIS reports. Empty if JARVIS reports no case for the project. |
| `aggregate_pvc` | Count of distinct values from passing IMPROVED_100_TRIES tests with a FULL diagnostic, summed by method under test and parameter. Empty if no census measurement is available for the row. |
| `sound_muts` | Count of distinct methods under test identified by class, method name, and available parameter types for passing IMPROVED_100_TRIES tests with a FULL diagnostic. Empty if no census measurement is available for the row. |

### `rq0-pvc-budget.csv`

Report `rq0`, `build/rq0/rq0-pvc-budget.csv`. One row per Improved sampling budget for the same ten scenario tests. Feeds the RQ0 table comparing parameter value coverage and mutation scores across budgets.

| Column | Meaning |
| --- | --- |
| `variant` | Improved variant identifier, including its configured sampling budget of 100, 200, or 1,000 tries. |
| `probes` | Count of passing generalized test runs with a FULL diagnostic included in the parameter value coverage calculation. |
| `total_pvc` | Sum of the distinct input value counts per parameter per included test run, without deduplicating across runs. |
| `killed_mutants` | Count of distinct PIT mutants marked detected, summed across fixture projects. Empty if the budget has no mutation results. |
| `covered_mutants` | Count of distinct PIT mutants with a status other than NO_COVERAGE or NON_VIABLE, summed across fixture projects. Empty if the budget has no mutation results. |
| `covered_mutation_score` | Detected mutant count divided by covered mutant count, as a fraction from 0 to 1 without fixed decimal-place rounding. Empty if mutation results are unavailable or no mutants were covered. |

### `rq0-table2-comparison.csv`

Report `rq0`, `build/rq0/rq0-table2-comparison.csv`. One row per scenario from Table 2 of the JARVIS publication. Feeds the paper's RQ0 table comparing original, JARVIS, and Teralizer parameter value coverage.

| Column | Meaning |
| --- | --- |
| `scenario_number` | Scenario number from 1 to 10 in JARVIS Table 2 order. |
| `table_row` | Scenario label from JARVIS Table 2, naming the original test class and the test method when specified. |
| `original_cut_pvc` | Count of distinct parameter values exercised by the original unit tests, as reported by JARVIS. |
| `jarvis_pbt_pvc` | Count of distinct parameter values sampled by the synthesized properties alone, as reported by JARVIS. The IntervalTest property stopped on its second sample. |
| `suite_pvc` | Count of distinct values per method under test and parameter position in the union of captured original test inputs and matching IMPROVED_100_TRIES value logs. Empty if no passing generalized run with a FULL diagnostic matched the scenario. |

### `tab-detections-per-mutator.csv`

Report `rq1`, `build/rq1/tab-detections-per-mutator.csv`. One row per PIT mutation operator, pooling counts across controlled projects. Feeds the RQ1 table of mutant counts and detection rates per operator.

| Column | Meaning |
| --- | --- |
| `mutator` | Short name of the PIT mutation operator, with package and Mutator text removed and conditional operator names normalized. |
| `total_mutants` | Count of INITIAL mutant report rows for the operator, including uncovered mutants. |
| `percent` | Percentage of all INITIAL mutant report rows from this operator, from 0 to 100 and rounded to two decimal places. |
| `INITIAL` | Percentage of covered mutants PIT marked detected for the initial suite, from 0 to 100 and rounded to two decimal places. Zero if no mutants were covered. |
| `NAIVE_200_TRIES` | Percentage of covered mutants PIT marked detected for Naive with 200 tries, from 0 to 100 and rounded to two decimal places. Empty if the variant has data for other operators but not this one. |
| `detected_diff_naive_200_tries` | Naive detection rate minus the initial rate, in percentage points rounded to two decimal places. Empty when the Naive rate is empty. |
| `IMPROVED_200_TRIES` | Percentage of covered mutants PIT marked detected for Improved with 200 tries, from 0 to 100 and rounded to two decimal places. Empty if the variant has data for other operators but not this one. |
| `detected_diff_improved_200_tries` | Improved detection rate minus the initial rate, in percentage points rounded to two decimal places. Empty when the Improved rate is empty. |

### `tab-mutants-per-project.csv`

Report `rq1`, `build/rq1/tab-mutants-per-project.csv`. One row per controlled project for the INITIAL suite. Feeds the RQ1 table of test and class inclusion and mutant coverage.

| Column | Meaning |
| --- | --- |
| `project` | Controlled project identifier, including the EvoSuite search budget where applicable. |
| `included_tests` | Count of JUnit test report rows from the INITIAL suite. |
| `test_inclusion_share` | INITIAL JUnit report count divided by the project's count of test records, as a fraction from 0 to 1. |
| `included_classes` | Count of INITIAL JaCoCo class report rows with at least one instruction covered, excluding abbreviated class names containing three consecutive dots. |
| `class_inclusion_share` | Included class count divided by the implementation class and enum count used in the dataset statistics, as a fraction from 0 to 1. |
| `total` | Count of INITIAL PIT mutant report rows, including both covered and uncovered mutants. |
| `covered` | Count of INITIAL mutant report rows whose PIT status is not NO_COVERAGE. |
| `covered_share` | Covered mutant count divided by total mutant count, as a fraction from 0 to 1. |
| `uncovered` | Count of INITIAL mutant report rows with PIT status NO_COVERAGE. |
| `uncovered_share` | Uncovered mutant count divided by total mutant count, as a fraction from 0 to 1. |

### `tab-mutation-score-improvements.csv`

Report `rq1`, `build/rq1/tab-mutation-score-improvements.csv`. One row per controlled project and suite variant. Feeds the RQ1 mutation-score improvement table and the mutation detection comparison figure.

| Column | Meaning |
| --- | --- |
| `row_key` | Project identifier and variant joined by a colon. |
| `project` | Controlled project identifier, including the EvoSuite search budget where applicable. |
| `strategy` | Suite variant identifier, either INITIAL or a generalization strategy and its configured try budget. |
| `detected_of_covered_pct` | Percentage of mutants with a status other than NO_COVERAGE that PIT marked detected, from 0 to 100 and rounded to two decimal places. |
| `absolute_improvement` | Variant score minus the project's INITIAL score, in percentage points rounded to two decimal places. Zero for INITIAL. |
| `relative_improvement` | Absolute change divided by the project's INITIAL score, expressed as a signed percent rounded to two decimal places. Zero for INITIAL or a zero initial score. |

### `tab-mutation-detection-comparison.csv`

Report `rq2`, `build/rq2/tab-mutation-detection-comparison.csv`. One row per project and PIT detection outcome for IMPROVED_200_TRIES mutants whose status is not NO_COVERAGE. Feeds the RQ2 detected and undetected mutant model properties table.

| Column | Meaning |
| --- | --- |
| `project_name` | Controlled project identifier, including the EvoSuite search budget where applicable. |
| `is_detected` | Whether PIT's detection flag is set, recorded as yes or no. This is not a check for KILLED status. |
| `count` | Count of distinct mutant report rows in the detection group. |
| `avg_model_operation_count` | Mean operation node count in the covering assertions' input models, truncated to an integer. The mean uses the joined coverage rows rather than one value per mutant. |
| `median_model_operation_count` | Median operation node count in the covering assertions' input models across the joined coverage rows, truncated to an integer. |
| `avg_total_constraint_count` | Mean count of input constraint clauses of covering generalizations across the joined coverage rows, truncated to an integer. |
| `median_total_constraint_count` | Median count of input constraint clauses of covering generalizations across the joined coverage rows, truncated to an integer. |
| `avg_used_constraint_pct` | Mean percentage of input clauses used in generators across the joined coverage rows, from 0 to 100 and truncated to an integer. SQL integer division counts partial use as 0% and no constraints as 100%. |
| `median_used_constraint_pct` | Median percentage of input clauses used in generators across the joined coverage rows, from 0 to 100 and truncated to an integer. Uses fractional division before taking the median and counts no constraints as 100%. |

### `tab-lines-per-project.csv`

Report `rq3`, `build/rq3/tab-lines-per-project.csv`. One row per controlled project and 200-try variant that killed mutants the ORIGINAL suite did not detect. Feeds the RQ3 test source lines table.

| Column | Meaning |
| --- | --- |
| `project` | Controlled project identifier, including the EvoSuite search budget where applicable. |
| `b_variant` | Comparison suite variant, either NAIVE_200_TRIES or IMPROVED_200_TRIES. |
| `lines_before` | Total line count of the ORIGINAL test methods as rendered by Spoon. |
| `added_lines` | Total line count of the generated Java files for tests that killed at least one newly killed mutant, including blank lines. |
| `removed_lines` | Total line count of replaceable original test methods as rendered by Spoon. Every recorded assertion in such a method has a killing generalization. |
| `lines_after` | Estimated line count after adding generated test files and removing replaceable test methods: original plus added minus removed. |
| `lines_delta` | Signed change in line count, calculated as the estimated count after minus the count before. |
| `lines_delta_pct` | Signed percent change in line count relative to the original count, rounded to one decimal place. Zero if the original count is zero. |

### `tab-runtime-per-project.csv`

Report `rq3`, `build/rq3/tab-runtime-per-project.csv`. One row per controlled project and 200-try variant that killed mutants the ORIGINAL suite did not detect. Feeds the RQ3 test suite runtime table.

| Column | Meaning |
| --- | --- |
| `project` | Controlled project identifier, including the EvoSuite search budget where applicable. |
| `b_variant` | Comparison suite variant, either NAIVE_200_TRIES or IMPROVED_200_TRIES. |
| `runtime_before` | Sum of ORIGINAL JUnit test runtimes in seconds, rounded to two decimal places. |
| `added_runtime` | Sum of JUnit runtimes for generalized tests that killed at least one newly killed mutant, in seconds rounded to two decimal places. |
| `removed_runtime` | Sum of JUnit runtimes for replaceable original tests, in seconds rounded to two decimal places. Every recorded assertion in such a test has a killing generalization. |
| `runtime_after` | Estimated test suite runtime in seconds: original plus added minus removed, rounded to two decimal places. |
| `runtime_delta` | Signed runtime change in seconds, calculated as the estimated runtime after minus the runtime before and rounded to two decimal places. |
| `runtime_delta_pct` | Signed percent change in runtime relative to the original runtime, rounded to one decimal place. Zero if the original runtime is zero. |

### `tab-tests-per-project.csv`

Report `rq3`, `build/rq3/tab-tests-per-project.csv`. One row per controlled project and 200-try variant that killed mutants the ORIGINAL suite did not detect. Feeds the RQ3 test counts table.

| Column | Meaning |
| --- | --- |
| `project` | Controlled project identifier, including the EvoSuite search budget where applicable. |
| `b_variant` | Comparison suite variant, either NAIVE_200_TRIES or IMPROVED_200_TRIES. |
| `tests_before` | Count of tests with an ORIGINAL entry in the test runtime summary. |
| `added_tests` | Count of distinct generalizations that killed at least one newly killed mutant. |
| `removed_tests` | Count of replaceable original tests for which every recorded assertion has a killing generalization. |
| `tests_after` | Estimated test count after generalization and reduction: original plus added minus removed. |
| `tests_delta` | Signed change in test count, calculated as the estimated count after minus the count before. |
| `tests_delta_pct` | Signed percent change in test count relative to the original count, rounded to one decimal place. Zero if the original count is zero. |

### `test_runtime_differences.csv`

Report `rq3`, `build/rq3/test_runtime_differences.csv`. One row per generalization variant, pooled across included generated tests and their included source tests. Feeds the RQ3 runtime overhead table and the runtime comparison figure.

| Column | Meaning |
| --- | --- |
| `variant` | Generalization variant identifier, specifying the strategy and try budget or BASELINE. |
| `mean_t_runtime_ms` | Mean INITIAL JUnit runtime of source tests across the matched pairs, in milliseconds rounded to two decimal places. |
| `mean_g_runtime_ms` | Mean JUnit runtime of generalized tests across the matched pairs, in milliseconds rounded to two decimal places. |
| `mean_runtime_diff_ms` | Mean of generalized runtime minus INITIAL source test runtime across matched pairs, in milliseconds rounded to two decimal places. |
| `ratio_of_mean_runtimes` | Mean generalized runtime divided by mean INITIAL source test runtime, as a unitless ratio rounded to two decimal places. |
| `tries` | Minimum configured jqwik try count across the matched pairs for the variant. |
| `mean_runtime_diff_per_try_ms` | Mean of each paired runtime difference divided by its configured try count, in milliseconds per try rounded to two decimal places. |

### `evosuite_runtime_analysis.csv`

Report `rq4`, `build/rq4/evosuite_runtime_analysis.csv`. One row per project and EvoSuite search budget, with mean runtimes across its target classes. Feeds the paper's EvoSuite phase-runtime table and uses the same class timings as the phase-runtime figure.

| Column | Meaning |
| --- | --- |
| `project_name` | Final directory name of the project workspace, including the EvoSuite configuration suffix. |
| `search_budget` | Configured EvoSuite search budget in seconds, rounded to two decimal places. |
| `total` | Mean total logged EvoSuite runtime per target class in seconds, rounded to two decimal places. Includes phases not shown separately in this file. |
| `search` | Mean time per target class spent searching for tests, in seconds rounded to two decimal places. An unrecorded phase contributes zero. |
| `inlining` | Mean time per target class spent inlining constants, in seconds rounded to two decimal places. An unrecorded phase contributes zero. |
| `minimization` | Mean time per target class spent minimizing generated tests, in seconds rounded to two decimal places. An unrecorded phase contributes zero. |
| `coverage_analysis` | Mean time per target class spent analyzing coverage, in seconds rounded to two decimal places. An unrecorded phase contributes zero. |
| `assertion_generation` | Mean time per target class spent generating assertions, in seconds rounded to two decimal places. An unrecorded phase contributes zero. |
| `junit_check` | Mean time per target class spent checking generated tests with JUnit, in seconds rounded to two decimal places. An unrecorded phase contributes zero. |
| `writing_tests` | Mean time per target class spent writing generated tests, in seconds rounded to two decimal places. An unrecorded phase contributes zero. |

### `tab-pareto-commons.csv`

Report `rq4`, `build/rq4/tab-pareto-commons.csv`. One row per EvoSuite-only or EvoSuite-plus-Teralizer configuration on the commons-utils runtime versus mutant detection front. Feeds the paper's commons-utils Pareto-points table and lists the front shown in the efficiency figure.

| Column | Meaning |
| --- | --- |
| `point` | Point number starting at 1, in order of increasing runtime before rounding. |
| `evosuite_budget` | EvoSuite search budget in seconds with an s suffix, taken from the project workspace name. The report writes 'unknown' if it cannot parse the budget. |
| `teralizer_variant` | Generalization variant key, including the strategy and jqwik try limit. Empty for EvoSuite-only points. |
| `detection_rate` | Percentage of covered mutants marked detected by PIT, from 0 to 100 and rounded to one decimal place. Uses the initial EvoSuite suite or the corresponding Teralizer variant. |
| `runtime_seconds` | Sum of logged EvoSuite phase times over target classes, plus selected Teralizer task times for a combined point, rounded to the nearest second. The Teralizer times cover specification extraction, generalization, and selected validation tasks. |

### `tab-pareto-eqbench.csv`

Report `rq4`, `build/rq4/tab-pareto-eqbench.csv`. One row per EvoSuite-only or EvoSuite-plus-Teralizer configuration on the eqbench runtime versus mutant detection front. Feeds the paper's eqbench Pareto-points table and lists the front shown in the efficiency figure.

| Column | Meaning |
| --- | --- |
| `point` | Point number starting at 1, in order of increasing runtime before rounding. |
| `evosuite_budget` | EvoSuite search budget in seconds with an s suffix, taken from the project workspace name. The report writes 'unknown' if it cannot parse the budget. |
| `teralizer_variant` | Generalization variant key, including the strategy and jqwik try limit. Empty for EvoSuite-only points. |
| `detection_rate` | Percentage of covered mutants marked detected by PIT, from 0 to 100 and rounded to one decimal place. Uses the initial EvoSuite suite or the corresponding Teralizer variant. |
| `runtime_seconds` | Sum of logged EvoSuite phase times over target classes, plus selected Teralizer task times for a combined point, rounded to the nearest second. The Teralizer times cover specification extraction, generalization, and selected validation tasks. |

### `tab-teralizer-runtimes.csv`

Report `rq4`, `build/rq4/tab-teralizer-runtimes.csv`. One row per successful project workspace with test generalization enabled. Feeds the paper's table of total Teralizer runtime across all strategies.

| Column | Meaning |
| --- | --- |
| `project_name` | Standardized project workspace name that identifies the source suite and, for EvoSuite suites, the search budget. |
| `seconds` | Recorded wall-clock time for the Teralizer invocation in seconds, rounded to one decimal place. |
| `hours` | The same wall-clock time converted from the unrounded seconds to hours and rounded to two decimal places. |

### `tab-teralizer-stage-runtimes.csv`

Report `rq4`, `build/rq4/tab-teralizer-stage-runtimes.csv`. One row per successful project workspace and generalization variant, including shared time for Stages 1 to 3 but not for Stage 5. Feeds the paper's pipeline-stage runtime table and uses the same task timings as the stage-runtime figure.

| Column | Meaning |
| --- | --- |
| `row_key` | Standardized project name and generalization variant key joined by a colon. |
| `project_name` | Standardized project workspace name that identifies the source suite and, for EvoSuite suites, the search budget. |
| `variant` | Generalization variant key, such as BASELINE or IMPROVED_200_TRIES. |
| `stage_1_2` | Sum of task times for project setup, original-test execution and collection, test analysis, and filtering, in seconds rounded to one decimal place. The same project time is repeated for every variant. |
| `stage_3` | Sum of task times for JPF instrumentation, build, specification extraction, analysis, and cleanup, in seconds rounded to one decimal place. The same project time is repeated for every variant. |
| `stage_4` | Sum of generalization and generalization cleanup task times for this variant, in seconds rounded to one decimal place. |
| `stage_5` | Sum of validation and evaluation task times for this variant, in seconds rounded to one decimal place. Excludes project-wide Stage 5 work. |
| `stage_1_2_share` | Stages 1 and 2 time divided by the row total before rounding, as a fraction from 0 to 1 rounded to six decimal places. |
| `stage_3_share` | Stage 3 time divided by the row total before rounding, as a fraction from 0 to 1 rounded to six decimal places. |
| `stage_4_share` | Stage 4 time divided by the row total before rounding, as a fraction from 0 to 1 rounded to six decimal places. |
| `stage_5_share` | Stage 5 time divided by the row total before rounding, as a fraction from 0 to 1 rounded to six decimal places. |
| `total` | Sum of the four stage times before rounding, in seconds rounded to one decimal place. |

### `tab-exclusions-breakdown.csv`

Report `rq5`, `build/rq5/tab-exclusions-breakdown.csv`. One row for all tests, one for all assertions, and one per generalization variant in the controlled projects with generalization enabled. Feeds the paper's inclusion and exclusion breakdown table.

| Column | Meaning |
| --- | --- |
| `strategy` | All for tests and assertions, or the generalization variant key for generalization attempts. |
| `level` | Kind of item counted: Test, Assertion, or Generalization. |
| `total` | Count of all items for this level and strategy, including excluded items. |
| `included` | Count of items still marked as included in the pipeline. |
| `included_pct` | Included count divided by the row total, as a fraction from 0 to 1. |
| `filtering` | Count of excluded items whose recorded reason names TestFilteringTask. Tests and assertions must also have a recorded REJECT decision to count here. |
| `filtering_pct` | Filtering count divided by the row total, as a fraction from 0 to 1. |
| `failures` | Count of all excluded items not classified as filtering. |
| `failures_pct` | Failure count divided by the row total, as a fraction from 0 to 1. |

### `tab-exclusions-filtering.csv`

Report `rq5`, `build/rq5/tab-exclusions-filtering.csv`. One row per test-level or assertion-level filter that records at least one rejection in the controlled projects with generalization enabled. Feeds the paper's test and assertion filtering table.

| Column | Meaning |
| --- | --- |
| `row_key` | Level and displayed filter name joined by a colon. |
| `level` | Kind of item evaluated: Test or Assertion. |
| `filter` | Short display name derived from the Java filter class name. For example, UnsupportedAssertionFilter is reported as AssertionType. |
| `total` | Count of all recorded decisions by this filter at this level, not the total count of items at that level. |
| `accept` | Count of ACCEPT decisions, which pass this filter. |
| `accept_pct` | ACCEPT count divided by all decisions for this filter and level, as a fraction from 0 to 1. |
| `defer` | Count of DEFER decisions, which do not by themselves exclude an item. |
| `defer_pct` | DEFER count divided by all decisions for this filter and level, as a fraction from 0 to 1. |
| `reject` | Count of REJECT decisions, which exclude the evaluated item. |
| `reject_pct` | REJECT count divided by all decisions for this filter and level, as a fraction from 0 to 1. |

### `tab-exclusions-spf-causes.csv`

Report `rq5`, `build/rq5/tab-exclusions-spf-causes.csv`. One row per observed cause of a failed JPF specification extraction task in the controlled corpus. Feeds the paper's table of assertion-level specification extraction failures.

| Column | Meaning |
| --- | --- |
| `row_key` | Failure group and cause label joined by a colon. |
| `group` | Broad failure category: SPF exception, Exceeded analysis limit, or Teralizer error. |
| `cause` | Specific failure label assigned by matching the task diagnostic text. Unmatched diagnostics are labeled Other SPF/JPF exceptions. |
| `assertions` | Count of failed EXECUTE_JPF task records assigned to this cause, not a deduplicated count of assertion IDs. |
| `of_failures` | Cause count divided by all failed EXECUTE_JPF task records, as a fraction from 0 to 1. |
| `of_assertions` | Cause count divided by all assertion records in the controlled corpus, including assertions that did not reach extraction, as a fraction from 0 to 1. |

### `tab-exclusions-test-failures.csv`

Report `rq5`, `build/rq5/tab-exclusions-test-failures.csv`. One row per generalization variant in the controlled corpus, with counts of JUnit failure types linked to its generalizations. Feeds the paper's table of generalized tests that fail on the original program.

| Column | Meaning |
| --- | --- |
| `row_key` | Generalization variant key, identical to variant. |
| `variant` | Generalization variant key, such as BASELINE or NAIVE_10_TRIES. |
| `generalizations` | Count of all generalization records for this variant, including attempts that produced no JUnit report. |
| `too_many_filter_misses` | Count of linked JUnit report rows with net.jqwik.api.TooManyFilterMissesException, meaning too many generated inputs missed the jqwik filter. |
| `too_many_filter_misses_pct` | TooManyFilterMissesException report count divided by all generalization records for the variant, as a fraction from 0 to 1. |
| `assertion_failed` | Count of linked JUnit report rows with org.opentest4j.AssertionFailedError. |
| `assertion_failed_pct` | AssertionFailedError report count divided by all generalization records for the variant, as a fraction from 0 to 1. |
| `arithmetic` | Count of linked JUnit report rows with java.lang.ArithmeticException. |
| `arithmetic_pct` | ArithmeticException report count divided by all generalization records for the variant, as a fraction from 0 to 1. |
| `number_format` | Count of linked JUnit report rows with java.lang.NumberFormatException. |
| `number_format_pct` | NumberFormatException report count divided by all generalization records for the variant, as a fraction from 0 to 1. |

### `rq6_assertion_type_rejections.csv`

Report `rq6`, `build/rq6/rq6_assertion_type_rejections.csv`. One row per assertion method name rejected by the AssertionType filter in eligible RepoReapers projects. Feeds the RQ6 table of rejected assertion names.

| Column | Meaning |
| --- | --- |
| `assertion_name` | Simple name of the assertion method called. |
| `assertions` | Count of distinct assertions with this name rejected by AssertionType, recorded as UnsupportedAssertionFilter. |
| `share` | Rejected assertions with this name divided by all assertions in eligible projects, as a fraction from 0 to 1. |

### `rq6_exclusion_mechanisms.csv`

Report `rq6`, `build/rq6/rq6_exclusion_mechanisms.csv`. One row per observed mechanism and entity level in eligible RepoReapers projects. Feeds the RQ6 table of included entities and specific exclusion mechanisms.

| Column | Meaning |
| --- | --- |
| `row_key` | Lowercase entity level and mechanism code joined by a period. |
| `level` | Entity level: Test, Assertion, or Generalization. Generalization counts use the selected variant. |
| `mechanism_label` | Label for inclusion, filter rejection, a generation-time gate, an inherited-test inlining limit, a compilation failure, or a processing failure. |
| `reader_outcome` | Outcome used in the aggregate exclusion table: included, filtering, or failures. |
| `entity_count` | Count of entities assigned to this mechanism at this level. |
| `share` | Mechanism count divided by the level total, as a fraction from 0 to 1. |
| `level_total` | Count of entities across all mechanisms at this level, repeated on each row for the level. |

### `rq6_filtering_comparison.csv`

Report `rq6`, `build/rq6/rq6_filtering_comparison.csv`. One row per dataset comparing generalized tests with a recorded post-generation filter decision. Feeds the RQ6 filtering comparison table and supports the paper's comparison of inclusion rates after generation.

| Column | Meaning |
| --- | --- |
| `dataset_key` | Dataset identifier: controlled or realworld. |
| `dataset` | Dataset label: Controlled or RepoReapers. |
| `total` | Count of generalized tests with an ACCEPT or REJECT from the generated-suite NonPassingTest filter. The controlled count uses IMPROVED_200_TRIES in projects configured for generalization, and the RepoReapers count uses the selected variant in eligible projects. |
| `included` | Count of generalized tests with an ACCEPT decision from that filter. |
| `excluded` | Count of generalized tests with a REJECT decision from that filter. |
| `included_share` | Included count divided by the count with a filter decision, as a fraction from 0 to 1. |

### `rq6_generalization_funnel.csv`

Report `rq6`, `build/rq6/rq6_generalization_funnel.csv`. One row per successive generalization population for the selected variant in eligible RepoReapers projects. Feeds the RQ6 generalized-test funnel table.

| Column | Meaning |
| --- | --- |
| `population_key` | Identifier for the population from attempted through final_usable. |
| `population` | Display label for the lifecycle population: Attempted, Emitted, Filter result recorded, Filter passed, Validated, Reduced, or Final usable. |
| `count` | Count of generalizations meeting the population condition. Validated requires source creation, compilation, execution, report collection and filter passage, while Reduced uses the lifecycle's generated PIT completion flag. |
| `entering` | Count in the preceding population. For Attempted, this is the count of attempts itself. |
| `excluded` | Count in the entering population that did not reach the named population. Zero for Attempted. |
| `attempt_known_exclusions` | Count of exclusions treated as known attempts: all exclusions for Emitted, Filter passed, Validated and Final usable. In the Filter result recorded and Reduced rows, this counts only exclusions with a project task for the recorded failure stage and the same variant or no variant. |
| `attempt_unknown_exclusions` | Count of exclusions not treated as known attempts. Only Filter result recorded and Reduced can have a nonzero count here. |

### `rq6_jpf_exception_causes.csv`

Report `rq6`, `build/rq6/rq6_jpf_exception_causes.csv`. One row per recovered cause category for generic Java PathFinder (JPF) uncaught-exception diagnostics in eligible RepoReapers projects. Feeds the RQ6 diagnostic classification table.

| Column | Meaning |
| --- | --- |
| `category` | Cause classified from retained diagnostic text: Application exception, JPF native-peer gap, JPF model/field gap, or Unparsed. |
| `count` | Count of UNCAUGHT_EXCEPTION_PATH diagnostic records classified in this category. |
| `share` | Category count divided by all UNCAUGHT_EXCEPTION_PATH diagnostic records in eligible projects, as a fraction from 0 to 1. Zero when there are no such records. |

### `rq6_missing_value_causes.csv`

Report `rq6`, `build/rq6/rq6_missing_value_causes.csv`. One row per recorded reason for the MissingValue filter to reject assertions in eligible RepoReapers projects. Feeds the RQ6 table of MissingValue rejection causes.

| Column | Meaning |
| --- | --- |
| `reason_code` | Resolver reason code recorded on the MissingValue rejection. The query substitutes <missing> for null, but the report requires the five expected codes. |
| `assertions` | Count of distinct assertions rejected by MissingValue with this reason code. |
| `share` | Category count divided by the sum of counts for all MissingValue rejection codes, as a fraction from 0 to 1. |

### `rq6_mut_choice_sensitivity.csv`

Report `rq6`, `build/rq6/rq6_mut_choice_sensitivity.csv`. One row per category of method-under-test (MUT) candidate evidence for ParameterType rejections with reason NO_GENERALIZABLE_PARAMETERS in eligible RepoReapers projects. Feeds the RQ6 table on sensitivity to the choice of method under test.

| Column | Meaning |
| --- | --- |
| `category` | Choice-dependent if any recorded candidate call passes arguments, and Choice-invariant if none does. Either classification requires usable call source for every candidate in a nonempty list, otherwise the label is Candidate detail unavailable. |
| `count` | Count of distinct assertion and candidate-detail pairs in this category. |
| `share` | Category count divided by all selected assertion and candidate-detail pairs, including those with unavailable details, as a fraction from 0 to 1. Zero when there are no pairs. |

### `rq6_non_passing_failure_types.csv`

Report `rq6`, `build/rq6/rq6_non_passing_failure_types.csv`. One row per original-suite JUnit result and failure type among the ten pairs with the most tests rejected by NonPassingTest in eligible RepoReapers projects. Feeds the RQ6 table of the most frequent failure types.

| Column | Meaning |
| --- | --- |
| `result` | Non-passing result recorded for the original suite by JUnit, such as ERROR, FAILED, or SKIPPED. |
| `failure_type` | Failure or error type or skip marker recorded in the original JUnit report. A null type is shown as <missing>. |
| `tests` | Count of distinct rejected tests with this result and type. A test can count in multiple rows if its executions recorded different pairs. |

### `rq6_non_passing_outcomes.csv`

Report `rq6`, `build/rq6/rq6_non_passing_outcomes.csv`. One row per original-suite JUnit outcome assigned to tests rejected by NonPassingTest in eligible RepoReapers projects. Feeds the RQ6 table of recorded outcomes for those rejections.

| Column | Meaning |
| --- | --- |
| `outcome` | Recorded outcome assigned to a test using this precedence across all original-suite executions: error, failed, skipped, then passed. |
| `tests` | Count of distinct rejected tests assigned to this outcome. Each rejected test counts once. |
| `share` | Outcome count divided by all tests rejected by NonPassingTest in eligible projects, as a fraction from 0 to 1. |

### `rq6_parameter_type_rejections.csv`

Report `rq6`, `build/rq6/rq6_parameter_type_rejections.csv`. One row per category of retained selected-method evidence for ParameterType rejections in eligible RepoReapers projects. Feeds the RQ6 table of evidence for those rejections.

| Column | Meaning |
| --- | --- |
| `category` | Selected method has unsupported_types, zero_parameters, or missing_declaration, or falls into derivation_gap. A derivation gap records supported parameters despite the rejection. |
| `assertions` | Count of distinct assertions rejected by ParameterType and classified in this category. |
| `share` | Category count divided by all assertions rejected by ParameterType in eligible projects, as a fraction from 0 to 1. |

### `rq6_reconstruction_outcomes.csv`

Report `rq6`, `build/rq6/rq6_reconstruction_outcomes.csv`. One row per observed claim, evidence status and outcome label in the RepoReapers reconstruction audit. Counts describe reviewed records, not full populations for sampled claims.

| Column | Meaning |
| --- | --- |
| `row_key` | Claim identifier, entity evidence status and outcome label joined by colons. |
| `claim` | Identifier of the reconstructed claim: no-assertions, assertion-to-mut, or output-directories. |
| `status` | Entity evidence status: resolved, unresolved, or incompatible. |
| `outcome` | Claim-specific classification recorded in the audit, such as supported-mapping or absent-artifact. |
| `reviewed_count` | Count of audit entity records with this claim, status and outcome. The entity is a test, an assertion-to-method mapping, or a project, depending on the claim. |

### `rq6_reconstruction_summary.csv`

Report `rq6`, `build/rq6/rq6_reconstruction_summary.csv`. One row per reconstructed RepoReapers claim, with counts for its full audit population and the method and finding of the review.

| Column | Meaning |
| --- | --- |
| `claim_key` | Identifier of the reconstructed claim: no-assertions, assertion-to-mut, or output-directories. |
| `claim_status` | Overall status recorded by the audit: supported, partially-supported, contradicted, or evidence-gap. |
| `resolved` | Count of population members with a resolved classification. This includes classifications that contradict the claim. |
| `unresolved` | Count of population members without a resolved classification, excluding incompatible records. Includes members outside the review. |
| `unreviewed_population` | Count of unresolved population members not represented by individual review records in the audit. |
| `incompatible` | Count of population members whose evidence was classified as incompatible. |
| `total` | Count of all population members for the claim, equal to resolved plus unresolved plus incompatible. |
| `method` | Audit description of how evidence was selected and classified. |
| `finding` | Audit finding, including an estimate or confidence interval if stated in the audit. |

### `rq6_return_type_rejections.csv`

Report `rq6`, `build/rq6/rq6_return_type_rejections.csv`. One row per encoded return type among the ten types with the most ReturnType rejections in eligible RepoReapers projects. Feeds the RQ6 table of the most frequent rejected return types.

| Column | Meaning |
| --- | --- |
| `return_type` | Return type recorded in the filter result detail. A null or absent type is shown as <missing>. |
| `assertions` | Count of distinct assertions rejected by ReturnType with this recorded type. |
| `share` | Type count divided by the sum of counts for all rejected types, including types outside the top ten, as a fraction from 0 to 1. |

### `rq6_widening_refusals.csv`

Report `rq6`, `build/rq6/rq6_widening_refusals.csv`. One row per recorded widening refusal code for the selected variant in eligible RepoReapers projects. Feeds the RQ6 table of widening refusals before test emission.

| Column | Meaning |
| --- | --- |
| `code` | Widening refusal code stored on the generalization attempt. The widening check records the first matching refusal. |
| `cause` | Description of the refusal condition corresponding to the code. |
| `refusals` | Count of generalization attempts refused with this code. |
| `refusal_total` | Count of generalization attempts with a widening refusal code, repeated on each row. |
| `refusals_pct` | Refusals with this code divided by all widening refusals, as a fraction from 0 to 1. |
| `attempts` | Count of all generalization attempts for the selected variant in eligible RepoReapers projects, repeated on each row. |
| `attempts_pct` | Refusals with this code divided by all generalization attempts, as a fraction from 0 to 1. |

### `tab-exclusions-breakdown-extended.csv`

Report `rq6`, `build/rq6/tab-exclusions-breakdown-extended.csv`. One row per entity level in eligible RepoReapers projects, with inclusion and exclusions grouped into filtering and failures. Feeds the paper's RepoReapers exclusion results table.

| Column | Meaning |
| --- | --- |
| `level` | Entity level: Test, Assertion, or Generalization. Generalization counts cover attempts for the selected variant. |
| `total` | Count of entities at this level across all three outcomes. |
| `included` | Count of included entities. Tests and assertions must be marked included with no failed entity-level task, while generalizations must pass the generated-test filter. |
| `included_pct` | Included count divided by the level total, as a fraction from 0 to 1. |
| `filtering` | Count of entities excluded by a filter rejection, generation-time gate or inherited-test inlining limit. |
| `filtering_pct` | Filtering count divided by the level total, as a fraction from 0 to 1. |
| `failures` | Count of entities excluded by compilation quarantine or processing failure. |
| `failures_pct` | Failure count divided by the level total, as a fraction from 0 to 1. |

### `tab-exclusions-filtering-extended.csv`

Report `rq6`, `build/rq6/tab-exclusions-filtering-extended.csv`. One row per entity level and filter with at least one rejection in eligible RepoReapers projects. Feeds the paper's RepoReapers filtering results table.

| Column | Meaning |
| --- | --- |
| `row_key` | Entity level and displayed filter name joined by a colon. |
| `level` | Entity level evaluated: Test, Assertion, or Generalization. |
| `filter` | Short name of the filter or proactive check. The report derives verdicts for InheritedTestMethod, SeedSpecConsistency and WideningLicense from source mapping or lifecycle evidence. |
| `total` | Count of distinct entities with a verdict at this level and filter. |
| `accept` | Count of distinct evaluated entities with an ACCEPT verdict. |
| `accept_pct` | Accept count divided by the evaluated count, as a fraction from 0 to 1. |
| `defer` | Count of distinct evaluated entities with a DEFER verdict. Zero if none. |
| `defer_pct` | Defer count divided by the evaluated count, as a fraction from 0 to 1. |
| `reject` | Count of distinct evaluated entities with a REJECT verdict. |
| `reject_pct` | Reject count divided by the evaluated count, as a fraction from 0 to 1. |

### `tab-processing-failures.csv`

Report `rq6`, `build/rq6/tab-processing-failures.csv`. One row per classified cause and first stage of exclusion among eligible RepoReapers projects. Feeds the paper's table of project-level exclusions by stage and cause.

| Column | Meaning |
| --- | --- |
| `row_key` | Pipeline stage and cause before display substitutions, joined by a colon. Stages 1 and 2 use a single group. |
| `cause` | Description of the project exclusion cause, with report display names for tools and test suites. |
| `count` | Count of eligible projects assigned this cause at their first stage of exclusion. Projects without a classified cause do not contribute. |
