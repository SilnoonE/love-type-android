# 이전 것이 사라지지 않는 누적 방식

## 세 겹으로 기록

1. **폴더**: 날짜와 순번으로 자료 전체를 분리한다.
2. **커밋**: 새 작업을 새 커밋으로 쌓는다. 과거 커밋은 재작성하지 않는다.
3. **태그**: 보관 시점에 `snapshot-YYYY-MM-DD-NN-slug` 태그를 붙인다. 이미 있는 태그는 옮기지 않는다.

태그와 폴더는 앱 출시 버전과 별개입니다. 앱 versionCode를 임의로 올리지 않습니다. 당일 두 번째 업로드는 `DD-02-...`처럼 별도 폴더를 만듭니다.

## 다음 업로드

Python 3로 실행합니다. 아래 경로와 제목은 다음 작업에 맞춰 바꿉니다.

```powershell
python tools/add_update.py --source "E:/fifteenthapppshocology" --id "2026/09/27-01-fix-state" --title "화면 복원 보완"
```

새로 생성된 README·docs·verification 내용을 실제 작업에 맞게 작성한 뒤 파일 목록을 봉인합니다. PDF가 있으면 `--guide` 옵션으로 명시적으로 지정합니다.

```powershell
python tools/add_update.py --seal "2026/09/27-01-fix-state"
python tools/verify_archive.py --base HEAD
git add updates
git diff --cached --stat
git commit -m "archive: add 2026-09-27 state restoration update"
git tag -a snapshot-2026-09-27-01-fix-state -m "화면 복원 보완 기록"
git push origin main
git push origin snapshot-2026-09-27-01-fix-state
```

`add_update.py`는 이미 존재하는 폴더 ID를 거부합니다. `--seal`도 이미 커밋된 폴더를 거부합니다. `verify_archive.py --base HEAD`는 기존 스냅샷의 수정·삭제·추가 및 파일 해시 불일치를 검사합니다.

## 과거 문서에 오류가 있으면

과거 폴더를 고치지 말고 새 폴더에 정정 이유와 대상 문서 링크를 남깁니다. 목차에 새 정정을 연결합니다. 최신 문서로 바뀌더라도 원래 작성 내용을 계속 볼 수 있습니다.

## 주의할 점

검증 도구는 로컬 작업을 점검하는 도구입니다. 서버의 강제 푸시 방지·관리자 삭제 금지 기능을 설정한 것은 아닙니다. GitHub에서 임의로 폴더를 삭제하거나 force-push하면 규칙을 우회할 수 있으므로 사용하지 않습니다. 이 저장소의 AGENTS.md에도 같은 규칙을 명시했습니다.
