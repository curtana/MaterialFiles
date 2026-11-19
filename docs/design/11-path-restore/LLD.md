# LLD — 프로세스 종료 후 창 경로 복원 (이슈 #11)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 |
| 근거 | SRS v1.2, HLD v1.3 (설계 브랜치 `design/11-path-restore` 74408d9, PR #60 — v1.3은 F1 반영, 설계 불변). 오너 결정 Q1=A·Q2=가·Q3=활성 창 복원(이슈 #11 issuecomment-6999035882) |
| 기준 코드 | `origin/develop` 0941fa7 — 이 문서가 인용하는 `filelist/`·`util/`·`provider/` 파일은 HLD 기준 7cf16b7과 같다(`git diff --stat 7cf16b7 origin/develop`에 테스트 파일 하나만 바뀜). 줄 번호는 이 커밋 기준이다. |

## 1. 변경 파일
경로 앞부분 `app/src/main/java/me/zhanghai/android/files/`(테스트는 `app/src/test/java/me/zhanghai/android/files/`)은 생략한다.

| 파일 | 신규/수정 | 요지 | HLD |
|---|---|---|---|
| `filelist/PaneStartPath.kt` | 신규 | 순수 함수 `resolvePaneStartPath`·`decodePanePath`·`encodePanePath` (2-1~2-3) | K3 |
| `filelist/FileListFragment.kt` | 수정 | `onCreate`(신규 재정의)·`onSaveInstanceState`(신규 재정의) 추가, `onActivityCreated` 시작 경로 블록의 기본 디렉터리 분기(`:328-331`)를 K3 호출로 바꿈, 저장 키 상수 (2-4~2-6) | K1 |
| `filelist/FileListHostFragment.kt` | 수정 | `onSaveInstanceState`(신규 재정의) 추가, `onActivityCreated`의 `panes = …`(`:147`) 바로 뒤에 활성 창 되돌리기, 저장 키 상수 (2-7·2-8) | K4 |
| `filelist/FileListViewModel.kt` | 수정(주석만) | `:25` TODO 주석을 실제 상태에 맞게 고침 (2-9) | K2 |
| `filelist/PaneStartPathTest.kt` (test) | 신규 | K3 JVM 단위 테스트 (3-1) | K3 |
| `docs/STANDARDS_TRACEABILITY.md` | 수정 | 편차 해소 표시·2.1 행·4장 #11 행·판 올림 (2-10) | K5 |

코드 5개 파일 + 문서 1개(PROCESS_ROLES 11-1 한도 안). `DualPaneViewModel.kt`·뷰모델 팩토리(`util/FragmentViewModelLazy.kt`)·`provider/`·`FileListActivity.kt`는 바꾸지 않는다(HLD D1·D7·1.2).

## 2. 함수 명세

### 2-1. `me.zhanghai.android.files.filelist.resolvePaneStartPath(restoredPath: T?, intentPath: T?, defaultPath: () -> T): T` (`internal`, `<T : Any>`)
- **추적**: HLD K3 `resolve` / REQ-11-02, 03, 06
- **책임**: 창의 시작 경로를 "복원 값 → Intent 경로 → 기본 디렉터리" 순으로 고른다.
- **선언**: 파일 최상위 `internal` 함수. 제네릭이라 Android·`Path` 타입에 기대지 않아 JVM JUnit으로 시험한다(HLD K3). 파일 머리에 `FileListRefreshPolicy.kt:11`과 같은 취지의 주석("file level functions so that they can be tested without Android")을 둔다.
- **입력·전제**:
  - `restoredPath` — 2-2 `decodePanePath`의 결과. 새로 시작·복원 값 없음·깨진 값이면 `null`.
  - `intentPath` — 기존 Intent 처리(`FileListFragment.kt:275-327`)가 정한 경로(`argsPath`·`ACTION_VIEW_DOWNLOADS`·압축 루트). 없으면 `null`.
  - `defaultPath` — 기본 디렉터리를 돌려주는 람다. 실제 호출부는 `{ Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat }`. 람다로 받는 이유: 현행처럼 앞의 두 값이 모두 없을 때만 설정을 읽는다(`:328-330`).
  - 호출 스레드: 메인(`onActivityCreated`). 입출력 없음.
- **출력·사후조건**: `restoredPath ?: intentPath ?: defaultPath()`. `defaultPath`는 앞의 두 값이 모두 `null`일 때만 정확히 1번 불린다. 상태 없음.
- **오류**: 던지지 않는다(`defaultPath`가 던지는 것은 그대로 전파 — 현행 `valueCompat`와 같음).
- **알고리즘**: `return restoredPath ?: intentPath ?: defaultPath()`
- **기존 코드와의 관계**: 현행 `if (path == null) { path = Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat }`(`FileListFragment.kt:328-330`)를 대체하고 맨 앞에 복원 값을 더한다.

### 2-2. `me.zhanghai.android.files.filelist.decodePanePath(uriString: String?, getPath: (URI) -> Path = { Paths.get(it) }): Path?` (`internal`)
- **추적**: HLD K3 `decode`, 3.3, R2 / REQ-11-06
- **책임**: 저장된 URI 문자열을 경로로 되돌리고, 읽을 수 없으면 로그를 남기고 `null`을 돌려준다.
- **입력·전제**:
  - `uriString` — 2-5가 Bundle에서 읽은 값. 없으면 `null`. 내용은 신뢰하지 않는다(앱 업데이트 뒤 이전 형식일 수 있음, SRS A6).
  - `getPath` — 시험용 주입점. 기본값은 백포트 `java8.nio.file.Paths.get(URI)`. 실제 호출부는 기본값만 쓴다.
  - 호출 스레드: 메인. `Paths.get(URI)`는 provider의 `getPath(uri)`만 부르고, 이는 입출력 없이 끝난다(HLD 3.3·R2 근거).
- **출력·사후조건**: 성공이면 `getPath(URI(uriString))`의 결과. 실패면 `null`. 상태 없음.
- **오류** (잡는 것 / 잡지 않는 것):
  - `uriString == null` → `null`(`getPath`를 부르지 않음, 로그 없음).
  - URI 문법 오류(`URISyntaxException`) → 기존 `URI::class.createOrLog`(`storage/URIExtensions.kt:11-17`, 스택 출력 후 `null`)로 `null`. `getPath`를 부르지 않는다.
  - `getPath`가 던지는 `IllegalArgumentException`(백포트 `Paths.get`의 "Missing scheme", provider의 스킴·경로·쿼리 검사 `LocalLinuxFileSystemProvider.kt:64-69`·`FtpFileSystemProvider.kt:96-102`·`ArchiveFileSystemProvider.kt:74-80`, 그 하위형 `InvalidPathException` 포함), `java8.nio.file.FileSystemNotFoundException`, `java8.nio.file.ProviderNotFoundException` → `e.printStackTrace()` 후 `null`.
  - 그 밖의 예외와 `Error`(예: `AssertionError`)는 잡지 않는다(HLD K3 — 3.3에서 URI로는 닿지 않음을 확인).
- **백포트 동작 확인 결과** (HLD K3 "LLD 시험에서 확인" 항목): `retrofile` 1.2.0 `java8.nio.file.Paths.get(URI)` 바이트코드를 `javap -c`로 확인했다 — ① 스킴이 `null`이면 `IllegalArgumentException("Missing scheme")` ② `file`이면 기본 파일 시스템의 provider로 ③ 아니면 `FileSystemProvider.installedProviders()`에서 스킴이 같은 provider(대소문자 무시)로 ④ 없으면 **`FileSystemNotFoundException`**("Provider "…" not installed"). 백포트 `Paths.get(URI)`는 `ProviderNotFoundException`을 던지지 않는다. 그래도 HLD대로 셋 다 잡는다(provider 구현이 던질 수 있는 경우 대비, 비용 없음). 이 동작은 시험 T-D07로 고정한다.
- **알고리즘**:
  ```kotlin
  if (uriString == null) return null
  val uri = URI::class.createOrLog(uriString) ?: return null
  return try {
      getPath(uri)
  } catch (e: IllegalArgumentException) {
      e.printStackTrace(); null
  } catch (e: FileSystemNotFoundException) {
      e.printStackTrace(); null
  } catch (e: ProviderNotFoundException) {
      e.printStackTrace(); null
  }
  ```
  (로그 방식은 이 앱의 관용구 `printStackTrace()`를 따른다 — `URIExtensions.kt:15`.)
- **기존 코드와의 관계**: `Intent.extraPath` getter(`util/IntentPathExtensions.kt:21-24`)와 같은 "URI 문자열 → `createOrLog` → `Paths.get`" 왕복이다. 다른 점은 `Paths.get`의 예외를 잡는 것뿐이다(`extraPath`는 잡지 않는다 — 그쪽은 이 작업 범위 밖이라 바꾸지 않는다).

### 2-3. `me.zhanghai.android.files.filelist.encodePanePath(path: Path): String` (`internal`)
- **추적**: HLD K3 `encode`, D2 / REQ-11-01
- **책임**: 경로를 저장용 URI 문자열로 바꾼다.
- **입력·전제**: `path` — 창 뷰모델의 현재 경로(`FileListViewModel.currentPath`, `FileListViewModel.kt:39-41`). 창의 현재 경로는 항상 절대 경로다(`TrailData.of`·`navigateTo`로만 정해짐). 메인 스레드.
- **출력·사후조건**: `path.toUri().toString()`. 2-2로 같은 provider가 설치된 상태에서 되돌리면 같은 경로(시험 T-E01).
- **오류**: 잡지 않는다. `toUri()`는 기존 `Intent.extraPath` setter(`util/IntentPathExtensions.kt:33-38`)가 새 창에서 열기(`FileListFragment.kt:954-958` → `FileListActivity.createViewIntent`)·바로가기에서 같은 현재 경로에 이미 그대로 부르는 계약이다 — 새 실패 경로를 만들지 않는다.
- **알고리즘**: `return path.toUri().toString()`
- **기존 코드와의 관계**: `Intent.extraPath` setter의 `value?.toUri()?.toString()`(`IntentPathExtensions.kt:37`)과 같은 표현.

### 2-4. `FileListFragment.onCreate(savedInstanceState: Bundle?)` — 신규 재정의
- **추적**: HLD K1 (a) / REQ-11-01, 06
- **책임**: 저장 상태의 복원 값을 프래그먼트 필드에 보관한다.
- **새 필드**: `private var restoredPathUri: String? = null` — 주석: 복원 때 받은 경로, 뷰를 만들기 전에 다시 저장되는 경우에도 잃지 않게 보관한다는 취지.
- **입력·전제**: 시스템 종료 후 복원이면 `savedInstanceState`에 2-6이 넣은 키가 있을 수 있다. 새로 시작이면 `null`. 메인 스레드.
- **출력·사후조건**: `restoredPathUri = savedInstanceState?.getString(STATE_PATH_URI)`. 그 밖의 상태는 바꾸지 않는다.
- **오류**: 없음(`Bundle.getString`은 키가 없거나 형이 다르면 `null` — 형이 다르면 경고 로그만 남긴다).
- **알고리즘**: `super.onCreate(savedInstanceState)` → 위 대입.
- **자리**: `onCreateView`(`FileListFragment.kt:190`) 바로 앞. 지금 이 클래스에는 `onCreate` 재정의가 없다(`grep -n "override fun on" FileListFragment.kt` 결과 `onCreateView`·`onActivityCreated`부터).
- **기존 코드와의 관계**: HLD K1 (a)가 `onCreate`에서 읽도록 정한 이유 — `onActivityCreated`보다 먼저 불리고, 뷰를 만들지 않은 채 다시 저장되는 경우에도 2-6이 다시 넣을 값이 있어야 한다.

### 2-5. `FileListFragment.onActivityCreated(savedInstanceState: Bundle?)` — 시작 경로 블록만 수정
- **추적**: HLD K1 (b), D3 / REQ-11-01, 02, 03, 04, 05, 07
- **책임**: 창 뷰모델에 경로가 없을 때(`!viewModel.hasTrail`, `:274`) 기존 Intent 처리를 모두 한 뒤 시작 경로만 K3로 고른다.
- **입력·전제**: 2-4가 `restoredPathUri`를 채웠다. 호스트가 이미 활성 창을 되돌렸다(2-8) — AndroidX `Fragment.performActivityCreated`는 자기 `onActivityCreated` 뒤에 자식 FragmentManager의 `dispatchActivityCreated`를 부르므로 호스트 `onActivityCreated`가 창보다 먼저 끝난다(이 순서는 이미 `FileListFragment.kt:145` 주석 "Created by the host before its panes"가 기대는 순서다).
- **출력·사후조건**:
  - `hasTrail == true`(구성 변경): 지금처럼 블록 전체를 건너뛴다 — 경로·이동 기록·스크롤·검색·선택 유지(REQ-11-07).
  - `hasTrail == false`: `viewModel.resetTo(start)`(→ `TrailData.of(start)`, REQ-11-04), `pickOptions`는 지금처럼 Intent에서(REQ-11-02). `start`는 복원 값이 읽히면 그 경로(REQ-11-01·02), 아니면 지금과 같은 Intent 경로 또는 기본 디렉터리(REQ-11-03·06).
  - 복원 경로가 접근 불가여도 여기서는 확인하지 않는다 — 이후 기존 목록 읽기가 실패를 표시한다(`:755-782`, Q2 가, REQ-11-05, HLD D6).
- **오류**: 2-2가 예외를 `null`로 바꾸므로 이 블록에서 새로 던지는 것이 없다.
- **알고리즘** (바뀌는 줄만 — `:275-327`의 `var path = argsPath`·`when` 블록은 글자 그대로 둔다):
  ```kotlin
  // 현행 :328-331
  //   if (path == null) { path = Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat }
  //   viewModel.resetTo(path)
  // 을 다음으로 바꾼다.
  // The saved path takes precedence over the intent after the system recreated this pane, but the
  // pick options above still come from the intent (#11).
  val startPath = resolvePaneStartPath(decodePanePath(restoredPathUri), path) {
      Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
  }
  viewModel.resetTo(startPath)
  ```
  `:332-334`의 `pickOptions` 대입은 그대로.
- **기존 코드와의 관계**: `ACTION_VIEW_DOWNLOADS`·압축 루트 변환(`:320-327`)은 Intent 경로에만 적용된다 — 복원 값은 저장 시점의 현재 경로(압축 안 경로면 이미 압축 루트 아래 경로)라 다시 변환하지 않는다. 반대쪽 창 경로 표시는 `:336` 관찰 → `onCurrentPathChanged`(`:622-626`) → `DualPaneViewModel.setPanePath`로 지금처럼 퍼진다(HLD 3.1, 추가 작업 없음).

### 2-6. `FileListFragment.onSaveInstanceState(outState: Bundle)` — 신규 재정의
- **추적**: HLD K1 (a), 3.2, D1·D2 / REQ-11-01, 03
- **책임**: 창의 현재 경로를 URI 문자열로 저장 상태에 넣는다.
- **입력·전제**: 프래그먼트가 붙어 있다(시스템이 화면을 멈출 때·구성 변경 직전). 메인 스레드. `viewModel`(`:143`)은 붙어 있는 동안 언제든 읽을 수 있다.
- **출력·사후조건**:
  - `viewModel.hasTrail == true` → `outState.putString(STATE_PATH_URI, encodePanePath(viewModel.currentPath))`.
  - `false`(복원된 프래그먼트가 뷰를 만들기 전에 다시 저장되는 경우) → `restoredPathUri`가 있으면 그대로 다시 넣는다. 없으면 넣지 않는다.
  - 영구 저장 없음 — Bundle에만 쓴다(REQ-11-03). 넣는 값은 문자열 하나(SRS 3장 저장 크기). 비밀번호·키는 URI에 없다(HLD 3.2).
- **오류**: 2-3과 같음(잡지 않음).
- **알고리즘**:
  ```kotlin
  super.onSaveInstanceState(outState)
  // Only the current path is saved, and the view model keeps the rest across configuration
  // changes (#11).
  val pathUri = if (viewModel.hasTrail) encodePanePath(viewModel.currentPath) else restoredPathUri
  pathUri?.let { outState.putString(STATE_PATH_URI, it) }
  ```
- **자리**: 2-4 바로 뒤(생명주기 순서대로). 키 상수는 `companion object`(`:1651-1656`)에 추가:
  `private const val STATE_PATH_URI = "${BuildConfig.APPLICATION_ID}.state.PATH_URI"` — `util/IntentPathExtensions.kt:19` `EXTRA_PATH_URI`와 같은 명명. `BuildConfig` import 추가.
- **기존 코드와의 관계**: 이 앱의 다른 화면은 `outState.putState(State(...))`(`util/ParcelableState.kt:14-18`, 예 `navigation/EditBookmarkDirectoryDialogFragment.kt:73`)를 쓰지만 여기서는 쓰지 않는다 — `getState`는 `getParcelableSafe(...)!!`라 값이 없거나 클래스가 바뀌면 던지고, 복원 때 클래스 해석이 필요하다. 문자열 키는 HLD D2(이전 형식이어도 `null`로 깔끔히 떨어짐, SRS A6)에 그대로 맞는다.

### 2-7. `FileListHostFragment.onSaveInstanceState(outState: Bundle)` — 신규 재정의
- **추적**: HLD K4, D7 / REQ-11-09
- **책임**: 활성 창 번호를 저장 상태에 넣는다.
- **입력·전제**: 호스트가 붙어 있다. `viewModel`(`DualPaneViewModel`, `FileListHostFragment.kt:87`). 메인 스레드.
- **출력·사후조건**: `outState.putInt(STATE_ACTIVE_PANE, viewModel.activePane)`. 정수 하나.
- **오류**: 없음.
- **알고리즘**: `super.onSaveInstanceState(outState)` → 위 대입.
- **자리**: `onActivityCreated` 뒤(이 클래스의 다른 생명주기 재정의 `onResume` `:236` 앞). 키 상수는 이 클래스에 `companion object`가 없으므로 새로 만든다(`Binding.companion`은 중첩 클래스 것, `:550`): `private const val STATE_ACTIVE_PANE = "${BuildConfig.APPLICATION_ID}.state.ACTIVE_PANE"`. 자리는 클래스 끝 중첩 클래스들 앞(`FileListFragment.kt:1651` 배치와 같게). `BuildConfig` import 추가.

### 2-8. `FileListHostFragment.onActivityCreated(savedInstanceState: Bundle?)` — 활성 창 되돌리기 추가
- **추적**: HLD K4, 3.1 활성 창 행, D7 / REQ-11-09
- **책임**: 저장 상태가 있으면 저장 시점의 활성 창으로 되돌린다.
- **입력·전제**: `viewModel.isDualPaneEnabled`가 이미 정해졌다(`:114-115`). 창 2가 필요한데 없으면 이미 만들어졌다(`:137-142`).
- **출력·사후조건**:
  - 시스템 종료 후 복원(새 뷰모델, 처음 값 0): 저장 값이 1이고 두 창 사용이면 `activePane == 1`. 이어서 `:159` `dualPaneLayout.activePane = viewModel.activePane`이 그 창을 보이게(한 창 모드)·활성 표시(두 창 모드)로 만들고, 관찰자(`:161`·`:174`·`:189`)·서랍(`observeCurrentPath` `:348-352`)이 그 창을 따른다(REQ-11-09 (a)(b)).
  - 선택 모드(`isDualPaneEnabled == false`): `setActivePane(1)`은 무시돼 창 1(`DualPaneViewModel.kt:24-26`, REQ-11-09 (d)).
  - 구성 변경: 저장 값 = 현재 값이라 `setActivePane`이 아무것도 하지 않는다(`DualPaneViewModel.kt:27-29`, HLD D7).
  - 새로 시작(`savedInstanceState == null`): 아무것도 하지 않는다 → 창 1(#2 SRS A8 ④).
  - 키가 없는 저장 상태(이 기능 이전 판이 저장한 것): 기본값을 현재 값으로 두어 아무것도 하지 않는다.
- **오류**: 없음(범위 밖 값은 `setActivePane`이 무시, `DualPaneViewModel.kt:23-26`).
- **알고리즘** (`:147` `panes = listOfNotNull(pane1, pane2)` 바로 뒤, `:148` `navigationFragment.listener = this` 앞에 넣는다):
  ```kotlin
  if (savedInstanceState != null) {
      // The view model is new after the system killed the process, and everything below reads the
      // active pane (#11).
      viewModel.setActivePane(
          savedInstanceState.getInt(STATE_ACTIVE_PANE, viewModel.activePane)
      )
  }
  ```
- **자리를 고른 이유** (HLD K4가 LLD에 맡긴 순서): `:147` 뒤면 `panes`가 정해져 `activePane` getter(`:99-100`)가 유효하고, `:148` 앞이면 서랍 리스너 연결·`:159`·관찰자 연결보다 먼저라 처음 그리기부터 복원된 창이 쓰인다. 탐색 서랍 프래그먼트와 두 창의 `onActivityCreated`는 호스트 `onActivityCreated`가 끝난 뒤에 불린다(2-5 입력·전제와 같은 근거).

### 2-9. `FileListViewModel` `:25` 주석
- **추적**: HLD K2 ("`:25` TODO는 LLD에서 정리 여부를 정한다")
- **결정**: TODO를 지우지 않고 현재 사실로 고친다 — 경로는 이제 저장되지만 이동 기록·검색 상태 등은 여전히 저장되지 않으므로 할 일이 남아 있다.
  `// TODO: Use SavedStateHandle to save state.` → `// TODO: Use SavedStateHandle to save the rest of the state. Only the current path is saved now, by FileListFragment (#11).`
- 코드 변경 없음.

### 2-10. `docs/STANDARDS_TRACEABILITY.md` 갱신 (K5, REQ-11-08) — 구현 PR에 포함
구현 PR의 같은 커밋 묶음에 넣는다(빠지면 검수 반려, 부록 S). `<PR>`은 구현 PR 번호, `<날짜>`는 커밋 날짜.

1. **머리 상태 줄(`:3`)**: `v0.11 (2026-11-04. …` → `v0.12 (<날짜>. …, v0.12 #11 `State_Preservation` 편차 해소 — 5장 이력)`.
2. **2.1 `State_Preservation` 행(`:46`)**:
   - "이 앱에 해당?" 열: `예(이슈 #2)` → `예(이슈 #2, #11)`.
   - "충족 방법·확인 방법" 열의 마지막 문장("앱 프로세스가 종료된 뒤 기본 폴더로 시작하는 기존 동작(SRS A6)은 … 해소 이슈 #11).")을 다음으로 바꾼다: `시스템이 앱 프로세스를 종료한 뒤 복원할 때는 각 창의 현재 경로와 활성 창을 저장 상태로 되살린다(#11, SRS REQ-11-01·09). 이동 기록의 아래 단계·단계별 스크롤 위치는 복원하지 않는다(오너 결정 Q1 A — 조항 허용 범위, issuecomment-6999035882). 확인: 홈 → adb shell am kill → 최근 앱에서 복귀(#11 LLD V2).` 앞 문장(두 창의 경로·기록… 확인: 홈으로 나갔다가…)은 그대로.
3. **3장 편차 등록부 `State_Preservation` 행(`:119`)**: 편차·사유·승인 열은 기록이므로 그대로 두고, "해소 이슈" 열 `#11` → `#11 — 해소(PR #<PR>, <날짜>). 남는 범위: 이동 기록·단계별 스크롤 위치·경로 외 창 상태는 복원하지 않음(오너 결정 Q1 A, 조항 허용 범위)`.
4. **4장 기능별 추적**: `#36` 행(`:136`) 뒤에 한 행 추가:
   `| #11 프로세스 종료 후 창 경로 복원 | `State_Preservation`, `Back_Button_Nav`, `Stability_ANR`, `Orientation_Transitions`, `Config_Changes` | REQ-11-01~09 (`docs/design/11-path-restore/SRS.md` v1.2 5장) | 3장 `State_Preservation` 편차 해소. 새 권한·설정 키·영구 저장 없음. 새 편차 없음 |`
5. **5장 변경 이력**: `| <날짜> | v0.12 | 3장 `State_Preservation` 편차 해소 표시(#11, PR #<PR>), 2.1 행의 해당 열·충족 방법 갱신, 4장 #11 행 추가 | 개발팀 |`
- 범위 밖(손대지 않음): 이슈 #2 SRS A6·5장, #2 HLD 3.1의 "프로세스 종료 시 사라짐"·"처음 값(창 1 활성)" 서술 — SRS REQ-11-08대로 릴리스 문서 동기화 때 설계팀이 고친다.

## 3. 테스트 설계

### 3-1. JUnit (`filelist/PaneStartPathTest.kt`, JUnit 4, 새 의존성 없음)
- 실행: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "me.zhanghai.android.files.filelist.PaneStartPathTest" --console=plain`, 이어서 전체 `testDebugUnitTest`와 `assembleDebug`.
- 경로 값은 시험용 `TestPaths.path(...)`(`filejob/TestPaths.kt`, 같은 모듈 `internal`)를 쓴다. `resolve` 시험은 제네릭이라 문자열로 해도 된다.
- JVM에서는 provider가 설치되지 않는다(백포트 `installedProviders`는 정적 목록, ServiceLoader 없음). 그래서 실제 `Paths.get`은 스킴 없음·설치 안 된 스킴 경우에만 쓰고(`file` 스킴은 기본 provider가 없어 시험하지 않는다), 나머지는 `getPath` 주입으로 시험한다.

| 테스트 ID | 대상 함수 | 경우 | 기대 결과 |
|---|---|---|---|
| T-R01 | `resolvePaneStartPath` | 복원 값만 있음 | 복원 값, `defaultPath` 호출 0회 (REQ-11-01) |
| T-R02 | 〃 | 복원 값 + Intent 경로 | 복원 값 (REQ-11-02) |
| T-R03 | 〃 | Intent 경로만 | Intent 경로, `defaultPath` 0회 (REQ-11-03) |
| T-R04 | 〃 | 둘 다 없음 | `defaultPath()` 결과, 호출 정확히 1회 (REQ-11-03·06) |
| T-D01 | `decodePanePath` | `null` | `null`, `getPath` 호출 0회 |
| T-D02 | 〃 | 정상 문자열 + 가짜 `getPath`(받은 URI 기록 후 경로 반환) | 가짜 경로, 받은 URI == `URI(입력)` |
| T-D03 | 〃 | 문법 오류 문자열(`"%zz"`, `"a b"`) | `null`, `getPath` 0회 |
| T-D04 | 〃 | 빈 문자열 `""`, 실제 `Paths.get` | `null` (스킴 없음 → `IllegalArgumentException`) |
| T-D05 | 〃 | 상대 경로 `"Download/a"`, 실제 `Paths.get` | `null` (스킴 없음) |
| T-D06 | 〃 | 설치 안 된 스킴 `"mf-test-unknown:///a"`, 실제 `Paths.get` | `null` |
| T-D07 | 백포트 `Paths.get(URI)` (동작 고정) | T-D06과 같은 URI | `assertThrows(FileSystemNotFoundException::class.java)` — HLD K3 "무엇을 던지는지 확인" 기록 |
| T-D08 | `decodePanePath` | 가짜 `getPath`가 `IllegalArgumentException`·`InvalidPathException`·`FileSystemNotFoundException`·`ProviderNotFoundException`을 각각 던짐 | 각각 `null` |
| T-D09 | 〃 | 가짜 `getPath`가 `AssertionError`를 던짐 | 그대로 전파(`assertThrows`) — `Error`는 잡지 않음 |
| T-D10 | 〃 | 가짜 `getPath`가 `IllegalStateException`을 던짐 | 그대로 전파 — 잡는 예외는 HLD K3의 셋뿐임을 고정 |
| T-E01 | `encodePanePath` + `decodePanePath` | `TestPaths.path("/storage/emulated/0/Download/a b/한글")`를 인코딩 → `getPath = { TestPaths.path(it.path) }`로 디코딩 | 결과 == 원래 경로, 인코딩 문자열 == `path.toUri().toString()`, 스킴 `test:` |

K1(2-4~2-6)·K4(2-7·2-8)는 프래그먼트·`LiveData`(메인 루퍼 필요, `arch core-testing`은 의존성에 없음) 코드라 JVM 시험 대상이 아니다 — 3-2 수동 절차와 코드 검사로 확인한다.

### 3-2. 수동 확인 (에뮬레이터만 — SRS 4.2 V1~V4 구체화)
**기기 규칙**: 로컬 AVD만 쓴다. 실기기(오너 태블릿·폰)에는 설치하지 않는다. 모든 `adb` 명령에 `-s <에뮬레이터 serial>`(예: `emulator-5554`)을 붙이고, `./gradlew installDebug`(연결된 모든 기기에 설치)는 쓰지 않는다.

**준비**
1. `adb devices`로 연결 목록 확인. 실기기가 있으면 `-s`로만 진행한다. 다른 작업(#27·#54·#55)이 같은 AVD를 쓰고 있으면 데이터를 지우지 말고 PM에게 순서를 묻는다.
2. AVD: `Medium_Phone_API_36`(`emulator -avd Medium_Phone_API_36`, `-wipe-data`·새 AVD·시스템 이미지 내려받기 금지). 로컬에는 API 36 AVD 둘뿐이라 SRS 4.2의 "API 23 하나" 권장은 **미실행**으로 기록한다(4장 F2).
3. 빌드·설치: `assembleDebug` → `adb -s <serial> install -r <app/build/outputs/apk/debug/의 APK>`. 모든 파일 접근: `adb -s <serial> shell appops set --uid me.zhanghai.android.files MANAGE_EXTERNAL_STORAGE allow`.
4. 시험 자료: `adb -s <serial> shell mkdir -p /sdcard/DCIM/X /sdcard/Download/many`, `many`에 파일 200개(`adb shell 'for i in $(seq 1 200); do touch /sdcard/Download/many/f$i; done'`), 시험용 zip(호스트에서 venv 파이썬 `zipfile`로 `G:/tmp/MeterialFiles/11-path-restore/t.zip` 생성, 안에 폴더 `d/`) → `adb push … /sdcard/Download/t.zip`.
5. 증거: 각 단계 화면을 `adb -s <serial> exec-out screencap -p > $MAIN_REPO/_workspace/wo/11-path-restore/<케이스>.png`로 남기고, 케이스마다 `adb -s <serial> logcat -b crash -d`가 비었는지 확인한다.

**공통 절차**
- **V1** (빠른 반복용): `adb shell settings put global always_finish_activities 1` → 앱에서 창 준비 → 홈(`adb shell input keyevent KEYCODE_HOME`) → 최근 앱(`adb shell input keyevent KEYCODE_APP_SWITCH`)에서 앱 카드 누르기 → 확인. **끝나면 반드시** `... always_finish_activities 0`.
- **V2** (정본, 통과 판정): 앱에서 창 준비 → 홈 → `adb shell am kill me.zhanghai.android.files` → `adb shell pidof me.zhanghai.android.files` 출력이 비었는지 확인(남아 있으면 다시 홈 확인 후 재시도, 비지 않으면 그 회차 무효) → **최근 앱에서 앱 카드로 복귀** → 확인. `am force-stop`은 쓰지 않는다(사용자 강제 종료라 시스템 종료 재현이 아님, SRS 4.2).
- **V3** (새로 시작): 창 준비 → (a) 최근 앱에서 카드를 위로 밀어 지움 → 실행기 아이콘으로 열기, (b) 뒤로 가기로 화면 끝냄 → 실행기 아이콘으로 열기.
- **V4** (구성 변경): `adb shell settings put system accelerometer_rotation 0` → `user_rotation 1`(가로) → `user_rotation 0`. 끝나면 원래 값으로.

**케이스** (두 창 표시는 이 AVD의 가로 화면, 너비 600dp 이상 — #2 WSC-3. 한 창 모드는 세로.)

| ID | REQ | 준비 → 절차 | 통과 기준 |
|---|---|---|---|
| M-01 | 01 | 가로(두 창): 창 1 `/storage/emulated/0/Download`, 창 2 `/storage/emulated/0/DCIM` → V1, V2 각각 | 두 창이 각각 그 폴더. 각 창의 반대쪽 경로 표시(#2 REQ-2-17)도 맞는 경로. 세로로 돌려 숨은 창도 그 폴더 |
| M-02 | 01 | 세로(한 창): 창 1 Download, 창 2(전환 후) DCIM, 창 1로 돌아옴 → V2 | 창 1 Download, 창 2로 전환하면 DCIM(숨은 창 복원) |
| M-03 | 02 | `adb shell am start -a android.intent.action.VIEW -t inode/directory -d file:///storage/emulated/0/Music -n me.zhanghai.android.files/.filelist.FileListActivity` → 창 1을 Download로 → V2 | 창 1 Download(Intent의 Music이 아님) |
| M-04 | 02 | `adb shell am start -a android.intent.action.GET_CONTENT -t '*/*' -n me.zhanghai.android.files/.filelist.FileListActivity` → Download로 → V1 | Download에서 열리고 선택 모드 UI(한 창, 선택 툴바)가 그대로. 파일을 누르면 화면이 결과와 함께 끝난다(`adb shell dumpsys activity activities`에서 사라짐). 호출한 앱이 결과를 받는지는 adb에 호출 앱이 없어 **미확인**으로 적는다 — 에뮬레이터에 첨부 기능이 있는 앱이 있으면 그 앱으로 대신 확인 |
| M-05 | 03 | 두 창을 옮긴 뒤 V3 (a)·(b) | 두 창 모두 기본 디렉터리, 창 1 활성 |
| M-06 | 04 | 창 1 `/storage/emulated/0/DCIM/X` → V2 → 뒤로 가기 반복 | `DCIM` → `0` → … 한 단계씩 위로 |
| M-07 | 04, 05 | 창 1에서 `t.zip` 안 `d/`로 → V2 → 뒤로 가기 | 압축 안 `d/`에서 열림 → 뒤로 가기로 압축 루트 → `Download` 쪽으로 |
| M-08 | 05 | 가로: 창 2 `/sdcard/DCIM/X`, 창 1 Download → V1 준비(홈까지) → `adb shell rm -r /sdcard/DCIM/X` → 복귀 | 창 2가 `X` 경로에서 기존 오류 문구·툴바 부제 "오류", 창 1 정상 목록, 창 2에서 뒤로 가기로 `DCIM` |
| M-09 | 05 | 창 1 `t.zip` 안 → V2 준비(홈·kill) → `adb shell rm /sdcard/Download/t.zip` → 복귀 | 그 경로에서 기존 오류 표시, 강제 종료 없음 |
| M-10 | 05 | SFTP/SMB 저장소 폴더 → V2 준비 → `adb shell svc wifi disable; svc data disable` → 복귀 → 켜고 새로 고침 | 오류 표시 → 새로 고침 뒤 목록. **접속할 시험 서버가 없으면 미실행**으로 적는다(서버 구축은 범위 밖) |
| M-11 | 06 | M-01 준비로 V1 5회, V2 5회 연속 | 매회 `logcat -b crash` 비어 있음, ANR 대화상자 없음 |
| M-12 | 07 | 창 1 `many`에서 아래로 스크롤·경로 표시줄 아래 단계가 남게 위로 한 번·파일 하나 선택 → V4 | 경로·경로 표시줄 아래 단계·스크롤·선택 그대로(#2 REQ-2-04) |
| M-13 | 09 (a) | 세로: 창 2로 전환, 창 2를 DCIM으로 → V2 | 복귀 화면에 창 2·DCIM |
| M-14 | 09 (b) | 가로: 창 2 활성 → V2 | 창 2에 활성 표시 |
| M-15 | 09 (c) | M-13 준비 → V3 (a) | 창 1 활성, 두 창 기본 디렉터리 |
| M-16 | 09 (d) | M-04처럼 선택 모드로 연 뒤 → V2 | 창 1(선택 모드는 한 창) |
| M-17 | (R1 확인, 판정 아님) | 창 1 `many`에서 아래로 스크롤 → V2 | 스크롤 위치가 되살아나는지 **기록만** 한다(REQ 아님, HLD R1). 결과는 PR 코멘트에 적고 설계팀에 알린다(SRS Q1 A행 반영은 설계팀) |

**코드 검사** (검수 때): diff에 `SharedPreferences`·`Settings.`의 새 키·파일 쓰기가 없음(REQ-11-03), 2-2·2-5의 복원 경로에 파일 시스템 접근 호출(`exists`·`readAttributes`·`newDirectoryStream` 등)이 없음(REQ-11-06), 추적표 변경이 diff에 있음(REQ-11-08).

**정리**: `always_finish_activities 0`, 회전 설정 원복, `svc wifi enable; svc data enable`, 시험 자료 삭제(`/sdcard/DCIM/X`·`many`·`t.zip`), 에뮬레이터 종료, `check_leftovers.sh`.

### 3-3. REQ 추적
| REQ | 함수 | 시험 |
|---|---|---|
| 01 | 2-3·2-4·2-5·2-6 | T-R01, T-E01, M-01, M-02 |
| 02 | 2-1·2-5 | T-R02, M-03, M-04 |
| 03 | 2-1·2-5·2-6 | T-R03, T-R04, M-05, 코드 검사 |
| 04 | 2-5(`resetTo` → `TrailData.of`) | M-06, M-07 |
| 05 | 2-5(접근 확인 안 함) | M-08, M-09, M-10 |
| 06 | 2-1·2-2 | T-R04, T-D01~D10, M-11, 코드 검사 |
| 07 | 2-5(`hasTrail` 분기 불변) | M-12 |
| 08 | 2-10 | 검수(diff) |
| 09 | 2-7·2-8 | M-13~M-16 |

## 4. 설계 피드백
- **F1 (정보, 설계 불변 — HLD v1.3에서 표현 정정)**: HLD K3·3.3은 설치되지 않은 스킴에서 `ProviderNotFoundException`을 들고(3.3 "그 스킴은 `ProviderNotFoundException` → `decode` `null`"), K3는 "둘 중 무엇을 던지는지는 LLD 시험에서 확인"이라 했다. 백포트 바이트코드 확인 결과 `Paths.get(URI)`는 **`FileSystemNotFoundException`**을 던진다(2-2). LLD는 HLD대로 둘 다 잡으므로 동작·설계는 그대로이고, T-D07로 고정한다. 설계팀에 전달해 HLD v1.3에서 정정됐다(아래).
- **F2 (환경, SRS 수정 불요)**: SRS 4.2는 에뮬레이터 "API 23 하나, API 34 이상 하나 권장"이지만 로컬 AVD는 `Medium_Phone_API_36`·`_b`뿐이다. 시스템 이미지 내려받기·새 AVD 만들기는 하지 않으므로 API 23 확인은 미실행으로 기록한다. 이 작업에 API별 분기 코드가 없어(SRS 3장) 위험은 낮다. 필요하면 PM 판단.
- **F3 (정보)**: M-04의 "고른 결과가 호출 앱에 돌아가는지"는 adb만으로는 호출 앱이 없어 확인할 수 없다. 선택 조건이 복원 때에도 Intent에서 정해진다는 것(REQ-11-02 핵심)은 선택 모드 UI 유지와 코드 검사(2-5: `when` 블록 불변)로 확인한다.
- 설계팀 답변 (2026-11-19): F1 — HLD를 바로 고쳤다(v1.3, 74408d9: K3·3.3의 예외 이름을 `FileSystemNotFoundException`으로, `ProviderNotFoundException`은 방어적으로 함께 잡음. 설계·동작 불변, 바뀐 절 재검수는 이 LLD 검수와 함께). F2 — 이견 없음, 검수 기록·구현 PR 본문에도 "API 23 미실행"을 남긴다.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1.0 | 처음 작성 | SRS v1.2·HLD v1.2 검수 통과 | PM |
