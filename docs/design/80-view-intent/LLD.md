# LLD — 앱이 떠 있을 때 받은 경로 열기 (이슈 #80)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1.1) |
| 근거 | SRS v1.3, HLD v1.2 (`docs/design/80-view-intent/`), V0 기록(`_workspace/wo/80-view-intent/`) |

줄 번호는 develop `ac012e0` 기준이다.

## 0. V0 결과 (SRS 4.2 V0, HLD R2)
API 36 AVD(`Medium_Phone_API_36_b`, emulator-5580). 고치기 전 빌드, 같은 빌드에 수명주기 로그만 더한 빌드(L), `singleTop`을 더한 버려지는 시험 빌드(S, S2)로 확인했다. 시험 빌드 변경은 `spike.diff`(커밋하지 않음).

| 경우 | 고치기 전(standard) | `singleTop`(S) |
|---|---|---|
| (a) 맨 앞에서 root intent와 같은 VIEW | `am`: "delivered to currently running top-most instance"(result 3). 그러나 **`onNewIntent`는 불리지 않는다**(L 빌드 로그에 없음). 화면 그대로 | `onPause → onNewIntent → onResume` |
| (b) 맨 앞에서 다른 폴더 VIEW | 같은 작업에 새 인스턴스(`Hist #1`) | `onNewIntent`, 인스턴스 1개 |
| 백그라운드에서 다른 폴더 VIEW | 새 인스턴스(`Hist #2`) | `onStart → onNewIntent → onResume` |
| 백그라운드에서 root intent와 같은 VIEW | 작업만 앞으로(result 2), 전달 없음 | `onStart → onNewIntent → onResume` |
| 프로세스 종료 뒤 VIEW | — | `onCreate(saved) → onStart → onNewIntent → onResume` |
| 폴더 바로 가기 플래그(`NEW_TASK\|CLEAR_TASK`) | — | 새 인스턴스, 이전 것 파괴(REQ-80-06 유지) |
| "새 창에서 열기"(지금 플래그) | 새 작업 | **새 작업 없이 자기 `onNewIntent`로**(result 3) → HLD v1.2 C7·D7로 해결 |
| 위 + `FLAG_ACTIVITY_PREVIOUS_IS_TOP` | — | 새 작업(result 0). 선택 화면에서 D4(앞·백그라운드 둘 다) 새 작업, `BAL_ALLOW_VISIBLE_WINDOW` |

- 모든 `onNewIntent`에서 Activity는 STARTED, Host는 `isResumed == false`, Host 뷰는 이미 있다. 곧 HLD 3장 2·D2의 가정은 API 36에서 맞다.
- SRS 1.2 ④(다른 앱이 `NEW_TASK` 없이 보냄)는 재현하지 못했다. `am start`는 늘 `NEW_TASK`를 단다. 미확인으로 남는다.
- 원본 기록: `v0_prefix.txt`, `v0_L.txt`, `v0_S.txt`, `v0_recreate.txt`, `newwin_S.txt`, `newwin_S2.txt`, `d4_fg.txt`, `d4_S2.txt`.

## 1. 변경 파일
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | 수정 | `FileListActivity`에 `android:launchMode="singleTop"`(C1) |
| `filelist/PaneStartPath.kt` | 수정 | 순수 함수 `isPickAction`·`resolveIntentPath`·`resolveNewIntentAction`, `enum NewIntentAction`, 상수 `ACTION_VIEW_DOWNLOADS`·`NEW_TASK_VIEW_INTENT_FLAGS`(C4·C7) |
| `filelist/FileListActivity.kt` | 수정 | `onNewIntent`(C2), `createNewTaskViewIntent`(C7), Android 쪽 연결 `Intent.resolvePanePath()`(C4) |
| `filelist/FileListHostFragment.kt` | 수정 | `onNewIntent`·`applyPendingNewIntent`, `onResume` 호출, `isPickIntent` → `isPickAction`(C3) |
| `filelist/FileListFragment.kt` | 수정 | 시작 경로 계산을 C4로(C5), `openInNewTask`가 C7 사용, `argsPath`·`ACTION_VIEW_DOWNLOADS` 사본 제거 |
| `app/src/test/.../filelist/PaneStartPathTest.kt` | 수정 | 3장 시험(C6) |
| `docs/STANDARDS_TRACEABILITY.md` | 수정 | 4장에 #80 행(SRS 5장) |

