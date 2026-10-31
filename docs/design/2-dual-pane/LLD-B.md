# LLD-B — 두 창 컨테이너와 제스처 (이슈 #6)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중(v1.2 — PM 결정 반영) |
| 근거 | SRS v4.7, HLD v4.7 (`docs/design/2-dual-pane/`), LLD-A v1.4, `docs/STANDARDS_TRACEABILITY.md`(develop) |
| 기준 코드 | `origin/develop` a76055e (#5 머지 PR #15). 인용 줄 번호는 이 커밋 기준 |
| 이슈 | #6 (하위 이슈 B, 상위 #2). #5에서 미룬 결정 F1(툴바 `MenuHost`)·F3(격자 열 수)을 포함 |
| HLD 구성요소 | K3(나머지), K4(신규), K2·K5·K1(이 단계 몫) |
| 추적 REQ | REQ-2-02~07, 10, 11 (+ 창 2가 생기며 함께 성립시키는 REQ-2-01·09·18·19의 창 2 부분) |

## 0. 범위와 전제

### 0.1 이 단계가 하는 것 / 하지 않는 것
- **한다**
  - K4 `DualPaneLayout`(신규): 창 컨테이너 둘, 너비 판단(600dp, 고정 메뉴는 목표 상태로 차감), 한 창/두 창 배치, 오른쪽→왼쪽 쓸기 인식과 충돌 회피, 탭·길게 누르기·접근성 클릭으로 활성 창 지정, RTL 반전, 창별 인셋 분배.
  - K3 `DualPaneViewModel` 나머지: `activePaneLiveData`·`setActivePane`·`togglePane()`·`isDualPaneEnabled`·`paneModeLiveData`.
  - K2 `FileListHostFragment`: 선택 모드가 아니면 창 2를 만든다. 뒤로 가기 전달·탐색 서랍 경로 관찰이 **활성 창을 따라간다**. 검색 펼침 뒤로 가기 콜백(F1 ④)과 메뉴 키 전달(F1 ③)을 더한다.
  - K5 `FileListFragment`: **F1** 지원 액션바(`setSupportActionBar`) + 옵션 메뉴 → 툴바 `MenuHost`(HLD D2, 동등성 ①~⑥은 3장). **F3** 두 창 모드의 격자 열 수를 창 너비로. 인자에 창 번호.
  - K1 `FileListActivity`: 메뉴 키를 호스트로(F1 ③).
- **하지 않는다**: 창 표시 줄·활성 창 표시·창 표시 누르기 전환·전환 뒤 접근성 초점 이동(K7·K9, #7), 확인 창(K6, #8), 반대쪽 복사·이동(#9). K3 `panePaths`(반대쪽 경로 게시판)는 **#7이 소유**한다(PM 결정 2026-10-29, #9가 그것을 쓴다) — 이 단계에는 읽는 곳이 없다(FB-4).
- 이 단계가 끝나면 두 창 모드에서 **어느 창이 활성인지 보이는 표시가 아직 없다**(K7은 #7). develop 안의 중간 상태이며 릴리스는 #7까지 묶어서 한다(PROCESS_ROLES 12장 "묶음을 끝낸 뒤 릴리스").

### 0.2 추적 — 이 단계의 몫
| REQ | 이 단계의 몫 | 확인 |
|---|---|---|
| REQ-2-01 | 창 2 생성(선택 모드 제외, A1) | T-B01, T-B02 |
| REQ-2-02 | 창마다 `FileListViewModel`(기존, 프래그먼트 범위). 창 2가 생기며 실제로 성립 | T-B03 |
| REQ-2-03 | K4 너비 판단(2.3 `isDualPaneWidth`, `onMeasure`) | U-01~U-05, T-B04 |
| REQ-2-04 | K4가 측정마다 재판단, 고정 메뉴 열림 설정 변경 시 K2 → K4 재측정, 숨은 창은 `GONE`만(D8) | T-B05, T-B06 |
| REQ-2-05 | 한 창 모드에서 K4는 `activePane`만 보인다 → 모드 전환에도 활성 창이 그대로 | T-B07 |
| REQ-2-06 | 한 창 모드 쓸기 → K3 `togglePane()` | T-B08 |
| REQ-2-07 | 두 창 모드 쓸기 → `togglePane()`, 탭·길게 누르기·접근성 클릭 → `setActivePane(i)` | T-B09, T-B10, T-B11 |
| REQ-2-09 (창 2 부분) | 탐색 서랍·키보드 단축키·메뉴 키·뒤로 가기가 **활성 창**을 따라감. 툴바 메뉴는 창마다(D2) | T-B12, T-B13 |
| REQ-2-10 | 서랍 제스처가 K4보다 바깥에서 먼저 처리됨(2.3, R1 정적 확인) | T-B14 |
| REQ-2-11 | 쓸기 제외 영역 (a)~(e) | T-B15 |
| REQ-2-18·19 (창 2 부분) | 창 2는 빈 `Intent`로 → 기본 디렉터리, 창 1만 Intent 경로 | T-B16 |

### 0.3 사용자에게 보이는 변화
- **의도된 변화**(요구사항): 두 창 모드, 쓸기 전환, 탭 활성화. 창 2의 붙여넣기 막대·speed dial도 창마다 보인다(HLD R6 — 이 단계는 숨기지 않는다, 7장).
- **F1(툴바 `MenuHost`)**: 3장의 ①~⑥을 유지한다 — 한 창 모드의 툴바 모양·메뉴·단축키·메뉴 키·홈 단추·제목은 기준 APK와 같아야 한다. 단 **하나의 경계 경우**가 달라질 수 있다: API 33 이상에서 검색을 펼친 채 **열고 닫는 서랍을 연 뒤** 뒤로 가기를 누르면, 지금은 그 순간의 콜백 등록 순서에 따라 서랍이 먼저 닫히기도 하고 검색이 먼저 접히기도 한다(4.1). 이 LLD는 API 33 미만의 지금 순서(검색 접기 먼저)로 통일한다 — **PM 결정(2026-10-29, FB-3)**.
- **F3(격자 열 수)**: 한 창 모드는 지금 공식 그대로라 변화 없음. 두 창 모드만 창 너비로 계산(2.5 `updateSpanCount`).

### 0.4 #5에서 미룬 결정의 처리 요약
| 결정 | 이 LLD의 처리 | 절 |
|---|---|---|
| F1 툴바 `MenuHost`(HLD D2·K5, 동등성 ①~⑥) | 창 툴바에 `MenuProvider`를 붙이고 지원 액션바를 쓰지 않는다. ①은 조치 불필요(지원 액션바가 스타일을 적용하지 않음 — 바이트코드 근거, FB-1), ②~⑥은 각각 대체 경로를 둔다 | 3장, 2.5 |
| F3 격자 열 수를 창 너비로(HLD K5) | 두 창 모드: K4가 정한 목표 창 너비로. 한 창 모드: 지금 공식 유지(LLD-A F3의 "한 창에서 열 수가 달라지는 화면" 회피) | 2.5 `updateSpanCount`, FB-5 |

## 1. 변경 파일 (R4 범위 공지 — PROCESS_ROLES 11-1, 5개 초과)

| # | 파일 | 신규/수정 | 요지 | 구성요소 |
|---|---|---|---|---|
| 1 | `app/src/main/java/me/zhanghai/android/files/filelist/DualPaneLayout.kt` | 신규 | 창 컨테이너 둘, 너비 판단, 쓸기·탭·길게 누르기·접근성 클릭, 인셋 분배, RTL | K4 |
| 2 | `app/src/main/java/me/zhanghai/android/files/filelist/DualPaneViewModel.kt` | 수정 | `activePaneLiveData`, `setActivePane`, `togglePane`, `isDualPaneEnabled`, `paneModeLiveData`, `PaneMode` | K3 |
| 3 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListHostFragment.kt` | 수정 | 창 2 생성, K4 연결, 활성 창 추종(뒤로 가기·서랍 경로), 검색 뒤로 가기 콜백, 메뉴 키 | K2 |
| 4 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt` | 수정 | F1 `MenuHost` 전환, F3 열 수, `Args.paneIndex`, 공개 멤버(`currentPathLiveData`, `searchViewExpandedLiveData`, `collapseSearchView`, `onMenuKeyEvent`) | K5 |
| 5 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListActivity.kt` | 수정 | `dispatchKeyEvent` — 메뉴 키를 호스트로(F1 ③) | K1 |
| 6 | `app/src/main/res/layout/file_list_host_fragment_include.xml` | 수정 | `paneContainer` `FrameLayout` → `DualPaneLayout` > `paneContainer` + `pane2Container` | K2·K4 |
| 7 | `app/src/main/res/layout-sw600dp-land/file_list_host_fragment_include.xml` | 수정 | 위와 같음(고정 메뉴 변형) | K2·K4 |
| 8 | `app/build.gradle` | 수정 | 두 번째 `dependencies` 블록(`app/build.gradle:121`~)에 `testImplementation 'junit:junit:4.13.2'` 한 줄(FB-6, 오너 승인 2026-10-29) | 시험 |
| 9 | `app/src/test/java/me/zhanghai/android/files/filelist/DualPaneLayoutTest.kt` | 신규 | U-01~U-05(5.1) — 저장소의 첫 단위 테스트 소스 | 시험 |

- 합계 9개(신규 2, 수정 7): 앱 Kotlin 5, 레이아웃 XML 2, `app/build.gradle` 1, 시험 Kotlin 1. `provider/`·`filejob/`·JNI는 건드리지 않는다(SRS C-1). `app/build.gradle`은 시험 의존성 한 줄뿐(앱 런타임 의존성·설정 변경 없음, PROCESS_ROLES 4장상 검수 필수 파일). 새 권한·설정 키·문자열 없음.
- 창 1 컨테이너 ID는 `R.id.paneContainer`를 그대로 쓴다(이전 판의 저장 상태 복원과 `FileListHostFragment.kt:115`의 조회가 같은 ID를 본다). 창 2는 `R.id.pane2Container`.
- 바꾸지 않지만 관계있는 것: `res/layout/file_list_fragment*.xml`(창 레이아웃 — `R.id.appBarLayout`·`R.id.recyclerView` ID를 K4가 찾는다), `ui/IgnoreFitsSystemWindowsFullyDraggableDrawerContentLayout.kt`, `ui/PersistentDrawerLayout.kt`, `navigation/NavigationFragment.kt`.

## 2. 함수 명세

표기: "기존 `F:N`"은 a76055e의 줄. `F` = `FileListFragment.kt`, `H` = `FileListHostFragment.kt`, `A` = `FileListActivity.kt`, `V` = `DualPaneViewModel.kt`.

### 2.1 K1 `FileListActivity`

#### `FileListActivity.dispatchKeyEvent(event: KeyEvent): Boolean` (신규 override)
- **추적**: HLD K1·D2 ③ / REQ-2-09
- **책임**: 메뉴 키(`KEYCODE_MENU`)를 활성 창 툴바의 넘침 메뉴로 보낸다(지원 액션바가 하던 일).
- **입력·전제**: 메인 스레드. `fragment`(lateinit, `A:24`)는 `onCreate`에서 정해진다.
- **출력·사후조건**: 메뉴 키면 `fragment.onMenuKeyEvent(event)`가 `true`일 때 `true`. 그 밖의 키·`false`면 `super.dispatchKeyEvent(event)`.
- **오류**: `fragment`가 아직 초기화되지 않았으면(이론상 `onCreate` 전) `super`로 넘긴다(`this::fragment.isInitialized` 검사).
- **알고리즘**: `if (event.keyCode == KEYCODE_MENU && this::fragment.isInitialized && fragment.onMenuKeyEvent(event)) return true; return super.dispatchKeyEvent(event)`.
- **기존 코드와의 관계**: 지금은 `AppCompatActivity.dispatchKeyEvent`가 지원 액션바에 `onMenuKeyEvent`를 묻는다(appcompat 1.7.1: `KEYCODE_MENU`이고 `getSupportActionBar() != null`이면 `ActionBar.onMenuKeyEvent`). 지원 액션바가 없어지므로 같은 자리(액티비티 `dispatchKeyEvent`, `super` 앞)에서 처리한다. `onKeyShortcut`(`A:58-63`)은 바꾸지 않는다.

### 2.2 K3 `DualPaneViewModel` (수정, 기존 `V:1-25`)

#### `data class PaneMode(val isDualPane: Boolean, val paneWidthDp: Int)` (신규, 같은 파일)
- **추적**: HLD K3 `paneMode` / REQ-2-03·04
- **책임**: K4의 판단 결과. `paneWidthDp`는 **목표 상태 기준** 창 하나의 너비(dp, 내림) — 두 창이면 W/2, 한 창이면 W(2.3 `onMeasure` 6).
- **처음 값**: `PaneMode(false, 0)` — K4의 첫 측정 전.

#### 필드·함수
| 멤버 | 명세 |
|---|---|
| `private val _activePaneLiveData = MutableLiveData(0)` / `val activePaneLiveData: LiveData<Int>` (공개로 바꿈) / `val activePane: Int` (기존) | 처음 0(창 1, REQ-2-09·A8 ④). 값은 0 또는 1. |
| `var isDualPaneEnabled: Boolean = false` | HLD K3: 저장하지 않는 값. K2가 `onActivityCreated`마다 Intent에서 계산해 넣는다(2.4). 프로세스 종료 뒤에도 Intent는 남으므로 다시 맞게 계산된다. |
| `fun setActivePane(index: Int)` | **전제**: 메인 스레드. **사후**: `index !in 0..1`이거나 (`!isDualPaneEnabled && index != 0`)이면 아무것도 하지 않는다. 지금 값과 같으면 아무것도 하지 않는다(관찰자 재호출 방지). 아니면 `_activePaneLiveData.value = index`. **오류**: 없음(잘못된 값은 무시 — K4가 창 밖 좌표를 넘길 수 있음). |
| `fun togglePane()` | `if (!isDualPaneEnabled) return; setActivePane(1 - activePane)`. 쓸기 확정(K4)과 #7의 창 표시 누르기가 함께 쓴다(HLD K3). |
| `private val _paneModeLiveData = MutableLiveData(PaneMode(false, 0))` / `val paneModeLiveData: LiveData<PaneMode>` / `val paneMode: PaneMode` | |
| `fun setPaneMode(mode: PaneMode)` | 같은 값이면 무시, 아니면 게시. |
| `isStorageAccessRequested`, `isNotificationPermissionRequested` | 그대로(`V:12-24`). |

- **기존 코드와의 관계**: LLD-A 2.2가 만든 기본형을 넓힌다. `activePane` 게터(`V:9-10`)는 호스트 `activePane`(`H:89-90`)가 그대로 쓴다.

### 2.3 K4 `DualPaneLayout` (신규, `filelist/DualPaneLayout.kt`)

#### 클래스 선언
```kotlin
class DualPaneLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr)
```
- **추적**: HLD K4·3.2·3.3·3.4·D3·D4·D5·D8 / REQ-2-03~07, 10, 11
- 자식: XML의 자식 둘(`R.id.paneContainer`, `R.id.pane2Container` — 각각 `FrameLayout`). 자식 수가 2가 아니면 `onFinishInflate`에서 `check(childCount == 2)`로 실패(개발 중 레이아웃 실수 검출).
- 상태(뷰 필드, 저장하지 않음 — 활성 창의 진실원은 K3):

| 필드 | 뜻 |
|---|---|
| `var listener: Listener?` | 2.3 `Listener` |
| `var isSwipeEnabled: Boolean = false` | K2가 `isDualPaneEnabled`로 넣는다. `false`면 쓸기·두 창 모드 모두 없음(제외 (e), 선택 모드). |
| `var activePane: Int = 0` (setter) | K2가 K3 관찰로 넣는다. 한 창 모드면 가시성을 바꾸고 `requestLayout()`. |
| `var persistentDrawerView: View?` | 고정 메뉴 화면에서만 K2가 넣는 서랍 뷰(`R.id.navigationFragment`). 열고 닫는 서랍 화면은 `null`. |
| `var isPersistentDrawerOpen: Boolean` (setter) | 고정 메뉴의 **목표** 상태(`Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN`). 바뀌면 `requestLayout()`(재판단, REQ-2-04). |
| `private var isDualPane = false` | 지금 모드 |
| `private var reportedMode: PaneMode?` | 마지막으로 알린 값 |
| `private var lastInsets: WindowInsets?` | 2.3 `dispatchApplyWindowInsets` |
| 제스처 상태 `downX`, `downY`, `downPane`, `activePointerId`, `swipeState`(`IDLE`/`EXCLUDED`/`IGNORED`/`DRAGGING`), `isTap`, `isDisallowIntercept`, `velocityTracker` | 2.3 제스처 함수들 |
| 상수: `touchSlop = ViewConfiguration.get(context).scaledTouchSlop`, `minFlingVelocity = scaledMinimumFlingVelocity`, `swipeMinDistance = dpToDimensionPixelSize(SWIPE_MIN_DISTANCE_DP)`, `fastScrollTouchWidth = dpToDimensionPixelSize(FAST_SCROLL_TOUCH_WIDTH_DP)` | |

#### `DualPaneLayout.Listener` (중첩 인터페이스)
```kotlin
interface Listener {
    fun onPaneModeChanged(mode: PaneMode)
    fun onSwipeToTogglePane()
    fun onPaneActivated(index: Int)
}
```
- K2가 구현해 K3에 넘긴다(2.4). K4는 뷰모델을 모른다(뷰 → 프래그먼트 → 뷰모델).

#### `companion object` 상수와 파일 수준 순수 함수
- `const val DUAL_PANE_MIN_WIDTH_DP = 600` (REQ-2-03, WSC Compact/Medium 경계)
- `const val SWIPE_MIN_DISTANCE_DP = 48` — 확정 거리(HLD 3.3-3 "기준 거리 값은 LLD"). 근거: 48dp 터치 대상 크기와 같은 크기로, 슬롭(약 8dp)을 넘겨 가로채기 시작한 뒤 의도한 쓸기로 볼 만큼 충분하고 화면 폭(최소 약 320dp)의 1/6 미만이라 짧은 쓸기도 된다.
- `const val FAST_SCROLL_TOUCH_WIDTH_DP = 48` — 제외 (c) 띠 너비. 근거: fastscroll 1.3.0의 `afs_min_touch_target_size` = 48dp(라이브러리 `res/values/values.xml`), `FastScroller.isInViewTouchTarget`이 엄지·트랙 뷰를 이 최소 크기로 넓혀 판정(R2 닫음, 7장).

- 두 순수 함수는 클래스 밖 **파일 수준** `internal fun`으로 둔다(JVM 클래스 `DualPaneLayoutKt`). 단위 테스트가 `ViewGroup`을 상속한 `DualPaneLayout` 클래스를 불러오지 않고 호출할 수 있게 하기 위해서다(로컬 JVM 시험에는 Android 프레임워크 구현이 없다). 상수는 `const`라 컴파일 때 값이 박혀 클래스 로드가 없다.

##### `internal fun isDualPaneWidth(availableWidthPx: Int, density: Float): Boolean` (`DualPaneLayout.kt` 파일 수준, 순수)
- **추적**: HLD 3.2 판단 규칙 / REQ-2-03
- **입력·전제**: `availableWidthPx ≥ 0`, `density > 0`.
- **출력**: `availableWidthPx / density >= DUAL_PANE_MIN_WIDTH_DP` (부동소수 비교 — 600dp 정확히는 두 창).
- **오류**: 없음.
- **테스트**: U-01·U-02.

##### `internal fun computeAvailableWidthPx(ownWidthPx: Int, parentWidthPx: Int, persistentDrawerRangePx: Int?, isPersistentDrawerOpen: Boolean): Int` (`DualPaneLayout.kt` 파일 수준, 순수)
- **추적**: HLD 3.2 W, D3 / REQ-2-03
- **입력**: `persistentDrawerRangePx` = 고정 메뉴 화면이면 서랍 뷰의 `measuredWidth + 좌우 margin`, 아니면 `null`.
- **출력**: `null`이면 `ownWidthPx`(열고 닫는 서랍은 내용 위에 겹치므로 빼지 않음). 아니면 `parentWidthPx - (if (isPersistentDrawerOpen) persistentDrawerRangePx else 0)`, 0 아래로 내려가지 않게 `coerceAtLeast(0)`.
- **이유**: K4의 자기 너비는 `PersistentDrawerLayout`이 서랍 애니메이션 중 매 프레임 줄인다(`ui/PersistentDrawerLayout.kt:169-200`). 부모 전체 너비와 **목표** 상태를 쓰면 애니메이션 중 모드가 흔들리지 않는다(HLD 3.2). 시스템 인셋은 빼지 않는다 — 창 너비 등급(WSC-1)이 인셋을 포함한 앱 창 너비로 정해지는 것과 맞춘다.
- **테스트**: U-03~U-05.

#### `DualPaneLayout.onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)`
- **추적**: HLD 3.2·D3·D8 / REQ-2-03·04·05
- **입력·전제**: 부모(`FullDraggableContainer` 또는 `PersistentDrawerLayout`)가 `EXACTLY`로 잰다. 고정 메뉴 화면에서는 부모가 서랍 뷰를 먼저 잰 뒤 내용(K4)을 잰다(`ui/PersistentDrawerLayout.kt:111-166` → `measureContentViews` `:169-200`) — 그래서 `persistentDrawerView.measuredWidth`가 이미 있다.
- **출력·사후조건**: 자기 크기 = spec 크기. 두 창이면 두 자식 `VISIBLE`, 시작 쪽 자식 너비 `width / 2`, 끝 쪽 `width - width / 2`, 높이 전체. 한 창이면 `activePane` 자식만 `VISIBLE`·전체 크기, 다른 자식 `GONE`(재지 않음). 모드가 바뀌었으면 인셋을 다시 나누고(2.3 `dispatchChildInsets`) `listener.onPaneModeChanged`를 **`post`로** 알린다.
- **오류**: 없음. `listener`가 없으면 알리지 않는다.
- **알고리즘**:
  1. `width = MeasureSpec.getSize(widthMeasureSpec)`, `height = MeasureSpec.getSize(heightMeasureSpec)`, `setMeasuredDimension(width, height)`.
  2. `drawerRange = persistentDrawerView?.let { it.measuredWidth + it.marginLeft + it.marginRight }`, `parentWidth = (parent as? View)?.measuredWidth ?: width`.
  3. `available = computeAvailableWidthPx(width, parentWidth, drawerRange, isPersistentDrawerOpen)`.
  4. `newIsDualPane = isSwipeEnabled && isDualPaneWidth(available, density)` (선택 모드는 늘 한 창, A1).
  5. `modeChanged = newIsDualPane != isDualPane`; `isDualPane = newIsDualPane`. `applyChildVisibility()`를 **매번** 부른다(두 창: 둘 다 `VISIBLE` / 한 창: `activePane`만 `VISIBLE`, 다른 쪽 `GONE` — 이미 그 값이면 `setVisibility`를 부르지 않는다). XML에서 두 자식이 `VISIBLE`로 시작하므로 첫 측정에서도 한 창이면 숨은 창이 `GONE`이 된다. `modeChanged`이면 `lastInsets?.let { dispatchChildInsets(it) }`.
  6. `mode = PaneMode(isDualPane, ((if (isDualPane) available / 2 else available) / density).toInt())`. `mode != reportedMode`면 `reportedMode = mode` 후 `post { listener?.onPaneModeChanged(mode) }` — 측정 중에 관찰자가 뷰를 건드리지 않게 다음 메시지로 미룬다.
  7. 보이는 자식을 위 크기의 `EXACTLY` spec으로 `measure`.
- 가시성이 실제로 바뀌는 측정(첫 측정·모드 전환)에서는 `requestLayout`이 한 번 더 예약될 수 있다(무해, 한 프레임). 그 밖의 측정에서는 가시성 값이 같아 아무 일도 없다.
- **기존 코드와의 관계**: 지금 `R.id.paneContainer` `FrameLayout`(`res/layout/file_list_host_fragment_include.xml:17-20`) 자리를 대신한다.

#### `DualPaneLayout.onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int)`
- **추적**: HLD K4 (d) / REQ-2-03, SRS A2·`RTL-1`
- **알고리즘**: 두 창이면 `isRtl = isLayoutDirectionRtl`(`util/ViewExtensions.kt:80`), 창 1(인덱스 0)이 시작 쪽: LTR은 `[0, w1)`, RTL은 `[width - w1, width)`. 창 2는 남은 쪽. 한 창이면 보이는 자식을 `[0, width)`. `GONE` 자식은 배치하지 않는다.

#### `DualPaneLayout.dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets` (override)
- **추적**: HLD K4, LLD-A 2.6의 "창 컨테이너는 인셋을 소비하지 않는다" 유지
- **책임**: 시스템 창 인셋을 창마다 나눠 준다. 두 창이면 서로 맞닿은 쪽의 인셋을 0으로.
- **알고리즘**: `lastInsets = insets`; `dispatchChildInsets(insets)`; `return insets.consumeSystemWindowInsets()`(형제 없음, `ui/PersistentDrawerLayout.kt:39-68`와 같은 관용구).
- `private fun dispatchChildInsets(insets: WindowInsets)`: 한 창 모드 → 두 자식 모두 원래 `insets`(숨은 창도 미리 받아 둬 보이게 될 때 패딩이 맞음). 두 창 모드 → 왼쪽에 놓인 자식은 `replaceSystemWindowInsets(left, top, 0, bottom)`, 오른쪽 자식은 `(0, top, right, bottom)`(RTL이면 창 1이 오른쪽). `@Suppress("DEPRECATION")` — 기존 `PersistentDrawerLayout`·`PersistentBarLayout`과 같은 API.
- **이유**: API 30 미만의 `ViewGroup` 기본 분배는 첫 자식이 소비하면 다음 자식에 주지 않는다. 창 루트 아래 `PersistentBarLayout`이 소비하므로(`ui/PersistentBarLayout.kt:42-69`) 창 2가 인셋을 못 받는다 → 직접 나눈다. 고정 메뉴 화면에서는 부모가 측정마다 다시 넘긴다(`ui/PersistentDrawerLayout.kt:70-101`, `:165`).

#### 쓸기·탭 인식 (HLD 3.3·3.4·D4·D5)

공통 정의:
- **쓸기 방향**: LTR은 오른쪽→왼쪽(`dx < 0`), RTL은 왼쪽→오른쪽(`dx > 0`)(SRS A2). `swipeSign = if (isRtl) +1 else -1`, 방향 성분 `d = dx * swipeSign`(양수면 쓸기 방향).
- 첫 포인터만 본다. `ACTION_POINTER_DOWN`이 오면 이 제스처는 쓸기(`swipeState = IGNORED`, 가로채기 전이면)·탭(`isTap = false`) 모두 아니다. 이미 `DRAGGING`이면 첫 포인터로 계속.

##### `DualPaneLayout.dispatchTouchEvent(ev: MotionEvent): Boolean` (override)
- **추적**: HLD 3.4·D5 / REQ-2-07
- **책임**: 탭(슬롭 안의 DOWN→UP)과 길게 누르기로 활성 창을 정하고, 자식 전달 **전에** 활성화한다. 속도 추적.
- **알고리즘**:
  - `velocityTracker.addMovement(ev)`.
  - `ACTION_DOWN`: 상태 초기화(`swipeState = IDLE`, `isTap = true`, `isDisallowIntercept = false`), `downX/downY/activePointerId` 기록, `downPane = findPaneIndexAt(ev.x, ev.y)`(보이는 자식 중 좌표를 담은 것, 없으면 −1). `isDualPane && downPane >= 0 && downPane != activePane`이면 `postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout())`. 그다음 `swipeState = if (isExcludedFromSwipe(ev)) EXCLUDED else IDLE`.
  - `ACTION_MOVE`: 첫 포인터의 `|dx|` 또는 `|dy|`가 `touchSlop`을 넘으면 `isTap = false`, `removeCallbacks(longPressRunnable)`.
  - `ACTION_UP`: `removeCallbacks(longPressRunnable)`. `isTap && swipeState != DRAGGING && isDualPane && downPane >= 0`이면 **`super.dispatchTouchEvent(ev)` 전에** `listener?.onPaneActivated(downPane)`(K3에서 같은 값이면 무시).
  - `ACTION_CANCEL`: `removeCallbacks(longPressRunnable)`, `isTap = false`.
  - `return super.dispatchTouchEvent(ev)`. UP·CANCEL 뒤에 `velocityTracker.clear()`.
- `longPressRunnable`: `isTap`이 아직 참이면 `swipeState = IGNORED`(길게 누른 뒤 끌어도 쓸기로 보지 않음), `listener?.onPaneActivated(downPane)`.
- **길게 누르기 결정**(HLD 3.4가 LLD에 맡긴 것): 비활성 창에서 슬롭 안에 머문 채 길게 누르기 시간이 지나면 **그 창을 활성으로 한다**. 이유: 길게 누르기는 선택을 시작하고(`FileListAdapter.kt:246-252`), 그 선택에 작용하는 뒤로 가기·키보드 단축키·#9 반대쪽 명령은 활성 창 기준이다 — 선택이 비활성 창에 생기면 뒤로 가기로 선택을 풀 수 없다. K4는 DOWN을 받은 `dispatchTouchEvent`에서 **`super.dispatchTouchEvent(ev)`를 부르기 전에** `postDelayed(longPressRunnable, longPressTimeout)`을 건다. 자식 뷰의 길게 누르기 판정(`View`의 길게 누르기 확인 콜백)은 그 뒤 `super` 안에서 같은 지연 이상으로 걸린다(스크롤 컨테이너 안이면 누름 확인 지연 뒤에 건다). 같은 메인 루퍼에서 만기 시각이 같거나 늦고 먼저 걸린 메시지가 먼저 실행되므로 K4의 활성화가 항상 먼저다 → 선택은 이미 활성인 창에서 시작된다. 구현은 이 호출 순서(`postDelayed` → `super`)를 지켜야 한다. 접근성 서비스는 터치 없이 클릭을 보내므로 이 경로와 무관(아래 `onRequestSendAccessibilityEvent`).
- **기존 코드와의 관계**: 자식 클릭 처리는 그대로(`super`). 탭에서 활성화한 뒤 UP이 넘어가므로 그 UP으로 실행되는 클릭(파일 열기, 툴바 메뉴)은 이미 활성인 창에서 실행된다(HLD 3.4).

##### `DualPaneLayout.onInterceptTouchEvent(ev: MotionEvent): Boolean` (override)
- **추적**: HLD 3.3-1·2·4·D4 / REQ-2-06·07·11
- **알고리즘**: `isDisallowIntercept`이면 `false`. `swipeState`가 `EXCLUDED`·`IGNORED`면 `false`. `ACTION_MOVE`면 `return updateSwipe(ev)`(가로채기 시작이면 `true`). 그 밖 `false`.

##### `DualPaneLayout.onTouchEvent(ev: MotionEvent): Boolean` (override)
- **추적**: HLD 3.3-5
- **알고리즘**:
  - `ACTION_DOWN`(자식이 DOWN을 소비하지 않아 여기로 온 경우 — 빈 목록·오류 글자·여백): `return isSwipeEnabled && swipeState == IDLE` — 받아야 이후 MOVE가 온다. 제외면 `false`(부모 `FullDraggableContainer`가 자기 `onTouchEvent`로 받을 수 있게).
  - `ACTION_MOVE`: `updateSwipe(ev)`; `true`.
  - `ACTION_UP`: `swipeState == DRAGGING`이면 `finishSwipe(ev)`. `swipeState = IDLE`. `true`.
  - `ACTION_CANCEL`: `swipeState = IDLE`, `true`.
- 두 경로(`onInterceptTouchEvent`, `onTouchEvent`)는 같은 상태 필드와 `updateSwipe`를 쓴다(HLD 3.3-5 "하나의 인식기 상태").

##### `private fun updateSwipe(ev: MotionEvent): Boolean`
- **추적**: HLD 3.3-2, SRS 3.4
- **알고리즘**: `swipeState != IDLE`이면 `swipeState == DRAGGING` 반환. `dx = x - downX`, `dy = y - downY`(첫 포인터). `|dy| > touchSlop && |dy| >= |dx|` → `swipeState = IGNORED`, `false`(세로가 먼저 — 이 제스처 끝까지 무시, 세로 스크롤 지연 없음). `|dx| > touchSlop && |dx| > |dy|` → 방향 성분 `d > 0`이면 `swipeState = DRAGGING`, `parent?.requestDisallowInterceptTouchEvent(true)`는 **부르지 않는다**(서랍 컨테이너의 판단을 막지 않음 — 방향이 반대라 겹치지 않음), `true`. `d <= 0`(반대 방향 — 열고 닫는 서랍 열기 쪽)이면 `swipeState = IGNORED`, `false`. 그 밖 `false`.
- 가로채면 자식은 `ACTION_CANCEL`을 받아 누름·세로 스크롤이 일어나지 않는다(표준 동작).

##### `private fun finishSwipe(ev: MotionEvent)`
- **추적**: HLD 3.3-3 / REQ-2-06·07
- **알고리즘**: `d = (x - downX) * swipeSign`. `velocityTracker.computeCurrentVelocity(1000)`, `v = xVelocity(activePointerId) * swipeSign`. `d >= swipeMinDistance || (d > touchSlop && v >= minFlingVelocity)`면 `listener?.onSwipeToTogglePane()`. 아니면 아무것도 하지 않는다(쓸기 취소).
- 끄는 동안 창이 따라 움직이는 시각 피드백은 두지 않는다(SRS·HLD에 요구 없음, 전환은 손을 뗄 때 한 번).

##### `override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean)`
- **추적**: HLD 3.3-4
- **알고리즘**: `if (disallowIntercept && swipeState != DRAGGING) swipeState = EXCLUDED`; `isDisallowIntercept = disallowIntercept`; `super.requestDisallowInterceptTouchEvent(disallowIntercept)`. fastscroll 끌기 시작(`FastScroller.setDragging(true)` → 부모에 `requestDisallowInterceptTouchEvent(true)`, fastscroll 1.3.0 바이트코드)과 breadcrumb `HorizontalScrollView` 스크롤의 2차 방어.

##### `private fun isExcludedFromSwipe(ev: MotionEvent): Boolean` (DOWN에서 1회)
- **추적**: HLD 3.3-1 (a)~(e) / REQ-2-11, `Back_Gesture_Nav`·`GN-1`·`GN-3`
- **알고리즘**(하나라도 참이면 제외):
  - (e) `!isSwipeEnabled`.
  - (a) `isInSystemGestureArea(ev)`.
  - (b) 손가락 아래 보이는 창의 `R.id.appBarLayout` 영역 안: 창 컨테이너에서 `findViewById<View>(R.id.appBarLayout)`, `Rect(0, 0, w, h)`를 `offsetDescendantRectToMyCoords(view, rect)`로 K4 좌표로 바꿔 `contains`. 툴바·선택 툴바·breadcrumb와 #7의 창 표시 줄이 모두 이 안이다(`res/layout/file_list_fragment_app_bar_include.xml:13-56`).
  - (c) 같은 창의 `R.id.recyclerView` 영역 중 끝(end) 가장자리 `fastScrollTouchWidth` 띠 안(LTR 오른쪽 띠, RTL 왼쪽 띠 — fastscroll은 `AutoMirrorDrawable`로 RTL에서 왼쪽). 목록이 비어 fastscroll이 안 보여도 띠는 뺀다(손해는 띠만큼 쓸기 영역이 줄어드는 것뿐).
  - (d) `canChildScrollHorizontally(ev.x, ev.y, direction = -swipeSign)`: 좌표 아래 자손을 위에서 아래로(자식은 나중 것부터) 따라가며, **`ViewGroup`인 자손**(실제 가로 스크롤 컨테이너 — `HorizontalScrollView`·`RecyclerView`·`ViewPager` 계열) 중 하나라도 `canScrollHorizontally(direction)`이면 참. `TextView`(마키 한 줄 파일 이름 `file_item_list.xml:84`·`FileListAdapter.kt:228-231`, `EditText` 포함)처럼 `ViewGroup`이 아닌 뷰는 보지 않는다 — 마키 글자 위에서도 쓸기가 되게(검수 1회차 `[판단]` 1, PM 결정 2026-10-29). 쓸기에서 손가락이 `swipeSign` 쪽으로 움직이면 내용은 반대쪽으로 스크롤하므로 `-swipeSign`. `FullDraggableHelper.canNestedViewScroll`(drawer 1.0.3)과 같은 방식.
- 좌표 변환 비용은 DOWN 1회뿐.

##### `private fun isInSystemGestureArea(ev: MotionEvent): Boolean`
- **추적**: HLD 3.3-1(a) / REQ-2-11(a)
- **알고리즘**: `Build.VERSION.SDK_INT < Q`면 `false`(시스템 가장자리 제스처 없음, SRS 3.1). 아니면 `insets = ViewCompat.getRootWindowInsets(this)?.getInsets(WindowInsetsCompat.Type.systemGestures()) ?: return false`, `getLocationInWindow(loc)`, `xw = ev.x + loc[0]`, `yw = ev.y + loc[1]`, `root = rootView`. `xw < insets.left || xw >= root.width - insets.right || yw >= root.height - insets.bottom`면 참. 필수 제스처 영역(`getMandatorySystemGestureInsets`, GN-3)은 이 영역의 부분이라 따로 보지 않는다(HLD 3.3-1(a)).
- **HLD와 다른 점**: HLD는 "API 29 미만에서는 `WindowInsetsCompat.getInsets(Type.systemGestures())`가 0을 돌려주므로 버전 분기 없이"라고 적었으나, androidx.core 1.16.0의 `WindowInsetsCompat$Impl.getSystemGestureInsets()`는 API 29 미만에서 **시스템 창 인셋을 돌려준다**(바이트코드: `getSystemWindowInsets()` 호출). 그래서 명시적 버전 분기로 HLD의 의도(29 미만은 제외 영역 없음)를 지킨다 → 설계 피드백 FB-2.
- 인셋을 `onApplyWindowInsets`로 저장해 두는 대신 DOWN 때 루트 인셋을 읽는다 — 창 기준 값이 그대로라 고정 메뉴 화면에서 부모가 바꾼 시스템 창 인셋의 영향을 받지 않고, 회전·크기 변경 뒤 값도 늘 최신이다(HLD 3.3-1(a)이 "방식은 LLD"로 남긴 좌표 변환).

##### `override fun onRequestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean`
- **추적**: HLD K4 (c)·3.9 / REQ-2-07
- **알고리즘**: `event.eventType == TYPE_VIEW_CLICKED && isDualPane`이면 `index = indexOfChild(child)`(K4의 직속 자식 = 창 컨테이너)로 `listener?.onPaneActivated(index)`. `return super.onRequestSendAccessibilityEvent(child, event)`.
- 접근성 서비스가 켜져 있을 때만 불린다(`ViewGroup.requestSendAccessibilityEvent`). 클릭 처리 뒤에 오지만 툴바 메뉴는 창마다라(D2) 결과가 같다(HLD 3.9, R10). 접근성이 켜진 채 손으로 누르면 탭 경로와 겹쳐 두 번 불리지만 같은 값이라 K3에서 무시된다.

##### `override fun shouldDelayChildPressedState(): Boolean = false`
- 기존 `paneContainer`가 `FrameLayout`(같은 값 `false`)이었으므로 자식의 눌림 표시·길게 누르기 시점이 바뀌지 않게 한다(`ViewGroup` 기본값 `true`면 스크롤 컨테이너로 취급돼 눌림이 늦어진다).

##### `override fun onDetachedFromWindow()`
- `removeCallbacks(longPressRunnable)`, `velocityTracker.recycle()` 후 `super`. (트래커는 `onAttachedToWindow`에서 `VelocityTracker.obtain()`.)

### 2.4 K2 `FileListHostFragment` (수정)

#### `onActivityCreated(savedInstanceState: Bundle?)` (수정, 기존 `H:101-145`)
- **추적**: HLD K2·3.5·3.7 / REQ-2-01·04·09·18·19
- **알고리즘**(바뀌는 단계만, 나머지 순서는 그대로):
  1. `val isDualPaneEnabled = !isPickIntent(args.intent)`; `viewModel.isDualPaneEnabled = isDualPaneEnabled`.
  2. 새로 시작(`savedInstanceState == null`): `pane1 = FileListFragment().putArgs(FileListFragment.Args(args.intent, 0))`, 두 창 사용이면 `pane2 = FileListFragment().putArgs(FileListFragment.Args(Intent(), 1))`(빈 Intent → 기본 디렉터리, REQ-2-18·19, HLD 3.7). 한 트랜잭션에서 `add(R.id.paneContainer, pane1)`, `pane2?.let { add(R.id.pane2Container, it) }`, `add(R.id.navigationFragment, navigationFragment)` — 창을 탐색 서랍보다 먼저(기존 주석 `H:108-109`).
  3. 복원: `pane1 = findFragmentById(R.id.paneContainer) as FileListFragment`, `pane2 = findFragmentById(R.id.pane2Container) as FileListFragment?`. 두 창 사용인데 `pane2 == null`이면(#5 판이 저장한 상태 — 개발 빌드끼리만 생김) 새 창 2를 만들어 `childFragmentManager.commitNow { add(R.id.pane2Container, pane2) }` — 이어지는 단계가 창 2의 뷰모델·LiveData를 읽으므로 즉시 실행.
  4. `panes = listOfNotNull(pane1, pane2)`.
  4-1. `viewModel.setPaneMode(PaneMode(false, 0))` — 호스트 범위 뷰모델에 남은 이전 구성(회전 전)의 값을 지운다. 새 뷰의 K4는 첫 측정에서 다시 알린다(`reportedMode`가 새 뷰에서 `null`). 그 사이 창의 `updateSpanCount`는 한 창 공식을 쓴다 — 이전 구성의 엉뚱한 창 너비로 열 수를 정하지 않는다(검수 1회차 `[판단]` 4, PM 결정 2026-10-29).
  5. K4 연결: `binding.dualPaneLayout.isSwipeEnabled = isDualPaneEnabled`, `persistentDrawerView = if (binding.persistentDrawerLayout != null) binding.navigationFragment else null`, `isPersistentDrawerOpen = Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat`(첫 측정 전에 목표 상태가 있게), `listener = 아래 구현`, `activePane = viewModel.activePane`.
  6. `viewModel.activePaneLiveData.observe(viewLifecycleOwner) { binding.dualPaneLayout.activePane = it }`.
  7. 뒤로 가기(4장): ① 창 전달 콜백 — `isEnabled`는 `viewModel.activePaneLiveData.switchMap { panes[it].backPressedEnabledLiveData }` 관찰(기존 `H:132-134`의 고정 대상을 바꿈), `handleOnBackPressed`는 `activePane.paneOnBackPressedDispatcher.onBackPressed()`(기존 `H:128-130` 그대로) ② 서랍 콜백(기존 `H:136-138`) ③ **검색 접기 콜백(신규, F1 ④)** — `isEnabled`는 `viewModel.activePaneLiveData.switchMap { panes[it].searchViewExpandedLiveData }`, `handleOnBackPressed`는 `activePane.collapseSearchView()`. 셋 다 `addOnBackPressedCallback`(같은 소유자 `viewLifecycleOwner`), 이 순서로.
  8. 고정 메뉴 설정 관찰(기존 `H:140-144`): `onPersistentDrawerOpenChanged(it)`에 K4 목표 상태 반영 추가(아래).
- **오류**: 복원 시 `pane1`이 없으면 지금처럼 `ClassCastException`(이전 판 상태는 액티비티의 `restoreHostFragment`가 이미 걸러 냄 — LLD-A 2.1).

#### `private fun isPickIntent(intent: Intent): Boolean` (신규)
- **추적**: HLD K2·3.7 / SRS A1
- **출력**: `intent.action`이 `ACTION_GET_CONTENT`·`ACTION_OPEN_DOCUMENT`·`ACTION_CREATE_DOCUMENT`·`ACTION_OPEN_DOCUMENT_TREE` 중 하나면 `true`.
- **기존 코드와의 관계**: 창의 선택 모드 판단(`F:252-286`)과 같은 액션 목록. 창 코드는 바꾸지 않고 호스트에 같은 목록을 둔다(창은 선택 옵션까지 만들어야 하고 호스트는 여부만 필요). 두 목록이 같아야 한다는 주석을 양쪽에 단다.

#### `DualPaneLayout.Listener` 구현 (호스트 안 `object`)
| 함수 | 명세 |
|---|---|
| `onPaneModeChanged(mode)` | `viewModel.setPaneMode(mode)` |
| `onSwipeToTogglePane()` | `viewModel.togglePane()` |
| `onPaneActivated(index)` | `viewModel.setActivePane(index)` |

#### `private fun onPersistentDrawerOpenChanged(open: Boolean)` (수정, 기존 `H:165-173`)
- 기존 열고 닫기 뒤에 `binding.dualPaneLayout.isPersistentDrawerOpen = open` 추가(REQ-2-03·04의 "고정 메뉴 열기·닫기 때 재판단").

#### `fun onMenuKeyEvent(event: KeyEvent): Boolean` (신규, 공개)
- **추적**: F1 ③ / REQ-2-09
- `if (!this::panes.isInitialized) return false; return activePane.onMenuKeyEvent(event)`. 기존 `onKeyShortcut`(`H:158-163`)과 같은 모양.

#### `NavigationFragment.Listener.observeCurrentPath(owner, observer)` (수정, 기존 `H:209-211`)
- **추적**: HLD 3.4 / REQ-2-09
- `viewModel.activePaneLiveData.switchMap { panes[it].currentPathLiveData }.observe(owner, observer)` — 활성 창이 바뀌면 탐색 서랍의 선택 표시가 새 활성 창의 경로를 따른다. `currentPath`·`navigateTo`·`navigateToRoot`·`navigateToDefaultRoot`(`H:194-207`)는 이미 `activePane`에 위임하므로 그대로.

#### `Binding` (수정, 기존 `H:403-422`)
- 필드 추가: `val dualPaneLayout: DualPaneLayout`, `val navigationFragment: View`(`includeBinding.dualPaneLayout`, `includeBinding.navigationFragment` — 두 레이아웃 변형 모두에 있는 ID라 바인딩 필드가 non-null).

### 2.5 K5 `FileListFragment` (수정 — 창)

#### `Args` (수정, 기존 `F:1405-1406`)
- `class Args(val intent: Intent, val paneIndex: Int) : ParcelableArgs` — HLD K5 "인자에 창 번호". 이 단계는 호스트가 넣기만 하고, 읽는 곳은 #7(K7 창 번호 표시)이다(LLD-A 4장 계획). 기본값을 두지 않는다 — 호출하는 곳은 호스트 두 곳(2.4-2·3)뿐이고 모두 명시한다(Parcelize는 복원 때 기본값을 쓰지 않으므로 기본값이 복원 호환을 주지도 않는다).

#### `dualPaneViewModel` (신규 private 필드)
- `private val dualPaneViewModel by viewModels<DualPaneViewModel>({ requireParentFragment() })` — HLD K3의 창 쪽 접근(`util/FragmentViewModelLazy.kt:15-19`). 호스트가 같은 키로 먼저 만들어 둔다(`H:81`, 호스트 `onActivityCreated`가 자식보다 먼저).

#### `onCreate(savedInstanceState)` (수정, 기존 `F:167-171`)
- `setHasOptionsMenu(true)` 삭제(옵션 메뉴 경로를 쓰지 않음, F1).

#### `onActivityCreated(savedInstanceState)` (수정, 기존 `F:182-337`)
- **추적**: HLD D2, F1 ⑤⑥, F3
- 바뀌는 곳:
  1. `F:185-187` → `val activity = requireActivity()`(캐스트 제거), `activity.setTitle(R.string.file_list_title)` 유지, `activity.setSupportActionBar(binding.toolbar)` **삭제**, 대신 `binding.toolbar.setTitle(R.string.file_list_title)`(⑥), `binding.toolbar.setNavigationOnClickListener { listener.onNavigationIconClicked() }`(⑤).
  2. 끝(기존 `F:337` 앞, 모든 `observe` 뒤): 메뉴 붙이기 — `viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver { override fun onStart(owner) { owner.lifecycle.removeObserver(this); binding.toolbar.addMenuProvider(menuProvider, owner) } })`. **이유**: 메뉴 갱신 함수(`updateViewSortMenuItems` 등)는 `viewModel.sortOptions`처럼 `MediatorLiveData`가 활성일 때만 값이 있는 속성을 읽는다(`FileSortOptionsLiveData.kt:18-30`, 비활성이면 `valueCompat`이 null → NPE). 지금은 옵션 메뉴가 지원 액션바의 지연 무효화로 RESUMED 뒤에 만들어져 문제가 없었다. 같은 소유자의 관찰자는 등록 순서대로 `ON_START`를 받으므로, 이 관찰자를 LiveData 관찰 **뒤에** 등록하면 메뉴가 만들어질 때 모든 LiveData가 활성이다. 2인자 `addMenuProvider(provider, owner)`는 `ON_DESTROY`에만 제거하므로 멈춤·재시작에 메뉴를 다시 만들지 않는다(3인자 판은 RESUMED를 벗어날 때마다 메뉴를 지우고 다시 만들어 검색 상태·최근 앱 썸네일이 흔들림 — 쓰지 않음).
  3. 열 수(F3): `dualPaneViewModel.paneModeLiveData.observe(viewLifecycleOwner) { updateSpanCount() }` 추가(기존 `F:320-324` 고정 메뉴 관찰 옆).

#### `private val menuProvider = object : MenuProvider` (신규 — 기존 옵션 메뉴 3개 대체)
| 함수 | 명세 |
|---|---|
| `onCreateMenu(menu, menuInflater)` | 기존 `onCreateOptionsMenu`(`F:339-345`) 본문: `menuBinding = MenuBinding.inflate(menu, menuInflater)`, 그룹 구분선, `setUpSearchView()`. 이어서 기존 `onPrepareOptionsMenu`(`F:393-399`)의 세 갱신(`updateViewSortMenuItems`, `updateSelectAllMenuItem`, `updateShowHiddenFilesMenuItem`)을 부른다 — 툴바 `MenuHost`는 `onPrepareMenu`를 부르지 않는다(appcompat 1.7.1 `Toolbar`는 `MenuHostHelper.onPrepareMenu`를 호출하지 않음). 이후 갱신은 지금처럼 LiveData 관찰이 한다. |
| `onMenuItemSelected(menuItem)` | 기존 `onOptionsItemSelected`(`F:401-495`)에서 `android.R.id.home` 갈래를 뺀 같은 `when`(홈은 ⑤의 내비게이션 클릭으로). `else -> false`. |
- 삭제: `onCreateOptionsMenu`, `onPrepareOptionsMenu`, `onOptionsItemSelected` override.
- 메뉴 인플레이터: 툴바의 `SupportMenuInflater(toolbar.context)` — 지금 지원 액션바의 테마 컨텍스트(`ToolbarActionBar.getThemedContext()` = 툴바 컨텍스트)와 같아 `app:showAsAction`·검색 뷰 스타일(`ThemeOverlay.MaterialFiles.ActionBar`의 `searchViewStyle`, `res/values/styles.xml:61-64`)이 같다.

#### `fun collapseSearchView()` (가시성 `private` → 공개, 기존 `F:387-391`)
- 호스트의 검색 접기 콜백(2.4-7 ③)이 부른다. 본문 그대로.

#### `val searchViewExpandedLiveData: LiveData<Boolean>` (신규 공개 게터)
- `get() = viewModel.searchViewExpandedLiveData`(`FileListViewModel.kt:79`).

#### `val currentPathLiveData: LiveData<Path>` (신규 공개 게터) / `observeCurrentPath` 삭제
- `get() = viewModel.currentPathLiveData`. 기존 `observeCurrentPath`(`F:1394-1396`)의 유일한 호출자였던 호스트가 `switchMap`으로 바꾸므로 삭제한다.

#### `fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean` (수정, 기존 `F:497-517`)
- **추적**: F1 ② / REQ-2-09
- 기존 하단 막대 → 선택 툴바 순서 뒤에 **툴바 메뉴**를 더한다: `val menu = binding.toolbar.menu; menu.setQwertyMode(...); if (menu.performShortcut(keyCode, event, 0)) return true`. `this::menuBinding.isInitialized`가 아니면(메뉴 생성 전) 건너뛴다.
- **순서 근거**: 지금은 `AppCompatDelegateImpl$AppCompatWindowCallback.dispatchKeyShortcutEvent`가 액티비티(`onKeyShortcut` → 하단·선택 메뉴)를 먼저 부르고, 처리되지 않으면 `AppCompatDelegateImpl.onKeyShortcut` → `ToolbarActionBar.onKeyShortcut`(툴바 메뉴 `performShortcut`, 같은 `setQwertyMode`)을 부른다(appcompat 1.7.1 바이트코드). 그래서 툴바 메뉴를 마지막에 둔다. 단축키 중복은 Ctrl+A(툴바 "전체 선택"·선택 툴바 "전체 선택")뿐이고 같은 함수라 순서와 무관하게 결과가 같다.

#### `fun onMenuKeyEvent(event: KeyEvent): Boolean` (신규)
- **추적**: F1 ③
- `if (event.action == KeyEvent.ACTION_UP) binding.toolbar.showOverflowMenu(); return true` — `ToolbarActionBar.onMenuKeyEvent`(UP에서 `openOptionsMenu` → `DecorToolbar.showOverflowMenu`, DOWN·UP 모두 `true`)와 같다.

#### `private fun onPickOptionsChanged(pickOptions)` (수정, 기존 `F:720-738`)
- `requireActivity().title = title`(`F:733`) 뒤에 `binding.toolbar.title = title` 추가(⑥). 액티비티 제목도 계속 바꾼다(창 제목 — 접근성 창 이름·최근 앱).

#### `private fun updateSpanCount()` (수정, 기존 `F:595-606`)
- **추적**: HLD K5(F3) / REQ-2-03
- **알고리즘**: `LIST -> 1`. `GRID ->` `val mode = dualPaneViewModel.paneMode`; `widthDp = if (mode.isDualPane) mode.paneWidthDp else <기존 식: screenWidthDp - (고정 메뉴 열림이면 navigation_max_width)>`; `(widthDp / 180).coerceAtLeast(2)`.
- **이유**: 두 창 모드에서 `screenWidthDp`는 창 너비의 두 배라 열이 너무 많다(HLD K5). 한 창 모드는 기존 식을 그대로 둬 LLD-A F3이 지적한 "실제 서랍 너비·인셋 때문에 열 수가 바뀌는 화면"을 만들지 않는다. 두 창 모드의 창 너비는 K4가 목표 상태로 계산한 값이라 고정 메뉴 애니메이션 중에 흔들리지 않는다 → 설계 피드백 FB-5(PM 승인 2026-10-29).
- 호출 시점: 기존 두 곳(보기 방식 `F:590`, 고정 메뉴 설정 `F:321-323`) + `paneModeLiveData` 변경.

#### 그대로 두는 것 (확인 목록)
- 창 뒤로 가기 콜백 세 개와 순서(`F:227-246`), `paneOnBackPressedDispatcher`(`F:141-142`), `Listener`(`F:1408-1412`), `refresh()`(`F:658-660`), 시작 경로 결정(`F:248-308`), 하단·선택 툴바(`OverlayToolbarActionMode`·`PersistentBarLayoutToolbarActionMode`).

### 2.6 레이아웃

#### `res/layout/file_list_host_fragment_include.xml` (수정)
- `:16-20`의 `FrameLayout`(`paneContainer`)을 다음으로 바꾼다. 주석("Must not fit system windows …")은 K4로 옮긴다.
```xml
<me.zhanghai.android.files.filelist.DualPaneLayout
    android:id="@+id/dualPaneLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <FrameLayout android:id="@+id/paneContainer"
        android:layout_width="match_parent" android:layout_height="match_parent" />
    <FrameLayout android:id="@+id/pane2Container"
        android:layout_width="match_parent" android:layout_height="match_parent" />
