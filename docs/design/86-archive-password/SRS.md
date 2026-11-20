# SRS — 압축 비밀번호 입력 중 주 스레드 멈춤(ANR) (이슈 #86)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1.1, PR #90 1회차 조건부 통과 반영), 구현됨(PR #91, v1.7.5-beta.2) |
| 근거 | 요구사항 정리 v1 (이슈 #86), 재현 결과(같은 이슈, PM 요약), PM 결정 |
| 기준 코드 | `origin/develop` 0096406. 줄 번호는 모두 이 커밋 기준이다 |
| 증거 | `_workspace/wo/86-archive-password/`(ANR 트레이스 `anr_B2_trace.txt`·`anr_B3_trace.txt`, logcat `avd_B2.logcat.txt`·`avd_B3.logcat.txt`, 암호 zip 생성기 `make_enc_zip.py`, 시나리오 `scenB.sh`, `progress.md`) |
| 관련 설계 | `docs/design/83-archive-anr/`(HLD 3.2 스레드 표), `docs/design/69-stale-archive/`(HLD 3.3) |

## 1. 목적과 범위

### 1.1 목적
압축 비밀번호 창에서 OK를 누르면 주 스레드가 `ArchivePasswordDialogFragment.onOk()`(`fileaction/ArchivePasswordDialogFragment.kt:108`) → `Path.archiveAddPassword()`(`provider/archive/PathArchiveExtensions.kt:11-14`) → `ArchiveFileSystem.addPassword()`(`provider/archive/ArchiveFileSystem.kt:111-118`)를 부른다. `addPassword()`는 목록에 문자열 하나를 붙이려고 `synchronized(lock)`을 잡는데, 이 `lock`은 작업 스레드가 압축 전체 항목을 읽는 동안(`ensureEntriesLocked`, `:125-134`, 조회 함수가 `synchronized(lock)` 안에서 부른다 — `:58-62`, `:90-98` 등) 계속 잡혀 있다.

AVD 재현(암호 zip `deep.zip` 20만 항목, 시나리오 B: 창이 떠 있는 동안 원본이 바뀌어 #69 감시가 목록을 다시 읽음)에서 ANR 2/2 — 주 스레드는 `addPassword(ArchiveFileSystem.kt:112)`에서 `waiting to lock`, 쥔 쪽은 `getDirectoryChildren(:92)` → `ensureEntriesLocked` → `readEntries`(`anr_B2_trace.txt:78-81`), 대기 최대 7.8초. #83(PR #88)은 `refresh()`만 고쳤다.

### 1.2 범위
- `ArchiveFileSystem.addPassword()`가 진행 중인 항목 읽기를 기다리지 않게 한다(PM 결정: 비밀번호 목록을 큰 잠금 밖에서 원자적으로 갱신하고, 닫힘 확인은 이미 휘발성인 `entriesCache.isOpen`(`provider/archive/ArchiveEntriesCache.kt:19-31`)으로 한다. 읽는 쪽은 지금처럼 잠금 안에서 목록을 한 번 읽는다).
- 그 뒤에도 바뀌지 않아야 할 동작(추가한 비밀번호의 사용, 닫힌 파일 시스템, 비밀번호 입력 뒤 흐름, #69·#83 동작)을 요구사항으로 고정한다.

### 1.3 범위 밖
- 압축 처리 전반의 재설계(요구사항 정리 v1). 항목 읽기 자체의 속도·취소.
- **`ArchiveFileSystem.isOpen()`(`:148`)·`close()`(`:138-146`)의 잠금 방식** — PM 지시로 호출 위치를 추적한 결과, 이 둘을 `ArchiveFileSystem`에 대해 부르는 곳이 없다(주 스레드든 다른 스레드든). 근거:
  - 앱 소스(`app/src/main`, `app/src/test`, Kotlin·Java) 전체에서 `isOpen`을 쓰는 곳 중 `ArchiveFileSystem`에 닿는 것은 자기 정의(`ArchiveFileSystem.kt:113`, `:140`, `:148`)뿐이다. 나머지는 채널(`AbstractFileByteChannel.kt:176` 등)이나 다른 파일 시스템(`RootableFileSystem.kt:29`, `:37` — `LinuxFileSystem.kt:16`의 로컬·루트 파일 시스템만 감싼다)이다.
  - `FileSystem.close()`를 부르는 곳은 `RootableFileSystem.kt:30`, `:33`과 `RemoteFileSystemInterface.kt:12`뿐이다. 앞의 둘은 리눅스 파일 시스템용이고, 뒤의 것은 `RootFileSystem`(`RootFileSystem.kt:19-21`, 생성은 `LinuxFileSystem.kt:16`뿐)이 루트 서비스에 넘긴 파일 시스템을 닫는다 — 압축 파일 시스템은 넘어가지 않는다.
  - `ArchiveFileSystemProvider`(`provider/archive/ArchiveFileSystemProvider.kt`)는 `removeFileSystem`(`:72-74`)만 있고 `close()`·`isOpen()`을 부르지 않는다. `FileSystemCache`(`provider/common/FileSystemCache.kt:13-62`)도 부르지 않는다.
  - NIO 백포트 라이브러리(`me.zhanghai.android.retrofile:library:1.2.0`, `java8.nio.file`)의 바이트코드 전체에서 `java8/nio/file/FileSystem.isOpen`·`FileSystem.close` 호출이 없다(`javap -c`로 확인; `FileTreeWalker`의 `isOpen`은 자기 함수).
  - 따라서 주 스레드가 이 둘로 `lock`을 기다리는 경로는 지금 없으므로 이 이슈에서 바꾸지 않는다. 앞으로 부르는 곳이 생기면 별도 이슈로 다룬다.
- 조회 함수(`getEntry`·`newInputStream`·`getDirectoryChildren`·`readSymbolicLink`)의 잠금 방식. 모두 작업 스레드에서 불린다(#83 HLD 3.2).

## 2. 기능 요구사항

용어: **비밀번호 추가** = `ArchiveFileSystem.addPassword()` 호출. **항목 읽기** = `ensureEntriesLocked`(`:125-134`)가 `ArchiveReader.readEntries(archiveFile, passwords, rootDirectory)`(`:130`)로 전체 항목을 읽는 일. **데이터 열기** = `newInputStream`(`:74-87`)이 `ArchiveReader.newInputStream(archiveFile, passwords, entry)`(`:82`)를 부르는 일. 둘 다 `lock` 안에서 `passwords`를 읽는다.

| ID | 요구사항 | 우선순위 | 검증 방법 |
|---|---|---|---|
| REQ-86-01 | 비밀번호 추가는 다른 스레드가 항목 읽기로 `lock`을 쥐고 있어도 그 읽기를 기다리지 않고 돌아와야 한다. 그래서 비밀번호 창 OK의 주 스레드 호출(`ArchivePasswordDialogFragment.kt:108`)이 멈추지 않는다. | 필수 | JVM 단위 시험: 다른 스레드가 `lock`을 쥔 채 래치로 막혀 있는 동안 비밀번호 추가 → 1초 안에 돌아옴. fail-before는 #83과 같은 방식 — 추출 클래스와 같은 모양에 지금의 잠금 구조(추가가 같은 `lock`을 잡음, `:111-118`)를 흉내 낸 임시 구현을 먼저 커밋해 시험이 시간 초과로 실패함을 보이고, 실제 구현으로 바꿔 통과시킨다. 시험은 멈추지 않고 시간 초과로 끝나야 한다. 코드 검사: 추가 경로에 `lock`이 없음. AVD(V1). |
| REQ-86-02 | 비밀번호 추가가 돌아온 뒤 시작하는 항목 읽기·데이터 열기는 추가한 비밀번호를 포함한 목록을 써야 한다. 목록은 추가한 순서대로 끝에 붙고, 같은 값의 중복도 지금처럼 허용한다(`passwords += password`, `:116`). 추가 때 이미 진행 중이던 읽기는 시작할 때 읽은 목록을 쓴다 — 지금도 추가가 그 읽기 끝을 기다리므로 그 읽기는 추가 전 목록을 쓴다. 읽는 쪽은 한 번의 읽기·열기 안에서 목록을 한 번만 읽는다(PM 결정). | 필수 | JVM 단위 시험: 추가 뒤 다음 읽기(시험에서는 `lock` 안에서 목록을 읽는 가짜 읽기)가 추가한 값을 순서대로 봄(수정 전후 모두 통과하는 회귀 방지 시험). |
| REQ-86-03 | 닫힌 파일 시스템(`close()` 뒤)에서 비밀번호 추가는 지금처럼 `ClosedFileSystemException`을 던지고 목록을 바꾸지 않아야 한다(`:113-115`). | 필수 | JVM 단위 시험: 닫은 뒤 추가 → `ClosedFileSystemException`(회귀 방지). |
| REQ-86-04 | 여러 스레드가 동시에 비밀번호를 추가해도 추가한 값이 하나도 빠지지 않아야 한다(잠금 없이 갱신하므로 갱신 손실이 없어야 한다). | 필수 | JVM 단위 시험: N개 스레드가 동시에 서로 다른 값을 추가 → 목록에 N개가 모두 있음. |
| REQ-86-05 | 비밀번호 입력 뒤의 동작은 그대로여야 한다: OK 뒤 원래 작업을 다시 시도하고(`ArchivePasswordRequiredException.kt:25-31`의 continuation), 틀린 비밀번호면 libarchive의 "Incorrect passphrase"가 다시 `ArchivePasswordRequiredException`이 되어(`ArchiveExceptionExtensions.kt:24-27`) 비밀번호 창이 다시 뜬다. #69 동작(원본 변경 → 다시 읽기, 지운 압축 → 오류 화면)과 #83 동작(새로 고침이 읽기를 기다리지 않음)도 그대로다. | 필수 | 기존 `ArchiveEntriesCacheTest`·`ArchivePathObservableTest` 등 단위 시험 통과. AVD(V1·V2). |

## 3. 비기능 요구사항
- **사용자에게 보이는 차이**: 없다(PM 결정). 항목 읽기 중에 OK를 누르면 창은 곧바로 닫히고, 다시 시도한 작업은 작업 스레드에서 진행 중인 읽기가 끝날 때까지 기다린다(지금은 그동안 주 스레드가 멈춘다). 새 화면 요소·문구는 없다.
- 지원 API 레벨·권한·루트 여부와 무관하다(백엔드 내부 동시성 변경).
- 시험 가능성: 비밀번호 목록과 그 추가 규칙은 안드로이드·libarchive 의존 없이 JVM에서 시험할 수 있는 작은 내부 클래스로 둔다(PM 결정).

## 4. 제약과 가정
- **C-1**: `ArchiveFileSystem`은 상위(upstream) 코드다. 바꾸는 범위는 `passwords` 필드와 `addPassword()`, `passwords`를 읽는 두 곳(`:82`, `:130`), 그리고 3장의 시험 가능한 새 내부 클래스(이 저장소 코드, 운영 파일 1개 — 이름은 HLD·LLD에서 정함)로 한정한다.
- **A-1**: 비밀번호 추가와 `close()`가 동시에 일어나면 어느 쪽이 먼저인지는 정하지 않는다. `close()`가 끝난 뒤 시작한 추가는 반드시 `ClosedFileSystemException`(REQ-86-03)이다 — 닫힘 값이 휘발성이므로 보장된다(`ArchiveEntriesCache.kt:19-20`, #83 SRS A-1과 같음). 겹친 추가가 예외 없이 끝나도 닫힌 파일 시스템의 조회는 `ClosedFileSystemException`이라 해가 없다(`ArchiveEntriesCache.kt:42-44`).
- **A-2**: 지금 `ArchiveFileSystem.close()`를 부르는 곳이 없으므로(1.3) A-1의 경합은 실제로 일어나지 않는다. 그래도 계약으로 고정한다.
- **V(AVD 확인)**: 로컬 AVD에서만 한다. 실기기(R54N102PRWV)에는 설치하지 않는다.
  - V1 재현 시나리오 B 재실행: `deep.zip`(20만 항목, 깊은 경로)에서 비밀번호 창을 띄우고, 원본을 바꿔 목록 다시 읽기를 일으킨 뒤 OK → ANR 없음(logcat `ANR in` 없음, 입력 응답 지연 프로브가 수 초대가 아님), 읽기가 끝난 뒤 파일이 맞는 비밀번호로 열림.
  - V2 틀린 비밀번호 → 비밀번호 창이 다시 뜸 → 맞는 비밀번호 → 열림. 정상(암호 없는) zip 열기·탐색이 수정 전과 같음.

## 5. 규격 근거 (부록 S)
해당 없음(외부 규격 없음). 동작 근거는 1.1의 코드 인용과 재현 트레이스다.

## 6. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | v1 | 최초 작성 | 이슈 #86 요구사항 정리 v1, PM 결정 | PM |
| 2026-11-20 | v1.1 | C-1에 새 내부 클래스 포함, 1.1 축약 | SRS 1회차 검수 경미 2건 (PR #90) | 검수팀 |
| 2026-11-20 | v1.1.1 | 머리표 상태에 구현됨(PR #91) 표시. 내용 변경 없음 | 릴리스 v1.7.5-beta.2 문서 동기화 | 설계팀 |
