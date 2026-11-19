# HLD — 압축 파일 새로 고침 중 주 스레드 멈춤(ANR) (이슈 #83)

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 |
| 근거 | SRS v1.1 |
| 기준 코드 | `origin/develop` ac012e0. 줄 번호는 모두 이 커밋 기준이다 |

## 1. 구조 개요
바뀌는 곳은 백엔드 `provider/archive/` 안, `ArchiveFileSystem`의 항목 캐시 상태뿐이다. 프런트엔드(`filelist/FileListViewModel.kt:81-87`의 `archiveRefresh()` 호출, `FileListLiveData`·`FileListLoader`의 다시 읽기)와 #69 감시(`ArchivePathObservable`)는 바꾸지 않는다 — 호출하는 쪽은 그대로 두고, 불리는 `refresh()`가 잠금을 기다리지 않게 한다.

```mermaid
flowchart LR
  UI["주 스레드<br/>FileListViewModel.reload() (변경 없음)"] -->|archiveRefresh → refresh()| FS["H2 ArchiveFileSystem<br/>refresh(): 잠금 없이 H1.invalidate()"]
  OBS["#69 ArchivePathObservable<br/>(작업 스레드, 변경 없음)"] -->|refresh()| FS
  LOAD["작업 스레드 조회<br/>getDirectoryChildren 등"] -->|synchronized(lock)| FS
  FS --> H1["H1 ArchiveEntriesCache<br/>닫힘·요청 세대·읽은 세대·항목"]
```

지금은 `refresh()`가 항목 읽기와 같은 `lock`을 잡는다(`ArchiveFileSystem.kt:122-129`, 읽기는 `:131-146`이 그 `lock` 안에서 한다). 이를 "잠금 없이 요청 세대만 올리는 무효화"와 "잠금 안에서 세대를 비교해 다시 읽는 조회"로 나눈다(PM 결정 A안). 무효화·닫힘·세대 규칙은 JVM 시험을 위해 새 내부 클래스 H1로 뺀다(SRS 3장 시험 가능성, C-1).

## 2. 구성요소

| 구성요소 | 위치(패키지·파일) | 책임 | 추적 REQ |
|---|---|---|---|
| H1 항목 캐시 상태 | `provider/archive/ArchiveEntriesCache.kt`(새 파일, 이 저장소 코드 — 이름·서명은 LLD에서 정함) | 제네릭 `<T>`(운영은 항목 지도·트리 쌍). 생성 인자로 `ArchiveFileSystem`의 `lock` 객체를 받는다(잠금은 하나로 유지, D2). 상태: 닫힘 여부(`@Volatile`), 요청 세대(`AtomicLong`), 읽은 세대·값(잠금 안에서만 읽고 씀). 연산 — **무효화**: 잠금 없이, 닫혔으면 `ClosedFileSystemException`, 아니면 요청 세대 +1. **잠금 안 조회**(호출자가 `lock`을 쥔 상태): 닫혔으면 `ClosedFileSystemException`; 요청 세대를 먼저 읽어 두고, 값이 없거나 읽은 세대가 그와 다르면 넘겨받은 읽기 함수를 불러 값과 읽은 세대(먼저 읽어 둔 값)를 바꾼다; 읽기 함수가 던지면 상태를 바꾸지 않고 그대로 던진다; 값을 돌려준다. **잠금 안 닫기**: 이미 닫혔으면 아무것도 안 함(그 사실을 돌려줌), 아니면 닫힘으로 바꾸고 값을 버린다. 안드로이드·libarchive 의존이 없다. | REQ-83-01, 02, 03, 04 |
| H2 `ArchiveFileSystem` 연결 | `provider/archive/ArchiveFileSystem.kt`(기존·상위 코드) | 필드 `isOpen`·`isRefreshNeeded`·`entries`·`tree`(`:52-60`, `passwords`는 제외)를 H1 하나로 바꾼다. `refresh()`(`:122-129`)는 `synchronized(lock)` 없이 H1 무효화만 한다. `ensureEntriesLocked`(`:131-146`)는 H1 잠금 안 조회에 "`ArchiveReader.readEntries(archiveFile, passwords, rootDirectory)` + `ArchiveException` → `toFileSystemOrInterruptedIOException(file)` 변환"을 읽기 함수로 넘긴다. 조회 함수(`:62-111`)의 `synchronized(lock)`, `addPassword`(`:113-120`, 닫힘 검사는 H1로), `close()`(`:150-161`, H1 잠금 안 닫기가 "열려 있었음"이면 `provider.removeFileSystem(this)`), `isOpen()`(`:163`)는 잠금 구조를 그대로 둔다. 필드가 H1로 옮겨지므로 `ensureEntriesLocked` 뒤 `entries!!`·`tree!!`를 읽던 곳(`getEntryLocked` `:70-73`, `getDirectoryChildren` `:99`)은 H1 잠금 안 조회가 돌려준 값을 쓴다(전달 방식은 LLD에서 확정). | REQ-83-01~05 |
| H3 #69 설계 문서 갱신 | `docs/design/69-stale-archive/HLD.md` 3.3·D6(문서) | "`refresh()`가 `lock`을 잡는다", "작업 스레드의 `refresh()`는 읽기가 끝날 때까지 기다린다", "명시 새로 고침의 주 스레드 `archiveRefresh()`는 #83 범위라 그대로 둔다" 서술과 R6·6.1 코드 검사 줄을 이 HLD에 맞춰 "#83 뒤" 주석으로 고친다. #69 코드(`ArchivePathObservable`의 실행기 위임)는 그대로다. | REQ-83-05 |

