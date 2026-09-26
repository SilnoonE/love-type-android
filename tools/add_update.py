"""Append-only Android snapshot export. PDF checks require pypdf."""
from pathlib import Path
import argparse, hashlib, json, re, subprocess, sys
from datetime import date

ROOT=Path(__file__).resolve().parents[1]
ID_RE=re.compile(r'^(\d{4})/(\d{2})/(\d{2})-(\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)$')
TEXT={'.kt','.kts','.xml','.properties','.pro','.toml','.md','.json','.bat','.py','.txt','.sh'}
ASSETS={'.png','.webp','.jpg','.jpeg','.svg'}
TEST_IDS={'app':'ca-app-pub-3940256099942544~3347511713','banner':'ca-app-pub-3940256099942544/6300978111','interstitial':'ca-app-pub-3940256099942544/1033173712'}
SECRETS=re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{15,}|gh[pousr]_[A-Za-z0-9]{20,}|AIza[\w-]{25,}|(?im:^\s*(?:storePassword|keyPassword|api[_-]?key|access[_-]?token)\s*[=:]\s*["\']?[^\s"\']+)')

def checked_id(value):
    m=ID_RE.fullmatch(value)
    if not m:raise ValueError('ID must be YYYY/MM/DD-NN-slug')
    date(*map(int,m.group(1,2,3)))
    p=(ROOT/'updates'/value).resolve()
    if not p.is_relative_to((ROOT/'updates').resolve()):raise ValueError('Outside updates')
    return p

def git(args):
    return subprocess.run(['git','-C',str(ROOT),*args],capture_output=True,text=True,encoding='utf-8',check=False)

def scan_text(text,label):
    if SECRETS.search(text):raise ValueError('Potential secret in '+label)
    ids=re.findall(r'ca-app-pub-\d+[~/]\d+',text)
    if any(x not in TEST_IDS.values() for x in ids):raise ValueError('Production advertising ID in '+label)

def check_public_pdf(raw):
    import io
    try:
        from pypdf import PdfReader
    except ImportError as exc:
        raise ValueError("Install pypdf to check the public PDF page limit") from exc
    try:
        pages = len(PdfReader(io.BytesIO(raw)).pages)
    except Exception as exc:
        raise ValueError("Cannot inspect PDF") from exc
    if not 1 <= pages <= 10:
        raise ValueError("Public PDF must contain 1 to 10 pages")

def clean(text,rel):
    ids=re.findall(r'ca-app-pub-\d+[~/]\d+',text)
    for ident in set(ids):
        if '~' in ident:target=TEST_IDS['app']
        elif rel.endswith('MainActivity.kt'):target=TEST_IDS['interstitial']
        elif rel.endswith('activity_main.xml'):target=TEST_IDS['banner']
        else:raise ValueError('Review unfamiliar advertising location: '+rel)
        text=text.replace(ident,target)
    scan_text(text,rel)
    return '\n'.join(x.rstrip() for x in text.replace('\r\n','\n').splitlines()).rstrip()+'\n'

def export(source):
    src=source.resolve()
    if not (src/'app/src/main/AndroidManifest.xml').is_file():raise ValueError('Not an Android project')
    rootfiles=['build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','app/build.gradle.kts','app/proguard-rules.pro','gradle/libs.versions.toml','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties']
    candidates=[src/x for x in rootfiles]
    candidates+=list((src/'app/src').rglob('*'))
    contents={};changed=[]
    for f in candidates:
        if not f.is_file():continue
        if f.is_symlink() or not f.resolve().is_relative_to(src):raise ValueError('Unexpected external path')
        rel=f.relative_to(src).as_posix()
        if rel.startswith('app/src/') and f.suffix.lower() not in {'.kt','.java','.xml',*ASSETS}:raise ValueError('Review non-source file: '+rel)
        if re.search(r'(?i)(google-services|keystore|testkey|credentials|secrets|\.env)',rel):raise ValueError('Excluded path: '+rel)
        raw=f.read_bytes()
        if f.suffix.lower() in TEXT or f.name=='gradlew':
            original=raw.decode('utf-8-sig');cleaned=clean(original,rel)
            if re.findall(r'ca-app-pub-\d+[~/]\d+',original)!=re.findall(r'ca-app-pub-\d+[~/]\d+',cleaned):changed.append(rel)
            raw=cleaned.encode('utf-8')
        if len(raw)>90*1024*1024:raise ValueError('File too large: '+rel)
        contents[rel]=raw
    return contents,changed

def seal(value):
    folder=checked_id(value)
    if not folder.is_dir():raise ValueError('No snapshot folder')
    rel=folder.relative_to(ROOT).as_posix()
    tracked=git(['ls-tree','-r','--name-only','HEAD','--',rel])
    if tracked.returncode==0 and tracked.stdout.strip():raise ValueError('Already committed: create another update instead')
    files={}
    for f in sorted(folder.rglob('*')):
        if f.is_file() and f.name!='manifest.json':
            if f.is_symlink():raise ValueError('Symlink in archive')
            raw=f.read_bytes()
            if f.suffix.lower()=='.pdf':check_public_pdf(raw)
            if f.suffix.lower() in TEXT or f.name=='gradlew':
                normalized=raw.replace(b'\r\n',b'\n')
                if normalized!=raw:f.write_bytes(normalized)
                raw=normalized
            if f.suffix.lower() in TEXT:scan_text(raw.decode('utf-8-sig'),str(f.relative_to(folder)))
            files[f.relative_to(folder).as_posix()]={'sha256':hashlib.sha256(raw).hexdigest(),'bytes':len(raw)}
    manifest={'schemaVersion':1,'snapshotId':value,'policy':'append-only','files':files}
    (folder/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print('SEALED',value,'files='+str(len(files)))

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--source',type=Path);ap.add_argument('--id');ap.add_argument('--title');ap.add_argument('--guide',type=Path);ap.add_argument('--seal')
    args=ap.parse_args()
    if args.seal:seal(args.seal);return
    if not args.source or not args.id or not args.title:ap.error('--source, --id and --title required')
    target=checked_id(args.id)
    if target.exists():raise ValueError('Snapshot already exists; choose a NEW date/sequence ID')
    content,changed=export(args.source)
    pdf=None
    if args.guide:
        pdf=args.guide.read_bytes()
        if not pdf.startswith(b'%PDF-'):raise ValueError('Guide must be a PDF')
        check_public_pdf(pdf)
    target.mkdir(parents=True,exist_ok=False)
    for rel,raw in content.items():
        dst=target/'source'/rel;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(raw)
    for d in ['docs','verification']:(target/d).mkdir()
    (target/'README.md').write_text('# '+args.title+'\n\n이번 업데이트의 변화와 검증 범위를 기록하세요. 기존 폴더는 수정하지 않습니다.\n',encoding='utf-8',newline='\n')
    (target/'docs/EXPORT_NOTES.md').write_text('# 공유 사본 가공 내역\n\n운영 광고 ID만 공식 테스트 ID로 치환한 파일:\n\n'+''.join('- `'+p+'`\n' for p in changed)+'\n서명 키, 캐시, 로컬 설정, 로컬 JDK 설정은 허용 목록에 포함하지 않았습니다. 원본 프로젝트는 수정하지 않았습니다.\n',encoding='utf-8',newline='\n')
    if pdf:(target/'docs/code-guide.pdf').write_bytes(pdf)
    index=ROOT/'updates/README.md'
    with index.open('a',encoding='utf-8',newline='\n') as f:
        day=args.id[:10].replace('/','-')
        f.write(f'| {day} | [{args.title}]({args.id}/README.md) | [소스]({args.id}/source/) | [문서]({args.id}/docs/) | [검증]({args.id}/verification/) |\n')
    print('CREATED',args.id,'source_files='+str(len(content)),'sanitized_files='+str(len(changed)))
    print('Fill in documentation, then run --seal with the same ID. No commit or push was performed.')

if __name__=='__main__':
    try:main()
    except (ValueError,OSError) as e:print('STOP:',e,file=sys.stderr);sys.exit(1)
