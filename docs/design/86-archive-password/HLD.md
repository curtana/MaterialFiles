# HLD — 압축 비밀번호 입력 중 주 스레드 멈춤(ANR) (이슈 #86)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1, PR #90 1회차) |
| 근거 | SRS v1.1 |
| 기준 코드 | `origin/develop` 0096406. 줄 번호는 모두 이 커밋 기준이다 |

## 1. 구조 개요
바뀌는 곳은 백엔드 `provider/archive/` 안, `ArchiveFileSystem`의 비밀번호 목록뿐이다. 프런트엔드(`fileaction/ArchivePasswordDialogFragment.kt:108`의 `archiveAddPassword()` 호출)와 `PathArchiveExtensions.kt:11-14`는 바꾸지 않는다 — 부르는 쪽은 그대로 두고, 불리는 `addPassword()`가 `lock`을 잡지 않게 한다. #83의 `ArchiveEntriesCache`(`provider/archive/ArchiveEntriesCache.kt`)는 그대로 쓰고, 닫힘 확인에 그 휘발성 `isOpen`(`:19-31`)을 빌린다.

```mermaid
flowchart LR
  UI["주 스레드<br/>ArchivePasswordDialogFragment.onOk() (변경 없음)"] -->|archiveAddPassword → addPassword()| FS["H2 ArchiveFileSystem<br/>addPassword(): 잠금 없이 H1.add()"]
  LOAD["작업 스레드 조회<br/>ensureEntriesLocked · newInputStream"] -->|synchronized(lock) 안에서 H1 한 번 읽기| FS
  FS --> H1["H1 비밀번호 목록<br/>불변 목록의 원자적 참조"]
  H1 -.닫힘 확인.-> C["ArchiveEntriesCache.isOpen<br/>(#83, 휘발성, 변경 없음)"]
```

지금은 `addPassword()`가 항목 읽기와 같은 `lock`을 잡는다(`ArchiveFileSystem.kt:111-118`; 읽기는 `ensureEntriesLocked` `:125-134`가 그 `lock` 안에서 한다). 이를 "잠금 없이 불변 목록 참조를 원자적으로 바꾸는 추가"와 "잠금 안에서 참조를 한 번 읽어 쓰는 읽기"로 나눈다(PM 결정). 목록 규칙은 JVM 시험을 위해 새 내부 클래스 H1로 뺀다(SRS 3장, C-1).

## 2. 구성요소