</me.zhanghai.android.files.filelist.DualPaneLayout>
```
- K4는 자식 `LayoutParams`를 무시하고 2.3대로 잰다(XML의 `match_parent`는 `ViewGroup.LayoutParams` 필수 속성 때문).

#### `res/layout-sw600dp-land/file_list_host_fragment_include.xml` (수정)
- `:18-22`의 `FrameLayout`을 위와 같은 `DualPaneLayout`으로. `PersistentDrawerLayout`의 내용 뷰(gravity 없음)로 남는다.

## 3. F1 — 툴바 `MenuHost` 전환의 동등성 (HLD D2 ①~⑥)

근거는 appcompat 1.7.1 `classes.jar`를 `javap -c`로 읽은 것(로컬 Gradle 캐시). "지금"은 `setSupportActionBar(binding.toolbar)`(`F:187`) 경로.

| # | 지금 하는 일 | 바꾼 뒤 유지 방법 | 시험 |
|---|---|---|---|
| ① 툴바 스타일(제목·부제목 글꼴, 여백, 높이, `popupTheme`, 표시 옵션) | **적용하지 않는다.** `AppCompatDelegateImpl.setSupportActionBar` → `new ToolbarActionBar(toolbar, title, callback)` → `new ToolbarWidgetWrapper(toolbar, false)`. 두 번째 인자 `style=false`면 생성자가 `actionBarStyle` 읽기 블록 전체(`titleTextStyle`·`subtitleTextStyle`·`contentInsetStart/End`·`height`·`popupTheme`·`displayOptions`)를 건너뛰고 `detectDisplayOptions()`만 한다(바이트코드 `iload_2; ifeq 452`). 툴바 글꼴·여백은 지금도 툴바 자체 스타일(`toolbarStyle`, XML `app:subtitleTextAppearance` 등 `file_list_fragment_app_bar_include.xml:27-36`)에서 온다. | 조치 없음. HLD D2 ①과 LLD-A F1 ①의 전제("지원 액션바가 `actionBarStyle`을 툴바에 적용")는 사실이 아니다 → FB-1 | T-B20(기준 APK와 툴바 캡처 비교, 밝은·어두운 테마, MDC·Material3 테마 설정 각각) |
| ② 툴바 메뉴 단축키(Ctrl+L·R·A·H, `res/menu/file_list.xml:84-105`) | 액티비티가 처리하지 않은 단축키를 `ToolbarActionBar.onKeyShortcut`이 툴바 메뉴 `performShortcut` | 창 `onKeyShortcut`이 하단·선택 메뉴 다음에 툴바 메뉴 `performShortcut`(2.5). 호스트 → **활성 창**(`H:158-163` 그대로) | T-B13, LLD-A T-11 재실행 |
| ③ 메뉴 키로 넘침 메뉴 | `AppCompatActivity.dispatchKeyEvent`: `KEYCODE_MENU` → `ToolbarActionBar.onMenuKeyEvent`(UP에서 `showOverflowMenu`) | `FileListActivity.dispatchKeyEvent`(2.1) → 호스트 `onMenuKeyEvent` → 활성 창 `onMenuKeyEvent`(2.5) | T-B13, LLD-A T-12 재실행 |
| ④ 검색 펼침의 뒤로 가기 접기 | API 33 미만: `AppCompatDelegateImpl.onBackPressed`가 디스패처보다 먼저 `ActionBar.collapseActionView()`. API 33 이상: `setSupportActionBar`가 켠 `Toolbar.setBackInvokedCallbackEnabled(true)` — 펼칠 때 툴바가 자기 `OnBackInvokedCallback`을 등록 | 호스트의 **검색 접기 콜백**(2.4-7 ③, 활성 창 검색 상태를 따름)을 서랍 콜백 **뒤에** 등록 → 모든 API에서 "검색 접기 > 서랍 > 창" 순서. 툴바의 `setBackInvokedCallbackEnabled`는 켜지 않는다(켜면 숨은 창·비활성 창의 펼친 검색이 뒤로 가기를 가로챔 — `Toolbar.updateBackInvokedCallbackState`는 붙어 있기만 하면 등록, `GONE`도 붙어 있음) | T-B17, LLD-A T-09 재실행 |
| ⑤ 내비게이션 아이콘 → `android.R.id.home` | `ToolbarWidgetWrapper`가 `setNavigationOnClickListener`로 홈 항목을 창 콜백에 보냄 → `onOptionsItemSelected(home)`(`F:403-406`) → `listener.onNavigationIconClicked()` | `binding.toolbar.setNavigationOnClickListener { listener.onNavigationIconClicked() }`(2.5). 기본 내비게이션 설명(`abc_action_bar_up_description`)은 XML의 `app:navigationContentDescription`이 있어 지금도 적용되지 않으므로 할 일 없음 | T-B18, LLD-A T-05·T-10 재실행 |
| ⑥ 액티비티 제목 → 툴바 제목 | `ToolbarActionBar.setWindowTitle` → `ToolbarWidgetWrapper.setTitleInt` → `toolbar.setTitle`(제목 표시 옵션이 켜져 있으므로). 접근성 창 제목(`setAccessibilityPaneTitle`)은 `mTitleSet`이 참일 때만이라 지금 쓰이지 않음 | 창이 `binding.toolbar.title`을 직접(시작 시 `file_list_title`, 선택 모드 제목은 `onPickOptionsChanged`). 액티비티 제목도 계속 설정 | T-B19, LLD-A T-04(c)(d)(e) 재실행 |

- 그 밖에 `setSupportActionBar`가 하던 것: 툴바 메뉴 클릭 리스너·메뉴 콜백 연결(→ `MenuHost`가 대신), `invalidateOptionsMenu` 지연 생성(→ 2.5 `ON_START` 관찰자). 이 앱의 `filelist/`에는 `invalidateOptionsMenu`·`supportActionBar` 사용이 없다(`grep`).
- 창이 둘이어도 지원 액션바를 쓰는 창이 없으므로 HLD D2 대안 (b)의 "전환마다 메뉴 재생성·제목 깜박임"이 없다.

## 4. 뒤로 가기 순서 (HLD 3.5 확장)

### 4.1 지금 순서
- 액티비티 디스패처: 호스트 [① 창 전달 → ② 서랍](`H:127-138`). 창 디스패처: [상위 폴더 → 선택 → speed dial](`F:227-246`). 나중에 더한 켜진 콜백이 먼저.
- 검색 접기: API 33 미만은 디스패처 밖(AppCompat)에서 가장 먼저. API 33 이상은 툴바 자체 `OnBackInvokedCallback` — 펼친 순간 등록되며, 그 뒤에 액티비티 디스패처가 자기 콜백을 새로 등록하면(켜진 콜백이 0 → 1이 될 때, activity 1.10.1) 그쪽이 먼저가 된다. 예: 최상위 폴더에서 검색을 펼치고 열고 닫는 서랍을 열면 서랍이 먼저 닫히고, 하위 폴더에서 같은 순서면 검색이 먼저 접힌다(상태 의존).

### 4.2 이 LLD의 순서
- 호스트가 같은 소유자로 [① 창 전달 → ② 서랍 → ③ 검색 접기]를 등록 → **검색 접기(활성 창) > 서랍 > speed dial > 선택 > 상위 폴더 > 기본(종료)**. API와 무관하게 하나로, API 33 미만의 지금 순서와 같다. API 33 이상의 상태 의존 경우 하나가 달라질 수 있다 → 0.3, FB-3.
- ①과 ③의 대상은 `activePaneLiveData.switchMap`으로 활성 창을 따른다 — 비활성 창의 선택·검색·speed dial은 뒤로 가기에 반응하지 않는다(HLD 3.5 "활성 창일 때만").
- 예측 뒤로 가기: ③이 켜져 있는 동안 디스패처가 콜백을 등록하므로 "홈으로" 미리보기가 나오지 않는다 — 지금 툴바 콜백이 있을 때와 같다.

## 5. 테스트 설계

### 5.1 단위 테스트 (필수)
SRS C-3의 시험 의존성은 오너가 승인했다(FB-6, 2026-10-29). `app/build.gradle`에 `testImplementation 'junit:junit:4.13.2'`를 더하고, 순수 함수 `isDualPaneWidth`·`computeAvailableWidthPx`(2.3, 파일 수준)를 `app/src/test/java/me/zhanghai/android/files/filelist/DualPaneLayoutTest.kt`(JUnit 4)로 시험한다. 실행: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --console=plain 2>&1 | tail -n 30` → `BUILD SUCCESSFUL`, 결과 `app/build/test-results/testDebugUnitTest/*.xml`에서 5건 통과. T-B04는 실기 경계 확인으로 그대로 한다.

