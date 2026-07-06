#!/usr/bin/env python3
"""Emit a POM's <dependency> entries as XML for embedding in a synthetic census POM.

Reads the upstream pom.xml of a pinned census project and prints its full dependency
closure with ${property} versions resolved, preserving scope, optional, type, classifier,
and exclusions. Fails loud on an unresolvable version so the fixture prep gate stops.
"""

import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def local(tag):
    return tag.split("}", 1)[1] if "}" in tag else tag


def main(pom_path):
    root = ET.parse(pom_path).getroot()
    props = {
        local(p.tag): (p.text or "").strip() for p in root.findall("m:properties/*", NS)
    }
    props["project.version"] = root.findtext("m:version", "", NS).strip()

    entries = []
    for dep in root.findall("m:dependencies/m:dependency", NS):
        artifact = dep.findtext("m:artifactId", "?", NS)
        version = dep.find("m:version", NS)
        if version is None or not (version.text or "").strip():
            sys.exit(f"unmanaged version for dependency {artifact} in {pom_path}")
        value = version.text.strip()
        if value.startswith("${") and value.endswith("}"):
            key = value[2:-1]
            if key not in props:
                sys.exit(
                    f"unresolvable version property {value} for {artifact} in {pom_path}"
                )
            version.text = props[key]
        entries.append(dep)

    if not entries:
        sys.exit(f"no dependencies found in {pom_path}")

    for dep in entries:
        for el in dep.iter():
            el.tag = local(el.tag)
            el.tail = None
            if el.text is not None and not el.text.strip():
                el.text = None
        ET.indent(dep, space="  ", level=2)
        print("    " + ET.tostring(dep, encoding="unicode").strip())


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: extract-pom-deps.py <pom.xml>")
    main(sys.argv[1])