## 3. 데이터 흐름·상태

### 3.1 세대 규칙
- 초기: 요청 세대 0, 값 없음 → 첫 조회가 읽는다(지금의 `isRefreshNeeded = true` 초기값 `:56`과 같음, REQ-83-04).
- 조회는 **읽기 전에** 요청 세대 `g`를 읽고, 읽기가 성공하면 읽은 세대를 `g`로 둔다. 읽는 동안 무효화가 오면 요청 세대는 `g+1` 이상이 되어 다음 조회에서 다시 읽는다(REQ-83-02). 지금 코드의 "읽기 끝에 `isRefreshNeeded = false`"(`:144`)가 그 사이의 무효화를 지울 수 있다는 #69 D6의 우려는, 지우는 대신 "읽기 시작 때의 세대"만 기록하므로 생기지 않는다.
- 진행 중 읽기를 시작한 조회는 그 결과를 돌려준다(같은 호출 안에서 다시 읽지 않음, SRS REQ-83-02). 끝없는 무효화가 와도 한 조회가 끝없이 읽지 않는다.
- 읽기가 시작되기 전에 온 무효화는 그 읽기에 포함된다(그 읽기가 원본을 새로 읽으므로 다시 읽을 필요 없음).
- 읽기 실패: 값·읽은 세대를 바꾸지 않으므로 다음 조회가 다시 시도한다(지금 `isRefreshNeeded`가 참으로 남는 것과 같음, REQ-83-04). 이전에 읽은 값이 있어도 세대가 다르면 다시 시도한다.