## 2. 함수 명세

### `filelist.ACTION_VIEW_DOWNLOADS` (상수, 이동)
- **추적**: HLD C4 / REQ-80-08
- `internal const val ACTION_VIEW_DOWNLOADS = "me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS"`를 `PaneStartPath.kt`에 둔다. `FileListFragment.kt:1675-1676`의 `private const val` 사본은 지운다. 값은 `res/xml/shortcuts.xml:19`와 같다.

### `filelist.isPickAction(action: String?): Boolean` (신규, `internal`, 파일 수준)
- **추적**: HLD C4 / REQ-80-05, 08
- **책임**: Intent 동작이 선택 모드인지 판정하는 하나뿐인 규칙.
- **입력·전제**: `action`은 `Intent.action`, null 가능. 어느 스레드든.
- **출력**: `ACTION_GET_CONTENT`·`ACTION_OPEN_DOCUMENT`·`ACTION_CREATE_DOCUMENT`·`ACTION_OPEN_DOCUMENT_TREE`면 `true`, 그 밖(null 포함)은 `false`.
- **오류**: 없음.
- **알고리즘**: `when (action) { 네 값 -> true; else -> false }`.
- **기존 코드와의 관계**: `FileListHostFragment.isPickIntent`(`FileListHostFragment.kt:275-281`)를 대체한다. Host는 `isPickAction(args.intent.action)`을 부르고 `isPickIntent`와 그 동기화 주석은 지운다. `FileListFragment.kt:299`의 동기화 주석은 "pick 갈래는 `isPickAction()`과 같아야 한다"로 바꾼다(그 `when` 갈래의 값 목록은 PickOptions 구성 때문에 남는다).

### `filelist.resolveIntentPath(action: String?, path: Path?, mimeType: String?, downloadsPath: () -> Path, archiveRootPath: (Path) -> Path = { it.createArchiveRootPath() }): Path?` (신규, `internal`, 파일 수준)
- **추적**: HLD C4 / REQ-80-01, 04, 08
- **책임**: 선택 모드가 아닌 Intent가 여는 경로를 정한다. 새로 시작할 때와 받았을 때가 같이 쓴다.
- **입력·전제**: `action`·`mimeType`은 `Intent.action`·`Intent.type`(문자열, null 가능). `path`는 `Intent.extraPath`(null 가능). `downloadsPath`는 `ACTION_VIEW_DOWNLOADS`일 때만 부른다. `archiveRootPath`는 시험용 주입점이고 기본값은 지금 쓰는 `createArchiveRootPath`(`PathArchiveExtensions.kt:27-28`). 메인 스레드에서 불려도 된다.
- **출력·사후조건**:
  - `action == ACTION_VIEW_DOWNLOADS` → `downloadsPath()`(`path`는 무시, 지금과 같음).
  - `path == null` → `null`.
  - `mimeType?.asMimeTypeOrNull()`이 있고 `path.isArchiveFile(그 MIME)`(`PathExtensions.kt:22`: MIME 판정과 `is ArchivePath`뿐) → `archiveRootPath(path)`.
  - 그 밖 → `path`.
  - 선택 모드 동작을 넘겨도 같은 규칙을 적용한다. 선택 모드 걸러내기는 호출하는 쪽의 몫이다(지금 `FileListFragment.kt:300-334`도 선택 갈래에서 `path`를 그대로 둔다 — 2장 C5 참고).
- **오류**: 던지지 않는다. `archiveRootPath` 기본값은 압축 파일 시스템 객체만 만들고 파일을 읽지 않는다(`ArchiveFileSystemProvider.kt:60-64`, 지금 시작 경로와 같은 호출).
- **알고리즘**: 위 순서 그대로 `if` 사슬.
- **기존 코드와의 관계**: `FileListFragment.kt:335-347`의 두 갈래를 옮긴다. HLD 시그니처에 `archiveRootPath` 인자를 더했다(4장 F1).

