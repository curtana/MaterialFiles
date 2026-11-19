# LLD — 지워진 압축 파일·폴더의 예전 목록 표시 (이슈 #69)

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 |
| 근거 | SRS v1.1, HLD v1.2 |
| 기준 코드 | `origin/develop` f7f5e15(#67 머지 뒤). 이 LLD가 다루는 `filelist/`·`provider/` 파일은 HLD 기준 8cafb75와 같아(`git diff --stat 8cafb75 f7f5e15 -- …/filelist …/provider` 빈 출력) 줄 번호도 같다. 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준, 테스트는 `app/src/test/java/me/zhanghai/android/files/` 기준 |

## 1. 변경 파일
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `provider/archive/ArchivePathObservable.kt` | 신규 | HLD H2: 원본 감시를 감싸 "무효화 → 관찰자"를 실행기 작업으로 넘기는 `PathObservable` |
| `provider/archive/ArchiveFileSystemProvider.kt` | 수정 | HLD H1: `observe()`(`:278-281`) 본문과 import 두 줄 |
| `filelist/FileListRefreshPolicy.kt` | 수정 | HLD H3: 순수 함수 `failureValue` 추가 |
| `filelist/FileListLiveData.kt` | 수정 | HLD H4: `:80` 한 줄 |
| `provider/archive/ArchivePathObservableTest.kt`(test) | 신규 | 3장 T-H2-01~06 |
| `filelist/FileListRefreshPolicyTest.kt`(test) | 수정 | 3장 T-H3-01~03, T-R4-01 추가 |

그 밖(`FileListFragment.kt`, `PathObserver.kt`, `FileListLoader.kt`, `FileListViewModel.kt`, `ArchiveFileSystem.kt`, 다른 provider)은 바꾸지 않는다(HLD 2장 "변경 없음"). 소스 4개 + 테스트 2개.

## 2. 함수 명세

### `provider.archive.ArchivePathObservable` (class, 신규, `internal`)
```kotlin
internal class ArchivePathObservable(
    private val archiveFileObservable: PathObservable,
    private val invalidate: () -> Unit,
    private val executor: Executor
) : PathObservable
```
- **추적**: HLD H2, D5, D6 / REQ-69-01, REQ-69-02
- **책임**: 원본 압축 파일의 감시(`archiveFileObservable`)에 래퍼를 등록해, 원본 알림이 오면 실행기 작업에서 `invalidate()` 뒤 관찰자를 부른다.
- **필드**: `private val wrappers = mutableMapOf<() -> Unit, () -> Unit>()`(관찰자 → 래퍼), `synchronized(wrappers)`로만 읽고 쓴다.
- **안드로이드 의존 없음**: import는 `provider.common.PathObservable`, `java.util.concurrent.Executor`뿐. 예외 기록은 주변 코드 관례대로 `e.printStackTrace()`(`FileListLiveData.kt:72`, `ArchiveReader.kt:138`)로 한다 — `android.util.Log`를 쓰지 않아 JVM 시험이 된다.
- **기존 코드와의 관계**: 시험한 수정안(`prototype.diff`)의 익명 객체를 클래스로 뺀 것. 다른 점은 래퍼가 무효화·관찰자를 직접 하지 않고 `executor`에 넘긴다는 것(D6)과 `I69` 로그가 없다는 것. 묶기(1000ms)는 원본 `AbstractPathObservable`(`provider/common/AbstractPathObservable.kt:20-24`)이 하므로 여기서 하지 않는다.

### `ArchivePathObservable.addObserver(observer: () -> Unit)` (신규)
- **추적**: HLD H2 / REQ-69-01, REQ-69-02
- **입력·전제**: 호출 스레드 무관(실제로는 `PathObserver`의 풀 작업, `PathObserver.kt:40-43`). 같은 `observer`를 두 번 넣으면 앞 래퍼를 원본에서 떼고 새 래퍼로 바꾼다(`AbstractPathObservable.kt:23`의 덮어쓰기와 같은 의미).
- **출력·사후조건**: 원본에 래퍼 `w`가 하나 등록되고 `wrappers[observer] = w`. `w()`는 다음만 한다:
  ```kotlin
  val wrapper: () -> Unit = {
      executor.execute {
          try {
              invalidate()
          } catch (e: Exception) {
              e.printStackTrace()
          }
          observer()
      }
  }
  ```
  즉 `w()`는 `executor.execute` 하나만 부르고 돌아온다 — `w`를 부르는 `Notifier` 스레드(`AbstractPathObservable.kt:64-77`)는 `ArchiveFileSystem.lock`을 기다리지 않는다(HLD 3.3, R6).
- **오류**: 원본 `addObserver`가 던지면(닫힌 원본이면 `ClosedDirectoryObserverException`, `AbstractPathObservable.kt:42-46`) `wrappers`에서 방금 넣은 항목을 빼고 그대로 던진다. `invalidate()`의 예외(예: 닫힌 파일 시스템의 `ClosedFileSystemException`, `ArchiveFileSystem.kt:124-126`)는 작업 안에서 잡고 기록만 한다 — 관찰자는 그래도 불린다(REQ-69-02). `observer()`의 예외는 잡지 않는다(지금 `Notifier`에서 부를 때와 같다. 실제 관찰자는 `mainHandler.post`뿐, `PathObserver.kt:42`).
- **알고리즘**:
  ```
  wrapper = (위 정의)
  previous = synchronized(wrappers) { wrappers.put(observer, wrapper) }
  if (previous != null) archiveFileObservable.removeObserver(previous)
  try archiveFileObservable.addObserver(wrapper)
  catch (e) { synchronized(wrappers) { wrappers.remove(observer, wrapper) }; throw e }
  ```

### `ArchivePathObservable.removeObserver(observer: () -> Unit)` (신규)
- **추적**: HLD H2 / REQ-69-01
- **출력·사후조건**: `observer`의 래퍼를 맵에서 빼고 원본에서 뗀다. 모르는 `observer`면 아무것도 하지 않는다(원본을 부르지 않는다).
- **오류**: 원본 `removeObserver`의 예외는 그대로 던진다.
- **알고리즘**: `val w = synchronized(wrappers) { wrappers.remove(observer) } ?: return; archiveFileObservable.removeObserver(w)`
- **기존 코드와의 관계**: `PathObserver`는 지금 `removeObserver`를 부르지 않지만(`close()`만, `PathObserver.kt:55`) `PathObservable` 계약(`provider/common/PathObservable.kt`)을 채운다.

### `ArchivePathObservable.close()` (신규, `@Throws(IOException::class)`)
- **추적**: HLD H2, 3.3 / REQ-69-01
- **출력·사후조건**: `wrappers`를 비우고 `archiveFileObservable.close()`. 원본 `close()`가 등록된 래퍼(묶기 작업 포함)를 모두 취소하므로(`AbstractPathObservable.kt:49-58`) 하나씩 떼지 않는다. 이미 실행기에 넘어간 작업은 끝까지 돈다 — 관찰자는 `PathObserver`의 `mainHandler.post`라 닫힌 뒤 불려도 `FileListLiveData`가 닫힌 뒤의 읽기 요청이 될 뿐이고, 이는 지금 `Notifier`에 이미 올라간 알림과 같다.
- **오류**: 원본 `close()`의 `IOException`은 그대로 던진다(`PathObserver.kt:55`가 `closeSafe()`로 받는다).

### `provider.archive.ArchiveFileSystemProvider.observe(path: Path, intervalMillis: Long): PathObservable` (수정, `:278-281`)
- **추적**: HLD H1, D1, D2 / REQ-69-01, REQ-69-06
- **입력·전제**: `PathObserver`의 풀 작업 스레드(`PathObserver.kt:26-32`)에서 `Path.observe()`(`provider/common/PathExtensions.kt:278-279`)를 거쳐 불린다. 주 스레드에서 불리지 않는다.
- **출력·사후조건**: `ArchivePathObservable(archiveFile.observe(intervalMillis), fileSystem::refresh, AsyncTask.THREAD_POOL_EXECUTOR)`. 원본 감시 하나가 새로 생긴다(호출마다 새로, HLD 3.3 끝).
- **오류**: `path`가 `ArchivePath`가 아니면 `ProviderMismatchException`(이 파일의 다른 함수들과 같은 형식, 예 `:274`). 원본 `observe()`가 던지는 `UnsupportedOperationException`·`IOException`은 잡지 않는다 — `PathObserver.kt:33-39`가 지금처럼 무시하고 감시 없이 동작한다(REQ-69-01 "감시 미지원이면 지금처럼").
- **알고리즘**:
  ```kotlin
  path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
  val fileSystem = path.fileSystem
  return ArchivePathObservable(
      fileSystem.archiveFile.observe(intervalMillis), fileSystem::refresh,
      AsyncTask.THREAD_POOL_EXECUTOR
  )
  ```
  import 추가: `android.os.AsyncTask`, `me.zhanghai.android.files.provider.common.observe`.
- **잠금 확인(REQ-69-06, HLD 6.1 코드 검사)**: 이 함수가 읽는 `path.fileSystem`(`ArchivePath.kt:59`)과 `archiveFile`(`ArchiveFileSystem.kt:34` `val`)은 `lock`을 잡지 않는다. `fileSystem::refresh`는 참조만 만들고 부르지 않는다. 압축 항목을 읽지 않는다(`ensureEntriesLocked`를 부르는 함수가 없다). 첫 목록 읽기(`FileListLiveData.kt:38`)는 다른 풀 작업이라 이 함수를 기다리지 않는다.
- **원본 경로별 동작**: 모든 provider가 `PathObservableProvider`를 구현하므로(`git grep "PathObservableProvider"` — linux(Rootable), content, document, ftp, sftp, smb, webdav, archive) `Path.observe()`의 형 변환(`PathExtensions.kt:279`)은 실패하지 않는다. 로컬·루트 원본은 `RootableFileSystemProvider.observe`(`provider/root/RootableFileSystemProvider.kt:154-176`)가 읽기 권한을 먼저 확인하고 `WatchServicePathObservable`(로컬, `provider/linux/LocalLinuxFileSystemProvider.kt:355-358`) 또는 원격 감시를 만든다. 압축 안의 압축이면 `archiveFile`이 `ArchivePath`라 이 함수가 다시 불려 안쪽부터 `ArchivePathObservable`이 겹친다 — 알림은 바깥 원본 감시 → 바깥 압축 무효화 → 안쪽 래퍼 → 안쪽 압축 무효화 → 관찰자 순서다(HLD H1 "바깥 압축부터").
- **기존 코드와의 관계**: `FileListViewModel.reload()`의 `archiveRefresh()`(`FileListViewModel.kt:81-87`)와 같은 `ArchiveFileSystem.refresh()`(`ArchiveFileSystem.kt:122-129`)를 쓴다. 그 주 스레드 호출은 #83 범위라 그대로 둔다.

### `filelist.failureValue(previous: T?, e: Exception): T?` (신규, `internal`, 파일 수준 함수)
```kotlin
/**
 * The value a failed load should keep. A failure because the path no longer exists drops the list
 * shown so that the error is shown instead of stale files; other failures keep it (#29).
 */
internal fun <T> failureValue(previous: T?, e: Exception): T? =
    if (e is NoSuchFileException) null else previous
```
- **추적**: HLD H3, D3, D4 / REQ-69-03, REQ-69-05
- **책임**: 실패 결과가 담을 목록을 정한다.
- **입력·전제**: `previous` = 실패 직전 LiveData 값의 목록(`valueCompat.value`, `null` 가능). `e` = 읽기가 던진 예외. 부작용 없음, 스레드 무관.
- **출력·사후조건**: `e`가 `java8.nio.file.NoSuchFileException`(하위 클래스 포함)이면 `null`, 아니면 `previous` 그대로(같은 객체).
- **오류**: 없음.
- **알고리즘**: 위 한 줄. `e.cause`는 보지 않는다(HLD H3, R2 — 아래 "R2 확인").
- **기존 코드와의 관계**: `FileListRefreshPolicy.kt`의 `shouldLoadSilently`·`shouldNotifyFailure`(`:17-18`, `:30-42`) 옆에 둔다. 그 두 함수가 `Stateful<*>`로 일반화돼 있고 기존 시험(`FileListRefreshPolicyTest.kt`)이 `List<String>`으로 시험하므로, HLD의 `List<FileItem>?` 대신 형 인자 `T`로 둔다(동작 같음, 4장 F1). import 추가: `java8.nio.file.NoSuchFileException`.

### `filelist.FileListLiveData.loadFileList(): Stateful<List<FileItem>>` (수정, `:62-81`)
- **추적**: HLD H4 / REQ-69-03, 04, 05, 07
- **입력·전제**: `FileListLoader`의 풀 작업 스레드(`FileListLiveData.kt:25-28`). 지금과 같다.
- **출력·사후조건**: 성공은 지금과 같다. 실패는 `Failure(failureValue(valueCompat.value, e), e)` — 없음 실패면 `Failure(null, e)`, 그 밖은 지금과 같은 `Failure(이전 목록, e)`.
- **오류**: 지금과 같다(모든 `Exception`을 결과로 바꾼다).
- **알고리즘**: `:80` `Failure(valueCompat.value, e)` → `Failure(failureValue(valueCompat.value, e), e)`. 그 밖의 줄(조용함 `:48-52`, 명시 실패 표식 `:54-60`, 감시 `:39`)은 바꾸지 않는다. 같은 패키지 파일 수준 함수라 import는 필요 없다.
- **화면 결과(바꾸지 않는 코드로 확인)**: `Failure(null, e)`면 `FileListFragment.onFileListChanged`에서 부제목 "Error"(`:784`), `hasFiles` 거짓 → 가운데 오류 글 보임(`:791`), 글은 `throwable.toString()`(`:795`, `:803`), 토스트 분기(`:796-801`)를 타지 않음, 목록 비움(`adapter.clear()`, `:811`). 다음 자동 계기는 `shouldLoadSilently(true, Failure(null, …))`가 거짓이라 로딩 표시(REQ-69-04).

### R2 확인 — 루트·원격 프로세스 경로의 `NoSuchFileException` 전달 (HLD R2 해소)
루트 경로의 목록 읽기는 다음 길로 같은 형의 예외를 받는다. **형은 유지된다**(코드로 확인, 기기 확인은 하지 않음).
1. `FileListLiveData.kt:64` `path.newDirectoryStream()` → `RootableFileSystemProvider.newDirectoryStream`(`provider/root/RootableFileSystemProvider.kt:70-73`) → `callRootable`(`provider/root/RootablePath.kt:41-63`)은 전략에 따라 로컬 또는 루트 provider를 **한 번** 부르고 예외를 바꾸지 않는다(다시 시도·감싸기 없음).
2. 루트 provider는 `RemoteFileSystemProvider.newDirectoryStream`(`provider/remote/RemoteFileSystemProvider.kt:78-90`) → `call { … }`(`provider/remote/IInterfaceExtensions.kt:18-28`).
3. 루트 프로세스 쪽: `RemoteFileSystemProviderInterface.newDirectoryStream`(`provider/remote/RemoteFileSystemProviderInterface.kt:38-46`)이 `tryRun`(`IInterfaceExtensions.kt:31-45`)으로 감싸고, 로컬 provider의 `opendir` 실패는 `SyscallException.toFileSystemException`(`provider/linux/syscall/SyscallException.kt:58-69`, `ENOENT` → `NoSuchFileException`, `:66`)으로 `NoSuchFileException`이 된다(`provider/linux/LocalLinuxFileSystemProvider.kt:123-127`). `tryRun`은 그 `IOException`을 `ParcelableException.value`에 넣는다(`:38-40`).
4. `ParcelableException.writeToParcel`은 `dest.writeSerializable(value)`(`provider/remote/ParcelableException.kt:33-35`), 앱 쪽은 `readSerializableCompat()`(`:24`, `:30`; `compat/ParcelCompat.kt:65` = `readSerializable()`)로 푼다. 자바 직렬화는 클래스 이름으로 객체를 되살리고, 두 프로세스가 같은 APK라 `java8.nio.file.NoSuchFileException` 그대로 돌아온다. 원인 사슬(`SyscallException` ← `ErrnoException`)도 직렬화 가능한 예외다.
5. 앱 쪽 `call`이 `exception.value?.let { throw it }`(`IInterfaceExtensions.kt:27`)으로 그 객체를 던진다 → `loadFileList`의 `catch`(`:79`) → `failureValue`가 `is NoSuchFileException` 참.

같은 직렬화 길로 루트 경로의 다른 오류(예: `AccessDeniedException`)가 지금도 형 그대로 표시되므로 새 위험은 없다. 원격 서버 provider(FTP·SFTP·SMB·WebDAV·SAF)가 "없음"을 다른 형으로 알리면 REQ-69-05(B3)로 동작한다(SRS A-1) — 지금보다 나빠지지 않아 이 LLD에서 더 확인하지 않는다. 압축 원본이 지워진 경우는 `ArchiveReader.openArchive`(`provider/archive/archiver/ArchiveReader.kt:131-164`)가 채널 열기 실패를 삼킨 뒤 `file.newInputStream()`(`:153`)에서 로컬 provider의 `NoSuchFileException`을 그대로 던진다(`ArchiveException` 변환 `ArchiveFileSystem.kt:139-141`을 거치지 않음). 원인 조사에서 당겨서 새로 고침이 `NoSuchFileException`을 보인 것과 같다(SRS 1.1).

### 스레드·잠금 요약 (HLD 3.3, 6.1 코드 검사 항목)
| 호출 | 스레드 | `ArchiveFileSystem.lock` |
|---|---|---|
| `ArchiveFileSystemProvider.observe` | `PathObserver` 풀 작업(`PathObserver.kt:26`) | 잡지 않음 |
| `ArchivePathObservable.addObserver`·`removeObserver`·`close` | `PathObserver` 풀 작업(`:26-58`) | 잡지 않음 |
| 래퍼 `w()` | `Notifier` HandlerThread(`AbstractPathObservable.kt:23`, `:67-68`) | 잡지 않음(`executor.execute`만) |
| 래퍼 작업(`invalidate` = `refresh()` → `observer()`) | `AsyncTask.THREAD_POOL_EXECUTOR` | `refresh()`가 잡음 — 풀 스레드라 기다려도 됨 |
| `observer()` → `mainHandler.post(onChange)` → `onChangeObserved()` | 주 스레드는 `post`된 것만(`PathObserver.kt:41-42`, `FileListLiveData.kt:83-89`) | 잡지 않음 |
| `loadFileList` → `failureValue` | 로더 풀 작업(`FileListLiveData.kt:25-28`) | 읽기가 잡음(지금과 같음) |

#69가 주 스레드에서 `ArchiveFileSystem`의 `synchronized(lock)` 함수를 부르는 곳은 없다.

## 3. 테스트 설계

JVM 단위 시험(`./gradlew testDebugUnitTest`). 모두 안드로이드 없이 돈다. **fail-before 확인 방법**(새 함수라 "수정 전"은 컴파일이 안 되므로): 구현 첫 WIP 커밋에서 지금 동작을 흉내 낸 임시 구현으로 시험을 돌려 실패를 기록하고, 다음 커밋에서 실제 구현으로 통과를 기록한다.
- `failureValue` 임시 구현 = `previous`를 그대로 돌려줌(지금 `FileListLiveData.kt:80`의 동작).
- `ArchivePathObservable` 임시 구현 = 래퍼가 `executor` 없이 호출 스레드에서 바로 `invalidate()` → `observer()`(시험한 수정안의 동작).

| 테스트 | 대상 함수 | 경우(정상·경계·오류) | 기대 결과 | fail-before(임시 구현) |
|---|---|---|---|---|
| T-H3-01 `failureValue_noSuchFile_dropsList` | `failureValue` | (`listOf("a","b")`, `NoSuchFileException("/x")`); 하위 클래스도 — 시험 안 익명 하위 클래스 `object : NoSuchFileException("/x") {}` | `null` | 실패(목록이 돌아옴) |
| T-H3-02 `failureValue_otherFailure_keepsList` | `failureValue` | (목록, `AccessDeniedException("/x")`), (목록, `IOException("x")`), (목록, `FileSystemException("/x")`), (목록, `IOException(NoSuchFileException("/x"))` — 원인 사슬은 보지 않음), (목록, `RuntimeException()`) | 같은 목록 객체(`assertSame`) | 통과 |
| T-H3-03 `failureValue_noPrevious_isNull` | `failureValue` | (`null`, `NoSuchFileException`), (`null`, `IOException`) | `null` | 통과 |
| T-R4-01 `noSuchFileFailure_showsErrorScreenRules` | `failureValue` + `shouldLoadSilently` + `shouldNotifyFailure` | `f = Failure(failureValue(files, nsf), nsf)`: `shouldLoadSilently(true, f)`, `shouldNotifyFailure(Success(files), f, true)`, `shouldNotifyFailure(Success(files), f, false)`. 대조: 다른 `IOException`이면 `shouldNotifyFailure(Success(files), Failure(failureValue(files, io), io), false)` 참(REQ-69-05) | 앞 셋 모두 거짓, 대조 참 | 실패(목록이 남아 조용함·토스트 참) |
| T-H2-01 `notification_invalidatesBeforeObserver` | `ArchivePathObservable` | 가짜 원본(등록된 래퍼를 목록으로 들고 있다가 시험이 부름), 즉시 실행기 `Executor { it.run() }`, 무효화·관찰자가 공유 목록에 기록 | 기록 `["invalidate", "observer"]` | 통과 |
| T-H2-02 `invalidateThrows_observerStillCalled` | 〃 | 무효화가 `IllegalStateException`(`ClosedFileSystemException`과 같은 계열) 던짐, 즉시 실행기 | 래퍼 호출이 예외를 밖으로 내지 않고 관찰자 1회 | 통과(시험한 수정안도 잡음) |
| T-H2-03 `removeObserver_removesWrapperFromSource` | 〃 | `addObserver(o)` 뒤 `removeObserver(o)`; 모르는 관찰자 `removeObserver(p)` | 원본의 래퍼 목록이 빔; 모르는 관찰자는 원본 호출 없음 | 통과 |
| T-H2-04 `close_closesSource` | 〃 | `close()` | 가짜 원본 `closed` 참 | 통과 |
| T-H2-05 `addObserverTwice_replacesWrapper` | 〃 | 같은 `o`로 두 번 `addObserver` | 원본에 래퍼 1개, 알림 시 관찰자 1회 | 통과(임시 구현도 같은 맵 처리) |
| T-H2-06 `blockedInvalidate_wrapperReturnsImmediately` (R6) | 〃 | 무효화가 `CountDownLatch` `release`를 최대 5초 기다림(잠금을 쥔 읽기를 흉내), 실제 스레드 실행기 `Executors.newSingleThreadExecutor()`(시험 끝 `shutdownNow`). 시험 스레드에서 래퍼를 부르고 걸린 시간을 잰다 | 래퍼가 1초 안에 돌아오고 그 시점에 관찰자 0회; `release.countDown()` 뒤 관찰자가 2초 안에 1회(`CountDownLatch` 대기) | 실패(래퍼가 무효화를 기다림 — 5초 뒤 돌아옴) |

기존 `FileListRefreshPolicyTest`·`FileListLoaderTest`와 나머지 단위 시험은 그대로 통과해야 한다. `ArchiveFileSystemProvider.observe`·`FileListLiveData.loadFileList`는 안드로이드·libarchive 의존이라 JVM 시험하지 않고 코드 검사(2장 잠금 확인)와 AVD로 확인한다.

### 3.1 AVD 확인 (SRS 6장 V1~V5)
**로컬 AVD만** 쓴다(예: `Medium_Phone_API_36`). 모든 `adb`에 `-s emulator-<port>`를 붙이고(먼저 `adb devices`로 포트 확인), **실기기(R54N102PRWV 등)에는 설치·명령하지 않는다**. 수정 전 빌드(develop f7f5e15)와 수정 뒤 빌드(feature 브랜치)를 각각 `assembleDebug` → `adb -s emulator-<port> install -r`로 설치해 비교한다. 준비: `t.zip`(안에 `d/a.txt`, `b.txt`)과 내용이 다른 `t2.zip`(안에 `c.txt`)을 `/sdcard/Download/`에 push. 화면 확인은 `uiautomator dump` 글자(증거 폴더 `repro.sh` 방식)와 스크린샷. 결과는 `_workspace/wo/69-stale-archive/avd_<V>_{before,after}.txt`.

| V | 절차 | 수정 뒤 기대 | 수정 전 기대(fail-before) |
|---|---|---|---|
| V1 HOT 압축 | VIEW intent로 `t.zip` 열기(`am start -W -a android.intent.action.VIEW -t application/zip -d file:///storage/emulated/0/Download/t.zip -n me.zhanghai.android.files/.filelist.FileListActivity`) → 목록 확인 → (a) 화면에 둔 채 `adb shell rm /sdcard/Download/t.zip`, 5초 대기 / (b) 다시 push 후 열기 → 홈 → `pidof`로 프로세스 생존 확인 → `rm` → 같은 intent로 다시 열기. (c) `d/` 안에서 (a) 반복 | (a)(b)(c) 모두 `d`·`b.txt` 없음, 가운데 `java8.nio.file.NoSuchFileException…` 글, 부제목 "Error", 토스트 없음 | 예전 `d`·`b.txt`가 남음 |
| V2 COLD 압축 | `am force-stop` → `pidof` 빈 것 확인 → `rm` → 다시 열기 | 첫 화면부터 오류 화면 | 같음(지금도 오류) |
| V3 로컬 폴더 | `/sdcard/Download/F/`(파일 2개)를 앱에서 열어 둠 → `adb shell rm -r /sdcard/Download/F` → 당겨서 새로 고침(`input swipe`로 아래로 끌기) → 결과 확인. 보조: 새로 고치지 않고 5초 대기 뒤 화면(필수 판정 아님). 마지막으로 `mkdir`+파일 push → 당겨서 새로 고침 | 새로 고침 뒤 가운데 진행 표시를 거쳐 오류 화면, 토스트 없음. 다시 만든 뒤 새로 고침 → 목록(L1) | 예전 목록 + "Error" + 토스트 |
| V4 정상 압축 | `t.zip` 열기 → `d/` → 뒤로 → 수정 전과 같은 목록·로딩 표시인지. 그다음 화면에 둔 채 `adb shell "cat /sdcard/Download/t2.zip > /sdcard/Download/t.zip"`(같은 inode에 덮어써 감시가 유지되게), 5초 대기 | 목록이 `c.txt`로 바뀜(REQ-69-02) | 정상 열기는 같음, 덮어쓰기 뒤에도 `d`·`b.txt`가 남음 |
| V5 B3 유지 | `t.zip`을 화면에 둔 채 `adb shell "head -c 100 /sdcard/Download/t2.zip > /sdcard/Download/t.zip"`(잘린 zip, 해석 오류 = `NoSuchFileException`이 아닌 `FileSystemException`, `ArchiveExceptionExtensions.kt:28`), 5초 대기 | 예전 목록 유지 + 부제목 "Error" + 토스트 한 번(REQ-69-05) | 변화 없음(감시가 없어 다시 읽지 않음) — B3 자체는 당겨서 새로 고침으로 같은 결과 |

V5에서 잘린 zip이 오류 없이 읽히면(libarchive가 받아들이면 — 미확인) 그 사실을 기록하고 #29 REQ-29-06 절차 대신 "압축 아닌 글자 파일로 덮어쓰기"(`echo junk > …/t.zip`)로 한 번 더 시도한다. 둘 다 오류가 나지 않으면 V5는 기존 `FileListRefreshPolicyTest`(B3 시험)와 T-H3-02로 갈음하고 보고에 적는다.

## 4. 설계 피드백
- **F1 (경미, LLD에서 정함)**: HLD H3 시그니처 `failureValue(previous: List<FileItem>?, e: Exception): List<FileItem>?`를 형 인자 `<T>`로 일반화했다. 같은 파일의 기존 함수가 `Stateful<*>`로 일반화돼 있고 시험이 `List<String>`을 쓰기 때문이다. 호출 결과·동작은 같다. HLD가 "이름은 LLD에서"로 열어 둔 범위의 다듬기라 설계팀 문의 없이 기록만 한다.
- **F2 (HLD R2 해소)**: 2장 "R2 확인" — 루트 경로에서 `NoSuchFileException`은 `ParcelableException`의 자바 직렬화로 형이 유지된다. HLD R2의 "미확인"은 이 LLD로 "코드로 확인됨(기기 미확인)"이 된다. HLD 문서 수정은 필요 없다(대응 칸이 "LLD에서 확인"이라 적혀 있음).
- 그 밖에 SRS·HLD와 어긋나는 점은 찾지 못했다. 설계팀 문의 없음.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1 | 최초 작성 | SRS v1.1, HLD v1.2 | PM |
