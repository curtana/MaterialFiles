# LLD — 조각 C: 바꾸기 전 원본 확인 (이슈 #55)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 조각 C1·C2의 영문 작업지시서와 구현·검수의 기준이다. 조각 A·B의 LLD는 따로 쓴다(HLD 2.4).

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과 (v1.1), PM 결정 반영 (v1.2) |
| 근거 | SRS v1.4, HLD v1.3 (2.1 조각 C, 3.3, 4, 5 D3·D4, 6) |
| 기준 코드 | `origin/develop` 0cd4358. `provider/`는 HLD 기준 0941fa7과 같다(`git diff --stat 0941fa7 0cd4358 -- app/src/main/java/me/zhanghai/android/files/provider` 비어 있음). 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준 |
| 추적 REQ | REQ-55-08, REQ-55-09 |
| 나눔 | **C1**(공용 함수·로컬·공급자 간·문서 제공자) → **C2**(SFTP·SMB·FTP·WebDAV). PR을 따로 하고 C1이 develop에 머지된 뒤 C2를 시작한다 |

## 0. 공통 규칙 (C1·C2 모든 백엔드)

'바꾸기'로 다시 시도하는 복사(`REPLACE_EXISTING`, `filejob/FileJobs.kt:1358-1359`, 1391-1394, 1416-1418)에서 백엔드 `copy`는 다음 순서를 지킨다.

