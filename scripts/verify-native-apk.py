#!/usr/bin/env python3
import argparse, hashlib, json, os, re, subprocess, zipfile
from pathlib import Path

def run(cmd):
    p=subprocess.run(cmd,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if p.returncode: raise RuntimeError(f'{cmd[0]} failed {p.returncode}:\n{p.stdout}')
    return p.stdout

def main():
    a=argparse.ArgumentParser(); a.add_argument('apk',type=Path); a.add_argument('--build-tools',required=True,type=Path); a.add_argument('--signed',action='store_true'); a.add_argument('--release',action='store_true'); a.add_argument('--expect-version-name'); a.add_argument('--expect-version-code',type=int); a.add_argument('--certificate-sha256'); a.add_argument('--output',type=Path); x=a.parse_args()
    bt=x.build_tools; apk=x.apk
    b=run([str(bt/('aapt2.exe' if os.name=='nt' else 'aapt2')),'dump','badging',str(apk)])
    assert "package: name='zepigit.firefin.app'" in b
    assert "minSdkVersion:'21'" in b
    assert "application-label:'Firefin'" in b
    if x.expect_version_name: assert f"versionName='{x.expect_version_name}'" in b
    if x.expect_version_code is not None: assert f"versionCode='{x.expect_version_code}'" in b
    if x.release: assert "application-debuggable" not in b
    banner = re.search(r"application:.*banner='([^']*)'", b)
    assert banner and banner.group(1), 'Missing non-empty application banner'
    assert 'leanback-launchable-activity:' in b
    run([str(bt/('zipalign.exe' if os.name=='nt' else 'zipalign')),'-c','-v','4',str(apk)])
    with zipfile.ZipFile(apk) as z:
        names=z.namelist()
        forbidden=('flutter_assets/','libflutter.so','libapp.so','libmpv.so','kernel_blob.bin')
        assert not any(any(v in n for v in forbidden) for n in names)
        libs=[n for n in names if n.startswith('lib/') and n.endswith('.so')]
        if libs: assert all(n.startswith('lib/armeabi-v7a/') for n in libs)
    if x.signed:
        signer=bt/('apksigner.bat' if os.name=='nt' else 'apksigner')
        out=run([str(signer),'verify','--min-sdk-version','21','--verbose','--print-certs',str(apk)])
        assert re.search(r'Verified using v1 scheme.*: true',out)
        assert re.search(r'Verified using v2 scheme.*: true',out)
        if x.certificate_sha256:
            cert=x.certificate_sha256.replace(':','').lower()
            actual=[v.lower() for v in re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f:]+)',out,re.I)]
            assert actual and all(v==cert for v in actual)
    result={'apk':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'applicationId':'zepigit.firefin.app','minSdk':21,'signed':x.signed}
    text=json.dumps(result,indent=2)+'\n'; print(text)
    if x.output: x.output.write_text(text,encoding='utf-8')
main()
