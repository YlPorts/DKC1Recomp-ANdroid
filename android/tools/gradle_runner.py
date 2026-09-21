#!/usr/bin/env python3
"""Bootstrap Gradle 8.11.1 from its official distribution and published SHA-256.

A text-only wrapper is used so the port ZIP contains no unverified wrapper JAR.
The distribution is fetched on the developer's machine, not bundled in the APK.
"""
from __future__ import annotations
import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
VERSION = "8.11.1"
BASE = f"https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip"
ANDROID = Path(__file__).resolve().parents[1]


def safe_extract(archive: Path, target: Path) -> None:
    with zipfile.ZipFile(archive) as z:
        entries = z.infolist()
        if len(entries)>30000 or sum(e.file_size for e in entries)>1024*1024*1024:
            raise ValueError("Gradle archive exceeds the extraction safety limit")
        seen=set()
        for e in entries:
            p=PurePosixPath(e.filename)
            if p.is_absolute() or ".." in p.parts or "\\" in e.filename or ":" in e.filename \
                    or not p.parts or p.parts[0]!=f"gradle-{VERSION}" \
                    or stat.S_ISLNK(e.external_attr>>16) or e.filename in seen:
                raise ValueError("Unsafe Gradle archive entry: "+e.filename)
            seen.add(e.filename)
        for e in entries:
            path=target.joinpath(*PurePosixPath(e.filename).parts)
            if e.is_dir(): path.mkdir(parents=True,exist_ok=True)
            else:
                path.parent.mkdir(parents=True,exist_ok=True)
                with z.open(e) as src,path.open("wb") as dst: shutil.copyfileobj(src,dst)


def get_gradle() -> Path:
    cache=ANDROID/".tool-cache";cache.mkdir(parents=True,exist_ok=True)
    root=cache/f"gradle-{VERSION}"
    executable=root/"bin"/("gradle.bat" if os.name=="nt" else "gradle")
    if executable.is_file() and (root/".verified-sha256").is_file(): return executable
    with tempfile.TemporaryDirectory(prefix="gradle-stage-",dir=cache) as directory:
        stage=Path(directory);archive=stage/"gradle.zip"
        with urllib.request.urlopen(BASE+".sha256",timeout=60) as response:
            digest=response.read(256).decode("ascii").strip().lower()
        if not re.fullmatch(r"[0-9a-f]{64}",digest): raise ValueError("Invalid official Gradle SHA-256 response")
        sha=hashlib.sha256();total=0
        with urllib.request.urlopen(BASE,timeout=120) as response,archive.open("wb") as stream:
            while block:=response.read(1024*1024):
                total+=len(block)
                if total>300*1024*1024: raise ValueError("Gradle archive too large")
                sha.update(block);stream.write(block)
        if sha.hexdigest()!=digest: raise ValueError("Gradle SHA-256 mismatch; nothing executed")
        safe_extract(archive,stage)
        extracted=stage/f"gradle-{VERSION}"
        if not (extracted/"bin/gradle").is_file(): raise ValueError("Incomplete Gradle archive")
        (extracted/"bin/gradle").chmod(0o755)
        (extracted/".verified-sha256").write_text(digest+"\n")
        if root.exists(): raise RuntimeError("Incomplete existing Gradle cache; inspect/remove "+str(root))
        extracted.rename(root)
    return executable


def main() -> int:
    try:
        gradle=get_gradle()
        return subprocess.run([str(gradle),*sys.argv[1:]],cwd=ANDROID,check=False).returncode
    except (OSError,ValueError,RuntimeError,zipfile.BadZipFile) as error:
        print("Gradle bootstrap:",error,file=sys.stderr);return 1
if __name__=="__main__":raise SystemExit(main())
