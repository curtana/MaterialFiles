# LLD — 압축 비밀번호 입력 중 주 스레드 멈춤(ANR) (이슈 #86)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1.1, PR #90 1회차 조건부 통과 반영) |
| 근거 | SRS v1.1, HLD v1 |
| 기준 코드 | `origin/develop` 0096406. 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준, 테스트는 `app/src/test/java/me/zhanghai/android/files/` 기준. 줄 번호는 모두 이 커밋 기준이다 |

## 1. 변경 파일
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `provider/archive/ArchivePasswordList.kt` | 신규 | HLD H1: 불변 목록의 원자적 참조. 잠금 없는 추가(CAS 루프), 잠금 안 읽기. 안드로이드·libarchive 의존 없음 |
| `provider/archive/ArchiveFileSystem.kt` | 수정 | HLD H2: 필드 `passwords`(`:55`)를 H1로, `addPassword()`(`:111-118`)에서 `synchronized(lock)` 제거, `passwords` 읽는 두 곳(`:82`, `:130`)을 `passwords.getLocked()` 한 번으로 |
| `provider/archive/ArchivePasswordListTest.kt`(test) | 신규 | 3장 T-86-01~05 |

그 밖(`fileaction/ArchivePasswordDialogFragment.kt`, `provider/archive/PathArchiveExtensions.kt`, `ArchiveEntriesCache.kt`, `archiver/ArchiveReader.kt`)은 바꾸지 않는다(HLD 1장·6.1 코드 검사). HLD H3(#83 HLD 3.2 v1.2, #69 HLD 3.3 주석)은 설계팀이 이 설계 브랜치에서 이미 반영했다(dd4046c) — 구현에는 문서 변경이 없다. 운영 2 + 테스트 1 파일.

## 2. 함수 명세

### `provider.archive.ArchivePasswordList` (class, 신규, `internal`)
```kotlin
internal class ArchivePasswordList(
    private val lock: Any,
    private val isOpen: () -> Boolean
) {
    private val passwords = AtomicReference(listOf<String>())
    fun add(password: String)
    fun getLocked(): List<String>
}
```
- **추적**: HLD H1, D1~D4 / REQ-86-01~04
- **책임**: 압축 비밀번호 목록과 그 갱신·읽기 규칙(HLD 3.1)을 한곳에 둔다.
- **생성 인자**: `lock` = `ArchiveFileSystem.lock`(`ArchiveFileSystem.kt:50`) 객체 그대로 — 이 클래스는 그 잠금을 잡지 않고 `getLocked()`의 전제 확인에만 쓴다(D3). `isOpen` = 열림 여부를 잠금 없이 알려 주는 함수, 운영은 `{ entriesCache.isOpen }`(휘발성 `ArchiveEntriesCache.isClosed`, `ArchiveEntriesCache.kt:19-31`, D4). 이 클래스는 자기 닫힘 값을 갖지 않는다.
- **상태**: `passwords: AtomicReference<List<String>>`, 초기값 빈 목록(지금 `listOf<String>()` `:55`와 같음). 참조가 가리키는 목록은 공개된 뒤 바뀌지 않는다 — 새 값은 언제나 `current + password`로 만든 새 목록이다(R1).
- **import**: `java8.nio.file.ClosedFileSystemException`, `java.util.concurrent.atomic.AtomicReference`뿐. JVM 시험이 된다(`ArchiveEntriesCacheTest`와 같은 조건).
- 파일 머리 저작권 주석은 같은 패키지 새 파일 관례(`ArchiveEntriesCache.kt:1-4`, `Copyright (c) 2026 Hai Zhang …`)를 따르고, 클래스 KDoc에 "추가는 잠금을 잡지 않아 진행 중인 항목 읽기를 기다리지 않는다, 읽기는 소유 파일 시스템의 잠금 안에서 한 번 한다"를 한두 줄로 적는다(`ArchiveEntriesCache.kt:11-17` 밀도).

### `ArchivePasswordList.add(password: String)` (신규)
- **추적**: HLD H1 추가, D1·D2·D4 / REQ-86-01·02·03·04
- **책임**: 잠금 없이 비밀번호를 목록 끝에 붙인다.
- **입력·전제**: `password` 아무 문자열(빈 문자열·중복 허용 — 지금 `passwords += password` `:116`과 같음, 거르지 않는다). 호출 스레드 무관(운영은 주 스레드, `ArchivePasswordDialogFragment.kt:108`). `lock`을 쥐고 있든 아니든 동작이 같다.
- **출력·사후조건**: 정상 반환 뒤 시작하는 `getLocked()`는 `password`가 끝에 붙은 목록(또는 그 뒤 다른 추가까지 붙은 목록)을 본다 — `AtomicReference`의 휘발성 쓰기→읽기 순서(HLD 3.1). 동시 추가는 서로를 지우지 않는다(REQ-86-04).
- **오류**: 반복 시작 때 `!isOpen()`이면 `ClosedFileSystemException`을 던지고 목록을 바꾸지 않는다(REQ-86-03). 그 밖의 예외 없음. 확인과 CAS 사이에 닫힘이 끼면 예외 없이 붙을 수 있다 — SRS A-1·A-2, HLD R2대로 허용.
- **알고리즘** (CAS 루프, HLD D2):
  ```kotlin
  fun add(password: String) {
      while (true) {
          if (!isOpen()) {
              throw ClosedFileSystemException()
          }
          val current = passwords.get()
          if (passwords.compareAndSet(current, current + password)) {
              return
          }
      }
  }
  ```
  `synchronized`·`Thread.holdsLock`을 쓰지 않는다(코드 검사). 닫힘 확인은 루프 안(매 시도)에 둔다 — 경합 중 닫히면 다음 시도에서 알아챈다. `compareAndSet`은 참조 동일성 비교라 방금 읽은 `current`와 비교하면 된다.
- **`updateAndGet` 확인 결과(검수 메모)**: minSdk 23(`app/build.gradle:34`), `AtomicReference.updateAndGet`은 API 24+. 이 빌드는 `coreLibraryDesugaringEnabled true`(`app/build.gradle:60`)·`desugar_jdk_libs:2.1.5`(`:126`)이고, 그 구성 jar(`desugar_jdk_libs_configuration-2.1.5.jar`)의 `META-INF/desugar/d8/lint/compile_api_level_30/desugared_apis_30_1.txt:452`에 `java/util/concurrent/atomic/AtomicReference#updateAndGet(Ljava/util/function/UnaryOperator;)` 가 있고 라이브러리 jar에 `java/util/concurrent/atomic/DesugarAtomicReference.class`가 있다 — 즉 디슈가링이 덮는다. 그래도 **CAS 루프를 쓴다**: HLD D2의 기본값이고, 디슈가링 경로(`j$` 함수형 인터페이스 변환)에 기대지 않으며, 저장소의 기존 원자 연산도 `compareAndSet`만 쓴다(`filejob/FileJob.kt:119`, `provider/remote/RemoteCopyOutcomeWatch.kt:15`). 결과는 같다.
- **기존 코드와의 관계**: `ArchiveFileSystem.addPassword()`의 `synchronized(lock) { if (!entriesCache.isOpen) throw …; passwords += password }`(`:111-118`)를 대체한다. 닫힘 검사 의미는 같다(같은 휘발성 값).

### `ArchivePasswordList.getLocked(): List<String>` (신규)
- **추적**: HLD H1 잠금 안 읽기, D3 / REQ-86-02
- **책임**: 소유 파일 시스템의 `lock` 안에서 현재 목록을 돌려준다.
- **입력·전제**: 호출자가 `lock`을 쥐고 있다 — `check(Thread.holdsLock(lock))`(#83 `getLocked` `ArchiveEntriesCache.kt:41`과 같은 방식, 어기면 `IllegalStateException`).
- **출력·사후조건**: `passwords.get()` 한 번의 결과(불변 목록) 그대로. 상태를 바꾸지 않는다. 돌려준 목록은 뒤의 추가로 바뀌지 않는다.
- **오류**: 전제 위반만 `IllegalStateException`. 닫힘은 검사하지 않는다 — 두 호출 위치 모두 이미 `entriesCache.getLocked`가 닫힘을 검사한 뒤다(`:76`→`:82`; `:130`은 `getLocked`의 읽기 함수 안, `ArchiveEntriesCache.kt:42-44`). 지금 `passwords` 읽기도 닫힘을 보지 않는다.
- **알고리즘**: `check(Thread.holdsLock(lock)); return passwords.get()`.

### `provider.archive.ArchiveFileSystem.passwords` 필드 (수정, `:55`)
```kotlin
private val passwords = ArchivePasswordList(lock) { entriesCache.isOpen }
```
- **추적**: HLD H2 / REQ-86-01~05
- `var` → `val`. 선언 위치는 지금 그대로(`lock` `:50`, `entriesCache` `:52-53` 뒤) — 초기화 순서상 `lock`이 이미 있다. 람다는 호출 때 `entriesCache`를 읽으므로 순서와 무관하지만 위치를 바꾸지 않는다.

### `ArchiveFileSystem.addPassword(password: String)` (수정, `:111-118`)
```kotlin
fun addPassword(password: String) {
    passwords.add(password)
}
```
- **추적**: HLD H2 / REQ-86-01·03
- **책임·입력·오류**: 서명·호출자(`PathArchiveExtensions.kt:11-14`)·예외(닫힘 → `ClosedFileSystemException`, 주 스레드 호출자에게 그대로 전파 — HLD 3.3) 모두 그대로. `synchronized(lock)`만 없어진다.

### `ArchiveFileSystem.newInputStream(file: Path): InputStream` (수정, `:82` 한 줄)
- **추적**: HLD H2 / REQ-86-02
- `ArchiveReader.newInputStream(archiveFile, passwords, entry)` → `ArchiveReader.newInputStream(archiveFile, passwords.getLocked(), entry)`. `:75`의 `synchronized(lock)` 안이고 이 호출 한 번에서 한 번만 읽는다. 그 밖(`:74-87`)은 그대로.

### `ArchiveFileSystem.ensureEntriesLocked(file: Path)` (수정, `:130` 한 줄)
- **추적**: HLD H2 / REQ-86-02
- `ArchiveReader.readEntries(archiveFile, passwords, rootDirectory)` → `ArchiveReader.readEntries(archiveFile, passwords.getLocked(), rootDirectory)`. 읽기 함수는 `entriesCache.getLocked`(`ArchiveEntriesCache.kt:40-55`) 안에서만 불리고, 그 함수는 `holdsLock`을 확인한 뒤 부르므로 전제가 늘 맞는다. 항목 읽기 한 번에 한 번만 읽는다.

### 스레드·잠금 요약 (HLD 3.2, 6.1 코드 검사 항목)
| 호출 | 스레드 | `lock` |
|---|---|---|
| `addPassword()` → `ArchivePasswordList.add` | 주(`ArchivePasswordDialogFragment.kt:108`) | **잡지 않음**(이번 변경) |
| 데이터 열기·항목 읽기 → `getLocked()` | 풀 작업 스레드 | 잡음(지금과 같음) |
| `refresh()`, `close()`, `isOpen()`, 조회 4개 | 지금과 같음 | 지금과 같음 |

코드 검사: `ArchivePasswordList.kt`에 `synchronized`가 없다. `ArchiveFileSystem`의 `synchronized(lock)` 블록은 지금 8개(`:59`, `:69`, `:75`, `:91`, `:102`, `:112`, `:139`, `:148`)에서 `addPassword`(`:112`)의 1개만 빠진 7개가 된다. `passwords` 이름이 나오는 곳은 필드 선언·`add` 호출·`getLocked()` 두 번뿐이다. `ArchiveFileSystem.kt`·`ArchivePasswordList.kt` 밖의 운영 코드 diff 없음.

## 3. 테스트 설계

JVM 단위 시험 `provider/archive/ArchivePasswordListTest.kt`(`./gradlew testDebugUnitTest`). 대상은 H1만 — `ArchiveFileSystem`은 provider·libarchive 의존이라 JVM 시험하지 않고 코드 검사(2장 요약)와 AVD(3.1)로 확인한다. 픽스처: `lock = Any()`, `cache = ArchiveEntriesCache<String>(lock)`, `list = ArchivePasswordList(lock) { cache.isOpen }`(운영과 같은 연결, HLD 6.1-3). 잠금 안 읽기는 시험에서 `synchronized(lock) { list.getLocked() }`로 감싼다(= 가짜 항목 읽기, SRS REQ-86-02).

**멈추지 않는 시험**: 잠금을 쥐는 스레드는 `release: CountDownLatch(1)`을 `release.await(5, SECONDS)`로만 기다린다. 시험 스레드는 `CountDownLatch.await(timeout)`·`join(5000)`만 쓴다. 모든 스레드 시험은 `try { … } finally { release.countDown(); 스레드 join(5000) }`로 정리한다(`ArchiveEntriesCacheTest.kt:56-74` 형태). 그래서 임시 구현에서도 각 시험은 몇 초 안에 실패로 끝난다.

**fail-before 확인 방법**(새 클래스라 "수정 전"은 컴파일되지 않으므로 #83과 같은 방식, SRS REQ-86-01): 구현 첫 WIP 커밋에 H1과 **같은 서명**의 임시 구현 + 시험을 넣는다(`ArchiveFileSystem`은 아직 바꾸지 않음). 임시 구현은 지금의 잠금 구조(`:111-118`)를 흉내 낸다 — 파일 머리에 `// TEMPORARY: mimics the current locking of ArchiveFileSystem.addPassword() …` 주석:
- 상태 `private var passwords = listOf<String>()`(lock 안에서만)
- `add(password)` = `synchronized(lock) { if (!isOpen()) throw ClosedFileSystemException(); passwords += password }`
- `getLocked()` = `check(Thread.holdsLock(lock)); return passwords`

임시 구현에서 실패해야 하는 것은 **T-86-01**뿐이다(추가가 `lock`을 기다려 1초 시간 초과). T-86-02~05는 계약 시험이라 임시 구현에서도 통과한다(REQ-86-02·03·04는 지금 구조도 지키므로 회귀 방지). 다음 커밋에서 실제 구현으로 바꾸고 `ArchiveFileSystem`을 연결해 전부 통과를 기록한다. 두 실행의 명령과 출력 끝부분(실패 시험 이름·`AssertionError` 줄 포함)을 PR 코멘트에 붙인다.

| 테스트 | 대상 | 경우 | 기대 결과 | 임시 구현 |
|---|---|---|---|---|
| T-86-01 `add_whileLockHeld_returnsWithoutWaiting` (REQ-86-01) | `add` | 스레드 A: `synchronized(lock) { held.countDown(); release.await(5s) }`. 시험은 `held.await(2s)` 참 확인 → 스레드 B: `list.add("p1"); added.countDown()` | `added.await(1, SECONDS)` 참, 그 시점에 A 살아 있음(`A.isAlive`). `finally`에서 풀고 join 뒤 `synchronized(lock) { list.getLocked() }` = `["p1"]` | **실패**(B가 `lock`을 기다림 → 1초 시간 초과) |
| T-86-02 `add_thenGetLocked_returnsPasswordsInOrder` (REQ-86-02) | `add`·`getLocked` | 처음 읽기 → `add("a")` → `add("b")` → `add("a")` → 읽기 | 처음 `[]`, 뒤 `["a", "b", "a"]`(순서 유지, 중복 허용). 처음 읽은 목록 객체는 뒤에도 `[]`(불변, R1) | 통과 |
| T-86-03 `add_whenClosed_throwsAndKeepsList` (REQ-86-03) | `add` | `add("a")` → `synchronized(lock) { cache.closeLocked() }` → `add("b")` | `assertThrows(ClosedFileSystemException::class.java)`, 뒤 읽기 `["a"]` | 통과 |
| T-86-04 `add_concurrently_losesNothing` (REQ-86-04) | `add` | 8개 스레드가 `start: CountDownLatch(1)`을 `await(5s)` 뒤 각자 `"t<i>-<j>"`를 j=0..999 추가, `done.await(10s)` | 목록 크기 8000, 값 집합이 기대 집합과 같음, 각 스레드 값의 상대 순서 유지(`t<i>-` 거른 부분 목록이 j 오름차순) | 통과 |
| T-86-05 `getLocked_withoutLock_throwsIllegalState` (HLD D3) | `getLocked` | `lock` 없이 호출 | `IllegalStateException`(`assertThrows`)이고 `ClosedFileSystemException`이 아님(`assertFalse(e is ClosedFileSystemException)`, #83 T-83-08과 같은 이유) | 통과 |

기존 `ArchiveEntriesCacheTest`·`ArchivePathObservableTest`와 나머지 단위 시험은 그대로 통과해야 한다(REQ-86-05). 실행: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" timeout 1200 ./gradlew testDebugUnitTest --console=plain 2>&1 | tail -n 30`, 이어 `assembleDebug`.

### 3.1 AVD 확인 (SRS 4장 V1·V2)
**로컬 AVD만**(재현 때 `Medium_Phone_API_36`, `-no-snapshot`, 포트 5562). 모든 `adb`에 `-s emulator-<port>`(먼저 `adb devices`로 확인). **실기기 R54N102PRWV에는 설치·명령하지 않는다.** 수정 뒤 빌드(feature 브랜치 `assembleDebug`)만 설치한다 — 수정 전은 재현 증거 `_workspace/wo/86-archive-password/anr_B2_trace.txt`·`anr_B3_trace.txt`(develop 0096406, ANR 2/2)로 갈음한다.

준비: 재현 스크립트 `$MAIN_REPO/_workspace/wo/86-archive-password/{lib.sh,scenB.sh,uifind.py,make_enc_zip.py}`. `lib.sh`의 `H`와 `scenB.sh:2`의 `. …/lib.sh`가 지워진 임시 폴더(`G:/tmp/MeterialFiles/86-archive-password`)를 가리키므로, 임시 폴더를 다시 만들어 `lib.sh`·`uifind.py`를 복사하거나 경로를 고친 사본을 쓴다(원본 `_workspace` 파일은 그대로 둔다). `lib.sh`의 포트(`emulator-5562`)는 실제 AVD 포트에 맞춘다. 압축 생성: venv 파이썬 `make_enc_zip.py <tmp>/deep.zip 200000 pw deep`(넷째 인자가 있으면 깊은 경로), 같은 파일을 `deep2.zip`으로도 두고 둘 다 `/sdcard/Download/`에 push. 각 시나리오 전 `logcat -c`, 끝나면 `logcat -d`·`dumpsys dropbox --print data_app_anr`을 `_workspace/wo/86-archive-password/avd_<V>_after.txt`로 저장한다.

판정(공통): 시나리오 뒤 새 `data_app_anr` 항목 없음(`anr_after == anr_before`), logcat에 `ANR in me.zhanghai.android.files` 없음, `Skipped N frames`에서 N ≥ 300(약 5초) 없음, 입력 지연 프로브(`scenB.sh`의 `probe1~5`)가 모두 1000 ms 미만.

| V | 절차 | 수정 뒤 기대 | 수정 전(증거) |
|---|---|---|---|
| V1 (SRS V1) | `Z=deep bash scenB.sh <out> 1`: `deep.zip` 열기 → 목록에서 `z_last.txt` 탭 → 비밀번호 창 → `pw` 입력 → `deep2.zip`을 `deep.zip`에 덮어써 #69 감시의 다시 읽기 시작 → 1초 뒤 OK. 2회 반복 | 판정 통과. 창은 곧 닫히고, 다시 읽기가 끝난 뒤 `z_last.txt`가 맞는 비밀번호로 열림(logcat의 `ActivityTaskManager: START` 텍스트 보기 또는 `uiautomator dump`/스크린샷에 내용 표시) | ANR 2/2(`anr_B2_trace.txt:78-81` main `addPassword:112` 대기) |
| V2 (SRS V2) | 스크립트 없음(`scenB.sh`는 시나리오 B 전용) — `lib.sh`의 `open_zip`·`ui`를 `Z=small`로 써서 손 절차로 한다. `deep.zip`이 아닌 작은 암호 zip(`make_enc_zip.py <tmp>/small.zip 10 pw`)을 열어 `z_last.txt` 탭 → `bad` 입력 OK → 창이 다시 뜸을 확인(`uiautomator dump`에 `OK`) → `pw` 입력 OK. 이어 암호 없는 zip(예: venv `zipfile`로 만든 `plain.zip`, 파일 2개·폴더 1개) 열기·폴더 들어갔다 나오기 | 틀린 비밀번호 뒤 창이 다시 뜸(REQ-86-05), 맞는 비밀번호 뒤 파일이 열림. 암호 없는 zip 목록·이동 정상. 판정 통과 | 수정 전과 같음(동작 불변 확인) |

끝나면 앱 제거·push한 파일 삭제·애니메이션 설정 복원(바꿨다면)·에뮬레이터 종료(`adb -s emulator-<port> emu kill`)·임시 폴더 삭제.

## 4. 설계 피드백
- **F1 (검수 메모 해소, 설계 변경 아님)**: HLD D2가 LLD에 맡긴 `updateAndGet` 디슈가링 여부를 확인했다 — 덮인다(2장 `add`의 근거). 그래도 D2 기본값인 `compareAndSet` 루프를 그대로 쓴다. HLD와 어긋나지 않아 설계팀 문의 없음.
- **F2 (LLD에서 정함)**: 닫힘 확인을 CAS 루프의 매 시도 앞에 둔다(HLD는 "닫혔으면 예외, 아니면 CAS 되풀이"). 경합 중 닫힘을 조금 더 일찍 알아챌 뿐 계약(REQ-86-03, SRS A-1)은 같다.
- **F3 (LLD에서 정함)**: `getLocked()`는 닫힘을 검사하지 않는다 — 두 호출 위치 모두 `entriesCache.getLocked`가 먼저 검사한다(2장). 지금 `passwords` 읽기와 같다.
- 그 밖에 SRS·HLD와 어긋나는 점은 찾지 못했다. 설계팀 문의 없음.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | v1 | 최초 작성 | SRS v1.1, HLD v1 | PM |
| 2026-11-20 | v1.1 | 3.1 `scenB.sh:2` 줄 번호 정정, V2가 스크립트 없는 손 절차임을 명시 | LLD 1회차 검수 경미 2건 [기계] (PR #90) | 검수팀 |