| 테스트 | 대상 함수 | 경우 | 기대 결과 |
|---|---|---|---|
| U-01 | `isDualPaneWidth` | 경계: (1575px, 2.625) = 600.0dp | `true` |
| U-02 | `isDualPaneWidth` | 경계: (1574px, 2.625) ≈ 599.6dp, (599px, 1.0) | `false` |
| U-03 | `computeAvailableWidthPx` | 열고 닫는 서랍(range `null`): own 1000, parent 1200 | 1000 |
| U-04 | `computeAvailableWidthPx` | 고정 메뉴 목표 열림: parent 2000, range 640 / 목표 닫힘 | 1360 / 2000 (own 값과 무관 — 애니메이션 중 own이 달라도 같음) |
| U-05 | `computeAvailableWidthPx` | range > parent(비정상) | 0 |

### 5.2 준비
LLD-A 5.1과 같다(빌드 명령, 기준 APK = develop a76055e 빌드, `adb logcat -b crash -d` 확인, 시험 폴더). 기기 구성 SRS 3.2 C1~C4. 추가:
- C3 분할 화면: `adb shell am start --windowingMode 6 …`가 안 되면 최근 앱 화면에서 "분할 화면"으로 열고 경계를 끈다.
- 제스처 내비게이션 전환: `adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural` / `...threebutton`.
- RTL: `adb shell settings put global debug.force_rtl 1` 후 앱 재시작(끝나면 0).
- 이 PC의 AVD는 API 36뿐 — API 29 미만·API 33 미만 갈래는 해당 AVD를 만들 수 있으면 확인, 아니면 **미확인**으로 보고.