### `filelist.NewIntentAction` (신규, `internal enum class`) / `filelist.resolveNewIntentAction(isPickScreen: Boolean, action: String?, flags: Int, path: Path?): NewIntentAction` (신규, `internal`, 파일 수준)
- **추적**: HLD C3 ①②③, D4, R5 / REQ-80-01, 04, 05, 06
- **책임**: 떠 있는 화면이 받은 Intent로 무엇을 할지 정한다. 값: `IGNORE`, `OPEN_IN_ACTIVE_PANE`, `OPEN_IN_NEW_TASK`.
- **입력·전제**: `isPickScreen`은 받는 화면이 선택 모드 화면인지(`isPickAction(args.intent.action)`). `action`·`flags`는 받은 Intent의 값. `path`는 받은 Intent의 `resolvePanePath()` 결과.
- **출력**(위에서부터 먼저 맞는 것):
  1. `isPickAction(action)` → `IGNORE`(C3 ①, REQ-80-05(a)).
  2. `flags and FLAG_ACTIVITY_MULTIPLE_TASK != 0` → `IGNORE`. 새 작업을 바란 Intent(C7, "새 창에서 열기")가 어떤 버전에서 `PREVIOUS_IS_TOP`을 무시하고 자기에게 돌아와도(R5) 활성 창을 옮기거나 D4가 되풀이되지 않게 끊는다. 바깥에서 오는 경로 Intent는 이 플래그를 달지 않는다(4장 F2).
  3. `path == null` → `IGNORE`(REQ-80-04, A4. `ACTION_MAIN`도 여기서 걸린다).
  4. `isPickScreen` → `OPEN_IN_NEW_TASK`(C3 ②, D4).
  5. 그 밖 → `OPEN_IN_ACTIVE_PANE`(C3 ③).
- **오류**: 없음.
- **기존 코드와의 관계**: 새 규칙. Android 없이 시험한다(3장).

### `filelist.NEW_TASK_VIEW_INTENT_FLAGS` (상수, 신규) / `FileListActivity.Companion.createNewTaskViewIntent(path: Path): Intent` (신규)
- **추적**: HLD C7, D7 / REQ-80-05, 06
- **책임**: 새 작업에 둘러보기 화면을 여는 Intent 하나를 만든다.
- **정의**: `PaneStartPath.kt`에 `internal const val NEW_TASK_VIEW_INTENT_FLAGS = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP`(컴파일 상수라 JVM 시험에서 읽힌다). `createNewTaskViewIntent(path) = createViewIntent(path).addFlags(NEW_TASK_VIEW_INTENT_FLAGS)`.
- **출력**: `ACTION_VIEW`, 경로 extra, 위 세 플래그.
- **기존 코드와의 관계**: `FileListFragment.openInNewTask`(`FileListFragment.kt:977-981`)는 `startActivitySafe(FileListActivity.createNewTaskViewIntent(path))`로 바뀐다. 폴더 바로 가기(`:1615-1616`)는 바꾸지 않는다(HLD 4장).

### `filelist.resolvePanePath` — `internal fun Intent.resolvePanePath(): Path?` (신규, `FileListActivity.kt` 파일 수준)
- **추적**: HLD C4, D6 / REQ-80-08
- **책임**: Android 값(Intent·`Environment`)을 `resolveIntentPath`에 잇는다. 두 호출처(C3, C5)가 이 하나를 쓴다.
- **알고리즘**: `resolveIntentPath(action, extraPath, type, { Paths.get(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path) })`. `extraPath`(`IntentPathExtensions.kt:21-32`)와 `getExternalStoragePublicDirectory`는 문자열·URI 해석만 하고 파일을 읽지 않는다.
- **오류**: 없음(`extraPath`가 해석 실패를 `null`로 돌림).
- **기존 코드와의 관계**: `FileListFragment.kt:336-340`의 다운로드 경로 식을 옮긴다.

### `FileListActivity.onNewIntent(intent: Intent)` (신규, override)
- **추적**: HLD C2 / REQ-80-01, 07
- **입력·전제**: 메인 스레드. V0상 STARTED 상태에서 온다.
- **알고리즘**: `super.onNewIntent(intent)` 뒤 `fragment.onNewIntent(intent)`. `fragment`는 `onCreate`(`FileListActivity.kt:31-36`)에서 늘 정해지므로 `onNewIntent` 때 초기화돼 있다. `setIntent`는 부르지 않는다(D5).
- **오류**: 없음.

