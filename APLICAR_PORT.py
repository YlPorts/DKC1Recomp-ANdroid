#!/usr/bin/env python3
"""Apply this Android overlay to a new checkout; never overwrite an existing directory."""
from __future__ import annotations
import argparse
from pathlib import Path
import shutil
import subprocess
import sys

BASE = "cb4dae77a6552790cbb7a9663957b2cb6c3e6b58"
URL = "https://github.com/YlPorts/DKC1Recomp-ANdroid.git"
PACKAGE = Path(__file__).resolve().parent

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destino",type=Path,default=Path("DKC1Recomp-Android"))
    args=parser.parse_args();target=args.destino.expanduser().resolve()
    if target.exists():raise RuntimeError("El destino ya existe. No se modifica: "+str(target))
    if not (PACKAGE/"android/tools/build_android.py").is_file():raise RuntimeError("Paquete Android incompleto.")
    if not shutil.which("git"):raise RuntimeError("Instala Git antes de aplicar el port.")
    target.mkdir(parents=True)
    def git(*args):subprocess.run(["git",*args],cwd=target,check=True)
    git("init");git("remote","add","origin",URL)
    git("fetch","--depth","1","origin",BASE);git("checkout","--detach","FETCH_HEAD")
    shutil.copytree(PACKAGE/"android",target/"android",
                    ignore=shutil.ignore_patterns(".gradle",".cxx",".tool-cache","third_party","__pycache__","build","local.properties"))
    print("Adaptación aplicada en",target)
    print('Ejecuta: python android/tools/build_android.py --rom "RUTA_A_TU_ROM" --sdk "RUTA_A_TU_SDK"')
    return 0

if __name__=="__main__":
    try:raise SystemExit(main())
    except (OSError,RuntimeError,subprocess.CalledProcessError) as error:
        print("ERROR:",error,file=sys.stderr);raise SystemExit(1)