### 5.3 시험 목록
| 테스트 | 대상 함수 | 경우 | 절차 | 기대 결과 |
|---|---|---|---|---|
| T-B00 빌드·단위 | 전체, U-01~U-05 | 정상 | `assembleDebug`, `testDebugUnitTest` | 둘 다 `BUILD SUCCESSFUL`, U 5건 통과, 새 경고 보고 |
| T-B01 창 2 생성 | 호스트 `onActivityCreated` 2 | 정상 | C1 시작 → `dumpsys activity top` | `FileListHostFragment` > `FileListFragment` 2개·`NavigationFragment` |
| T-B02 선택 모드 한 창(A1) | `isPickIntent`, K4 (e) | 경계 | `GET_CONTENT`·`OPEN_DOCUMENT_TREE`·`CREATE_DOCUMENT`로 시작(LLD-A T-04 명령) → C1에서 쓸기, C2(가로)에서 화면 | 창 1개만, 쓸기 무반응(K4 (e)로 인식 안 함, 혹시 불려도 `togglePane()`은 `isDualPaneEnabled=false`라 무시), 가로에서도 한 창, 결과 반환 기준과 같음. K3 `setActivePane`의 범위 밖 값·선택 모드 무시 갈래는 화면으로 만들 수 없으므로 코드 검수의 줄 대조로 확인 |
| T-B03 창별 상태(REQ-2-02) | K5 기존 | 정상 | SRS REQ-2-02 검증 절차 그대로(C1·C3) | SRS 기대대로 |
| T-B04 너비 판단(REQ-2-03) | `onMeasure`, `isDualPaneWidth`, `computeAvailableWidthPx` | 정상·경계 | C1 → 한 창, C2 → 두 창. C4(`wm size 1700x1300`, `wm density 320`, 가로): 고정 메뉴 펼침 → 한 창, 접음 → 두 창. 경계: 폰 AVD를 **세로**로 두고 `adb shell wm density 160` + `adb shell wm size 600x1000` → 너비 600dp, 이어서 `wm size 599x1000` → 599dp(세로라 `layout-sw600dp-land`가 아닌 열고 닫는 서랍 변형이 쓰임 — 짧은 변이 600이어도 `-land` 한정자가 맞지 않음). 끝나면 `wm size reset`·`wm density reset` | 기대대로. 600dp는 두 창, 599dp는 한 창 |
| T-B05 재판단·상태 유지(REQ-2-04) | `onMeasure`, `onPersistentDrawerOpenChanged` | 정상·경계 | 두 창 경로·선택·복사 목록을 다르게 둔 뒤 C1↔C2 회전 ×3(빠르게 포함, `Orientation_Transitions`), C3 분할 경계 끌기, C4 고정 메뉴 토글 ×3 | 모드가 기대대로 바뀌고 두 창 경로·선택·검색어·복사 목록·스크롤 위치 유지. 고정 메뉴 애니메이션 중 모드가 깜박이지 않음 |
| T-B06 숨은 창 유지(D8) | K4 가시성 | 경계 | C1 창 2에서 하위 폴더·스크롤 → 쓸기로 창 1 → 다시 창 2 | 경로·스크롤 그대로(목록 다시 읽지 않음 — 진행 표시 없음) |
| T-B07 모드 전환과 활성 창(REQ-2-05) | K4 `activePane`, `onMeasure` 5 | 정상 | SRS REQ-2-05 절차 | 기대대로 |
| T-B08 한 창 쓸기(REQ-2-06) | `updateSwipe`, `finishSwipe`, `togglePane` | 정상·경계 | C1: 목록 위·빈 폴더(빈 화면)·오류 화면에서 오른쪽→왼쪽 쓸기 ×2. 경계: 짧게(슬롭 넘고 48dp 미만) 천천히 → 무반응, 짧고 빠르게(플링) → 전환. 세로로 시작한 뒤 가로로 꺾기 → 무반응(세로 스크롤만). 마키: 설정에서 파일 이름 줄임을 "흐르게(마키)"로 두고 긴 이름 파일 글자 위에서 시작해 쓸기 → 전환 | 1→2→1. 빈 화면·마키 글자 위에서도 됨(3.3-5 경로, 제외 (d)는 `ViewGroup`만) |
| T-B09 두 창 쓸기(REQ-2-07) | 같음 | 정상 | C2: 창 1 활성에서 쓸기 → 창 2 활성 → 쓸기 → 창 1 (활성 표시가 아직 없으므로 툴바 메뉴 "새로 고침" 대신 **탐색 서랍 항목으로 이동**해 어느 창이 움직이는지로 확인) | 교대로 바뀜 |
| T-B10 탭 활성화(REQ-2-07) | `dispatchTouchEvent` | 정상·경계 | C2 창 1 활성: 창 2의 빈 곳·breadcrumb·파일을 각각 탭 → 매번 서랍 "다운로드"로 확인. 파일 탭은 파일도 열림. 창 2를 세로 스크롤·fastscroll 끌기 → 활성 그대로 | 탭은 활성화 + 원래 동작, 스크롤은 활성 안 바뀜 |
| T-B11 길게 누르기·접근성 클릭 | `longPressRunnable`, `onRequestSendAccessibilityEvent` | 경계 | C2 창 1 활성: 창 2 파일 길게 누르기 → 선택 생김, 바로 뒤로 → 선택 해제(창 2가 활성이었음). 길게 누른 채 왼쪽으로 끌기 → 활성 다시 안 바뀜. TalkBack 켜고 창 2 파일 두 번 탭 → 이후 뒤로 가기·서랍 이동이 창 2에(R10) | 기대대로 |
| T-B12 활성 창 추종(REQ-2-09) | 호스트 `observeCurrentPath`, 뒤로 가기 ①③ | 정상 | C2: 창 1 `lldA/a`, 창 2 기본 디렉터리. 창 2 활성 → 서랍 열기: 선택 표시가 창 2 경로 → 뒤로 → 창 2만 상위로. 창 1 활성 → 뒤로 → 창 1만. 창 1에서 선택 후 창 2 활성 → 뒤로 → 창 2 상위로(창 1 선택 그대로) | 기대대로 |
| T-B13 단축키·메뉴 키(F1 ②③) | 창 `onKeyShortcut`, `onMenuKeyEvent`, 액티비티 `dispatchKeyEvent` | 정상 | LLD-A T-11·T-12 절차를 C1(한 창)에서 기준과 비교. C2에서 창 2 활성 → Ctrl+R·Ctrl+H·Ctrl+L·메뉴 키 → 창 2 툴바에서 일어남 | 한 창은 기준과 같음. 두 창은 활성 창에서만 |
| T-B14 서랍 제스처(REQ-2-10, R1) | (FullDraggableContainer 그대로) | 정상·경계 | C1·C2: 목록 위 아무 곳·빈 화면에서 왼쪽→오른쪽 → 서랍 열림. 서랍 연 채 오른쪽→왼쪽 → 서랍만 닫힘, 창·활성 그대로. C4 메뉴 단추로 고정 메뉴 펼침·접힘. RTL 강제에서 방향 반대로 반복 | 기대대로. 쓸기 방향이 RTL에서 반전(A2) |
| T-B15 충돌 회피(REQ-2-11) | `isExcludedFromSwipe`, `isInSystemGestureArea`, `requestDisallowInterceptTouchEvent` | 경계 | 제스처 내비게이션(API 36): 오른쪽 가장자리에서 안쪽으로 → 뒤로 가기만. 왼쪽 가장자리 → 뒤로 가기만. 아래 가장자리에서 대각선 → 홈·전환만. 긴 경로 breadcrumb 오른쪽→왼쪽 끌기·짧은 경로 breadcrumb 위 쓸기 → 경로 표시줄만(창 그대로). 툴바 위 쓸기 → 무반응. fastscroll 끌기(긴 목록) → 목록만. 당겨서 새로 고침 → 그대로. 3버튼 내비게이션: 시험할 때만 `isInSystemGestureArea`에 `systemGestures` 인셋 값을 찍는 임시 `Log.d`를 넣고(커밋 전에 지움) 값을 기록한 뒤, 그 값 밖의 가장자리 근처에서 쓸기 | 기대대로. 3버튼 갈래: 기록한 인셋 띠 안에서 시작한 쓸기는 무반응, 띠 밖은 전환(인셋이 0이면 가장자리까지 전환). 값을 못 얻으면 미확인 |
| T-B16 창 2 시작 경로(REQ-2-18·19) | 호스트 2 | 정상 | SRS REQ-2-18·19 절차 + 압축 파일 열기 | 창 1 = Intent 경로·압축 루트, 창 2 = 기본 디렉터리 |
| T-B17 검색 뒤로 가기(F1 ④) | 호스트 ③, 창 `collapseSearchView` | 정상·경계 | C1: (1) 검색 펼침(키보드 닫음) → 뒤로 → 접힘 (2) 하위 폴더 + 검색 펼침 + 서랍 열기 → 뒤로 → 검색 접힘, 다시 뒤로 → 서랍 닫힘 (3) C2: 창 1 검색 펼침, 창 2 활성 → 뒤로 → 창 2 상위 폴더(창 1 검색 그대로) | (1) 기준과 같음 (2) 4.2 순서. 기준(API 36)의 결과도 함께 적어 0.3의 차이 여부를 보고 (3) 기대대로 |
| T-B18 홈 단추(F1 ⑤) | 창 `setNavigationOnClickListener` | 정상 | C1 메뉴 아이콘 → 서랍 열림. C4 → 고정 메뉴 토글. TalkBack에서 아이콘 설명 | 기준과 같음("탐색 서랍 열기") |
| T-B19 제목(F1 ⑥) | 창 `onActivityCreated`, `onPickOptionsChanged` | 정상 | 일반 시작, `GET_CONTENT`(단일·다중), `CREATE_DOCUMENT`, `OPEN_DOCUMENT_TREE` | 제목 문구·위치가 기준과 같음 |
| T-B20 툴바 모양(F1 ①) | (조치 없음 확인) | 정상 | C1·C4에서 기준과 툴바 캡처 비교: 밝은·어두운 테마 × 설정의 Material 3 켬·끔(테마 `themes.xml`·`themes_material3.xml`) | 제목·부제목 글꼴·크기·여백·높이·넘침 메뉴 팝업 모양 같음 |
| T-B21 격자 열 수(F3) | 창 `updateSpanCount` | 경계 | 격자 보기: C1·C2(한 창 시점 비교를 위해 C2는 기준과)·C4 펼침/접힘에서 열 수. 두 창 모드(C2·C4 접힘)에서 창마다 열 수 = `max(2, 창너비dp/180)`, 고정 메뉴 토글 직후 | 한 창은 기준과 같음. 두 창은 식대로, 토글 중 깜박임 없음 |
| T-B22 인셋(K4 `dispatchApplyWindowInsets`) | K4 인셋 분배 | 경계 | C2·C4에서 두 창: 상태 표시줄 아래 앱바, 목록 마지막 항목이 내비게이션 막대에 가리지 않음, 3버튼 내비게이션이 옆에 있는 가로(C2)에서 끝 쪽 창만 그 인셋을 가짐. RTL 강제에서 반복. 한 창 ↔ 두 창 전환 직후 패딩 확인 | 가리는 곳 없음, 맞닿은 쪽에 빈 여백 없음 |
| T-B23 자원(R5) | (측정만) | 정상 | C2에서 두 창을 서로 다른 큰 폴더로 → `adb shell dumpsys meminfo me.zhanghai.android.files` TOTAL PSS를 기준 APK(한 창)와 비교해 기록 | 기록만(판정 없음, R5) |
| T-B24 회귀 | LLD-A T-01~T-19 중 T-05·T-06~T-12·T-14·T-16·T-17 | 회귀 | C1·C4에서 LLD-A 절차 | LLD-A 기대와 같음(0.3의 차이 외) |

