# 현재 소스에서 확인한 보완점

정적 소스 검토 결과입니다. 이 보관 작업에서는 앱 로직을 수정하거나 실기기에서 재현하지 않았습니다.

| 항목 | 코드 위치 | 이유와 다음 확인 |
|---|---|---|
| 손상 데이터 보호 | DailyCardRepository.getAllRecords | 읽기 실패가 빈 목록처럼 반환돼 다음 저장 때 원본을 덮을 수 있음. 오류 상태·백업 필요. |
| 저장 형식 버전 | schema_version | 저장만 하고 읽기 시 상위 버전 확인이 없음. |
| 날짜 검증 | MainActivity.submitDailyAnswer | 날짜 불일치와 currentDailyRecord==null을 동시에 검사해 기록이 남으면 차단을 우회할 수 있음. |
| 자정 갱신 | MainActivity.onResume | 홈 복귀 때 일부 갱신하며 켜둔 화면 자정 감시는 없음. |
| 선택 복원 | onCreate → restoreScreen → showDailyQuestionScreen | 복원한 선택 ID를 다시 null로 초기화할 수 있음. |
| 화면 출처 | screenOrigin | 상세에서 수정으로 가면 원래 모음집 출처를 잃을 수 있음. |
| 기존 테스트 복원 | onSaveInstanceState | 기존 점수와 질문 위치를 Bundle에 보관하지 않음. |
| 저장 중 입력 | submitDailyAnswer / 선택지 클릭 | 제출 버튼을 꺼도 옵션 클릭이 다시 켜 중복 요청 가능. |
| 즐겨찾기 실패·연타 | setFavorite 콜백 | 실패 안내와 저장 중 잠금이 없고 오래된 화면값을 토글할 수 있음. |
| 생명주기 | Repository·Activity 콜백 | Activity 종료 이후 UI 콜백, 인스턴스별 executor·잠금 검토 필요. |
| 공유 파일 | daily_card_share.png | 고정 파일명 덮어쓰기로 연속 공유 시 수신 내용이 달라질 수 있음. |
| 접근성·디자인 | item_daily_card.xml 등 | 즐겨찾기 40dp, 장식 그림 설명, 주제별 카드 전체 색상 미적용. |
| 날짜 테스트 | DailyFeatureUnitTest | 실제 Scheduler를 호출하지 않고 수식을 복사해 검사함. |

주요 검토 범위는 [공개용 개발 요약](CODE_GUIDE.md)을, 실제 구현은 이 스냅샷의 source 폴더를 참고하세요.
