#!/usr/bin/env python3
"""Verify the real APK's signature, ARM64 ELF layout and JNI exports, not gameplay."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

def run(command: list[str]) -> str:
    result=subprocess.run(command,check=True,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    return result.stdout

def elf_loads(data: bytes) -> list[dict[str,int]]:
    if data[:6]!=b'\x7fELF\x02\x01':raise ValueError('Expected little-endian ELF64')
    if struct.unpack_from('<H',data,18)[0]!=183:raise ValueError('Expected AArch64 ELF')
    offset=struct.unpack_from('<Q',data,32)[0]
    size,count=struct.unpack_from('<HH',data,54)
    if size!=56 or offset+size*count>len(data):raise ValueError('Invalid program header table')
    result=[]
    for i in range(count):
        kind,flags,off,addr,physical,filesz,memsz,align=struct.unpack_from('<IIQQQQQQ',data,offset+i*size)
        if kind==1:
            if align<16384 or (off-addr)%16384:raise ValueError('ELF segment lacks 16-KiB alignment')
            result.append(dict(offset=off,virtual_address=addr,alignment=align))
    if not result:raise ValueError('No ELF loadable segments')
    return result

def verify(apk: Path,sdk: Path) -> dict:
    tools=sdk/'build-tools/35.0.0'
    sign=tools/('apksigner.bat' if os.name=='nt' else 'apksigner')
    executable='.exe' if os.name=='nt' else ''
    signatures=run([str(sign),'verify','--verbose','--print-certs',str(apk)])
    alignment=run([str(tools/('zipalign'+executable)),'-c','-P','16','4',str(apk)])
    metadata=run([str(tools/('aapt'+executable)),'dump','badging',str(apk)])
    if "name='com.ylports.dkc1recomp'" not in metadata:raise ValueError('Wrong package name')
    if "sdkVersion:'23'" not in metadata:raise ValueError('Unexpected minimum SDK')
    if "native-code: 'arm64-v8a'" not in metadata:raise ValueError('Unexpected ABI set')
    needed={'lib/arm64-v8a/libmain.so','lib/arm64-v8a/libSDL2.so'}
    binaries=[]
    with zipfile.ZipFile(apk) as archive:
        if len(archive.namelist())!=len(set(archive.namelist())):raise ValueError('Duplicate ZIP entries')
        native={n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')}
        if native!=needed:raise ValueError('Unexpected native libraries: '+str(native))
        if any(Path(n).suffix.lower() in ('.sfc','.smc','.srm','.sav','.state') for n in archive.namelist()):
            raise ValueError('Private game file found in APK')
        notices=[n for n in archive.namelist() if n.startswith('assets/licenses/')]
        if len(notices)<6:raise ValueError('Missing packaged license notices')
        for name in sorted(native):
            info=archive.getinfo(name)
            if info.compress_type!=zipfile.ZIP_STORED:raise ValueError('Native library unexpectedly compressed')
            data=archive.read(name)
            segments=elf_loads(data)
            binaries.append(dict(name=name,size=len(data),sha256=hashlib.sha256(data).hexdigest(),load_segments=segments))
            if name.endswith('/libmain.so'):
                prebuilt=sdk/'ndk/28.2.13676358/toolchains/llvm/prebuilt'
                hosts=list(prebuilt.glob('*'))
                nm=next((h/'bin'/('llvm-nm'+executable) for h in hosts if (h/'bin'/('llvm-nm'+executable)).exists()),None)
                if nm is None:raise ValueError('NDK llvm-nm missing')
                with tempfile.TemporaryDirectory(prefix='dkc1-apk-check-') as directory:
                    lib=Path(directory)/'libmain.so';lib.write_bytes(data)
                    symbols=run([str(nm),'-D','--defined-only',str(lib)])
                exports={line.split()[-1] for line in symbols.splitlines() if line.split()}
                required=['SDL_main']+['Java_com_ylports_dkc1recomp_GameActivity_'+n for n in
                    ('nativeSetTouchMask','nativeSetMenuPaused','nativeSetLifecyclePaused','nativeRequest','nativeSetMuted')]
                for symbol in required:
                    if symbol not in exports:raise ValueError('Missing native entry point: '+symbol)
    return dict(apk=apk.name,size_bytes=apk.stat().st_size,sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
        verification_passed=True,signature=signatures,zip_alignment_verified=True,metadata=metadata,
        native_libraries=binaries,license_files=notices,rom_file_bundled=False,
        installed_on_android=False,gameplay_tested_on_android=False,
        scope='Signature, package, ABI, 16-KiB alignment and JNI exports; not device execution')

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',required=True,type=Path)
    parser.add_argument('--sdk',required=True,type=Path)
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    try:
        report=verify(args.apk.resolve(),args.sdk.resolve())
        text=json.dumps(report,indent=2,ensure_ascii=False)+'\n'
        if args.report:args.report.write_text(text)
        print(text)
        return 0
    except (OSError,ValueError,subprocess.CalledProcessError,zipfile.BadZipFile) as error:
        print('APK verification failed:',error)
        if isinstance(error,subprocess.CalledProcessError):print(error.stdout or '')
        return 1
if __name__=='__main__':raise SystemExit(main())
