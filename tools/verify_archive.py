"""Verify snapshot manifests and preserve every file from prior snapshots."""
from pathlib import Path
import argparse, hashlib, json, re, subprocess, sys
from add_update import ROOT, TEXT, scan_text, check_public_pdf

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--base');a=parser.parse_args()
    manifests=list((ROOT/'updates').glob('*/*/*/manifest.json'))
    if not manifests:raise ValueError('No sealed snapshots')
    count=0
    for mf in manifests:
        folder=mf.parent;data=json.loads(mf.read_text(encoding='utf-8'));expected=data['files']
        actual={p.relative_to(folder).as_posix() for p in folder.rglob('*') if p.is_file() and p!=mf}
        if actual!=set(expected):raise ValueError('Snapshot file list changed: '+data['snapshotId'])
        for rel,item in expected.items():
            p=(folder/rel).resolve()
            if not p.is_relative_to(folder.resolve()) or p.is_symlink():raise ValueError('Bad manifest path')
            raw=p.read_bytes()
            if p.suffix.lower()=='.pdf':check_public_pdf(raw)
            if hashlib.sha256(raw).hexdigest()!=item['sha256'] or len(raw)!=item['bytes']:raise ValueError('Snapshot hash changed: '+rel)
            if p.suffix.lower() in TEXT:scan_text(raw.decode('utf-8-sig'),rel)
            if re.search(r'(?i)(^|/)(?:local.properties|testkey[^/]*|google-services.json)$|\.(?:jks|keystore|apk|aab|p12|db)$',rel):raise ValueError('Forbidden path: '+rel)
            count+=1
    if a.base:
        def g(*args):return subprocess.run(['git','-C',str(ROOT),*args],capture_output=True,check=True).stdout
        base=g('rev-parse','--verify',a.base+'^{commit}').decode().strip()
        # Compare baseline git blobs to working tree (not just staged changes).
        prior=g('ls-tree','-r','-z',base,'--','updates/').split(b'\0')
        oldfolders=set();oldpaths=set()
        for entry in prior:
            if not entry:continue
            meta,name=entry.split(b'\t',1);rel=name.decode();parts=rel.split('/')
            if len(parts)<5 or not re.fullmatch(r'\d{4}',parts[1]):continue
            oldfolders.add('/'.join(parts[:4]));oldpaths.add(rel)
            file=ROOT/rel
            if not file.is_file():raise ValueError('Old file deleted: '+rel)
            blob=meta.split()[2].decode();expected=g('cat-file','blob',blob)
            raw=file.read_bytes()
            if file.suffix.lower() in TEXT or file.name=='gradlew':
                raw=raw.replace(b'\r\n',b'\n');expected=expected.replace(b'\r\n',b'\n')
            if raw!=expected:raise ValueError('Old file modified: '+rel)
        for folder in oldfolders:
            for p in (ROOT/folder).rglob('*'):
                if p.is_file() and p.relative_to(ROOT).as_posix() not in oldpaths:raise ValueError('File added inside old snapshot: '+str(p.relative_to(ROOT)))
    print('OK snapshots='+str(len(manifests))+' files='+str(count)+'; integrity and preservation checks passed')

if __name__=='__main__':
    try:main()
    except (ValueError,OSError,subprocess.CalledProcessError) as e:print('STOP:',e,file=sys.stderr);sys.exit(1)