1. 원본 속성 읽기, 대상 확인, 같은 파일이면 끝, `REPLACE_EXISTING`이 없으면 `FileAlreadyExistsException` — **지금 코드 그대로**.
2. 원본 확인
   - 일반 파일: 원본을 **읽기로 연다**. 연 핸들로 그대로 복사한다(문서 제공자만 예외, 2.5).
   - 폴더: 대상을 지울 때만 원본 폴더 목록을 **열어 보고 닫는다**.
   - 링크(`NOFOLLOW_LINKS`일 때): 바꾸지 않는다. 이미 `readlink` → `createSymbolicLinkForCopy`(`provider/common/SymbolicLinkCopy.kt:19-46`) 순서라 원본 확인이 먼저다.
   - FTP·WebDAV의 링크 원본: "Cannot copy symbolic links" 실패를 대상 지우기 **전**으로 옮긴다(#56).
3. 대상이 있고 '바꾸기'면 대상을 지운다. 지우기가 `NoSuchFileException`이면 무시한다.
4. 대상을 만들고 쓴다. 그 뒤 속성 단계는 지금 그대로(조각 A가 고친다).

2가 실패하면 3·4를 하지 않고 2의 예외(지금과 같은 `FileSystemException` 계열, HLD 3.3)를 그대로 던진다 → `FileJobs`의 기존 오류 창(REQ-55-05의 처리, `FileJobs.kt:1454-1508`). 창의 버튼은 바꾸지 않는다(REQ-55-09). HLD 2.1 표대로 **확인할 수 없는 경로는 없다**.

순서는 공용 함수 두 개(2.1)로 강제하고, 백엔드는 열기·닫기·지우기·쓰기를 람다로 넘긴다(HLD D3). 람다는 백엔드 예외(`SyscallException`·`ClientException`·`ResolverException`·`DavException`·FTP `IOException`)를 지금 코드와 같은 함수로 `FileSystemException`으로 바꿔 던진다 — 공용 함수가 `NoSuchFileException`을 알아보려면 바뀐 예외여야 한다(`createSymbolicLinkForCopy`와 같은 약속, `SymbolicLinkCopy.kt:15-17`).

**바뀌지 않는 것**: 이동 전용 경로(`DocumentCopyMove.move` 55-85, 각 백엔드 `move`), 속성 단계, 링크 분기, 충돌 판정(`FileJobs.kt`), 충돌·오류 창, 문자열. 이동이 `copy`로 대체될 때(`ForeignCopyMove.move` 150 등)는 순서 변경이 함께 적용된다 — 성공 결과는 같다(HLD 1절).

## 1. 변경 파일

### C1
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `provider/common/SourceCheckedReplace.kt` | 신규 | 공용 함수 `replaceAfterOpeningSource`, `replaceAfterCheckingSource` (2.1·2.2) |
| `provider/linux/LinuxCopyMove.kt` | 수정 | `copy`의 일반 파일(57-135)·폴더(136-152) 분기 (2.3) |
| `provider/common/ForeignCopyMove.kt` | 수정 | `copy`의 일반 파일(42-85)·폴더(86-92) 분기 (2.4) |
| `provider/document/DocumentCopyMove.kt` | 수정 | `copy`(32-51) + 비공개 `checkSourceReadable` (2.5·2.6) |
| `app/src/test/java/me/zhanghai/android/files/provider/common/SourceCheckedReplaceTest.kt` | 신규 | 공용 함수 단위 테스트 (3.1) |
| `app/src/test/java/me/zhanghai/android/files/provider/common/ForeignCopyMoveCleanupTest.kt` | 수정 | `ForeignCopyMove` 회귀 테스트(수정 전 실패·수정 후 통과)와 fake 확장 (3.2) |

**파일 수 6**(운영 코드 4 + 테스트 2). HLD 2.1·6절은 C1을 5개(테스트 1)로 적었다. `ForeignCopyMove`는 JVM에서 시험할 수 있는 유일한 백엔드이고 그 fake(`FakeProvider`, 파일 안 `private`)가 이미 `ForeignCopyMoveCleanupTest.kt`에 있어, 수정 전 실패를 보이는 회귀 테스트를 그 파일에 더한다. 5개를 지키려면 (가) 회귀 테스트를 빼거나 (나) 공용 함수 테스트를 `ForeignCopyMoveCleanupTest.kt`에 함께 넣어야 한다 — 어느 쪽도 이 안보다 낫지 않아 6개로 한다. **PM이 6개를 승인했다(2026-11-19, 4절 F1)**.

### C2
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `provider/sftp/SftpCopyMove.kt` | 수정 | `copy`의 REGULAR(76-140)·DIRECTORY(141-158) 분기 (2.7) |
| `provider/smb/SmbCopyMove.kt` | 수정 | `copy`의 일반 파일(74-95)·폴더(96-113) 분기 (2.8) |
| `provider/smb/client/Client.kt` | 수정 | `copyFile`(318-426)에 지우기 람다 인자 (2.9) |
| `provider/ftp/FtpCopyMove.kt` | 수정 | `copy`(33-103)의 지우기 위치 (2.10) |
| `provider/webdav/WebDavCopyMove.kt` | 수정 | `copy`(38-114)의 지우기 위치 (2.11) |

파일 수 5. C2의 백엔드는 `object Client`가 실제 네트워크 연결을 만들어 JVM에서 시험할 수 없다(테스트 의존성은 JUnit뿐, `app/build.gradle:222`). 순서 규칙은 C1의 공용 함수 테스트가, 백엔드별 결선은 코드 검사와 서버 수동 시험(3.4)이 확인한다.

## 2. 함수 명세

### 2.1 `me.zhanghai.android.files.provider.common.replaceAfterOpeningSource` (C1, 신규)
```kotlin
@Throws(IOException::class)
fun <S, R> replaceAfterOpeningSource(
    openSource: () -> S,
    closeSource: (S) -> Unit,
    deleteExisting: (() -> Unit)?,
    write: (S) -> R
): R
```
- **추적**: HLD 2.1 "원본 확인 뒤 바꾸기(신규 공용 함수)", D3 / REQ-55-08, 09
- **책임**: 원본을 먼저 열고, 열렸을 때만 기존 대상을 지운 뒤, 연 원본으로 대상을 쓰게 하는 순서를 강제한다.
- **입력·전제**: 파일 작업 스레드에서 동기 호출. `deleteExisting == null`이면 지울 대상이 없거나 '바꾸기'가 아니다. 람다가 던지는 예외는 이미 `FileSystemException` 계열로 바뀌어 있다(0절). `closeSource`는 `openSource`가 돌려준 값에 한 번만 불린다.
- **출력·사후조건**: `write`의 반환값. 정상 종료면 `closeSource`가 정확히 한 번 불렸다.
- **오류**:
  - `openSource` 예외 → 그대로 전파. `deleteExisting`·`write`·`closeSource`는 불리지 않는다(①).
  - `deleteExisting`의 `NoSuchFileException` → 무시하고 계속(③). 그 밖의 예외 → `write` 없이 `closeSource` 후 그 예외 전파(⑤).
  - `write` 예외 → `closeSource` 후 전파.
  - 위 두 경우 `closeSource`의 예외는 원래 예외에 `addSuppressed`로 붙인다(원래 예외가 이긴다).
  - `write`가 성공하고 `closeSource`만 실패 → 그 예외 전파(Kotlin `Closeable.use`와 같은 규칙).
- **알고리즘**:
  ```
  s = openSource()                       // 실패 → 그대로 던짐
  result = try {
      if (deleteExisting != null)
          try { deleteExisting() } catch (NoSuchFileException) { /* Ignored. */ }
      write(s)
  } catch (t: Throwable) {
      try { closeSource(s) } catch (t2: Throwable) { t.addSuppressed(t2) }
      throw t
  }
  closeSource(s)
  return result
  ```
- **기존 코드와의 관계**: `createSymbolicLinkForCopy`(`provider/common/SymbolicLinkCopy.kt:19-46`)와 같은 자리·같은 방식(람다 주입, JVM 시험). 링크 분기는 그 함수를 계속 쓴다.

### 2.2 `me.zhanghai.android.files.provider.common.replaceAfterCheckingSource` (C1, 신규, 같은 파일)
```kotlin
@Throws(IOException::class)
fun <R> replaceAfterCheckingSource(
    checkSource: () -> Unit,
    deleteExisting: (() -> Unit)?,
    write: () -> R
): R = replaceAfterOpeningSource(checkSource, {}, deleteExisting) { write() }
```
- **추적**: HLD 2.1(폴더 '열어 보기', 문서 제공자 D4) / REQ-55-08
- **책임**: 원본을 연 핸들을 쓰지 않는 경로(폴더, 문서 제공자)에서 "열어 보고 닫기 → 지우기 → 쓰기" 순서를 강제한다.
- **입력·전제**: `checkSource`는 스스로 열고 닫는다. 지울 대상이 없을 때 비용을 내지 않으려면 호출자가 `checkSource` 안에서 건너뛴다(각 백엔드 절).
- **출력·오류**: 2.1과 같다(`closeSource`는 아무것도 하지 않는다).

### 2.3 `LinuxCopyMove.copy(source: ByteString, target: ByteString, copyOptions: CopyOptions)` (C1, 수정)
- **추적**: HLD 2.1 "로컬·루트" / REQ-55-08
- **책임**: 일반 파일·폴더의 '바꾸기'에서 원본 확인 뒤 대상을 지운다.
- **입력·전제**: 그대로. `sourceStat`(33-37)·`targetStat`(38-46)·같은 inode·`FileAlreadyExistsException`(47-56) 그대로.
- **알고리즘**:
  - 지우기 람다(두 분기 공용, `targetStat != null`일 때만 만든다, 아니면 `null`):
    `{ try { Syscall.remove(target) } catch (e: SyscallException) { throw e.toFileSystemException(target.toString()) } }` — ENOENT는 `NoSuchFileException`이 되어(`linux/syscall/SyscallException.kt:66`) 공용 함수가 무시한다. 지금 58-66의 "ENOENT 무시"와 같다.
  - `S_ISREG`(57): `replaceAfterOpeningSource(`
    - `openSource = { Syscall.open(source, O_RDONLY, 0) }`(지금 67-71, 예외 변환 그대로),
    - `closeSource = { fd -> Syscall.close(fd) }`(지금 130-134, 예외 변환 그대로),
    - `deleteExisting =` 위 람다,
    - `write = { sourceFd -> 지금 73-128 그대로 }`(대상 열기·`sendfile`·대상 닫기·실패 시 정리)`)`.
  - `S_ISDIR`(136): `replaceAfterCheckingSource(`
    - `checkSource = { if (targetStat != null) { val dir = Syscall.opendir(source) (변환: source); Syscall.closedir(dir) (변환: source) } }`,
    - `deleteExisting =` 위 람다,
    - `write = { 지금 146-152 그대로(mkdir, 진행 알림) }`)`.
  - `S_ISLNK`(153-177)·그 밖(178-180)·속성 단계(181-) 그대로.
- **오류**: 원본 열기·`opendir` 실패 → 그 `FileSystemException`(예: `AccessDeniedException`), 대상 그대로. 그 밖은 지금과 같다.
- **동작 차이(성공 경로 없음)**: 원본 닫기 실패가 쓰기 실패와 겹치면 지금은 닫기 예외가 이기지만(130-134의 `finally`), 이제 쓰기 예외가 이기고 닫기 예외는 suppressed다. `closedir` 실패는 지우기 전이라 대상이 남는다.
- **기존 코드와의 관계**: `Syscall.opendir`·`closedir`(`linux/syscall/Syscall.kt:181`, 46). 212-224(xattr, #48)는 건드리지 않는다(SRS 5절).

### 2.4 `ForeignCopyMove.copy(source: Path, target: Path, vararg options: CopyOption)` (C1, 수정)
- **추적**: HLD 2.1 "공급자 간" / REQ-55-08
- **책임**: 공급자 간 복사의 '바꾸기'에서 원본 확인 뒤 대상을 지운다.
- **입력·전제**: 23-40 그대로. 이 함수는 `REPLACE_EXISTING`이면 대상 존재를 묻지 않는다(38) — 그대로 둔다. 복사 작업에서 `REPLACE_EXISTING`은 충돌 뒤 다시 시도에만 붙으므로(`FileJobs.kt:1391-1394`, 1416-1418) 대상 확인 왕복을 더하지 않는다.
- **알고리즘**:
  - 지우기 람다: `copyOptions.replaceExisting`이면 `{ target.deleteIfExists() }`, 아니면 `null`.
  - `isRegularFile`(42): `replaceAfterOpeningSource(`
    - `openSource = { source.newInputStream(*openOptions) }`(46-51의 `openOptions` 그대로),
    - `closeSource = { it.close() }`,
    - `deleteExisting =` 위 람다,
    - `write = { inputStream -> 지금 52-83 그대로 }`)` — 지금의 `use { }`(51)는 공용 함수의 닫기로 바뀐다.
  - `isDirectory`(86): `replaceAfterCheckingSource(`
    - `checkSource = { if (copyOptions.replaceExisting) source.newDirectoryStream().close() }`(`provider/common/PathExtensions.kt:152`),
    - `deleteExisting =` 위 람다,
    - `write = { target.createDirectory(); progressListener?.invoke(size) }`(90-91)`)`.
  - 링크 분기(93-106)·속성 단계(109-130)·`move`(133-174) 그대로.
- **오류**: `newInputStream`·`newDirectoryStream` 실패 → 그 예외, 대상 그대로.
- **비용**: 폴더 원본의 '바꾸기'(대상이 일반 파일·링크일 때만 생긴다 — 폴더끼리는 합치기, `FileJobs.kt:1381-1386`)에서 목록 열기 1회.

### 2.5 `DocumentCopyMove.copy(source: DocumentPath, target: DocumentPath, copyOptions: CopyOptions): Uri` (C1, 수정)
- **추적**: HLD 2.1 "문서 제공자", D4 / REQ-55-08
- **책임**: 같은 문서 제공자 안 복사의 '바꾸기'에서 원본을 열어 보고 닫은 뒤 대상을 지운다.
- **알고리즘**:
  ```
  (20-31 그대로)
  val targetExists = DocumentResolver.exists(target)           // 32
  if (targetExists && !copyOptions.replaceExisting) throw FileAlreadyExistsException(target.toString())
  return replaceAfterCheckingSource(
      checkSource = { if (targetExists) checkSourceReadable(source) },
      deleteExisting = if (targetExists) {
          { try { DocumentResolver.remove(target) }
            catch (e: ResolverException) { throw e.toFileSystemException(target.toString()) } }
      } else null,
      write = { 지금 43-51 그대로(DocumentResolver.copy, 예외 변환) }
  )
  ```
- **오류**: 확인 실패 → `ResolverException.toFileSystemException(source)`(또는 스트림 닫기의 `IOException`), 대상 그대로. 지우기의 `NoSuchFileException`은 이제 무시한다(지금은 38-41이 던진다) — `exists`와 `remove` 사이에 대상이 사라진 경우로, 결과는 '바꾸기'의 목적과 같다.
- **기존 코드와의 관계**: `move`(55-85)는 이동이라 바꾸지 않는다(SRS 1.3, HLD 1절).

### 2.6 `DocumentCopyMove.checkSourceReadable(source: DocumentPath)` (C1, 신규 private)
```kotlin
@Throws(IOException::class)
private fun checkSourceReadable(source: DocumentPath)
```
- **추적**: HLD 2.1 문서 제공자 행, D4, 6절(클라우드 열어 보기 비용) / REQ-55-08
- **책임**: 원본이 폴더면 자식 목록을, 아니면 내용을 읽기로 열 수 있는지 확인하고 바로 닫는다.
- **알고리즘**:
  ```
  try {
      if (DocumentResolver.getMimeType(source) == MimeType.DIRECTORY.value) {
          DocumentResolver.queryChildren(source)                 // 커서는 안에서 닫힌다
      } else {
          DocumentResolver.openInputStream(source, "r").close()  // 읽지 않고 바로 닫는다
      }
  } catch (e: ResolverException) {
      throw e.toFileSystemException(source.toString())
  }
  ```
  MIME이 `null`이면 일반 파일로 본다(`DocumentResolver.copyManually`가 `MimeType.GENERIC`으로 보는 것과 같다, `document/resolver/DocumentResolver.kt:149-155`).
- **기존 코드와의 관계**: `DocumentResolver.getMimeType(path)`(232), `queryChildren`(410), `openInputStream`(389), `file.MimeType`. 바로 닫으므로 HLD 6절의 클라우드 내려받기 비용은 열기 1회로 끝난다(제공자 구현에 따름, 미확인). 확인과 `copyDocument` 사이의 틈은 HLD 6절 그대로 남는다.
- **알려진 한계 (PM 결정 2026-11-19, 검수 L2)**: 같은 제공자 복사(`copyApi24`의 `copyDocument`, `DocumentResolver.kt:85-90`)는 원본을 스트림으로 열지 않는다. 그래서 스트림으로는 열 수 없지만 `copyDocument`로는 복사되는 원본(가상 문서 등)은 대상이 있을 때의 '바꾸기'에서 이 확인 때문에 새로 실패한다(대상은 남는다). 대상이 없는 복사는 확인을 하지 않아 영향이 없다. 받아들인 동작이며 M-C4와 함께 미확인이다. `queryChildren`은 제공자가 로딩 중이면 기다린다(`DocumentResolver.kt:419-422`).

### 2.7 `SftpCopyMove.copy(source: SftpPath, target: SftpPath, copyOptions: CopyOptions)` (C2, 수정)
- **추적**: HLD 2.1 "SFTP" / REQ-55-08
- **알고리즘**:
  - 지우기 람다(`targetAttributes != null`일 때만): `{ try { Client.remove(target) } catch (e: ClientException) { throw e.toFileSystemException(target.toString()) } }`(지금 77-86·142-151의 "없음 무시"는 공용 함수가 한다).
  - REGULAR(76): `replaceAfterOpeningSource(`
    - `openSource = { Client.openByteChannel(source, READ, FileAttributes.EMPTY) (변환: source) .newInputStream() }`(87-91),
    - `closeSource = { try { it.close() } catch (e: IOException) { throw ClientException(e).toFileSystemException(source.toString()) } }`(134-138),
    - `write = { sourceInputStream -> 지금 93-132 그대로 }``)`.
  - DIRECTORY(141): `replaceAfterCheckingSource(checkSource = { if (targetAttributes != null) Client.scandir(source) (변환: source) }, deleteExisting, write = { 152-157 그대로 })`.
  - SYMLINK·그 밖·속성 단계(186-217)·`sourceModeAttributes`(68-74) 그대로(조각 A가 고친다).
- **기존 코드와의 관계**: `sftp/client/Client.kt:119` `scandir`는 목록 전체를 읽는다(SSH 디렉터리 핸들만 여는 함수가 `Client`에 없다 — 더하면 C2가 6개 파일이 된다). '바꾸기'이고 원본이 폴더이며 대상이 파일·링크일 때만 생기는 비용이다.

### 2.8 `SmbCopyMove.copy(source: SmbPath, target: SmbPath, copyOptions: CopyOptions)` (C2, 수정)
- **추적**: HLD 2.1 "SMB" / REQ-55-08
- **알고리즘**:
  - 지우기 람다(`targetInformation != null`일 때만): `{ try { Client.delete(target) } catch (e: ClientException) { throw e.toFileSystemException(target.toString()) } }`.
  - 일반 파일(74): 지금 75-84의 지우기를 없애고 `Client.copyFile(…, deleteExistingTarget = 위 람다)`(85-95). 지우기는 `copyFile` 안에서 원본을 연 뒤 일어난다(2.9). `copyFile` 둘레의 `catch (e: ClientException)`(91-95)는 그대로 — 람다가 던지는 `FileSystemException`은 `ClientException`이 아니므로(`smb/client/ClientException.kt:21` `: Exception`) 바뀌지 않고 그대로 나간다.
  - 폴더(96): `replaceAfterCheckingSource(checkSource = { if (targetInformation != null) Client.openDirectoryIterator(source).close() (변환: source) }, deleteExisting, write = { 107-113 그대로 })`.
  - 재분석 지점(114-138)·속성 단계 그대로.
- **기존 코드와의 관계**: `smb/client/Client.kt:89` `openDirectoryIterator`(`CloseableIterator`, `provider/common/CloseableIterator.kt:10`).

### 2.9 `smb.client.Client.copyFile(…, deleteExistingTarget: (() -> Unit)?)` (C2, 수정)
```kotlin
/**
 * ...(기존 설명)
 *
 * [deleteExistingTarget], if not null, is invoked after the source is opened and before the
 * target is created. Exceptions it throws (already converted [java8.nio.file.FileSystemException],
 * not [ClientException]) are propagated as is.
 */
@Throws(ClientException::class, IOException::class)
fun copyFile(
    source: Path, target: Path, copyAttributes: Boolean, openReparsePoint: Boolean,
    intervalMillis: Long, listener: ((Long) -> Unit)?, cleanupFailureListener: (() -> Unit)?,
    deleteExistingTarget: (() -> Unit)?
)
```
- **추적**: HLD 2.1 "SMB"("인자로 지우기 함수를 받거나 — LLD": 지우기 함수를 받는다) / REQ-55-08
- **책임**: 원본을 연 뒤, 대상을 `FILE_CREATE`로 만들기 전에 기존 대상을 지운다.
- **입력·전제**: 호출자는 `SmbCopyMove.copy` 하나뿐(`smb/SmbCopyMove.kt:86`). `deleteExistingTarget`은 `FileSystemException`을 던진다(2.8).
- **알고리즘**: 327-334 그대로. 그 뒤 `replaceAfterOpeningSource(`
  - `openSource = { sourceShare.openFile(…) }`(335-346, `SMBRuntimeException` → `ClientException` 그대로),
  - `closeSource = { it.close() }`(지금 `sourceFile.use`, 351),
  - `deleteExisting = deleteExistingTarget`,
  - `write = { sourceFile -> 지금 352-421 그대로 }``)`. 바깥 `catch (e: SMBRuntimeException)`(423-425) 그대로.
- **오류**: 원본 열기 실패(`STATUS_ACCESS_DENIED` 등) → `ClientException`, 지우기 없음. 지우기 실패 → 그 `FileSystemException`, 대상 만들기 없음, 원본 닫힘.
- **기존 코드와의 관계**: 원본은 `SMB2ShareAccess.ALL`로 열려(340) 같은 공유의 대상 지우기(`delete` 266-, `FILE_DELETE_ON_CLOSE`)와 충돌하지 않는다. 원본과 대상이 같은 파일이면 `SmbCopyMove` 52-56에서 이미 끝난다.

### 2.10 `FtpCopyMove.copy(source: FtpPath, target: FtpPath, copyOptions: CopyOptions)` (C2, 수정)
- **추적**: HLD 2.1 "FTP" / REQ-55-08, #56
- **알고리즘**:
  ```
  (19-40 그대로; 41-45의 지우기를 없앤다)
  val deleteExisting = if (targetFile != null) {
      { try { Client.delete(target, targetFile.isDirectory) }
        catch (e: IOException) { throw e.toFileSystemExceptionForFtp(target.toString()) } }
  } else null
  when {
      sourceFile.isDirectory -> replaceAfterCheckingSource(
          checkSource = { if (targetFile != null) Client.listDirectory(source) (변환: source) },
          deleteExisting, write = { 49-54 그대로 })
      sourceFile.isSymbolicLink ->
          throw UnsupportedOperationException("Cannot copy symbolic links")   // 이제 지우기 전 (#56)
      else -> replaceAfterOpeningSource(
          openSource = { Client.retrieveFile(source) (변환: source) },        // 59-63
          closeSource = { try { it.close() } catch (e: IOException) { throw e.toFileSystemExceptionForFtp(source.toString()) } },  // 96-100
          deleteExisting, write = { sourceInputStream -> 64-94 그대로 })
  }
  (104-115 그대로)
  ```
- **오류·주의**:
  - FTP 응답 550은 '없음'과 '거부'를 가리지 못하고 `NoSuchFileException`이 된다(`ftp/client/NegativeReplyCodeException.kt:19`). 그래서 대상 지우기가 550으로 거부되면 공용 함수가 무시하고 쓰기로 간다(지금은 그 자리에서 실패). 결과: `STOR`가 덮어쓰기를 허용하면 '바꾸기'가 이뤄지고(사용자가 고른 결과), 허용하지 않으면 `storeFile`(65-69)이 정리 단계(`try` 77-94) **밖**에서 실패해 대상이 남는다. 폴더 원본이면 `MKD`가 실패하고 대상이 남는다. 어느 경우도 지우지 않은 데이터를 잃지 않는다. 이 차이를 PR 강조점에 적는다.
  - 원본 `RETR` 연결을 연 채 지우기·쓰기가 풀의 다른 연결을 쓴다(`ftp/client/Client.kt:46-66`, 222-233). 연결 수를 제한한 서버에서는 지우기 전에 실패하고 대상이 남는다(HLD 6절).
- **기존 코드와의 관계**: `move`(118-)는 바꾸지 않는다.

### 2.11 `WebDavCopyMove.copy(source: WebDavPath, target: WebDavPath, copyOptions: CopyOptions)` (C2, 수정)
- **추적**: HLD 2.1 "WebDAV" / REQ-55-08, #56
- **알고리즘**: 2.10과 같은 모양.
  - 46-50의 지우기를 없애고 `deleteExisting = if (targetFile != null) { { try { Client.delete(target) } catch (e: DavException) { throw e.toFileSystemException(target.toString()) } } } else null`.
  - `isDirectory` → `replaceAfterCheckingSource(checkSource = { if (targetFile != null) Client.findCollectionMembers(source) (변환: source) }, deleteExisting, write = { 54-59 그대로 })`.
  - `isSymbolicLink` → 그대로 던짐(이제 지우기 전, #56).
  - 그 밖 → `replaceAfterOpeningSource(openSource = { Client.get(source) (변환: source) }, closeSource = { try { it.close() } catch (e: DavException) { throw e.toFileSystemException(source.toString()) } }, deleteExisting, write = { 69-105 그대로 })`.
- **기존 코드와의 관계**: `Client.get`은 `GET`을 보내고 상태를 확인한 뒤 본문 스트림을 준다(`webdav/client/DavResourceCompat.kt:34-35` `checkStatus`) — 403·404면 지우기 전에 실패한다. `findCollectionMembers`(`webdav/client/Client.kt:137`)는 PROPFIND 깊이 1. 닫기 예외 처리는 지금(107-111)과 같게 `DavException`만 바꾼다.

## 3. 테스트 설계

### 3.1 공용 함수 — `SourceCheckedReplaceTest` (C1, JVM, 신규)
람다로 호출 순서를 `calls` 목록에 기록한다(`SymbolicLinkCopyTest.kt:20-40`과 같은 방식). 공용 함수가 없던 수정 전 코드에는 이 테스트가 컴파일되지 않으므로 '수정 전 실패' 확인은 3.2가 맡는다.

| 테스트 | 대상 함수 | 경우 | 기대 결과 |
|---|---|---|---|
| U-C01 `openFails_noDeleteNoWriteNoClose` | 2.1 | 열기가 `AccessDeniedException` (HLD ①) | 같은 예외 인스턴스, `calls == [open]` |
| U-C02 `replace_opensThenDeletesThenWritesThenCloses` | 2.1 | 지우기 람다 있음, 모두 성공 (②) | `calls == [open, delete, write, close]`, `write` 반환값을 돌려줌 |
| U-C03 `deleteNoSuchFile_ignored` | 2.1 | 지우기가 `NoSuchFileException` (③) | 예외 없음, `[open, delete, write, close]` |
| U-C04 `noReplace_noDelete` | 2.1 | `deleteExisting = null` (④) | `[open, write, close]` |
| U-C05 `deleteFails_noWrite_sourceClosed` | 2.1 | 지우기가 `AccessDeniedException` (⑤) | 같은 예외, `[open, delete, close]` |
| U-C06 `writeFails_sourceClosed_closeFailureSuppressed` | 2.1 | 쓰기 `IOException` + 닫기 `IOException` | 쓰기 예외가 나가고 `suppressed`에 닫기 예외, `[open, write, close]` |
| U-C07 `closeFailsAfterWrite_thrown` | 2.1 | 쓰기 성공, 닫기 `IOException` | 닫기 예외 |
| U-C08 `check_failsBeforeDelete` | 2.2 | 확인이 `AccessDeniedException` | 같은 예외, `[check]` |
| U-C09 `check_thenDeleteThenWrite` | 2.2 | 확인 성공, 지우기 있음 | `[check, delete, write]`, 반환값 전달 |

### 3.2 공급자 간 회귀 — `ForeignCopyMoveCleanupTest` 확장 (C1, JVM)
fake 확장: `FakeProvider`에 `inputStreamException`, `directoryStreamException`, `sourceIsDirectory`(원본 속성을 폴더로), `events`(호출 순서: `open:<path>`, `delete:<path>`, `dirstream:<path>`, `mkdir:<path>`, `write`)를 더한다. `newDirectoryStream`(지금 `UnsupportedOperationException`)은 `directoryStreamException`이 없으면 빈 스트림, `createDirectory`는 기록만 한다. 기존 U-M01–06은 기본값에서 지금과 같게 동작해야 한다.

| 테스트 | 경우 | 기대 결과 | 수정 전 코드에서 |
|---|---|---|---|
| U-C10 `copy_replace_sourceUnreadable_targetKept` | `REPLACE_EXISTING`, `newInputStream`이 `AccessDeniedException` | 같은 예외, `deletedPaths` 비어 있음 | **실패**(`deletedPaths == [target]`, 43-45가 먼저 지움) |
| U-C11 `copy_replace_opensSourceBeforeDeletingTarget` | `REPLACE_EXISTING`, 모두 성공 | `events`에서 `open:/source/file`이 `delete:/target/file`보다 앞 | **실패**(지우기가 먼저) |
| U-C12 `copy_replace_sourceDirectoryUnlistable_targetKept` | `sourceIsDirectory`, `REPLACE_EXISTING`, `newDirectoryStream`이 `AccessDeniedException` | 같은 예외, `deletedPaths` 비어 있음, `mkdir` 없음 | **실패**(87-89가 먼저 지움) |
| U-C13 `copy_replace_directory_checksThenDeletesThenCreates` | `sourceIsDirectory`, `REPLACE_EXISTING`, 성공 | `events == [dirstream:/source/file, delete:/target/file, mkdir:/target/file]` | **실패**(`dirstream` 없음) |
| U-C14 `copy_noReplace_directory_noCheck` | `sourceIsDirectory`, 옵션 없음 | `dirstream` 없음, `mkdir` 1회 | 통과(회귀 방지) |

구현 PR은 U-C10–13을 **수정 전 코드에서 먼저 돌려 실패를 확인하고**, 수정 뒤 통과를 확인해 두 출력의 끝부분을 PR 코멘트에 붙인다(PROCESS_ROLES 11-1 5번).

### 3.3 실행
- JVM: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "me.zhanghai.android.files.provider.common.*" --console=plain` 후 전체 `testDebugUnitTest`, 그리고 `assembleDebug`.
- 코드 검사(검수자): C1은 2.3–2.5, C2는 2.7–2.11의 모든 대상 지우기 호출이 공용 함수의 `deleteExisting`으로만 일어나고, 그 앞에 원본 열기·확인이 있다(REQ-55-08 코드 검사 항목). `FileJobErrorDialogFragment.kt`·`FileJobConflictDialogFragment.kt` 변경 없음(REQ-55-09).

### 3.4 수동 시험 (에뮬레이터·서버)
| 시험 | 조각 | 환경 | 절차 | 기대 |
|---|---|---|---|---|
| M-C1 로컬 읽기 권한 없는 원본 | C1 | 에뮬레이터, 디버그 빌드(디버그 가능 → `run-as`) | `adb shell run-as me.zhanghai.android.files sh -c 'mkdir -p files/src files/dst && echo new > files/src/a && chmod 000 files/src/a && echo keep > files/dst/a'` → 앱에서 `/data/user/0/me.zhanghai.android.files/files/src/a`를 `files/dst`로 복사 → 충돌 창 '바꾸기' | 오류 창(권한 거부), `run-as … cat files/dst/a` = `keep`. 수정 전 빌드에서는 `a`가 지워진다 |
| M-C2 로컬 폴더 원본 | C1 | 같음 | `files/src/d`(폴더, `chmod 000`)를 일반 파일 `files/dst/d`가 있는 곳으로 복사 → '바꾸기' | 오류, `files/dst/d` 남음 |
| M-C3 공급자 간 | C1 | 에뮬레이터 | Linux 경로의 읽기 권한 없는 원본(M-C1의 `a`)을 SAF로 연 폴더(같은 이름 `a` 있음)로 복사 → '바꾸기' | 오류, SAF의 `a` 남음. 준비가 안 되면 PR에 "미확인", U-C10–13이 같은 순서를 확인 |
| M-C4 문서 제공자 | C1 | 에뮬레이터 | 같은 SAF 트리 안에서 원본을 읽을 수 없게 만들 방법이 없다(제공자가 권한을 정함) | **미확인**(코드 검사로 갈음). 2.6의 가상 문서 한계도 미확인으로 둔다(PM 결정). 정상 '바꾸기'가 지금처럼 되는지만 확인 |
| M-C5 회귀 | C1 | 에뮬레이터 | 내부 저장소 파일·폴더의 정상 '바꾸기', 파일 링크 '바꾸기' | 지금과 같은 결과 |
| M-C6 SFTP | C2 | SFTP 서버 | 서버에 `chmod 000 src/a`, `dst/a` 있음 → 앱에서 `src/a`를 `dst`로 '바꾸기'. 폴더(`chmod 000 src/d`, `dst/d`는 파일)도 | 오류, `dst/a`·`dst/d` 남음 |
| M-C7 SMB | C2 | SMB 서버 | 읽기 거부 ACL 파일로 M-C6과 같게 | 같음 |
| M-C8 FTP | C2 | FTP 서버 | (a) M-C6과 같게. (b) 링크 원본(서버 쪽 링크)을 같은 이름 대상 위로 '바꾸기'. (c) 대상 `dst/a`의 삭제(`DELE`)는 거부하고 덮어쓰기(`STOR`)는 허용하는 서버 설정에서 '바꾸기'. (d) 삭제·덮어쓰기를 모두 거부하는 설정에서 '바꾸기' | (a) 오류·대상 남음. (b) "Cannot copy symbolic links"·**대상 남음**(#56, 지금은 지워짐). (c) 550을 무시하고 `STOR`로 덮어써 '바꾸기' 성공(2.10). (d) `storeFile` 오류·대상 남음(지금은 삭제 단계의 550 오류). 서버 설정을 만들 수 없으면 (c)(d)는 미확인 |
| M-C9 WebDAV | C2 | WebDAV 서버 | 403을 주는 원본(서버 권한 설정)으로 M-C6과 같게 | 같음 |

서버가 없으면 M-C6–9는 PR에 "미확인"으로 적는다(SRS 6절). 오너 기기에는 설치하지 않는다(사전 승인 필요).

## 4. 설계 피드백
- **F1 (PM 판단, 범위 알림)**: C1의 파일 수가 6개다(1절). HLD 2.1 나눔 표·6절 "C1 5"는 테스트 1개를 가정했다. 운영 코드 4개는 HLD와 같고 늘어난 것은 테스트 파일 하나다. **PM 결정(2026-11-19): 6개 승인.** HLD 수정은 요청하지 않는다(설계 동작 변화 없음).
- **F2 (경미, 설계팀 참고)**: HLD 2.1 공용 함수 행의 "`provider/common/SymbolicLinkCopy.kt:271-297`"는 실제로 19-46이다(파일이 46줄). 내용(람다 주입 방식)은 맞아 LLD는 19-46을 쓴다. 다음 HLD 갱신 때 고치면 된다.
- **F3 (LLD 결정, 기록)**: HLD는 SMB에 "지우기 함수를 받거나 '바꾸기' 여부를 받는다 — LLD"를 남겼다. 지우기 함수를 받는다(2.9): 예외 변환(`ClientException` → `FileSystemException`)이 `SmbCopyMove`에 그대로 남고 `Client`가 '바꾸기' 의미를 몰라도 된다.
- **F4 (LLD 결정, 기록)**: FTP 550 모호성(2.10). 공용 함수의 '없음 무시'(HLD ③)를 FTP에도 그대로 쓴다. 덮어쓰기가 거부되면 대상이 남으므로 REQ-55-08을 해치지 않는다. **PM 결정(2026-11-19, 검수 L4): 받아들인다.** 결과·오류 메시지 변화는 PR 강조점에 적고 M-C8 (c)(d)로 확인한다.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1 | 최초 작성(조각 C1·C2) | PM LLD 요청(조각 C만) | PM |
| 2026-11-19 | v1.1 | 0절·1절의 LLD 절 번호 참조 정정, 2.9 `copyFile`에 람다 예외 KDoc·`@Throws(IOException)` 추가 | LLD 검수 1차 조건부 통과 L1·L3 [기계] (PR #64 코멘트). L2·L4 [판단]은 PM 판단 대기 | reviewer |
| 2026-11-19 | v1.2 | F1(C1 6파일) 승인 기록, 2.6에 가상 문서 한계(L2) 추가·M-C4에 포함, F4(L4) 승인 기록·M-C8에 삭제 거부 + STOR 허용/불허 두 경우 추가, 상태 행 | PM decision 2026-11-19 (PR #64 코멘트) | PM |
