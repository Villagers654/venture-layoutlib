#!/usr/bin/env python3
"""Builds Venture's Android 2D framework against this fork's classes, as Venture's
build does (src/runtime/layoutlib/CMakeLists.txt), and runs its linkage check.

  venture_check.py --venture DIR [--fork DIR] [--work DIR]
"""
import argparse, hashlib, os, re, shutil, subprocess, sys, urllib.request, zipfile
from pathlib import Path

LAYOUTLIB = ("https://dl.google.com/android/maven2/com/android/tools/layoutlib/layoutlib/17.0.4/layoutlib-17.0.4.jar",
             "ea661abe6ce643f5b6c7cb467675821cfc5deefad6f2885e6893909d7434d5aa")
ASM = ("https://repo1.maven.org/maven2/org/ow2/asm/asm/9.8/asm-9.8.jar",
       "876eab6a83daecad5ca67eb9fcabb063c97b5aeb8cf1fca7a989ecde17522051")


def fetch(url, sha, dest):
    if not dest.is_file() or hashlib.sha256(dest.read_bytes()).hexdigest() != sha:
        with urllib.request.urlopen(url) as r:
            data = r.read()
        if hashlib.sha256(data).hexdigest() != sha:
            sys.exit(f"{url}: checksum mismatch")
        dest.write_bytes(data)
    return dest


def run(*cmd):
    print("+", " ".join(str(c) for c in cmd), flush=True)
    subprocess.run([str(c) for c in cmd], check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--venture", type=Path, required=True)
    ap.add_argument("--fork", type=Path, default=Path(__file__).resolve().parent.parent)
    ap.add_argument("--work", type=Path, default=Path("venture-check"))
    a = ap.parse_args()
    v, fork, work = a.venture.resolve(), a.fork.resolve(), a.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    java_home = Path(os.environ.get("JAVA_HOME", ""))
    exe = ".exe" if os.name == "nt" else ""
    java, javac, jar = (java_home / "bin" / (n + exe) for n in ("java", "javac", "jar"))

    cmake = (v / "src/runtime/layoutlib/CMakeLists.txt").read_text(encoding="utf-8")
    m = re.search(r"set\(venture_fork_classes\s+([^)]*)\)", cmake)
    if not m:
        sys.exit("Venture's CMakeLists names no fork classes")
    fork_classes = m.group(1).split()
    extras = re.findall(r'"\$\{PROJECT_SOURCE_DIR\}/(src/runtime/java/bridge/[^"]+\.java)"', cmake)

    classes = work / "layoutlib"
    if not (classes / "android/view/View.class").is_file():
        shutil.rmtree(classes, ignore_errors=True)
        with zipfile.ZipFile(fetch(*LAYOUTLIB, work / "layoutlib.jar")) as z:
            z.extractall(classes)
    asm = fetch(*ASM, work / "asm.jar")
    tools, lib = v / "tools/layoutlib", v / "src/runtime/layoutlib"

    select = [sys.executable, tools / "select_sources.py", "--framework", v / "src/runtime/java/framework",
              "--overlay", lib / "java", "--fork-root", fork / "bridge/src", "--layoutlib", classes,
              "--keep", lib / "venture-keeps.txt", "--sources", work / "sources.txt", "--replaced", work / "replaced.txt"]
    for c in fork_classes:
        select += ["--fork-class", c]
    for e in extras:
        select += ["--extra", v / e]
    run(*select)
    processed = work / "layoutlib-processed.jar"
    run(java, "-cp", asm, tools / "ProcessLayoutlib.java", classes, processed, work / "delegates.txt",
        lib / "layoutlib-rules.txt", work / "replaced.txt")
    out = work / "classes-2d"
    shutil.rmtree(out, ignore_errors=True)
    run(javac, "-encoding", "UTF-8", "--release", "21", "-nowarn", "-d", out, "-cp", processed, f"@{work / 'sources.txt'}")
    framework = work / "venture-framework-2d.jar"
    run(jar, "--create", "--file", framework, "-C", out, ".")
    pruned = work / "layoutlib-pruned.jar"
    run(java, "-cp", asm, tools / "ProcessLayoutlib.java", "--prune", processed, framework, pruned)
    run(java, "-cp", asm, tools / "CheckLinkage.java", pruned, framework, lib / "linkage-allowed.txt", lib / "linkage-known.txt")
    print(f"OK: {len(fork_classes)} fork classes build into Venture's 2D framework and link")


if __name__ == "__main__":
    main()
