#!/usr/bin/env python3
import argparse, hashlib, json, os, re, subprocess, zipfile
from pathlib import Path

def run(cmd):
    p=subprocess.run(cmd,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if p.returncode: raise RuntimeError(f'{cmd[0]} failed {p.returncode}:\n{p.stdout}')
    return p.stdout

def check(condition, message):
    # Explicit checks instead of assert, which `python -O` would remove.
    if not condition:
        raise SystemExit(f'APK verification failed: {message}')

def main():
    a=argparse.ArgumentParser(); a.add_argument('apk',type=Path); a.add_argument('--build-tools',required=True,type=Path); a.add_argument('--signed',action='store_true'); a.add_argument('--release',action='store_true'); a.add_argument('--expect-version-name'); a.add_argument('--expect-version-code',type=int); a.add_argument('--certificate-sha256'); a.add_argument('--output',type=Path); x=a.parse_args()
    bt=x.build_tools; apk=x.apk
    b=run([str(bt/('aapt2.exe' if os.name=='nt' else 'aapt2')),'dump','badging',str(apk)])
    check("package: name='zepigit.firefin.app'" in b, 'package is not zepigit.firefin.app')
    check("minSdkVersion:'21'" in b, 'minSdk is not 21')
    check("application-label:'Firefin'" in b, "label is not 'Firefin'")
    if x.expect_version_name: check(f"versionName='{x.expect_version_name}'" in b, f'versionName is not {x.expect_version_name}')
    if x.expect_version_code is not None: check(f"versionCode='{x.expect_version_code}'" in b, f'versionCode is not {x.expect_version_code}')
    if x.release: check("application-debuggable" not in b, 'release APK is debuggable')
    banner = re.search(r"application:.*banner='([^']*)'", b)
    check(banner and banner.group(1), 'missing non-empty application banner')
    check('leanback-launchable-activity:' in b, 'missing Leanback launcher activity')
    run([str(bt/('zipalign.exe' if os.name=='nt' else 'zipalign')),'-c','-v','4',str(apk)])
    with zipfile.ZipFile(apk) as z:
        names=z.namelist()
        forbidden=('flutter_assets/','libflutter.so','libapp.so','libmpv.so','kernel_blob.bin')
        check(not any(any(v in n for v in forbidden) for n in names), 'Flutter/mpv artifacts present')
        libs=[n for n in names if n.startswith('lib/') and n.endswith('.so')]
        if libs:
            allowed = {'armeabi-v7a'} if x.release else {'armeabi-v7a', 'x86'}
            abis = {n.split('/')[1] for n in libs}
            check('armeabi-v7a' in abis and abis <= allowed, f'unexpected native ABIs: {abis}')
    if x.signed:
        signer=bt/('apksigner.bat' if os.name=='nt' else 'apksigner')
        out=run([str(signer),'verify','--min-sdk-version','21','--verbose','--print-certs',str(apk)])
        check(re.search(r'Verified using v1 scheme.*: true',out), 'v1 signature not verified')
        check(re.search(r'Verified using v2 scheme.*: true',out), 'v2 signature not verified')
        if x.certificate_sha256:
            cert=x.certificate_sha256.replace(':','').lower()
            actual=[v.lower() for v in re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f:]+)',out,re.I)]
            check(actual and all(v==cert for v in actual), 'signing certificate does not match the pinned SHA-256')
    result={'apk':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'applicationId':'zepigit.firefin.app','minSdk':21,'signed':x.signed}
    text=json.dumps(result,indent=2)+'\n'; print(text)
    if x.output: x.output.write_text(text,encoding='utf-8')
main()