### 3.2 스레드
| 호출 | 스레드(근거) | 잠금 |
|---|---|---|
| 명시 새로 고침 `refresh()` | 주 스레드(`FileListViewModel.kt:81-87`) | 없음(이번 변경) |
| 감시 무효화 `refresh()` | 풀 작업 스레드(#69 D6, `ArchivePathObservable.kt:27-36`) | 없음(이번 변경) |
| 조회·항목 읽기 | 풀 작업 스레드(`FileListLiveData.kt:25-28`) | `lock`(지금과 같음) |
| `addPassword()` | 주 스레드(`ArchivePasswordDialogFragment.kt:108`) | `lock`(지금과 같음, #86 범위) |

- 명시 새로 고침 뒤 `_fileListLiveData.reload()`(`FileListViewModel.kt:86`)가 시작한 다시 읽기는 풀 스레드에서 `lock`을 기다리다가, 앞선 읽기가 끝나면 세대가 달라 다시 읽는다. 주 스레드는 기다리지 않고 로딩 표시가 그동안 이어진다(SRS 3장 보이는 차이).
- 무효화는 여러 스레드(주·풀)에서 동시에 올 수 있어 요청 세대는 원자적으로 올린다(`AtomicLong`). 값·읽은 세대는 `lock` 안에서만 다뤄 가시성이 `synchronized`로 보장된다.
- 닫힘: `close()`가 `lock` 안에서 `@Volatile` 닫힘 값을 쓰고, 무효화는 그 값을 잠금 없이 읽는다. `close()`가 끝난 뒤 시작한 무효화는 휘발성 쓰기→읽기 순서로 반드시 닫힘을 보고 `ClosedFileSystemException`을 던진다(REQ-83-03, SRS A-1). 겹친 무효화가 세대만 올리고 끝나도 닫힌 뒤 조회는 닫힘 검사에서 던진다.

### 3.3 오류 전파
바뀌지 않는다. 읽기 실패는 조회 호출자에게 지금과 같은 `IOException`으로(`:137-141`), 닫힘은 `ClosedFileSystemException`으로 간다. #69의 `ArchivePathObservable`은 무효화 예외를 지금처럼 잡아 기록한다(`:29-33`).

## 4. 외부 인터페이스
없음. `ArchiveFileSystem`의 공개 함수 서명(`refresh()`, `close()`, `isOpen()`, 조회 함수)과 `Path.archiveRefresh()`(`PathArchiveExtensions.kt:22-25`)는 그대로다. 설정·Intent·파일 형식 변경 없음.

## 5. 설계 결정

| 결정 | 대안 | 고른 이유 |
|---|---|---|
| D1 세대 값(요청·읽은) 비교 | `isRefreshNeeded`를 `@Volatile`로 두고 잠금 없이 세우기(#69 D6이 거부한 안) / 주 스레드 호출을 작업 스레드로 넘기기 | 휘발성 플래그는 읽기 끝의 `false` 쓰기(`:144`)가 읽는 중의 무효화를 지운다(REQ-83-02 위반). 작업 스레드로 넘기는 안은 호출하는 쪽(`filelist/`)을 바꾸고, 넘긴 작업과 `_fileListLiveData.reload()`의 순서를 따로 맞춰야 한다. 세대 비교는 무효화를 지우지 않고 호출하는 쪽을 바꾸지 않는다(PM 결정 A안). |
| D2 H1은 `ArchiveFileSystem`의 `lock`을 받아 쓴다 | H1이 자기 잠금을 가짐 | `passwords`·`newInputStream`(`:76-89`)도 같은 `lock` 아래에서 항목과 함께 다뤄진다. 잠금을 둘로 나누면 순서 규칙이 새로 생긴다. 하나로 두면 조회 함수는 지금 구조 그대로다. |
| D3 H1을 별도 내부 클래스로 | `ArchiveFileSystem` 안에서 바로 고침 | `ArchiveFileSystem`은 `ArchiveReader`(libarchive)를 직접 부르고(`:138`) 생성에 provider 객체가 필요해 JVM에서 시험하기 어렵다. 읽기 함수를 받는 작은 클래스로 빼면 가짜 읽기·래치로 REQ-83-01~04를 시험할 수 있다(PM 결정). |
| D4 진행 중 읽기는 취소하지 않는다 | 무효화 때 진행 중 읽기를 끊음 | libarchive 읽기의 취소 수단이 없고 범위 밖이다(SRS 1.3). |

## 6. 위험과 미결 사항
| ID | 위험 | 대응 |
|---|---|---|
| R1 | 세대 비교 순서를 잘못 짜면(읽은 뒤 요청 세대를 읽음) 읽는 중의 무효화를 잃는다. | 3.1 규칙("읽기 전에 읽어 둔 세대로 기록")을 LLD에 고정하고, JVM 시험(6.1 두 번째)으로 확인한다. |
| R2 | `close()`와 무효화가 겹칠 때 무효화가 예외 없이 끝날 수 있다. | SRS A-1대로 허용. 닫힌 뒤 조회는 반드시 던진다. |
| R3 | 같은 압축을 연속 새로 고치면 읽기가 끝날 때까지 로딩이 길어진다(보이는 차이). | SRS 3장에서 허용. 다시 읽는 횟수는 지금과 같다. |

### 6.1 검증
- JVM 단위 시험(`app/src/test/java/me/zhanghai/android/files/provider/archive/`, H1 대상, 실제 스레드 + 래치):
  1. 읽기 함수를 래치로 막은 채 다른 스레드가 `lock`을 쥐고 조회 중 → 무효화가 1초 안에 돌아옴(REQ-83-01).
  2. 1의 상태에서 무효화 → 래치 해제 → 첫 조회는 막혔던 읽기의 값을 받음 → 다음 조회에서 읽기 함수가 한 번 더 불림. 무효화 없이 두 번 조회하면 읽기 한 번. 진행 중 읽기 없이(첫 조회 끝난 뒤) 무효화 → 다음 조회에서 읽기 함수가 한 번 더 불림(REQ-83-02).
  3. 닫은 뒤 무효화·조회가 `ClosedFileSystemException`, 닫기 두 번은 두 번째가 "이미 닫힘"(REQ-83-03).
  4. 읽기 함수가 한 번 던짐 → 조회가 그 예외를 던짐 → 다음 조회에서 다시 읽어 성공(REQ-83-04).
  - fail-before/pass-after(SRS REQ-83-01·02): H1과 같은 모양에 지금의 잠금 구조(무효화도 `synchronized(lock)`, 읽기 끝에 플래그 지움, `:122-146`)를 흉내 낸 임시 구현을 먼저 커밋해 1·2가 시간 초과로 실패하고 3·4는 통과함을 보인 뒤 실제 구현으로 바꿔 모두 통과시킨다(#69와 같은 방식). 시험은 시간 초과(예: 1초 기다림 + 끝에 래치 해제)로 끝나야 하며 멈춰 있으면 안 된다.
  - 기존 `ArchivePathObservableTest`와 다른 단위 시험 통과(REQ-83-05).
- 코드 검사(LLD·코드 검수): `refresh()`·H1 무효화에 `synchronized`가 없음, 조회 함수의 잠금 구조가 지금과 같음, `ArchiveFileSystem` 밖(`filelist/`, `ArchivePathObservable`) 변경 없음.
- AVD(로컬 AVD만, 실기기 R54N102PRWV 금지): SRS V1~V4. 재현 때와 같은 큰 압축으로 ANR 없음과 #69 회귀 없음을 확인한다.

## 7. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | v1 | 최초 작성. #69 HLD 3.3·D6 서술 갱신(H3) | SRS v1.1 검수 통과 | PM |
| 2026-11-20 | v1.1 | H2에 `entries!!`·`tree!!` 읽던 곳이 H1 값을 쓴다고 명시, 6.1 시험 2에 "진행 중 읽기 없는 무효화" 경우 추가, H3에 #69 HLD R6·6.1 코드 검사 주석 포함 | HLD 1회차 검수 m1·m2·m3 (PR #87) | 검수팀 |
