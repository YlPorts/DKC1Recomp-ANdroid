#!/usr/bin/env python3
"""Run honest host-side unit tests. This does NOT build or test an Android APK."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
ANDROID=Path(__file__).resolve().parents[1]

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report",type=Path)
    args=parser.parse_args()
    commands=[];ok=True
    with tempfile.TemporaryDirectory(prefix="dkc1-port-unit-") as t:
        temp=Path(t);java=temp/"java";java.mkdir()
        def run(name,command):
            nonlocal ok
            result=subprocess.run([str(x) for x in command],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            print("\n==",name,"==\n"+result.stdout)
            commands.append({"name":name,"exit_code":result.returncode,"output":result.stdout})
            ok=ok and result.returncode==0
            return result.returncode==0
        cc=shutil.which("cc") or shutil.which("clang")
        if not cc or not shutil.which("javac") or not shutil.which("java"):
            print("Tests require a host C compiler and JDK 17+",file=sys.stderr);return 2
        binary=temp/"test_platform"
        if run("C unit test compilation (host, not Android)",[cc,"-std=c11","-Wall","-Wextra","-Werror",
                "-fsanitize=address,undefined","-fno-omit-frame-pointer","-g","-I",ANDROID/"native",
                ANDROID/"native/android_platform.c",ANDROID/"tests/test_platform.c","-lm","-o",binary]):
            run("C utilities with ASan/UBSan",[binary])
        sources=ANDROID/"app/src/main/java/com/ylports/dkc1recomp"
        if run("Pure-Java unit test compilation",["javac","--release","17","-d",java,
                sources/"PadModel.java",sources/"RomVerifier.java",ANDROID/"tests/PortTests.java"]):
            run("Java controls and ROM rejection",["java","-ea","-cp",java,"com.ylports.dkc1recomp.PortTests"])
        run("Python build-tool and static contract tests",[sys.executable,ANDROID/"tests/test_tools.py"])
    report={"scope":"Host-side unit tests only","all_executed_tests_passed":ok,
        "android_apk_built":False,"android_ndk_compile_tested":False,
        "retail_rom_import_tested":False,"gameplay_tested":False,"device_tested":False,
        "tests":commands}
    if args.report:args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    return 0 if ok else 1
if __name__=="__main__":raise SystemExit(main())
