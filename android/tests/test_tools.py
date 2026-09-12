from __future__ import annotations
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
ANDROID=Path(__file__).resolve().parents[1]

def load_module(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
build=load_module("port_build",ANDROID/"tools/build_android.py")
wrapper=load_module("port_gradle",ANDROID/"tools/gradle_runner.py")

class ToolsTest(unittest.TestCase):
    def test_native_size(self):
        payload=bytes(build.ROM_SIZE)
        self.assertIs(build.strip_copier_header(payload),payload)
    def test_header_size(self):
        payload=bytes([3])*build.ROM_SIZE
        self.assertEqual(build.strip_copier_header(bytes([7])*512+payload),payload)
    def test_wrong_sizes(self):
        for size in (0,1,512,build.ROM_SIZE-1,build.ROM_SIZE+1,build.ROM_SIZE+513):
            with self.subTest(size=size),self.assertRaises(ValueError):build.strip_copier_header(bytes(size))
    def test_invalid_hash(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/"bad.sfc";p.write_bytes(bytes(build.ROM_SIZE))
            with self.assertRaisesRegex(ValueError,"SHA-256"):build.read_rom(p)
    def test_oversized_input(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/"bad.sfc"
            with p.open("wb") as f:f.truncate(128*1024*1024)
            with self.assertRaises(ValueError):build.read_rom(p)
    def test_java_python_hash_match(self):
        text=(ANDROID/"app/src/main/java/com/ylports/dkc1recomp/RomVerifier.java").read_text()
        self.assertIn(build.ROM_SHA,text)
    def test_generated_hashes_ignore_manifest(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t);(p/"bank.c").write_text("hello");(p/"android-generation.json").write_text("{}")
            before=build.generated_files(p);self.assertEqual(list(before),["bank.c"])
            (p/"bank.c").write_text("other");self.assertNotEqual(before,build.generated_files(p))
    def test_repository_allows_synced_header_not_core_changes(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t)
            for name in ("runner", "scripts", "recomp"):(root/name).mkdir()
            for name in ("CMakeLists.txt", "runner/dkc1_game.c", "scripts/generate_snesrecomp.py", "recomp/funcs.h"):
                (root/name).write_text("base\n")
            def git(*args):
                return subprocess.run(["git",*args],cwd=root,check=True,text=True,capture_output=True).stdout.strip()
            git("init");git("add",".")
            git("-c","user.name=Port unit test","-c","user.email=test@example.invalid","commit","-m","Test base")
            old_root,old_base=build.ROOT,build.BASE
            try:
                build.ROOT=root;build.BASE=git("rev-parse","HEAD")
                (root/"recomp/funcs.h").write_text("synced header\n")
                build.check_repository(False)
                (root/"runner/dkc1_game.c").write_text("changed core\n")
                with self.assertRaisesRegex(RuntimeError,"Hay cambios"):
                    build.check_repository(False)
                build.check_repository(True)
            finally:build.ROOT,build.BASE=old_root,old_base
    def test_sdk_missing(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaisesRegex(RuntimeError,"Faltan componentes"):build.check_sdk(Path(t))
    def extract(self,entries):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);zpath=root/"input.zip";out=root/"out";out.mkdir()
            with zipfile.ZipFile(zpath,"w") as z:
                for name,data in entries:z.writestr(name,data)
            wrapper.safe_extract(zpath,out)
            return [p.relative_to(out).as_posix() for p in out.rglob("*") if p.is_file()]
    def test_safe_gradle_zip(self):
        self.assertEqual(self.extract([("gradle-8.11.1/bin/gradle",b"ok")]),["gradle-8.11.1/bin/gradle"])
    def test_zip_traversal(self):
        for path in ("../outside", "/root/outside", "gradle-8.11.1/../../outside",
                     "gradle-8.11.1\\bad", "C:/bad", "other/bin/gradle"):
            with self.subTest(path=path),self.assertRaises(ValueError):self.extract([(path,b"x")])
    def test_zip_symlink(self):
        info=zipfile.ZipInfo("gradle-8.11.1/link")
        info.create_system=3;info.external_attr=(stat.S_IFLNK|0o777)<<16
        with self.assertRaises(ValueError):self.extract([(info,b"../../outside")])
    def test_manifest_no_broad_permissions(self):
        root=ET.parse(ANDROID/"app/src/main/AndroidManifest.xml").getroot()
        self.assertFalse(root.findall("uses-permission"))
        ns="{http://schemas.android.com/apk/res/android}"
        game=[a for a in root.find("application").findall("activity") if a.attrib[ns+"name"]==".GameActivity"][0]
        self.assertEqual(game.attrib[ns+"exported"],"false")
        self.assertEqual(game.attrib[ns+"process"],":game")
    def test_jni_names(self):
        java=(ANDROID/"app/src/main/java/com/ylports/dkc1recomp/GameActivity.java").read_text()
        c=(ANDROID/"native/android_host.c").read_text()
        names=re.findall(r"native void (native\w+)\(",java)
        self.assertEqual(len(names),5)
        for name in names:self.assertIn("Java_com_ylports_dkc1recomp_GameActivity_"+name,c)
    def test_no_desktop_frontend_linked(self):
        cmake=(ANDROID/"native/CMakeLists.txt").read_text()
        uncommented="\n".join(line for line in cmake.splitlines() if not line.lstrip().startswith("#"))
        self.assertNotIn("sdl_host.c",uncommented)
        self.assertNotIn("macos_graphics.m",uncommented)
        self.assertIn("--no-undefined",cmake)
        self.assertIn("16384",cmake)
    def test_rom_verification_runs_before_sdk(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/"bad.sfc";p.write_bytes(b"invalid")
            result=subprocess.run([sys.executable,str(ANDROID/"tools/build_android.py"),"--rom",str(p),"--verify-only"],capture_output=True,text=True)
            self.assertEqual(result.returncode,1);self.assertIn("4 MiB",result.stderr)
    def test_apply_refuses_existing_destination(self):
        script=ANDROID.parent/"APLICAR_PORT.py"
        if not script.is_file():self.skipTest("Overlay-only checkout")
        with tempfile.TemporaryDirectory() as t:
            p=Path(t);sentinel=p/"keep";sentinel.write_text("user data")
            result=subprocess.run([sys.executable,str(script),"--destino",str(p)],capture_output=True,text=True)
            self.assertEqual(result.returncode,1);self.assertEqual(sentinel.read_text(),"user data")
            self.assertFalse((p/".git").exists())

if __name__=="__main__":unittest.main(verbosity=2)
