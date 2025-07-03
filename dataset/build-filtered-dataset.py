import csv
import re
from contextlib import ExitStack

INPUT_FILE = "dataset_java_extended.csv"
OUTPUT_FILE = "dataset_java_extended_filtered.csv"
FILE_ENCODING = "utf-8"


def is_maven(build_tool):
    return build_tool.strip().lower() == "maven"

def is_java8_or_lower(java_version):
    if not java_version or java_version.strip() == "":
        return False  # Exclude empty Java version
    java_version = java_version.strip()
    # Match 1.0 to 1.8 or 0 to 8 (optionally .0)
    return (
        re.match(r"^1\.[0-8]$", java_version) or
        re.match(r"^[0-8](\.0)?$", java_version)
    )

def is_junit4_or_5(junit_version):
    if not junit_version or junit_version.strip() == "":
        return False
    junit_version = junit_version.strip()
    return junit_version.startswith("4") or junit_version.startswith("5")

def meets_filter_criteria(row):
    return (
        is_maven(row["build_tool"]) and
        is_java8_or_lower(row["java_version"]) and
        is_junit4_or_5(row["junit_version"]) and
        row['has_src_main'] == 'True' and
        row['has_src_test'] == 'True' and
        float(row['repo_size']) < 100000
    )

def filter_dataset():
    with ExitStack() as stack:
        source_file = stack.enter_context(open(INPUT_FILE, newline="", encoding=FILE_ENCODING))
        target_file = stack.enter_context(open(OUTPUT_FILE, "w", newline="", encoding=FILE_ENCODING))

        csv_reader = csv.DictReader(source_file)
        csv_writer = csv.DictWriter(target_file, fieldnames=csv_reader.fieldnames)
        csv_writer.writeheader()

        for row in csv_reader:
            if meets_filter_criteria(row):
                csv_writer.writerow(row)


if __name__ == "__main__":
    filter_dataset()
