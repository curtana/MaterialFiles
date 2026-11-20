# SRS — 압축 파일 새로 고침 중 주 스레드 멈춤(ANR) (이슈 #83)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1.1, PR #87 2회차), 구현됨(PR #88, v1.7.5-beta.2) |
| 근거 | 요구사항 정리 v1 (이슈 #83), 재현 결과(같은 이슈, PM 요약), PM 결정(A안) |
| 기준 코드 | `origin/develop` ac012e0. 줄 번호는 모두 이 커밋 기준이다 |
| 증거 | `_workspace/wo/83-archive-anr/`(ANR 트레이스 `anr_menu_refresh_trace.txt`·`anr_swipe_refresh_trace.txt`·`anr_observe_then_refresh_trace.txt`, `progress.md`) |
| 관련 설계 | `docs/design/69-stale-archive/`(HLD 3.3·D6), `docs/design/29-list-refresh/` |

## 1. 목적과 범위

### 1.1 목적
압축 파일 안 목록에서 명시 새로 고침(메뉴 새로 고침, 당겨서 새로 고침)을 하면 주 스레드가 `FileListViewModel.reload()`(`filelist/FileListViewModel.kt:81-87`)에서 `archiveRefresh()`(`provider/archive/PathArchiveExtensions.kt:22-25`) → `ArchiveFileSystem.refresh()`(`provider/archive/ArchiveFileSystem.kt:122-129`)를 부른다. `refresh()`는 플래그 하나를 세우려고 `lock`을 잡는데, 이 `lock`은 작업 스레드가 압축 전체 항목을 읽는 동안(`ensureEntriesLocked`, `:131-146`, 모든 조회가 `synchronized(lock)` 안에서 읽는다 — `:63-67`, `:92-100` 등) 계속 잡혀 있다. 그래서 읽기가 진행 중이면 주 스레드가 읽기가 끝날 때까지 멈춘다.

AVD 재현(127 MB, 5만 항목, 첫 읽기 약 29초)에서 ANR 3건: 메뉴 새로 고침 2회 연속, 열자마자 당겨서 새로 고침, 감시(#69)가 시작한 다시 읽기 중 새로 고침. 세 트레이스 모두 주 스레드는 `ArchiveFileSystem.refresh(ArchiveFileSystem.kt:123)`에서 `waiting to lock`, 쥔 쪽은 `getDirectoryChildren(:94)` → `ensureEntriesLocked` → libarchive 읽기다. #69 감시 경로만으로는 주 스레드가 막히지 않았다.

### 1.2 범위
- `ArchiveFileSystem.refresh()`(무효화)가 진행 중인 항목 읽기를 기다리지 않게 한다(PM 결정 A안: 무효화는 긴 잠금 없이 세대 값만 올리고, 읽기는 잠금 안에서 세대를 보고 다시 읽는다).
- 그 뒤에도 바뀌지 않아야 할 동작(무효화 손실 없음, 닫힌 파일 시스템, #69 동작, 정상 열기·오류)을 요구사항으로 고정한다.

### 1.3 범위 밖
- 압축 처리 전반의 재설계(요구사항 정리 v1). 항목 읽기 자체의 속도, 진행 중인 읽기의 취소.
- 암호 입력 대화상자의 주 스레드 `addPassword()`(`fileaction/ArchivePasswordDialogFragment.kt:108` `archiveAddPassword`, 같은 `lock`) — 이슈 #86.
- `ArchiveFileSystem`의 다른 잠금 함수(`isOpen()`, `close()`, 조회 함수)의 잠금 방식. 이 SRS가 다루는 주 스레드 호출은 `refresh()`뿐이다(1.1; `addPassword()`는 #86).

## 2. 기능 요구사항

용어: **무효화** = `ArchiveFileSystem.refresh()` 호출. **항목 읽기** = `ensureEntriesLocked`(`:131-146`)가 `ArchiveReader.readEntries`로 압축 전체 항목을 읽는 일. **조회** = 항목을 쓰는 `getEntry`·`newInputStream`·`getDirectoryChildren`·`readSymbolicLink`(`:62-111`).

| ID | 요구사항 | 우선순위 | 검증 방법 |
|---|---|---|---|
| REQ-83-01 | 무효화는 다른 스레드에서 항목 읽기가 진행 중이어도 그 읽기가 끝나기를 기다리지 않고 돌아와야 한다(항목 읽기가 쥔 잠금을 기다리지 않는다). 그래서 명시 새로 고침의 주 스레드 호출(`FileListViewModel.kt:84`)이 멈추지 않는다. | 필수 | JVM 단위 시험: 가짜 항목 읽기를 래치로 막아 둔 채 다른 스레드에서 무효화 → 1초 안에 돌아옴(pass-after). fail-before는 지금 코드를 직접 시험할 수 없으므로(`ArchiveReader.readEntries`가 `ArchiveFileSystem` 안에 고정, `:138`) #69와 같은 방식으로 보인다 — 추출 클래스와 같은 모양에 지금의 잠금 구조(무효화도 같은 `lock`을 잡음, `:122-129`)를 흉내 낸 임시 구현을 먼저 커밋해 시험이 시간 초과로 실패함을 보이고, 실제 구현으로 바꿔 통과시킨다. 코드 검사: 무효화 경로에 항목 읽기와 같은 잠금이 없음. AVD(V1~V3). |
| REQ-83-02 | 항목 읽기가 진행 중일 때 무효화가 일어나면, 그 읽기가 끝난 뒤의 다음 조회는 항목을 다시 읽어야 한다(무효화가 진행 중인 읽기의 완료로 지워지지 않는다 — #69 HLD D6이 지키던 불변식). 진행 중이던 읽기를 시작한 조회는 그 읽기 결과를 그대로 돌려준다(같은 호출 안에서 다시 읽지 않는다 — 지금도 무효화가 읽기 끝을 기다리므로 그 조회는 무효화 전 결과를 받는다). 읽기가 진행 중이 아닐 때의 무효화도 지금처럼 다음 조회에서 다시 읽게 한다. | 필수 | JVM 단위 시험: 막힌 읽기 중 무효화 → 읽기 해제 → 다음 조회에서 가짜 항목 읽기가 한 번 더 불림(fail-before: REQ-83-01과 같은 임시 구현에서 무효화가 막혀 시간 초과, pass-after). 무효화 없이 두 번 조회하면 읽기는 한 번(캐시 유지). |
| REQ-83-03 | 닫힌 파일 시스템(`close()` 뒤)에서 무효화는 지금처럼 `ClosedFileSystemException`을 던져야 한다(`:124-126`). 닫힌 뒤의 조회도 지금처럼 `ClosedFileSystemException`(`:133-135`). | 필수 | JVM 단위 시험: 닫은 뒤 무효화·조회가 `ClosedFileSystemException`(수정 전후 모두 통과하는 회귀 방지 시험). |
| REQ-83-04 | 항목 읽기가 실패하면(`ArchiveException` → `IOException`, `:137-141`) 지금처럼 그 예외를 조회 호출자에게 던지고, 다음 조회가 다시 읽기를 시도해야 한다. 처음 여는 압축은 첫 조회에서 읽는다(지금의 `isRefreshNeeded = true` 초기값, `:56`). | 필수 | JVM 단위 시험: 가짜 읽기가 한 번 예외 → 조회가 그 예외를 던짐 → 다음 조회에서 읽기가 다시 불려 성공. |
| REQ-83-05 | #69 동작은 그대로여야 한다: 원본 압축 파일 변경 알림 → 무효화 → 다시 읽기(REQ-69-01·02), 지운 압축 → 오류 화면(REQ-69-03·04), 정상 열기·탐색(REQ-69-06·07). 감시 경로의 무효화(`ArchivePathObservable`, `provider/archive/ArchivePathObservable.kt:27-36`)는 바꾸지 않는다. | 필수 | 기존 `ArchivePathObservableTest` 등 단위 시험 통과. AVD(V4): #69의 지운 압축 시나리오와 정상 zip 열기·하위 폴더·뒤로 가기가 수정 전과 같음. |

## 3. 비기능 요구사항
- **사용자에게 보이는 차이**: 항목 읽기가 진행 중일 때 새로 고치면 화면은 곧바로 반응하지만(ANR 없음), 새로 고침 표시(로딩)는 진행 중인 읽기와 이어지는 다시 읽기가 끝날 때까지 이어질 수 있다. 다시 읽는 횟수는 지금과 같다 — 진행 중 읽기는 끝까지 가서 그 결과를 돌려주고(REQ-83-02), 새로 고침이 요청한 다음 읽기가 한 번 다시 읽는다. 지금도 무효화가 읽기 끝을 기다린 뒤 플래그를 세워 다음 읽기가 다시 읽으므로 같다. 새 화면 요소·문구는 없다.
- 지원 API 레벨·권한·루트 여부와 무관하다(백엔드 내부 동시성 변경).
- 시험 가능성: 무효화·닫힘·세대 상태와 잠금 규칙은 안드로이드·libarchive 의존 없이 JVM에서 시험할 수 있는 작은 내부 클래스로 둔다(PM 결정). 항목 읽기는 그 클래스에 함수로 넘겨 시험에서 가짜로 바꾼다.

## 4. 제약과 가정
- **C-1**: `ArchiveFileSystem`은 상위(upstream) 코드다. 바꾸는 범위는 무효화·항목 캐시 상태와 그 잠금으로 한정한다(PM 결정: 운영 코드 1 + 시험 1 파일 규모. 추출 클래스가 새 파일이면 운영 2 파일 — HLD에서 정한다).
- **A-1**: 무효화와 `close()`가 동시에 일어나면 어느 쪽이 먼저인지는 정하지 않는다. `close()`가 끝난 뒤에 시작한 무효화는 반드시 `ClosedFileSystemException`(REQ-83-03)이고, 겹친 무효화가 예외 없이 끝나도 닫힌 파일 시스템의 조회는 `ClosedFileSystemException`이라 해가 없다. 무효화가 잠금 없이 닫힘 여부를 읽으므로, `close()`가 끝난 뒤의 무효화가 닫힘을 반드시 보도록 닫힘 상태와 세대 값은 스레드 사이 가시성이 보장돼야 한다(방법은 HLD).
- **V(AVD 확인)**: 로컬 AVD에서만 한다. 실기기(R54N102PRWV)에는 설치하지 않는다. 재현 때와 같은 큰 압축(127 MB, 5만 항목)으로:
  - V1 메뉴 새로 고침 2회 연속, V2 열자마자 당겨서 새로 고침, V3 감시가 시작한 다시 읽기 중 새로 고침 — 각각 ANR 없음(logcat `ANR in`·`Skipped frames` 큰 값 없음), 읽기가 끝난 뒤 목록이 정상 표시.
  - V4 #69 회귀: 압축 목록을 보는 중 원본 삭제 → 오류 화면, 정상 zip 열기·탐색.

## 5. 규격 근거 (부록 S)
해당 없음(외부 규격 없음). 동작 근거는 1.1의 코드 인용과 재현 트레이스다.

## 6. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | v1 | 최초 작성 | 이슈 #83 요구사항 정리 v1, PM 결정 A안 | PM |
| 2026-11-20 | v1.1 | REQ-83-02에 진행 중 읽기의 결과 처리 명시, REQ-83-01·02 fail-before 방식(임시 구현) 명시, A-1에 가시성 요구 | SRS 1회차 검수 m1·m2·m3 (PR #87) | 검수팀 |
| 2026-11-20 | v1.1.1 | 머리표 상태에 구현됨(PR #88) 표시. 내용 변경 없음 | 릴리스 v1.7.5-beta.2 문서 동기화 | 설계팀 |
