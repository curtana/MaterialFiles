# LLD — 네트워크 이동·이름 바꾸기: rename과 MOVE 구분, 바꾸기 이동의 대상 선삭제 없애기 (이슈 #95)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1.1, PR #96 1회차 조건부 통과 반영) |
| 근거 | SRS v2.4, HLD v2.5(PR #96 검수 통과) |
| 기준 코드 | `origin/develop` 9374221. 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준, 테스트는 `app/src/test/java/me/zhanghai/android/files/` 기준. 줄 번호는 모두 이 커밋 기준이다 |

## 0. 나누기 (조각·순서·의존)

오너 원칙(긴 작업은 따로 머지되는 작은 조각으로)에 따라 세 조각으로 나눈다. 조각마다 feature 브랜치 하나, develop 대상 PR 하나, 파일 6개 이하.

| 조각 | HLD | 내용 | 파일(운영+테스트) | 의존 |
|---|---|---|---|---|
| **P1** 공통 이동 꼬리 + FTP·SFTP | C1(FTP·SFTP), C2(SFTP), C4 | 공통 함수 `moveByRenameOrCopy`(2.1) 신설과 시험, FTP·SFTP `move()`가 그것을 쓰게 바꿈(선삭제 제거, 되돌리기 제거), SFTP 덮어쓰기 rename | 운영 3 + 신규 1 + 테스트 1 = 5 | 없음 |
| **P2** SMB·WebDAV | C1(SMB·WebDAV), C2(SMB·WebDAV), C3, C4 | SMB·WebDAV `move()`가 P1 함수를 쓰게 바꿈, SMB ReplaceIfExists·WebDAV Overwrite를 `overwrite` 인수로, WebDAV 다른 서버 예외형 | 운영 4 + 테스트 1 = 5 | **P1 머지 뒤**(2.1 함수를 쓴다) |
| **P3** 다른 이동 경로의 사본 유지 | C5, C4 | `ForeignCopyMove.move`·`LinuxCopyMove.move`·`DocumentResolver.moveByCopy`의 원본 삭제 실패 되돌리기 제거 | 운영 3 + 테스트 1 = 4 | 없음(P1·P2와 독립) |

- 권장 순서: **P1 → P2**, P3는 아무 때나(P1과 나란히 가능). 세 조각 모두 머지되면 이슈 #95가 끝난다. P1·P3 PR 본문은 `Refs #95`, 마지막에 머지되는 PR이 `Closes #95`.
- 중간 상태의 안전성: P1만 들어간 상태에서 SMB·WebDAV는 지금 코드 그대로다(선삭제·되돌리기 유지) — 지금과 같은 동작이라 새 위험이 없다. P1의 FTP 바꾸기 이동은 선삭제 없이 기존 파일 위로 RNTO, 서버가 거절하면 대체 복사(#55 순서)로 간다. P3만 들어간 상태도 네트워크 네 곳은 지금과 같다.

## 1. 변경 파일

| 조각 | 파일 | 신규/수정 | 요지 |
|---|---|---|---|
| P1 | `provider/common/RenameOrCopyMove.kt` | 신규 | 2.1 `moveByRenameOrCopy`: 대상 확인 뒤의 이동 꼬리(덮어쓰기 결정 → rename → 원자면 던짐, 아니면 옵션 보정 → 복사 → 원본 삭제). 대상 삭제 호출이 없다 |
| P1 | `provider/ftp/FtpCopyMove.kt` | 수정 | 2.2: `move()`의 선삭제 `:166-170` 제거, `:172-208`을 2.1 호출로 |
| P1 | `provider/sftp/SftpCopyMove.kt` | 수정 | 2.3: 선삭제 `:275-279` 제거, `:281-317`을 2.1 호출로(`overwrite` 전달) |
| P1 | `provider/sftp/client/Client.kt` | 수정 | 2.4: `rename(path, newPath, overwrite)`, `overwrite`면 `RenameFlags.OVERWRITE` |
| P1 | `provider/common/RenameOrCopyMoveTest.kt`(test) | 신규 | 3장 U-95-01~09 |
| P2 | `provider/smb/SmbCopyMove.kt` | 수정 | 2.5: 선삭제 `:198-202` 제거, `:204-242`를 2.1 호출로 |
| P2 | `provider/smb/client/Client.kt` | 수정 | 2.6: `rename(path, newPath, overwrite)`, `:469` 상수 `true` → `overwrite` |
| P2 | `provider/webdav/WebDavCopyMove.kt` | 수정 | 2.7: 선삭제 `:177-181` 제거, `:183-219`를 2.1 호출로 |
| P2 | `provider/webdav/client/Client.kt` | 수정 | 2.8: `move(source, target, overwrite)`, `:118` 상수 `false` → `overwrite`, `:114-116` 예외를 `toDavException()`으로(C3) |
| P2 | `provider/webdav/client/ClientMoveAuthorityTest.kt`(test) | 신규 | 3장 U-95-10~11 |
| P3 | `provider/common/ForeignCopyMove.kt` | 수정 | 2.9: `move()` `:164-186`의 두 `catch` 제거 → `source.delete()`만 |
| P3 | `provider/linux/LinuxCopyMove.kt` | 수정 | 2.10: `move()` `:299-310`의 되돌리기 `:302-308` 제거 |
| P3 | `provider/document/resolver/DocumentResolver.kt` | 수정 | 2.11: `moveByCopy()`(`:359-386`)의 `:370-384` 되돌리기 `:375-382` 제거 |
| P3 | `provider/common/ForeignCopyMoveCleanupTest.kt`(test) | 수정 | 3장 U-95-12~14 추가(기존 가짜 `FakeProvider` 재사용) |

바꾸지 않는 곳(HLD 2장 그대로): `filejob/FileJobs.kt` 전체, 네 `copy()`, `provider/common/SourceCheckedReplace.kt`, `ftp/client/Client.kt`(`renameFile` 인수 없음 — RNTO에 덮어쓰기 지정 수단이 없다), C5 경로의 바꾸기 선삭제(`LinuxCopyMove.kt:266-270`, `DocumentCopyMove.kt:94-103`), 루트 제공자(`root/RootableFileSystemProvider.kt:104-106` → 루트 프로세스에서 같은 `LinuxCopyMove.move`를 쓰므로 2.10으로 함께 바뀐다), 문자열·권한·설정. 쓰지 않게 된 import(예: `FtpCopyMove`의 `NoSuchFileException`이 다른 곳에서 안 쓰이면)는 구현 때 정리한다.

## 2. 함수 명세

### 2.1 `provider.common.moveByRenameOrCopy` (P1, 신규, `internal`)
```kotlin
@Throws(IOException::class)
internal fun moveByRenameOrCopy(
    copyOptions: CopyOptions,
    targetExists: Boolean,
    sourceSize: Long,
    rename: (overwrite: Boolean) -> Unit,
    copy: (CopyOptions) -> Unit,
    deleteSource: () -> Unit
)
```
- **추적**: HLD C1 (1)~(4), 3.1 단계 3·4, D2·D3 / REQ-95-01·02·03·04·05·09
- **책임**: 네트워크 `move()`가 원본·대상 확인을 마친 뒤의 공통 꼬리 — 서버 rename을 먼저, 원자가 아니면 실패 시 스트리밍 복사 뒤 원본 삭제. 대상을 지우는 호출을 갖지 않는다(선삭제도 되돌리기도 없음).
- **입력·전제**:
  - 호출자가 이미 끝낸 것: 원본 정보 읽기, 대상 정보 읽기, 같은 파일이면 진행률 올리고 반환, 대상이 있고 `!copyOptions.replaceExisting`이면 `FileAlreadyExistsException`. 따라서 여기 들어올 때 `targetExists`이면 `replaceExisting`이다(그래도 2단계에서 둘 다 본다, HLD D3).
  - `sourceSize`: rename 성공 때 진행률에 넘길 크기(지금 백엔드마다 쓰던 값 그대로).
  - 세 람다는 백엔드 예외를 **이미 `java8.nio.file.FileSystemException`으로 바꿔서** 던진다(`SourceCheckedReplace.kt:20-21`과 같은 규약). `rename`은 원자 이동 때 던질 형태(SMB의 `AtomicMoveNotSupportedException`·`InvalidFileNameException` 포함)로 바꿔 던진다 — 비원자면 그 예외는 버려지므로 손해가 없다.
  - 호출 스레드: `FileJobService` 작업 스레드(동기). 상태 없음.
- **출력·사후조건**: 정상 반환이면 (a) rename 성공 + 진행률 `sourceSize` 1회, 또는 (b) rename 실패(비원자) → `copy` 성공 → `deleteSource` 성공.
- **오류**:
  - `rename`이 `FileSystemException`을 던지고 `copyOptions.atomicMove`면 그 예외를 **그대로** 던진다(같은 객체). `copy`·`deleteSource`를 부르지 않는다(REQ-95-01).
  - `rename`이 `FileSystemException`이 아닌 것(다른 `IOException`, `RuntimeException`)을 던지면 잡지 않고 그대로 나간다 — 지금 각 백엔드가 자기 예외형(`ClientException`, `DavException`, FTP는 `IOException` 전부)만 잡던 것과 같다(람다가 정확히 그 형만 바꾸므로). 이 규칙 때문에 WebDAV 다른 서버의 `IOException` 탈출(SRS 1.4-4)은 P1 단계에서는 지금과 같고 P2(2.8)에서 고쳐진다.
  - `copy`가 던지면 그대로 나간다. `deleteSource`를 부르지 않는다(원본 남음, 부분 대상 정리는 각 `copy()`의 기존 몫).
  - `deleteSource`가 던지면 그대로 나간다(같은 객체, suppressed 추가 없음). 대상의 사본은 남는다(REQ-95-09). `NoSuchFileException`도 같다(지금도 되돌리지 않고 던짐).
- **알고리즘**:
  ```kotlin
  val overwrite = targetExists && copyOptions.replaceExisting
  var renameSuccessful = false
  try {
      rename(overwrite)
      renameSuccessful = true
  } catch (e: FileSystemException) {
      if (copyOptions.atomicMove) {
          throw e
      }
      // Ignored.
  }
  if (renameSuccessful) {
      copyOptions.progressListener?.invoke(sourceSize)
      return
  }
  if (copyOptions.atomicMove) {
      throw AssertionError()
  }
  val copyOptionsForCopy = if (copyOptions.copyAttributes && copyOptions.noFollowLinks) {
      copyOptions
  } else {
      CopyOptions(
          copyOptions.replaceExisting, true, false, true, copyOptions.progressIntervalMillis,
          copyOptions.progressListener, copyOptions.cleanupFailureListener
      )
  }
  copy(copyOptionsForCopy)
  // Keep the copy if deleting the source fails (#95): the target now has the new content and the
  // source is still there, so nothing is lost.
  deleteSource()
  ```
  옵션 보정 식은 지금 네 백엔드의 것(FTP `:189-195` 등)을 글자 그대로 옮긴 것이다(`skipAttributes`는 기본값 `false` — 이동은 속성 건너뛰기를 쓰지 않는다, `FileJobs.kt:1543` 주석 #55 Q4).
- **기존 코드와의 관계**: 네 백엔드 `move()`의 rename 이후 부분(FTP `FtpCopyMove.kt:172-208`, SFTP `SftpCopyMove.kt:281-317`, SMB `SmbCopyMove.kt:204-242`, WebDAV `WebDavCopyMove.kt:183-219`)을 하나로 모은 것. 바뀌는 것은 두 가지뿐 — `rename`에 `overwrite`를 넘김(REQ-95-04), 원본 삭제 실패 시 대상 삭제 없음(REQ-95-09). 바꾸기 이동의 기존 대상 처리는 `copy` 람다가 부르는 각 백엔드 `copy()`의 #55 순서(`replaceAfterOpeningSource`/`replaceAfterCheckingSource`)에 맡긴다(HLD D2, REQ-95-05). 파일 머리 저작권 주석·KDoc 밀도는 같은 패키지 `SourceCheckedReplace.kt:1-22`를 따른다. JVM 시험이 된다(안드로이드 의존 없음).

### 2.2 `provider.ftp.FtpCopyMove.move(source: FtpPath, target: FtpPath, copyOptions: CopyOptions)` (P1, 수정)
- **추적**: HLD C1 / REQ-95-01·02·03·04·05·09
- **책임**: FTP 이동. 서버 RNTO 먼저, 비원자면 실패 시 대체.
- **입력·전제·출력**: 지금과 같다(`FtpCopyMove.kt:145-209`).
- **알고리즘** (바뀌는 곳만):
  1. `:146-165` 그대로(원본·대상 목록, 같은 파일, `FileAlreadyExistsException`).
  2. `:166-170`(바꾸기면 `Client.delete(target, …)`) **삭제**.
  3. `:172-208`을 다음으로 바꾼다:
     ```kotlin
     moveByRenameOrCopy(
         copyOptions, targetFile != null, sourceSize,
         rename = {
             try {
                 Client.renameFile(source, target)
             } catch (e: IOException) {
                 throw e.toFileSystemExceptionForFtp(source.toString(), target.toString())
             }
         },
         copy = { copy(source, target, it) },
         deleteSource = {
             try {
                 Client.delete(source, sourceFile.isDirectory)
             } catch (e: IOException) {
                 throw e.toFileSystemExceptionForFtp(source.toString())
             }
         }
     )
     ```
     `rename` 람다는 `overwrite`를 쓰지 않는다(`_ ->` 또는 암시 `it` 무시) — RNTO에는 덮어쓰기 지정 수단이 없어 바꾸기 이동이면 기존 파일 위로 그냥 RNTO한다(HLD C2, R4).
- **오류**: 2.1 그대로. `toFileSystemExceptionForFtp`는 어떤 `IOException`이든 `FileSystemException`으로 바꾸므로(`ftp/IOExceptionFtpExtensions.kt:7-16`) 지금 `catch (e: IOException)`(`:176`)의 범위와 같다.
- **기존 코드와의 관계**: `Client.renameFile`(`ftp/client/Client.kt:206-215`)·`Client.delete` 그대로. `copy()`(`:19-143`) 그대로.

### 2.3 `provider.sftp.SftpCopyMove.move(source: SftpPath, target: SftpPath, copyOptions: CopyOptions)` (P1, 수정)
- **추적**: HLD C1·C2 / REQ-95-01~05·09
- **알고리즘** (바뀌는 곳만):
  1. `:241-274` 그대로(원본 `lstat`·MODE 확인, 대상 `lstat`, 크기, 같은 파일, `FileAlreadyExistsException`).
  2. `:275-279`(바꾸기면 `Client.remove(target)`) **삭제**.
  3. `:281-317`을 다음으로:
     ```kotlin
     moveByRenameOrCopy(
         copyOptions, targetAttributes != null, sourceSize,
         rename = { overwrite ->
             try {
                 Client.rename(source, target, overwrite)
             } catch (e: ClientException) {
                 throw e.toFileSystemException(source.toString(), target.toString())
             }
         },
         copy = { copy(source, target, it) },
         deleteSource = {
             try {
                 Client.remove(source)
             } catch (e: ClientException) {
                 throw e.toFileSystemException(source.toString())
             }
         }
     )
     ```
- **오류**: 2.1 그대로. `ClientException`은 `Exception` 하위형(`sftp/client/ClientException.kt:21`)이라 지금도 그 밖의 예외는 잡지 않았다 — 람다가 `ClientException`만 바꾸므로 같다.

### 2.4 `provider.sftp.client.Client.rename(path: Path, newPath: Path, overwrite: Boolean)` (P1, 수정)
- **추적**: HLD C2 / REQ-95-04
- **책임**: 같은 authority 안 SFTP rename. `overwrite`면 덮어쓰기 rename.
- **입력·전제**: 호출자는 2.3 하나뿐(`grep "Client.rename("` 결과 `SftpCopyMove.kt:283`만). 기본값을 두지 않는다(호출자가 반드시 정하게).
- **알고리즘**:
  ```kotlin
  withClient(path.authority) {
      if (overwrite) {
          it.rename(path.remotePath, newPath.remotePath, EnumSet.of(RenameFlags.OVERWRITE))
      } else {
          it.rename(path.remotePath, newPath.remotePath)
      }
  }
  ```
  authority 확인(`:99-103`)과 캐시·감시 갱신(`:105-108`)은 그대로. `:96` 주석을 "Unless overwrite, this won't overwrite an existing file, unlike POSIX rename()."처럼 고친다. import `net.schmizz.sshj.sftp.RenameFlags`, `java.util.EnumSet`.
- **오류**: sshj 0.40.0 `SFTPClient.rename(String, String, Set<RenameFlags>)` — `OVERWRITE`를 v3 서버가 `posix-rename@openssh.com`을 알리지 않으면 요청 전에 `SFTPException`(SRS 1.6) → `callOrEvict`가 `ClientException`으로 → 2.3이 `FileSystemException`으로 → 2.1이 대체로. 서버 상태 변화 없음.
- **기존 코드와의 관계**: 2인수 호출(`:104`)은 `overwrite == false`일 때 그대로 남는다 — 일반 이동·이름 바꾸기의 서버 요청은 지금과 같다.

### 2.5 `provider.smb.SmbCopyMove.move(source: SmbPath, target: SmbPath, copyOptions: CopyOptions)` (P2, 수정)
- **추적**: HLD C1 / REQ-95-01~05·09
- **알고리즘** (바뀌는 곳만):
  1. `:169-197` 그대로(공유 이동 거부, `SmbFileKey` 같은 파일, `FileAlreadyExistsException`).
  2. `:198-202`(바꾸기면 `Client.delete(target)`) **삭제**.
  3. `:204-242`를 다음으로:
     ```kotlin
     moveByRenameOrCopy(
         copyOptions, targetInformation != null, sourceInformation.endOfFile,
         rename = { overwrite ->
             try {
                 Client.rename(source, target, overwrite)
             } catch (e: ClientException) {
                 e.maybeThrowAtomicMoveNotSupportedException(source.toString(), target.toString())
                 e.maybeThrowInvalidFileNameException(target.toString())
                 throw e.toFileSystemException(source.toString(), target.toString())
             }
         },
         copy = { copy(source, target, it) },
         deleteSource = {
             try {
                 Client.delete(source)
             } catch (e: ClientException) {
                 throw e.toFileSystemException(source.toString())
             }
         }
     )
     ```
     지금은 두 `maybeThrow…`를 원자일 때만 불렀다(`:209-213`). 람다에서는 늘 부르지만 둘 다 `FileSystemException` 하위형(`AtomicMoveNotSupportedException`, `InvalidFileNameException` — `provider/common/InvalidFileNameException.kt:10`)을 던지고, 비원자면 2.1이 버리므로 결과가 같다.
- **오류**: 2.1 그대로.

### 2.6 `provider.smb.client.Client.rename(path: Path, newPath: Path, overwrite: Boolean)` (P2, 수정)
- **추적**: HLD C2 / REQ-95-04, SRS 4장(동시성)
- **알고리즘**: `:469` `diskEntry.use { it.rename(newSharePath.path, true) }` → `it.rename(newSharePath.path, overwrite)`. 나머지(`:445-473`) 그대로. 호출자는 2.5 하나(`SmbCopyMove.kt:206`). 기본값 없음.
- **동작 변화**: 일반 이동(`overwrite == false`)의 ReplaceIfExists가 `true` → `false`. 확인과 rename 사이에 생긴 대상을 조용히 덮어쓰던 것이 rename 실패 → 대체 `copy()`의 `FileAlreadyExistsException` → 충돌 창이 된다(HLD R2, 3.2 마지막 줄 — 의도된 변화).

### 2.7 `provider.webdav.WebDavCopyMove.move(source: WebDavPath, target: WebDavPath, copyOptions: CopyOptions)` (P2, 수정)
- **추적**: HLD C1 / REQ-95-01~06·09
- **알고리즘** (바뀌는 곳만):
  1. `:157-176` 그대로.
  2. `:177-181`(바꾸기면 `Client.delete(target)`) **삭제**.
  3. `:183-219`를 다음으로:
     ```kotlin
     moveByRenameOrCopy(
         copyOptions, targetResponse != null, sourceSize,
         rename = { overwrite ->
             try {
                 Client.move(source, target, overwrite)
             } catch (e: DavException) {
                 throw e.toFileSystemException(source.toString(), target.toString())
             }
         },
         copy = { copy(source, target, it) },
         deleteSource = {
             try {
                 Client.delete(source)
             } catch (e: DavException) {
                 throw e.toFileSystemException(source.toString())
             }
         }
     )
     ```
- **오류**: 2.1 그대로. 2.8의 C3 뒤에는 다른 서버 실패도 `DavException`(`DavIOException`)이라 이 람다가 `FileSystemException`(`webdav/DavExceptionExtensions.kt:19-20`, 메시지 보존)으로 바꾸고, 비원자면 2.1이 대체로 보낸다(REQ-95-06).

### 2.8 `provider.webdav.client.Client.move(source: Path, target: Path, overwrite: Boolean)` (P2, 수정)
- **추적**: HLD C2·C3, D4 / REQ-95-04·06
- **알고리즘**:
  ```kotlin
  if (source.authority != target.authority) {
      throw IOException("Paths aren't on the same authority").toDavException()
  }
  try {
      DavResource(getClient(source.authority), source.url).move(target.url, overwrite) {}
  } catch (e: IOException) {
      throw e.toDavException()
  }
  ```
  캐시·감시 갱신(`:122-125`) 그대로. 호출자는 2.7 하나(`WebDavCopyMove.kt:185`). 기본값 없음.
- **동작**: dav4jvm `move(url, forceOverride)` — `false`면 `Overwrite: F`(지금과 같음), `true`(바꾸기 이동이고 대상 있음)면 헤더 생략 = T(SRS 1.6, RFC 4918 10.6).
- **오류**: 다른 authority면 `DavIOException`(`DavIOException.kt:11-16`, 메시지 "Paths aren't on the same authority" 보존). 지금 `IOException`이 `@Throws(DavException::class)` 선언과 어긋나던 것(`:113-116`)이 선언과 맞게 된다. 서버 요청은 지금처럼 없다.
- **기존 코드와의 관계**: 같은 파일의 다른 메서드가 이미 `IOException` → `toDavException()` 규칙을 쓴다(`:119-121`, HLD D4).

### 2.9 `provider.common.ForeignCopyMove.move(source: Path, target: Path, vararg options: CopyOption)` (P3, 수정)
- **추적**: HLD C5 / REQ-95-07·08·09
- **알고리즘**: `:146-163` 그대로(원자 거부, 옵션 보정, `copy`). `:164-186`의
  ```kotlin
  try { source.delete() } catch (e: IOException) { …target.delete()… throw e } catch (e: UnsupportedOperationException) { …target.delete()… throw e }
  ```
  를 `source.delete()` 한 줄로 바꾼다. 두 분기 모두 `throw e`(같은 객체)였으므로, 대상 삭제를 빼면 `try/catch`가 하는 일이 없다. 위에 한 줄 주석 "Keep the copy if deleting the source fails (#95)."을 둔다.
- **오류**: `source.delete()`의 `IOException`(`NoSuchFileException` 포함)·`UnsupportedOperationException`이 그대로 나간다(suppressed 없음). 대상은 남는다.
- **기존 코드와의 관계**: `copy()`(`:22-144`)와 `ForeignCopyMoveCleanupTest` 기존 시험(U-M01~06, U-C10~14, U-A07~09)은 영향 없음. import `NoSuchFileException`은 `copy()` 쪽에서 계속 쓰는지 구현 때 확인하고 안 쓰면 뺀다.

### 2.10 `provider.linux.LinuxCopyMove.move(source: ByteString, target: ByteString, copyOptions: CopyOptions)` (P3, 수정)
- **추적**: HLD C5 / REQ-95-07·08·09
- **알고리즘**: `:299-310`
  ```kotlin
  try { Syscall.remove(source) } catch (e: SyscallException) {
      if (e.errno != OsConstants.ENOENT) { try { Syscall.remove(target) } catch (e2: …) { e.addSuppressed(…) } }
      throw e.toFileSystemException(source.toString())
  }
  ```
  → 대상 삭제 블록(`:302-308`)만 뺀다:
  ```kotlin
  // Keep the copy if deleting the source fails (#95).
  try {
      Syscall.remove(source)
  } catch (e: SyscallException) {
      throw e.toFileSystemException(source.toString())
  }
  ```
  그 위(바꾸기 선삭제 `:266-270` 포함)는 그대로(SRS 1.3).
- **오류**: 지금과 같은 변환 예외. 루트 경로도 같은 함수라 함께 바뀐다(`root/RootableFileSystemProvider.kt:104-106` → `linux/LocalLinuxFileSystemProvider.kt:225`).
- 시그니처(`LinuxCopyMove.kt:242-243`)는 바꾸지 않는다.

### 2.11 `provider.document.resolver.DocumentResolver.moveByCopy(sourcePath, targetPath, intervalMillis, listener, cleanupFailureListener): Uri` (P3, 수정, `private`)
- **추적**: HLD C5 / REQ-95-07·08·09
- **알고리즘**: `:370-384`의 `try { …remove(sourceUri, sourceParentUri) } catch (e: ResolverException) { if (…!is NoSuchFileException) { …remove(targetUri, targetParentUri)… }; throw e }`를 되돌리기 없이:
  ```kotlin
  // Keep the copy if deleting the source fails (#95).
  val sourceUri = getDocumentUri(sourcePath)
  val sourceParentUri = getDocumentUri(sourcePath.requireParent())
  remove(sourceUri, sourceParentUri)
  return targetUri
  ```
  `catch`가 `throw e`(같은 객체)였으므로 결과 예외가 같다. 호출자(`:298`, `:330`) 그대로.
- **오류**: `ResolverException` 그대로 나간다. 대상 문서는 남는다.

## 3. 테스트 설계

JVM 단위 테스트(`./gradlew testDebugUnitTest`, JUnit 4). "수정 전 실패"는 PROCESS_ROLES 11-1-5대로 실제로 확인한다. 2.1은 신규 함수라 수정 전 코드에 대상이 없다 — 시험을 먼저 쓰고 컴파일 실패(red)를 확인한 뒤 구현한다.

| 테스트 | 조각 | 대상 함수 | 경우 | 기대 결과 | 수정 전 |
|---|---|---|---|---|---|
| U-95-01 | P1 | 2.1 | 대상 없음, rename 성공 | 호출 기록 `[rename:false]`, 진행률 `sourceSize` 1회, `copy`·`deleteSource` 안 부름 | 신규(red) |
| U-95-02 | P1 | 2.1 | 바꾸기·대상 있음, rename 성공 | 기록 `[rename:true]` — rename 앞에 다른 호출 없음(REQ-95-03·04) | 신규 |
| U-95-03 | P1 | 2.1 | 덮어쓰기 진리표: (`targetExists`,`replaceExisting`) = (F,F),(F,T),(T,F),(T,T) | `overwrite`가 (T,T)에서만 `true` | 신규 |
| U-95-04 | P1 | 2.1 | 원자, rename이 `FileSystemException` | 같은 예외 객체를 던짐, 기록 `[rename:false]`만(REQ-95-01) | 신규 |
| U-95-05 | P1 | 2.1 | 비원자·바꾸기·대상 있음, rename 실패, 옵션 보정 필요(`copyAttributes=false`) | 기록 `[rename:true, copy, deleteSource]`, `copy`에 넘긴 옵션: `replaceExisting=true`, `copyAttributes=true`, `atomicMove=false`, `noFollowLinks=true`, 진행률·정리 실패 리스너가 원래 객체와 같음(REQ-95-02·05) | 신규 |
| U-95-06 | P1 | 2.1 | 비원자, rename 실패, 옵션이 이미 `copyAttributes && noFollowLinks` | `copy`가 받은 옵션이 원래 객체와 `assertSame` | 신규 |
| U-95-07 | P1 | 2.1 | 비원자, rename 실패, `copy` 성공, `deleteSource`가 `AccessDeniedException` | 같은 예외 객체, suppressed 0개, 기록 `[rename, copy, deleteSource]`로 끝(대상 삭제 람다 자체가 없음 — 사본 유지, REQ-95-09). `NoSuchFileException`도 같은 결과(같은 시험 안 두 번째 경우) | 신규 |
| U-95-08 | P1 | 2.1 | 비원자, rename 실패, `copy`가 `IOException` | 같은 예외, `deleteSource` 안 부름 | 신규 |
| U-95-09 | P1 | 2.1 | 비원자, rename이 `FileSystemException`이 아닌 `IOException` | 잡지 않고 같은 예외를 던짐, `copy` 안 부름(2.1 오류 둘째 항 — 백엔드 예외형 범위 유지) | 신규 |
| U-95-10 | P2 | 2.8 | 다른 authority(같은 `Protocol.DAVS`, 호스트 `a.example`·`b.example`)의 가짜 `Client.Path` 둘로 `Client.move(a, b, false)` | `assertThrows(DavException::class.java)` — `DavIOException`, 메시지 "Paths aren't on the same authority". `DavException`은 `IOException`이 아니므로(dav4jvm) 형을 정확히 이것으로 쓴다 | **실패**: 수정 전 확인은 2인수 `Client.move(a, b)`로 먼저 돌려 `IOException`(DavException 아님)으로 실패함을 본 뒤, 2.8에서 셋째 인수를 더하고 시험을 3인수로 바꾼다(3인수를 바로 쓰면 수정 전은 컴파일 실패 red일 뿐이다). 검수 1회차에 수정 전 코드에서 `IOException` 실측됨 |
| U-95-11 | P2 | 2.8 → 2.7 변환 | U-95-10의 예외에 `toFileSystemException("a", "b")` | `FileSystemException`, 메시지 보존, `cause`가 원래 `IOException` | 실패(U-95-10과 함께) |
| U-95-12 | P3 | 2.9 | `deleteException = IOException()`, `ForeignCopyMove.move(source, target, option)` | 같은 예외 객체, `deletedPaths == [source]`(대상 삭제 없음), suppressed 0개 | **실패**: 지금은 `[source, target]` |
| U-95-13 | P3 | 2.9 | `deleteException = UnsupportedOperationException()` | 같은 예외 객체, `deletedPaths == [source]` | **실패**: 지금 `[source, target]` |
| U-95-14 | P3 | 2.9 | `deleteException = NoSuchFileException(source)` | 같은 예외, `deletedPaths == [source]` | 통과(회귀 지킴 — 지금도 되돌리지 않음) |

- 2.1 시험의 가짜: `SourceCheckedReplaceTest.FakeBackend`(`provider/common/SourceCheckedReplaceTest.kt:17-50`)처럼 호출 기록 `calls`와 실패 주입 필드를 갖는 작은 클래스. `CopyOptions`는 생성자로 직접 만든다(`provider/common/CopyOptions.kt:22-31`).
- U-95-10의 가짜 경로: `Client.Path`(`webdav/client/Client.kt:257-261`)를 구현하는 작은 클래스 — `authority = Authority(Protocol.DAVS, host, 443, "")`, `url = "https://$host/x".toHttpUrl()`, `resolve`는 `UnsupportedOperationException`. `Client` 객체 초기화는 맵·`Property.Name` 상수뿐이라 JVM에서 된다고 본다. **`@Parcelize` `Authority`를 JVM에서 만들 수 없으면**(android.jar 스텁 오류) 이 두 시험은 빼고 코드 검사로 대신하며 PR 강조점에 적는다(구현 때 확인).
- U-95-12~14는 기존 `FakeProvider`(`ForeignCopyMoveCleanupTest.kt:219-`)의 `deleteException`·`deletedPaths`를 그대로 쓴다. 가짜 `delete`는 경로와 무관하게 같은 예외를 던지므로, 지금 코드에서는 대상 삭제도 시도되어 기록에 남는다 — 그것이 수정 전 실패의 근거다.
- 시험할 수 없는 것(코드 검사로 대신): 2.2~2.7의 백엔드 람다 배선(각 `Client`가 실제 네트워크 `object`), 2.4·2.6의 라이브러리 호출, 2.10(`Syscall` JNI), 2.11(`ContentResolver`). 검사 항목: 네 `move()`에 rename 앞 대상 삭제 호출이 없다, `moveByRenameOrCopy`에 `targetExists`로 각 백엔드의 대상 정보 `!= null`을 넘긴다, 일곱 곳의 원본 삭제 실패 경로에 대상 삭제가 없다, `FileJobs.kt` diff 없음.
- 회귀: 조각마다 `./gradlew testDebugUnitTest`(기존 시험 전부)와 `assembleDebug` 통과.

### 3.1 수동 확인
| 항목 | 조각 | 방법 | 상태 |
|---|---|---|---|
| 실서버 바꾸기 이동(덮어쓰기 수락/거절), SMB 일반 이동, WebDAV 두 서버 사이 이동, SFTP `posix-rename` | P1·P2 | 서버 없음 | **미확인**(HLD R1) |
| FTP 같은 서버 다른 폴더 바꾸기 이동(선택) | P1 | 로컬 AVD에서 앱 내장 FTP 서버를 켜고 같은 앱에서 `ftp://127.0.0.1:<포트>` 연결 → 대상 폴더에 같은 이름 파일을 두고 이동 → "교체". 기대: 대상이 새 내용, 원본 없음(RNTO 수락이면 서버 rename, 거절이면 대체 복사). 실기기(R54N102PRWV)는 쓰지 않는다 | 선택 — 구현자가 AVD가 되면 실행, 안 되면 미확인으로 적는다 |
| 로컬·SAF 원본 삭제 실패 시 사본 유지 | P3 | 원본 삭제만 실패하는 상황(다른 파일 시스템으로의 이동 + 원본 쪽 쓰기 금지)을 AVD에서 만들기 어렵다 | **미확인**(코드 검사·U-95-12~14로 대신) |

## 4. 설계 피드백
1. **공통 함수 신설(2.1)** — HLD C1은 네 `move()`를 제자리에서 고치는 것으로 적었고 1장에 "새 모듈·새 인터페이스 없음"이라 했다. LLD는 네 곳의 같은 꼬리를 `provider/common/RenameOrCopyMove.kt`의 함수 하나로 모은다. 새 모듈·인터페이스가 아니라 같은 패키지 `SourceCheckedReplace.kt`와 같은 형태의 함수이고, 그래야 REQ-95-03·04·05·09를 JVM으로 시험할 수 있다(HLD 6.1 "흐름 결정을 순수 함수로 떼면"). 동작은 HLD 3.1과 같다. → 설계팀 확인(2026-11-20): HLD와 일치 — 1장 "새 모듈·새 인터페이스 없음"은 모듈·공개 인터페이스 경계를 뜻하고, 6.1이 순수 함수 분리를 LLD 판단으로 열어 두었다. HLD 변경 없음.
2. **예외 범위(2.1 오류 둘째 항)** — 공통 함수는 `FileSystemException`만 rename 실패로 본다. 람다가 각 백엔드의 기존 `catch` 형만 바꾸므로 지금 범위와 같다. 그래서 WebDAV 다른 서버 결함은 P1이 아니라 P2(C3)에서 고쳐진다 — HLD와 어긋남 없음, 조각 순서만의 결과.
3. 그 밖의 SRS·HLD 불일치 없음.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | 1 | 최초 작성(조각 P1~P3, 함수 2.1~2.11, 시험 U-95-01~14) | SRS v2.4·HLD v2.5 검수 통과 | PM |
| 2026-11-20 | 1.1 | U-95-10 수정 전 확인 순서(2인수로 먼저 실패 확인 → 3인수)와 `assertThrows(DavException)` 명시, 줄 번호 정정(`ForeignCopyMove.kt:164-186`, `DocumentResolver.kt:359-386`·`:370-384`), 4장 1항 설계팀 확인 기록, 상태 행 | PR #96 LLD 1회차 조건부 통과 경미 2건([기계]) | 검수팀 |
