# LLD — 압축 파일 새로 고침 중 주 스레드 멈춤(ANR) (이슈 #83)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1 PR #87 1회차 조건부, 경미 1건 v1.1 반영) |
| 근거 | SRS v1.1, HLD v1.1 |
| 기준 코드 | `origin/develop` ac012e0. 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준, 테스트는 `app/src/test/java/me/zhanghai/android/files/` 기준. 줄 번호는 모두 이 커밋 기준이다 |

## 1. 변경 파일
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `provider/archive/ArchiveEntriesCache.kt` | 신규 | HLD H1: 닫힘·요청 세대·읽은 세대·값을 가진 내부 클래스. 안드로이드·libarchive 의존 없음 |
| `provider/archive/ArchiveFileSystem.kt` | 수정 | HLD H2: 필드 `isOpen`·`isRefreshNeeded`·`entries`·`tree`(`:52`, `:56-60`)를 H1 하나로, `refresh()`(`:122-129`)에서 `synchronized(lock)` 제거, `ensureEntriesLocked`(`:131-146`)가 값을 돌려줌, 조회 함수가 그 값을 씀 |
| `provider/archive/ArchiveEntriesCacheTest.kt`(test) | 신규 | 3장 T-83-01~08 |

그 밖(`filelist/FileListViewModel.kt`, `provider/archive/ArchivePathObservable.kt`, `PathArchiveExtensions.kt`, `ArchiveFileSystemProvider.kt`, `fileaction/ArchivePasswordDialogFragment.kt`)은 바꾸지 않는다(HLD 1장·6.1 코드 검사). HLD H3(#69 HLD 주석)은 설계팀이 이 설계 브랜치에서 이미 반영했다(`docs/design/69-stale-archive/HLD.md` v1.3) — 구현에는 문서 변경이 없다. 운영 2 + 테스트 1 파일.

## 2. 함수 명세

### `provider.archive.ArchiveEntriesCache<T : Any>` (class, 신규, `internal`)
```kotlin
internal class ArchiveEntriesCache<T : Any>(private val lock: Any) {
    @Volatile
    private var isClosed = false
    private val requestedGeneration = AtomicLong()
    private var loadedGeneration = 0L  // lock 안에서만. value != null일 때만 의미
    private var value: T? = null       // lock 안에서만
    ...
}
```
- **추적**: HLD H1, D2, D3 / REQ-83-01~04
- **책임**: 항목 캐시의 닫힘·세대·값 상태와 그 잠금 규칙을 한곳에 둔다. 운영에서 `T` = `Pair<Map<Path, ReadArchive.Entry>, Map<Path, List<Path>>>`(`ArchiveReader.readEntries`의 반환형, `archiver/ArchiveReader.kt:32-36`).
- **생성 인자**: `lock` = `ArchiveFileSystem.lock`(`ArchiveFileSystem.kt:50`) 객체 그대로. 이 클래스는 그 잠금을 스스로 잡지 않는다 — 잠금 안 함수(`getLocked`·`closeLocked`)는 호출자가 쥐고 있어야 하고, 무효화(`invalidate`)는 잡지 않는다(D2: 잠금은 하나).
- **import**: `java8.nio.file.ClosedFileSystemException`, `java.util.concurrent.atomic.AtomicLong`뿐. JVM 시험이 된다(retrofile은 기존 JVM 시험도 쓴다, 예 `filejob/TestPaths.kt`).
- **상태 불변식**: `value == null` ⇔ 아직 읽지 않았거나 닫힘. `value != null`이면 `loadedGeneration` = 그 값을 읽기 **시작 전에** 읽은 요청 세대(HLD 3.1, R1).
- 파일 머리의 저작권 주석은 같은 패키지 새 파일 관례(`ArchivePathObservable.kt:1-4`, `Copyright (c) 2026 Hai Zhang …`)를 따른다.

### `ArchiveEntriesCache.isOpen: Boolean` (속성, 신규)
```kotlin
val isOpen: Boolean
    get() = !isClosed
```
- **추적**: HLD H2 / REQ-83-03
- **책임**: 닫힘 여부를 휘발성 읽기로 돌려준다. 잠금이 필요 없다.
- **사용처**: `ArchiveFileSystem.addPassword`(`:115`)·`close`(`:152`)·`isOpen()`(`:163`). 이 셋은 지금처럼 `synchronized(lock)` 안에서 부른다(잠금 구조 유지, HLD H2).
- 백킹 필드는 공개 속성과 이름이 겹치지 않게 `isClosed`(뜻 반전, `@Volatile`)로 둔다.

### `ArchiveEntriesCache.invalidate()` (신규)
- **추적**: HLD H1 무효화, D1 / REQ-83-01, REQ-83-02, REQ-83-03
- **입력·전제**: 어느 스레드든(주 스레드 `FileListViewModel.kt:84` 경유, 풀 스레드 `ArchivePathObservable.kt:27-36` 경유). **`lock`을 잡지 않는다.** 여러 스레드에서 동시에 불려도 된다.
- **출력·사후조건**: 열려 있으면 `requestedGeneration`이 원자적으로 1 오른다(`incrementAndGet()`). 다른 상태는 바꾸지 않는다.
- **오류**: 닫혀 있으면(`!isOpen` 휘발성 읽기) `ClosedFileSystemException`. `close()`가 끝난 뒤 시작한 호출은 반드시 던진다(휘발성 쓰기→읽기, SRS A-1). `close()`와 겹친 호출이 예외 없이 끝나는 것은 허용(HLD R2) — 닫힌 뒤 `getLocked`가 던지므로 해가 없다.
- **알고리즘**:
  1. `if (!isOpen) throw ClosedFileSystemException()`
  2. `requestedGeneration.incrementAndGet()`
- **기존 코드와의 관계**: `ArchiveFileSystem.refresh()`(`:122-129`)의 본문을 대체한다. 지금의 `isRefreshNeeded = true`(`:127`) 대신 세대를 올리며, `synchronized(lock)`(`:123`)이 없어진다 — 이것이 ANR 원인 제거다(재현 트레이스의 `refresh(ArchiveFileSystem.kt:123)` `waiting to lock`).

### `ArchiveEntriesCache.getLocked(read: () -> T): T` (신규)
- **추적**: HLD H1 잠금 안 조회, 3.1 세대 규칙, R1 / REQ-83-02, REQ-83-03, REQ-83-04
- **입력·전제**: 호출자가 `lock`을 쥐고 있다(`check(Thread.holdsLock(lock))`, 아니면 `IllegalStateException` — 호출 규칙 위반을 조용히 넘기지 않게 한다. 시험 T-83-08). 호출 스레드는 풀 작업 스레드(`FileListLiveData.kt:25-28`). `read`는 압축 전체를 읽는 오래 걸리는 함수이며 `IOException`(또는 그 밖의 예외)을 던질 수 있다.
- **출력·사후조건**: 반환값 = 캐시 값. 다시 읽었으면 `value = read()의 결과`, `loadedGeneration = g`(1단계에서 읽어 둔 값). 다시 읽지 않았으면 상태 불변.
- **오류**: 닫혀 있으면 `ClosedFileSystemException`(읽지 않음). `read`가 던지면 상태(`value`·`loadedGeneration`)를 바꾸지 않고 **그 예외를 그대로** 던진다(감싸지 않음) — 다음 호출이 다시 시도한다(REQ-83-04).
- **알고리즘**:
  1. `check(Thread.holdsLock(lock))`
  2. `if (!isOpen) throw ClosedFileSystemException()`
  3. `val g = requestedGeneration.get()` — **읽기 전에** 읽는다(R1). 이 뒤에 온 무효화는 `g`보다 큰 세대를 남긴다.
  4. `val current = value`; `if (current != null && loadedGeneration == g) return current`
  5. `val newValue = read()` (던지면 그대로 전파, 6단계에 가지 않음)
  6. `value = newValue; loadedGeneration = g; return newValue`
  - 같은 호출 안에서 다시 읽지 않는다(5단계 뒤 세대를 다시 보지 않음, SRS REQ-83-02 "진행 중이던 읽기를 시작한 조회는 그 결과를 돌려준다").
  - 읽기 시작 전의 무효화는 3단계 `g`에 들어가 그 읽기에 포함된다(HLD 3.1 넷째 항목, T-83-05).
- **기존 코드와의 관계**: `ArchiveFileSystem.ensureEntriesLocked`(`:131-146`)의 닫힘 검사(`:133-135`)·`isRefreshNeeded` 판단(`:136`)·저장(`:142-144`)을 대체한다. 지금의 "읽기 끝에 `isRefreshNeeded = false`"(`:144`)는 없어진다(#69 HLD D6 우려 해소).

### `ArchiveEntriesCache.closeLocked(): Boolean` (신규)
- **추적**: HLD H1 잠금 안 닫기 / REQ-83-03
- **입력·전제**: 호출자가 `lock`을 쥐고 있다(`check(Thread.holdsLock(lock))`).
- **출력·사후조건**: 열려 있었으면 `isClosed = true`(휘발성 쓰기), `value = null`, `true` 반환. 이미 닫혔으면 아무것도 바꾸지 않고 `false`.
- **오류**: 없음.
- **알고리즘**: `check(Thread.holdsLock(lock)); if (isClosed) return false; value = null; isClosed = true; return true`. (`value`를 먼저 비우고 닫힘을 마지막에 쓰는 순서는 지금 `close()`의 `entries = null … isOpen = false`(`:157-159`)와 같다.)
- **기존 코드와의 관계**: `ArchiveFileSystem.close()`(`:150-161`)의 상태 정리(`:156-159`)를 대체한다. `isRefreshNeeded = false`(`:156`)는 세대 방식에 해당 값이 없어 대응 없음(닫힌 뒤엔 2단계에서 던지므로 무의미).

### `provider.archive.ArchiveFileSystem` 필드 (수정, `:52-60`)
- **추적**: HLD H2
- **변경**: `:52` `private var isOpen = true`, `:56` `private var isRefreshNeeded = true`, `:58` `entries`, `:60` `tree`를 지우고, `:50` `lock` **뒤에**(초기화 순서) 다음 한 필드를 둔다. `:54` `passwords`는 그대로.
```kotlin
private val entriesCache =
    ArchiveEntriesCache<Pair<Map<Path, ReadArchive.Entry>, Map<Path, List<Path>>>>(lock)
```
- 초기 상태: 요청 세대 0, 값 없음 → 첫 조회가 읽는다(지금 `isRefreshNeeded = true` 초기값 `:56`과 같음, REQ-83-04).

### `ArchiveFileSystem.ensureEntriesLocked(file: Path): Pair<Map<Path, ReadArchive.Entry>, Map<Path, List<Path>>>` (수정, `:131-146`)
- **추적**: HLD H2 / REQ-83-02, REQ-83-04
- **입력·전제**: 호출자(조회 함수)가 `lock`을 쥐고 있다(지금과 같음).
- **출력**: 항목 지도·트리 쌍(H1이 돌려준 값). 반환형이 `Unit`에서 바뀐다 — 필드가 H1로 옮겨져 `entries!!`·`tree!!`를 읽을 수 없으므로 값을 돌려받아 쓴다(HLD H2 "전달 방식은 LLD에서 확정").
- **오류**: 닫힘 → `ClosedFileSystemException`(H1). 읽기 실패 → 지금과 같은 `e.toFileSystemOrInterruptedIOException(file)`(`:139-141`).
- **본문**:
```kotlin
@Throws(IOException::class)
private fun ensureEntriesLocked(file: Path): Pair<Map<Path, ReadArchive.Entry>, Map<Path, List<Path>>> =
    entriesCache.getLocked {
        try {
            ArchiveReader.readEntries(archiveFile, passwords, rootDirectory)
        } catch (e: ArchiveException) {
            throw e.toFileSystemOrInterruptedIOException(file)
        }
    }
```
- `passwords`는 람다가 호출 시점(잠금 안)에 읽는다 — 지금과 같다(`:138`).

### `ArchiveFileSystem.getEntryLocked(entries: Map<Path, ReadArchive.Entry>, path: Path): ReadArchive.Entry` (수정, `:69-73`)
- **추적**: HLD H2
- **변경**: 첫 인자 `entries`를 받고 본문을 `entries[path] ?: throw NoSuchFileException(path.toString())`로. 감싼 `synchronized(lock)`(`:71`, 재진입)은 그대로 둔다(상위 코드 변경 최소, 잠금 구조 유지).

### 조회 함수 4개 (수정, `:62-111`)
- **추적**: HLD H2 / REQ-83-05(동작 불변)
- 각 함수의 `synchronized(lock)`과 나머지 줄은 그대로 두고 아래만 바꾼다.

| 함수 | 줄 | 변경 |
|---|---|---|
| `getEntry(path)` | `:65-66` | `val (entries, _) = ensureEntriesLocked(path)` → `getEntryLocked(entries, path)` |
| `newInputStream(file)` | `:78-79` | `val (entries, _) = ensureEntriesLocked(file)` → `getEntryLocked(entries, file)` |
| `getDirectoryChildren(directory)` | `:94-95`, `:99` | `val (entries, tree) = ensureEntriesLocked(directory)` → `getEntryLocked(entries, directory)`, `:99` `tree[directory]!!` |
| `readSymbolicLink(link)` | `:105-106` | `val (entries, _) = ensureEntriesLocked(link)` → `getEntryLocked(entries, link)` |

### `ArchiveFileSystem.addPassword(password: String)` (수정, `:113-120`)
- **추적**: HLD H2 (#86 범위 밖 — 잠금 그대로)
- **변경**: `:115` `if (!isOpen)` → `if (!entriesCache.isOpen)`. `synchronized(lock)`은 그대로.

### `ArchiveFileSystem.refresh()` (수정, `:122-129`)
- **추적**: HLD H2, D1 / REQ-83-01, REQ-83-03
- **변경**: 본문 전체를 `entriesCache.invalidate()` 한 줄로. **`synchronized(lock)` 없음.** 공개 서명 불변(`Path.archiveRefresh()` `PathArchiveExtensions.kt:22-25`, `ArchivePathObservable` 호출 그대로).
```kotlin
fun refresh() {
    entriesCache.invalidate()
}
```

### `ArchiveFileSystem.close()` (수정, `:150-161`)
- **추적**: HLD H2 / REQ-83-03
- **변경**: 지금 순서(닫힘 확인 → `provider.removeFileSystem(this)` → 상태 정리)를 유지한다.
```kotlin
override fun close() {
    synchronized(lock) {
        if (!entriesCache.isOpen) {
            return
        }
        provider.removeFileSystem(this)
        entriesCache.closeLocked()
    }
}
```
- HLD H1은 "잠금 안 닫기가 열려 있었음이면 `removeFileSystem`"이라 적었으나, 그렇게 하면 `removeFileSystem`보다 닫힘이 먼저 된다. 지금 순서를 지키려고 앞에서 `isOpen`을 보고 `closeLocked()`를 마지막에 부른다(같은 `lock` 안이라 두 검사 사이에 다른 `close()`가 끼지 못한다). `closeLocked()`의 반환값은 여기서 쓰지 않는다. 동작 차이 없음 — 4장 F1.

### `ArchiveFileSystem.isOpen(): Boolean` (수정, `:163`)
- **변경**: `synchronized(lock) { entriesCache.isOpen }`. 잠금 그대로.

### 스레드·잠금 요약 (HLD 3.2, 6.1 코드 검사 항목)
| 호출 | 스레드 | `lock` |
|---|---|---|
| `refresh()` → `invalidate()` | 주(`FileListViewModel.kt:84`)·풀(`ArchivePathObservable.kt:27-36`) | **잡지 않음**(이번 변경) |
| 조회 4개 → `ensureEntriesLocked` → `getLocked` → `read` | 풀(`FileListLiveData.kt:25-28`) | 잡음(지금과 같음) |
| `addPassword()` | 주(`ArchivePasswordDialogFragment.kt:108`) | 잡음(지금과 같음, #86) |
| `close()` → `closeLocked()`, `isOpen()` | 호출자 스레드 | 잡음(지금과 같음) |

코드 검사: `ArchiveEntriesCache.invalidate`·`ArchiveFileSystem.refresh`에 `synchronized`가 없다. `ArchiveFileSystem`의 다른 `synchronized(lock)` 블록 수(`:64`, `:71`, `:77`, `:93`, `:104`, `:114`, `:151`, `:163` 8개)는 그대로다. `filelist/`·`ArchivePathObservable.kt` diff 없음.

## 3. 테스트 설계

JVM 단위 시험 `provider/archive/ArchiveEntriesCacheTest.kt`(`./gradlew testDebugUnitTest`). 대상은 H1만 — `ArchiveFileSystem`은 provider·libarchive 의존이라 JVM 시험하지 않고 코드 검사(2장 요약)와 AVD(3.1)로 확인한다. 시험 값 `T` = `String`, 가짜 읽기는 호출 횟수를 `AtomicInteger`로 세고 `"v<횟수>"`를 돌려준다. `lock = Any()`, 잠금 안 함수는 시험에서 `synchronized(lock) { … }`로 감싸 부른다.

**멈추지 않는 시험(시간 초과로 끝남)**: 막힌 읽기는 `release: CountDownLatch(1)`을 `release.await(5, SECONDS)`로만 기다린다(무한 대기 금지). 막힌 읽기를 도는 스레드와 무효화를 도는 스레드는 `thread { … }`(kotlin.concurrent) 또는 `Executors.newSingleThreadExecutor()`로 만들고, 시험 스레드는 `CountDownLatch.await(timeout)`만 쓴다. 모든 시험은 `try { … } finally { release.countDown(); 스레드 join(5000) / executor.shutdownNow() }`로 정리한다. 그래서 임시 구현에서도 각 시험은 최대 몇 초 안에 실패로 끝난다.

**fail-before 확인 방법**(새 클래스라 "수정 전"은 컴파일되지 않으므로, #69와 같은 방식 — SRS REQ-83-01·02): 구현 첫 WIP 커밋에 H1과 **같은 서명**의 임시 구현 + 시험을 넣는다(`ArchiveFileSystem`은 아직 바꾸지 않음). 임시 구현은 지금의 잠금 구조(`ArchiveFileSystem.kt:122-146`)를 흉내 낸다:
- `invalidate()` = `synchronized(lock) { if (!isOpen) throw ClosedFileSystemException(); isRefreshNeeded = true }`
- `getLocked(read)` = `check(holdsLock)`; 닫힘 검사; `if (isRefreshNeeded) { value = read(); isRefreshNeeded = false }`; `return value!!` (`isRefreshNeeded` 초기값 `true`)
- `closeLocked()` = 실제와 같음(`isRefreshNeeded = false` 포함).

임시 구현에서 실패해야 하는 것은 **T-83-01·T-83-02**(무효화가 막힌 읽기의 끝까지 기다려 1초 대기가 시간 초과)다. 나머지는 H1 계약 시험이라 임시 구현에서도 통과한다. 다음 커밋에서 실제 구현으로 바꿔 전부 통과를 기록한다. 두 실행의 명령과 출력 끝부분을 PR 코멘트에 붙인다.

| 테스트 | 대상 | 경우 | 기대 결과 | 임시 구현 |
|---|---|---|---|---|
| T-83-01 `invalidate_whileReadBlocked_returnsWithoutWaiting` (REQ-83-01) | `invalidate` | 스레드 A: `synchronized(lock) { cache.getLocked { started.countDown(); release.await(5s); "v1" } }`. 시험은 `started.await(2s)` 참을 확인한 뒤 스레드 B에서 `invalidate()` → `done.countDown()` | `done.await(1, SECONDS)` 참. 그 시점에 A는 아직 끝나지 않음(`A.isAlive`) | **실패**(B가 `lock`을 기다림 → 1초 시간 초과) |
| T-83-02 `invalidate_duringRead_nextGetReloads` (REQ-83-02, R1) | `invalidate`·`getLocked` | T-83-01과 같이 막은 뒤 B의 무효화가 1초 안에 끝남을 확인 → `release.countDown()` → A의 결과 받기(A가 결과를 `AtomicReference`에 둠, `A.join(5000)`) → 시험 스레드에서 `getLocked` 한 번 더 | A의 결과 `"v1"`(막힌 읽기의 값, 같은 호출 안에서 다시 읽지 않음), 다음 조회 `"v2"`, 읽기 횟수 2 | **실패**(1초 시간 초과) |
| T-83-03 `get_withoutInvalidate_readsOnce` (REQ-83-02 캐시 유지) | `getLocked` | 조회 2회 | 둘 다 `"v1"`, 읽기 1 | 통과 |
| T-83-04 `invalidate_afterRead_nextGetReloads` (REQ-83-02) | `invalidate`·`getLocked` | 조회 → (진행 중 읽기 없이) 무효화 → 조회 → 조회 | `"v1"`, `"v2"`, `"v2"`, 읽기 2 | 통과 |
| T-83-05 `invalidate_beforeFirstGet_readsOnce` (HLD 3.1) | 〃 | 무효화 2회 → 조회 → 조회 | `"v1"`, `"v1"`, 읽기 1(읽기 전 무효화는 그 읽기에 포함) | 통과 |
| T-83-06 `closed_invalidateAndGetThrow` (REQ-83-03) | `closeLocked`·`invalidate`·`getLocked`·`isOpen` | 조회 1회 뒤 `closeLocked()` 2회 → `invalidate()` → `getLocked` | 첫 `closeLocked` 참, 두 번째 거짓, `isOpen` 거짓, `invalidate`·`getLocked` 모두 `ClosedFileSystemException`(`assertThrows`), 닫힌 뒤 읽기 횟수 그대로 1 | 통과 |
| T-83-07 `readThrows_propagatesAndNextGetRetries` (REQ-83-04) | `getLocked` | (a) 첫 읽기가 `IOException` 객체 `e` 던짐 → 조회가 던짐 → 다음 조회 성공. (b) 값 `"v…"`가 있는 상태에서 무효화 → 읽기가 던짐 → 조회가 던짐 → 다음 조회 | (a) 던진 예외가 `e`와 같은 객체(`assertSame`), 다음 조회가 값 반환, 읽기 2. (b) 실패 뒤 다음 조회가 다시 읽음(예전 값을 돌려주지 않음) | 통과 |
| T-83-08 `lockedFunctions_withoutLock_throwIllegalState` (HLD H1 전제) | `getLocked`·`closeLocked` | `lock`을 잡지 않고 호출 | 둘 다 `IllegalStateException`이고 `ClosedFileSystemException`이 **아님**(`assertFalse(e is ClosedFileSystemException)` — JDK와 같다면 `ClosedFileSystemException`도 `IllegalStateException`의 하위 클래스라 닫힘과 구분하려고), 읽기 0 | 통과 |

기존 `ArchivePathObservableTest`(REQ-83-05)와 나머지 단위 시험은 그대로 통과해야 한다. 실행: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" timeout 1200 ./gradlew testDebugUnitTest --console=plain 2>&1 | tail -n 30`, 이어 `assembleDebug`.

### 3.1 AVD 확인 (SRS 4장 V1~V4)
**로컬 AVD만**(예: `Medium_Phone_API_36`, 재현 때 `-no-snapshot`, 포트 5560). 모든 `adb`에 `-s emulator-<port>`(먼저 `adb devices`로 확인). **실기기 R54N102PRWV에는 설치·명령하지 않는다.** 수정 뒤 빌드(feature 브랜치 `assembleDebug`)만 설치한다 — 수정 전(fail-before)은 재현 증거 `_workspace/wo/83-archive-anr/anr_*_trace.txt` 3건(develop ac012e0 기준)으로 갈음한다.

준비: 재현 때와 같은 큰 압축 `big.tar.bz2`(127 MB, 5만+4 항목, 첫 읽기 약 29초). 남아 있지 않으면 venv 파이썬(`tarfile`, 무작위 내용 작은 파일 5만 개, `w:bz2`) 스크립트로 다시 만들고 스크립트는 `$MAIN_REPO/_workspace/wo/83-archive-anr/`에 둔다. `/sdcard/Download/`에 push. 각 시나리오 전 `adb -s … logcat -c`, 끝나면 `adb -s … shell dumpsys dropbox --print data_app_anr`(재현 때 쓴 방법)과 `logcat -d`를 `_workspace/wo/83-archive-anr/avd_<V>_after.txt`로 저장한다.

판정(공통): 시나리오 시작 뒤 새 `data_app_anr` 항목 없음, logcat에 `ANR in me.zhanghai.android.files` 없음, `Choreographer … Skipped N frames`에서 N ≥ 300(약 5초) 없음. 읽기가 끝난 뒤 압축 루트 목록이 정상 표시(`uiautomator dump` 글자 또는 스크린샷).

| V | 재현 시나리오 | 절차 | 수정 뒤 기대 | 수정 전(증거) |
|---|---|---|---|---|
| V1 | 1 | `big.tar.bz2`를 연다 → 읽는 중(10초 안에) 메뉴 새로 고침 2회 연속 | 판정 통과, 로딩 표시가 이어지다 목록 | ANR(`anr_menu_refresh_trace.txt`) |
| V2 | 2 | 열자마자 당겨서 새로 고침(`input swipe`로 목록을 아래로 끌기) | 판정 통과 | ANR(`anr_swipe_refresh_trace.txt`) |
| V3 | 4 | 열어 첫 읽기 완료까지 대기 → 같은 inode에 같은 내용 덮어쓰기(`cp big.tar.bz2 /sdcard/Download/b2; cat b2 > big.tar.bz2`)로 #69 감시의 다시 읽기를 시작시킴 → 몇 초 안에 메뉴 새로 고침 | 판정 통과 | ANR(`anr_observe_then_refresh_trace.txt`) |
| V4a | #69 V1 | 작은 `t.zip`을 열어 둔 채 `adb -s … shell rm /sdcard/Download/t.zip`, 5초 대기 | 가운데 `NoSuchFileException` 오류 화면(#69 LLD 3.1 V1(a)와 같음) | #69 머지 뒤 동작과 같음 |
| V4b | #69 V4 | `t.zip` 다시 push → 열기 → `d/` → 뒤로 → 화면에 둔 채 `cat t2.zip > t.zip`, 5초 대기 | 목록·이동 정상, 덮어쓰기 뒤 `c.txt`로 바뀜(REQ-69-02) | 같음 |

V1·V2의 "메뉴 새로 고침"·"당겨서 새로 고침" 조작과 압축 여는 방법은 재현 때와 같게 한다(`progress.md` 00:05~00:17). 끝나면 앱 제거·push한 파일 삭제·에뮬레이터 종료(`adb -s emulator-<port> emu kill`).

## 4. 설계 피드백
- **F1 (경미, LLD에서 정함)**: HLD H2는 `close()`가 "H1 잠금 안 닫기가 '열려 있었음'이면 `provider.removeFileSystem(this)`"라 적었다. 그대로 하면 닫힘 표시가 `removeFileSystem`보다 먼저 된다. 지금 코드 순서(`:152-159`)를 지키려고 `entriesCache.isOpen`으로 먼저 확인하고 `removeFileSystem` 뒤에 `closeLocked()`를 부른다. 모두 같은 `lock` 안이라 결과는 같고 HLD의 책임 배분(닫기 상태는 H1)도 그대로다. 설계 변경이 아닌 순서 다듬기라 설계팀 문의 없이 기록만 한다.
- **F2 (경미, LLD에서 정함)**: HLD H1의 "호출자가 `lock`을 쥔 상태" 전제를 `check(Thread.holdsLock(lock))`로 강제한다(T-83-08). 운영 호출(조회 4개·`close()`)은 모두 잠금 안이라 동작 차이 없다.
- **F3 (전달 방식 확정)**: HLD H2가 LLD로 미룬 "`entries!!`·`tree!!` 읽던 곳의 값 전달"은 `ensureEntriesLocked`가 쌍을 돌려주고 `getEntryLocked`가 `entries`를 인자로 받는 방식으로 정했다(2장).
- 그 밖에 SRS·HLD와 어긋나는 점은 찾지 못했다. 설계팀 문의 없음.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | v1 | 최초 작성 | SRS v1.1, HLD v1.1 | PM |
| 2026-11-20 | v1.1 | T-83-08에 "`ClosedFileSystemException`이 아님" 단언 추가 | LLD 1회차 검수 m1 (PR #87) | 검수팀 |