### `FileListHostFragment.onNewIntent(intent: Intent)` (신규, public)
- **추적**: HLD C3, D2, R3 / REQ-80-01~06
- **입력·전제**: 메인 스레드, Host의 `onActivityCreated`(`:112-`) 뒤(V0: 뷰가 이미 있음).
- **출력·사후조건**: `pendingNewIntent`(새 필드 `private var pendingNewIntent: Pair<NewIntentAction, Path>? = null`)가 정해지거나 그대로다. 저장 상태에 넣지 않는다(HLD 3장 5).
- **알고리즘**:
  1. `intent.action`이 `isPickAction`이면 바로 끝낸다(경로를 계산하지 않는다 — 선택 Intent의 압축 경로로 압축 파일 시스템 객체를 만들지 않게, 검수 L3). 그 밖이면 `path = intent.resolvePanePath()`; `action = resolveNewIntentAction(isPickAction(args.intent.action), intent.action, intent.flags, path)`. `resolveNewIntentAction`의 1번 규칙은 순수 함수 계약으로 남긴다(같은 결과).
  2. `IGNORE`면 끝(앞서 보류된 값은 건드리지 않는다).
  3. `pendingNewIntent = action to path!!`(IGNORE가 아니면 `path`는 null이 아님). 새로 온 것이 앞의 보류 값을 덮는다(마지막 Intent가 이김).
  4. `if (isResumed) applyPendingNewIntent()` — R3: 어떤 버전이 `onPause` 없이 넘겨도 바로 적용한다. `isResumed`면 창 뷰가 있다.
- **오류**: 없음. 입출력 없음(REQ-80-04, SRS 3장).

### `FileListHostFragment.applyPendingNewIntent()` (신규, private)
- **추적**: HLD C3 ②③, D2, D3, D4 / REQ-80-01, 02, 03, 05
- **알고리즘**:
  1. `val (action, path) = pendingNewIntent ?: return`; `pendingNewIntent = null`.
  2. `OPEN_IN_ACTIVE_PANE` → `navigateTo(path)`(`:350-352` → `activePane.navigateTo`, `FileListFragment.kt:967-971`: 검색 접기, 스크롤 상태 저장, `TrailData.navigateTo`) 뒤 `closeNavigationDrawer()`(`:369-371`). 탐색 서랍의 책갈피 선택과 같은 두 호출이다. 다른 창·활성 창 번호는 건드리지 않는다(REQ-80-02).
  3. `OPEN_IN_NEW_TASK` → `startActivitySafe(FileListActivity.createNewTaskViewIntent(path))`(`FragmentExtensions.kt:174`). 선택 화면의 경로·선택 조건은 그대로다(REQ-80-05(b)).
  4. `IGNORE`는 보류되지 않으므로 오지 않는다(`when`의 `IGNORE -> {}`).
- **D4 시작 시점 결정**: `onNewIntent`에서 바로가 아니라 `onResume`(또는 R3의 resumed 즉시)에서 시작한다. 이유: V0에서 `onNewIntent` 때 시작해도 `BAL_ALLOW_VISIBLE_WINDOW`로 허용됐지만 그것은 API 36에서 작업이 이미 앞으로 온 뒤라서다. 버전별 백그라운드 시작 제한에 기대지 않도록 화면이 확실히 앞에 있는 resumed 때 시작한다. C3 ③과 같은 보류 장치를 쓴다.
- **오류**: `startActivitySafe`는 실패를 토스트로 알린다(지금 "새 창에서 열기"와 같음).

### `FileListHostFragment.onResume()` (수정, `:250-259`)
- **추적**: HLD D2
- **알고리즘**: `super.onResume()` 바로 뒤 `applyPendingNewIntent()`를 부르고, 그 뒤 지금의 권한 확인(`:253-258`)을 그대로 둔다. V0상 이 시점에 창 뷰는 프로세스 종료 뒤 재생성에서도 이미 있다.

### `FileListFragment.onActivityCreated` 시작 경로 부분 (수정, `:295-354`)
- **추적**: HLD C5 / REQ-80-07, 08
- **알고리즘**:
  1. `var path = argsPath`(`:296`)를 `var path: Path? = args.intent.extraPath`로 바꾼다. `argsPath`(`:142`)는 쓰는 곳이 `:296` 하나뿐이라 지운다.
  2. `when (val action = intent.action)`의 선택 갈래(`:301-334`)는 그대로 둔다. 이 갈래는 `path`를 건드리지 않는다(선택 화면의 시작 위치, 예: `OpenDirectoryContract`의 `extraPath`, `FileListActivity.kt:115-116`). 지금과 같다.
  3. `ACTION_VIEW_DOWNLOADS ->`·`else ->` 갈래(`:335-347`)를 `else -> path = intent.resolvePanePath()` 하나로 바꾼다.
  4. `resolvePaneStartPath`(`:351-353`) 우선순위(복원 > Intent > 기본)는 그대로다.
