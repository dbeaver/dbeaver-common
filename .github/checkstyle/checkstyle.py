#!/usr/bin/env python3
# Copyright (C) 2026 DBeaver Corp and others
# SPDX-License-Identifier: Apache-2.0

"""Run the Checkstyle CLI with DBeaver rules and emit advisory PR annotations."""

import argparse
from collections import Counter
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root).decode("utf-8")


def diff_context(root, base):
    """Map changed paths to new-side hunk ranges, including three context lines."""
    paths = git(root, "diff", "--no-renames", "--name-only", "-z", "--diff-filter=ACM", base, "HEAD", "--").split("\0")
    ranges = {}
    for path in filter(None, paths):
        diff = git(root, "diff", "--no-renames", "--no-ext-diff", "--no-textconv", "--unified=3", base, "HEAD", "--", path)
        ranges[path] = []
        for match in re.finditer(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", diff, re.MULTILINE):
            start = int(match[1])
            count = int(match[2]) if match[2] is not None else 1
            if count:
                ranges[path].append((start, start + count - 1))
    return ranges


def escape(value, property_value=False):
    value = str(value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    if property_value:
        value = value.replace(":", "%3A").replace(",", "%2C")
    return value


def annotation(path, error):
    level = "error" if error.get("severity") == "error" else "warning"
    properties = {"file": path, "line": error.get("line", "1"), "title": "Checkstyle"}
    if error.get("column"):
        properties["col"] = error.get("column")
    properties = ",".join(f"{key}={escape(value, True)}" for key, value in properties.items())
    rule = error.get("source", "Checkstyle").rsplit(".", 1)[-1]
    return f"::{level} {properties}::{escape(error.get('message', ''))} [{escape(rule)}]"


def report(document, root, ranges=None):
    counts = Counter()
    emitted = Counter()
    matching = 0
    for file in document.findall("file"):
        path = Path(file.attrib["name"]).resolve().relative_to(root).as_posix()
        for error in file.findall("error"):
            if error.get("severity") == "ignore":
                continue
            line = int(error.get("line", "0"))
            if ranges is not None and not any(start <= line <= end for start, end in ranges.get(path, [])):
                continue
            rule = error.get("source", "Checkstyle").rsplit(".", 1)[-1]
            counts[rule] += 1
            matching += 1
            level = "error" if error.get("severity") == "error" else "warning"
            # GitHub accepts at most ten annotations of each level per step.
            if emitted[level] < 10:
                print(annotation(path, error))
                emitted[level] += 1
            else:
                print(f"Checkstyle: {escape(path)}:{line}: {escape(error.get('message', ''))} [{escape(rule)}]")
    print(f"Checkstyle: {matching} matching violations (advisory); {sum(emitted.values())} annotations emitted.")
    for rule, count in sorted(counts.items()):
        print(f"Checkstyle: {escape(rule)}: {count}")
    return counts


def analyze(jar, plugin, config, root):
    files = git(root, "ls-files", "-z", "--", "*.java", "*.xml", "*.properties").split("\0")
    # Restrict analysis to tracked files in the target repository, excluding tool checkouts.
    files = [str((root / path).resolve()) for path in filter(None, files)
             if not (root / path).is_symlink() and (root / path).is_file()
             and (root / path).resolve().is_relative_to(root)]
    if not files:
        return ET.Element("checkstyle")
    with tempfile.TemporaryDirectory(prefix="checkstyle-") as directory:
        arguments = Path(directory) / "files.txt"
        output = Path(directory) / "report.xml"
        # Picocli @files avoid the operating system's command-line length limit.
        arguments.write_text("\n".join('"' + path.replace("\\", "\\\\").replace('"', '\\"') + '"'
                                       for path in files), encoding="utf-8")
        result = subprocess.run([
            "java", "-cp", os.pathsep.join((str(jar), str(plugin))),
            "com.puppycrawl.tools.checkstyle.Main", "-c", str(config), "-f", "xml",
            "-o", str(output), f"@{arguments}",
        ], cwd=root, check=False)
        # The shared rules use warning severity, so violations are advisory, while
        # nonzero exits indicate failures that must not be hidden by the reporter.
        result.check_returncode()
        document = ET.parse(output).getroot()
        if document.tag != "checkstyle":
            raise ValueError("Checkstyle did not produce a Checkstyle XML report")
        return document


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--plugin", required=True, type=Path)
    parser.add_argument("--config", required=True, type=Path)
    args = parser.parse_args()
    root = Path.cwd().resolve()
    base = os.environ.get("CHECKSTYLE_BASE_SHA")
    ranges = diff_context(root, base) if base else None
    document = analyze(args.jar.resolve(), args.plugin.resolve(), args.config.resolve(), root)
    report(document, root, ranges)


if __name__ == "__main__":
    main()
