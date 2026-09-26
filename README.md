# 연애 성격 테스트 · 개발 기록

한 번의 성격 테스트에서 **매일 답하고 카드를 모으는 앱**으로 확장한 과정을 코드와 설명으로 기록합니다.

**새 업데이트를 올려도 이전 자료는 그대로 남습니다.** 날짜별 폴더 안에 당시 소스·개발 기록·학습 자료·검증 결과를 함께 보관합니다. 이 저장소는 **공개**로 운영합니다. 운영 광고 ID·서명 키·개인정보를 제외한 전체 소스와 공개용 개발 요약을 공개합니다. 개인용 상세 학습서는 공개하지 않습니다.

개인용 상세 학습서는 로컬에 보관하고, 공개 PDF는 **10쪽 이내**로 제한합니다. [이번 공개 범위 정정](updates/2026/09/26-03-public-summary/README.md).

## 바로 보기

- [전체 업데이트 목록](updates/README.md)
- [2026-09-26: 전체 소스 공개 전환 기록](updates/2026/09/26-02-publication/README.md)
- [2026-09-26: 오늘의 질문과 카드 모음집](updates/2026/09/26-01-daily-cards/README.md)
- [공개용 개발 요약 - GitHub에서 읽기](updates/2026/09/26-01-daily-cards/docs/CODE_GUIDE.md)
- [8쪽 공개용 개발 요약](updates/2026/09/26-01-daily-cards/docs/code-guide.pdf)
- [실제 Android 소스](updates/2026/09/26-01-daily-cards/source/)
- [확인된 보완점](updates/2026/09/26-01-daily-cards/docs/KNOWN_ISSUES.md)

## 누적 구조

```text
updates/
  README.md                       # 모든 업데이트 목차
  2026/
    09/
      26-01-daily-cards/
        README.md                 # 이번 변화와 읽는 순서
        source/                   # 당시 Android 프로젝트 사본
        docs/                     # 개발 과정, 구조, 코드 해설, PDF
        verification/             # 검증 범위와 당시 결과
        manifest.json             # 보관 파일 목록과 SHA-256
tools/
  add_update.py                   # 새 폴더만 만드는 등록 도구
  verify_archive.py               # 무결성·과거 폴더 보존 검사
docs/
  ARCHIVE_POLICY.md               # 누적 규칙과 다음 업로드 방법
  PUBLICATION_POLICY.md           # 포함·제외 항목
```

다음 작업은 새 형제 폴더에 추가합니다. 예: `updates/2026/09/27-01-fix-state/`. 기존 `26-01-daily-cards`는 덮어쓰지 않습니다.

## 소스 열기

Android Studio에서 확인하려는 업데이트의 **source 폴더**를 프로젝트로 엽니다. 저장소 루트는 여러 버전을 모아둔 기록실이므로 Android 프로젝트 루트가 아닙니다.

현재 소스는 AGP 9.0.1, Gradle 9.2.1, compile/target SDK 36, minSdk 24입니다. SDK/JDK 위치는 각 개발 환경에서 설정하세요. 운영 광고 ID는 Google의 공식 테스트 ID로 교체했고, 서명 키와 로컬 설정은 제외했습니다. 이 사본으로 운영 앱을 배포하지 마세요.

## 이번 보관의 기준

이번이 이 앱의 **첫 GitHub 전체 스냅샷**입니다. 과거 전체 소스가 없어 이전 버전 스냅샷을 임의로 만들지 않았습니다. 초기 기능은 개발 기록으로 설명하고, 현재 확보한 소스부터 누적 보관합니다.

앱 개발 완료 보고, 기존 테스트 결과 파일, 직접 수행한 검증을 구분해 기록했습니다. 저장 오류·날짜 변경·화면 복원 등의 보완 항목도 숨기지 않고 남겼습니다.

[보관 규칙](docs/ARCHIVE_POLICY.md) · [업로드 제외 정책](docs/PUBLICATION_POLICY.md)