- **동작 불변 근거**: 선택 갈래가 아니면 `resolvePanePath()`가 지금 두 갈래와 같은 결과를 낸다(다운로드는 `path` 무시, 압축 MIME이면 압축 루트, 그 밖은 `extraPath`, 없으면 `null` → 기본 디렉터리).

### HLD R1·R3·D4 시점 해소 요약
- **R1**(결과를 바라는 시작이 맨 위로 넘겨지지 않음): 이 앱의 선택 계약을 부르는 곳은 모두 다른 화면이다(`PathPreference.kt:27`, `BookmarkDirectoryListFragment.kt:37`, `EditBookmarkDirectoryDialogFragment.kt:34`, `EditSftpServerFragment.kt:47`, `SaveAsActivity.kt:23` — 저장소 grep). 그래서 시작 순간 맨 위는 `FileListActivity`가 아니고 `singleTop`이어도 새 화면이 열린다. V0에서 설정 → 기본 폴더 고르기가 `Hist #2`로 새로 쌓이는 것을 확인했다(`d4_fg.txt`). 바깥 앱이 `NEW_TASK`로 선택 Intent를 이 작업에 보내 맨 위 화면에 넘겨지는 경우는 `resolveNewIntentAction` 1번 규칙으로 무시한다(그때 시스템은 결과를 바로 취소한다, HLD R1). 결과 반환은 V4에서 API 36으로 본다. 다른 버전은 HLD R1의 남은 위험이다.
- **R3**: `FileListHostFragment.onNewIntent` 4단계(`isResumed`면 바로 적용). V0에서는 늘 `isResumed == false`였다.
- **D4 시점**: `applyPendingNewIntent`의 "D4 시작 시점 결정" — resumed 때 시작.

## 3. 테스트 설계
JVM 단위 시험(`PaneStartPathTest.kt`, `./gradlew testDebugUnitTest`). Android 없이 돈다(`Intent.ACTION_*`·`FLAG_*`는 컴파일 상수, 경로는 `TestPaths`, MIME은 `MimeType` 값 클래스 — `CopyMoveConfirmationTest`가 이미 씀).

