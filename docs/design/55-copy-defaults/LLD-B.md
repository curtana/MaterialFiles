# LLD — 조각 B: 링크 따라가기 + 깊이 제한 (이슈 #55)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 조각 B1·B2의 영문 작업지시서와 구현·검수의 기준이다. 조각 C는 `LLD-C.md`, 조각 A는 `LLD-A.md`.

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 (v1.2) — 검수 1차 반려 지적 반영, 2차 검수 대기 |
| 근거 | SRS v1.4 (2절 Q2, REQ-55-02·04·05·06·07, 6절, 7절), HLD v1.3 (2.3 조각 B, 2.4, 3.1, 3.3, 4, 5 D5–D10, 6) |
| 기준 코드 | `origin/develop` c39a97e(A1 머지 뒤, PR #75). v1·v1.1은 55c521f 기준이었고 v1.2에서 `FileJobs.kt` 줄을 c39a97e로 옮겼다. 그 밖의 파일(`FileJobConflictDialogFragment.kt`, `ftp/client/Client.kt`, `FileItem.kt`, `provider/common/`)은 두 커밋 사이에 바뀌지 않았다(`git diff --stat 55c521f c39a97e`). 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준. 줄과 함께 코드 앵커(식·주석)를 적는다. A2(네트워크 백엔드 속성 단계)는 B 파일과 겹치지 않는다 |
| 추적 REQ | B1: REQ-55-04(FTP 원본). B2: REQ-55-02, 04, 05, 06, 07 |
| 나눔 | **B1**(FTP 상대 링크 풀기, 어느 때나) → **B2**(따라가기·깊이·항목 단위 실패·충돌 판정, A1 머지 뒤 — HLD 2.4 `B1 → B2`, `A1 → A2 → B2`). PR을 따로 한다. #54(PR #68)는 머지됐다 |

## 0. 공통 규칙

1. **폰→폰은 바꾸지 않는다**(REQ-55-02): B2의 모든 새 동작은 `CopyKind.NETWORK`일 때만이다. 폰→폰 복사·이동의 순회(`Files.walkFileTree(source, visitor)`, 링크 안 따라감), `visitFileFailed`의 다시 던지기(작업 중단), 옵션 배열(`NOFOLLOW_LINKS` 있음), 충돌 판정(`attributesNoFollowLinks`)은 지금과 같다. 코드는 옮겨도(함수로 떼기) 동작은 같아야 한다.
2. **분류는 A1의 것을 쓴다**: B2는 분류하지 않는다. A1이 만든 `CopyKind`와 `classifyCopy(Path, Path)`(`filejob/CopyClassification.kt`, LLD-A 2.1·2.4)의 결과를 받아 쓴다. B2가 기대는 A1 심볼은 4절 F7에 모았다.
3. **깊이**(SRS 1.5 "링크 깊이", Q2 안 A): 복사 시작 항목에서 지금 폴더까지 오는 길에 따라간 **폴더 링크**의 수. 시작 항목 자신이 링크면 1부터다. 상한 `MAX_LINK_DEPTH = 8` 한 곳. 깊이 9가 되는 폴더 링크는 따라가지 않고 그 항목만 실패한다. 파일 링크는 깊이를 세지 않는다(4절 F1).
4. **순회 오류는 둘로 나눈다**(HLD D10): ① 깊이 초과·라이브러리 순환(`FileSystemLoopException`) — 건너뛰기·모두 건너뛰기·취소(다시 시도 없음). ② 그 밖의 순회 실패(폴더 목록·속성 읽기 실패, 목록 도중 실패) — 다시 시도·건너뛰기·모두 건너뛰기·취소. 두 창 모두 지금의 복사 오류 창 문구(`file_job_copy_error_*`)와 같은 '모두 건너뛰기' 상태(`ActionAllInfo.skipCopyMoveError`)를 쓴다(4절 F4). 새 문자열 없음(HLD D6).
5. **다시 시도는 반복문**(HLD 검수 지적): 같은 항목의 다시 시도가 또 실패하면 같은 함수 안의 `while`로 다시 묻는다. 다시 순회 안에서 그 항목 자신의 실패는 오류 창을 띄우지 않고 결과(`ItemWalkFailure`)로 바깥 반복문에 돌려준다. 재귀는 '더 깊은 다른 항목'의 실패일 때만 생기며, 깊이는 폴더 깊이로 묶인다(재시도 횟수와 무관).

**바뀌지 않는 것**: `provider/` 전부(B1의 FTP `Client.kt` 제외), 이동 경로(`MoveFileJob`, `copyForMove`, `moveByCopy` — `followLinks = false`), 압축하기·삭제 등 다른 작업의 `scan`, 문자열·레이아웃, `FileJobErrorDialogFragment`.

## 1. 변경 파일

### B1
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `provider/ftp/client/Client.kt` | 수정 | `Client.Path`에 `resolveSibling(String)`, 새 `Client.Path.resolveSymbolicLinkTarget(String)`, `listFile`이 그것을 쓴다 (2.1–2.2) |
| `app/src/test/java/me/zhanghai/android/files/provider/ftp/client/FtpSymbolicLinkTargetTest.kt` | 신규 | 상대·절대·`..` 링크 대상 풀기 (3.1) |

파일 수 2.

### B2
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `filejob/NetworkCopyWalk.kt` | 신규 | `MAX_LINK_DEPTH`, `LinkWalkState`, `LinkDepthExceededException`, `ItemWalkFailure`, `retryFailedWalk`, `CopyErrorDecision`, `decideCopyError` — 순수 함수·상태 (2.3–2.7) |
| `filejob/FileJobs.kt` | 수정 | `scan`(복사용), `CopyFileJob`의 순회, 오류 처리 추출, `copy`·`copyOrMove`의 `followLinks`, 충돌 판정, `showConflictDialog`, `open`·`save`, `TransferInfo` 상한, 몇 클래스의 가시성 (2.8–2.16) |
| `filejob/FileJobConflictDialogFragment.kt` | 수정 | `Args.isMerge`, `getTitle`·`getMessage`가 받은 값을 쓴다 (2.17) |
| `app/src/test/java/me/zhanghai/android/files/filejob/NetworkCopyWalkTest.kt` | 신규 | 깊이·재개 상태, 다시 시도 반복문, `ActionAllInfo`와의 관계, 예외 문구, 진행률 상한 (3.2) |

파일 수 4(HLD 2.3 나눔 표와 같다).

## 2. 함수 명세

### B1

#### 2.1 `Client.Path.resolveSibling(other: String): Client.Path` (B1, 인터페이스에 추가)
```kotlin
interface Path {
    val authority: Authority
    val remotePath: String
    fun resolve(other: String): Path
    fun resolveSibling(other: String): Path
}
```
- **추적**: HLD 2.3 "FTP 링크 풀기 보정" / REQ-55-04
- **책임**: 링크의 부모 폴더 기준으로 경로를 푼다.
- **입력·전제**: 구현은 `FtpPath` 하나다(`provider/ftp/FtpPath.kt:29`). `FtpPath`는 `AbstractPath.resolveSibling(other: String): T`(`provider/common/AbstractPath.kt:30`, `T = FtpPath`)를 이미 가진다 — `resolve(other: String)`(같은 파일 24)이 지금 `Client.Path.resolve`를 채우는 것과 같은 방식이라 `FtpPath.kt`는 고치지 않는다.
- **출력**: `AbstractPath.resolveSibling(Path)`(27-28)대로 `parent?.resolve(other) ?: other`. `other`가 절대 경로면 `other` 그대로(`ByteStringListPath.resolve`, `provider/common/ByteStringListPath.kt:158-160`). `..`·`.`는 정규화하지 않는다(같은 파일 168 `segments + other.segments`) — 서버가 푼다(링크의 부모가 링크일 수 있어 글자 그대로 정규화하면 틀릴 수 있다).

#### 2.2 `internal fun Client.Path.resolveSymbolicLinkTarget(target: String): Client.Path` (B1, 신규) + `Client.listFile` (수정)
- **추적**: HLD 2.3 "FTP 링크 풀기 보정", SRS 7절 / REQ-55-04
- **책임**: FTP 링크 대상 문자열을 링크가 있는 폴더 기준으로 푼다(POSIX 링크 규칙).
- **알고리즘**:
  ```kotlin
  // A relative symbolic link target is relative to the directory containing the link (#55 B1).
  internal fun Client.Path.resolveSymbolicLinkTarget(target: String): Client.Path =
      resolveSibling(target)
  ```
  `Client.kt` 최상위(파일 끝, `object Client` 밖)에 둔다 — JVM 시험에서 `object Client`의 초기화(`TIMESTAMP_FORMATTER` 등)를 타지 않게.
  `listFile`(55c521f 257-265)의 `val target = path.resolve(targetString)`(263)을 `val target = path.resolveSymbolicLinkTarget(targetString)`으로 바꾼다. 그 밖(한 단계만 푸는 것, 264 `listFileNoFollowLinks(target, false)`)은 그대로.
- **오류**: 없음(경로 계산만). 풀린 경로가 없으면 지금처럼 `listFileNoFollowLinks`가 서버 응답으로 실패한다.
- **영향(탐색 포함, HLD 2.3 B1)**: `listFile(path, false)`를 부르는 곳 — 속성 보기 `FtpFileAttributeView.readAttributes`(`provider/ftp/FtpFileAttributeView.kt:23`, 탐색 목록의 `loadFileItem`이 링크 대상 속성을 읽을 때), `FtpFileSystemProvider.checkAccess`(`provider/ftp/FtpFileSystemProvider.kt:376`), 복사·이동의 원본 속성(`FtpCopyMove.kt:25`, 145, `noFollowLinks == false`일 때) — 이 모두가 상대 링크를 바르게 푼다. 지금은 `/d/link` → `b`가 `/d/link/b`로 풀려 실패하고, 탐색 화면에서는 깨진 링크 표시가 된다(`file/FileItem.kt:56-61`이 실패를 `null`로 받음). B2 전에는 복사가 `NOFOLLOW_LINKS`라 복사 동작은 같다.
- **구현 순서(수정 전 실패 확인)**: (1) 함수를 **지금 동작 그대로**(`resolve(target)`) 떼어 `listFile`이 쓰게 한다 → (2) 3.1 테스트 → 실행해 U-B1-01·03·04 **실패** 확인 → (3) 본문을 `resolveSibling(target)`로 바꾸고 2.1을 더함 → 통과 확인. 두 출력 끝부분을 PR 코멘트에 붙인다.

### B2 — 순수 부분 (`filejob/NetworkCopyWalk.kt`, 신규)

파일 머리 KDoc: `// Pure parts of network copies (#55 B2): link depth, resuming a walk, and error decisions.`

#### 2.3 `internal const val MAX_LINK_DEPTH = 8` + `internal class LinkDepthExceededException(file: String) : FileSystemLoopException(file)`
- **추적**: HLD 2.3 "링크 깊이 계산"·"상한 8은 한 곳의 상수", D6 / REQ-55-06
- **책임**: 깊이 상한 한 곳, 깊이 초과를 라이브러리 순환과 같은 예외 종류로 알린다.
- **알고리즘**:
  ```kotlin
  /** Thrown for a directory link that would exceed [MAX_LINK_DEPTH] (#55 REQ-55-06). */
  internal class LinkDepthExceededException(file: String) : FileSystemLoopException(file) {
      override val message: String
          get() = "${super.message}: more than $MAX_LINK_DEPTH levels of symbolic links"
  }
  ```
  오류 창 본문은 `e.toString()`(`FileJobs.kt:1486-1492`의 `file_job_copy_error_message_format` 인자)이고 `Throwable.toString()`은 `getLocalizedMessage()` → `getMessage()`를 쓰므로 창에 "…LinkDepthExceededException: /a/l/l/…: more than 8 levels of symbolic links"가 보인다(REQ-55-06 "깊이 제한 때문임을 알 수 있어야"). 영어 고정 문구 — 예외 문자열은 지금도 번역하지 않는다(D6).
- **기존 코드와의 관계**: `java8.nio.file.FileSystemLoopException`은 `public class`(final 아님, retrofile 1.2.0 `javap` 확인), 생성자 `(String)` 하나. `FileSystemException.getMessage()`는 `open`이다.

#### 2.4 `internal class LinkWalkState(private val baseDepth: Int = 0)`
```kotlin
/**
 * The directories a following-links walk is in (#55 B2): their link depths and, for resuming a
 * directory whose listing failed midway, the children already visited. Not thread safe.
 */
internal class LinkWalkState(private val baseDepth: Int = 0) {
    private class Frame(val depth: Int, val visitedChildren: MutableSet<Path>)
    private val frames = ArrayList<Frame>()

    val currentDepth: Int get() = frames.lastOrNull()?.depth ?: baseDepth
    fun depthOf(isSymbolicLink: Boolean): Int = if (isSymbolicLink) currentDepth + 1 else currentDepth
    fun isExceeded(depth: Int): Boolean = depth > MAX_LINK_DEPTH
    fun markVisited(path: Path): Boolean = frames.lastOrNull()?.visitedChildren?.add(path) ?: true
    fun enter(depth: Int, visitedChildren: Set<Path> = emptySet()) {
        frames += Frame(depth, visitedChildren.toMutableSet())
    }
    fun exit(): ExitedDirectory {
        check(frames.isNotEmpty()) { "No directory to exit" }
        val frame = frames.removeAt(frames.lastIndex)
        return ExitedDirectory(frame.depth, frame.visitedChildren)
    }
}

internal class ExitedDirectory(val depth: Int, val visitedChildren: Set<Path>)
```
- **추적**: HLD 2.3 "링크 깊이 계산", 오류 처리 ③ / REQ-55-06
- **책임**: 깊이를 **들어갈 때만** 쌓고 `postVisitDirectory`에서 뺀다(HLD 2.3 "깊이를 쌓는 것은 … `CONTINUE`만"). 목록 도중 실패한 폴더를 다시 순회할 때 이미 방문한 자식을 알려 준다.
- **입력·전제**: 순회 한 번(시작 항목 하나)의 지역 객체, 파일 작업 스레드 하나(HLD 3.2). `baseDepth`는 다시 순회의 시작 깊이(부모 깊이, 0 이상).
- **출력·사후조건**:
  - `depthOf`·`isExceeded`는 상태를 바꾸지 않는다. 호출자는 `isExceeded`가 거짓이고 그 폴더로 **실제로 들어갈 때만**(`preVisitDirectory`가 `CONTINUE`를 돌려줄 때만) `enter`한다. `SKIP_SUBTREE`를 돌려주는 분기(초과, 복사 실패·건너뛰기 `copied == false`, 합치기 건너뛰기)는 `enter`하지 않는다 — 라이브러리가 그때 `postVisitDirectory`를 부르지 않으므로 짝이 맞는다.
  - `markVisited(path)`: 지금 폴더(맨 위 프레임)의 방문 집합에 더하고, 이미 있었으면 `false`. 프레임이 없으면(시작 항목) 언제나 `true`. 정상 순회에서는 한 폴더의 목록에 같은 경로가 두 번 나오지 않으므로 늘 `true`이고, 재개(`enter(depth, visitedChildren)`)한 폴더에서만 이미 방문한 자식에 `false`가 된다.
  - `exit()`: 맨 위 프레임을 빼고 그 깊이·방문 집합을 돌려준다. 비었으면 `IllegalStateException`(짝이 어긋난 버그를 숨기지 않는다).
- **오류**: `exit()`의 `check`뿐.
- **알고리즘**: 위 코드. `kotlin.collections.ArrayDeque.removeLast()` 대신 `ArrayList.removeAt(lastIndex)`를 쓴다(JDK 21 `List.removeLast`와 이름이 겹치는 Android 호환 문제를 피한다).
- **기존 코드와의 관계**: 새 클래스. 같은 일을 하는 기존 코드 없음.

#### 2.5 `internal class ItemWalkFailure(val error: IOException, val enteredDepth: Int?, val visitedChildren: Set<Path>)`
- **추적**: HLD 2.3 오류 처리 ②·③, D10 / REQ-55-05
- **책임**: 순회의 시작 항목 자신이 실패했음을 바깥 반복문에 돌려주는 값.
- **필드**: `error` — 창에 보일 예외. `enteredDepth` — 그 폴더에 **이미 들어가 대상 폴더를 만든 적이 있으면** 그 깊이, 아니면 `null`. ③(목록 도중 실패, `postVisitDirectory(폴더, 예외)`)이면 들어갔던 깊이다. ②(들어가기 전 실패, `visitFileFailed`)는 앞선 ③의 재개 중이면 그 값을 **이어받고**(2.5a, 검수 M1), 아니면 `null`. `visitedChildren` — 이미 방문한 자식(같은 규칙으로 이어받음, 없으면 빈 집합).
- **`val isCountedOnSkip: Boolean get() = enteredDepth == null`**: 건너뛸 때 진행률에서 한 항목을 뺄지(검수 m1). 들어간 적 있는 폴더는 대상 폴더를 이미 만들었고 `copyOrMove`가 옮긴 수에 셌으므로 빼지 않는다. 들어간 적 없는 항목은 집계가 한 항목(크기 0)으로 셌으므로(2.10) 뺀다.

#### 2.5a `internal fun startFailureBeforeEntering(error: IOException, resume: ItemWalkFailure?): ItemWalkFailure` + `internal fun LinkWalkState.enterIfResumedStart(directory: Path, start: Path, resume: ItemWalkFailure?): Boolean` (B2, 신규, 순수 — 검수 M1·m4)
- **추적**: HLD 2.3 오류 처리 ②·③ / REQ-55-05, 검수 M1
- **책임**: 방문자의 재개 판단을 순수 함수로 뗀다(JVM 시험, 3.2 U-B25–27).
- **알고리즘**:
  ```kotlin
  /**
   * The failure of a walk start that failed before being entered. A start being resumed (#55 ③)
   * keeps what the previous failure knew, so the next retry still doesn't copy it again.
   */
  internal fun startFailureBeforeEntering(error: IOException, resume: ItemWalkFailure?): ItemWalkFailure =
      ItemWalkFailure(error, resume?.enteredDepth, resume?.visitedChildren ?: emptySet())

  /** Enters [directory] without copying it again if it is a resumed start; false otherwise. */
  internal fun LinkWalkState.enterIfResumedStart(
      directory: Path, start: Path, resume: ItemWalkFailure?
  ): Boolean {
      val depth = resume?.enteredDepth ?: return false
      if (directory != start) {
          return false
      }
      enter(depth, resume.visitedChildren)
      return true
  }
  ```
- **M1의 경우**: ③으로 실패한 폴더 `D`(깊이 3, 자식 `a` 방문)를 다시 시도 → 다시 순회에서 `D`의 목록을 아예 열지 못함(라이브러리가 `preVisitDirectory` 전에 `visitFileFailed(D)`) → `startFailureBeforeEntering(e, resume)`이 깊이 3·`{a}`를 이어받는다 → 다음 다시 시도에서 `enterIfResumedStart`가 참이 되어 `D`를 `copy`로 다시 만들지 않고 `a`도 건너뛴다. v1.1은 여기서 `ItemWalkFailure(e, null, emptySet())`를 만들어 재개 정보를 잃었다.
- 시작 항목이 아닌 항목의 ② 실패는 `startFailureBeforeEntering(exception, null)`(= 들어간 적 없음).

#### 2.6 `internal inline fun retryFailedWalk(first: ItemWalkFailure, ask: (ItemWalkFailure) -> Boolean, rewalk: (ItemWalkFailure) -> ItemWalkFailure?): Boolean`
- **추적**: HLD 2.3 오류 처리 ②·③, D10, HLD 검수 지적("연속 실패는 재귀가 아니라 반복", "다시 순회 결과를 바깥에 넘김") / REQ-55-05
- **책임**: 한 항목의 순회 실패를 묻고, 다시 시도면 그 항목부터 다시 순회하고, 그 항목 자신이 또 실패하면 **같은 반복문에서** 다시 묻는다.
- **입력·전제**: `ask(failure)` — 참이면 다시 시도, 거짓이면 건너뜀(호출자가 진행률을 이미 정리함). 취소면 `ask`가 예외를 던진다. `rewalk(previous)` — 그 항목부터 새로 순회하고, 그 항목 **자신**이 또 실패하면 새 `ItemWalkFailure`를, 지나갔으면(복사·건너뛰기·하위 항목 실패 처리 포함) `null`을 돌려준다. 바로 앞 실패(`previous`)를 받아 ③의 재개 정보를 이어 간다.
- **출력**: 참 = 그 항목을 순회했다, 거짓 = 건너뛰었다.
- **알고리즘**:
  ```kotlin
  var failure = first
  while (true) {
      if (!ask(failure)) {
          return false
      }
      failure = rewalk(failure) ?: return true
  }
  ```
- **오류**: `ask`·`rewalk`의 예외(취소 `InterruptedIOException`, 그 밖의 `IOException`)를 그대로 전한다.

#### 2.7 `internal enum class CopyErrorDecision { RETRY, SKIP, CANCEL }` + `internal inline fun decideCopyError(actionAllInfo: ActionAllInfo, canRetry: Boolean, tryUserAction: () -> Boolean, showDialog: () -> ErrorResult): CopyErrorDecision`
- **추적**: HLD 2.3 "오류 처리(추출)", D10 / REQ-55-05, 06
- **책임**: 지금 `copyOrMove`의 일반 `IOException` 처리(`FileJobs.kt:1464-1519`)의 판단 부분. 복사 오류와 순회 오류가 같은 '모두 건너뛰기' 상태를 쓴다.
- **알고리즘**(지금 코드의 순서 그대로):
  ```kotlin
  if (actionAllInfo.skipCopyMoveError) {
      return CopyErrorDecision.SKIP
  }
  if (canRetry && tryUserAction()) {
      return CopyErrorDecision.RETRY
  }
  val result = showDialog()
  return when (result.action) {
      FileJobErrorAction.POSITIVE -> {
          check(canRetry) { "No positive button without retry" }
          CopyErrorDecision.RETRY
      }
      FileJobErrorAction.NEGATIVE -> {
          if (result.isAll) {
              actionAllInfo.skipCopyMoveError = true
          }
          CopyErrorDecision.SKIP
      }
      FileJobErrorAction.CANCELED -> CopyErrorDecision.SKIP
      FileJobErrorAction.NEUTRAL -> CopyErrorDecision.CANCEL
  }
  ```
- **지금 코드와 같음 확인**: 1466-1470(모두 건너뛰기면 묻지 않고 건너뜀) → 1471-1477(`UserActionRequiredException`이면 사용자 동작 뒤 다시 시도) → 1478-1498(창) → 1499-1518(긍정=다시 시도, 부정=건너뛰기(+모두), 닫힘=건너뛰기, 중립=취소). `canRetry == false`(① 깊이 초과·순환)일 때는 사용자 동작을 시도하지 않고(그 예외는 `UserActionRequiredException`이 아니다) 긍정 버튼이 없다.
- **가시성 변경**(`FileJobs.kt`): `ActionAllInfo`(662)와 `ErrorResult`(607)를 `private class` → `internal class`로. 생성자·필드는 그대로.

### B2 — `filejob/FileJobs.kt` (수정)

#### 2.8 `TransferInfo` 상한 + `ScanInfo`·`TransferInfo` 가시성 (482-537, 352-382)
- **추적**: HLD 6절 "다시 순회와 집계" / PM 지시(진행률 100% 초과 — 상한 설계)
- **책임**: 옮긴 수·크기가 집계보다 커지면 **전체를 늘려** 진행률이 100%를 넘지 않게 한다.
- **알고리즘**:
  - `incrementTransferredFileCount()`(494-496)·`addTransferredFile(path)`(498-507)의 `++transferredFileCount` 뒤: `if (transferredFileCount > fileCount) { fileCount = transferredFileCount }`.
  - `addToTransferredSize(size)`(524-526)·`addTransferredFile`의 `transferredSize += …` 뒤: `if (transferredSize > this.size) { this.size = transferredSize }`.
  - `skipFile(path)`(509-518)·`skipFileIgnoringSize()`(520-522)의 `--fileCount`·`size -= …` 뒤에도 같은 검사를 한다 — 건너뛰기가 전체를 옮긴 값 아래로 줄이지 못한다(검수 m1).
  - 비공개 메서드 하나로 묶는다: `private fun growTotalsToTransferred()` — `fileCount = max(fileCount, transferredFileCount)`, `size = max(size, transferredSize)`. 불변식: 언제나 `transferredFileCount <= fileCount`, `transferredSize <= size`.
- **이유**: 집계는 순회 실패 항목을 한 항목(크기 0)으로 센다(2.10). 그 항목을 다시 시도로 복사하면 실제 항목이 더 많다. 지금도 집계 뒤 바뀐 폴더에서 같은 일이 생긴다(HLD 6절). 표시 쪽에는 이미 부분 상한이 있다 — `FileJobProgress.percent`의 `coerceIn(0, 100)`(`filejob/FileJobProgress.kt:23-38`, 앱 안 작업 목록), "N/M"의 `coerceAtMost(fileCount)`(`FileJobs.kt:415-416`, 470) — 하지만 알림 진행 막대(`postTransferSizeNotification` 384-438, `postNotification(…, max, progress, …)`)는 `progress > max`를 그대로 넘기고 그 처리를 프레임워크에 맡긴다. 전체를 늘리면 진행 값이 최대값을 넘는 일이 없어지고 모든 표시(알림 막대·"N/M"·앱 안 목록)가 한 곳의 값으로 맞는다. 초과 뒤에는 전체가 옮긴 수와 같아지므로 막대는 100%에 머문다 — 얻는 것은 최대값 초과 방지다. 폰→폰에도 적용되지만 지금 100%를 넘던 경우에만 결과가 달라진다(표시만).
- **가시성**: JVM 시험을 위해 `ScanInfo`(352)·`TransferInfo`(482)를 `private class` → `internal class`. `TransferInfo` 생성자가 `ScanInfo`를 받으므로 둘 다 바꾼다. 필드·메서드 가시성은 그대로.
- **오류**: 없음.

#### 2.9 `scan` 복사용 — `FileJob.scan(sources: List<Path>, copyKinds: List<CopyKind>, notificationTitleRes: Int): ScanInfo` (신규 오버로드) + `FileJob.scanTree(source: Path?, scanInfo, titleRes)` (기존 본문을 떼어 냄)
- **추적**: HLD 2.3 "순회(집계)", D8, 3.1 2단계 / REQ-55-06, 07
- **책임**: 복사 작업의 집계를 시작 항목마다 그 분류로 한다. 네트워크면 링크를 따라가고 같은 깊이 규칙으로 멈춘다(집계가 끝나지 않는 일이 없다).
- **입력·전제**: `sources.size == copyKinds.size`(`require`). 파일 작업 스레드.
- **알고리즘**:
  1. 지금 `scan(sources: List<Path?>, …)`(270-300)의 `for` 안 `Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {…})`(273-296)를 **글자 그대로** `private fun FileJob.scanTree(source: Path?, scanInfo: ScanInfo, @PluralsRes notificationTitleRes: Int)`로 옮기고, 기존 `scan(sources)`는 `for (source in sources) { scanTree(source, scanInfo, notificationTitleRes) }`가 된다(삭제·이동·압축하기 등 다른 작업의 동작 그대로).
  2. 새 오버로드:
     ```kotlin
     val scanInfo = ScanInfo()
     for ((source, copyKind) in sources.zip(copyKinds)) {
         if (copyKind == CopyKind.NETWORK) {
             scanTreeFollowingLinks(source, scanInfo, notificationTitleRes)
         } else {
             scanTree(source, scanInfo, notificationTitleRes)
         }
     }
     postScanNotification(scanInfo, notificationTitleRes)
     return scanInfo
     ```
- **기존 코드와의 관계**: `scan(source: Path, …)`(303-305)·`scan(source, recursive, …)`(308-323)는 그대로(복사가 아닌 작업이 쓴다).

#### 2.10 `FileJob.scanTreeFollowingLinks(source: Path, scanInfo: ScanInfo, titleRes: Int)` (신규) + `FileJob.scanFailedPath(scanInfo, titleRes)` (신규) + `Path.isSymbolicLinkForLinkDepth(): Boolean` (신규)
- **추적**: HLD 2.3 "순회(집계)", D5, D8 / REQ-55-06, 07
- **알고리즘**:
  ```kotlin
  val state = LinkWalkState()
  Files.walkFileTree(
      source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE,
      object : SimpleFileVisitor<Path>() {
          override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
              val depth = state.depthOf(directory.isSymbolicLinkForLinkDepth())
              if (state.isExceeded(depth)) {
                  scanFailedPath(scanInfo, notificationTitleRes)
                  throwIfInterrupted()
                  return FileVisitResult.SKIP_SUBTREE
              }
              scanPath(attributes, scanInfo, notificationTitleRes)
              throwIfInterrupted()
              state.enter(depth)
              return FileVisitResult.CONTINUE
          }
          override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
              scanPath(attributes, scanInfo, notificationTitleRes)   // 지금과 같음
              throwIfInterrupted()
              return FileVisitResult.CONTINUE
          }
          override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
              scanFailedPath(scanInfo, notificationTitleRes)
              throwIfInterrupted()
              return FileVisitResult.CONTINUE
          }
          override fun postVisitDirectory(directory: Path, exception: IOException?): FileVisitResult {
              state.exit()
              return FileVisitResult.CONTINUE
          }
      }
  )
  ```
  - 초과·순환·목록 실패 항목은 **한 항목, 크기 0**으로 센다(HLD 2.3 "한 항목으로 세고", D8 — 창은 복사 단계에서 한 번만). 목록 도중 실패(`postVisitDirectory`의 예외)는 그때까지 센 자식만 남기고 조용히 넘어간다.
  - `scanFailedPath`: `scanInfo.incrementFileCount(); postScanNotification(scanInfo, notificationTitleRes)` — `scanPath`(325-333)에서 크기만 뺀 것.
  - `Path.isSymbolicLinkForLinkDepth()`(파일 안 `private`):
    ```kotlin
    // Unknown counts as a link, so that a walk can't go deeper than the limit by failing here.
    private fun Path.isSymbolicLinkForLinkDepth(): Boolean =
        try {
            readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isSymbolicLink
        } catch (e: IOException) {
            e.printStackTrace()
            true
        }
    ```
    폴더마다 링크를 따르지 않는 속성 읽기 1회(HLD D5, SRS 6절 "폴더마다 1회 허용"). 파일에는 하지 않는다.
- **오류**: 취소(`throwIfInterrupted`)만 밖으로 나간다. 그 밖의 순회 실패는 세고 계속한다.

#### 2.11 `CopyFileJob.run()` (807-828)
- **추적**: HLD 3.1 1·2단계 / REQ-55-01(A1), 06, 07
- **바꾸는 것**: A1은 `for` 안에서 `val copyKind = classifyCopy(source, targetDirectory)`를 한다(824, LLD-A 2.6). B2는 분류를 집계보다 **먼저** 한 번 한다:
  ```kotlin
  val copyKinds = sources.map { classifyCopy(it, targetDirectory) }
  val scanInfo = scan(sources, copyKinds, <지금의 제목 리소스>)
  …
  for ((source, copyKind) in sources.zip(copyKinds)) { … copyRecursively(source, target, isExtract, copyKind, transferInfo, actionAllInfo) … }
  ```
  분류 호출 수·위치(`CopyFileJob.run`·`open`·`save` 세 곳, LLD-A 3.3)는 그대로이고 결과를 집계와 순회가 함께 쓴다(SRS D1).

#### 2.12 `CopyFileJob.copyRecursively(...)` (830-867) + 새 멤버 `copyRecursivelyFollowingLinks`, `walkFollowingLinks`, `retryWalk`, `askWalkError`, `private inner class FollowingLinksCopyVisitor`, `private class CopyWalk`
- **추적**: HLD 2.3 "순회(복사)", "오류 처리" ①②③, 3.1 3단계 / REQ-55-04, 05, 06
- **책임**: 네트워크면 링크를 따라가며 복사하고, 깊이 초과·순환·순회 실패를 그 항목만의 실패로 처리한다. 폰→폰은 지금 그대로.
- **`copyRecursively`**: 맨 앞에
  ```kotlin
  if (copyKind == CopyKind.NETWORK) {
      copyRecursivelyFollowingLinks(CopyWalk(source, target, isExtract, transferInfo, actionAllInfo))
      return
  }
  ```
  그 뒤 지금의 `Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {…})`(839-866, A1이 `copyKind`를 넘김)는 **그대로**(폰→폰: 링크 안 따라감, `visitFileFailed`는 다시 던짐).
- **`CopyWalk`**(`CopyFileJob` 안 `private class`): 복사 시작 항목 하나의 문맥.
  ```kotlin
  private class CopyWalk(
      val source: Path, val target: Path, val isExtract: Boolean,
      val transferInfo: TransferInfo, val actionAllInfo: ActionAllInfo
  ) {
      val type: CopyMoveType get() = if (isExtract) CopyMoveType.EXTRACT else CopyMoveType.COPY
      fun targetOf(path: Path): Path = target.resolveForeign(source.relativize(path))
  }
  ```
  `targetOf`는 지금 방문자의 `target.resolveForeign(source.relativize(…))`(845, 855)와 같다. 다시 순회도 **복사 시작 항목**(`source`) 기준으로 대상 경로를 계산하므로 같은 자리에 복사된다(HLD 2.3 ②).
- **`copyRecursivelyFollowingLinks(walk)`**:
  ```kotlin
  val failure = walkFollowingLinks(walk, walk.source, LinkWalkState(), null) ?: return
  retryWalk(walk, walk.source, failure, 0)
  ```
- **`walkFollowingLinks(walk, start: Path, state: LinkWalkState, resume: ItemWalkFailure?): ItemWalkFailure?`**:
  ```kotlin
  val visitor = FollowingLinksCopyVisitor(walk, start, state, resume)
  Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, visitor)
  return visitor.startFailure
  ```
  반환 = `start` 자신의 실패(없으면 `null`) — 다시 순회 결과를 바깥 반복문에 넘기는 통로(HLD 검수 지적).
- **`retryWalk(walk, item: Path, first: ItemWalkFailure, baseDepth: Int)`**:
  ```kotlin
  retryFailedWalk(
      first,
      { failure -> askWalkError(walk, item, failure.error, true, failure.isCountedOnSkip) },
      { previous -> walkFollowingLinks(walk, item, LinkWalkState(baseDepth), previous) }
  )
  ```
  `baseDepth`는 실패한 항목의 **부모** 깊이(HLD 2.3 "깊이는 … 부모까지에서 이어진다"). 반환값(참/거짓)은 쓰지 않는다 — 어느 쪽이든 바깥 순회는 `CONTINUE`이고, 건너뛰기의 진행률 정리는 `askWalkError`가 했다.
- **`askWalkError(walk, item: Path, e: IOException, canRetry: Boolean, isCountedOnSkip: Boolean = true): Boolean`** — 참 = 다시 시도, 거짓 = 건너뜀:
  ```kotlin
  e.printStackTrace()
  return when (askCopyError(e, item, walk.targetOf(item), walk.type, canRetry, walk.actionAllInfo)) {
      CopyErrorDecision.RETRY -> true
      CopyErrorDecision.SKIP -> {
          if (isCountedOnSkip) {
              walk.transferInfo.skipFileIgnoringSize()
          }
          postCopyMoveNotification(walk.transferInfo, item, walk.type)
          false
      }
      CopyErrorDecision.CANCEL -> throw canceledByUser()
  }
  ```
  `skipFileIgnoringSize`(520-522): 집계가 그 항목을 크기 0으로 셌으므로(2.10) 크기는 빼지 않는다. 이미 들어간 폴더(③, `isCountedOnSkip == false`)는 수도 빼지 않는다(2.5, 검수 m1). ①(깊이 초과·순환)은 들어가기 전이라 기본값 `true`.
- **`private inner class FollowingLinksCopyVisitor(walk, start, state, resume) : SimpleFileVisitor<Path>()`**: (`private` — 비공개 `CopyWalk`를 받는다) `var startFailure: ItemWalkFailure? = null; private set`.

  | 메서드 | 알고리즘 |
  |---|---|
  | `preVisitDirectory(directory, attributes)` | (a) **재개한 시작 폴더**: `if (state.enterIfResumedStart(directory, start, resume)) return CONTINUE`(2.5a) — 이미 만든 대상 폴더라 `copy`를 다시 부르지 않는다(합치기 창이 다시 뜨지 않음, HLD 2.3 ③ "합치기로 이어 간다"). (b) `if (!state.markVisited(directory)) return SKIP_SUBTREE` — 재개 전에 이미 방문한 자식. (c) `val depth = state.depthOf(directory.isSymbolicLinkForLinkDepth())`; `if (state.isExceeded(depth)) { askWalkError(walk, directory, LinkDepthExceededException(directory.toString()), false); return SKIP_SUBTREE }` — ①, 쌓지 않음. (d) `val copied = copy(directory, walk.targetOf(directory), walk.isExtract, CopyKind.NETWORK, walk.transferInfo, walk.actionAllInfo)`; `throwIfInterrupted()`; `if (!copied) return SKIP_SUBTREE`(쌓지 않음); `state.enter(depth)`; `CONTINUE` |
  | `visitFile(file, attributes)` | `if (!state.markVisited(file)) return CONTINUE`; `copy(file, walk.targetOf(file), walk.isExtract, CopyKind.NETWORK, …)`; `throwIfInterrupted()`; `CONTINUE`. 대상 없는 링크는 라이브러리가 링크 자체의 속성으로 여기 넘긴다(retrofile `FileTreeWalker.getAttributes`가 따라간 읽기 실패 시 `NOFOLLOW_LINKS`로 다시 읽음, `javap` 확인) → `copyOrMove`(NOFOLLOW 없음)에서 백엔드가 따라간 속성을 읽다 실패 → 지금의 복사 오류 창(REQ-55-05, HLD 3.1) |
  | `visitFileFailed(file, exception)` | `if (!state.markVisited(file)) return CONTINUE`. `exception is FileSystemLoopException`(라이브러리 순환, `LinkDepthExceededException` 포함)이면 `askWalkError(walk, file, exception, false)` 후 `CONTINUE` — ①. 아니면 `file == start`면 `startFailure = startFailureBeforeEntering(exception, resume)` 후 `CONTINUE`(라이브러리는 시작 항목 실패 뒤 순회를 끝낸다. 재개 정보를 이어받는다 — 2.5a, 검수 M1); 아니면 `retryWalk(walk, file, startFailureBeforeEntering(exception, null), state.currentDepth)` 후 `CONTINUE` — ② |
  | `postVisitDirectory(directory, exception)` | `val exited = state.exit()`. `exception == null`이면 `CONTINUE`. 아니면(③, `DirectoryIteratorException`의 원인 — retrofile `FileTreeWalker`가 목록 도중 실패를 `END_DIRECTORY`의 예외로 넘김, `javap` 확인) `val failure = ItemWalkFailure(exception, exited.depth, exited.visitedChildren)`; `directory == start`면 `startFailure = failure`; 아니면 `retryWalk(walk, directory, failure, state.currentDepth)`(빼고 난 뒤 = 부모 깊이). `CONTINUE` |

- **입력·전제**: `start`는 복사 시작 항목이거나 그 아래 항목. `resume`은 `start`의 바로 앞 실패(첫 순회·②의 다시 시도면 `enteredDepth == null`).
- **출력·사후조건**: 순회가 끝나면 `state`의 프레임이 비어 있다(들어간 폴더마다 `postVisitDirectory`가 한 번). 시작 항목 자신의 실패만 `startFailure`로 남고, 다른 항목의 실패는 그 자리에서 처리됐다.
- **오류**: 취소(`canceledByUser()`, `throwIfInterrupted`)는 바깥으로 나가 작업이 끝난다(지금과 같음). `copy`가 던지는 예외는 지금처럼 `copyOrMove`가 처리하고, `InvalidFileNameException`(1455-1461)처럼 지금도 다시 던지는 예외는 지금처럼 작업을 끝낸다.
- **재귀의 범위**: `retryWalk` 안의 다시 순회에서 **더 깊은 다른 항목**이 실패하면 그 방문자가 다시 `retryWalk`를 부른다(중첩). 중첩 깊이는 폴더 깊이로 묶인다. 같은 항목의 연속 실패는 `retryFailedWalk`의 `while`이 맡는다(0절 5).
- **기존 코드와의 관계**: import 추가 `java.util.EnumSet`, `java8.nio.file.FileVisitOption`, `java8.nio.file.FileSystemLoopException`. 라이브러리가 목록을 못 열면 `preVisitDirectory` 전에 `visitFileFailed`를 부른다(retrofile `FileTreeWalker.visit`: `newDirectoryStream` 실패 → `ENTRY` 이벤트에 예외, `javap` 확인) — 그래서 ②의 다시 순회는 대상 폴더를 아직 만들지 않은 상태에서 시작한다.

#### 2.13 `FileJob.askCopyError(e: IOException, source: Path, target: Path, type: CopyMoveType, canRetry: Boolean, actionAllInfo: ActionAllInfo): CopyErrorDecision` (신규, `private`)
- **추적**: HLD 2.3 "오류 처리(추출)" / REQ-55-05
- **책임**: 지금 `copyOrMove`의 오류 창(1478-1498)을 띄우고 2.7로 판단한다.
- **알고리즘**:
  ```kotlin
  val targetParent = target.parent
  return decideCopyError(
      actionAllInfo, canRetry, { e is UserActionRequiredException && showUserAction(e) }
  ) {
      showErrorDialog(
          getString(type.getResourceId(R.string.file_job_copy_error_title_format, …extract…, …move…), getFileName(source)),
          getString(type.getResourceId(R.string.file_job_copy_error_message_format, …), getFileName(targetParent), e.toString()),
          getReadOnlyFileStore(target, e),
          true,
          if (canRetry) getString(R.string.retry) else null,
          getString(R.string.skip),
          getString(android.R.string.cancel)
      )
  }
  ```
  `canRetry == false`면 긍정 버튼이 없다(`FileJobErrorDialogFragment`가 `null` 문구 버튼을 만들지 않음 — 압축하기 오류 창 778-788과 같은 방식). '모두' 체크는 보인다(모두 건너뛰기). `showErrorDialog`의 `isPositiveRetry`(569)는 `null`이면 거짓이라 건너뛰기·취소는 지금처럼 '해결 안 됨'으로 기록된다(`isFileJobErrorResolved`).
- **`copyOrMove`의 `catch (e: IOException)`(1464-1519)**는 이렇게 바뀐다(동작 같음):
  ```kotlin
  } catch (e: IOException) {
      e.printStackTrace()
      when (askCopyError(e, source, target, type, true, actionAllInfo)) {
          CopyErrorDecision.RETRY -> { retry = true; continue }
          CopyErrorDecision.SKIP -> {
              transferInfo.skipFile(source)
              postCopyMoveNotification(transferInfo, source, type)
              return false
          }
          CopyErrorDecision.CANCEL -> throw canceledByUser()
      }
  }
  ```
  지금 코드에서 `targetParent`(1270 `val targetParent = target.parent`)는 **원래** `target`의 부모이고, 창 문구의 `target`은 이름 바꾸기로 바뀐 `target`(1351 `var target = target`)이다. 두 값의 부모는 같다(`resolveSibling`) — `askCopyError`가 바뀐 `target`의 부모를 써도 문구는 같다.

#### 2.14 `copy(...)` (952-963) + `copyOrMove(...)` (1260-)
- **추적**: HLD 2.3 "옵션 조립", "충돌 판정", D7, D9 / REQ-55-02, 04, 07
- **`copy`**: `copyOrMove(source, target, type, true, false, copyKind == CopyKind.NETWORK, copyKind == CopyKind.NETWORK, transferInfo, actionAllInfo)` — A1의 `skipAttributes` 다음에 `followLinks`.
- **`copyOrMove`**: 인자 `followLinks: Boolean`을 `skipAttributes` 뒤에 더한다. KDoc 한 줄: `// Only copies may follow links or skip attributes; moves keep both off (#55 Q4).` `copyForMove`(1236-1242)·`moveByCopy`(1250-1256)는 `followLinks = false`를 위치 인자로 더한다.
  - 옵션 조립(1359-1376): `this += LinkOption.NOFOLLOW_LINKS`(1360)를 `if (!followLinks) { this += LinkOption.NOFOLLOW_LINKS }`로. 나머지 순서는 그대로 — 폰→폰·이동의 배열은 지금과 같다.
  - 충돌 판정(1387-1396):
    ```kotlin
    val sourceIsDirectory = (if (followLinks) sourceFile.attributes else sourceFile.attributesNoFollowLinks).isDirectory
    val targetIsDirectory = (if (followLinks) targetFile.attributes else targetFile.attributesNoFollowLinks).isDirectory
    ```
    `FileItem.attributes`는 링크를 따라간 속성이고, 대상 없는 링크면 링크 자체의 속성이다(`file/FileItem.kt:36-37`, `loadFileItem` 56-61). 대상도 따라간다(HLD D9). 그 뒤 `isMerge`(1396)와 '모두 적용' 분기(1397-1410)는 그대로.
  - 창 호출(1411): `showConflictDialog(sourceFile, targetFile, type, isMerge)`.
- **출력·사후조건**: 네트워크 복사의 모든 `copyTo`(첫 시도·바꾸기·이름 바꾸기 다시 시도)에 `NOFOLLOW_LINKS`가 없다. 작업의 `isMerge`와 창의 버튼·제목·문구·#54 경고가 **같은 값**에서 나온다(REQ-55-07, D7).
- **#54와의 관계**: #54(PR #68)는 창에서 `binding.replaceWarningText.isVisible = !isMerge`(`FileJobConflictDialogFragment.kt:82`)로 경고를 보인다. B2 뒤 이 `isMerge`는 `Args.isMerge` = 작업의 판정이다. 폰→폰·이동은 판정이 지금과 같으므로 경고도 같다. 네트워크에서 폴더 링크 ↔ 폴더는 합치기가 되어 경고가 없다(SRS 5절, #54 D1과 맞음).

#### 2.15 `FileJob.showConflictDialog(sourceFile, targetFile, type, isMerge: Boolean): ConflictResult` (613-637)
- **추적**: HLD 2.3 "충돌 창", D7 / REQ-55-07
- **알고리즘**: 인자 `isMerge`를 더해 `FileJobConflictDialogFragment.Args(sourceFile, targetFile, type, isMerge) { action, name, all -> … }`, `getTitle(targetFile, isMerge, service)`, `getMessage(targetFile, type, isMerge, service)`(629-630). 백그라운드 알림(`BackgroundActivityStarter`의 제목·본문)도 같은 값을 쓴다.

#### 2.16 `open(...)` (1593-1623) + `save(...)` (1806-1821)
- **추적**: HLD 2.3 "순회(집계)" / REQ-55-06
- **바꾸는 것**: A1은 두 함수에서 `copy(…)` 호출 자리에서 `classifyCopy(…)`를 한다(`open` 1613-1615, `save` 1811-1813, LLD-A 2.6). B2는 그 값을 지역 변수로 먼저 구해 집계와 복사에 함께 쓴다:
  - `open`: `val cacheDirectory = …`가 `scan`보다 뒤에 있으므로(1600-1606 → 1607) `cacheDirectory` 계산을 `scan` 앞으로 옮기고 `val copyKind = classifyCopy(file, cacheDirectory)`; `scan(listOf(file), listOf(copyKind), <지금의 제목>)`; `copy(file, targetFile, isExtract, copyKind, …)`. `cacheDirectory.createDirectories()`는 그 자리(scan 뒤)에 둔다 — 순서를 바꾸는 것은 경로 계산뿐.
  - `save`: `val targetParent = target.parent`를 `scan` 앞으로, `val copyKind = classifyCopy(source, targetParent ?: target)`; `scan(listOf(source), listOf(copyKind), R.plurals.file_job_copy_scan_notification_title_format)`; `copy(source, target, false, copyKind, …)`.
- 둘 다 시작 항목 하나를 `copy`로 직접 복사한다(순회 없음). B2에서 달라지는 것은 집계가 링크를 따라가는 것과 `copyOrMove`의 `followLinks`뿐이다.

### B2 — `filejob/FileJobConflictDialogFragment.kt` (수정)

#### 2.17 `Args.isMerge`, `getTitle`, `getMessage`, `onCreateDialog`
- **추적**: HLD 2.3 "충돌 창", D7, 4절("`Args`에 합치기 여부 필드 하나 — 회전 때 복원") / REQ-55-07
- **바꾸는 것**:
  - `Args`(318-325): `val type: CopyMoveType` 다음에 `val isMerge: Boolean`(리스너는 마지막 그대로). `@Parcelize`가 `Boolean`을 저장하므로 창을 다시 만들 때(회전) 같은 값이 돌아온다.
  - `onCreateDialog`(70-147): `val isMerge = args.isMerge`(75). `getTitle(targetFile, isMerge, requireContext())`, `getMessage(targetFile, args.type, isMerge, requireContext())`(73-74). 그 아래 `isMerge`를 쓰는 곳(76 버튼, 82 #54 경고, 84-100 이름표)은 글자 그대로.
  - `companion object`: `getTitle(targetFile: FileItem, isMerge: Boolean, context: Context): String`, `getMessage(targetFile: FileItem, type: CopyMoveType, isMerge: Boolean, context: Context): String` — 본문의 `isMerge(sourceFile, targetFile)`(285, 299)를 인자로 바꾼다. 쓰지 않게 되는 `sourceFile` 인자는 뺀다. `private fun isMerge(...)`(311-315)는 지운다(창이 스스로 계산하는 곳이 없어진다, D7).
- **바뀌지 않는 것**: 버튼 정의·동작(`onDialogButtonClick`), `bindFileItem`(링크 배지·크기 표시), 이름 입력, `State`, `FileJobConflictDialogActivity`. #67(회전 때 입력한 이름 사라짐)은 범위 밖 — B2 뒤 같은 파일에서 따로 한다.

## 3. 테스트 설계

### 3.1 B1 — `provider/ftp/client/FtpSymbolicLinkTargetTest` (JVM, 신규)
시험용 `Client.Path`: 파일 안 `private class TestFtpPath(private val path: ByteStringListPath<*>) : Client.Path` — `authority`는 `throw UnsupportedOperationException()`, `remotePath = path.toString()`, `resolve(other) = TestFtpPath(path.resolve(other.toByteString()))`, `resolveSibling(other) = TestFtpPath(path.resolveSibling(other.toByteString()))`. 경로는 `filejob/TestPaths.path(...)`(`internal`, 같은 테스트 모듈)로 만들고 `ByteStringListPath`로 형변환한다 — `TestFileSystem.getPath(String)`은 지원하지 않으므로 `ByteString` 판(`ByteStringListPath.resolve(ByteString)`·`resolveSibling(ByteString)`, 172-174)을 쓴다. 두 판은 `AbstractPath`의 `String` 판과 같은 `resolve(Path)`·`resolveSibling(Path)`로 간다.

| 테스트 | 경우 | 기대 결과 | 수정 전(2.2 (1) 상태) |
|---|---|---|---|
| U-B1-01 `relativeTarget_resolvesAgainstLinkDirectory` | 링크 `/d/link`, 대상 `b` | `remotePath == "/d/b"` | **실패**(`/d/link/b`) |
| U-B1-02 `absoluteTarget_unchanged` | `/d/link`, `/x/y` | `/x/y` | 통과 |
| U-B1-03 `parentRelativeTarget_notNormalized` | `/d/link`, `../e/f` | `/d/../e/f` (서버가 푼다) | **실패**(`/d/link/../e/f`) |
| U-B1-04 `linkAtRoot` | `/link`, `b` | `/b` | **실패**(`/link/b`) |
| U-B1-05 `dotTarget` | `/d/link`, `./b` | `/d/./b` | 실패(`/d/link/./b`) |

### 3.2 B2 — `filejob/NetworkCopyWalkTest` (JVM, 신규)
경로는 `TestPaths.path(...)`. `ActionAllInfo()`·`ErrorResult(action, isAll)`·`ScanInfo()`·`TransferInfo(ScanInfo(), null)`는 2.7·2.8의 가시성 변경으로 만들 수 있다.

**깊이·재개 상태(2.4)**
| 테스트 | 경우 | 기대 결과 |
|---|---|---|
| U-B01 `noLinks_deepTree_neverExceeds` | 링크 아닌 폴더 100단 `enter(depthOf(false))` | 깊이 0, `isExceeded` 늘 거짓 |
| U-B02 `eightLinks_pass_ninthExceeds` | 링크 폴더 8단 `enter(depthOf(true))` | 8단째 깊이 8·초과 아님, 9번째 `depthOf(true) == 9`·`isExceeded` 참 |
| U-B03 `exit_restoresParentDepth` | 링크 3단 뒤 `exit()` 3회 | 깊이 3 → 2 → 1 → 0, `exit()`가 들어갈 때의 깊이를 돌려줌 |
| U-B04 `nonLinkDirectory_keepsDepth` | 깊이 2에서 링크 아닌 폴더 `enter` | 깊이 2 |
| U-B05 `skippedSubtree_siblingSeesParentDepth` | 깊이 8에서 링크 형제 `depthOf(true)`(초과, `enter` 안 함) 뒤 다음 형제 | 다음 형제 `depthOf(false) == 8`, `depthOf(true) == 9` (SKIP 뒤 누수 없음, HLD 2.3 회귀 테스트) |
| U-B06 `rewalk_continuesFromBaseDepth` | `LinkWalkState(5)`: `currentDepth`, `depthOf(true)`; `LinkWalkState(8).depthOf(true)` | 5, 6; 9·초과 |
| U-B07 `exit_withoutEnter_throws` | 새 상태에서 `exit()` | `IllegalStateException` |
| U-B08 `markVisited_resumeSkipsVisitedChildren` | `enter(1, setOf(/s/a, /s/b))` 뒤 `markVisited(/s/a)`, `markVisited(/s/c)`, 다시 `markVisited(/s/c)`; `exit().visitedChildren` | 거짓, 참, 거짓; `{a, b, c}` (연속 실패 때 집합이 이어짐) |
| U-B09 `markVisited_withoutFrame_true` | 프레임 없이 `markVisited(/s)` 두 번 | 둘 다 참(시작 항목) |

**다시 시도 반복문(2.6)**
| 테스트 | 경우 | 기대 결과 |
|---|---|---|
| U-B10 `skip_doesNotRewalk` | `ask`가 거짓 | 거짓, `rewalk` 0회 |
| U-B11 `consecutiveFailures_loopNotRecurse` | `rewalk`가 3번 새 실패 뒤 `null`, `ask`는 늘 참. `rewalk` 안에서 '실행 중' 표시를 켜고 `ask`가 그 표시가 꺼져 있음을 단언 | 참, `ask` 4회·`rewalk` 3회, 단언 실패 없음(같은 항목의 재시도가 `rewalk` 안에서 다시 묻지 않는다 = 재귀 아님) |
| U-B12 `rewalk_receivesPreviousFailure` | 첫 실패 `ItemWalkFailure(e1, 2, {a})`, 둘째 `(e2, 2, {a, b})` | 첫 `rewalk`가 첫 실패를, 둘째가 둘째 실패를 받음; `ask`가 첫·둘째 실패를 순서대로 받음 |
| U-B13 `cancel_propagates` | `ask`가 둘째 호출에서 `InterruptedIOException` | 예외 전파, `rewalk` 1회 |

**오류 판단과 `ActionAllInfo`(2.7)**
| 테스트 | 경우 | 기대 결과 |
|---|---|---|
| U-B14 `skipAll_set_skipsWithoutDialog` | `skipCopyMoveError = true` | `SKIP`, `showDialog`·`tryUserAction` 0회 |
| U-B15 `negativeAll_setsSkipAll` | 창 `NEGATIVE`, `isAll = true` | `SKIP`, `skipCopyMoveError == true` |
| U-B16 `negative_withoutAll_keepsFlag` | `NEGATIVE`, `isAll = false` | `SKIP`, 플래그 거짓 |
| U-B17 `actions_mapLikeCopyOrMove` | `POSITIVE`/`CANCELED`/`NEUTRAL`(canRetry 참) | `RETRY`/`SKIP`/`CANCEL` |
| U-B18 `userAction_retriesBeforeDialog` | canRetry 참, `tryUserAction` 참 | `RETRY`, 창 0회 |
| U-B19 `noRetry_noUserAction_noPositive` | canRetry 거짓: `tryUserAction` 호출 수; 창 `POSITIVE` | 0회; `IllegalStateException` |
| U-B20 `walkSkipAll_appliesToLaterItems` | 같은 `ActionAllInfo`로 (1) `retryFailedWalk`의 `ask`를 `decideCopyError(…) == RETRY`로 만들고 창이 `NEGATIVE`+모두 → (2) 다른 항목의 `retryFailedWalk`, (3) 복사 오류(`canRetry` 참) 판단 | (1) 거짓, (2) 창 없이 거짓·`rewalk` 0회, (3) 창 없이 `SKIP` — 순회 오류의 '모두 건너뛰기'가 뒤의 순회·복사 오류에 같이 적용된다(역방향은 U-B14) |

**예외·진행률(2.3, 2.8)**
| 테스트 | 경우 | 기대 결과 | 수정 전 |
|---|---|---|---|
| U-B21 `depthExceeded_isLoopAndNamesLimit` | `LinkDepthExceededException("/a/l")` | `FileSystemLoopException`이다; `toString()`에 `/a/l`과 `"8"`이 있다 | (새 클래스) |
| U-B22 `transferInfo_countNeverExceedsTotal` | `ScanInfo` 1개 항목으로 만든 `TransferInfo`에 `incrementTransferredFileCount()` 3회 | `fileCount == 3`, `transferredFileCount == 3` | **실패**(`fileCount == 1`) |
| U-B23 `transferInfo_sizeNeverExceedsTotal` | 크기 10으로 집계, `addToTransferredSize(25)` | `size == 25`; `FileJobProgress(SIZE, …).percent == 100` | **실패**(`size == 10`) |
| U-B24 `transferInfo_withinTotals_unchanged` | 3개·30으로 집계, 1개·10 옮김 | `fileCount == 3`, `size == 30`(폰→폰 정상 경우 회귀 방지) | 통과 |
| U-B28 `transferInfo_skipNeverBelowTransferred` | 2개로 집계, 2개 옮김 뒤 `skipFileIgnoringSize()` | `fileCount == 2`(옮긴 수 아래로 줄지 않음, 검수 m1) | **실패**(`fileCount == 1`) |

**재개 판단(2.5, 2.5a — 검수 M1·m1·m4)**
| 테스트 | 경우 | 기대 결과 | 수정 전(2.5a를 v1.1처럼 `ItemWalkFailure(e, null, emptySet())`로 둔 상태) |
|---|---|---|---|
| U-B25 `resumedStart_failingBeforeEntering_keepsResume` | ③ 실패 `r = ItemWalkFailure(e1, 3, {/s/d/a})` → `f = startFailureBeforeEntering(e2, r)` → 새 `LinkWalkState(2)`에서 `enterIfResumedStart(/s/d, /s/d, f)`, 이어서 `markVisited(/s/d/a)` | `f.error == e2`, `f.enteredDepth == 3`, `f.visitedChildren == {a}`; `enterIfResumedStart` 참, `currentDepth == 3`; `markVisited(a)` 거짓(다시 복사하지 않음) | **실패**(`enteredDepth == null`, `enterIfResumedStart` 거짓) |
| U-B26 `enterIfResumedStart_onlyForEnteredStart` | `resume == null`; `enteredDepth == null`; `directory != start` | 셋 다 거짓, 프레임 없음(`currentDepth == baseDepth`, `exit()`가 `IllegalStateException`) | 통과 |
| U-B27 `isCountedOnSkip` | `startFailureBeforeEntering(e, null)`; ③ 실패(`enteredDepth` 2); U-B25의 `f` | 참; 거짓; 거짓 | U-B25의 `f`에서 **실패**(참) |

`ScanInfo`에 항목을 넣는 방법: `ScanInfo`의 `incrementFileCount()`·`addToSize()`(364-370)는 공개 메서드다.

구현 순서: (1) 2.7·2.8의 가시성만 바꾸고 3.2의 U-B22–24·28을 먼저 써서 **U-B22·23·28 실패 확인** → (2) 2.8 상한 → 통과 확인 → (3) 2.4·2.5를 쓰고 2.5a를 **v1.1 동작**(`ItemWalkFailure(error, null, emptySet())`)으로 둔 채 U-B25–27을 써서 **U-B25·27 실패 확인** → (4) 2.5a를 이 문서대로 → 통과 확인 → (5) 나머지(2.3, 2.6–2.7, 2.9–2.17)와 U-B01–21. 두 출력의 끝부분을 PR 코멘트에 붙인다(PROCESS_ROLES 11-1 5번). 순회·충돌 결선은 실제 백엔드(JNI·네트워크)가 필요해 JVM에서 시험하지 않는다 — 코드 검사(3.3)와 수동(3.4).

### 3.3 실행·코드 검사
- JVM: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "me.zhanghai.android.files.provider.ftp.client.*" --console=plain`(B1), `--tests "me.zhanghai.android.files.filejob.*"`(B2) 후 전체 `testDebugUnitTest`, 그리고 `assembleDebug`.
- 코드 검사(검수자):
  - B1: `listFile`이 `resolveSymbolicLinkTarget`을 쓰고, `FtpPath.kt`는 바뀌지 않았다.
  - B2 폰→폰 불변(REQ-55-02): `copyRecursively`의 폰→폰 분기, `scanTree`, `copyForMove`·`moveByCopy`의 옵션 배열, 폰→폰 충돌 판정(`attributesNoFollowLinks`)이 c39a97e(A1 머지 뒤)와 같은 동작이다. `NOFOLLOW_LINKS`가 빠지는 곳은 `followLinks == true`(= `copy`의 `CopyKind.NETWORK`)뿐.
  - B2 깊이(HLD 2.3): `state.enter`는 `CONTINUE`를 돌려주는 분기에만 있고, 들어간 폴더마다 `postVisitDirectory`가 `exit` 한 번. 집계(2.10)와 복사(2.12)가 같은 `LinkWalkState`·`MAX_LINK_DEPTH`·`isSymbolicLinkForLinkDepth`를 쓴다.
  - B2 재개(검수 M1): 방문자의 `visitFileFailed`에서 시작 항목의 실패가 `startFailureBeforeEntering(exception, resume)`(재개 정보를 이어받음)이고, `preVisitDirectory` (a)가 `enterIfResumedStart`를 쓴다. ③의 건너뛰기는 `isCountedOnSkip`이 거짓이라 `skipFileIgnoringSize`를 부르지 않는다.
  - B2 반복문: 같은 항목의 다시 시도는 `retryFailedWalk`의 `while`뿐이고, 다시 순회 안에서 시작 항목 자신의 실패는 창을 띄우지 않고 `startFailure`로 돌아간다.
  - B2 충돌(REQ-55-07): 작업의 `isMerge`가 `Args.isMerge`·`getTitle`·`getMessage`·알림에 그대로 가고, 창 안에 `isMerge`를 계산하는 코드가 없다. #54 경고(`replaceWarningText.isVisible = !isMerge`)가 그 값을 쓴다.

### 3.4 수동 시험 (에뮬레이터·서버)
| 시험 | 조각 | 환경 | 절차 | 기대 |
|---|---|---|---|---|
| M-B1 FTP 상대 링크 탐색 | B1 | FTP 서버 | 서버에 `d/b`(파일)와 `d/link -> b`(상대) → 앱에서 `d` 열기 | `link`가 깨진 링크 배지가 아니라 파일로 보이고 크기가 `b`와 같다 |
| M-B2 폰 링크 → NAS | B2 | 에뮬레이터 + SFTP/SMB/FTP/WebDAV | `adb shell 'mkdir -p /sdcard/Download/s/d && echo x > /sdcard/Download/s/f && ln -s f /sdcard/Download/s/lf && ln -s d /sdcard/Download/s/ld'`(앱 권한으로 만들 수 없으면 루트 셸) → `s`를 NAS로 복사 | NAS에 `lf`(일반 파일, 내용 `x`)·`ld`(폴더). FTP·WebDAV에서 "Cannot copy symbolic links"가 나지 않는다(REQ-55-04) |
| M-B3 NAS 링크 → 폰 | B2 | SFTP·SMB 서버 | 서버의 파일·폴더 링크를 폰으로 | 폰에 일반 파일·폴더(REQ-55-04) |
| M-B4 대상 없는 링크 | B2 | 에뮬레이터 + 서버 | `ln -s nowhere /sdcard/Download/s/broken` 추가 → `s`를 NAS로 | `broken`에서 복사 오류 창(다시 시도·건너뛰기·취소), 건너뛰기 뒤 나머지 복사됨(REQ-55-05) |
| M-B5 깊이 8 | B2 | SFTP·FTP·WebDAV 서버 | 서버 폴더 `p`에 `p/l -> .`(부모를 가리킴) → `p`를 폰으로 | 작업이 끝난다. `p/l`이 8겹까지 복사되고 9번째에서 오류 창(긍정 버튼 없음, 본문에 "more than 8 levels") → 건너뛰기 뒤 끝(REQ-55-06) |
| M-B6 라이브러리 순환 | B2 | 에뮬레이터 + 서버 | `ln -s . /sdcard/Download/s/self` → `s`를 NAS로 | 첫 `self`에서 `FileSystemLoopException` 오류 창(긍정 버튼 없음), 건너뛰기 뒤 끝(로컬 `fileKey`) |
| M-B7 집계 | B2 | M-B5와 같음 | 복사 시작 직후 알림 | 집계가 끝나고(멈추지 않음) 진행률이 100%를 넘지 않는다 |
| M-B8 합치기 창 | B2 | 에뮬레이터 + 서버 | NAS 대상 폴더에 `ld`라는 폴더가 있을 때 M-B2의 `ld`를 복사 → 창 확인 → 회전 | '합치기' 버튼·제목·문구, #54 경고 없음, 회전 뒤에도 같음. 합치기 → `ld` 안에 내용(REQ-55-07) |
| M-B9 폰→폰 회귀 | B2 | 에뮬레이터 | M-B2의 `s`를 `/sdcard/Documents`로 복사 → `adb shell ls -l /sdcard/Documents/s` | `lf`·`ld`가 링크 그대로(REQ-55-02). 같은 이름 링크가 대상에 있을 때 '교체' 창과 #54 경고(지금과 같음) |
| M-B10 목록 실패 다시 시도 | B2 | SFTP 서버 | 원본 하위 폴더 `chmod 000` → 폰으로 복사 → 오류 창에서 권한을 되돌린 뒤 '다시 시도' | 그 폴더가 같은 자리에 복사되고 합치기 창이 뜨지 않는다. 되돌리지 않고 다시 시도하면 같은 창이 다시 뜬다(반복) |

서버가 없으면 M-B1–B8·M-B10은 PR에 "미확인"으로 적는다(SRS 6절). 오너 기기에는 설치하지 않는다(사전 승인 필요). M-B10의 ③(목록 **도중** 실패)은 재현 방법이 없어 코드 검사·U-B08·U-B12·U-B25로 대신한다.

## 4. 설계 피드백
- **F1 (LLD 해석, 설계팀 확인함)**: 깊이는 **폴더 링크**만 센다(0절 3). SRS REQ-55-06 글자는 "링크 깊이가 8을 넘는 링크"지만, HLD 2.3·D5는 `preVisitDirectory`에서만 링크 여부를 읽고 파일에는 왕복을 더하지 않는다(SRS 6절 권장). 그래서 폴더 링크 8겹 안의 **파일 링크**(깊이로 치면 9)는 복사된다. 파일 링크는 순환을 만들 수 없어 REQ-55-06의 목적(순환 종료)에는 영향이 없다. 파일 링크까지 세려면 파일마다 링크 안 따른 속성 읽기 1회가 는다. **설계팀 답(2026-11-19)**: OK, SRS·HLD 변경 없음 — HLD 2.3·D5가 깊이를 폴더 링크로 좁혔고 SRS 6절이 파일 왕복을 더하지 않기를 권장하며, 파일 링크는 순환을 만들지 않는다.
- **F2 (LLD 결정, 기록)**: ③(목록 도중 실패)의 다시 시도는 "이미 방문한 자식은 건너뛰고 이어서"로 한다(2.4 `markVisited`, 2.12 (a)(b)). 처음부터 다시 돌면 이미 복사한 자식마다 교체·합치기 창이 뜬다. 방문 집합은 열린 폴더 줄기에만 있다(메모리 = 그 폴더들의 목록 크기). 설계팀 확인: HLD 2.3 ③과 맞음(2026-11-19).
- **F3 (LLD 결정, PM 지시 응답)**: 진행률 100% 초과는 `TransferInfo`가 전체를 늘려 막는다(2.8). 표시 쪽 상한만 더하는 안은 알림·"N/M"·앱 안 목록 세 곳에 따로 상한을 두어야 해서 버렸다. 어느 안이든 초과 뒤 막대는 100%에 머문다(검수 m2).
- **F4 (LLD 결정, 기록)**: 순회 오류(①·②)의 '모두 건너뛰기'는 복사 오류와 같은 `skipCopyMoveError`를 쓴다(HLD 2.3 "같은 오류 처리", SRS REQ-55-06 "REQ-55-05와 같은 오류 처리"). 깊이 초과만 따로 모두 건너뛰기를 두는 안은 플래그가 늘고 사용자에게 차이가 보이지 않는다. 설계팀 확인: 이의 없음(2026-11-19).
- **F5 (LLD 결정, 기록)**: 폴더의 링크 여부를 읽지 못하면 링크로 친다(2.10 `isSymbolicLinkForLinkDepth`). 따라간 속성은 읽혔는데 따르지 않은 속성만 실패하는 드문 경우다. 링크가 아닌 것으로 치면 그 경로로 깊이 제한을 벗어날 수 있다.
- **F6 (LLD 결정, 기록)**: `getTitle`·`getMessage`에서 쓰지 않게 되는 `sourceFile` 인자를 뺀다(2.17). 호출자는 `showConflictDialog` 한 곳뿐이다(`FileJobs.kt:629-630`).
- **F7 (A1 의존, 기록)**: B2가 기대는 A1 심볼(LLD-A 2.1·2.4·2.6) — `CopyKind`(`PHONE_TO_PHONE`, `NETWORK`), `classifyCopy(Path, Path)`, `copy(source, target, isExtract, copyKind, transferInfo, actionAllInfo)`, `CopyFileJob.copyRecursively(source, target, isExtract, copyKind, transferInfo, actionAllInfo)`, `copyOrMove(…, copyAttributes, skipAttributes, …)`. v1.2에서 머지된 A1(c39a97e, PR #75)과 대조했다: `CopyKind`·`classifyCopy(Path, Path)`(`filejob/CopyClassification.kt:19-22`, 51-52), `CopyFileJob.run`의 분류(`FileJobs.kt:824`), `copyRecursively(…, copyKind, …)`(830-838), `copy(…, isExtract, copyKind, …)`(952-963, `copyOrMove`에 `copyKind == CopyKind.NETWORK`를 `skipAttributes`로 넘김), `copyOrMove(…, copyAttributes, skipAttributes, …)`(1260-, 1365 `if (skipAttributes)`), `copyForMove`·`moveByCopy`의 `false`(1242, 1256), `open`·`save`의 분류(1614, 1812) — 이름·인자 순서 모두 이 문서와 같다.
- **F8 (참고)**: 대상 없는 링크의 건너뛰기는 `copyOrMove`의 `transferInfo.skipFile(source)`(링크 자체 크기를 뺌)이고 집계는 링크 자체 속성(라이브러리가 넘긴 것)을 셌으므로 맞는다. 정상 링크 항목은 집계가 따라간 크기를, 건너뛰기가 링크 크기를 빼서 약간 어긋날 수 있다 — 2.8 상한이 100% 초과를 막는다. 고치려면 `TransferInfo`가 분류를 알아야 해서 하지 않는다.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1 | 최초 작성(조각 B1·B2) | PM LLD 요청(조각 B, A1 위) | PM |
| 2026-11-19 | v1.1 | 2.8·F3 상한 효과 문구 정정(m2), 2.12 방문자를 `private inner class`로(m3). M1·m1·m4([판단])는 PM 판단 대기 | LLD 검수 1차 반려 [기계] 항목 (PR #73 코멘트) | reviewer |
| 2026-11-19 | v1.2 | 2.5a 신설(`startFailureBeforeEntering`·`enterIfResumedStart`, 재개 중 ②로 실패해도 재개 정보 유지 — M1·m4), 2.5 `isCountedOnSkip`·2.12 `askWalkError`가 ③ 건너뛰기에서 수를 빼지 않음, 2.8 건너뛰기에도 상한 불변식(m1), 2.6 `ask`가 `ItemWalkFailure`를 받음, U-B25–28, 3.3 검사 항목. 기준 코드를 A1 머지 뒤 c39a97e로 옮기고 `FileJobs.kt` 줄 번호 갱신, F7을 머지된 A1과 대조 | LLD 검수 1차 반려 M1·m1·m4([판단], PM 승인 — PR #73 결정 코멘트), A1 머지(PR #75) | PM, reviewer |
