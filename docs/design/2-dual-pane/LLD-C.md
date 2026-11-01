# LLD-C — 창 표시와 접근성 (이슈 #7)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중(v1.1) |
| 근거 | SRS v4.8, HLD v4.9 (`docs/design/2-dual-pane/`), LLD-A v1.4, LLD-B v1.3, LLD-D v1.3.1, `docs/STANDARDS_TRACEABILITY.md`(develop) |
| 기준 코드 | `origin/develop` 0cca1b4 (#6 머지 PR #22 — #5·#6·#8 구현 포함). 인용 줄 번호는 이 커밋 기준 |
| 이슈 | #7 (하위 이슈 C, 상위 #2). 선행 #6(머지됨). K3 `panePaths`를 소유하고 #9(E)가 쓴다 |
| HLD 구성요소 | K7(창 표시), K9(창 표시 문자열), K3 `panePaths`(소유), K5 자기 현재 경로 게시·창 표시 그리기, K2 창 표시 누르기 처리 |
| 추적 REQ | REQ-2-08, 17, 20 (+ REQ-2-07의 두 창 모드 창 표시 활성화 경로, HLD 3.9) |

## 0. 범위와 전제

### 0.1 이 단계가 하는 것 / 하지 않는 것
- **한다**
  - K3 `DualPaneViewModel`: 창별 현재 경로 게시판 `panePaths`(2.1). #9가 쓸 API까지 정한다(4장).
  - K5 `FileListFragment`: 자기 현재 경로를 K3에 게시, 창 표시 줄(창 표시 컨트롤 + 반대쪽 경로)과 활성 창 선을 그림, 컨트롤 누르기 → 호스트, 전환 뒤 초점 받기(2.3).
  - K2 `FileListHostFragment`: 창 표시 누르기 처리 — 한 창 모드 `togglePane()` + 새로 보이는 창 컨트롤로 초점 이동, 두 창 모드 비활성 창 활성화(2.4).
  - 창 표시 모드를 정하는 순수 함수와 단위 시험(2.2, 5.1).
  - 앱바 레이아웃에 창 표시 줄과 활성 창 선(2.5), K9 문자열(영어·한국어, 2.6).
- **하지 않는다**: 반대쪽 복사·이동 명령(#9 — 이 단계는 `panePaths` API만 준비), 확인 창(#8, 머지됨), K4의 쓸기·탭 인식 변경(#6 그대로 — 창 표시 줄은 이미 앱바 안이라 쓸기 제외 (b)에 들어 있다, `DualPaneLayout.kt:413-417`), 비활성 창 speed dial·붙여넣기 막대 숨김(HLD R6 — 7장).
- 이 단계가 끝나면 #6이 남긴 "두 창 모드에서 어느 창이 활성인지 보이는 표시가 아직 없다"(LLD-B 0.1)가 닫힌다.

### 0.2 추적 — 이 단계의 몫
| REQ | 이 단계의 몫 | 확인 |
|---|---|---|
| REQ-2-08 | 두 창 모드 활성 창 = 앱바 아래 굵은 선(형태) + "활성" 글자(글자), 색은 보조(2.3 `updatePaneIndicator`, 2.5). 대비는 3장 | U-C03·U-C04, T-C04, T-C09 |
| REQ-2-17 | 한 창 모드: 보이는 창 번호 `N / 2` + "창 M: <반대쪽 경로>"(K3 게시판 관찰). 두 창 모드: 활성 표시와 두 창의 경로 표시줄(기존 breadcrumb) | U-C02, T-C01, T-C04, T-C14 |
| REQ-2-20 | 한 창 모드 창 표시 컨트롤 누르기 → K3 `togglePane()`, 라벨·동작 설명, 전환 뒤 접근성 초점·입력 초점 이동, 48dp, 키보드·마우스(2.3·2.4, 3장) | T-C02, T-C03, T-C06~T-C08 |
| REQ-2-07(두 창 모드 창 표시) | 비활성 창 컨트롤 누르기·Enter·TalkBack 두 번 탭 → 그 창 활성(HLD 3.9, R10) | T-C05 |

### 0.3 사용자에게 보이는 변화
- **의도된 변화**(요구사항): 앱바의 경로 표시줄 아래에 창 표시 줄이 생긴다(선택 모드 제외). 한 창 모드에서는 `1 / 2` 단추와 "창 2: <경로>", 두 창 모드에서는 "창 1 · 활성"/"창 2"와 활성 창 앱바 아래 굵은 선.
- **그 결과로 생기는 변화**: 앱바가 창 표시 줄 높이(48dp, 터치 대상 크기)만큼, 두 창 모드에서는 선 두께(3dp)만큼 더 높아져 목록 영역이 그만큼 줄어든다. 창 표시 줄은 경로 표시줄처럼 목록과 함께 접히지 않는다(2.5 — 앱바 스크롤 플래그가 없는 자식 뒤에 놓임).
- **바뀌지 않는 것**: 툴바 제목·부제목·메뉴, 경로 표시줄, 쓸기·탭 동작(창 표시 줄은 앱바 안이라 쓸기 제외 영역 — 지금도 앱바 위 쓸기는 무반응), 선택 모드(다른 앱이 파일을 고르려고 연 화면)의 화면 전체.

### 0.4 #6에서 넘겨받은 것
| 항목 | 이 LLD의 처리 | 절 |
|---|---|---|
| `Args.paneIndex`(LLD-B 2.5 — "읽는 곳은 #7") | 창 번호·반대쪽 창 번호·경로 게시의 키로 읽는다 | 2.3 |
| K3 `togglePane()`(LLD-B 2.2 — "#7의 창 표시 누르기가 함께 쓴다") | 호스트의 창 표시 누르기 처리가 부른다 | 2.4 |
| `panePaths`(LLD-B FB-4 — #7 소유, PM 결정 PR #17) | K3에 만들고 #9가 쓸 API를 정한다 | 2.1, 4장 |
| R10 남은 위험(LLD-B 7장 — "창 표시 컨트롤 경로는 #7") | 두 창 모드 비활성 창 컨트롤의 클릭 동작 | 2.3, 2.4, T-C05 |
| 창 공개 `LiveData`는 붙기 전에 읽힐 수 있으면 미러로(LLD-B v1.3 2.5) | 이 단계의 새 관찰은 모두 호스트 범위 K3의 `LiveData`이고 창 뷰모델을 호스트가 읽지 않으므로 미러가 필요 없다 | 2.1, 2.3 |

## 1. 변경 파일 (R4 범위 공지 — PROCESS_ROLES 11-1, 5개 초과)

| # | 파일 | 신규/수정 | 요지 | 구성요소 |
|---|---|---|---|---|
| 1 | `app/src/main/java/me/zhanghai/android/files/filelist/DualPaneViewModel.kt` | 수정 | `PANE_COUNT`, `panePaths`(`getPanePathLiveData`·`getPanePath`·`setPanePath`), 파일 수준 `otherPaneIndex` | K3 |
| 2 | `app/src/main/java/me/zhanghai/android/files/filelist/PaneIndicator.kt` | 신규 | `PaneIndicatorMode`, 순수 함수 `getPaneIndicatorMode` | K7 |
| 3 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt` | 수정 | 경로 게시, 창 표시 줄 그리기·누르기·초점, `Listener.onPaneIndicatorClicked`, `Binding` 필드 | K5·K7 |
| 4 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListHostFragment.kt` | 수정 | `onPaneIndicatorClicked` 구현 | K2 |
| 5 | `app/src/main/res/layout/file_list_fragment_app_bar_include.xml` | 수정 | 경로 표시줄 아래 `paneIndicatorLayout`(컨트롤 + 반대쪽 경로)과 `activePaneLine` | K7 |
| 6 | `app/src/main/res/values/strings.xml` | 수정 | K9 창 표시 문자열 8개 | K9 |
| 7 | `app/src/main/res/values-ko/strings.xml` | 수정 | 위의 한국어(번역 대상 7개) | K9 |
| 8 | `app/src/test/java/me/zhanghai/android/files/filelist/PaneIndicatorTest.kt` | 신규 | U-C01~U-C05(5.1) | 시험 |

- 합계 8개(신규 2, 수정 6): 앱 Kotlin 4, 레이아웃 XML 1, 문자열 XML 2, 시험 Kotlin 1. `provider/`·`filejob/`·JNI·`app/build.gradle`은 건드리지 않는다(SRS C-1, 시험 의존성은 #6·#8에서 이미 있음 `app/build.gradle:216`). 새 권한·설정 키·라이브러리 없음.
- 창 레이아웃은 두 변형(`res/layout/file_list_fragment_include.xml:21`, `res/layout-sw600dp-land/file_list_fragment_include.xml:22`)이 같은 앱바 include를 쓰므로 앱바 XML 한 곳만 고친다. 뷰 바인딩 클래스 `FileListFragmentAppBarIncludeBinding`에 새 ID가 생긴다.
- 바꾸지 않지만 관계있는 것: `DualPaneLayout.kt`(쓸기 제외 (b) `:413-417`, 탭 활성화 `:280-287`, 접근성 클릭 수신 `:486-491`, 한 창 모드 가시성 `:46-56`·`:175-183`), `CopyMoveConfirmation.kt:75-80`(`toLocationDisplayString`, 재사용).

## 2. 함수 명세

표기: "`F:N`"은 0cca1b4의 `FileListFragment.kt` 줄, `H` = `FileListHostFragment.kt`, `V` = `DualPaneViewModel.kt`, `L` = `DualPaneLayout.kt`.

### 2.1 K3 `DualPaneViewModel` (수정, 기존 `V:1-71`)

#### `companion object { const val PANE_COUNT = 2 }` (신규)
- **추적**: HLD K3·K9 `%1$d / %2$d` / REQ-2-17·20
- 창 수. 창 표시의 전체 창 수와 게시판 크기에 쓴다. 창 수를 바꾸는 요구는 범위 밖(SRS 1.3)이라 상수다.

#### `internal fun otherPaneIndex(paneIndex: Int): Int` (`DualPaneViewModel.kt` 파일 수준, 순수)
- **추적**: HLD K3 "반대쪽 창", 3.6 `[반대쪽]` / REQ-2-17 (#9 REQ-2-12가 씀)
- **입력·전제**: `paneIndex`는 0 또는 1.
- **출력**: `1 - paneIndex`.
- **오류**: 없음(범위 밖 값은 호출자 결함 — 결과도 범위 밖이 되어 `getPanePath`가 `IndexOutOfBoundsException`을 던진다).
- **이유(파일 수준)**: `DualPaneLayout.kt`의 순수 함수와 같은 이유 — 단위 시험이 `ViewModel` 클래스를 불러오지 않게(LLD-B 2.3).
- **기존 코드와의 관계**: `togglePane()`의 `1 - activePane`(`V:37`)과 같은 식. `togglePane`은 바꾸지 않는다.
- **테스트**: U-C05.

#### 창 경로 게시판 `panePaths` (신규)
| 멤버 | 명세 |
|---|---|
| `private val _panePathLiveDatas = List(PANE_COUNT) { MutableLiveData<Path?>(null) }` | 창마다 하나. 처음 `null` = "아직 모름". |
| `fun getPanePathLiveData(index: Int): LiveData<Path?>` | 그 창의 현재 경로 관찰용. **전제**: 메인 스레드, `index in 0 until PANE_COUNT`(아니면 `IndexOutOfBoundsException`). |
| `fun getPanePath(index: Int): Path?` | 지금 값(`_panePathLiveDatas[index].value`). `null`이면 그 창이 아직 경로를 게시하지 않았다(창 2 첫 적재 전, 또는 선택 모드라 창 2가 없음). |
| `fun setPanePath(index: Int, path: Path)` | **쓰는 곳은 그 창의 K5 하나뿐**(2.3 `onCurrentPathChanged`). **사후**: 지금 값과 `==`이면 아무것도 하지 않는다(관찰자 재호출 방지 — 같은 경로로 다시 적재해도 `TrailLiveData`가 새 값을 내므로, `FileListViewModel.kt:39`). 아니면 `value = path`. **오류**: 없음. 메인 스레드(`setValue`). |

- **추적**: HLD K3 `panePaths[0..1]`(#7 소유), 2.1 분배표, 3.1(호스트 범위 상태), 3.8("반대쪽 경로를 아직 모를 때") / REQ-2-17 (#9 REQ-2-12)
- **수명**: 호스트 프래그먼트 범위라 회전·크기 변경을 넘어 남는다. 이때 창의 `FileListViewModel`도 남으므로 값이 맞다. 프로세스 종료 뒤에는 `null`에서 다시 시작하고 창이 처음 관찰에서 다시 게시한다(HLD 3.1 "프로세스 종료 뒤 처음 값").
- **초기화하지 않는다**: 호스트 `onActivityCreated`의 `setPaneMode(PaneMode(false, 0))`(`H:141-143`)와 달리 경로는 이전 구성의 값이 그대로 맞으므로 지우지 않는다(지우면 회전 직후 한 프레임 반대쪽 경로가 사라진다).
- **값의 뜻**: 창의 `FileListViewModel.currentPath` 원래 값(정규화하지 않음). 복사·이동 대상으로 쓸 때의 `j` 변환(HLD 3.6-1)은 K6(`ConfirmCopyMoveDialogFragment.show` → `createCopyMoveConfirmation`, `CopyMoveConfirmation.kt`)이 한다.
- **기존 코드와의 관계**: LLD-B 2.2의 `activePaneLiveData`·`paneModeLiveData`와 같은 모양(비공개 `MutableLiveData` + 공개 `LiveData`). `valueCompat`은 non-null 값용이라 쓰지 않는다. 게시판을 `List<LiveData>` 공개 속성 대신 함수로 두는 이유: 쓰기(`setPanePath`)를 한 곳에 모으고 형식을 `LiveData`로 좁히기 위해서.

### 2.2 `PaneIndicator.kt` (신규, 순수 로직)

#### `enum class PaneIndicatorMode { HIDDEN, SWITCHER, ACTIVE, INACTIVE }`
| 값 | 언제 | 보이는 것 |
|---|---|---|
| `HIDDEN` | 선택 모드(창 2 없음, SRS A1) | 창 표시 줄·선 모두 없음(HLD K7 (3)) |
| `SWITCHER` | 한 창 모드 | `N / 2` 단추 + "창 M: <반대쪽 경로>"(HLD K7 (1)) |
| `ACTIVE` | 두 창 모드, 이 창이 활성 | "창 N · 활성"(누를 수 없음) + 앱바 아래 굵은 선(HLD K7 (2)) |
| `INACTIVE` | 두 창 모드, 이 창이 비활성 | "창 N"(누를 수 있음, 누르면 활성), 선 자리는 비워 둠(HLD K7 (2)) |

#### `internal fun getPaneIndicatorMode(isDualPaneEnabled: Boolean, isDualPane: Boolean, paneIndex: Int, activePane: Int): PaneIndicatorMode` (파일 수준, 순수)
- **추적**: HLD K7 (1)~(3) / REQ-2-08·17·20
- **입력·전제**: `isDualPaneEnabled` = K3 `isDualPaneEnabled`, `isDualPane` = K3 `paneMode.isDualPane`(K4 판단), `paneIndex`·`activePane` ∈ {0, 1}.
- **출력**: `!isDualPaneEnabled` → `HIDDEN`, `!isDualPane` → `SWITCHER`, `paneIndex == activePane` → `ACTIVE`, 아니면 `INACTIVE`.
- **오류**: 없음.
- **비고**: 한 창 모드에서 숨은 창도 `SWITCHER`다 — 숨은 창의 창 표시 줄이 미리 맞는 내용을 가지고 있어 전환 직후 바로 초점을 받을 수 있다(2.4). `SWITCHER` 내용은 `activePane`과 무관하다(자기 번호·반대쪽 경로뿐).
- **테스트**: U-C01~U-C04.

### 2.3 K5 `FileListFragment` (수정 — 창)

#### `Listener` (수정, 기존 `F:1469-1473`)
- 추가: `fun onPaneIndicatorClicked(paneIndex: Int, hasInputFocus: Boolean)` — 창 표시 컨트롤이 눌렸다. `hasInputFocus`는 누른 순간 컨트롤이 입력 초점을 가졌는지(키보드로 실행했는지). 구현은 호스트(2.4).
- **추적**: HLD K7·3.9 / REQ-2-07·20
- **이유(호스트로 넘김)**: 한 창 모드 전환 뒤 초점은 **다른 창**의 컨트롤로 가야 하는데(HLD 3.9) 창은 다른 창을 모른다. 두 창을 다 아는 호스트가 `togglePane()`과 초점 이동을 함께 한다.

#### `onActivityCreated(savedInstanceState)` (수정, 기존 `F:186-358`)
- **추적**: HLD K5·K7 / REQ-2-08·17·20
- 바뀌는 곳(나머지 순서 그대로):
  1. 뷰 준비(기존 `F:207` `breadcrumbLayout.setListener` 뒤): `ViewCompat.setAccessibilityDelegate(binding.paneIndicatorText, paneIndicatorAccessibilityDelegate)`(아래), `binding.paneIndicatorText.setOnClickListener { listener.onPaneIndicatorClicked(args.paneIndex, it.isFocused) }`.
  2. 관찰(기존 `F:317` 경로 관찰은 그대로 — 그 콜백 `onCurrentPathChanged`가 게시를 더한다, 아래). 기존 `F:334` `paneModeLiveData` 관찰의 본문을 `{ updateSpanCount(); schedulePaneIndicatorUpdate() }`로. `dualPaneViewModel.isDualPaneEnabled`일 때만 추가: `dualPaneViewModel.activePaneLiveData.observe(viewLifecycleOwner) { schedulePaneIndicatorUpdate() }`, `dualPaneViewModel.getPanePathLiveData(otherPaneIndex(args.paneIndex)).observe(viewLifecycleOwner) { schedulePaneIndicatorUpdate() }`. 선택 모드면 관찰하지 않는다 — 창 표시 줄은 XML 기본값 `GONE` 그대로(2.5).
- **전제**: `dualPaneViewModel.isDualPaneEnabled`는 호스트가 자기 `onActivityCreated`에서 창을 추가하기 전에 넣는다(`H:106-107`). 창의 `onActivityCreated`는 그 뒤에 불린다(새로 시작: 창은 그 `commit`으로 추가됨, 복원: 부모의 `onActivityCreated` 뒤 자식에 전달).
- 새 관찰 대상은 모두 호스트 범위 K3의 `LiveData`다. 창 뷰모델을 호스트가 읽지 않으므로 LLD-B v1.3 2.5의 "붙기 전 읽힘" 문제와 무관하다.

#### `private fun onCurrentPathChanged(path: Path)` (수정, 기존 `F:558-561`)
- **추적**: HLD K5 "자기 현재 경로를 K3에 게시" / REQ-2-17 (#9 REQ-2-12)
- 기존 두 갱신 앞에 `dualPaneViewModel.setPanePath(args.paneIndex, path)`.
- **사후조건**: 창의 현재 경로가 바뀔 때마다(폴더 이동·뒤로·서랍 항목·경로 입력·`resetTo`) K3 게시판이 같은 값이 된다. 관찰은 창 뷰가 STARTED일 때 오고, 한 창 모드의 숨은 창도 RESUMED라(HLD 3.8) 계속 게시한다. 액티비티가 멈춘 동안에는 경로가 바뀌지 않는다.
- 선택 모드에서도 창 1은 게시한다(쓰는 곳이 없어도 해가 없고 분기를 줄인다).

#### `private val paneIndicatorUpdateRunnable = Runnable { if (view != null) updatePaneIndicator() }` / `private fun schedulePaneIndicatorUpdate()` (신규)
- **추적**: HLD K7, 3.4(탭 활성화 순서) / REQ-2-07·08
- **알고리즘**: `binding.paneIndicatorLayout.removeCallbacks(paneIndicatorUpdateRunnable)`; `binding.paneIndicatorLayout.post(paneIndicatorUpdateRunnable)`. 같은 메시지 안의 여러 관찰(활성 창·모드·경로)을 한 번의 갱신으로 모은다.
- **바로 갱신하지 않는 이유(필수)**: 두 창 모드에서 비활성 창의 컨트롤을 손으로 누르면 K4가 `ACTION_UP`을 자식에게 넘기기 **전에** 그 창을 활성으로 한다(`L:280-287`, HLD 3.4). 이때 갱신을 바로 하면 컨트롤이 `isClickable = false`가 된 뒤 같은 `ACTION_UP`을 받는다. `View.onTouchEvent`는 누를 수 없는 뷰의 `ACTION_UP`을 처리하지 않으므로 DOWN에서 켜진(또는 누름 확인 지연 뒤 켜질) 눌림 상태(물결 효과)가 꺼지지 않고 남는다. 한 메시지 미루면 그 `ACTION_UP`이 누를 수 있는 상태에서 처리되고(클릭은 `View`가 다시 `post`한 `performClick`으로 실행 — 이 갱신 뒤라 `onPaneIndicatorClicked`가 불리지만 이미 활성이라 K3에서 무시된다), 그다음 갱신된다. 시험 T-C05(a).
- **창 뷰가 없어졌으면**(`onDestroyView` 뒤 실행): 아무것도 하지 않는다.

#### `private fun updatePaneIndicator()` (신규)
- **추적**: HLD K7·K9·3.9 / REQ-2-08·17·20
- **입력·전제**: 메인 스레드, 창 뷰 있음.
- **알고리즘**:
  1. `mode = getPaneIndicatorMode(dualPaneViewModel.isDualPaneEnabled, dualPaneViewModel.paneMode.isDualPane, args.paneIndex, dualPaneViewModel.activePane)`. `number = args.paneIndex + 1`, `otherIndex = otherPaneIndex(args.paneIndex)`, `otherNumber = otherIndex + 1`, `count = DualPaneViewModel.PANE_COUNT`.
  2. `binding.paneIndicatorLayout.isVisible = mode != HIDDEN`. `binding.activePaneLine.visibility = when (mode) { ACTIVE -> VISIBLE; INACTIVE -> INVISIBLE; else -> GONE }` — 비활성 창도 선 높이를 차지해 두 창의 앱바 높이가 같다(목록 첫 줄이 나란히 맞음). `HIDDEN`이면 여기서 끝.
  3. 컨트롤(`binding.paneIndicatorText`)을 모드별로:

     | 모드 | `text` | `contentDescription` | `isClickable` | 클릭 동작 설명(`ACTION_CLICK` 라벨) |
     |---|---|---|---|---|
     | `SWITCHER` | `file_list_pane_switcher_format`(`number`, `count`) → "1 / 2" | `file_list_pane_switcher_description_format`(`number`, `count`) → "창 1 / 2" | `true` | `file_list_pane_switcher_action_format`(`otherNumber`) → "창 2로 전환" |
     | `ACTIVE` | `file_list_pane_name_active_format`(`number`) → "창 1 · 활성" | `file_list_pane_name_active_description_format`(`number`) → "창 1, 활성" | `false` | 없음 |
     | `INACTIVE` | `file_list_pane_name_format`(`number`) → "창 2" | `null`(보이는 글자 "창 2"가 라벨) | `true` | `file_list_pane_activate_action` → "이 창을 활성 창으로" |

     동작 설명은 `ViewCompat.replaceAccessibilityAction(view, AccessibilityActionCompat.ACTION_CLICK, label, null)`로 넣는다(HLD 3.9). "없음"은 `label = null`로 불러 이전에 넣은 동작을 지운다(androidx.core: 라벨·명령이 둘 다 `null`이면 제거). 명령을 `null`로 두면 실행 시 기본 처리(`performClick`)로 넘어간다. 전환 뒤에는 이 함수가 다시 불려 라벨·설명이 새 값이 된다(HLD 3.9).
  4. 반대쪽 경로(`binding.otherPanePathText`): `mode == SWITCHER`이고 `path = dualPaneViewModel.getPanePath(otherIndex)`가 `null`이 아니면 `isVisible = true`, `text = getString(R.string.file_list_pane_other_path_format, otherNumber, BidiFormatter.getInstance().unicodeWrap(path.toLocationDisplayString()))`. 아니면 `isVisible = false`(두 창 모드는 반대쪽 창의 경로 표시줄로 보인다 — HLD K7 (2), 경로를 아직 모르면 숨김).
- **출력·사후조건**: 창 표시 줄·선·컨트롤이 위 표대로. 입력 초점·접근성 초점은 바꾸지 않는다(초점은 2.3 `focusPaneIndicator`).
- **오류**: 없음.
- **기존 코드와의 관계**: 경로 문자열은 확인 창과 같은 `Path.toLocationDisplayString()`(`CopyMoveConfirmation.kt:75-80`, 압축 내부는 "압축 파일 경로 + 내부 경로")과 `BidiFormatter.unicodeWrap`(`ConfirmCopyMoveDialogFragment.kt:41-63`과 같은 관용구, `RTL-3`)을 재사용한다. `getString`·`isVisible`은 이 파일의 기존 관용구.

#### `private val paneIndicatorAccessibilityDelegate = object : AccessibilityDelegateCompat()` (신규)
- **추적**: HLD 3.9 "역할·라벨" / REQ-2-20, `Content_Description`
- `onInitializeAccessibilityNodeInfo(host, info)`: `super` 뒤 `if (host.isClickable) info.className = Button::class.java.name`. 누를 수 있을 때만 단추 역할로 알린다(`SWITCHER`·`INACTIVE`). `ACTIVE`(누를 수 없음)는 `TextView` 그대로라 TalkBack이 "단추"라고 읽지 않는다.
- `replaceAccessibilityAction`(위)보다 **먼저** 설정한다(2.3 `onActivityCreated` 1) — androidx.core는 이미 `AccessibilityDelegateCompat`가 있으면 그것을 두고 동작 목록만 더하며, `super.onInitializeAccessibilityNodeInfo`가 그 동작을 노드에 넣는다. 나중에 설정하면 앞서 넣은 동작 라벨이 있는 기본 위임을 덮는다.
- **이유(위젯 대신 위임)**: `Button`/`MaterialButton`을 쓰면 `ACTIVE`(누를 수 없음)에서도 단추로 읽히고, 끄면(`isEnabled = false`) 흐린 글자가 되어 대비(3장)가 떨어진다. 같은 뷰가 상태에 따라 단추·글자를 오가므로 위임이 단순하다.

#### `fun focusPaneIndicator(requestInputFocus: Boolean)` (신규 공개)
- **추적**: HLD 3.9 "전환 뒤 초점" / REQ-2-20, `Keyboard_Navigation`
- **입력·전제**: 메인 스레드. 호스트가 레이아웃 뒤(`post`)에 부른다(2.4).
- **알고리즘**: `if (view == null) return`. `val view = binding.paneIndicatorText`. `requestInputFocus`이면 `view.requestFocus()`. 그다음 `view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)`.
- **출력·사후조건**: 접근성 서비스가 켜져 있으면 이 창의 컨트롤이 접근성 초점을 받아 TalkBack이 새 라벨("창 2 / 2")과 동작 설명("창 1로 전환")을 읽는다. 꺼져 있으면 `false`일 뿐 무해(HLD 3.9). `requestInputFocus`면 입력 초점도 옮긴다.
- **입력 초점도 옮기는 이유(LLD 결정, FB-C4)**: 키보드로 눌린 컨트롤은 전환과 함께 `GONE`이 되어 입력 초점을 잃고, 시스템이 초점을 창 안의 다른 첫 뷰로 옮긴다. 같은 자리에서 Enter로 되돌릴 수 있게 새 컨트롤로 옮긴다. 터치로 누른 경우(`hasInputFocus = false` — `TextView`는 터치 모드에서 초점을 받지 않음)는 옮기지 않아 터치 모드를 깨지 않는다.

#### `Binding` (수정, 기존 `F:1475-1517`)
- 필드 추가: `val paneIndicatorLayout: ViewGroup`, `val paneIndicatorText: TextView`, `val otherPanePathText: TextView`, `val activePaneLine: View` — `appBarBinding`에서(두 창 레이아웃 변형 모두 같은 include라 non-null).

#### 그대로 두는 것 (확인 목록)
- `Args`(`F:1466-1467`, `paneIndex` 이미 있음), 뒤로 가기 콜백(`F:234-253`), `searchViewExpandedLiveData` 미러(`F:144-146`), `currentPathLiveData`(`F:1456-1457`), `updateSpanCount`(`F:634-652`), 툴바 제목(`F:193`, `F:779-780`).

### 2.4 K2 `FileListHostFragment` (수정)

#### `FileListFragment.Listener.onPaneIndicatorClicked(paneIndex: Int, hasInputFocus: Boolean)` (신규 구현)
- **추적**: HLD K3 `togglePane()`, K7 (1)(2), 3.4 "표시", 3.9 / REQ-2-07·20
- **입력·전제**: 메인 스레드, 창의 클릭에서 불린다. `panes`는 초기화됨(창이 있으면 호스트 `onActivityCreated`가 끝났다).
- **알고리즘**:
  1. 두 창 모드(`viewModel.paneMode.isDualPane`)면 `viewModel.setActivePane(paneIndex)` 후 끝. 비활성 창 컨트롤의 키보드 Enter·접근성 클릭·마우스 클릭이 여기로 온다(손 탭은 K4가 이미 활성화했으므로 같은 값이라 무시 — LLD-B 2.2). REQ-2-07의 탭 활성화와 같은 결과다(HLD K7 (2)).
  2. 한 창 모드: `paneIndex != viewModel.activePane`이면 끝(숨은 창 컨트롤은 `GONE`이라 눌릴 수 없다 — 방어).
  3. `viewModel.togglePane()`(쓸기 확정과 같은 함수, HLD K3·3.4). 그 뒤 `viewModel.activePane == paneIndex`면 끝(전환되지 않음 — 선택 모드에서는 컨트롤이 없으므로 이론상만).
  4. `val pane = panes[viewModel.activePane]`; `binding.dualPaneLayout.post { pane.focusPaneIndicator(hasInputFocus) }`.
- **`post`가 레이아웃 뒤에 실행되는 근거**: 3의 `togglePane()` → `activePaneLiveData` 관찰(`H:153-155`) → K4 `activePane` setter가 같은 호출 안에서 가시성을 바꾸고 `requestLayout()`(`L:46-56`). `requestLayout`이 예약한 화면 갱신은 메시지 큐에 동기 장벽을 세우고 비동기 메시지로 실행되므로, 그 뒤에 `post`한 일반 메시지는 갱신(측정·배치)이 끝난 뒤 실행된다. 그래서 새로 보이는 창의 컨트롤은 이미 배치되어 있다(HLD 3.9 "레이아웃 뒤(`post`)"). 새 창의 `SWITCHER` 내용은 `activePane`과 무관해 미리 맞다(2.2).
- **오류**: 없음. 호스트 뷰가 그 사이 없어지면 `post`한 일은 실행되지 않는다(뷰가 붙어 있지 않음). 실행되더라도 `focusPaneIndicator`가 창 뷰를 확인한다.
- **같은 파일의 주석 정정(동작 변경 없음)**: 검색 접기 콜백 주석(`H:172-174`)의 "take precedence over all the others"는 API 33 이상에서 사실과 다르다(열고 닫는 서랍이 디스패처 밖에서 먼저 — LLD-B v1.3 4.2, HLD v4.9 3.5). "within the activity dispatcher; on API 33+ an open modal drawer still goes first" 취지로 고친다.
- **기존 코드와의 관계**: `dualPaneLayoutListener.onSwipeToTogglePane`(`H:229-231`)과 같은 `togglePane()`. 초점 이동은 누르기 전환에만 한다 — 쓸기 전환은 손가락 제스처라 초점 이동 요구가 없다(HLD 3.9는 누르기만 다룸).

### 2.5 레이아웃 — `res/layout/file_list_fragment_app_bar_include.xml` (수정)
- **추적**: HLD K7, 3.9(48dp·대비·키보드) / REQ-2-08·17·20, `Touch_Target_Size`, `RTL-1`
- `BreadcrumbLayout`(`:50-55`) 뒤, `CoordinatorAppBarLayout` 안에 두 자식을 더한다. 둘 다 `app:layout_scrollFlags`가 없고 스크롤 플래그 없는 경로 표시줄 뒤라 목록과 함께 접히지 않는다(앞쪽 툴바 묶음만 접힘, `:22-25`).

```xml
<LinearLayout
    android:id="@+id/paneIndicatorLayout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingStart="@dimen/content_start_margin_minus_12dp"
    android:paddingEnd="@dimen/screen_edge_margin_minus_12dp"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:visibility="gone">

    <TextView
        android:id="@+id/paneIndicatorText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:minWidth="@dimen/touch_target_size"
        android:minHeight="@dimen/touch_target_size"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:background="?selectableItemBackground"
        android:focusable="true"
        android:gravity="center"
        android:singleLine="true"
        android:textAppearance="?textAppearanceListItemSecondary"
        android:textColor="?android:textColorPrimary" />

    <TextView
        android:id="@+id/otherPanePathText"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:ellipsize="middle"
        android:singleLine="true"
        android:textAppearance="?textAppearanceListItemSecondary"
        android:textColor="?android:textColorSecondary" />
</LinearLayout>

<View
    android:id="@+id/activePaneLine"
    android:layout_width="match_parent"
    android:layout_height="3dp"
    android:background="@android:color/white"
    android:backgroundTint="?colorControlNormal"
    android:visibility="gone" />
```

- **들여쓰기 맞춤**: 줄의 `paddingStart`·`paddingEnd`는 경로 표시줄(`:54-55`)과 같고, 컨트롤 안쪽 12dp가 경로 표시줄 항목의 12dp(`res/layout/breadcrumb_item.xml`의 `paddingStart`)와 같아 글자가 경로 표시줄 글자와 세로로 맞는다. start/end만 쓴다(`RTL-1`).
- **터치 대상(HLD 3.9가 LLD에 맡긴 방법)**: `minWidth`/`minHeight` 48dp(`@dimen/touch_target_size`, `res/values/dimens.xml:41`)를 고른다. `TouchDelegate`는 부모(줄) 경계 밖으로 넓힐 수 없어 줄 높이를 48dp 아래로 줄이면 위아래로 48dp를 만들 수 없고, 위의 경로 표시줄 항목과 겹친다. 그래서 줄 높이 = 컨트롤 높이 = 48dp(0.3).
- **좁은 창**: 두 창 모드 최소 창 너비 300dp(HLD 3.9)에서 줄 = 60dp + 컨트롤("창 2 · 활성" ≈ 12+약 70+12dp)로 넘치지 않는다. 한 창 모드의 반대쪽 경로는 남은 너비에서 가운데 줄임표로 줄인다(`ellipsize="middle"` — 앞의 "창 2:"와 경로 끝 폴더가 함께 남는다. `start`·`middle` 줄임은 `singleLine`이 필요해 `maxLines` 대신 씀 — `res/layout/file_item_list.xml:77` 주석과 같은 이유).
- **글자 스타일**: `?textAppearanceListItemSecondary` + `?android:textColor{Primary,Secondary}` — 이 앱 목록 보조 글자의 관용구(`res/layout/file_item_list.xml:94-95`). HLD 3.9의 "기존 툴바 부제목 글자 스타일"과 다르다 → FB-C2.
- **선 색**(HLD v4.9 3.9, FB-C1): `?colorControlNormal`은 색 상태 목록이라 배경 드로어블로 바로 쓸 수 없으므로 흰 색 배경에 `backgroundTint`로 칠한다(`View`의 `android:backgroundTint`, API 21+). 앱바 안이라 `android:theme="?actionBarTheme"`(`:18`)가 적용되어 `ThemeOverlay.MaterialFiles.ActionBar`의 값(`res/values/styles.xml:61-62`)이 된다.
- **배경**: 줄·선은 배경을 두지 않아(선 제외) 앱바 배경(`?colorAppBarSurface`, 들림 시 색 변화 포함)이 그대로 보인다. 컨트롤의 `?selectableItemBackground`는 누를 수 있을 때만 물결이 생긴다.
- **창 레이아웃 두 변형**(`res/layout/file_list_fragment_include.xml`, `res/layout-sw600dp-land/file_list_fragment_include.xml`)은 이 include를 그대로 쓰므로 바꾸지 않는다.

### 2.6 문자열 (K9의 창 표시 몫)
| 이름 | `values` | `values-ko` | 쓰는 곳 |
|---|---|---|---|
| `file_list_pane_switcher_format` | `%1$d / %2$d` (`translatable="false"`) | — | `SWITCHER` 보이는 글자(HLD K9 `%1$d / %2$d`) |
| `file_list_pane_switcher_description_format` | `Pane %1$d of %2$d` | `창 %1$d / %2$d` | `SWITCHER` 라벨 |
| `file_list_pane_switcher_action_format` | `Switch to pane %1$d` | `창 %1$d로 전환` | `SWITCHER` 동작 설명 |
| `file_list_pane_name_format` | `Pane %1$d` | `창 %1$d` | `INACTIVE` 보이는 글자·라벨 |
| `file_list_pane_name_active_format` | `Pane %1$d · Active` | `창 %1$d · 활성` | `ACTIVE` 보이는 글자 |
| `file_list_pane_name_active_description_format` | `Pane %1$d, active` | `창 %1$d, 활성` | `ACTIVE` 라벨 |
| `file_list_pane_activate_action` | `Make this the active pane` | `이 창을 활성 창으로` | `INACTIVE` 동작 설명 |
| `file_list_pane_other_path_format` | `Pane %1$d: %2$s` | `창 %1$d: %2$s` | `SWITCHER` 반대쪽 경로 |

- 위치: 두 파일의 기존 `file_list_confirm_copy_move_*` 묶음(`values/strings.xml:320-339`, `values-ko/strings.xml:284-298`) 뒤. 그 밖의 언어는 영어로 대체된다(#8과 같은 범위).
- HLD K9의 "활성"을 따로 두지 않고 두 형식(`…_name_active_format`·`…_active_description_format`)에 넣는다 — 번역에서 어순을 바꿀 수 있게. HLD K9 목록에 없는 반대쪽 경로 형식은 FB-C3.
- 라벨에 "단추" 같은 역할 말을 넣지 않는다(SRS 3.3 설명). 숫자는 `%d`라 로캘 숫자로 바뀐다(아랍어 등).

## 3. 접근성·대비 상세 (HLD 3.9 대응)

| HLD 3.9 항목 | 이 LLD | 시험 |
|---|---|---|
| 역할·라벨 | 누를 수 있을 때만 `Button` 역할(2.3 위임), 라벨은 2.3 표의 `contentDescription`(`INACTIVE`는 보이는 글자) | T-C03, T-C05(b) |
| 동작 설명 | `replaceAccessibilityAction(ACTION_CLICK, …)`, 상태가 바뀔 때마다 다시 설정 | T-C03 |
| 전환 뒤 초점 | 호스트가 레이아웃 뒤 `post`로 새 창 컨트롤에 `ACTION_ACCESSIBILITY_FOCUS`(+ 키보드였으면 입력 초점) | T-C03, T-C06 |
| 두 창 모드 활성화 | 비활성 창 컨트롤 클릭 → `setActivePane`(2.4-1). 그 밖의 요소는 K4 `TYPE_VIEW_CLICKED` 수신(#6, `L:486-491`) | T-C05 |
| 터치 대상 48dp | `minWidth`/`minHeight` 48dp, 줄 높이 48dp(2.5) | T-C08 |
| 좁은 창에서 겹침 없음 | 컨트롤은 줄이지 않고, 반대쪽 경로만 가운데 줄임(2.5). 두 창 모드는 경로 글자가 없다 | T-C12 |
| 대비·색 외 구분 | 활성 = 선(형태) + "활성" 글자(글자). 글자 색은 `?android:textColorPrimary`·`?android:textColorSecondary`(4.5:1 이상 기대). 선 색은 앱바 테마의 `?colorControlNormal`(= `?android:textColorPrimary`, 툴바 아이콘과 같은 색, HLD v4.9 3.9 — FB-C1 반영). 강조색 `?colorPrimary`는 사용자 테마 색 19개 중 9개가 밝은 테마 앱바(흰색)에서 3:1 미만이라 쓰지 않는다(아래) | T-C04, T-C09 |
| 키보드·마우스 | 컨트롤은 `focusable`(상태와 무관하게 늘 — `ACTIVE`로 바뀌어도 초점이 튀지 않게), Enter·D-pad 가운데는 `View`의 확인 키 처리로 `performClick`. 마우스 클릭은 터치 이벤트로 와 K4 탭 경로와 클릭을 탄다 | T-C05~T-C07 |

- **선 색의 대비 계산(FB-C1 근거)**: WCAG 상대 휘도 대비, 앱바 배경 = 밝은 테마 `#FFFFFF`(`color_surface_light`), 어두운 테마 `#202124`(`google_grey_900`). 기본 강조색은 밝은 `#1A73E8` 4.51:1, 어두운 `#8AB4F8` 7.64:1로 통과. 사용자 선택 테마 색(`res/values/colors_custom.xml` 500 계열 / `res/values-night/colors_custom.xml` 300 계열): 밝은 테마에서 light_blue 2.63, cyan 2.30, green 2.78, light_green 2.10, lime 1.51, yellow 1.22, amber 1.63, orange 2.16, grey 2.68로 9개가 3:1 미만이다. 어두운 테마는 19개 모두 4.37:1 이상. `?android:textColorPrimary`(앱바 테마의 `colorControlNormal`, `res/values/styles.xml:61-62`)는 두 테마에서 약 16:1.
- 실제 대비는 Accessibility Scanner(또는 화면 캡처 화소값)로 확인한다(T-C09, HLD 3.9 "실제 값은 LLD에서").

## 4. #9가 쓰는 `panePaths` API (계약)

| 항목 | 계약 |
|---|---|
| 얻기 | 창 프래그먼트 안에서 기존 `dualPaneViewModel`(`F:136`, 호스트 범위 K3). 반대쪽 창 번호는 `otherPaneIndex(args.paneIndex)`. 반대쪽 명령을 내는 창은 활성 창이다(HLD 3.4 — 선택 메뉴는 누른 창 = 활성 창). |
| 읽기 | 메뉴 실행 시점에 `dualPaneViewModel.getPanePath(otherPaneIndex(args.paneIndex))`. 메뉴 활성 여부를 따라가려면 `getPanePathLiveData(…)`를 `viewLifecycleOwner`로 관찰한다(경로가 바뀌면 읽기 전용 판단 `path.fileSystem.isReadOnly`도 다시 — HLD 3.6). |
| `null` | "반대쪽 경로를 아직 모름". 창 2 첫 적재 전, 또는 선택 모드(창 2 없음 — 이때는 #9 명령 자체가 없다). #9는 `null`이면 반대쪽 명령을 비활성으로 둔다(HLD 3.8). |
| 값 | 반대쪽 창 `FileListViewModel.currentPath` 원래 값. 정규화·`j` 변환을 하지 않는다 — `ConfirmCopyMoveDialogFragment.show(copy, files, <이 값>, Origin.OTHER_PANE, this)`(LLD-D 4장)가 판정·작업용 `j`를 만든다. |
| 신선도 | 반대쪽 창이 폴더를 바꾸면 메인 스레드에서 같은 메시지 안에 게시된다(2.3 `onCurrentPathChanged`). 한 창 모드의 숨은 창도 게시한다. 확인 창이 떠 있는 동안 바뀐 값은 확인 창에 영향이 없다(인자로 굳힘, HLD 3.6). |
| 쓰기 | **창 자신의 K5만**(`setPanePath`). #9는 쓰지 않는다. |
| LLD-D와의 관계 | LLD-D 4장의 "반대쪽 경로는 #6의 `DualPaneViewModel.panePaths`"는 이 계약으로 바뀐다(소유가 #7로 옮겨짐 — LLD-B FB-4, PM 결정 PR #17). 호출 모양(`show`의 대상 인자)은 같다. |
| 스레드 | 메인 스레드만(`LiveData.setValue`·`getValue`). |

## 5. 테스트 설계

### 5.1 단위 테스트 (필수)
JUnit 4(`app/build.gradle:216`, #6·#8에서 승인·추가됨). `app/src/test/java/me/zhanghai/android/files/filelist/PaneIndicatorTest.kt`. 실행: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --console=plain 2>&1 | tail -n 30` → `BUILD SUCCESSFUL`, `app/build/test-results/testDebugUnitTest/`에서 새 5건 + 기존 21건(`CopyMoveConfirmationTest` 16, `DualPaneLayoutTest` 5) 통과. `DualPaneViewModel`의 `LiveData` 멤버는 로컬 JVM에서 메인 루퍼가 없어 시험하지 않는다(새 시험 의존성 없이) — 수동 T-C01·T-C14로 확인.

| 테스트 | 대상 함수 | 경우 | 기대 결과 |
|---|---|---|---|
| U-C01 | `getPaneIndicatorMode` | 경계: `isDualPaneEnabled=false`, 나머지 네 조합(`isDualPane` true/false × `paneIndex`=`activePane` 여부) | 모두 `HIDDEN` |
| U-C02 | `getPaneIndicatorMode` | 정상: enabled, `isDualPane=false`, (0,0)·(1,0)·(0,1)·(1,1) | 모두 `SWITCHER`(숨은 창 포함) |
| U-C03 | `getPaneIndicatorMode` | 정상: enabled, dual, (0,0)·(1,1) | `ACTIVE` |
| U-C04 | `getPaneIndicatorMode` | 정상: enabled, dual, (0,1)·(1,0) | `INACTIVE` |
| U-C05 | `otherPaneIndex` | 정상: 0, 1 | 1, 0 |

### 5.2 준비
LLD-B 5.2와 같다(빌드, 기준 APK = develop 0cca1b4 빌드, `adb logcat -b crash -d`, 기기 구성 SRS 3.2 C1~C4, 제스처/3버튼 전환, RTL 강제). **실기기 `R54N102PRWV`는 쓰지 않는다**(에뮬레이터만). 추가:
- 접근성 노드 확인: `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` — 노드의 `class`·`content-desc`·`clickable`·`focusable`·`bounds`. 동작 설명(`ACTION_CLICK` 라벨)은 덤프에 없으므로 TalkBack에서 듣거나 미확인.
- TalkBack: 에뮬레이터에 TalkBack(Android Accessibility Suite)이 있으면 켠다. `adb input`의 터치는 TalkBack 터치 탐색을 우회한다(PR #22 T-B11 기록) — 초점 이동·두 번 탭은 에뮬레이터 창에서 마우스로 직접 하거나, 할 수 없으면 **미확인**으로 보고한다.
- 회색조: `adb shell settings put secure accessibility_display_daltonizer_enabled 1` + `adb shell settings put secure accessibility_display_daltonizer 0`(끝나면 `…_enabled 0`).
- 외부 키보드·마우스: 에뮬레이터 창의 물리 키보드 입력(Tab·방향키·Enter), `adb shell input keyevent KEYCODE_TAB`·`KEYCODE_ENTER`, 마우스 `adb shell input mouse tap <x> <y>`.
- 대비: Accessibility Scanner가 없으면 `adb exec-out screencap -p`의 화소값으로 글자·선과 배경의 대비를 계산한다(5장 계산식 3장과 같음).

### 5.3 시험 목록
| 테스트 | 대상 함수 | 경우 | 절차 | 기대 결과 |
|---|---|---|---|---|
| T-C00 빌드·단위 | 전체, U-C01~U-C05 | 정상 | `assembleDebug`, `testDebugUnitTest` | 둘 다 `BUILD SUCCESSFUL`, 26건 통과, 바뀐 파일에 새 경고 없음 |
| T-C01 한 창 표시(REQ-2-17) | `updatePaneIndicator`(`SWITCHER`), `onCurrentPathChanged`, `setPanePath` | 정상 | C1: 창 1에서 표시 확인 → 쓸기로 창 2 → 하위 폴더로 → 쓸기로 창 1 | 창 1: "1 / 2", "창 2: <창 2 경로>". 창 2: "2 / 2", "창 1: <창 1 경로>". 창 2에서 바꾼 폴더가 창 1의 표시에 바로 반영 |
| T-C02 누르기 전환(REQ-2-20) | 컨트롤 클릭, 호스트 `onPaneIndicatorClicked`, `togglePane` | 정상·경계 | C1: "1 / 2" 탭 → 탭 → (창 1→2→1). 컨트롤 위에서 오른쪽→왼쪽 쓸기(앱바 제외 영역) → 무반응. 창 2로 전환해 폴더 열고 다시 누르기로 돌아오기(쓸기 없이) | 1→2→1, 표시가 `2 / 2`·`1 / 2`로 갱신. 쓸기 무반응. SRS REQ-2-20 절차대로 |
| T-C03 TalkBack(REQ-2-20, HLD 3.9) | 위임, `replaceAccessibilityAction`, `focusPaneIndicator` | 정상 | C1 TalkBack: 컨트롤에 초점 → 읽기 기록 → 두 번 탭 → 읽기 기록 → 두 번 탭. 노드는 `uiautomator dump`로도 | "창 1 / 2, 단추, 두 번 탭하여 창 2로 전환"(문구는 TalkBack 판에 따라 다를 수 있음) → 전환 뒤 초점이 창 2 컨트롤로 옮겨 "창 2 / 2 … 창 1로 전환" → 되돌아감. 덤프: `class=android.widget.Button`, `content-desc="창 1 / 2"`. TalkBack을 쓸 수 없으면 덤프만 하고 나머지 미확인 |
| T-C04 두 창 활성 표시(REQ-2-08) | `updatePaneIndicator`(`ACTIVE`/`INACTIVE`), 선 | 정상 | C2: 창 1 활성 → 쓸기 → 창 2 탭 → 창 1 컨트롤 탭. 각 단계 캡처. 밝은·어두운 테마, 설정의 Material 3 켬·끔, 회색조 | 활성 창에만 "창 N · 활성"과 선, 비활성 창은 "창 M"(선 자리 빈칸, 두 앱바 높이 같음). 표시가 활성 창을 따라감. 회색조에서도 선·글자로 구분 |
| T-C05 두 창 비활성 컨트롤(REQ-2-07, R10) | 2.4-1, `schedulePaneIndicatorUpdate` | 정상·경계 | C2 창 1 활성: (a) 창 2 "창 2" 손 탭 → 활성, 눌림 물결이 남지 않음(캡처) (b) `uiautomator dump`로 창 1 컨트롤 `clickable=false`·`class=android.widget.TextView`, 창 2 `clickable=true`·`Button` (c) 키보드 Tab으로 창 2 컨트롤 → Enter (d) 마우스 클릭 (e) TalkBack 두 번 탭 | 각각 창 2가 활성, 표시 갱신. (e)를 못 하면 미확인 |
| T-C06 키보드(REQ-2-20, `Keyboard_Navigation`) | 컨트롤 `focusable`, `focusPaneIndicator(true)` | 정상 | C1 외부 키보드: Tab·방향키로 컨트롤까지 → Enter → 초점 위치 확인 → Enter | 전환, 입력 초점이 새 창 컨트롤로(초점 강조 표시), 다시 Enter로 되돌아감 |
| T-C07 마우스(REQ-2-20, `Mouse_Trackpad_Basic`) | 컨트롤 클릭 | 정상 | C1 `input mouse tap`으로 컨트롤 | 전환 |
| T-C08 터치 대상(`Touch_Target_Size`) | 2.5 | 경계 | C1·C2·C4에서 `uiautomator dump`의 컨트롤 `bounds` | 너비·높이 ≥ 48dp(px ÷ density) |
| T-C09 대비(`Visual_Contrast`, `Theme_Support`) | 2.5, 3장 | 경계 | C1·C2 밝은·어두운 테마, Material 3 켬·끔, 검은 밤 모드(`Theme.MaterialFiles.*.Black`, `colorSurface` 검정 — `res/values-night/themes_custom.xml:5-7`), 테마 색 기본과 강조색으로는 3:1 미만이던 밝은 노랑(`material_yellow`) | 글자 4.5:1 이상, 선 3:1 이상(Scanner 또는 화소 계산) |
| T-C10 선택 모드(SRS A1) | `HIDDEN` | 경계 | `GET_CONTENT`·`OPEN_DOCUMENT_TREE`·`CREATE_DOCUMENT`(LLD-A T-04 명령), C1·C2 | 창 표시 줄·선 없음, 앱바가 기준 APK와 같음 |
| T-C11 RTL(`RTL-1`·`RTL-3`·`RTL-4`) | 2.5, `BidiFormatter` | 경계 | RTL 강제 + 히브리어 이름 폴더를 창 2 경로로, C1·C2. 언어를 아랍어로 바꿔 한 번 | 줄이 거울 배치(컨트롤이 오른쪽 시작), 반대쪽 경로 글자 순서가 깨지지 않음, 숫자 로캘 표기 |
| T-C12 좁은 창·긴 경로 | 2.5 | 경계 | C1에서 창 2를 깊은 폴더(경로 > 화면 너비)로. C4(`wm size 1700x1300`, `wm density 320`, 고정 메뉴 접음 → 두 창, 창 약 425dp)와 LLD-B T-B04의 600dp 경계(세로, `wm density 160` + `wm size 600x1000` → 두 창, 창 300dp) | 한 창: 경로가 가운데 줄임표로 줄고 "창 2:"와 끝 폴더가 보임, 컨트롤과 겹치지 않음. 두 창 300dp: "창 2 · 활성"이 잘리지 않음 |
| T-C13 회전·복원 | K3 게시판 수명, 2.3 관찰 | 경계 | 창 2 활성·두 경로를 다르게 둔 뒤 C1↔C2 회전 ×3, `always_finish_activities` 켜고 다른 앱 갔다 오기 | 표시·선·반대쪽 경로가 즉시 맞음(경로가 빈 프레임 없음). 복원 뒤에도 맞음, 크래시 없음 |
| T-C14 경로 게시(#9 계약) | `setPanePath`, 4장 | 정상 | 한 창 모드 창 1에서, 창 2를 각각 경로 표시줄 누르기·뒤로·탐색 서랍 항목·Ctrl+L 경로 입력·압축 파일 열기로 이동시킨 뒤 창 1 표시 확인 | 매번 "창 2: <새 경로>"(압축 내부는 "압축 파일 경로 + 내부 경로") |
| T-C15 회귀 | LLD-B T-B08·T-B10·T-B12·T-B15(앱바 쓸기 부분), T-B21 | 회귀 | C1·C2에서 LLD-B 절차 | LLD-B 기대와 같음(앱바가 높아진 것 외). 창 표시 줄 위 탭은 그 창 활성화(T-B10의 "경로 표시줄" 경우와 같음) |

- 판정: 0.3의 의도된 변화 외에 기준 APK와 다르면 실패. 기록은 구현 PR 코멘트(시험별 통과/실패/미확인), 캡처는 `$MAIN_REPO/_workspace/impl-7/`.

## 6. 설계 피드백
SRS·HLD는 개발팀이 고치지 않았다. 아래는 설계팀에 보낸 것이다(이슈 #23의 HLD 정정과 같은 호출). **결과: 설계팀이 넷 다 HLD v4.9(커밋 2edec99)에 반영했다** — 3.9(선 색 `?colorControlNormal`, 글자 스타일, 입력 초점, 터치 대상 문장), K9 목록. SRS는 바뀌지 않음(REQ-2-08·3.3은 색을 정하지 않음). FB-C1은 강조색을 유지하려면 필수 조항(`Visual_Contrast`) 편차가 필요해 오너 결정 사항이 되므로, 설계팀은 필수 조건을 지키는 안으로 반영하고 PM이 강조색을 원하면 되돌리도록 했다.

- **FB-C1 (반영 — HLD v4.9 3.9. 사용자에게 보이는 색이라 PM 확인 대상) HLD 3.9 활성 창 선 색 `?colorPrimary` 계열의 3:1**: 사용자 테마 색 19개 중 9개가 밝은 테마 앱바(흰색) 대비 3:1 미만이다(3장 계산). 선은 REQ-2-08의 형태 단서이고 `Visual_Contrast`(필수)의 그래픽 3:1 대상이다. 권고: 선 색을 앱바 테마의 `?colorControlNormal`(= `?android:textColorPrimary`, 툴바 아이콘과 같은 색, 두 테마 약 16:1)로. 대안: `?colorPrimary` 유지 + 3:1 미만 테마 색 편차 등록.
- **FB-C2 (반영 — HLD v4.9 3.9, 같은 폴더 안내는 #8 대화상자 본문 글자로 분리) HLD 3.9 "기존 툴바 부제목 글자 스타일"**: 툴바 부제목 스타일 `TextAppearance.AppCompat.Widget.ActionBar.Subtitle.Small`은 12dp 고정(`res/values/styles.xml:18-20`)이라 글꼴 크기 설정을 따르지 않는다. LLD는 같은 보조 글자 색(`?android:textColorSecondary`)에 sp 크기인 목록 보조 글자 관용구(`?textAppearanceListItemSecondary`)를 쓴다(2.5). HLD 문구를 "보조 글자 색·sp 크기"로 바꾸기를 권한다.
- **FB-C3 (반영 — HLD v4.9 K9, 이름은 LLD가 정함) HLD K9 목록**: K7 (1)의 보조 글자 "창 M: <반대쪽 경로>" 형식과 K7 (2)의 활성 창 보이는 글자 형식이 K9 목록에 없다. 2.6의 8개로 보완하기를 권한다("활성"은 형식 안에 넣음).
- **FB-C4 (반영 — HLD v4.9 3.9) HLD 3.9 전환 뒤 초점**: HLD는 접근성 초점만 적었다. LLD는 키보드로 실행했을 때 입력 초점도 새 컨트롤로 옮긴다(2.3 `focusPaneIndicator`, `Keyboard_Navigation`). HLD 3.9 "키보드·마우스"에 한 문장 더하기를 권한다.
- **결정 기록(LLD 재량, HLD가 맡긴 것)**: 터치 대상은 `minWidth`/`minHeight` 48dp(2.5), `panePaths` API 모양과 `null` 뜻(2.1·4장), 창 표시 갱신을 한 메시지 미룸(2.3 — 눌림 상태 고착 방지), 단추 역할은 누를 수 있을 때만(2.3 위임), 선 두께 3dp·비활성 창은 자리만 둠(2.3), 반대쪽 경로 가운데 줄임(2.5), 비활성 창 speed dial·붙여넣기 막대는 숨기지 않음(R6, 7장).

## 7. 위험 처리 (HLD 6장)
| ID | 이 단계 처리 |
|---|---|
| R6 | 숨기지 않는다(요구 REQ-2-13대로 두 창 모두 표시). 활성 표시(선 + "활성")가 생겨 어느 창의 것인지 구분된다. 붐빔 조정은 요구 변경이 아닌 범위의 표시 조정이라 필요하면 별도 이슈. |
| R10 | 창 표시 컨트롤 경로 구현(2.3·2.4), T-C05(e)·T-C03. 목록 빈 곳처럼 클릭 동작이 없는 요소는 여전히 활성화되지 않는다 — 컨트롤이 대체 경로(HLD R10대로). |
| 이 LLD의 위험 C-1 | 전환 뒤 접근성 초점 이동이 TalkBack 판에 따라 레이아웃 직후에도 실패할 수 있다(HLD 3.9 "LLD에서 TalkBack 시험으로 확인"). 대응: T-C03에서 확인, 실패하면 `post`를 한 번 더 미루는 것이 아니라 새 컨트롤의 `ViewTreeObserver.OnGlobalLayoutListener` 한 번으로 바꾸는 안을 설계 문의로 올린다. 에뮬레이터에서 TalkBack 조작이 안 되면 미확인으로 보고. |
| 이 LLD의 위험 C-2 | 앱바가 48dp(+3dp) 높아져 작은 화면에서 목록이 줄어든다(0.3). 요구된 표시의 결과이며 터치 대상 48dp 필수 조항 때문에 줄일 수 없다. |
| R1~R5·R8·R9·R11 | #5·#6·#8에서 처리(이 단계와 무관). |

## 8. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-01 | v1 | 최초 작성 | 이슈 #7 LLD 단계(`panePaths` 소유·#9 API 포함) | PM |
| 2026-11-01 | v1.1 | FB-C1~C4 결과 반영: 근거 HLD v4.9, 선 색 `?colorControlNormal`(2.5 XML·선 색 설명, 3장), `styles.xml` 줄 정정(`:61-62`), T-C09에 검은 밤 모드·노랑 테마, 6장 결과 | 설계팀 회신(HLD v4.9, 2edec99) | 설계팀 |