| 구성요소 | 위치(패키지·파일) | 책임 | 추적 REQ |
|---|---|---|---|
| H1 비밀번호 목록 | `provider/archive/ArchivePasswordList.kt`(새 파일, 이 저장소 코드 — 이름·서명은 LLD에서 정함) | 상태: 불변 `List<String>`을 가리키는 원자적 참조 하나(초기 빈 목록). 생성 인자: `ArchiveFileSystem`의 `lock` 객체(읽기 규칙 확인용, D3)와 닫힘 여부를 알려 주는 함수(운영은 `{ entriesCache.isOpen }`). 연산 — **추가**: 잠금 없이, 닫혔으면 `ClosedFileSystemException`(목록 그대로), 아니면 "현재 목록 + 새 값"으로 비교-교환(CAS)을 성공할 때까지 되풀이한다(D2). **잠금 안 읽기**: 호출자가 `lock`을 쥔 상태(`check(Thread.holdsLock(lock))`, #83 `getLocked`와 같은 방식 `ArchiveEntriesCache.kt:41`)에서 현재 목록을 돌려준다. 안드로이드·libarchive 의존이 없다. | REQ-86-01, 02, 03, 04 |
| H2 `ArchiveFileSystem` 연결 | `provider/archive/ArchiveFileSystem.kt`(기존·상위 코드) | 필드 `passwords`(`:55`)를 H1 하나로 바꾼다(`lock`·`entriesCache` 선언 뒤, 초기화 순서). `addPassword()`(`:111-118`)는 `synchronized(lock)` 없이 H1 추가만 한다. `passwords`를 읽던 두 곳 — `newInputStream`의 `ArchiveReader.newInputStream(archiveFile, passwords, entry)`(`:82`)와 `ensureEntriesLocked` 읽기 함수의 `ArchiveReader.readEntries(archiveFile, passwords, rootDirectory)`(`:130`) — 은 H1 잠금 안 읽기를 **한 번** 불러 그 목록을 넘긴다. 둘 다 이미 `synchronized(lock)` 안이다(`:82`는 `:75`의 잠금 안, `:130`은 `ensureEntriesLocked`가 조회 함수의 잠금 `:59`·`:75`·`:91`·`:102` 안에서 불림). 조회 함수·`refresh()`·`close()`(`:138-146`)·`isOpen()`(`:148`)의 잠금 구조는 그대로다(SRS 1.3). | REQ-86-01~05 |
| H3 관련 설계 문서 갱신 | `docs/design/83-archive-anr/HLD.md` 3.2 스레드 표, `docs/design/69-stale-archive/HLD.md` 3.3(문서) | #83 HLD 3.2 표의 `addPassword()` 행 "잠금: `lock`(지금과 같음, #86 범위)"을 "없음(#86 뒤)"으로 고치고, 같은 절에 "`isOpen()`·`close()`는 앱에서 부르는 곳이 없음(#86 SRS 1.3)"을 덧붙인다. #69 HLD 3.3 "주 스레드의 `addPassword()`는 #86 범위다"(`:50`)에 "**#86 뒤**: `addPassword()`도 `lock`을 잡지 않는다" 주석을 단다. 코드 변경은 없다. 이 설계 PR에서 함께 고친다("#86 뒤" 표기는 #83이 #69 문서에 단 방식과 같다). | REQ-86-05 |

## 3. 데이터 흐름·상태

### 3.1 목록 규칙
- 초기: 빈 목록(지금 `listOf<String>()` `:55`와 같음).
- 추가는 현재 목록 참조 `l`을 읽어 `l + 값`(새 불변 목록)으로 CAS한다. 다른 추가가 먼저 바꿔 CAS가 실패하면 새 현재 목록으로 다시 만든다 — 동시 추가가 서로를 지우지 않는다(REQ-86-04). 값은 끝에 붙고 중복을 거르지 않는다(REQ-86-02, 지금 `passwords += password` `:116`과 같음).
- 읽기는 참조를 한 번 읽은 목록을 쓴다. 목록은 불변이라 읽은 뒤 다른 스레드의 추가가 그 목록을 바꾸지 않는다. 항목 읽기·데이터 열기 한 번에 한 번만 읽는다(PM 결정).
- 추가가 돌아온 뒤 시작한 읽기는 새 목록을 본다 — 원자적 참조 쓰기 → 읽기의 happens-before(REQ-86-02). 추가 때 진행 중이던 읽기는 이미 읽은 이전 목록으로 끝난다(지금도 추가가 그 읽기 끝을 기다리므로 같은 결과).

### 3.2 스레드
| 호출 | 스레드(근거) | 잠금 |
|---|---|---|
| `addPassword()` | 주 스레드(`ArchivePasswordDialogFragment.kt:108`, 트레이스 `anr_B2_trace.txt:78-81`) | 없음(이번 변경) |
| 항목 읽기·데이터 열기(H1 읽기) | 풀 작업 스레드(`FileListLiveData.kt:25-28` 등, #83 HLD 3.2) | `lock`(지금과 같음) |
| `refresh()` | 주·풀(#83 HLD 3.2) | 없음(#83, 변경 없음) |
| `close()`·`isOpen()` | 부르는 곳 없음(SRS 1.3) | `lock`(변경 없음) |

- OK 뒤 다시 시도된 작업(`ArchivePasswordRequiredException.kt:25-31`의 continuation)은 작업 스레드에서 `lock`을 기다리다가, 진행 중 읽기가 끝나면 새 목록으로 읽는다. 주 스레드는 기다리지 않는다(SRS 3장).
- 닫힘: `close()`가 `lock` 안에서 `ArchiveEntriesCache`의 `@Volatile` 닫힘 값을 쓰고(`ArchiveEntriesCache.kt:57-65`), 추가는 그 값을 잠금 없이 읽는다. `close()`가 끝난 뒤 시작한 추가는 반드시 닫힘을 보고 던진다(REQ-86-03, SRS A-1). 확인과 CAS 사이에 `close()`가 끼면 추가가 예외 없이 끝날 수 있으나, 닫힌 뒤 조회는 `getLocked`가 던지므로 해가 없다(SRS A-1·A-2).

### 3.3 오류 전파
바뀌지 않는다. 닫힘은 `ClosedFileSystemException`(주 스레드 호출자에게 지금처럼 그대로 전파), 틀린 비밀번호는 지금처럼 libarchive 오류 → `ArchivePasswordRequiredException`(`ArchiveExceptionExtensions.kt:24-27`) → 비밀번호 창(REQ-86-05).

## 4. 외부 인터페이스
없음. `ArchiveFileSystem.addPassword(String)`와 `Path.archiveAddPassword()`(`PathArchiveExtensions.kt:11-14`)의 서명, `ArchiveReader.readEntries`·`newInputStream`에 넘기는 `List<String>` 형식은 그대로다. 설정·Intent·파일 형식 변경 없음.

## 5. 설계 결정

| 결정 | 대안 | 고른 이유 |
|---|---|---|
| D1 불변 목록 + 원자적 참조 | 주 스레드 호출을 작업 스레드로 넘기기 / 목록 전용 작은 잠금 | 넘기는 안은 부르는 쪽(`fileaction/`)을 바꾸고, 넘긴 추가와 continuation 재시도의 순서를 따로 맞춰야 한다. 전용 작은 잠금도 짧게만 잡히므로 동작하지만, 읽기는 큰 `lock` 안에서 목록을 읽으므로 잠금 순서(큰 → 작은)를 새로 지켜야 한다. 원자적 참조는 잠금이 없어 순서 규칙이 생기지 않는다(PM 결정 "원자적 갱신"). |
| D2 CAS 되풀이(`compareAndSet` 루프) | `AtomicReference.updateAndGet` | 결과는 같다. `updateAndGet`은 API 24+(minSdk 23, `app/build.gradle:34`)이라 디슈가링(`:60`, `:126`) 적용 여부를 LLD에서 확인해야 하므로, 어느 API에서나 되는 루프를 기본으로 둔다. LLD가 확인하면 바꿔도 된다. |
| D3 H1이 `lock`을 받아 읽기를 잠금 안으로 제한 | 잠금 없는 읽기 허용 | 읽기는 잠금 없이도 안전하지만, "읽는 쪽은 잠금 안에서 한 번 읽음"(PM 결정)을 코드로 고정하고 #83 `getLocked`와 규칙을 맞춘다. 시험의 fail-before 임시 구현(추가도 같은 `lock`을 잡음)도 이 인자로 지금 구조를 흉내 낸다. |
| D4 닫힘 상태는 `ArchiveEntriesCache`에 둔다 | H1이 자기 닫힘 값을 가짐 | 닫힘 값이 둘이면 `close()`가 둘을 다 바꿔야 하고 어긋날 수 있다. 이미 휘발성인 #83의 값을 함수로 빌려 쓴다(PM 결정). |
| D5 `isOpen()`·`close()`는 그대로 | 함께 잠금 밖으로 | 부르는 곳이 없어(SRS 1.3) 고칠 이유가 없고, 상위 코드 변경을 줄인다(C-1). |

## 6. 위험과 미결 사항
| ID | 위험 | 대응 |
|---|---|---|
| R1 | 목록을 가변으로 두거나 참조를 두 번 읽으면 읽기 도중 목록이 바뀌거나 한 읽기 안에서 서로 다른 목록을 쓸 수 있다. | 3.1 규칙(불변 목록, 한 번 읽기)을 LLD에 고정하고 코드 검사로 확인한다. |
| R2 | `close()`와 추가가 겹칠 때 추가가 예외 없이 끝날 수 있다. | SRS A-1대로 허용. 지금 부르는 곳이 없다(A-2). |

### 6.1 검증
- JVM 단위 시험(`app/src/test/java/me/zhanghai/android/files/provider/archive/`, H1 대상, 실제 스레드 + 래치, 모든 기다림은 시간 제한):
  1. 다른 스레드가 `lock`을 쥔 채 래치로 막혀 있는 동안 추가 → 1초 안에 돌아옴. 끝에 래치를 풀어 시험이 멈추지 않게 한다(REQ-86-01).
  2. 추가 두 번 뒤 `lock` 안 읽기가 두 값을 추가 순서대로 봄(REQ-86-02).
  3. 닫힘 함수가 "닫힘"을 돌려주면 추가가 `ClosedFileSystemException`, 읽기 목록은 그대로(REQ-86-03). 운영과 같게 실제 `ArchiveEntriesCache`를 `closeLocked()`로 닫아 시험해도 된다(LLD에서 정함).
  4. N개(예: 8) 스레드가 시작 래치로 동시에 서로 다른 값을 M번씩 추가 → 목록 크기 N×M, 모든 값 포함(REQ-86-04).
  - fail-before/pass-after(SRS REQ-86-01): H1과 같은 모양에 지금의 잠금 구조(추가도 `synchronized(lock)`, `:111-118`)를 흉내 낸 임시 구현을 먼저 커밋해 1이 시간 초과로 실패하고 2~4는 통과함을 보인 뒤 실제 구현으로 바꿔 모두 통과시킨다(#83과 같은 방식).
  - 기존 `ArchiveEntriesCacheTest`·`ArchivePathObservableTest`와 다른 단위 시험 통과(REQ-86-05).
- 코드 검사(LLD·코드 검수): `addPassword()`·H1 추가에 `synchronized`가 없음, `passwords` 읽기가 각 경로에서 한 번이고 `lock` 안임, `ArchiveFileSystem` 밖 코드 변경 없음(H1 새 파일 제외).
- AVD(로컬 AVD만, 실기기 R54N102PRWV 금지): SRS V1(시나리오 B `deep.zip` 재실행 — `_workspace/wo/86-archive-password/scenB.sh`, `make_enc_zip.py`)·V2.

## 7. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | v1 | 최초 작성. #83 HLD 3.2·#69 HLD 3.3 서술 갱신(H3) | SRS v1.1 검수 통과 | PM |
