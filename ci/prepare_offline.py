#!/usr/bin/env python3
"""Back up pinned build dependencies, encrypted to a session's public key.

Never reads a ROM. The tiny temporary app only resolves Gradle dependencies:
it is NOT DKC1, is removed, and is NEVER published as an APK. Only the public
certificate is committed. Its private key stays outside GitHub.
"""
from pathlib import Path
import hashlib, os, shutil, subprocess, urllib.request
ROOT=Path.cwd()
WORK=Path(os.environ['RUNNER_TEMP'])/'dkc1-offline'
WORK.mkdir(exist_ok=True)
SDK=Path(os.environ['ANDROID_HOME'])
NDK='28.2.13676358'
SDL='c98c4fbff6d8f3016a3ce6685bf8f43433c3efcc'
GRADLE='8.11.1'
def run(args, **kwargs):
    subprocess.run([str(x) for x in args], check=True, **kwargs)
def download(url, target):
    with urllib.request.urlopen(url,timeout=180) as r, target.open('wb') as f:
        shutil.copyfileobj(r,f)
kit=WORK/'kit';kit.mkdir(exist_ok=True)
source=WORK/'public/repository'
shutil.copytree(ROOT,source,ignore=shutil.ignore_patterns('.git','.gradle','build'),dirs_exist_ok=True)
sdl=source/'android/third_party/SDL2'
sdl.mkdir(parents=True,exist_ok=True)
run(['git','init',sdl])
run(['git','-C',sdl,'remote','add','origin','https://github.com/libsdl-org/SDL.git'])
run(['git','-C',sdl,'fetch','--depth','1','origin',SDL])
run(['git','-C',sdl,'checkout','--detach','FETCH_HEAD'])
shutil.rmtree(sdl/'.git')
run(['tar','-czf',WORK/'sources.tar.gz','-C',WORK/'public','repository'])
# Hosted runner SDKs already have license files. Do not accept new terms automatically.
sdkmanager=SDK/'cmdline-tools/latest/bin/sdkmanager'
run([sdkmanager,'--install','platforms;android-35','build-tools;35.0.0',
     'cmake;3.22.1','ndk;'+NDK])
url=f'https://services.gradle.org/distributions/gradle-{GRADLE}-bin.zip'
archive=WORK/'gradle.zip'
download(url,archive)
with urllib.request.urlopen(url+'.sha256',timeout=60) as r:
    digest=r.read(256).decode().strip()
if hashlib.sha256(archive.read_bytes()).hexdigest()!=digest:
    raise RuntimeError('Gradle checksum mismatch')
run(['unzip','-q',archive,'-d',kit])
(kit/f'gradle-{GRADLE}/.verified-sha256').write_text(digest+'\n')
archive.unlink()
warm=WORK/'warm';(warm/'app/src/main').mkdir(parents=True,exist_ok=True)
(warm/'settings.gradle').write_text('''pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name='DKC1RecompAndroid'
include ':app'
''')
(warm/'build.gradle').write_text("plugins { id 'com.android.application' version '8.9.2' apply false }\n")
(warm/'app/build.gradle').write_text('''plugins { id 'com.android.application' }
android {
 namespace 'com.ylports.dkc1recomp'; compileSdk 35; buildToolsVersion '35.0.0'; ndkVersion '28.2.13676358'
 defaultConfig { applicationId 'com.ylports.dkc1recomp'; minSdk 23; targetSdk 35; versionCode 1; versionName 'dependency-cache-only'; ndk { abiFilters 'arm64-v8a' } }
 compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
 externalNativeBuild { cmake { path file('CMakeLists.txt'); version '3.22.1' } }
 buildTypes { release { minifyEnabled false } }
 packaging { jniLibs { useLegacyPackaging false } }
}
''')
(warm/'app/src/main/AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="Dependency cache only"/></manifest>')
(warm/'app/CMakeLists.txt').write_text('cmake_minimum_required(VERSION 3.22)\nproject(dependency_cache C)\nadd_library(cache_only SHARED cache_only.c)\n')
(warm/'app/cache_only.c').write_text('int dependency_cache_only(void) { return 0; }\n')
(warm/'gradle.properties').write_text('org.gradle.jvmargs=-Xmx2g\norg.gradle.workers.max=2\nandroid.useAndroidX=false\n')
os.environ['GRADLE_USER_HOME']=str(WORK/'gradle-home')
run([kit/f'gradle-{GRADLE}/bin/gradle','--no-daemon',':app:assembleDebug',':app:assembleRelease',':app:lintDebug'],cwd=warm)
shutil.rmtree(warm)
cache=kit/'gradle-home/caches';cache.mkdir(parents=True,exist_ok=True)
shutil.copytree(WORK/'gradle-home/caches/modules-2',cache/'modules-2',
               ignore=shutil.ignore_patterns('*.lock'),dirs_exist_ok=True)
# Full copies of required SDK components, not modified SDK binaries.
for name in ('platforms/android-35','build-tools/35.0.0','cmake/3.22.1','ndk/'+NDK,'licenses'):
    dst=kit/'sdk'/name;dst.parent.mkdir(parents=True,exist_ok=True)
    shutil.copytree(SDK/name,dst,symlinks=True,dirs_exist_ok=True)
run(['tar','-czf',WORK/'tools.tar.gz','-C',kit,'.'])
run(['openssl','cms','-encrypt','-binary','-aes256','-stream','-in',WORK/'tools.tar.gz',
     '-out',WORK/'tools.p7m','-outform','DER',ROOT/'ci/offline-recipient.pem'])
with (WORK/'tools.p7m').open('rb') as f:
    print('Encrypted archive SHA-256:',hashlib.file_digest(f,'sha256').hexdigest())
print('Encrypted dependency backup prepared. No ROM read/uploaded; no game APK built.')