- 판정: 0.3의 의도된 변화와 FB-3 경우 외에 기준과 다르면 실패. 기록은 구현 PR 코멘트(시험별 통과/실패/미확인), 캡처는 `$MAIN_REPO/_workspace/`.

## 6. 설계 피드백
SRS·HLD는 고치지 않았다. 아래는 설계팀·PM에 넘길 것이다(동작 영향과 결정 필요 여부 표시).

- **FB-1 (문서 정정, 동작 영향 없음 — 설계팀 `design/2-hld-v4.8`에서 처리) HLD D2 ①의 전제가 틀림**: `setSupportActionBar`는 `ToolbarWidgetWrapper(toolbar, false)`로 감싸 `actionBarStyle`을 툴바에 **적용하지 않는다**(3장 ① 근거). LLD-A F1 ①도 같은 오해였다. 지원 액션바를 빼도 툴바 모양은 바뀌지 않으므로 ①은 조치 없이 유지된다. HLD D2의 ① 문구를 "적용하지 않음(유지할 것 없음 — 확인은 캡처 비교)"으로 고치기를 권한다.
- **FB-2 (문서 정정, 동작은 HLD 의도대로 — 설계팀 `design/2-hld-v4.8`에서 처리) HLD 3.3-1(a) API 29 미만 서술**: `WindowInsetsCompat.getInsets(Type.systemGestures())`는 API 29 미만에서 0이 아니라 시스템 창 인셋을 돌려준다(androidx.core 1.16.0). LLD는 `SDK_INT < Q`면 제외 영역 없음으로 명시 분기해 HLD의 의도를 지킨다(2.3 `isInSystemGestureArea`). HLD의 "버전 분기 없이" 문구 정정을 권한다.
- **FB-3 (결정됨: "검색 접기 먼저"로 통일, PM 2026-10-29) 검색 펼침 + 열고 닫는 서랍 열림에서 뒤로 가기 순서**: 4.1·4.2. API 33 이상의 상태 의존 순서를 "검색 접기 먼저"로 통일한다(API 33 미만의 지금 동작과 같음). 대안: "서랍 먼저"로 통일(검색 콜백을 창 디스패처 맨 위에 두면 됨 — 그 경우 API 33 미만에서 달라짐). 권고: 검색 먼저(지금 대부분 경우와 같음).
- **FB-4 (결정됨: #7 소유, PM 2026-10-29) K3 `panePaths`**: LLD-A 4장은 #6에 두었으나 이 단계에 소비자가 없다. #7(K7 반대쪽 경로)이 만들고 #9가 쓴다(PM이 #9에 메모). #6 범위 밖. HLD 변경 아님.
- **FB-5 (결정됨: 승인, PM 2026-10-29) F3 격자 열 수 공식**: 두 창 모드만 창 너비(K4 목표 상태 기준), 한 창 모드는 지금 공식 유지(2.5). HLD K5 "창 너비로 계산"을 두 창 모드에 적용하고, 한 창 모드에는 LLD-A F3(보이는 변화 회피)을 따른 해석이다.
- **FB-6 (결정됨: 승인, 오너 2026-10-29) 단위 테스트 의존성**: `testImplementation 'junit:junit:4.13.2'`, U-01~U-05 필수(1장 8·9행, 5.1).
- **검수 1회차 `[판단]` 결정(PM 2026-10-29, PR #17 코멘트 6473369587)**: (1) 쓸기 제외 (d)를 `ViewGroup`인 가로 스크롤 컨테이너로 좁히고 T-B08에 마키 경우 추가 — 2.3 `isExcludedFromSwipe` (d), 5.3 T-B08에 반영. (4) 호스트 `onActivityCreated`에서 `paneMode` 초기화 — 2.4 4-1에 반영. (6) `panePaths`는 #7 소유 — FB-4. (7) FB-1·FB-2의 HLD 정정은 설계팀이 별도 PR(`design/2-hld-v4.8`)로 한다 — 이 LLD는 HLD를 고치지 않는다.
- **결정 기록(LLD 재량, HLD가 맡긴 것)**: 쓸기 확정 거리 48dp 또는 플링(2.3), fastscroll 띠 48dp(R2), 길게 누르기 시간에 비활성 창 활성화(HLD 3.4), 비활성 창의 speed dial·붙여넣기 막대는 숨기지 않음(R6).

## 7. 위험 처리 (HLD 6장)
| ID | 이 단계 처리 |
|---|---|
| R1 | **정적 확인으로 닫음, 실기는 T-B14**. `FullDraggableHelper.onInterceptTouchEvent`(drawer 1.0.3 바이트코드)는 가로 이동이 슬롭을 넘고 세로보다 크며 `isDrawerEnabled(dx)`일 때만 가로챈다. `isDrawerEnabled`는 `dx > 0`이면 왼쪽 서랍, `dx < 0`이면 오른쪽 서랍이 있을 때만 참 — 서랍은 `layout_gravity="start"` 하나라 LTR에서 오른쪽→왼쪽(`dx < 0`)은 가로채지 않는다(RTL은 거울). K4를 올릴 필요 없음. |
| R2 | **닫음**. fastscroll 1.3.0 터치 영역은 엄지·트랙 뷰를 최소 48dp(`afs_min_touch_target_size`)로 넓혀 판정, 끌기 시작 시 부모에 `requestDisallowInterceptTouchEvent(true)`(`FastScroller.setDragging`). 띠 48dp + 2차 방어(2.3). |
| R3 | 확장: 전달 콜백이 활성 창을 따름(4장). |
| R5 | 측정만(T-B23). 최적화하지 않음. |
| R6 | 숨기지 않음(요구 REQ-2-13대로 두 창 모두 표시). 붐빔은 #7 활성 표시와 함께 다시 볼 수 있음. |
| R10 | K4 접근성 클릭 수신 구현(2.3), T-B11. 창 표시 컨트롤 경로는 #7. |
| R9·R11 | #8(D). |

## 8. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-10-29 | v1 | 최초 작성 | 이슈 #6 LLD 단계(F1·F3 포함) | PM |
| 2026-10-29 | v1.1 | `isLongPressed` 삭제, 길게 누르기 선후 근거 정정(`super` 전 `postDelayed`), `Args.paneIndex` 기본값 제거, T-B02(K3 무시 갈래), T-B04(600dp 경계를 세로로), T-B15(3버튼 인셋 기록) 보강, 6장에 `[판단]` 대기 항목 | LLD 1회차 검수 `[기계]` 2·3·5·8·9 | 검수팀 |
| 2026-10-29 | v1.2 | PM 결정 반영: FB-3·FB-5 결정 표기, FB-4 `panePaths` #7 소유, FB-6 JUnit 승인 → 1장 8·9행(`app/build.gradle`, `DualPaneLayoutTest.kt`, 합계 9개)·5.1 필수·T-B00, 순수 함수를 파일 수준으로, 제외 (d)를 `ViewGroup`으로 좁힘·T-B08 마키, 호스트 `paneMode` 초기화(2.4 4-1), 6장 결정 기록 | PR #17 코멘트 6473369587 "PM 결정" | PM |