**fail-before 확인 방법**(#69와 같음): 새 함수라 고치기 전은 컴파일되지 않는다. 구현 첫 WIP 커밋에 지금 동작을 흉내 낸 임시 구현을 두고 시험을 돌려 실패를 기록한 뒤, 다음 커밋에서 실제 구현으로 통과를 기록한다.
- `isPickAction` 임시 = 지금 `isPickIntent` 본문 그대로 → 특성 시험이라 통과(동작 불변 확인용).
- `resolveIntentPath` 임시 = `FileListFragment.kt:335-347` 그대로 → 통과(동작 불변).
- `resolveNewIntentAction` 임시 = 늘 `IGNORE`(지금 떠 있는 화면은 받은 Intent를 쓰지 않음) → T-08·T-09·T-10 실패.
- `NEW_TASK_VIEW_INTENT_FLAGS` 임시 = `NEW_DOCUMENT or MULTIPLE_TASK`(지금 `openInNewTask` 플래그) → T-12 실패.

| 테스트 | 대상 | 경우 | 기대 결과 | 임시 구현 |
|---|---|---|---|---|
| T-01 `isPickAction_pickActions_true` | `isPickAction` | 선택 4종 | 모두 `true` | 통과 |
| T-02 `isPickAction_others_false` | `isPickAction` | `ACTION_VIEW`, `ACTION_MAIN`, `ACTION_VIEW_DOWNLOADS`, `null` | 모두 `false` | 통과 |
| T-03 `resolveIntentPath_folderView_returnsPath` | `resolveIntentPath` | `ACTION_VIEW`, 폴더 경로, `"resource/folder"` | 그 경로, `downloadsPath`·`archiveRootPath` 안 불림 | 통과 |
| T-04 `resolveIntentPath_archiveView_returnsArchiveRoot` | 〃 | `ACTION_VIEW`, `/x/t.zip`, `"application/zip"`, 가짜 `archiveRootPath`가 표식 경로 반환 | 표식 경로, `archiveRootPath`가 `/x/t.zip`로 1회 | 통과 |
| T-05 `resolveIntentPath_nonArchiveMime_returnsPath` | 〃 | `"text/plain"`, 잘못된 MIME `"zip"`, `null` MIME | 원래 경로 | 통과 |
| T-06 `resolveIntentPath_downloads_ignoresPath` | 〃 | `ACTION_VIEW_DOWNLOADS`, 경로 있음/없음 | `downloadsPath()` 값, 1회 | 통과 |
| T-07 `resolveIntentPath_noPath_null` | 〃 | `ACTION_VIEW`·`ACTION_MAIN`, 경로 `null` | `null`, `downloadsPath` 안 불림 | 통과 |
| T-08 `newIntent_browseScreen_folderView_opensInActivePane` | `resolveNewIntentAction` | `false`, `ACTION_VIEW`, `FLAG_ACTIVITY_NEW_TASK`, 경로 | `OPEN_IN_ACTIVE_PANE` | **실패** |
| T-09 `newIntent_browseScreen_downloads_opensInActivePane` | 〃 | `false`, `ACTION_VIEW_DOWNLOADS`, `0`, 다운로드 경로 | `OPEN_IN_ACTIVE_PANE` | **실패** |
| T-10 `newIntent_pickScreen_path_opensInNewTask` | 〃 | `true`, `ACTION_VIEW`, `FLAG_ACTIVITY_NEW_TASK`, 경로 | `OPEN_IN_NEW_TASK` | **실패** |
| T-11 `newIntent_ignored` | 〃 | ① 선택 동작 4종(두 화면 모두, 경로 있어도) ② `ACTION_MAIN`·경로 없는 VIEW(`path=null`, 둘러보기 화면·선택 화면 모두) ③ `NEW_TASK_VIEW_INTENT_FLAGS`를 단 VIEW(두 화면 모두) | 모두 `IGNORE` | 통과 |
| T-12 `newTaskViewIntentFlags_includePreviousIsTop` | `NEW_TASK_VIEW_INTENT_FLAGS` | `PREVIOUS_IS_TOP`·`MULTIPLE_TASK`·`NEW_DOCUMENT` 비트 | 셋 다 켜짐 | **실패**(`PREVIOUS_IS_TOP` 없음) |

REQ-80-08의 "같은 함수를 쓴다"는 코드 검사로 본다: `FileListFragment`와 `FileListHostFragment`가 둘 다 `Intent.resolvePanePath()`를 부르고, `isPickAction`이 Host 화면 판정과 `resolveNewIntentAction` 안에서 쓰인다.

### 3.1 AVD 절차 (SRS 4.2 V1~V5, 구현 단계)
emulator(로컬 AVD, `-s emulator-<port>`)에서 고친 빌드로 한다. 스크립트 틀은 `_workspace/wo/80-view-intent/v0.sh`·`newwin.sh`·`d4.sh`(경로·버튼 탭 함수). 확인값은 `uiautomator` 화면 문구, `dumpsys activity activities`의 `Hist #`, `dumpsys activity recents`.

| ID | 절차 | 기대 |
|---|---|---|
| V1 | 앱을 `/sdcard/V0/A`에 두고 `am start -W -a VIEW -t resource/folder -d file:///storage/emulated/0/V0/B -n …/.filelist.FileListActivity`; 같은 명령 한 번 더; 홈으로 간 뒤 한 번 더, root intent(A)도 한 번 | 매번 활성 창이 B(A면 A), `Hist #0` 하나. 연속 Intent: 맨 앞에서 B를 보낸 직후 곧바로 C를 보내면 C가 열린다(마지막이 이김). B 뒤 `-a android.intent.action.MAIN -n …`을 보내도 B가 그대로다(무시 Intent가 보류·적용을 취소하지 않음). 뒤로 가기 → `V0` → … 맨 위에서 앱을 떠남(REQ-80-03) |
| V2 | 폰 세로: 창 표시줄로 창 2 활성 → V1 | 창 2가 B, 창 1 경로 그대로. 태블릿 가로 AVD가 없으면 두 창 모드는 오너 기기 확인 항목으로 남긴다(기기 설치는 오너 확인 뒤) |
| V3 | `-t application/zip -d file:///…/t.zip`; 다운로드 바로 가기(`am start -a me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS -n …`) 앞·홈 두 번 | 압축 루트·다운로드 폴더, 뒤로 가기는 압축 파일이 있는 폴더 쪽 |
| V3b | 없는 폴더 URI, `-d` 없는 VIEW | 활성 창 오류 표시 / 화면 그대로 |
| V4 | 설정 → 기본 폴더 고르기 화면을 띄운 채 V1(앞·홈) → 최근 앱에 작업 하나 더, 그 작업이 B; 원래 작업은 선택 화면 그대로(`Hist`: FileList(pick)·Settings·FileList). 선택 화면에서 폴더를 골라 결과가 설정에 반영. 메뉴 "New window" → 작업 하나 더(`result code=0`), 원래 창 경로 그대로. 임시 로그(커밋하지 않음)로 `FileListHostFragment.onNewIntent`가 받은 `intent.flags`를 찍어, 외부 `am start` VIEW에는 `FLAG_ACTIVITY_MULTIPLE_TASK`가 없고(`0x10000000` 계열) 플래그가 `onNewIntent`까지 그대로 오는지 확인(4장 F2). 실행기 아이콘 → 경로 그대로. `-f 0x10008000` VIEW → 새로 시작 | REQ-80-05·06, R1·R5(API 36) |
| V5 | V1 뒤 하위 폴더로 이동 → 홈 → `am kill` → 복귀 → 그 하위 폴더; 회전 → 유지 | REQ-80-07 |

## 4. 설계 피드백
- **F0 (반영됨, HLD v1.2 `4a6f0b0`)**: V0에서 `singleTop`이면 "새 창에서 열기"가 새 작업 없이 보낸 화면 자신의 `onNewIntent`로 넘겨지는 것을 찾았다(`newwin_S.txt`). D4도 같은 이유로 선택 화면 자신에게 되돌아왔다(`d4_fg.txt`). `FLAG_ACTIVITY_PREVIOUS_IS_TOP`으로 해결되는 것을 확인해(`newwin_S2.txt`, `d4_S2.txt`) 설계팀에 문의했고, 설계팀이 C7·D7·R5를 더했다. SRS는 바뀌지 않았다(REQ-80-06 그대로).
- **F1**: HLD C4 `resolveIntentPath` 시그니처에 시험용 주입 인자 `archiveRootPath`(기본값 = 지금 함수)를 더했고, `mimeType`은 문자열(`Intent.type`)로 받는다. 동작 변화는 없다.
- **F2**: HLD R5가 LLD에 맡긴 "D4로 다시 받은 Intent 가려내기"는 `resolveNewIntentAction` 2번 규칙(`FLAG_ACTIVITY_MULTIPLE_TASK`가 있으면 무시)으로 정했다. 받은 Intent의 플래그가 `onNewIntent`에 그대로 오는지는 V4에서 로그로 확인한다. 바깥 앱이 경로 Intent에 `MULTIPLE_TASK`를 다는 경우는 지금도 새 작업이 생기는 경우(시스템이 기존 작업에 넘기지 않음)라 이 규칙에 걸릴 일이 없다고 본다(미확인).
- **F4**: HLD 3장 1의 보류 필드 `pendingIntentPath: Path?`를 `pendingNewIntent: Pair<NewIntentAction, Path>?`로 바꿨다. 같은 보류 장치로 C3 ③(활성 창)과 D4(새 작업, resumed 때 시작)를 함께 미루려면 무엇을 할지도 함께 저장해야 해서다. 경로만 두는 HLD 동작은 `OPEN_IN_ACTIVE_PANE` 경우와 같다.
- **F3**: HLD C3 ②의 선택 화면 판정 `viewModel.isDualPaneEnabled == false`를 같은 값의 원천인 `isPickAction(args.intent.action)`(`:115`)으로 쓴다. 결과는 같다.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | v1 | 최초 작성(V0 결과 포함, R1·R3·D4 시점 해소) | SRS v1.3, HLD v1.2 | PM |
| 2026-11-20 | v1.1 | 검수 1회차 경미 [기계] L2~L6 반영: V4 플래그 로그 절차, 선택 Intent는 경로 계산 전에 끝냄, T-06 화면 구분·V1 연속 Intent, F4(보류 필드), 3장 시험 번호 정렬. [판단] L1·L7은 PM 판단 대기 | LLD 검수 1회차(PR #85) | 검수팀 |
