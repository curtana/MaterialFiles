# LLD-D — 복사·이동 확인 창 (이슈 #8)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 |
| 근거 | SRS v4.7, HLD v4.7 (`docs/design/2-dual-pane/`), LLD-A v1.4(구조·확장 지점 4장) |
| 기준 코드 | `origin/develop` a76055e (#5 화면/창 분리 머지 뒤). HLD의 `FileListFragment.kt` 줄 번호는 #5 이전 기준이라 이 문서는 a76055e에서 다시 찾은 줄을 쓴다(6장 F2). |
| 이슈 | #8 (하위 이슈 D, 상위 #2, 선행 #5) |
| HLD 구성요소 | K6(전부), K9(확인 창 문자열), K5(붙여넣기 경로만) |
| 추적 REQ | REQ-2-13(붙여넣기 쪽 몫), 14, 15, 16, 21. REQ-2-12는 진입 API만(0.1) |

## 0. 범위와 전제

### 0.1 이 단계가 하는 것 / 하지 않는 것
- **한다**: 새 확인 창 `ConfirmCopyMoveDialogFragment`(K6)와 그 판단 로직(순수 함수), 확인 창 문자열(K9의 확인 창 몫). 기존 붙여넣기(`FileListFragment.pasteFiles`, a76055e `:1104-1116`)가 작업을 바로 시작하지 않고 확인 창을 거치게 한다. "실행"에서 작업을 시작하는 리스너는 붙여넣기·반대쪽 명령 두 출처를 모두 처리한다(HLD K6 책임).
- **#9(E)를 위해 준비만 한다**: 진입 API `ConfirmCopyMoveDialogFragment.show(copy, files, targetDirectory, Origin.OTHER_PANE, fragment)`와 리스너의 `OTHER_PANE` 갈래(선택 해제). #9는 선택 메뉴 항목 두 개(`res/menu/file_list_select.xml`)와 그 클릭에서 이 API를 부르는 것만 더한다. 반대쪽 경로(`DualPaneViewModel.panePaths`)는 #6 몫이다.
- **하지 않는다**: 반대쪽 복사·이동 메뉴·읽기 전용 비활성 판단(#9), 창 2·활성 창(#6), 창 표시 줄(#7). `filejob/`·`provider/`는 바꾸지 않는다(SRS C-1) — `CopyMoveType`(`filejob/FileJobs.kt:575-579`)과 `FileJobService.copy/move`(`filejob/FileJobService.kt:126-140`)를 그대로 쓴다.

### 0.2 추적 — 이 단계의 몫
| REQ | 이 단계의 몫 | 확인 |
|---|---|---|
| REQ-2-13 | 공유 복사 목록에서의 붙여넣기가 확인 창을 거친다. 공유 자체는 이미 된다(HLD K8, 변경 없음). | T-02, T-20 |
| REQ-2-14 | 모든 붙여넣기(복사·이동·압축 풀기)에서 확인 창. 확인 창이 떠 있는 동안 작업이 시작되지 않는다(작업 시작은 "실행" 콜백 안에만 있다). 반대쪽 명령은 #9가 같은 API로 연다. | T-02~T-05, T-17 |
| REQ-2-15 | 종류·항목 수(1개면 이름)·원래 위치 → 대상, 버튼 취소/실행, 취소·뒤로·바깥·Esc는 아무것도 바꾸지 않음, 회전 유지, 전체 폭 아님, 버튼 48dp. | T-02, T-03, T-06, T-07, T-16 |
| REQ-2-16 | 확인 창은 충돌을 미리 검사하지 않는다. "실행" 뒤 작업 스레드에서 기존 충돌 창(`FileJobs.kt:549-573`, 호출 `:1328`)이 뜬다. | T-10 |
| REQ-2-21 | 같은 폴더 안내(전부·일부·없음), 실행은 막지 않음. 판정 값과 작업에 넘기는 값이 같다(2.1). | T-08, T-09, T-11, T-12 |

### 0.3 사용자에게 보이는 변화
- 붙여넣기(막대의 "붙여넣기"·"여기에 압축 풀기", Ctrl+V)를 누르면 바로 시작하지 않고 확인 창이 뜬다(요구사항 그대로).
- 대상 경로에 `.` 조각이 있을 때(경로 직접 입력으로만 생김, `PathDialogFragment.kt:36-54`) 같은 폴더 복사가 지금은 같은 파일 판정으로 아무것도 하지 않고 끝나지만(`provider/linux/LinuxCopyMove.kt:46-49`), 이 단계 뒤에는 같은 폴더 안내가 뜨고 사본이 생긴다(SRS A7대로 — HLD 3.6-1이 의도한 변화). `..` 조각이 있는 경로는 지금과 같다(6장 F1).
- 그 밖의 동작(작업 내용, 충돌 창, 알림, 복사 목록 비우기)은 같다.

## 1. 변경 파일

| # | 파일 | 신규/수정 | 요지 | 구성요소 |
|---|---|---|---|---|
| 1 | `app/src/main/java/me/zhanghai/android/files/filelist/ConfirmCopyMoveDialogFragment.kt` | 신규 | 확인 창(인자·내용·버튼·리스너). | K6 |
| 2 | `app/src/main/java/me/zhanghai/android/files/filelist/CopyMoveConfirmation.kt` | 신규 | 입출력 없는 순수 로직: 작업 종류, 작업용 경로 정규화, 같은 폴더 개수, 원래 위치 요약, 위치 표시 문자열. | K6 |
| 3 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt` | 수정 | `pasteFiles` → `confirmPasteFiles`(확인 창 열기), 리스너 `copyMoveFiles` 구현, 인터페이스 선언 추가. | K5 |
| 4 | `app/src/main/res/values/strings.xml` | 수정 | 확인 창 문자열(2.4). | K9 |
| 5 | `app/src/main/res/values-ko/strings.xml` | 수정 | 같은 문자열의 한국어(SRS C-4). | K9 |

- 5개(신규 2, 수정 3). `provider/`·`filejob/`·JNI·`app/build.gradle`·레이아웃은 건드리지 않는다. 새 권한·설정 키·런타임 의존성 없음.
- 단위 테스트(3.2)는 테스트 의존성 승인이 있을 때만 더한다(SRS C-3, 6장 F3). 승인되면 `app/build.gradle`(`testImplementation`)과 `app/src/test/.../CopyMoveConfirmationTest.kt`가 늘어 7개가 된다 — 그때는 구현 착수 전에 범위를 알린다(PROCESS_ROLES 11-1).
- 바꾸지 않지만 관계있는 것: `ConfirmDeleteFilesDialogFragment.kt`(같은 모양의 본보기, `:21-65`), `util/DialogFragmentExtensions.kt:11-13`(`show(fragment)` = 자식 FragmentManager), `FileListViewModel.kt:195-220`(`pasteState`·`clearPasteState`), `:154-174`(`selectFiles`), `PasteState.kt`.

## 2. 함수 명세

표기: "기존 `:N`"은 `FileListFragment.kt`의 a76055e 줄. 다른 파일은 파일 이름을 붙인다.

### 2.1 `CopyMoveConfirmation.kt` (신규, 순수 로직)

모든 함수는 입출력이 없고 메인 스레드에서 부른다(HLD 3.6-1 "계산 시점", `Stability_ANR`). Android 프레임워크 클래스에 의존하지 않는다(단위 테스트 대상, 3.2).

#### `me.zhanghai.android.files.filelist.getCopyMoveType(copy: Boolean, files: Collection<FileItem>): CopyMoveType`
- **추적**: HLD K6, 3.6 "종류 결정" / REQ-2-15, SRS A4
- **책임**: 확인 창에 보일 작업 종류를 정한다.
- **입력·전제**: `copy`는 `PasteState.copy`(붙여넣기) 또는 명령 종류(반대쪽, #9). `files`는 비어 있지 않다(호출자 보장, 비면 아래 규칙상 COPY/EXTRACT 중 `all`이 참이라 EXTRACT가 되므로 호출자가 막는다 — 2.3 `confirmPasteFiles` 1).
- **출력**: `!copy` → `MOVE`; `copy && files.all { it.path.isArchivePath }` → `EXTRACT`; 그 밖 → `COPY`.
- **오류**: 없음.
- **기존 코드와의 관계**: 붙여넣기 막대 제목의 판단(기존 `:1019-1030`)과 `CopyFileJob`의 `isExtract`(`FileJobs.kt:739`)와 같은 조건. 이동은 막대와 같이 압축 여부를 보지 않는다(압축 내부는 읽기 전용이라 잘라내기가 숨겨짐, 기존 `:811-812`).

#### `Path.toCopyMoveJobPath(): Path` (internal 확장 함수)
- **추적**: HLD 3.6-1 "판정과 실행에 같은 값", D11, R9 / REQ-2-21
- **책임**: 같은 폴더 판정과 작업 호출에 **함께** 쓸 경로 값을 만든다.
- **입력·전제**: 파일 목록이 만든 경로(목록 항목·현재 폴더). 실제로는 모두 `ByteStringListPath` 하위 클래스다(2.1 끝 "provider별 확인").
- **출력**: 경로에 이름 조각 `..`가 하나라도 있으면 **그대로**(`this`), 없으면 `normalize()`.
- **오류**: 없음(아래 확인 범위 안에서 `normalize()`는 던지지 않는다).
- **알고리즘**: `if ((0 until nameCount).any { getName(it).toString() == ".." }) this else normalize()`.
- **왜 `..`는 정규화하지 않는가**(HLD R9-3 → 6장 F1, PM 결정 필요): `normalize()`는 문자열 처리라 `심볼릭링크/..`를 실제 위치와 다른 경로로 바꾼다. Android에서 `/sdcard`는 심볼릭 링크(`/storage/self/primary`)라, 경로 입력으로 `/sdcard/..`를 열면 목록은 실제 부모(`/storage/self`)의 내용인데 `normalize()`는 `/`를 돌려준다 — 작업에 넘기면 엉뚱한 폴더가 대상·원본이 된다. `.`만 지우는 것은 심볼릭 링크와 무관하게 같은 폴더를 가리키므로 안전하다. `..`가 있는 경로를 그대로 넘기면 작업 결과는 지금과 같고, 판정도 같은 값으로 하므로 안내와 결과는 여전히 일치한다.
- **기존 코드와의 관계**: 새 함수. `makePathListForJob`(기존 `:1118-1119`, 삭제 `:928`·압축 `:950`도 씀)은 바꾸지 않는다(HLD 3.6-1).

#### `class CopyMoveConfirmation` + `fun createCopyMoveConfirmation(files: FileItemSet, targetDirectory: Path): CopyMoveConfirmation`
- **추적**: HLD K6, 3.6-1 / REQ-2-15, 21, SRS A3
- **책임**: 확인 창 내용과 "실행" 값을 한 번에 계산한다.
- **입력·전제**: `files`는 비어 있지 않은, 보일 때 굳힌 원본 집합(2.2 `show`가 복사본을 만든다). 순서는 `FileItemSet`의 삽입 순서(`util/MapSet.kt:38` `LinkedMapSet`). `targetDirectory`는 대상 폴더(붙여넣기는 그 창의 현재 경로).
- **출력**(불변 값 객체, 필드):
  - `sources: List<Path>` — `files.map { it.path.toCopyMoveJobPath() }.sortedBy { it.toUri() }`. 정렬은 기존 `makePathListForJob`(`:1118-1119`)과 같다(작업 순서 유지).
  - `targetDirectory: Path` — `targetDirectory.toCopyMoveJobPath()`.
  - `itemCount: Int` — `files.size`.
  - `singleItemName: String?` — `itemCount == 1`이면 `files.single().name`(`FileItemExtensions.kt:33-34`; 압축 파일 풀기용 더미 루트는 압축 파일 이름 — `PathExtensions.kt:17-18`), 아니면 `null`.
  - `sameDirectoryCount: Int` — `sources` 중 `source.parent != null && source.parent == this.targetDirectory`인 개수. **작업에 넘길 값 그대로** 비교한다: `CopyFileJob`의 사본 이름 조건 `source.parent == targetDirectory`(`FileJobs.kt:750`)와 같은 식이다. `Path.equals`는 같은 클래스·같은 파일 시스템일 때만 참이다(`provider/common/ByteStringListPath.kt:248-260`) — provider가 다르면 같은 폴더가 아니다.
  - `firstLocation: Path?` — 삽입 순서 첫 항목의 위치(아래 `locationOf`).
  - `otherLocationCount: Int` — 서로 다른 위치 수 − 1(위치 `null`은 세지 않음). 같은지 판단은 `Path.equals`.
  - 계산 속성 `sameDirectoryNotice: SameDirectoryNotice` — `sameDirectoryCount == 0` → `NONE`, `== itemCount` → `ALL`, 그 사이 → `SOME`(HLD 3.6-1 문구 결정).
- **`locationOf(path: Path): Path?`** (private): `val job = path.toCopyMoveJobPath()`; `job.parent ?: if (job.isArchivePath) job.archiveFile.parent else null`. 압축 파일을 통째로 푸는 경우(`extractFiles` 기존 `:932-935`의 `createDummyArchiveRoot`, 압축 루트는 부모가 없음) 원래 위치를 압축 파일이 있는 폴더로 보인다. **표시용일 뿐이며 같은 폴더 판정에는 쓰지 않는다**(판정은 위 `sameDirectoryCount` — 압축 루트는 부모가 없어 같은 폴더가 아니고, 작업도 사본 이름이 아닌 새 폴더로 푼다: `FileJobs.kt:750-753`, HLD 3.6-1 "압축 풀기").
- **오류**: 없음.
- **기존 코드와의 관계**: 새 함수. HLD 3.6-1의 "항목별 판정 `s.parent.normalize() == target.normalize()`"를 `toCopyMoveJobPath()` 값으로 바꾼 것이다(`..` 없는 경로에서는 `s.normalize().parent == s.parent.normalize()` — 목록 항목 이름은 `.`·`..`가 아니므로 같은 값).

#### `enum class SameDirectoryNotice { NONE, ALL, SOME }`
- **추적**: HLD 3.6-1 문구 결정 / REQ-2-21.

#### `Path.toLocationDisplayString(): String` (internal 확장 함수)
- **추적**: HLD K6 / REQ-2-15
- **책임**: 원래 위치·대상을 사람이 읽을 문자열로.
- **알고리즘**: `if (isArchivePath) archiveFile.toUserFriendlyString() + (if (nameCount == 0) "" else toString()) else toUserFriendlyString()`. 예: 로컬 `/storage/emulated/0/Download`, 압축 내부 `/storage/emulated/0/a.zip/dir`, 원격은 기존처럼 URI.
- **기존 코드와의 관계**: `toUserFriendlyString()`(`PathExtensions.kt:20`, 경로 이동 대화상자 `NavigateToPathDialogFragment.kt:30`도 씀)을 재사용. 압축 경로의 `toUri()`(`provider/archive/ArchivePath.kt:48-51`)는 읽기 어려워 압축 파일 경로 + 내부 경로로 보인다. `ArchivePath.toString()`은 내부 절대 경로(`ByteStringListPath.kt:246`).

#### provider별 `normalize()` 확인 (HLD R9(2) 닫기)
- 파일 목록 경로는 모두 `ByteStringListPath` 하위 클래스다: `LinuxPath`, `ArchivePath`, `DocumentPath`, `FtpPath`, `SftpPath`, `SmbPath`, `WebDavPath`(`grep -rln "ByteStringListPath<" provider/`). 이들은 `normalize()`를 재정의하지 않고 기반 구현(`provider/common/ByteStringListPath.kt:122-144`: 조각 목록 처리 + `createPath(isAbsolute, segments)`)을 쓴다. 각 `createPath(absolute, segments)`는 생성자 호출뿐이다(`linux/LinuxPath.kt:51-52`, `archive/ArchivePath.kt:45-46`, `document/DocumentPath.kt:49-50`, `ftp/FtpPath.kt:52-53`, `sftp/SftpPath.kt:50-51`, `smb/SmbPath.kt:49-50`, `webdav/WebDavPath.kt:51-52`) — **입출력 없음**, 파일 시스템 객체를 그대로 넘기므로 `equals`도 유지된다.
- `ContentPath.normalize()`는 `this`(`content/ContentPath.kt:96`)이고 부모가 없다(`:94`) — 목록 항목이 되지 않지만 와도 안전하다. `ByteStringPath.normalize()`는 던지지만(`common/ByteStringPath.kt:73-75`) 심볼릭 링크 대상 표현에만 쓰여 목록 경로가 되지 않는다(`ArchiveFileSystemProvider.kt:172`, `FtpFileSystemProvider.kt:329`, `PathExtensions.kt:81`).
- 압축 풀기용 더미 루트(`createArchiveRootPath()`, `PathArchiveExtensions.kt:27-28`)는 조각이 없어 `normalize()` 결과가 같은 값이다 — `createDummyArchiveRoot`(`FileItemExtensions.kt:102-106`) 경로를 바꾸지 않는다.

### 2.2 `ConfirmCopyMoveDialogFragment` (신규, K6)

#### 클래스·인자
```kotlin
class ConfirmCopyMoveDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private val listener: Listener get() = requireParentFragment() as Listener
    ...
    @Parcelize
    class Args(
        val type: CopyMoveType,
        val files: FileItemSet,
        val targetDirectory: @WriteWith<ParcelableParceler> Path,
        val origin: Origin
    ) : ParcelableArgs

    enum class Origin { PASTE, OTHER_PANE }

    interface Listener {
        fun copyMoveFiles(
            type: CopyMoveType, sources: List<Path>, targetDirectory: Path,
            files: FileItemSet, origin: Origin
        )
    }
}
```
- **추적**: HLD K6, D7 / REQ-2-14, 15
- **설명**: `ConfirmDeleteFilesDialogFragment`(`:21-65`)와 같은 모양(`ParcelableArgs` + 부모 프래그먼트 `Listener`). `Path` 필드는 `NavigateToPathDialogFragment.kt:43`과 같은 `@WriteWith<ParcelableParceler>`. `CopyMoveType`·`Origin`은 enum이라 `@Parcelize`가 이름으로 쓴다. 인자가 Parcelable이라 회전·재생성·프로세스 종료 뒤 복원에서도 같은 내용(HLD 3.1 "확인 창 내용").
- `Origin`은 HLD K6의 "출처(붙여넣기 / 반대쪽 명령)". `OTHER_PANE`은 #9가 쓴다.

#### `ConfirmCopyMoveDialogFragment.Companion.show(copy: Boolean, files: FileItemSet, targetDirectory: Path, origin: Origin, fragment: Fragment): Unit`
- **추적**: HLD K6, 3.6 / REQ-2-14, 15. **#9가 쓰는 진입 API**.
- **책임**: 보일 때 원본·대상을 굳혀 확인 창을 연다.
- **입력·전제**: 메인 스레드. `fragment`는 `Listener`를 구현한다(창 `FileListFragment`). `files`는 비어 있지 않다. `files`는 살아 있는 집합이어도 된다(복사 목록 `PasteState.files`, 선택 `viewModel.selectedFiles` — 둘 다 나중에 그 자리에서 바뀌는 가변 집합: `FileListViewModel.kt:197-220`, `:154-174`).
- **출력·사후조건**: `ConfirmCopyMoveDialogFragment().putArgs(Args(getCopyMoveType(copy, frozen), frozen, targetDirectory, origin)).show(fragment)` — `frozen = FileItemSet().apply { addAll(files) }`(복사본). 대화상자는 `fragment`의 자식 FragmentManager에 붙는다(`util/DialogFragmentExtensions.kt:11-13`). 이 함수는 어떤 상태도 바꾸지 않는다(복사 목록·선택·작업 없음 — REQ-2-14).
- **왜 복사본인가**: `putArgs`는 `Bundle.putParcelable`로 객체 참조를 담고(`util/ParcelableArgs.kt:18-21`) 상태 저장 때까지 직렬화하지 않는다. 살아 있는 집합을 그대로 넣으면 "실행" 뒤 `clearPasteState()`(`FileListViewModel.kt:213-220`의 `files.clear()`)나 선택 해제가 인자 자체를 비우고, 확인 창이 떠 있는 동안 목록이 바뀌면 보인 것과 다른 항목으로 실행된다(HLD 3.6 "보일 때 인자로 굳힌다").
- **오류**: 없음. `fragment`가 `Listener`가 아니면 "실행" 때 `ClassCastException`(구조 위반, 기존 대화상자들과 같음).
- **기존 코드와의 관계**: `ConfirmDeleteFilesDialogFragment.show`(`:55-57`) 모양. 종류 계산을 여기서 하므로 호출자는 `copy` 불리언만 안다.

#### `ConfirmCopyMoveDialogFragment.onCreateDialog(savedInstanceState: Bundle?): Dialog`
- **추적**: HLD K6, 3.9, R11 / REQ-2-15, 21, SRS 3.3·3.5
- **책임**: 인자에서 내용을 계산해 대화상자를 만든다(회전 복원 때도 같은 계산).
- **입력·전제**: 메인 스레드. 인자 있음.
- **출력·사후조건**: `MaterialAlertDialogBuilder(requireContext(), theme)`로 만든 대화상자. 상태 변경 없음.
- **알고리즘**:
  1. `val confirmation = createCopyMoveConfirmation(args.files, args.targetDirectory)`.
  2. 제목: `getString(args.type.getResourceId(R.string.file_list_confirm_copy_move_copy_title, R.string.file_list_confirm_copy_move_extract_title, R.string.file_list_confirm_copy_move_move_title))`(`FileJobs.kt:581-590`의 `getResourceId` 재사용).
  3. `val bidi = BidiFormatter.getInstance()`(`androidx.core.text.BidiFormatter`, core-ktx 1.16.0 `app/build.gradle:134`) — 끼워 넣는 이름·경로마다 `bidi.unicodeWrap(text)`(`RTL-3`, SRS 3.5).
  4. 항목 줄: `singleItemName != null` → `getString(R.string.file_list_confirm_copy_move_item_name_format, bidi.unicodeWrap(name))`, 아니면 `getQuantityString(R.plurals.file_list_confirm_copy_move_item_count_format, itemCount, itemCount)`(`util/FragmentExtensions.kt:84-` 확장, 기존 사용 `ConfirmDeleteFilesDialogFragment.kt:45`).
  5. 위치 문자열: `val from = firstLocation?.let { bidi.unicodeWrap(it.toLocationDisplayString()) }`. `otherLocationCount > 0`이면 `from = getQuantityString(R.plurals.file_list_confirm_copy_move_locations_format, otherLocationCount, from, otherLocationCount)`(SRS A3 "외 N곳"). `val to = bidi.unicodeWrap(confirmation.targetDirectory.toLocationDisplayString())`. 경로 줄 = `from != null` → `getString(R.string.file_list_confirm_copy_move_from_to_format, from, to)`, 아니면 `getString(R.string.file_list_confirm_copy_move_to_format, to)`(위치를 모르는 경우 — 실제 목록 항목에서는 생기지 않지만 크래시 대신 대상만 보인다).
  6. 안내: `when (sameDirectoryNotice)` `NONE` → 없음, `ALL` → `getString(R.string.file_list_confirm_copy_move_same_directory_all)`, `SOME` → `getQuantityString(R.plurals.file_list_confirm_copy_move_same_directory_some_format, sameDirectoryCount, sameDirectoryCount)`.
  7. 메시지 = 항목 줄 + `"\n"` + 경로 줄 + (안내가 있으면 `"\n\n"` + 안내). `setTitle(제목)`, `setMessage(메시지)`.
  8. `setPositiveButton(R.string.file_list_confirm_copy_move_positive) { _, _ -> listener.copyMoveFiles(args.type, confirmation.sources, confirmation.targetDirectory, args.files, args.origin) }` — 작업용 값은 1에서 판정에 쓴 **같은 객체**.
  9. `setNegativeButton(android.R.string.cancel, null)`; `.create()`.
- **취소 경로(REQ-2-15, SRS A5)**: 취소 단추·뒤로 가기·바깥 누르기·Esc는 모두 대화상자를 닫기만 한다 — 이 프래그먼트는 `onCancel`·`onDismiss`를 재정의하지 않고 리스너를 부르지 않으므로 복사 목록·선택·작업 어느 것도 바뀌지 않는다. `isCancelable`은 기본값(true, `ConfirmDeleteFilesDialogFragment`와 같음).
- **Esc(`Keyboard_Exit`, HLD R11)**: API 28 이상은 `Dialog.onKeyDown`이 `KEYCODE_ESCAPE`를 받아 취소 가능한 대화상자를 `cancel()`한다(android-35 소스 `android/app/Dialog.java:678-688`). API 21~27은 `Dialog`에 Esc 처리가 없고, 처리되지 않은 Esc에 대해 시스템이 키 배치의 대체 키(BACK)를 보내 뒤로 가기로 닫힐 것으로 본다(미확인 — T-06(d)). 이 단계는 추가 코드를 넣지 않는다. T-06(d)가 API 27 이하에서 실패하면 `dialog.setOnKeyListener`로 `KEYCODE_ESCAPE` → `cancel()`을 더한다(검수에 알림).
- **버튼 터치 대상(`Touch_Target_Size`, HLD R11 닫기)**: 테마 값으로 48dp 이상이다. 대화상자 단추 스타일 `buttonBarPositive/NegativeButtonStyle`은 `Widget.MaterialComponents.Button.TextButton.Dialog`(MDC 테마) / `Widget.Material3.Button.TextButton.Dialog`(M3 테마, 이 앱은 두 테마를 다 가짐: `res/values/themes.xml:10`, `themes_material3.xml:10`)이고, 둘 다 `Widget.AppCompat.Button`의 `android:minHeight` 48dip(appcompat 1.7.1 `res/values/values.xml:1094`)을 덮어쓰지 않는다(material 1.12.0 `res/values/values.xml:6348-6400`, `:5238-5335` — `minHeight` 항목 없음, `minWidth` 64dp). 단추의 위·아래 inset(6dp/4dp)은 뷰 안쪽 그리기 여백이라 터치 영역(뷰 경계)은 줄지 않는다. 실측은 T-16(a).
- **전체 폭 금지(`UI_Secondary_Elements`)**: `ConfirmDeleteFilesDialogFragment`와 같은 `MaterialAlertDialogBuilder(requireContext(), theme)` — 큰 화면에서 대화상자 창 폭은 테마의 최소 폭 규칙을 따르는 내용 폭이다. 실측은 T-16(b).
- **대비·설명(`Visual_Contrast`, `Content_Description`)**: 새 글자는 대화상자 제목·본문의 기본 글자 스타일이고 새 비텍스트 조작 요소는 없다(두 단추는 글자 단추). 실측은 T-16(c).
- **오류**: 없음(입출력 없음). 인자 누락은 구조 위반(`args()`가 던짐, 기존과 같음).
- **기존 코드와의 관계**: `ConfirmDeleteFilesDialogFragment.onCreateDialog`(`:27-52`)와 같은 빌더·버튼 구성.

### 2.3 `FileListFragment` (수정, K5 — 붙여넣기 경로)

#### 클래스 선언 (수정)
- 기존 `:122-126`의 인터페이스 목록에 `ConfirmCopyMoveDialogFragment.Listener`를 더한다(`ConfirmDeleteFilesDialogFragment.Listener` 옆).

#### `FileListFragment.onBottomActionModeMenuItemClicked(item: MenuItem): Boolean` (수정)
- **추적**: HLD 3.6 "[붙여넣기]" / REQ-2-14
- **알고리즘**: `R.id.action_paste` 분기(기존 `:1090-1093`)의 `pasteFiles(currentPath)`를 `confirmPasteFiles(currentPath)`로 바꾼다. 다른 분기 그대로.
- **기존 코드와의 관계**: 붙여넣기 단추와 Ctrl+V(`res/menu/file_list_paste.xml`, `onKeyShortcut` 기존 `:497-506`의 `menu.performShortcut`)가 모두 이 분기로 온다 — `pasteFiles`의 호출처는 이 한 곳뿐(`grep -n "pasteFiles(" FileListFragment.kt`).

#### `FileListFragment.confirmPasteFiles(targetDirectory: Path): Unit` (private, 기존 `pasteFiles` 대체)
- **추적**: HLD K5·K6, 3.6 / REQ-2-13, 14
- **책임**: 공유 복사 목록을 이 창의 현재 폴더로 붙여넣는 확인 창을 연다.
- **입력·전제**: 메인 스레드. 붙여넣기 막대가 보일 때만 불린다(막대는 복사 목록이 비어 있지 않을 때만, 기존 `:1013-1018`; 읽기 전용 폴더에서는 단추 비활성, `:1033-1038`).
- **출력·사후조건**: 확인 창이 열린다. **복사 목록·선택·작업은 바꾸지 않는다**(지금의 `clearPasteState()`는 "실행" 리스너로 옮김).
- **알고리즘**:
  1. `val pasteState = viewModel.pasteState`; `if (pasteState.files.isEmpty()) return`(방어 — 2.1 `getCopyMoveType` 전제).
  2. `ConfirmCopyMoveDialogFragment.show(pasteState.copy, pasteState.files, targetDirectory, ConfirmCopyMoveDialogFragment.Origin.PASTE, this)`.
- **오류**: 없음.
- **기존 코드와의 관계**: 기존 `pasteFiles`(`:1104-1116`) 삭제·대체. 종류(`copy`)와 대상은 지금과 같은 값을 쓴다.

#### `FileListFragment.copyMoveFiles(type: CopyMoveType, sources: List<Path>, targetDirectory: Path, files: FileItemSet, origin: ConfirmCopyMoveDialogFragment.Origin): Unit` (override, 신규)
- **추적**: HLD K6 "실행 시 리스너가…", 3.6 / REQ-2-12(정리 몫), 13, 14, 16, 21
- **책임**: 확인 창의 "실행"에서 작업을 시작하고 출처별로 정리한다.
- **입력·전제**: 메인 스레드(대화상자 단추 콜백), 창 프래그먼트가 붙어 있음(대화상자는 이 창의 자식이라 창보다 먼저 사라진다). `sources`·`targetDirectory`는 확인 창이 판정에 쓴 값(2.1).
- **출력·사후조건**:
  1. `type == MOVE` → `FileJobService.move(sources, targetDirectory, requireContext())`, 그 밖(`COPY`·`EXTRACT`) → `FileJobService.copy(...)`(압축 풀기는 `CopyFileJob`이 원본으로 스스로 판단, `FileJobs.kt:739`). 둘 다 작업을 넣고 바로 돌아온다(`FileJobService.kt:104-113`).
  2. `origin == PASTE` → `viewModel.clearPasteState()`(기존 `:1115`와 같음 — 공유 목록 전체를 비우고 두 창의 막대가 함께 닫힌다, HLD K8). `origin == OTHER_PANE` → `viewModel.selectFiles(files, false)`(그 항목들의 선택 해제, SRS REQ-2-12, 기존 복사·잘라내기의 `:915`·`:920`과 같은 방식). 복사 목록은 건드리지 않는다.
- **오류**: 없음 — 작업 실패·충돌·자기 안으로는 작업 스레드에서 기존 알림·대화상자가 처리한다(`FileJobs.kt:1196-1235`, `:1237-`, `:1328`, HLD 3.8).
- **기존 코드와의 관계**: 기존 `pasteFiles`의 본문(`:1106-1115`)을 옮기고, 경로 목록을 `makePathListForJob(pasteState.files)`에서 확인 창이 넘긴 `sources`로 바꾼 것(정규화 범위는 복사·이동 호출뿐, HLD 3.6-1).
- **순서 근거**: 작업 시작(1) 뒤에 정리(2). 기존과 같은 순서라 막대가 닫히는 시점도 같다.

#### `FileListFragment.makePathListForJob` — **변경 없음** (기존 `:1118-1119`, 삭제·압축이 계속 씀)

### 2.4 문자열 (K9의 확인 창 몫)

`res/values/strings.xml`의 `file_list_paste_action_extract_here`(기존 `:319`) 뒤에 둔다. 한국어는 `values-ko/strings.xml`의 같은 자리(`:283` 뒤). `translatable="false"` 별칭은 한국어에 넣지 않는다(원 문자열이 이미 번역됨: `copy` ko `:13`, `file_item_action_extract` ko `:290`).

| 이름 | 종류 | 기본(영어) | 한국어 |
|---|---|---|---|
| `file_list_confirm_copy_move_copy_title` | string, `translatable="false"` | `@string/copy` | (별칭) |
| `file_list_confirm_copy_move_extract_title` | string, `translatable="false"` | `@string/file_item_action_extract` | (별칭) |
| `file_list_confirm_copy_move_move_title` | string | `Move` | `이동` |
| `file_list_confirm_copy_move_item_name_format` | string | `“%1$s”` | `“%1$s”` |
| `file_list_confirm_copy_move_item_count_format` | plurals | one `%1$,d item` / other `%1$,d items` | other `%1$,d개 항목` |
| `file_list_confirm_copy_move_locations_format` | plurals(수량 = 다른 위치 수) | one `%1$s and %2$,d other location` / other `%1$s and %2$,d other locations` | other `%1$s 외 %2$,d곳` |
| `file_list_confirm_copy_move_from_to_format` | string | `%1$s → %2$s` | `%1$s → %2$s` |
| `file_list_confirm_copy_move_to_format` | string | `To %1$s` | `대상: %1$s` |
| `file_list_confirm_copy_move_same_directory_all` | string | `The target is the same folder as the original location.` | `대상이 원래 위치와 같은 폴더입니다.` |
| `file_list_confirm_copy_move_same_directory_some_format` | plurals | one `The original location of %1$,d item is the same folder as the target.` / other `The original location of %1$,d items is the same folder as the target.` | other `%1$,d개 항목의 원래 위치가 대상과 같은 폴더입니다.` |
| `file_list_confirm_copy_move_positive` | string | `Start` | `실행` |

- 한국어 문구는 HLD K9(요구사항 v2 문구)와 같다. 화살표 `→`는 번역자가 RTL 언어에서 바꿀 수 있게 서식 문자열 안에 둔다(경로 자체는 2.2 알고리즘 3의 방향 격리).
- 취소 단추는 `android.R.string.cancel`(기존 확인 창과 같음).

## 3. 테스트 설계

### 3.1 수동 시험 (에뮬레이터, 기준 APK 비교)
준비는 LLD-A 5.1과 같다(빌드 명령, 기준 APK = `origin/develop` 머리, C1·C2·C4, 매 시험 뒤 `adb logcat -b crash -d` 비어 있음). 시험 폴더: `adb shell mkdir -p /sdcard/Download/lld8/a /sdcard/Download/lld8/b`, `adb shell touch /sdcard/Download/lld8/f1.txt /sdcard/Download/lld8/f2.txt /sdcard/Download/lld8/f3.txt /sdcard/Download/lld8/a/g1.txt`, 압축 파일 `t.zip`(안에 `x.txt`, `d/y.txt`)을 `adb push`로 `/sdcard/Download/lld8/`.

| 테스트 | 대상 함수 | 경우 | 절차 | 기대 결과 |
|---|---|---|---|---|
| T-00 빌드 | 전체 | 정상 | LLD-A 5.1-1 | `BUILD SUCCESSFUL`, 새 경고 없음 |
| T-01 회귀 없음(작업 쪽) | 2.3 | 정상 | 기준·새 APK로 같은 붙여넣기(복사 2개) | 만들어진 파일·알림이 같다(확인 창 단계만 추가) |
| T-02 복사 1개(REQ-2-14·15) | `confirmPasteFiles`, `onCreateDialog`, `copyMoveFiles` | 정상 | `lld8`에서 `f1.txt` 복사 → `b`로 이동 → 붙여넣기 | 확인 창: 제목 "복사", `“f1.txt”`, `/storage/emulated/0/Download/lld8 → /storage/emulated/0/Download/lld8/b`(실제 표시 경로), 안내 없음, 단추 "취소"·"실행". 창이 떠 있는 동안 알림 영역에 작업 알림 없음. 실행 → `b/f1.txt` 생성, 막대 닫힘 |
| T-03 여러 개·이동 | 같음 | 정상 | `f2`·`f3` 잘라내기 → `b` 붙여넣기 | 제목 "이동", "2개 항목", 실행 → 원본 사라지고 `b`에 생김 |
| T-04 압축 풀기 2종(SRS A4) | `getCopyMoveType`, `locationOf` | 정상·경계 | (a) `t.zip` 선택 → "압축 풀기" → `b`에서 "여기에 압축 풀기" (b) `t.zip` 안으로 들어가 `x.txt` 복사 → `b` 붙여넣기 | (a) 제목 "압축 풀기", `“t.zip”`, 원래 위치 `…/lld8`(압축 파일이 있는 폴더), 안내 없음, 실행 → 기준과 같은 결과 (b) 제목 "압축 풀기", 원래 위치 `…/lld8/t.zip/`가 아닌 `…/lld8/t.zip`(루트), 실행 → `b/x.txt` |
| T-05 압축 안 하위 폴더 표시 | `toLocationDisplayString` | 경계 | `t.zip/d` 안의 `y.txt` 복사 → `b` 붙여넣기 | 원래 위치 `…/lld8/t.zip/d` |
| T-06 취소 4종(REQ-2-15, A5) | `onCreateDialog` 취소 경로 | 정상 | 복사 목록 2개인 상태에서 확인 창을 열고 (a) 취소 (b) 뒤로 가기 (c) 바깥 누르기 (d) Esc(`adb shell input keyevent 111`) — API 36, 가능하면 API 27 이하 AVD도 | 넷 다 창이 닫히고 막대가 같은 개수로 남음, 작업 알림·파일 변화 없음. (d) API 27 이하 결과는 보고(미확인이면 그렇게 적음, 실패면 2.2 Esc 대안 적용) |
| T-07 회전 유지(REQ-2-15) | Args 복원, `onCreateDialog` 재계산 | 경계 | T-09의 확인 창(안내 있음)을 띄운 채 회전 ×2 → 실행. 별도로 회전 뒤 취소 | 창과 내용(안내 포함)이 같게 남음. 실행 결과가 회전 전 대상과 같음. 취소 뒤 막대 그대로 |
| T-08 같은 폴더 전부(REQ-2-21, A7) | `createCopyMoveConfirmation` ALL | 정상 | (a) `lld8`에서 `f1` 복사 → 같은 `lld8`에서 붙여넣기 (b) `f1` 잘라내기 → 같은 폴더 붙여넣기 | (a) 안내 "대상이 원래 위치와 같은 폴더입니다." → 실행 → `f1 (1).txt` 생성(기준과 같음) (b) 안내 → 실행 → 변화 없음(기준과 같음) |
| T-09 여러 원래 위치·일부 같음(A3) | `otherLocationCount`, SOME | 경계 | `lld8`에서 `f1` 복사, `lld8/a`에서 `g1` 복사(목록 2개) → (a) `lld8`에 붙여넣기 (b) `b`에 붙여넣기 | (a) 원래 위치 "`…/lld8` 외 1곳", 안내 "1개 항목의 원래 위치가 대상과 같은 폴더입니다." (b) "외 1곳", 안내 없음 |
| T-10 충돌 순서(REQ-2-16) | `copyMoveFiles` | 정상 | `b`에 `f1.txt`가 있는 상태에서 `lld8/f1` 복사 → `b` 붙여넣기 | 확인 창(안내 없음) → 실행 → 기존 충돌 창. 확인 창이 충돌을 미리 알리지 않음 |
| T-11 `.` 경로(정규화) | `toCopyMoveJobPath` | 경계(의도된 변화 0.3) | 넘침 메뉴 "경로로 이동"에 `/storage/emulated/0/Download/lld8/.` 입력 → 그 화면에서 `f1` 복사 → 같은 화면에서 붙여넣기. 그리고 일반 `lld8` 화면에서 복사한 `f1`을 `…/lld8/.` 화면에 붙여넣기 | 둘 다 안내 "전부 같음" → 실행 → 사본 생성. 확인 창의 대상 표시는 `…/lld8`(정규화 값). 기준 APK에서는 둘째 경우 아무 변화 없음 — 0.3의 의도된 차이로 기록 |
| T-12 `..` 경로(정규화 안 함, F1) | `toCopyMoveJobPath` | 경계 | `…/lld8/a/..` 입력 → `lld8` 화면에서 복사한 `f1`을 그 화면에 붙여넣기. 시험할 때만 `copyMoveFiles`에 `Log.d`(sources·targetDirectory)를 넣고 **커밋 전에 지운다** | 안내 없음, 로그에 대상이 `…/lld8/a/..` 그대로. 결과는 기준 APK와 같음(같은 파일이라 변화 없음) |
| T-13 읽기 전용 대상 | (변경 없음 확인) | 경계 | 복사 목록이 있는 채로 `t.zip` 안으로 이동 | 붙여넣기 단추 비활성(기준과 같음), 확인 창이 열리지 않음 |
| T-14 원격 경로(가능하면) | `toLocationDisplayString`, 정규화 | 정상 | SMB·SFTP 서버를 쓸 수 있으면 원격 폴더 → 로컬 붙여넣기 1회 | 원래 위치가 기존 URI 표시, 실행 결과 기준과 같음. 서버가 없으면 **미확인**(2.1 provider별 확인으로 갈음) |
| T-15 Ctrl+V | `onBottomActionModeMenuItemClicked` | 정상 | 막대가 있을 때 `adb shell input keycombination 113 50` | 확인 창이 뜸(바로 시작하지 않음) |
| T-16 접근성·큰 화면·RTL | `onCreateDialog` | 정상 | (a) Accessibility Scanner 또는 레이아웃 검사기로 두 단추 높이 측정(C1) (b) C2·C4에서 확인 창 폭 캡처 (c) 밝은·어두운 테마에서 Scanner 대비 확인, TalkBack으로 제목·본문·단추 읽기 (d) "RTL 레이아웃 방향 강제"(`adb shell settings put global debug.force_rtl 1`, 끝나면 0)와 아랍어 로캘에서 T-02·T-09 확인 창 캡처 | (a) 두 단추 48dp 이상 (b) 화면 전체 폭이 아님 (c) Scanner 대비 경고 없음, TalkBack이 내용·"실행"·"취소"를 읽음 (d) 경로·`→`·"외 N곳"의 순서가 깨지지 않음(경로 글자가 뒤섞이지 않음) |
| T-17 알림 없음(REQ-2-14) | 2.3 | 정상 | 큰 폴더 복사 목록으로 확인 창을 띄운 채 알림 영역을 내려 봄 | 작업 알림 없음. 실행 뒤에야 생김 |
| T-18 프로세스 종료 복원 | Args 복원 | 경계 | 확인 창을 띄운 채 홈 → `adb shell am kill me.zhanghai.android.files` → 복귀 → 실행 | 확인 창이 같은 내용으로 복원되고 실행 결과가 보인 대로. 크래시 없음(복사 목록은 비어 있음 — A6과 같음) |
| T-19 다른 확인 창 회귀 | (변경 없음 확인) | 정상 | 삭제·압축 만들기·이름 바꾸기 각 1회 | 기준과 같음 |
| T-20 새 작업 사이 붙여넣기(HLD D6) | 2.3 | 정상 | 넘침 메뉴 "새 작업" 화면에서 복사 → 원래 화면에서 붙여넣기 | 원래 화면에서 확인 창 → 실행 → 생성 |

- 판정: 0.3의 차이(확인 창, T-11) 외에 기준 APK와 다른 결과가 있으면 실패.
- `OTHER_PANE` 갈래(`copyMoveFiles`의 선택 해제)는 진입점이 #9에서 생기므로 #9의 시험에서 확인한다. 이 단계는 코드 검수의 줄 대조로 확인한다.
- 기록: 구현 PR 코멘트에 시험별 결과(통과/실패/미확인)와 캡처 파일 이름, 캡처는 `$MAIN_REPO/_workspace/`.

### 3.2 단위 시험 (테스트 의존성 승인 시에만 — SRS C-3, 6장 F3)
대상은 2.1의 순수 함수. 경로는 시험용 `ByteStringListPath` 하위 클래스(가짜 파일 시스템 두 개 — 같은 클래스·다른 파일 시스템 비교용)로 만든다. 이 하위 클래스가 JVM 단위 시험에서 Android 스텁 없이 만들어지는지는 미확인 — 안 되면 3.1로만 확인한다.

| 테스트 | 대상 함수 | 경우 | 기대 결과 |
|---|---|---|---|
| U-01 | `getCopyMoveType` | copy·일반 / copy·전부 압축 / copy·일부 압축 / move·압축 | COPY / EXTRACT / COPY / MOVE |
| U-02 | `toCopyMoveJobPath` | `/a/b`, `/a/./b`, `/a/b/../c`, `/` | `/a/b`, `/a/b`, `/a/b/../c`(그대로), `/` |
| U-03 | `createCopyMoveConfirmation` 같은 폴더 | 전부 같음 / 일부(1/2) / 없음 / 대상 `/a/.` / 대상 `/x/../a` / 다른 파일 시스템의 같은 문자열 경로 | ALL / SOME(1) / NONE / ALL / NONE / NONE |
| U-04 | 같은 함수 위치 요약 | 위치 1곳 / 2곳(삽입 순서 첫 항목) / 같은 위치 3개 | other 0 / first = 첫 항목 위치·other 1 / other 0 |
| U-05 | 같은 함수 `sources` | 순서가 섞인 3개 | `toUri()` 정렬, 각 원소 = `toCopyMoveJobPath()` 값, `sameDirectoryCount`가 이 값으로 계산됨 |

## 4. 확장 지점 (#9가 붙을 자리)

| 하위 이슈 | 붙을 자리 | 이 단계에서 준비한 것 |
|---|---|---|
| #9 E | 선택 메뉴 `res/menu/file_list_select.xml`에 "반대쪽으로 복사"·"반대쪽으로 이동" 두 항목, `updateOverlayToolbar`(기존 `:787-851`, 선택 메뉴 부분 `:807-833`)에서 반대쪽 경로 읽기 전용·선택 읽기 전용 판단(HLD 3.6 읽기 전용 규칙), `onOverlayActionModeMenuItemClicked`(기존 `:853-892`)에서 `ConfirmCopyMoveDialogFragment.show(copy, viewModel.selectedFiles, <반대쪽 경로>, Origin.OTHER_PANE, this)`. 반대쪽 경로는 #6의 `DualPaneViewModel.panePaths`. | `show` API(살아 있는 선택 집합을 넘겨도 됨 — 복사본을 만든다), `Origin.OTHER_PANE`, 리스너의 선택 해제 갈래, 판정·문구 전부 |
| #6 B | 두 창이 되어도 확인 창은 연 창의 자식이라(`show(fragment)`) 그 창의 뷰모델로 실행·정리한다. 바꿀 것 없음. | — |

## 5. 위험 처리 (HLD 6장 중 이 단계 몫)
| ID | 이 단계 처리 |
|---|---|
| R9(1) | HLD대로 둔다 — 심볼릭 링크·마운트 별칭은 풀지 않는다(안내가 안 나올 수 있음, 작업은 기존 처리). |
| R9(2) | **닫음** — 2.1 "provider별 `normalize()` 확인": 목록 경로의 `normalize()`는 모두 입출력 없는 기반 구현, 압축 풀기 더미 루트 값은 바뀌지 않음. |
| R9(3) | **완화안 적용, PM 결정 필요(F1)** — `..` 조각이 있는 경로는 정규화하지 않고 그대로 판정·작업에 쓴다(2.1 `toCopyMoveJobPath`). |
| R11 | **버튼 높이 닫음**(테마 값, 2.2) — 실측 T-16(a). **Esc**: API 28 이상 프레임워크 처리 확인(소스), API 27 이하 미확인 — T-06(d), 실패 시 키 리스너 추가(2.2). 전체 폭은 T-16(b). |

## 6. 설계 피드백
설계 문서(SRS·HLD)는 고치지 않았다.

- **F1 (PM 결정 필요) HLD 3.6-1·D11의 "작업에도 같은 `normalize()` 값" → `..`가 있는 경로는 정규화하지 않음으로 좁힘**: HLD R9 대응 칸은 "(3)이 실제로 생기면 `..`가 남은 경로에서는 정규화·안내를 하지 않는 쪽으로 좁히는 안을 LLD가 PM에게 올린다"고 정했다. 실제로 생긴다: Android의 `/sdcard`는 심볼릭 링크라 경로 입력으로 `/sdcard/..`를 열면 목록은 `/storage/self`의 내용인데 `normalize()`는 `/`를 준다 — HLD대로면 그 화면에 붙여넣기한 작업의 대상이 `/`가 되고, 그 화면에서 복사한 항목(`/sdcard/../primary`)은 없는 경로 `/primary`가 원본이 된다. 이 LLD의 안: 경로에 `..` 조각이 있으면 그 경로는 **정규화하지 않고 원래 값으로 판정하고 작업에도 원래 값을 넘긴다**(`.`만 있는 경로는 정규화). HLD가 적은 "정규화·안내를 하지 않음"과의 차이: 안내를 끄지 않고 원래 값의 `==`로 판정한다 — 작업 코드(`FileJobs.kt:750`)도 같은 `==`를 쓰므로 "안내와 결과가 같다"는 HLD 3.6-1의 원칙이 유지되고, `..` 경로의 동작은 지금과 같다. 잃는 것: `/a/b/..`처럼 링크가 없는 `..` 경로에서 같은 폴더 안내·사본 생성이 되지 않는다(지금도 같은 파일 판정으로 아무 변화 없이 끝남, `LinuxCopyMove.kt:46-49`). 바뀌는 범위는 2.1 `toCopyMoveJobPath` 한 함수다. PM이 HLD 원안(모두 정규화)을 고르면 그 함수를 `normalize()` 한 줄로 바꾸고 T-12 기대 결과를 바꾼다. 설계팀이 HLD 3.6-1·D11·R9 문구를 맞춰야 한다(PM 결정 뒤).
- **F2 HLD 줄 번호(문서만)**: HLD의 `FileListFragment.kt` 인용(`:1167-1179` 붙여넣기, `:1181-1182` `makePathListForJob`, `:991` 삭제, `:1013` 압축, `:874-875`·`:1096-1101` 읽기 전용, `:1082` 압축 판단 등)은 #5 이전 줄이다. a76055e에서는 `:1104-1116`, `:1118-1119`, `:928`, `:950`, `:811-812`·`:1033-1038`, `:1019` 등이다. 동작 영향 없음 — 릴리스 문서 동기화 때 맞추면 된다.
- **F3 (PM 결정 필요) 단위 시험 의존성**: HLD 3.6-1은 같은 폴더 판정을 "단위 테스트 대상(SRS C-3)"으로 둔다. 저장소에 테스트 의존성이 없어 넣으려면 라이브러리 승인(PROCESS_ROLES 10장)이 필요하다 — 요청 후보 `testImplementation 'junit:junit:4.13.2'`(EPL-1.0, 테스트 전용·APK에 들어가지 않음). 승인되지 않으면 3.1 수동 시험만으로 확인한다(T-08·T-09·T-11·T-12가 판정 경우를 덮음). #6(너비 판단)도 같은 결정을 쓴다.
- **F4 확인**: HLD K6의 "출처" 인자는 `Origin { PASTE, OTHER_PANE }`, 리스너는 `copyMoveFiles(...)` 하나로 정했다(이름만, 동작 영향 없음).

## 7. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-10-29 | v1 | 최초 작성 | 이슈 #8 LLD 단계 | PM |
