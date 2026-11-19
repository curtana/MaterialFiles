# LLD — 조각 A: 분류 + 네트워크 속성 규칙 (이슈 #55)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 조각 A1·A2의 영문 작업지시서와 구현·검수의 기준이다. 조각 C는 `LLD-C.md`, 조각 B는 따로 쓴다(HLD 2.4).

| 항목 | 내용 |
|---|---|
| 상태 | 검수 조건부 통과 (v1), 경미 m1·m2 반영 (v1.1), A2 줄 번호를 C2 머지 뒤 90f2c1e로 (v1.2) |
| 근거 | SRS v1.4 (2절 Q1·Q3·Q5, REQ-55-01·03, 8절 D1), HLD v1.3 (1, 2.2 조각 A, 2.4, 3.1, 4, 5 D1·D2) |
| 기준 코드 | A1(1·2.1–2.7절): `origin/develop` f899359(C1 머지 뒤, PR #66). A2(2.8–2.11절): `origin/develop` 90f2c1e(C2 머지 뒤, PR #71). A1 파일(`filejob/`, `provider/common/`)은 두 커밋 사이에 바뀌지 않았다(`git diff --stat f899359 90f2c1e -- app/src/main/java/me/zhanghai/android/files/filejob app/src/main/java/me/zhanghai/android/files/provider/common` 비어 있음). 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준. 줄과 함께 코드 앵커(주석·식)도 적는다 |
| 추적 REQ | REQ-55-01, REQ-55-03 (REQ-55-02의 속성 부분: 폰→폰은 지금 그대로) |
| 나눔 | **A1**(분류·옵션·`FileJobs`·공급자 간) → **A2**(SFTP·SMB·FTP·WebDAV). PR을 따로 하고 A1과 C2가 develop에 머지된 뒤 A2를 시작한다(HLD 2.4 `C1 → C2 → A1 → A2`) |

## 0. 공통 규칙

1. **분류는 한 곳**(REQ-55-01, HLD D1): `filejob/CopyClassification.kt`의 `classifyCopy(source, targetDirectory)`만 폰→폰/네트워크를 정한다. 복사 시작 항목마다 한 번 부르고, 그 아래 하위 항목은 같은 값을 물려받는다(SRS D1).
2. **백엔드에는 옵션으로만** 전한다(HLD D1): 네트워크 복사면 `FileJobs.copyOrMove`의 옵션 배열에 새 옵션 `SkipAttributesCopyOption`을 더한다. 폰→폰·이동은 지금과 같은 옵션이다(`NOFOLLOW_LINKS`도 A에서는 그대로 — 조각 B2가 바꾼다).
3. **옵션의 뜻**: "대상에 원본의 속성을 하나도 지정하지 않는다." 곧 (a) 복사 뒤 속성 단계(시각·권한·소유자·xattr)를 하지 않고 (b) 대상을 **만들 때** 원본 권한을 넘기지 않는다(Q5 안 B). `copyAttributes`보다 우선한다 — 다만 복사는 언제나 `copyAttributes = false`이고 이동은 이 옵션을 받지 않으므로(`FileJobs.kt:1232-1237`, 1245-1252) 둘이 함께 오는 일은 없다.
4. **백엔드의 처리 모양**: 다섯 백엔드(`ForeignCopyMove`, SFTP, SMB, FTP, WebDAV) 모두 `copy`의 끝에 있는 속성 단계 바로 앞(주석 `// We don't take error when copying attribute fatal, ...` 앞)에 `if (copyOptions.skipAttributes) return`을 둔다. 만들 때 권한을 넘기는 곳은 SFTP뿐이다(2.8). SMB·FTP·WebDAV·공급자 간은 만들 때 원본 권한을 넘기지 않는다(SRS 2.5 끝, HLD 2.2).
5. **모르는 옵션 예외가 없어야 한다**: 모든 제공자의 `copy`는 `toCopyOptions()`(`provider/common/CopyOptions.kt:46-77`)를 지난다. A1에서 이 함수가 새 옵션을 알아보므로, A2 전(A1만 머지된 상태)에도 SFTP·SMB·FTP·WebDAV·문서 제공자 `copy`는 옵션을 받고 무시할 뿐 실패하지 않는다(HLD 2.2 A1 "NAS 안 복사는 아직 맞춘다").

**바뀌지 않는 것**: `LinuxCopyMove`(Linux→Linux는 언제나 폰→폰), `DocumentCopyMove`(같은 제공자 안 복사에 속성 단계가 없다, `provider/document/DocumentCopyMove.kt`), 이동 경로(`MoveFileJob`, `copyForMove`, `moveByCopy`, 각 백엔드 `move`와 그 안에서 새로 만드는 `CopyOptions(...)` — 새 필드는 기본값 `false`), `scan`, 충돌 판정·창, 순회, 문자열, 링크 동작(B2).

## 1. 변경 파일

### A1
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `filejob/CopyClassification.kt` | 신규 | `CopyKind`, `CopyLocation`, `CopyLocation.isPhone`, 순수 `classifyCopy(CopyLocation, CopyLocation)`, 경로용 `classifyCopy(Path, Path)`, 비공개 `Path.copyLocation` (2.1–2.4) |
| `provider/common/CopyOptions.kt` | 수정 | `SkipAttributesCopyOption`, `CopyOptions.skipAttributes`, `toArray()`·`toCopyOptions()` (2.5) |
| `filejob/FileJobs.kt` | 수정 | `CopyFileJob.run`·`copyRecursively`, `copy`, `copyOrMove`, `open`, `save`, `copyForMove`·`moveByCopy`의 인자 (2.6) |
| `provider/common/ForeignCopyMove.kt` | 수정 | `copy`의 속성 단계 앞 생략 (2.7) |
| `app/src/test/java/me/zhanghai/android/files/filejob/CopyClassificationTest.kt` | 신규 | 분류 규칙 표 전체, 옵션 왕복 (3.1) |
| `app/src/test/java/me/zhanghai/android/files/provider/common/ForeignCopyMoveCleanupTest.kt` | 수정 | `ForeignCopyMove` 회귀 테스트(수정 전 실패·수정 후 통과)와 fake 확장 (3.2) |

**파일 수 6**(운영 코드 4 + 테스트 2). HLD 2.2 나눔 표·6절은 A1을 5개(테스트 1)로 적었다. 늘어난 것은 테스트 하나로, 조각 C1과 같은 이유다(LLD-C F1): JVM에서 시험할 수 있는 백엔드는 `ForeignCopyMove`뿐이고 그 fake(`FakeProvider`, 파일 안 `private`)가 `ForeignCopyMoveCleanupTest.kt`에 있다. 5개로 줄이면 '수정 전 실패'를 보이는 회귀 테스트를 빼야 한다 — PM 판단을 요청한다(4절 F1).

### A2
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `provider/sftp/SftpCopyMove.kt` | 수정 | `copy`: 만들 때 권한(`sourceModeAttributes`)과 속성 단계(`setstat`) 생략 (2.8) |
| `provider/smb/SmbCopyMove.kt` | 수정 | `copy`: 속성 단계(`setFileInformation`) 생략 (2.9) |
| `provider/ftp/FtpCopyMove.kt` | 수정 | `copy`: 속성 단계(`setLastModifiedTime`) 생략 (2.10) |
| `provider/webdav/WebDavCopyMove.kt` | 수정 | `copy`: 속성 단계(`setLastModifiedTime`) 생략 (2.11) |

파일 수 4. 네 백엔드는 `object Client`가 실제 네트워크 연결을 만들어 JVM에서 시험할 수 없다(테스트 의존성은 JUnit뿐, `app/build.gradle:222`; LLD-C 1절 C2와 같다). 옵션 해석은 A1의 테스트가, 백엔드 결선은 코드 검사와 서버 수동 시험(3.4)이 확인한다. SFTP의 생성 속성 식만 떼어 JVM 시험하는 안은 4절 F3에서 버렸다.

**C2와의 관계**: A2의 네 파일은 C2가 모두 고친다(LLD-C 2.7–2.11). C2가 바꾸는 곳은 '바꾸기' 순서(대상 지우기·원본 열기)이고 A2가 바꾸는 곳은 (SFTP) `sourceModeAttributes` 정의와 각 파일 끝의 속성 단계다 — 줄이 겹치지 않는다. C2 뒤에도 `sourceModeAttributes`는 C2의 `write` 람다 안에서 그대로 쓰인다(LLD-C 2.7 "`sourceModeAttributes`(68-74) 그대로(조각 A가 고친다)"). C2는 90f2c1e(PR #71)로 머지됐다. 2.8–2.11의 줄 번호는 90f2c1e 기준이다. A2는 A1 머지 뒤 develop에서 분기한다.

## 2. 함수 명세

### 2.1 `me.zhanghai.android.files.filejob.CopyKind` (A1, 신규)
```kotlin
/** Which copy rules apply to a copy (#55): `cp` defaults or network defaults. */
internal enum class CopyKind {
    PHONE_TO_PHONE,
    NETWORK
}
```
- **추적**: HLD 2.2 "복사 분류" / REQ-55-01
- **책임**: 분류 결과를 나타낸다. 조각 B2가 같은 값으로 링크 따라가기를 정한다.

### 2.2 `me.zhanghai.android.files.filejob.CopyLocation` + `CopyLocation.isPhone` (A1, 신규, 순수)
```kotlin
/** Where a path lives, as far as [classifyCopy] cares. Pure, so the rule is testable on the JVM. */
internal sealed interface CopyLocation {
    data object Linux : CopyLocation
    data class Archive(val archiveFile: CopyLocation) : CopyLocation
    data class Document(val isLocal: Boolean, val authority: String?) : CopyLocation
    data object Other : CopyLocation
}

internal val CopyLocation.isPhone: Boolean
```
- **추적**: HLD 2.2 "판정 규칙 … 순수 함수로 떼어 JVM에서 시험" / REQ-55-01, SRS 2.1 안 A
- **책임**: 경로 한 곳이 '폰'인지 정한다.
- **입력·전제**: 어느 스레드에서나. 값 객체라 부작용이 없다.
- **출력**(SRS 2.1 안 A 열):

  | 값 | `isPhone` | SRS 2.1 행 |
  |---|---|---|
  | `Linux` | 참 | 로컬 Linux 경로(비루트), 루트로 접근하는 같은 경로 |
  | `Archive(x)` | `x.isPhone` | 압축 파일 안 — 압축 파일이 있는 곳을 따른다(겹친 압축도 재귀) |
  | `Document(isLocal, authority)` | `isLocal && authority != MTP_DOCUMENTS_PROVIDER_AUTHORITY` | 기기 저장소(`com.android.externalstorage.documents`, 버그 리포트 `com.android.shell.documents`) → 폰. MTP → 네트워크. 그 밖의 문서 제공자(클라우드 등, `isLocal == false`) → 네트워크 |
  | `Other` | 거짓 | content URI, SFTP, SMB, FTP(S), WebDAV(S), 앞으로 더해질 제공자 |

- **알고리즘**:
  ```kotlin
  when (this) {
      CopyLocation.Linux -> true
      is CopyLocation.Archive -> archiveFile.isPhone
      is CopyLocation.Document -> isLocal && authority != MTP_DOCUMENTS_PROVIDER_AUTHORITY
      CopyLocation.Other -> false
  }
  ```
  `private const val MTP_DOCUMENTS_PROVIDER_AUTHORITY = "com.android.mtp.documents"`를 이 파일에 둔다(`// @see DocumentResolver.MTP_DOCUMENTS_PROVIDER_AUTHORITY` 주석). `DocumentResolver`의 같은 상수는 `private`이라(`provider/document/resolver/DocumentResolver.kt:44`) 쓸 수 없다 — 열려면 A1이 7개 파일이 된다(4절 F2).
- **기존 코드와의 관계**: HLD 2.2의 규칙 "`DocumentResolver.isLocal`(`DocumentResolver.kt:269-272`)이 참이고 MTP(49)가 아니면 폰"을 그대로 옮긴다. `isLocal`은 `LOCAL_AUTHORITIES`(46-50: 버그 리포트·기기 저장소·MTP)를 보므로, 그 목록이 바뀌면 이 판정도 따라간다. 기존 `filelist/PathExtensions.kt:24-29` `isLocalPath`(SRS 2.1 안 C)는 바꾸지 않는다 — 탐색 화면이 쓰는 다른 판정이다.

### 2.3 `me.zhanghai.android.files.filejob.classifyCopy(source: CopyLocation, targetDirectory: CopyLocation): CopyKind` (A1, 신규, 순수)
- **추적**: HLD 2.2 "(원본, 대상 폴더) 쌍을 폰→폰/네트워크로" / REQ-55-01
- **책임**: 두 곳이 모두 폰이면 `PHONE_TO_PHONE`, 아니면 `NETWORK`.
- **알고리즘**: `if (source.isPhone && targetDirectory.isPhone) CopyKind.PHONE_TO_PHONE else CopyKind.NETWORK`.

### 2.4 `me.zhanghai.android.files.filejob.classifyCopy(source: Path, targetDirectory: Path): CopyKind` (A1, 신규) + `private val Path.copyLocation: CopyLocation`
- **추적**: HLD 2.2 "복사 분류" / REQ-55-01 ("분류가 한 곳(함수 하나)에서만")
- **책임**: 실제 경로를 `CopyLocation`으로 바꿔 2.3에 넘긴다. 복사 작업이 분류를 얻는 **유일한** 입구다.
- **입력·전제**: 파일 작업 스레드. `source`는 복사 시작 항목(압축 풀기면 압축 파일 안의 경로), `targetDirectory`는 대상 폴더(2.6이 넘기는 값). 파일 시스템에 묻지 않는다(경로의 종류와 트리 URI만 본다) — 왕복·예외가 없다.
- **출력**: `CopyKind`.
- **오류**: 없다. `archiveFile`은 `isArchivePath`가 참일 때만 부른다(`provider/archive/PathArchiveExtensions.kt:16-20`의 `ProviderMismatchException`이 나지 않는다).
- **알고리즘**:
  ```kotlin
  internal fun classifyCopy(source: Path, targetDirectory: Path): CopyKind =
      classifyCopy(source.copyLocation, targetDirectory.copyLocation)

  private val Path.copyLocation: CopyLocation
      get() = when {
          isArchivePath -> CopyLocation.Archive(archiveFile.copyLocation)
          isLinuxPath -> CopyLocation.Linux
          isDocumentPath -> {
              this as DocumentResolver.Path
              CopyLocation.Document(DocumentResolver.isLocal(this), treeUri.authority)
          }
          else -> CopyLocation.Other
      }
  ```
  `isArchivePath`를 먼저 본다(압축 파일 안 경로는 Linux 경로가 아니지만 순서를 분명히 한다).
- **기존 코드와의 관계**: `isLinuxPath`(`provider/linux/LinuxPath.kt:185`, 루트 경로도 `LinuxPath`), `isArchivePath`(`provider/archive/ArchivePath.kt:110`), `archiveFile`(`PathArchiveExtensions.kt:16`), `isDocumentPath`(`provider/document/DocumentPath.kt:114`), `DocumentResolver.Path.treeUri`(`DocumentResolver.kt:596-597`), `DocumentResolver.isLocal`(269). `this as DocumentResolver.Path`는 `filelist/PathExtensions.kt:26`과 같은 방식이다.
- **JVM 시험**: 이 함수는 실제 `Path` 구현(Android `Uri`, 압축 파일 시스템)이 필요해 JVM에서 시험하지 않는다. 규칙은 2.2·2.3이 시험되고, 이 함수는 `when` 네 갈래가 2.2 표의 네 값과 맞는지를 코드 검사로 본다(3.3).

### 2.5 `provider/common/CopyOptions.kt` (A1, 수정)
```kotlin
/**
 * Don't give the target any attribute of the source: no permissions when creating it, and no
 * attribute step (times, permissions, owner, xattrs) after writing it. Used by network copies
 * (#55). Takes precedence over [StandardCopyOption.COPY_ATTRIBUTES].
 */
@Parcelize
data object SkipAttributesCopyOption : CopyOption, Parcelable

class CopyOptions(
    val replaceExisting: Boolean,
    val copyAttributes: Boolean,
    val atomicMove: Boolean,
    val noFollowLinks: Boolean,
    val progressIntervalMillis: Long,
    val progressListener: ((Long) -> Unit)?,
    val cleanupFailureListener: (() -> Unit)?,
    val skipAttributes: Boolean = false
)
```
- **추적**: HLD 2.2 "속성 생략 옵션", D2 / REQ-55-03
- **책임**: 속성 생략을 `CopyOption` 배열과 `CopyOptions` 사이에서 잃지 않고 옮긴다.
- **입력·전제**:
  - `skipAttributes`는 **마지막 인자, 기본값 `false`**. 지금 `CopyOptions(...)`를 위치 인자 7개로 만드는 곳(`ForeignCopyMove.kt:155`, `FtpCopyMove.kt:164`, `LinuxCopyMove.kt:293`, `SftpCopyMove.kt:280`, `SmbCopyMove.kt:217`, `WebDavCopyMove.kt:175` — 모두 `move`의 복사 대체)은 고치지 않고 `false`가 된다. 이동은 속성을 맞추는 것이 맞다(SRS Q4).
  - `Parcelable`(D2): 루트 원격 호출은 `ParcelableCopyOptions`(`provider/remote/ParcelableCopyOptions.kt:30-45`)로 옵션을 넘기며 `Parcelable`·`Serializable`이 아니면 `UnsupportedOperationException`이다. 루트 경로는 폰→폰이라 이 옵션이 넘어갈 일은 없지만 넘어가도 깨지지 않게 한다. 저장소에 `@Parcelize data object` 선례가 있다(`provider/webdav/client/Authentication.kt:25-26`).
- **알고리즘**:
  - `toArray()`(21-43): 끝에 `if (skipAttributes) { options += SkipAttributesCopyOption }`.
  - `toCopyOptions()`(46-77): `var skipAttributes = false`; `when`에 `option is SkipAttributesCopyOption -> skipAttributes = true`(`else` 예외 앞). 마지막 생성자 호출에 `skipAttributes`를 넘긴다. `===`가 아니라 `is`로 본다 — 원격 호출에서 다시 만든 인스턴스도 알아보게(parcelize가 `data object`를 같은 인스턴스로 돌려주는지에 기대지 않는다).
- **오류**: 없다(모르는 옵션 예외의 대상에서 빠진다).
- **기존 코드와의 관계**: `ProgressCopyOption`·`CleanupFailureCopyOption`과 같은 자리의 사용자 정의 옵션. import 추가: `android.os.Parcelable`, `kotlinx.parcelize.Parcelize`.

### 2.6 `filejob/FileJobs.kt` (A1, 수정)
- **추적**: HLD 2.2 "옵션 조립", 3.1 1·4단계 / REQ-55-01, 03, SRS D1
- **책임**: 시작 항목마다 한 번 분류하고, 네트워크면 옵션 배열에 `SkipAttributesCopyOption`을 넣는다.

| 함수 (f899359 줄) | 바꾸는 것 |
|---|---|
| `CopyFileJob.run()` (805-826) | `for (source in sources)` 안에서 `target`을 정한 뒤 `val copyKind = classifyCopy(source, targetDirectory)`. `copyRecursively(source, target, isExtract, copyKind, transferInfo, actionAllInfo)`. 대상 폴더는 `getTargetPathForDuplicate`로 원본 옆에 만드는 경우(818-819)에도 `targetDirectory`다(그때 `source.parent == targetDirectory`) |
| `CopyFileJob.copyRecursively(...)` (828-863) | 인자 `copyKind: CopyKind` 추가(`isExtract` 뒤). `preVisitDirectory`(843-845)·`visitFile`(853)의 `copy(...)`에 그대로 넘긴다 — 하위 항목은 시작 항목의 분류를 물려받는다(SRS D1). 순회 자체는 바꾸지 않는다(B2) |
| `copy(...)` (949-959) | 인자 `copyKind: CopyKind` 추가(`isExtract` 뒤). `copyOrMove(source, target, type, true, false, copyKind == CopyKind.NETWORK, transferInfo, actionAllInfo)` |
| `copyOrMove(...)` (1254-) | 인자 `skipAttributes: Boolean` 추가(`copyAttributes` 뒤). 옵션 조립(1353-1366)에서 `if (copyAttributes) { … }` 다음에 `if (skipAttributes) { this += SkipAttributesCopyOption }`. 그 밖(충돌·오류 처리, 다시 시도)은 그대로 — 다시 시도(`REPLACE_EXISTING`)에서도 같은 값으로 다시 조립된다 |
| `copyForMove(...)` (1231-1237), `moveByCopy(...)` (1244-1252) | `copyOrMove(…, copyAttributes = true, skipAttributes = false, …)` — 위치 인자 `false` 하나를 더한다. 동작 변화 없음(SRS Q4) |
| `open(...)` (1582-1610) | `copy(file, targetFile, isExtract, classifyCopy(file, cacheDirectory), transferInfo, actionAllInfo)`(1603). `cacheDirectory`는 Linux 경로라 원본이 폰이면 폰→폰, NAS·클라우드면 네트워크 |
| `save(source, target)` (1793-1806) | `copy(source, target, false, classifyCopy(source, targetParent ?: target), transferInfo, actionAllInfo)`(1799). `targetParent`는 이미 있는 `val targetParent = target.parent`(1796, Java 플랫폼 타입). 저장 대상은 고른 폴더 안의 파일이라 부모가 있지만, 루트 경로처럼 `null`이면 `target` 자신으로 판정한다 — 분류는 경로의 제공자·트리·압축 파일만 보므로(2.4) 부모와 같은 결과이고, `null`로 인한 NPE가 생기지 않는다 |

- **입력·전제**: `copyOrMove`의 `useCopy == false`(이동) 경로에서는 언제나 `skipAttributes == false`다(위 표). 이 가정을 KDoc 한 줄로 적는다(`// Only copies may skip attributes; moves keep them (#55 Q4).`).
- **출력·사후조건**: 네트워크 복사의 모든 `copyTo` 호출(첫 시도·'바꾸기' 다시 시도·이름 바꿔 다시 시도)에 `SkipAttributesCopyOption`이 있다. 폰→폰·이동의 옵션 배열은 지금과 같다(같은 순서, 같은 원소).
- **오류**: 새 예외 없음. 분류는 묻지 않으므로 실패하지 않는다(2.4).
- **기존 코드와의 관계**: import `me.zhanghai.android.files.provider.common.SkipAttributesCopyOption`. `scan`(269-299)·충돌 판정(1377-1386)·`showConflictDialog`는 A에서 바꾸지 않는다.

### 2.7 `ForeignCopyMove.copy(source: Path, target: Path, vararg options: CopyOption)` (A1, 수정)
- **추적**: HLD 2.2 "공급자 간 속성 단계" / REQ-55-03
- **책임**: 속성 생략이면 복사 뒤 `setTimes`를 하지 않는다.
- **알고리즘**: `when` 블록(47-118)이 끝난 바로 다음, 주석 `// We don't take error when copying attribute fatal, …`(119) 앞에:
  ```kotlin
  if (copyOptions.skipAttributes) {
      return
  }
  ```
  그 뒤(120-140, `getFileAttributeView`·`setTimes`)는 그대로. 일반 파일·폴더·링크 세 갈래 모두에 적용된다.
- **만들 때 권한**: `newOutputStream(CREATE_NEW, WRITE)`(57-59)·`createDirectory()`(99)·`createSymbolicLink`(107, 113)는 지금도 속성 인자 없이 만든다 — 바꿀 것 없다.
- **오류**: 없음(속성 단계는 원래도 실패를 기록만 한다).
- **기존 코드와의 관계**: `move`(144-)의 `optionsForCopy`(152-159)는 원래 옵션을 그대로 넘기거나 새 `CopyOptions`(기본 `skipAttributes = false`)를 만든다 — 이동은 이 옵션을 받지 않으므로 어느 쪽이든 지금과 같다.

### 2.8 `SftpCopyMove.copy(source: SftpPath, target: SftpPath, copyOptions: CopyOptions)` (A2, 수정, C2 위)
- **추적**: HLD 2.2 "SFTP 속성 단계" / REQ-55-03, SRS Q5 안 B
- **책임**: 속성 생략이면 새 파일·폴더를 권한 없이 만들고(서버 기본) 복사 뒤 `setstat`을 하지 않는다.
- **앵커와 알고리즘**:
  1. `val sourceModeAttributes = FileAttributes.Builder() … .build()`(90f2c1e 70-76, `when (sourceType)` 바로 위):
     ```kotlin
     val sourceModeAttributes = if (copyOptions.skipAttributes) {
         FileAttributes.EMPTY
     } else {
         FileAttributes.Builder()
             .apply { /* 지금 그대로 */ }
             .build()
     }
     ```
     이 값을 쓰는 곳 — 대상 `openByteChannel(target, targetFlags, sourceModeAttributes)`(114)·`mkdir(target, sourceModeAttributes)`(165) — 은 C2의 `write` 람다 안에 있고 그대로 이 값을 쓴다.
  2. `when` 블록이 끝난 뒤, 주석 `// We don't take error when copying attribute fatal, …`(90f2c1e 199) 앞에 `if (copyOptions.skipAttributes) { return }`. 그 뒤 `setstat` 블록(201-230)은 그대로.
- **바뀌지 않는 것**: `if (!sourceAttributes.has(FileAttributes.Flag.MODE)) throw …`(39-43) — 원본 종류(`sourceAttributes.type`)를 알려면 모드가 필요하다. 원본 열기(`openByteChannel(source, READ, FileAttributes.EMPTY)`, 93-95)는 원래 빈 속성이다. 링크 분기(172-196)는 `symlink`에 속성을 넘기지 않는다.
- **오류**: 없음. 서버가 `SSH_FXP_OPEN`/`MKDIR`의 빈 속성을 받으면 서버 기본 권한으로 만든다(SFTP v3 규약, 서버 동작은 미확인 — M-A4).
- **기존 코드와의 관계**: `FileAttributes.EMPTY`는 sshj 상수로 이미 쓰고 있다(`SftpCopyMove.kt:88`, `sftp/client/Client.kt:40`). `Client.mkdir`·`openByteChannel`(`sftp/client/Client.kt:55-74`)은 받은 속성을 그대로 넘긴다.

### 2.9 `SmbCopyMove.copy(source: SmbPath, target: SmbPath, copyOptions: CopyOptions)` (A2, 수정, C2 위)
- **추적**: HLD 2.2 "SMB 속성 단계" / REQ-55-03
- **알고리즘**: `else { throw AssertionError() }`로 끝나는 갈래 다음, 주석 `// We don't take error when copying attribute fatal, …`(90f2c1e 147) 앞에 `if (copyOptions.skipAttributes) { return }`. 그 뒤 `FileBasicInformation`·`setFileInformation`(151-161)은 그대로.
- **만들 때 속성**: 복사는 `copyAttributes == false`라 `attributesToCopy`가 이미 `FILE_ATTRIBUTE_NORMAL`이다(68-74, `smb/client/Client.kt:366-374`). 바꿀 것 없다. `Client.copyFile`(C2가 고침)은 A2에서 건드리지 않는다.
- **비고**: 서버 쪽 복사(`serverCopy`, 같은 세션)는 데이터만 옮긴다 — 수정 시각은 서버가 쓰기 시각으로 정한다(서버 동작 미확인, M-A3).

### 2.10 `FtpCopyMove.copy(source: FtpPath, target: FtpPath, copyOptions: CopyOptions)` (A2, 수정, C2 위)
- **추적**: HLD 2.2 "FTP 속성 단계" / REQ-55-03
- **알고리즘**: `when` 블록 다음, 주석 `// We don't take error when copying attribute fatal, …`(90f2c1e 128) 앞에 `if (copyOptions.skipAttributes) { return }`. 그 뒤 `setLastModifiedTime`(130-139, `MFMT`)은 그대로.
- **만들 때 속성**: `Client.createDirectory`·`storeFile`은 속성을 받지 않는다. 바꿀 것 없다.

### 2.11 `WebDavCopyMove.copy(source: WebDavPath, target: WebDavPath, copyOptions: CopyOptions)` (A2, 수정, C2 위)
- **추적**: HLD 2.2 "WebDAV 속성 단계" / REQ-55-03
- **알고리즘**: `when` 블록 다음, 주석 `// We don't take error when copying attribute fatal, …`(90f2c1e 139) 앞에 `if (copyOptions.skipAttributes) { return }`. 그 뒤 `setLastModifiedTime`(141-150, `PROPPATCH getlastmodified`)은 그대로.
- **만들 때 속성**: `MKCOL`·`PUT`은 속성을 보내지 않는다(`webdav/client/Client.kt`에 수정 시각 헤더 없음 — `grep -i mtime` 결과 없음). 바꿀 것 없다.

## 3. 테스트 설계

### 3.1 분류·옵션 — `filejob/CopyClassificationTest` (A1, JVM, 신규)
새 함수·새 옵션이라 수정 전 코드에서는 컴파일되지 않는다. '수정 전 실패' 확인은 3.2가 맡는다(LLD-C 3.1과 같은 처리).

시험용 값(SRS 2.1 안 A 열의 행):

| 이름 | 값 | 폰? |
|---|---|---|
| `linux` | `Linux` | 예 |
| `deviceStorage` | `Document(true, "com.android.externalstorage.documents")` | 예 |
| `bugReport` | `Document(true, "com.android.shell.documents")` | 예 |
| `mtp` | `Document(true, "com.android.mtp.documents")` | 아니오 |
| `cloud` | `Document(false, "com.google.android.apps.docs.storage")` | 아니오 |
| `nullAuthority` | `Document(false, null)` | 아니오 |
| `archiveOnPhone` | `Archive(Linux)` | 예 |
| `archiveOnDeviceStorage` | `Archive(deviceStorage)` | 예 |
| `archiveInArchiveOnPhone` | `Archive(Archive(Linux))` | 예 |
| `archiveOnNas` | `Archive(Other)` | 아니오 |
| `archiveOnMtp` | `Archive(mtp)` | 아니오 |
| `other` | `Other`(content·SFTP·SMB·FTP·WebDAV) | 아니오 |

| 테스트 | 대상 | 경우 | 기대 결과 |
|---|---|---|---|
| U-A01 `isPhone_optionATable` | 2.2 | 위 12개 값 각각 | `isPhone`이 표의 '폰?' 열과 같다 |
| U-A02 `classify_allPairs` | 2.3 | 12 × 12 쌍 전부(원본 × 대상 폴더) | 둘 다 '폰?'이 예일 때만 `PHONE_TO_PHONE`, 나머지는 `NETWORK`(실패 메시지에 쌍을 적는다) |
| U-A03 `classify_directionDoesNotMatter` | 2.3 | `(linux, other)`, `(other, linux)`, `(linux, cloud)`, `(cloud, linux)` | 모두 `NETWORK` (SRS 1.1 "방향 무관") |
| U-A04 `skipAttributes_roundTrip` | 2.5 | `CopyOptions(false, false, false, true, 0, null, null, skipAttributes = true).toArray()` | 배열에 `SkipAttributesCopyOption`이 있고, 다시 `toCopyOptions()`하면 `skipAttributes == true`, `noFollowLinks == true`, 나머지 거짓 |
| U-A05 `skipAttributes_parsedWithStandardOptions` | 2.5 | `arrayOf(LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING, SkipAttributesCopyOption).toCopyOptions()` | 예외 없음, `skipAttributes`·`replaceExisting`·`noFollowLinks` 참 |
| U-A06 `skipAttributes_defaultFalse` | 2.5 | `arrayOf(LinkOption.NOFOLLOW_LINKS).toCopyOptions()`, 7인자 생성자 `CopyOptions(…).toArray()` | `skipAttributes == false`, 배열에 `SkipAttributesCopyOption` 없음 (이동의 `CopyOptions(...)` 재조립이 지금과 같음) |

`Parcel` 왕복은 JVM에서 할 수 없다(`android.os.Parcel` 스텁). 루트 경로는 폰→폰이라 실제로 넘어가지 않는다 — 코드 검사로 `@Parcelize`가 붙었는지만 본다.

### 3.2 공급자 간 회귀 — `ForeignCopyMoveCleanupTest` 확장 (A1, JVM)
fake 확장: `FakeProvider`에 `val setTimesPaths = mutableListOf<Path>()`를 더하고, `getFileAttributeView`(298-303)가 지금의 `FakeAttributeView` 객체 대신 `setTimes`에서 `setTimesPaths += path`를 하는 뷰를 돌려준다(그 밖은 지금 `FakeAttributeView`와 같다). `events`에는 넣지 않는다 — U-C13·U-C14가 `events` 전체를 비교하기 때문이다. 기존 U-M01–06, U-C10–14는 그대로 통과해야 한다.

| 테스트 | 경우 | 기대 결과 | 수정 전 코드(2.7 전, 2.5 뒤)에서 |
|---|---|---|---|
| U-A07 `copy_skipAttributes_file_noSetTimes` | 일반 파일, 옵션 `SkipAttributesCopyOption` | 복사 성공(출력 스트림 닫힘), `setTimesPaths` 비어 있음 | **실패**(`setTimesPaths == [target]`) |
| U-A08 `copy_skipAttributes_directory_noSetTimes` | `sourceIsDirectory`, 옵션 `SkipAttributesCopyOption` | `events == [mkdir:/target/file]`, `setTimesPaths` 비어 있음 | **실패** |
| U-A09 `copy_default_setsTimes` | 일반 파일, 옵션 없음(폰→폰과 같은 경로) | `setTimesPaths == [target]` | 통과(회귀 방지, REQ-55-02 속성 유지) |

구현 순서: (1) 2.5(옵션) → (2) 3.1·3.2 테스트 → `ForeignCopyMoveCleanupTest`를 돌려 **U-A07·U-A08 실패를 확인**(2.5가 옵션을 알아보므로 모르는 옵션 예외가 아니라 `setTimes` 호출로 실패한다) → (3) 2.7 → 통과 확인. 두 출력의 끝부분을 PR 코멘트에 붙인다(PROCESS_ROLES 11-1 5번).

### 3.3 실행·코드 검사
- JVM: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "me.zhanghai.android.files.filejob.CopyClassificationTest" --tests "me.zhanghai.android.files.provider.common.*" --console=plain` 후 전체 `testDebugUnitTest`, 그리고 `assembleDebug`.
- 코드 검사(검수자):
  - A1: `classifyCopy(Path, Path)`가 `FileJobs.kt`에서 분류를 얻는 유일한 호출이고(`CopyFileJob.run`, `open`, `save` 세 곳), `copyRecursively` 아래로는 인자로만 내려간다(REQ-55-01 "한 곳"). `copyForMove`·`moveByCopy`는 `skipAttributes = false`. `Path.copyLocation`의 네 갈래가 2.2 표와 같다. 폰→폰 옵션 배열이 지금과 같다.
  - A2: 네 백엔드의 `copy`에서 속성 단계 호출(`setstat`·`setFileInformation`·`setLastModifiedTime`)이 `skipAttributes` 검사 뒤에만 있고, SFTP의 대상 `openByteChannel`·`mkdir`가 `skipAttributes`면 `FileAttributes.EMPTY`를 받는다(SRS REQ-55-03 코드 검사 항목). `move` 경로와 C2가 고친 '바꾸기' 순서는 바뀌지 않았다.

### 3.4 수동 시험 (에뮬레이터·서버)
| 시험 | 조각 | 환경 | 절차 | 기대 |
|---|---|---|---|---|
| M-A1 폰→폰 회귀 | A1 | 에뮬레이터 | `adb shell 'mkdir -p /sdcard/Download/a && echo x > /sdcard/Download/a/f && touch -d "2020-01-01 00:00:00" /sdcard/Download/a/f'` → 앱에서 `f`를 `/sdcard/Documents`로 복사 → `adb shell stat -c %Y /sdcard/Download/a/f /sdcard/Documents/f` | 두 값이 같다(REQ-55-02 속성 유지) |
| M-A2 폰→폰 공급자 간 | A1 | 에뮬레이터 | M-A1의 `f`를 SAF로 연 기기 저장소 폴더(`com.android.externalstorage.documents`, 예: `Documents` 트리)로 복사 → 앱의 속성 화면 또는 `stat` | 수정 시각이 원본과 같다(분류가 기기 저장소를 폰으로 본다). SAF 트리를 열 수 없으면 "미확인" |
| M-A3 폰↔NAS 시각 | A1·A2 | SFTP·SMB·FTP·WebDAV 서버 | 폰의 오래된 파일(M-A1의 `f`)과 폴더를 NAS로, NAS의 오래된 파일을 폰으로, NAS 안 다른 폴더로(같은 서버) 복사 | 모든 복사본의 수정 시각 = 복사한 시각. A1만 머지된 상태에서는 폰↔NAS만 맞고 NAS 안 복사는 아직 원본 시각(HLD 2.2 나눔) |
| M-A4 SFTP 권한 | A2 | SFTP 서버 | 원본 `chmod 755` 실행 파일·`chmod 700` 폴더를 같은 서버의 다른 폴더로 복사 → 서버에서 `ls -l` | 서버 기본 권한(umask 적용, 예: `644`·`755`). 원본 권한이 아니다(Q5 안 B) |
| M-A5 폰↔클라우드 SAF | A1 | 에뮬레이터 + 클라우드 SAF 앱 | 오래된 파일을 클라우드 SAF 폴더로 복사 | 복사본 수정 시각 = 복사한 시각. 제공자가 없으면 "미확인" |
| M-A6 압축 풀기 | A1 | 에뮬레이터 | 폰의 zip(오래된 항목 포함)을 내부 저장소로 풀기 | 풀린 항목 수정 시각 = zip 안의 시각(폰→폰, 지금과 같음) |

서버가 없으면 M-A3·M-A4는 PR에 "미확인"으로 적는다(SRS 6절). 오너 기기에는 설치하지 않는다(사전 승인 필요).

## 4. 설계 피드백
- **F1 (PM 판단, 범위 알림)**: A1의 파일 수가 6개다(1절). 운영 코드 4개는 HLD 2.2와 같고 늘어난 것은 `ForeignCopyMoveCleanupTest.kt` 하나다(LLD-C F1과 같은 이유). 5개를 지키려면 U-A07·08(수정 전 실패를 보이는 유일한 테스트)과 회귀 테스트 U-A09를 빼야 한다. **PM 판단 요청.** HLD 수정은 요청하지 않는다(설계 동작 변화 없음).
- **F2 (LLD 결정, 기록)**: MTP 판정은 `CopyClassification.kt`의 사설 상수 `"com.android.mtp.documents"`로 한다(2.2). `DocumentResolver.MTP_DOCUMENTS_PROVIDER_AUTHORITY`(44)는 `private`이고, 열면 A1이 7개 파일이 된다. 값은 Android 시스템 상수라 바뀔 일이 거의 없고, 이 파일의 KDoc에 출처를 적는다.
- **F3 (LLD 결정, 기록)**: A2는 JVM 테스트를 두지 않는다. SFTP 생성 속성 식만 `internal` 함수로 떼어 시험하는 안을 검토했으나, 그 식은 세 줄 `if`이고 실제 동작(서버에 보내는 `OPEN`·`MKDIR`·`SETSTAT`)은 `object Client`의 실제 세션이 필요해 fake로 바꿀 수 없다 — 파일 하나를 늘려 얻는 것이 적다. 옵션 해석은 A1(U-A04–06)이, 결선은 코드 검사(3.3)가 맡는다.
- **F4 (LLD 결정, 기록)**: `copyOrMove`에는 분류(`CopyKind`) 대신 효과(`skipAttributes: Boolean`)를 넘긴다(2.6). `copyOrMove`는 이동도 쓰므로 분류 값이 없는 호출자(이동)가 있다. 분류는 `copy`에서 효과로 바뀐다. 조각 B2가 링크 따라가기에 같은 분류가 필요하면 그때 `copy`에서 인자를 하나 더 만든다(B2 LLD).
- **F5 (경미, 설계팀 참고)**: HLD 2.2 "옵션 조립" 행의 `FileJobs.kt:1794-1799`(`save`)·`1583-1603`(`open`)는 f899359 기준 `save` 1793-1806, `open` 1582-1610이다(함수 시작 줄 차이). 내용은 같다. 다음 HLD 갱신 때 고치면 된다.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1 | 최초 작성(조각 A1·A2) | PM LLD 요청(조각 A만, A2는 C2 위) | PM |
| 2026-11-19 | v1.1 | F1 문구의 수정 전 실패 테스트를 U-A07·08로 정정(m1), 2.6 `save`의 `target.parent`가 `null`일 때 `target`으로 판정(m2) | LLD 검수 1차 조건부 통과 m1·m2 [기계] (PR #72 코멘트) | reviewer |
| 2026-11-19 | v1.2 | A2(2.8–2.11) 줄 번호를 C2 머지 뒤 develop 90f2c1e 기준으로, 기준 코드 행·C2 관계 문단 갱신(동작 변화 없음) | C2 머지(PR #71) | PM |
