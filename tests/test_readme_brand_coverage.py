"""README.md's brand line has to match the folders under examples/.

The line under "Tested lists from contributors" answers the first question an owner has - "is my
brand covered, and how many lists are there for it?" - and it is written by hand, so it goes stale
in two ways. A list lands and its brand's number is not bumped (Sony, Xiaomi and Hisense were each
one short after six lists landed without it), and a brand whose first list lands is left off the
line altogether, so its owners read that there is no list for them (KTC, INCLO and Liberton).

The numbers are counted from the folders, not from the page: an examples/ folder is one model, and
a numbered brand's number must be the number of folders it has. A brand can be printed under a
different name than its folder slug (PRISM+ for prismplus-*, Tele System for telesystem-*, and
Coocaa, Skyworth's budget line, under Skyworth), so those three carry an alias below.

Like the "N TV models" alt text in tests/test_examples.py, this only runs with CLEANYOURTV_STRICT=1:
docs/workflows/tv-list.md tells a contributor to leave the counts alone because they are fixed up on
merge, so a list PR must not go red over a number the maintainer bumps. On main CI sets it, which is
where a stale line has to be caught.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
EXAMPLES = ROOT / "examples"

START = "Tested lists from contributors"
END = "<details>"

# Folder slugs that are printed as another name on the line, and why.
ALIASES = {
    "prismplus": "prism+",   # the page prints the brand as "PRISM+"
    "telesystem": "tele",    # printed as "Tele System"
    "coocaa": "skyworth",    # Coocaa is Skyworth's budget line; the row reads "Skyworth Coocaa ..."
}
# Folders belonging to a brand whose own slug is different: counted with that brand.
SHARED = {"coocaa": "skyworth"}

NUMBERED = re.compile(r"\*\*([^*]+)\*\*\s+(\d+)")
WORD = re.compile(r"[A-Za-z0-9+]+")


def brand_line(body=None):
    """The coverage block: the numbered brands, the bare names and the projector sentence."""
    body = README.read_text(encoding="utf-8") if body is None else body
    start = body.index(START)
    end = body.index(END, start)
    return body[start:end]


def numbered_brands(block):
    """[(label, stated number)] for every '**Brand** N' on the line."""
    return [(label.strip(), int(number)) for label, number in NUMBERED.findall(block)]


def words(block):
    """Every word the line prints, lower-cased, so a name can be looked up in it."""
    return {word.lower() for word in WORD.findall(block)}


def folders():
    return sorted(path.name for path in EXAMPLES.iterdir() if path.is_dir())


def prefixes(names):
    return Counter(name.split("-")[0] for name in names)


def folder_count(label, counts):
    """The number of examples/ folders a numbered brand covers."""
    prefix = label.split()[0].lower()
    total = counts.get(prefix, 0)
    for slug, owner in SHARED.items():
        if owner == prefix:
            total += counts.get(slug, 0)
    return total


def short_brands(block, counts):
    """Numbered brands whose number is not the number of folders they have."""
    wrong = []
    for label, stated in numbered_brands(block):
        actual = folder_count(label, counts)
        if stated != actual:
            wrong.append((label, stated, actual))
    return wrong


def unnamed_brands(block, counts):
    """Folder slugs the line never prints under their own name or an alias."""
    printed = words(block)
    return sorted(prefix for prefix in counts
                  if prefix not in printed and ALIASES.get(prefix) not in printed)


class BrandLineTest(unittest.TestCase):
    @unittest.skipUnless(os.environ.get("CLEANYOURTV_STRICT"),
                         "counts are fixed up on merge; strict on main only")
    def test_each_number_is_the_number_of_its_folders(self):
        block = brand_line()
        wrong = short_brands(block, prefixes(folders()))
        self.assertEqual([], wrong, "the brand line disagrees with examples/: "
                         + "; ".join(f"{label} says {stated}, examples/ holds {actual}"
                                     for label, stated, actual in wrong))

    @unittest.skipUnless(os.environ.get("CLEANYOURTV_STRICT"),
                         "brands are fixed up on merge; strict on main only")
    def test_every_brand_with_a_list_is_named(self):
        block = brand_line()
        counts = prefixes(folders())
        missing = unnamed_brands(block, counts)
        self.assertEqual([], missing, "these brands have a list in examples/ but the line never "
                         "names them: " + ", ".join(missing))

    def test_the_line_is_read_at_all(self):
        # A parser that finds nothing would make both sweeps above pass for an empty page.
        block = brand_line()
        labels = [label for label, _ in numbered_brands(block)]
        for label in ("TCL", "Sony", "Xiaomi", "Philips", "Hisense", "Skyworth"):
            self.assertIn(label, labels, f"the brand line no longer states {label}'s count")
        counts = prefixes(folders())
        self.assertGreaterEqual(len(counts), 75, "examples/ holds fewer brands than this test expects")
        self.assertGreaterEqual(len(words(block)), 60, "the brand line has fewer words than this test expects")

    def test_the_aliases_still_point_at_folders(self):
        counts = prefixes(folders())
        for slug in ALIASES:
            with self.subTest(folder=slug):
                self.assertIn(slug, counts, f"{slug} has no folder in examples/: drop its alias")

    def test_a_stale_number_is_named(self):
        block = "**Sony** 2 · **TCL** 1 ·"
        counts = Counter({"sony": 3, "tcl": 1})
        self.assertEqual([("Sony", 2, 3)], short_brands(block, counts))

    def test_a_number_that_agrees_is_quiet(self):
        block = "**Sony** 3 · **TCL** 1 ·"
        counts = Counter({"sony": 3, "tcl": 1})
        self.assertEqual([], short_brands(block, counts))

    def test_a_coocaa_folder_counts_with_skyworth(self):
        block = "**Skyworth** 2 ·"
        counts = Counter({"skyworth": 1, "coocaa": 1})
        self.assertEqual([], short_brands(block, counts))

    def test_a_brand_the_line_never_names_is_reported(self):
        block = "**Skyworth** 1 · Acme · PRISM+ projectors"
        counts = Counter({"skyworth": 1, "acme": 1, "prismplus": 1, "inclo": 1})
        self.assertEqual(["inclo"], unnamed_brands(block, counts))

    def test_an_alias_counts_as_named(self):
        block = "PRISM+ · Tele System · Skyworth"
        counts = Counter({"prismplus": 1, "telesystem": 1, "coocaa": 1})
        self.assertEqual([], unnamed_brands(block, counts))

    def test_a_page_without_the_line_is_an_error(self):
        # Rather than reading an empty block and passing: the section is what this test is about.
        with self.assertRaises(ValueError):
            brand_line("# A page that lost the section\n")


if __name__ == "__main__":
    unittest.main()
