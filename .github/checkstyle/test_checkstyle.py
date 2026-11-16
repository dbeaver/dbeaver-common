# Copyright (C) 2026 DBeaver Corp and others
# SPDX-License-Identifier: Apache-2.0

from contextlib import redirect_stdout
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from xml.etree import ElementTree

import checkstyle


class ReporterTest(unittest.TestCase):
    def test_annotation_escapes_untrusted_message_and_path(self):
        error = ElementTree.Element("error", line="3", column="4", severity="warning",
                           message="100%\n::error::injected\r", source="example.CustomCheck")
        text = checkstyle.annotation("a,b:c.java", error)
        self.assertIn("file=a%2Cb%3Ac.java,line=3,title=Checkstyle,col=4", text)
        self.assertIn("100%25%0A::error::injected%0D [CustomCheck]", text)
        self.assertNotIn("\n", text)

    def test_filter_and_annotation_limit_preserve_all_matching_results(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            document = ElementTree.Element("checkstyle")
            file = ElementTree.SubElement(document, "file", name=str(root / "Sample.java"))
            for line in range(1, 16):
                ElementTree.SubElement(file, "error", line=str(line), severity="warning",
                              message="violation", source="example.CustomCheck")
            output = io.StringIO()
            with redirect_stdout(output):
                counts = checkstyle.report(document, root, {"Sample.java": [(3, 14)]})
            self.assertEqual(12, counts["CustomCheck"])
            self.assertEqual(10, output.getvalue().count("::warning "))
            self.assertIn("Sample.java:14: violation", output.getvalue())
            self.assertNotIn("line=1,", output.getvalue())

    def test_report_rejects_dtd_entities_and_wrong_root(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.xml"
            for xml in (
                '<!DOCTYPE checkstyle [<!ENTITY injected "expanded">]><checkstyle>&injected;</checkstyle>',
                '<!DOCTYPE checkstyle SYSTEM "file:///etc/passwd"><checkstyle/>',
                '<other/>',
            ):
                with self.subTest(xml=xml):
                    output.write_text(xml, encoding="utf-8")
                    with self.assertRaises(ValueError):
                        checkstyle.read_report(output)


class RepositoryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name).resolve()
        checkstyle.git(self.root, "init", "-q")

    def commit(self):
        checkstyle.git(self.root, "add", ".")
        checkstyle.git(self.root, "-c", "user.name=Checkstyle test", "-c", "user.email=test@example.invalid",
                       "-c", "commit.gpgsign=false", "commit", "-qm", "fixture")
        return checkstyle.git(self.root, "rev-parse", "HEAD").strip()

    def test_diff_context_handles_spaces_additions_deletions_and_renames(self):
        original = "".join(f"line {line}\n" for line in range(1, 21))
        (self.root / "with spaces.java").write_text(original, encoding="utf-8")
        (self.root / "Deleted.java").write_text(original, encoding="utf-8")
        (self.root / "Old.java").write_text(original, encoding="utf-8")
        base = self.commit()
        (self.root / "with spaces.java").write_text(original.replace("line 10\n", "changed\n"), encoding="utf-8")
        (self.root / "Deleted.java").unlink()
        (self.root / "Old.java").rename(self.root / "New.java")
        (self.root / "Added.java").write_text("new\n", encoding="utf-8")
        self.commit()
        ranges = checkstyle.diff_context(self.root, base)
        self.assertEqual([(7, 13)], ranges["with spaces.java"])
        self.assertEqual([(1, 1)], ranges["Added.java"])
        self.assertEqual([(1, 20)], ranges["New.java"])
        self.assertNotIn("Deleted.java", ranges)

    def test_merge_commit_uses_first_parent_when_event_base_is_unavailable(self):
        (self.root / "Sample.java").write_text("original\n", encoding="utf-8")
        base = self.commit()
        branch = checkstyle.git(self.root, "branch", "--show-current").strip()
        checkstyle.git(self.root, "checkout", "-qb", "feature")
        (self.root / "Sample.java").write_text("changed\n", encoding="utf-8")
        self.commit()
        checkstyle.git(self.root, "checkout", "-q", branch)
        checkstyle.git(self.root, "-c", "user.name=Checkstyle test", "-c", "user.email=test@example.invalid",
                       "-c", "commit.gpgsign=false", "merge", "-q", "--no-ff", "feature")

        selected = checkstyle.diff_base(self.root, "b33d43e8434c8e04cf48cb612edbaa4a8454f227")
        self.assertEqual(base, selected)
        self.assertEqual([(1, 1)], checkstyle.diff_context(self.root, selected)["Sample.java"])

    def test_non_merge_commit_keeps_event_base(self):
        (self.root / "Sample.java").write_text("original\n", encoding="utf-8")
        base = self.commit()
        (self.root / "Sample.java").write_text("changed\n", encoding="utf-8")
        self.commit()
        self.assertEqual(base, checkstyle.diff_base(self.root, base))


@unittest.skipUnless(os.environ.get("CHECKSTYLE_JAR") and os.environ.get("CHECKSTYLE_PLUGIN"),
                     "Set CHECKSTYLE_JAR and CHECKSTYLE_PLUGIN to run the CLI integration tests")
class CliTest(RepositoryTest):
    def setUp(self):
        super().setUp()
        self.jar = Path(os.environ["CHECKSTYLE_JAR"]).resolve()
        self.plugin = Path(os.environ["CHECKSTYLE_PLUGIN"]).resolve()
        self.config = Path(__file__).resolve().parents[1] / "dbeaver-checkstyle-config.xml"

    def analyze(self, source):
        (self.root / "Sample.java").write_text(source, encoding="utf-8")
        self.commit()
        return checkstyle.analyze(self.jar, self.plugin, self.config, self.root)

    def test_custom_style_and_constructor_method_nullability(self):
        document = self.analyze("""package example;

public class Sample {
    public Sample(String value) {
    }

    public String BadMethod(String value) {
        return value;
    }
}
""")
        errors = document.findall("file/error")
        self.assertTrue(any(error.get("source", "").endswith("MethodNameCheck") for error in errors))
        nullability = [error for error in errors if error.get("source", "").endswith("NullabilityAnnotationsCheck")]
        self.assertGreaterEqual(len(nullability), 3)
        self.assertTrue(any(error.get("line") == "4" for error in nullability))
        self.assertTrue(any(error.get("line") == "7" for error in nullability))

    def test_recognizes_dbeaver_nullability_annotations(self):
        document = self.analyze("""package example;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

public class Sample {
    public Sample(@NotNull String value) {
    }

    @Nullable
    public String value(@NotNull String value) {
        return value;
    }
}
""")
        self.assertEqual([], document.findall("file/error"))

    def test_invalid_configuration_fails(self):
        config = self.root / "invalid.xml"
        config.write_text("<module name='MissingCheck'/>", encoding="utf-8")
        (self.root / "Sample.java").write_text("class Sample {}\n", encoding="utf-8")
        self.commit()
        with self.assertRaises(subprocess.CalledProcessError):
            checkstyle.analyze(self.jar, self.plugin, config, self.root)


if __name__ == "__main__":
    unittest.main()
