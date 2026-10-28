# LLD-A — 화면/창 분리 (이슈 #5, #10 흡수)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 |
| 근거 | SRS v4.6, HLD v4.6 (`docs/design/2-dual-pane/`), `docs/STANDARDS_TRACEABILITY.md` v0.5 |
| 기준 코드 | `origin/develop` 759c331 (HLD 기준 3d87794 이후 코드 변경 없음 — 인용 줄 번호는 759c331에서 다시 확인함) |
| 이슈 | #5 (하위 이슈 A, 상위 #2), #10 (`onKeyShortcut` 결함, PM이 #5에 흡수) |
| HLD 구성요소 | K1, K2, K3(기본), K5(이 단계 몫) |
| 추적 REQ | REQ-2-01, 09, 18, 19 중 이 단계 몫(아래 0.2) |

## 0. 범위와 전제

### 0.1 이 단계가 하는 것 / 하지 않는 것
- **한다**: 지금 `FileListFragment` 하나가 가진 화면 단위 책임(서랍 두 종류, `NavigationFragment`와 그 `Listener`, 고정 메뉴 열림 설정에 따른 서랍 열고 닫기, 서랍 뒤로 가기 콜백, 툴바 메뉴 단추(서랍 열기) 처리, 저장소·알림 권한 요청 흐름 전체)을 새 `FileListHostFragment`(K2)로 옮긴다. 권한 요청 진행 상태는 새 `DualPaneViewModel`(K3 기본)로 옮긴다. `FileListActivity`(K1)는 호스트를 붙이고 단축키를 호스트 → 활성 창으로 넘기며, #10을 고친다.
- **창은 하나다**. 호스트는 창 컨테이너 하나(`R.id.paneContainer`)에 `FileListFragment` 하나를 붙인다. 활성 창은 늘 창 1(인덱스 0)이다.
- **하지 않는다(#6~#9 몫)**: `DualPaneLayout`·너비 판단·쓸기·탭 활성화·RTL 반전(K4, #6), 창 2 생성·선택 모드 판단(`isDualPaneEnabled`)·`togglePane()`·`paneMode`·`panePaths`(K3 나머지, #6), 창 표시 줄(K7, #7), 확인 창(K6, #8), 반대쪽 복사·이동(#9). 이들이 붙을 자리는 4장.
- **이 단계에서 미루는 K5 항목**(설계 피드백 F1·F3, **PM 결정으로 #6으로 미룸 확정** — 2026-10-28, #5 코멘트 "🗂 PM 결정 — LLD-A"): HLD D2의 "툴바 자체 메뉴(`MenuHost`)로 바꾸기"와 "격자 열 수를 창 너비로 계산"은 창이 하나인 이 단계에서 하면 **사용자에게 보이는 변화가 생긴다**(근거 F1·F3). 이 단계는 지금처럼 창 툴바를 `setSupportActionBar`로 지정하고 열 수 공식도 그대로 둔다. 두 항목은 창 2가 생기는 #6에서 한다.

### 0.2 추적 — 이 단계의 몫
| REQ | 이 단계의 몫 | 확인 |
|---|---|---|
| REQ-2-01 | 화면(호스트)/창(`FileListFragment`) 구조를 만든다. 창 1만 있다(창 2는 #6). | 3장 T-01 |
| REQ-2-09 | 탐색 서랍 항목·키보드 단축키를 호스트가 받아 **활성 창**(지금은 창 1)에 위임한다. 툴바 메뉴는 창의 메뉴라 그 창에 작용한다. 시작 직후 활성 창 = 창 1(K3 `activePane` 처음 값 0). | T-05, T-11 |
| REQ-2-18 | 창 경로를 저장하지 않는다 — 새 설정 키 없음, 창 1은 지금처럼 기본 디렉터리에서 시작(`FileListFragment.kt:335-337` 그대로). | T-02 |
| REQ-2-19 | 호스트가 받은 `Intent`를 창 1의 인자로 그대로 넘긴다. 창 1의 시작 경로 결정(`:282-342`)은 바꾸지 않는다. "반대쪽 창은 기본 디렉터리"는 창 2가 생기는 #6 몫. | T-03, T-04 |

### 0.3 사용자에게 보이는 변화
- 열고 닫는 서랍 화면(폰 세로·가로, `layout/`): **없음**.
- 고정 메뉴 화면(`layout-sw600dp-land/`, 짧은 변 600dp 이상 가로): **배치가 바뀐다** — 앱바·붙여넣기/선택 하단 막대·speed dial이 창 안으로 들어가 고정 메뉴 오른쪽 영역에만 놓이고, 고정 메뉴는 화면 위·아래 끝까지 닿는다. 화면/창 분리 자체의 결과라(앱바·하단 막대가 창 단위 부품, 서랍이 화면 단위 부품) 피할 수 없다. 앱바가 창 안으로 들어가고 고정 메뉴가 화면 위쪽까지 닿는 것은 HLD D2가 적었고 오너가 수용했다(R8). 새로 확인받을 것은 하단 막대·speed dial 위치, 고정 메뉴가 아래 끝까지 닿는 것, 초점·읽기 순서 변화(고정 메뉴가 앱바보다 앞 순서가 됨), 그리고 "이 변화가 #5에서 생긴다"는 점이다 → 설계 피드백 F2. **오너 수용(2026-10-28, #5 코멘트 "🗂 PM 결정 — LLD-A")** — 이 단계의 "보이는 변화 없음"에 대한 유일한 예외: 앱바·하단 막대·speed dial이 창 영역 안으로, 고정 메뉴가 화면 위·아래 끝까지, 초점·읽기 순서에서 고정 메뉴가 앱바보다 앞.
- 앱 업데이트 직후 이전 판이 저장한 화면 상태로 복원될 때(한 번): 그 상태(경로·선택·열린 대화상자 등)를 버리고 시작 경로부터 다시 연다(2.1 `restoreHostFragment`, PM 결정 m1). 이전 판에서는 이 경우 크래시였을 것으로 본다(정적 분석) — 정상 경로(새로 시작·회전·프로세스 종료)에는 변화 없음.

## 1. 변경 파일 (R4 범위 공지 — PROCESS_ROLES 11-1, 5개 초과)

| # | 파일 | 신규/수정 | 요지 | 구성요소 |
|---|---|---|---|---|
| 1 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListActivity.kt` | 수정 | `FileListHostFragment`를 붙인다(복원된 프래그먼트가 다른 타입이면 교체 — m1). `onKeyShortcut`을 호스트로 넘기고 처리되지 않으면 `super.onKeyShortcut`(#10). | K1 |
| 2 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListHostFragment.kt` | 신규 | 화면 단위 책임(서랍·탐색 서랍·권한·뒤로 가기 순서). | K2 |
| 3 | `app/src/main/java/me/zhanghai/android/files/filelist/DualPaneViewModel.kt` | 신규 | 호스트 범위 뷰모델: `activePane`(0), 권한 요청 진행 상태 두 개. | K3(기본) |
| 4 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt` | 수정 | 화면 단위 코드 제거, 창 전용 뒤로 가기 디스패처, 호스트 `Listener`, 공개 함수 정리. | K5 |
| 5 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListViewModel.kt` | 수정 | 권한 요청 진행 상태 두 개(`:222-234`) 제거(K3로 이동). | K5 |
| 6 | `app/src/main/res/layout/file_list_host_fragment.xml` | 신규 | 호스트 루트(`FrameLayout` + include, 기존 `file_list_fragment.xml:8-18`과 같은 모양). | K2 |
| 7 | `app/src/main/res/layout/file_list_host_fragment_include.xml` | 신규 | 열고 닫는 서랍: `DrawerLayout` > `FullDraggableContainer` > 창 컨테이너, 탐색 서랍. | K2 |
| 8 | `app/src/main/res/layout-sw600dp-land/file_list_host_fragment_include.xml` | 신규 | 고정 메뉴: `PersistentDrawerLayout` > 탐색 서랍, 창 컨테이너. | K2 |
| 9 | `app/src/main/res/layout/file_list_fragment_include.xml` | 수정 | 서랍 부분(`:10-19`, `:42-55`) 제거 — 루트가 `PersistentBarLayout`(`:21-41`). | K5 |
| 10 | `app/src/main/res/layout-sw600dp-land/file_list_fragment_include.xml` | 수정 | `PersistentDrawerLayout`·탐색 서랍(`:24-38`) 제거, 내용은 감싼 `FrameLayout`으로. | K5 |

- 합계 10개(신규 5, 수정 5): Kotlin 5개, 레이아웃 XML 5개. `provider/`·`filejob/`·JNI·`app/build.gradle`은 건드리지 않는다(SRS C-1). 새 의존성·권한·설정 키·문자열 없음.
- 바꾸지 않지만 관계있는 파일: `res/layout/file_list_fragment.xml`(창 루트, 그대로 씀), `navigation/NavigationFragment.kt`(리스너만 호스트로 바뀜), `ShowRequest*RationaleDialogFragment.kt` 5개(`requireParentFragment() as Listener` — `ShowRequestAllFilesAccessRationaleDialogFragment.kt:18-19` — 이므로 호스트에서 `show(this)`하면 호스트가 받는다), `ui/DrawerLayoutOnBackPressedCallback.kt`, `ui/SpeedDialViewOnBackPressedCallback.kt`, `ui/ToolbarActionMode.kt`.
- 파일 이름: HLD K5는 창 레이아웃을 `file_list_pane_fragment.xml`로 적었으나, 기존 `file_list_fragment*.xml`을 창 레이아웃으로 그대로 쓴다(창 클래스 이름이 `FileListFragment` 그대로라 뷰 바인딩 클래스 `FileListFragmentBinding` 등이 맞고 diff가 작다). 동작 영향 없음 — 설계 피드백 F4.

## 2. 함수 명세

표기: "기존 `:N`"은 `FileListFragment.kt` 줄(759c331). 다른 파일은 파일 이름을 붙인다.

### 2.1 K1 `FileListActivity`

#### `me.zhanghai.android.files.filelist.FileListActivity.onCreate(savedInstanceState: Bundle?): Unit` (수정)
- **추적**: HLD K1 / REQ-2-01, REQ-2-19
- **책임**: 화면 루트로 `FileListHostFragment`를 붙인다.
- **입력·전제**: 메인 스레드. `intent`는 시작 Intent(다른 앱의 열기·선택 요청 포함).
- **출력·사후조건**: `savedInstanceState == null`이면 `FileListHostFragment().putArgs(FileListHostFragment.Args(intent))`를 `android.R.id.content`에 `add`. 아니면 `fragment = restoreHostFragment()`(아래). 필드 `fragment`의 타입은 `FileListHostFragment`. 어느 경우든 `onCreate`가 끝나면 `android.R.id.content`에는 `FileListHostFragment` 하나만 있다.
- **오류**: 없음 — 복원 자리의 타입이 다른 경우는 `restoreHostFragment()`가 교체로 처리한다(기존 `FileListActivity.kt:34-35`의 무조건 `as` 캐스트를 대체).
- **알고리즘**: 기존 `FileListActivity.kt:25-37`에서 `FileListFragment` → `FileListHostFragment`, `FileListFragment.Args` → `FileListHostFragment.Args`로 바꾸고, `else` 분기(`:33-36`)를 `fragment = restoreHostFragment()`로 바꾼다. `findViewById<View>(android.R.id.content)`(ensureSubDecor) 줄은 그대로.
- **기존 코드와의 관계**: `FileListActivity.kt:23`, `:30-36` 대체.

#### `FileListActivity.restoreHostFragment(): FileListHostFragment` (private, 신규 — 검수 m1, PM 결정 2026-10-28)
- **추적**: HLD K1 / REQ-2-01, `State_Preservation`(크래시 없는 복원), LLD 5장 T-17
- **책임**: 저장 상태에서 복원된 `android.R.id.content`의 프래그먼트가 `FileListHostFragment`면 그대로 쓰고, 아니면(이전 판 앱이 저장한 `FileListFragment`, 또는 없음) 그것을 제거하고 새 호스트로 바꾼다.
- **입력·전제**: `onCreate`의 `super.onCreate(savedInstanceState)` **뒤**, `savedInstanceState != null`일 때만 부른다. 메인 스레드. 이 시점에 복원된 프래그먼트는 `CREATED` 상태다 — `FragmentActivity.onCreate`가 상태 복원 후 `dispatchCreate()`만 하고, 뷰 생성(`onCreateView`·`onActivityCreated`)은 `onStart`의 `dispatchActivityCreated()`에서 일어난다. 그래서 이전 판 프래그먼트의 뷰는 아직 없다.
- **왜 필요한가(교체하지 않으면)**: 이전 판(759c331 이전 구조)이 저장한 상태에는 `android.R.id.content`에 `FileListFragment`가, 그 자식으로 `R.id.navigationFragment` 자리의 `NavigationFragment`가 있다. 복원하면 새 판의 `FileListFragment`(창) 클래스로 다시 만들어지는데, ① 새 창 레이아웃에는 `R.id.navigationFragment`가 없어 `onStart`에서 자식 `NavigationFragment`의 컨테이너를 찾지 못해 `IllegalArgumentException("No view found for id …")`, ② 창의 `listener`(`requireParentFragment()`)가 부모가 없어 `IllegalStateException`, ③ 기존 `as FileListHostFragment` 캐스트가 `ClassCastException`이 된다(정적 분석, 실기 미확인 — T-17).
- **출력·사후조건**: 돌려준 호스트가 `android.R.id.content`의 유일한 프래그먼트. 교체한 경우 이전 프래그먼트와 그 자식들(`NavigationFragment`, 열려 있던 대화상자 프래그먼트 등)은 뷰가 만들어지기 전에 `onDestroy`·`onDetach`까지 끝난다. 창 `FileListViewModel`은 지연 생성(`FileListFragment.kt:168`)이고 `onCreate`(`:195-199`)가 접근하지 않으므로 보통 만들어지지 않는다 — 만들어졌다면 `ViewModelStore`가 비워지며 `onCleared`(`FileListViewModel.kt:236-238`)가 목록 `LiveData`를 닫는다.
- **오류**: 없음. `commitNow`는 `onCreate` 안(상태 저장 전, 다른 트랜잭션 실행 중이 아님)이라 허용된다.
- **알고리즘**:
  1. `val restored = supportFragmentManager.findFragmentById(android.R.id.content)`
  2. `if (restored is FileListHostFragment) return restored`
  3. `val host = FileListHostFragment().putArgs(FileListHostFragment.Args(intent))`
  4. `supportFragmentManager.commitNow { if (restored != null) remove(restored); add(android.R.id.content, host) }` — 반드시 `commitNow`. `FragmentActivity.onStart`는 첫 시작 때 `dispatchActivityCreated()`(복원된 프래그먼트의 뷰 생성)를 **먼저** 하고 그 뒤에 `execPendingActions()`를 부르므로(fragment 1.8.8 바이트코드), `commit`이면 교체가 실행되기 전에 이전 프래그먼트의 뷰가 만들어져 위 ①·②의 크래시가 그대로 난다.
  5. `return host`
- **상태 손실 범위**(교체될 때만 — 정상 회전·재생성에서는 2에서 돌아가므로 손실 없음):
  - 잃는 것: 이전 화면의 현재 경로·뒤로 가기 기록·검색어·선택·스크롤 위치, 열려 있던 대화상자(이름 바꾸기·새로 만들기·근거 대화상자 등 — 그 입력값 포함), 권한 요청 진행 상태(호스트 뷰모델은 새것이라 처음 값 false → 다음 `onResume`에서 필요하면 다시 묻는다).
  - 잃지 않는 것: 액티비티 `intent`(복원된 액티비티가 그대로 가짐 → 새 호스트 → 창 1이 시작 경로를 지금 규칙(기존 `FileListFragment.kt:282-342`)으로 다시 정한다: 런처로 연 화면은 설정의 기본 디렉터리, 경로 Intent로 연 화면은 그 경로, 선택 모드 Intent는 선택 모드 그대로), 설정 값, 책갈피.
  - 복사 목록(`PasteState`, 프로세스 전역)은 앱 업데이트로 프로세스가 이미 끝났으므로 원래 비어 있다 — 이 함수와 무관.
  - 이 손실은 프로세스 종료 뒤 경로가 복원되지 않는 기존 동작(SRS A6, 편차 #11)과 같은 수준이다.
- **기존 코드와의 관계**: `FileListActivity.kt:33-36`(`else` 분기의 무조건 캐스트) 대체. 같은 저장소에 비슷한 방어 패턴은 없다(새 함수).

#### `FileListActivity.onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean` (수정, #10)
- **추적**: HLD K1 / REQ-2-09, #10
- **책임**: 단축키를 호스트(→ 활성 창)에 먼저 주고, 처리되지 않으면 액티비티 기본 단축키 처리로 넘긴다.
- **입력·전제**: 메인 스레드, Ctrl 등 수정키가 눌린 `ACTION_DOWN`(`Activity.dispatchKeyShortcutEvent` 경로).
- **출력·사후조건**: `fragment.onKeyShortcut(keyCode, event)`가 `true`면 `true`, 아니면 `super.onKeyShortcut(keyCode, event)`의 값.
- **오류**: 없음.
- **알고리즘**: 기존 `FileListActivity.kt:39-44`의 `return super.onKeyUp(keyCode, event)`를 `return super.onKeyShortcut(keyCode, event)`로 바꾼다.
- **기존 코드와의 관계**: `FileListActivity.kt:39-44`. 동작 분석(미확인 — 실기 확인은 T-11): 창 툴바 메뉴의 단축키(`res/menu/file_list.xml:84,91,98,105`의 l·r·a·h)는 이 함수가 `false`를 돌려준 **뒤** AppCompat 창 콜백이 지원 액션바로 처리한다(`AppCompatDelegateImpl$AppCompatWindowCallback.dispatchKeyShortcutEvent` → `AppCompatDelegateImpl.onKeyShortcut` → `ActionBar.onKeyShortcut`, appcompat 1.7.1 바이트코드로 확인). 이 단계는 지원 액션바를 유지하므로(0.1) 이 경로는 그대로다. `super.onKeyShortcut`(`Activity.onKeyShortcut`)은 프레임워크 액션바(`NoActionBar` 테마라 없음, `res/values/themes.xml:10`)에 묻고 `false`를 돌려줄 것으로 보이며, 기존 `super.onKeyUp`도 추적 중이 아닌 DOWN 이벤트에는 `false`일 것으로 보여 관찰 가능한 차이는 없을 것으로 예상한다. 수정은 의도된 API 계약으로 되돌리는 것이다.

### 2.2 K3 `DualPaneViewModel` (신규, 기본)

#### `me.zhanghai.android.files.filelist.DualPaneViewModel : ViewModel()`
- **추적**: HLD K3 / REQ-2-09(활성 창 처음 값), HLD 3.8(권한 진행 상태)
- **책임**: 호스트 범위 상태. 이 단계에서는 활성 창 번호와 권한 요청 진행 상태만 가진다.
- **입력·전제**: 호스트가 `private val viewModel by viewModels { { DualPaneViewModel() } }`(`util/FragmentViewModelLazy.kt:15-19`, 창의 `:168`과 같은 방식)로 얻는다. 메인 스레드.
- **출력·사후조건**: 회전·크기 변경(액티비티 재생성)을 넘어 유지, 프로세스 종료 시 처음 값(HLD 3.1).
- **멤버**:
  - `private val _activePaneLiveData = MutableLiveData(0)` / `val activePane: Int get() = _activePaneLiveData.valueCompat` — 처음 값 0(창 1). 이 단계에는 바꾸는 함수가 없다(#6이 `setActivePane`/`togglePane`과 공개 `activePaneLiveData`를 더한다).
  - `var isStorageAccessRequested: Boolean`, `var isNotificationPermissionRequested: Boolean` — `FileListViewModel.kt:222-234`의 두 속성을 **이름·처음 값(false)·`MutableLiveData` 백킹까지 그대로** 옮긴다.
- **오류**: 없음.
- **기존 코드와의 관계**: `FileListViewModel.kt:222-234` 이동(2.5).

### 2.3 K2 `FileListHostFragment` (신규)

#### 클래스 선언
```kotlin
class FileListHostFragment : Fragment(), FileListFragment.Listener, NavigationFragment.Listener,
    ShowRequestAllFilesAccessRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionInSettingsRationaleDialogFragment.Listener
```
- **추적**: HLD K2 / REQ-2-01, 09, 18, 19
- **필드**:
  - 결과 런처 5개 — 기존 `:145-163`을 **선언 순서·계약·`@RequiresApi`까지 그대로** 옮긴다(콜백은 호스트의 같은 이름 함수). 런처는 프래그먼트 생성 중(필드 초기화)에 등록해야 하므로 필드로 둔다.
  - `private val args by args<Args>()`
  - `private val viewModel by viewModels { { DualPaneViewModel() } }`
  - `private lateinit var binding: Binding`
  - `private lateinit var navigationFragment: NavigationFragment`
  - `private lateinit var panes: List<FileListFragment>` — 이 단계에서 크기 1.
  - `private val activePane: FileListFragment get() = panes[viewModel.activePane]`
- **기존 코드와의 관계**: 기존 `:135-144`의 인터페이스 중 `NavigationFragment.Listener`와 근거 대화상자 `Listener` 5개가 여기로 온다.

#### `FileListHostFragment.onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View`
- **추적**: HLD K2 / REQ-2-01
- **책임**: 호스트 레이아웃을 펼친다.
- **출력·사후조건**: `Binding.inflate(inflater, container, false)`의 `root`, `binding` 설정.
- **기존 코드와의 관계**: 기존 `:201-208`과 같은 모양.

#### `FileListHostFragment.onActivityCreated(savedInstanceState: Bundle?): Unit`
- **추적**: HLD K2, 3.5 / REQ-2-01, 09, 19, R3
- **책임**: 창·탐색 서랍 자식 프래그먼트를 만들거나 찾고, 탐색 서랍 리스너를 연결하고, 뒤로 가기 콜백을 정해진 순서로 등록하고, 고정 메뉴 열림 설정을 관찰한다.
- **입력·전제**: 메인 스레드. `binding` 설정됨. 기존 코드가 `onActivityCreated`를 쓰므로(기존 `:210`, `NavigationFragment.kt:36`) 같은 콜백을 쓴다 — 자식(`NavigationFragment`)의 `onActivityCreated`가 부모의 것 **뒤에** 불려 그 안에서 `listener`를 쓰기 때문이다(`Fragment.performActivityCreated`: 자기 `onActivityCreated` → `mChildFragmentManager.dispatchActivityCreated()`, fragment 1.8.8 바이트코드로 확인).
- **출력·사후조건**: `panes`·`navigationFragment` 설정, `navigationFragment.listener == this`, 활동 디스패처에 [창 전달 콜백, 서랍 콜백(열고 닫는 서랍일 때)] 순서로 등록.
- **오류**: 복원 시 자식이 없으면 캐스트 예외 — 기존 `:217-218` 패턴과 같다.
- **알고리즘**:
  1. `super.onActivityCreated(savedInstanceState)`.
  2. `savedInstanceState == null`이면:
     `val pane = FileListFragment().putArgs(FileListFragment.Args(args.intent))`, `navigationFragment = NavigationFragment()`,
     `childFragmentManager.commit { add(R.id.paneContainer, pane); add(R.id.navigationFragment, navigationFragment) }` — **한 트랜잭션, 창 먼저**. 아니면 `findFragmentById(R.id.paneContainer) as FileListFragment`, `findFragmentById(R.id.navigationFragment) as NavigationFragment`.
     `panes = listOf(pane)`.
     창을 먼저 더하는 이유: `NavigationFragment.onActivityCreated`가 `listener.observeCurrentPath(...)`(`NavigationFragment.kt:49`)로 창 뷰모델에 접근하고, 항목 바인딩이 `listener.currentPath`를 읽는다. 자식 FragmentManager는 더한 순서로 상태를 올리므로(복원도 저장된 순서) 창의 `onActivityCreated`(시작 경로 `resetTo`, 기존 `:338`)가 먼저 끝난다.
  3. `navigationFragment.listener = this`(기존 `:220`).
  4. 창 전달 콜백 — `val paneOnBackPressedCallback = object : OnBackPressedCallback(false) { override fun handleOnBackPressed() { activePane.paneOnBackPressedDispatcher.onBackPressed() } }`.
     `activePane.backPressedEnabledLiveData.observe(viewLifecycleOwner) { paneOnBackPressedCallback.isEnabled = it }`.
     `addOnBackPressedCallback(paneOnBackPressedCallback)`(`util/FragmentExtensions.kt:37-39`).
  5. `binding.drawerLayout?.let { addOnBackPressedCallback(DrawerLayoutOnBackPressedCallback(it)) }`(기존 `:278-280` 이동). **4보다 뒤에** 등록한다(3장 R3 근거).
  6. `if (binding.persistentDrawerLayout != null) { Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.observe(viewLifecycleOwner) { onPersistentDrawerOpenChanged(it) } }`(기존 `:354-358` 이동).
- **기존 코드와의 관계**: 기존 `:213-220`, `:278-280`, `:354-358`을 옮긴다. 창의 `:221-223`(제목·지원 액션바)은 창에 남는다(0.1).

#### `FileListHostFragment.onResume(): Unit`
- **추적**: HLD K2, 3.8 / (권한, 새 REQ 없음)
- **책임**: 저장소·알림 권한을 화면당 한 번 확인한다.
- **알고리즘**: 기존 `:373-382`를 그대로 옮기고 `viewModel`은 `DualPaneViewModel`을 가리킨다:
  `if (!viewModel.isNotificationPermissionRequested) ensureStorageAccess()`; `if (!viewModel.isStorageAccessRequested) ensureNotificationPermission()`.
- **입력·전제/사후조건**: 호스트의 `onResume`은 자식 창의 `onResume`보다 먼저 불린다(`Fragment.performResume`) — 권한 흐름은 창 상태에 의존하지 않으므로 순서가 결과를 바꾸지 않는다. 허용 결과는 런처 콜백에서 `refreshPanes()`.
- **기존 코드와의 관계**: 기존 `:373-382` 이동(창의 `onResume`은 삭제).

#### `FileListHostFragment.onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean`
- **추적**: HLD K1·K2 / REQ-2-09
- **책임**: 단축키를 활성 창에 넘긴다.
- **알고리즘**: `if (!this::panes.isInitialized) return false`; `return activePane.onKeyShortcut(keyCode, event)`.
- **입력·전제**: 창의 `onKeyShortcut`은 창의 `onActivityCreated` 이후에만 안전하다(`bottomActionMode`·`overlayActionMode` lateinit, 기존 `:548`, `:557`) — 기존 `FileListActivity`도 같은 전제였다. 단축키는 창이 초점을 얻은 뒤에만 오므로 실제로 만족된다.
- **기존 코드와의 관계**: 기존 `FileListActivity.kt:40`의 `fragment.onKeyShortcut` 호출 대상.

#### `NavigationFragment.Listener` 구현 (6개)
- **추적**: HLD K2, 3.4 / REQ-2-09
- **책임**: 탐색 서랍 동작을 활성 창에 위임하고, 서랍 닫기는 호스트가 한다.
- **멤버와 알고리즘**:
  - `override val currentPath: Path get() = activePane.currentPath`
  - `override fun navigateTo(path: Path) { activePane.navigateTo(path) }`
  - `override fun navigateToRoot(path: Path) { activePane.navigateToRoot(path) }`
  - `override fun navigateToDefaultRoot() { activePane.navigateToDefaultRoot() }`
  - `override fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit) { activePane.observeCurrentPath(owner, observer) }` — 이 단계는 창이 하나라 직접 위임. #6에서 활성 창이 바뀌면 따라가도록 바꾼다(4장).
  - `override fun closeNavigationDrawer() { binding.drawerLayout?.closeDrawer(GravityCompat.START) }` — 기존 `:1461-1463` 이동.
- **입력·전제**: `panes` 초기화 뒤(2.3 `onActivityCreated` 알고리즘 2의 순서로 보장).
- **기존 코드와의 관계**: 기존 `:1445-1463`(`NavigationFragment.kt:79-86` 인터페이스).

#### `FileListFragment.Listener` 구현 (3개 — 창이 호스트에 묻는 것)
- **추적**: HLD K2 / REQ-2-01
- `override val hasPersistentDrawer: Boolean get() = binding.persistentDrawerLayout != null`
- `override val isPersistentDrawerOpen: Boolean get() = binding.persistentDrawerLayout?.isDrawerOpen(GravityCompat.START) == true` — 기존 `:661-663`의 조건과 같은 값. `PersistentDrawerLayout.openDrawer/closeDrawer`는 `isOpen`을 애니메이션 전에 바로 바꾸므로(`ui/PersistentDrawerLayout.kt:323-329`, `:356-362`) 설정 관찰 직후 읽어도 목표 상태다.
- `override fun onNavigationIconClicked()` — 기존 `:449-454` 그대로: `binding.drawerLayout?.openDrawer(GravityCompat.START)`; `if (binding.persistentDrawerLayout != null) Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.putValue(!Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat)`.
- **입력·전제**: 호스트 뷰가 있을 때만 불린다(창의 뷰가 호스트 뷰 안에 있으므로).

#### `FileListHostFragment.onPersistentDrawerOpenChanged(open: Boolean): Unit` (private)
- **추적**: HLD K2 / REQ-2-01
- **책임**: 고정 메뉴 열림 설정에 맞춰 고정 메뉴를 열고 닫는다.
- **알고리즘**: 기존 `:569-576`에서 `binding.persistentDrawerLayout?.let { if (open) it.openDrawer(START) else it.closeDrawer(START) }`. 기존 `:577`의 `updateSpanCount()`는 창이 같은 설정을 따로 관찰해 부른다(2.4 `onActivityCreated` 7).
- **순서 근거**: 같은 `LiveData`의 관찰자는 등록 순서로 불리고, 호스트의 관찰자가 먼저 등록된다(호스트 뷰가 먼저 만들어짐). 처음 전달도 호스트 뷰 `ON_START`가 자식보다 먼저다(`Fragment.performStart`: 자기 `ON_START` → `mChildFragmentManager.dispatchStart()`). 그래서 창의 `updateSpanCount()`가 읽는 `isPersistentDrawerOpen`은 이미 새 값이다 — 기존과 같은 결과.
- **기존 코드와의 관계**: 기존 `:569-578` 분할.

#### 권한 요청 함수 (이동, 17개)
- **추적**: HLD K2, 3.8
- **책임·알고리즘**: 아래 함수를 **본문 그대로** 옮긴다. 바뀌는 것은 두 가지뿐이다 — `viewModel`이 `DualPaneViewModel`을 가리키고, `refresh()` 호출이 `refreshPanes()`가 된다. 근거 대화상자는 `show(this)`로 호스트의 자식이 되어 호스트가 결과를 받는다(`util/DialogFragmentExtensions.kt:11-13`, 각 대화상자의 `requireParentFragment() as Listener`).

| 함수 | 기존 줄 | 바뀌는 곳 |
|---|---|---|
| `private fun ensureStorageAccess()` | `:1465-1488` | — |
| `override fun onShowRequestAllFilesAccessRationaleResult(shouldRequest: Boolean)` | `:1490-1499` | — (`ensureNotificationPermission()` 호출 그대로) |
| `private fun requestAllFilesAccess()` | `:1501-1503` | — |
| `private fun onRequestAllFilesAccessResult(isGranted: Boolean)` | `:1505-1510` | `refresh()` → `refreshPanes()` |
| `override fun onShowRequestStoragePermissionRationaleResult(shouldRequest: Boolean)` | `:1512-1518` | — |
| `private fun requestStoragePermission()` | `:1520-1522` | — |
| `private fun onRequestStoragePermissionResult(isGranted: Boolean)` | `:1524-1535` | `refresh()` → `refreshPanes()` |
| `override fun onShowRequestStoragePermissionInSettingsRationaleResult(shouldRequest: Boolean)` | `:1537-1543` | — |
| `private fun requestStoragePermissionInSettings()` | `:1545-1547` | — |
| `private fun onRequestStoragePermissionInSettingsResult(isGranted: Boolean)` | `:1549-1554` | `refresh()` → `refreshPanes()` |
| `private fun ensureNotificationPermission()` | `:1556-1573` | — |
| `override fun onShowRequestNotificationPermissionRationaleResult(shouldRequest: Boolean)` + `requestNotificationPermission()` + `onRequestNotificationPermissionResult(isGranted)` + `onShowRequestNotificationPermissionInSettingsRationaleResult(shouldRequest)` + `requestNotificationPermissionInSettings()` + `onRequestNotificationPermissionInSettingsResult(isGranted)` | `:1575-1623` | — (`@RequiresApi(TIRAMISU)` 그대로) |

- 내부 클래스 `RequestAllFilesAccessContract`(`:1632-1640`), `RequestPermissionInSettingsContract`(`:1642-1653`)도 호스트로 옮긴다(호스트 안 `private class`).
- **오류**: 기존과 같다(런처 실행 실패는 시스템 처리, 결과가 거부면 근거 대화상자).

#### `FileListHostFragment.refreshPanes(): Unit` (private)
- **추적**: HLD 3.8 / (권한 허용 뒤 새로 고침)
- **알고리즘**: `panes.forEach { it.refresh() }`. 이 단계는 창 1개 → 기존 `refresh()` 한 번과 같다.
- **기존 코드와의 관계**: 기존 `:1508`, `:1527`, `:1552`의 `refresh()` 대체.

#### `FileListHostFragment.Args(intent: Intent) : ParcelableArgs` (`@Parcelize`)
- 기존 `FileListFragment.Args`(`:1655-1656`)와 같은 모양. 호스트는 `args.intent`를 창 1의 `FileListFragment.Args(args.intent)`로 그대로 넘긴다(REQ-2-19).

#### `FileListHostFragment.Binding` (private class, `inflate(inflater, root, attachToRoot)`)
- 필드: `root: View`, `drawerLayout: DrawerLayout?`, `persistentDrawerLayout: PersistentDrawerLayout?`. 컨테이너(`R.id.paneContainer`, `R.id.navigationFragment`)는 자식 프래그먼트 `add`에 ID로만 쓰므로 필드가 없어도 된다.
- 알고리즘: `FileListHostFragmentBinding.inflate(...)` → `FileListHostFragmentIncludeBinding.bind(root)`에서 두 서랍을 꺼낸다(기존 `:1684-1692`의 include 바인딩 패턴). 두 변형 레이아웃에 한쪽 서랍만 있으므로 생성되는 바인딩 필드는 nullable이다(기존과 같음).

### 2.4 K5 `FileListFragment` (수정 — 창)

#### 클래스 선언 (수정)
- 기존 `:135-144`에서 `NavigationFragment.Listener`, `ShowRequestAllFilesAccessRationaleDialogFragment.Listener`, `ShowRequestNotificationPermissionRationaleDialogFragment.Listener`, `ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.Listener`, `ShowRequestStoragePermissionRationaleDialogFragment.Listener`, `ShowRequestStoragePermissionInSettingsRationaleDialogFragment.Listener`를 뺀다. 나머지(`BreadcrumbLayout.Listener` … `NavigateToPathDialogFragment.Listener`)는 그대로.
- 제거하는 필드: 런처 5개(`:145-163`), `navigationFragment`(`:172`).
- 남는 것: `args`·`argsPath`(`:165-166`), `viewModel`(`:168`), 그 밖의 필드 전부.

#### `FileListFragment.Listener` (신규 중첩 인터페이스) / `private val listener: Listener get() = requireParentFragment() as Listener`
- **추적**: HLD K5 / REQ-2-01
- **책임**: 창이 화면 단위 자원(서랍)에 대해 호스트에 묻거나 요청하는 통로.
- **멤버**: `val hasPersistentDrawer: Boolean`, `val isPersistentDrawerOpen: Boolean`, `fun onNavigationIconClicked()` (호스트 구현 2.3).
- **입력·전제**: 창의 부모는 늘 `FileListHostFragment`(창을 만드는 곳은 호스트뿐 — `grep -rn "FileListFragment()"`로 확인, 지금은 `FileListActivity.kt:31` 한 곳). 대화상자들의 기존 패턴(`ShowRequestAllFilesAccessRationaleDialogFragment.kt:18-19`)과 같다.
- **오류**: 부모가 아니면 `IllegalStateException`/`ClassCastException`(구조 위반).

#### `FileListFragment.paneOnBackPressedDispatcher: OnBackPressedDispatcher` / `backPressedEnabledLiveData: LiveData<Boolean>` (신규)
- **추적**: HLD K5, 3.5 / R3
- **책임**: 창의 뒤로 가기 콜백(상위 폴더, 선택 툴바 닫기, speed dial 닫기)을 **창 전용 디스패처**에 모아, 호스트가 활성 창 하나만 정해진 우선순위 자리로 전달하게 한다.
- **정의**:
  `private val _backPressedEnabledLiveData = MutableLiveData(false)`; `val backPressedEnabledLiveData: LiveData<Boolean> = _backPressedEnabledLiveData`;
  `val paneOnBackPressedDispatcher = OnBackPressedDispatcher(null, Consumer { _backPressedEnabledLiveData.value = it })`.
  생성자 `OnBackPressedDispatcher(Runnable?, androidx.core.util.Consumer<Boolean>?)`는 activity 1.10.1의 공개 API다(`app/build.gradle:132`, 바이트코드로 확인). 두 번째 인자는 "켜진 콜백이 있음"이 바뀔 때마다 불린다. 이 디스패처에는 `setOnBackInvokedDispatcher`를 부르지 않는다 — 시스템 뒤로 가기는 늘 액티비티 디스패처를 거쳐 호스트의 전달 콜백으로 온다.
- **사후조건**: 창 프래그먼트 수명 동안 하나. 콜백은 뷰 수명(`viewLifecycleOwner`)으로 더해져 뷰 `ON_STOP`에서 빠지고 `ON_START`에서 다시 들어간다(`LifecycleOnBackPressedCancellable`, 기존과 같은 수명 규칙).
- **기존 코드와의 관계**: 기존 `:264-277`이 쓰던 `addOnBackPressedCallback`(액티비티 디스패처, `util/FragmentExtensions.kt:37-39`) 대신 쓴다.

#### `FileListFragment.onActivityCreated(savedInstanceState: Bundle?): Unit` (수정)
- **추적**: HLD K5 / REQ-2-01, 18, 19
- **책임**: 창 뷰·관찰을 설정한다(화면 단위 부분 제거).
- **알고리즘**(기존 순서 유지, 바뀌는 곳만):
  1. 기존 `:213-220`(탐색 서랍 생성·리스너) **삭제** → 호스트.
  2. 기존 `:221-223`(`activity.setTitle`, `setSupportActionBar(binding.toolbar)`) **그대로**(0.1, F1).
  3. 기존 `:224-262` 그대로.
  4. 뒤로 가기(기존 `:263-280`): 세 콜백을 **같은 순서로** `paneOnBackPressedDispatcher.addCallback(viewLifecycleOwner, …)`에 더한다 — ① 상위 폴더 콜백(기존 `:264-275`, 본문·`breadcrumbLiveData` 관찰 그대로) ② `overlayActionMode.onBackPressedCallback`(`:276`) ③ `SpeedDialViewOnBackPressedCallback(binding.speedDialView)`(`:277`). 서랍 콜백(`:278-280`)은 **삭제** → 호스트.
  5. 시작 경로 결정 기존 `:282-342` **그대로**(선택 모드 Intent 판단 `:286-320`, `ACTION_VIEW_DOWNLOADS` `:321-326`, 압축 파일 `:327-333`, 기본 디렉터리 `:335-337`, `pickOptions` 설정 `:339-341`). REQ-2-18·19의 창 1 몫.
  6. 기존 `:343-353` 그대로(관찰 순서 포함 — 보기 방식 관찰이 열 수 관찰보다 먼저여야 한다는 `:351-353` 주석 유지).
  7. 기존 `:354-358`을 `if (listener.hasPersistentDrawer) { Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.observe(viewLifecycleOwner) { updateSpanCount() } }`로 바꾼다(서랍 열고 닫기는 호스트, 창은 열 수만).
  8. 기존 `:359-370` 그대로.
- **입력·전제**: 호스트 뷰가 이미 있다(창 뷰가 호스트의 `R.id.paneContainer` 안). `listener` 접근 안전.
- **기존 코드와의 관계**: 기존 `:210-371`.

#### `FileListFragment.onResume()` — **삭제** (기존 `:373-382` → 호스트 2.3)

#### `FileListFragment.onOptionsItemSelected(item: MenuItem): Boolean` (수정)
- **추적**: HLD K2(툴바 메뉴 단추, "창이 콜백으로 요청") / REQ-2-01
- **알고리즘**: `android.R.id.home` 분기(기존 `:448-456`)만 `listener.onNavigationIconClicked(); true`로 바꾼다. 나머지 분기 그대로.
- **기존 코드와의 관계**: 지원 액션바가 남으므로 툴바의 내비게이션 아이콘(`res/layout/file_list_fragment_app_bar_include.xml:33-34`) 누르기는 지금처럼 `android.R.id.home` 메뉴 항목으로 이 함수에 온다.

#### `FileListFragment.onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean` — **변경 없음** (기존 `:547-567`)
- 호출자가 `FileListActivity` → `FileListHostFragment`로 바뀔 뿐. 하단·선택 툴바 메뉴의 단축키(`res/menu/file_list_paste.xml:16` v, `file_list_select.xml:16,24,57` x·c·a, `file_list_pick.xml:30` a)를 처리한다.

#### `FileListFragment.onPersistentDrawerOpenChanged(open: Boolean)` — **삭제** (기존 `:569-578` → 호스트 2.3 + 2.4 `onActivityCreated` 7)

#### `FileListFragment.updateSpanCount(): Unit` (private, 수정)
- **추적**: HLD K5 / REQ-2-01(동작 유지)
- **알고리즘**: 기존 `:656-669`에서 `binding.persistentDrawerLayout`(`:661-663`) 대신 `listener.isPersistentDrawerOpen`으로 차감 여부를 정한다. 공식(`screenWidthDp` − 320dp, `/180`, 최소 2)은 **그대로**(F3).
- **기존 코드와의 관계**: 기존 `:656-669`.

#### `FileListFragment.refresh(): Unit` (가시성 `private` → 공개)
- **추적**: HLD 3.8
- **알고리즘**: 본문 그대로(`viewModel.reload()`, 기존 `:721-723`). 호스트 `refreshPanes()`가 부른다.

#### 탐색 서랍용 공개 멤버 (수정 — `override` 제거)
- `val currentPath: Path get() = viewModel.currentPath`(기존 `:1445-1446`), `fun navigateToRoot(path: Path)`(`:1448-1451`), `fun navigateToDefaultRoot()`(`:1453-1455`), `fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit)`(`:1457-1459`): `NavigationFragment.Listener`를 구현하지 않게 되므로 `override`를 빼고 공개 함수로 둔다. 본문 그대로.
- `navigateTo(path: Path)`(`:767-771`)는 `BreadcrumbLayout.Listener`·`NavigateToPathDialogFragment.Listener`의 `override`로 그대로 남고 호스트가 부른다.
- `closeNavigationDrawer()`(`:1461-1463`) **삭제** → 호스트.

#### 권한 관련 멤버 — **삭제** (기존 `:1465-1623`, `:1632-1653` → 호스트 2.3)

#### `FileListFragment.Binding` (private class, 수정)
- 필드 `drawerLayout`·`persistentDrawerLayout`(`:1660-1661`) 삭제, `inflate`에서 `includeBinding.drawerLayout, includeBinding.persistentDrawerLayout` 인자(`:1692`) 삭제. `includeBinding`은 `persistentBarLayout`을 위해 남는다.

#### 그대로 두는 것 (확인 목록)
- `setHasOptionsMenu(true)`(`:198`), `onCreateOptionsMenu`·`onPrepareOptionsMenu`·검색 뷰(`:384-444`): 지원 액션바 경로 그대로.
- `onPickOptionsChanged`의 `requireActivity().title = title`(`:796`): 지원 액션바가 액티비티 제목을 툴바에 보이므로 그대로.
- 선택 결과 반환 `pickPaths`(`:815-843`)의 `requireActivity().setResult/finish`: 그대로(선택 모드는 창 1).
- 붙여넣기 `pasteFiles`(`:1167-1179`)·선택 메뉴 `updateOverlayToolbar`(`:850-914`)·`openInNewTask`(`:777-781`): 그대로(#8·#9의 자리).
- `swipeRefreshLayout` 조건(`:237-241`): 창 레이아웃 변형이 지금과 같은 모양(sw600dp-land는 앱바가 내용 위에 겹치지 않는 `LinearLayout`)이라 그대로.

### 2.5 `FileListViewModel` (수정)
- `_isRequestingStorageAccessLiveData`·`isStorageAccessRequested`·`_isRequestingNotificationPermissionLiveData`·`isNotificationPermissionRequested`(`FileListViewModel.kt:222-234`) 삭제 → `DualPaneViewModel`(2.2). 다른 사용처 없음(`grep -rn "isStorageAccessRequested\|isNotificationPermissionRequested"` → `FileListFragment.kt`만).

### 2.6 레이아웃

#### `res/layout/file_list_host_fragment.xml` (신규)
`FrameLayout`(match_parent) 안에 `<include layout="@layout/file_list_host_fragment_include" />` 하나. 기존 `file_list_fragment.xml:8-18`과 같은 이유(뷰 바인딩이 변형마다 다른 루트 ID를 싫어함 — 그 파일 `:13-16` 주석).

#### `res/layout/file_list_host_fragment_include.xml` (신규 — 열고 닫는 서랍)
기존 `layout/file_list_fragment_include.xml:8-56`을 복사하고 `:21-41`(`PersistentBarLayout` 하위)만 다음으로 바꾼다:
```xml
<FrameLayout
    android:id="@+id/paneContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```
- `DrawerLayout`(`@+id/drawerLayout`, `fitsSystemWindows=true`) > `IgnoreFitsSystemWindowsFullyDraggableDrawerContentLayout`(`fitsSystemWindows=true`) > `paneContainer`; `NavigationFrameLayout`(`@+id/navigationFragment`, 속성·주석 `:44-54` 그대로).
- 인셋: `DrawerLayout`은 `fitsSystemWindows` 자식에 인셋을 전달하고(`FullDraggableContainer`는 그대로 직속 자식), `FullDraggableContainer`는 인셋을 소비하지 않으며(`ui/IgnoreFitsSystemWindowsFullyDraggableDrawerContentLayout.kt:17`), `paneContainer`는 `fitsSystemWindows`가 없어 소비하지 않고 자식에 넘긴다 → 창 루트 `PersistentBarLayout`이 받는 인셋은 지금과 같다. **`paneContainer`에 `fitsSystemWindows="true"`를 주지 않는다**(주면 패딩으로 소비됨).

#### `res/layout-sw600dp-land/file_list_host_fragment_include.xml` (신규 — 고정 메뉴)
```xml
<merge xmlns:android="http://schemas.android.com/apk/res/android">
    <me.zhanghai.android.files.ui.PersistentDrawerLayout
        android:id="@+id/persistentDrawerLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fitsSystemWindows="true">
        <me.zhanghai.android.files.ui.NavigationFrameLayout
            android:id="@+id/navigationFragment"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:layout_gravity="start"
            android:background="?colorSurface" />
        <FrameLayout
            android:id="@+id/paneContainer"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </me.zhanghai.android.files.ui.PersistentDrawerLayout>
</merge>
```
- `fitsSystemWindows="true"`가 새로 붙는다(기존 `layout-sw600dp-land/file_list_fragment_include.xml:24-28`에는 없음). 고정 메뉴가 이제 화면 위·아래 끝까지 닿으므로 인셋이 필요하다: `PersistentDrawerLayout.dispatchApplyWindowInsets`는 `fitsSystemWindows`일 때만 서랍(왼·위·아래)과 내용(서랍 너비만큼 줄인 인셋)에 나눠 준다(`ui/PersistentDrawerLayout.kt:39-68`, `:70-99`). 서랍 목록 `NavigationRecyclerView`는 스스로 인셋을 패딩으로 쓴다(`navigation/NavigationRecyclerView.kt:49`, `:68-`). 실제 표시(상태 표시줄 아래 겹침 없음)는 T-14로 확인(미확인).

#### `res/layout/file_list_fragment_include.xml` (수정 — 창, 열고 닫는 서랍 화면)
`<merge>` 아래를 기존 `:21-41`(`PersistentBarLayout` > `CoordinatorLayout`[앱바, 내용] + 하단 막대 + speed dial)만 남긴다. `DrawerLayout`(`:10-14`, `:55`), `FullDraggableContainer`(`:16-19`, `:42`), `NavigationFrameLayout`(`:44-54`) 삭제.

#### `res/layout-sw600dp-land/file_list_fragment_include.xml` (수정 — 창, 고정 메뉴 화면)
`PersistentBarLayout`(`:10-14`) > `LinearLayout`(`:16-20`) > [앱바 include(`:22`), **내용 감쌈**] + 하단 막대(`:41`) + speed dial(`:43`). 기존 `PersistentDrawerLayout`(`:24-28`, `:38`)과 그 안의 `NavigationFrameLayout`(`:30-35`)을 지우고, 내용 include(`:37`)는 다음으로 감싼다:
```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1">
    <include layout="@layout/file_list_fragment_content_include" />
</FrameLayout>
```
감싸는 이유: `file_list_fragment_content_include.xml`의 루트가 `<merge>`라 `<include>`의 `layout_*` 덮어쓰기가 적용되지 않는다. 기존 `PersistentDrawerLayout`이 하던 "남은 높이 전부(weight 1)" 자리를 `FrameLayout`이 맡는다.

## 3. 뒤로 가기 순서 (R3 닫기)

### 3.1 지금 순서(불변식의 기준)
- 기존 `:264-280`은 한 프래그먼트의 `viewLifecycleOwner`로 4개를 등록한다: 상위 폴더 → 선택 툴바 → speed dial → 서랍. `addCallback(owner, cb)`는 owner가 `ON_START`가 될 때 디스패처 끝에 더하고(`OnBackPressedDispatcher$LifecycleOnBackPressedCancellable`, activity 1.10.1 바이트코드), 디스패처는 **나중에 더한 켜진 콜백**부터 부른다. 결과: 서랍 > speed dial > 선택 해제 > 상위 폴더 > 기본(종료). HLD 3.5의 불변식과 같다.
- 검색 뷰 펼침: 지원 액션바가 처리한다 — API 33 미만은 키 이벤트를 액티비티보다 먼저 받는 AppCompat 창 콜백(`AppCompatDelegateImpl.dispatchKeyEvent` → `onKeyUp(BACK)` → `onBackPressed()` → `ActionBar.collapseActionView()`), API 33 이상(앱이 `android:enableOnBackInvokedCallback="true"`, `AndroidManifest.xml:46`)은 `setSupportActionBar`가 켠 `Toolbar.setBackInvokedCallbackEnabled(true)`의 툴바 자체 콜백. 이 단계는 지원 액션바를 유지하므로 이 경로는 바뀌지 않는다.

### 3.2 HLD가 제시한 두 방식의 검토
- (가) "자식 창의 뷰가 만들어진 뒤 서랍 콜백을 등록": **효과 없음**. 디스패처에 실제로 들어가는 시점은 등록 호출이 아니라 owner의 `ON_START`이고, `Fragment.performStart`는 자기 `ON_START`(뷰 포함) 뒤에 `mChildFragmentManager.dispatchStart()`를 부르므로(fragment 1.8.8 바이트코드) 호스트 콜백은 언제 등록하든 창 콜백보다 **먼저** 들어간다 → 창 콜백이 서랍보다 우선이 되어 불변식이 깨진다. 액티비티가 멈췄다 다시 시작할 때(`ON_STOP`에서 빠지고 `ON_START`에서 다시 들어감)도 같다.
- (나) "창 콜백 조건에 '서랍이 열려 있지 않음'을 더함": `SpeedDialViewOnBackPressedCallback`·`ToolbarActionMode.onBackPressedCallback`은 자기 `isEnabled`를 내부 리스너에서 직접 정한다(`ui/SpeedDialViewOnBackPressedCallback.kt:13-23`, `ui/ToolbarActionMode.kt:33-37`, `:95`, `:105`). 조건을 더하려면 두 공용 클래스를 바꾸거나 감싸야 해 변경 범위가 넓어진다.

### 3.3 이 LLD의 방식 — 창 전용 디스패처 + 호스트 전달 콜백
- 창은 세 콜백을 자기 `paneOnBackPressedDispatcher`에 **지금과 같은 순서**로 더한다(2.4). 창 안의 우선순위(speed dial > 선택 > 상위 폴더)는 같은 디스패처·같은 owner·같은 순서라 그대로다.
- 호스트는 액티비티 디스패처에 같은 owner(호스트 `viewLifecycleOwner`)로 ① 창 전달 콜백 ② 서랍 콜백 순서로 등록한다(2.3 알고리즘 4·5). 같은 owner의 `ON_START`에서 등록 순서대로 들어가므로 **서랍 > 창** 순서가 자식 수명주기 시점과 무관하게 늘 성립한다.
- 전달 콜백의 `isEnabled` = 활성 창 디스패처의 "켜진 콜백 있음"(`backPressedEnabledLiveData`). 그래서 API 33 이상에서 액티비티 디스패처가 시스템 `OnBackInvokedCallback`을 등록·해제하는 조건(켜진 콜백 유무)도 지금과 같다 — 예측 뒤로 가기에서 "홈으로" 미리보기가 나오는 조건이 바뀌지 않는다.
- 전달 콜백은 `handleOnBackPressed`만 넘긴다. 창의 세 콜백이 `handleOnBackStarted/Progressed/Cancelled`를 구현하지 않으므로(위 파일들) 진행 이벤트를 넘기지 않아도 결과가 같다. (`OnBackPressedDispatcher.dispatchOnBack*`는 테스트용 표시가 붙은 API라 쓰지 않는다.)
- 결과 순서: [API 33 미만: 검색 펼침 접기(AppCompat, 디스패처 밖)] > 서랍 닫기 > speed dial 닫기 > 선택 해제 > 상위 폴더 > 기본(액티비티 종료). 기존과 같다.
- #6에서: 창이 둘이 되면 전달 콜백이 `activePane`을 따라 대상·`isEnabled` 관찰을 바꾼다(4장). HLD 3.5의 "자기 조건 && 활성 창"이 구조로 성립한다.
- 시험: T-06~T-09.

## 4. 확장 지점 (#6~#9가 붙을 자리)

| 하위 이슈 | 붙을 자리 | 이 단계에서 준비한 것 |
|---|---|---|
| #6 B (K3·K4) | `R.id.paneContainer`(두 레이아웃 변형)를 `DualPaneLayout`(창 컨테이너 둘)로 바꾼다. 호스트 `onActivityCreated` 알고리즘 2에서 선택 모드가 아니면 창 2(`FileListFragment.Args(Intent())`)를 더한다(HLD 3.7). `DualPaneViewModel`에 `setActivePane`/`togglePane()`/`paneMode`/`panePaths`/`isDualPaneEnabled`와 공개 `activePaneLiveData`를 더한다. 전달 콜백(3.3)은 `activePaneLiveData`를 관찰해 대상 창과 `backPressedEnabledLiveData` 관찰을 바꾼다. `observeCurrentPath`는 활성 창 경로를 따라가도록(예: `activePaneLiveData.switchMap`) 바꾼다. `refreshPanes()`는 이미 모든 창. **K5 미룬 항목**: D2 툴바 `MenuHost`(F1의 동등성 목록 포함), 열 수를 창 너비로(F3), 창 `Args`에 창 번호. | `panes` 목록, `activePane` 게터, K3의 `activePane`, 창 전용 디스패처, 호스트 전달 콜백, `refreshPanes()`, 창 `Listener` |
| #7 C (K7) | 창 앱바 include(`res/layout/file_list_fragment_app_bar_include.xml`)의 경로 표시줄 아래 새 줄. | 창 레이아웃이 창 단위 앱바를 가짐 |
| #8 D (K6) | `FileListFragment.pasteFiles`(`:1167-1179`)가 확인 창을 거치게. 창의 자식 대화상자(`ConfirmDeleteFilesDialogFragment` 패턴). | 바꾸지 않음 |
| #9 E | `updateOverlayToolbar`(`:850-914`)·`onOverlayActionModeMenuItemClicked`(`:916-955`)에 반대쪽 명령. 반대쪽 경로는 #6의 `panePaths`. | 바꾸지 않음 |

## 5. 테스트 설계

이 저장소에는 단위 테스트 소스·의존성이 없다(SRS C-3). 이 단계는 순수 함수(너비 판단 등)가 없어 단위 테스트 대상도 없다. **빌드 + 에뮬레이터 수동 확인 + 기준 APK와의 비교**로 회귀를 잡는다.

### 5.1 준비
1. 빌드(구현 브랜치 워크트리에서):
   `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ANDROID_HOME="$LOCALAPPDATA/Android/Sdk" ./gradlew assembleDebug --console=plain 2>&1 | tail -n 30` → `BUILD SUCCESSFUL`. 경고가 새로 생기면(특히 사용되지 않는 import·deprecated) 보고에 적는다.
2. 기준 APK: `origin/develop`(759c331 또는 그 뒤 develop 머리)를 다른 워크트리에서 같은 명령으로 빌드 → `baseline.apk`. 새 빌드 → `feature.apk`. 같은 에뮬레이터에서 번갈아 설치(`adb install -r`)하며 같은 절차의 화면을 캡처(`adb exec-out screencap -p > <이름>.png`)해 비교한다.
3. 기기 구성: SRS 3.2의 C1(폰 세로), C2(폰 가로 — 열고 닫는 서랍), C4(태블릿 가로 — 고정 메뉴, `adb shell wm size 1700x1300` + `adb shell wm density 320` 뒤 가로, 끝나면 `reset`). 이 PC의 AVD는 `Medium_Phone_API_36`(API 36)뿐 — API 33 미만 경로(T-09b)를 보려면 API 30 이하 AVD를 만들어야 하며, 못 만들면 "미확인"으로 보고한다.
4. 매 시험 뒤 `adb logcat -b crash -d`가 비어 있는지 확인(새 크래시 없음).
5. 시험용 폴더: `adb shell mkdir -p /sdcard/Download/lldA/a/b && adb shell touch /sdcard/Download/lldA/f1.txt /sdcard/Download/lldA/a/f2.txt`, 압축 파일 하나(`/sdcard/Download/lldA/t.zip`, PC에서 만들어 `adb push`).

### 5.2 시험 목록
| 테스트 | 대상 함수 | 경우(정상·경계·오류) | 절차 | 기대 결과 |
|---|---|---|---|---|
| T-00 빌드 | 전체 | 정상 | 5.1-1 | `BUILD SUCCESSFUL` |
| T-01 시작·구조 | `FileListActivity.onCreate`, `FileListHostFragment.onActivityCreated` | 정상 | 앱 아이콘으로 시작(C1·C2·C4). `adb shell dumpsys activity top > top.txt` 뒤 `FileListActivity` 절의 프래그먼트 목록 전체(중첩 `Child FragmentManager` 포함)를 읽어 확인 | 기준 APK와 같은 화면. 프래그먼트: `FileListHostFragment` > `FileListFragment`(1개)·`NavigationFragment` |
| T-02 기본 디렉터리 시작(REQ-2-18) | 창 `onActivityCreated` 5 | 정상 | 하위 폴더로 이동 → 최근 앱에서 지움 → 다시 시작. 설정에서 기본 디렉터리를 `/sdcard/Download`로 바꾸고 반복 | 매번 설정의 기본 디렉터리 |
| T-03 경로 Intent(REQ-2-19) | 호스트 `Args` → 창 `Args` | 정상 | `adb shell am start -a android.intent.action.VIEW -t inode/directory -d file:///sdcard/Download/lldA -n me.zhanghai.android.files/.filelist.FileListActivity` (URI 형식이 안 먹으면 앱의 "바로가기 만들기"로 만든 바로가기, 또는 다른 파일 관리자에서 열기) | 창 1이 `/sdcard/Download/lldA`. 기준 APK와 같음 |
| T-04 압축·다운로드·선택 Intent | 창 `onActivityCreated` 5 | 정상·경계 | (a) 파일 앱 등에서 `t.zip`을 이 앱으로 열기 (b) `adb shell am start -a me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS -n me.zhanghai.android.files/.filelist.FileListActivity` (c) `adb shell am start -a android.intent.action.GET_CONTENT -t "*/*" -n …/.filelist.FileListActivity` 후 파일 선택 (d) `OPEN_DOCUMENT_TREE`(다른 앱의 폴더 선택, 예: 설정 앱의 저장소 선택 화면이 이 앱을 쓰게) 또는 `am start -a android.intent.action.OPEN_DOCUMENT_TREE -n …` 후 "현재 폴더 열기" (e) `CREATE_DOCUMENT`(`-a android.intent.action.CREATE_DOCUMENT -t text/plain --es android.intent.extra.TITLE x.txt`) | (a) 압축 루트 (b) 다운로드 폴더 (c) 제목 "파일 열기", 선택 시 결과 반환 후 화면 닫힘 (d) 하단 막대 "현재 폴더 열기"로 반환 (e) 하단 파일 이름 입력 막대. 모두 기준 APK와 같음 |
| T-05 탐색 서랍 위임(REQ-2-09) | 호스트 `NavigationFragment.Listener` 6개 | 정상 | C1: 서랍 열기(아이콘·왼쪽 가장자리 쓸기) → "다운로드" 등 항목 → 서랍이 닫히고 창이 그 경로. 경로를 바꾸면 서랍의 선택 표시가 따라감. C4: 고정 메뉴 항목 누르기 | 기준과 같음(항목 선택 표시, 서랍 닫힘, 경로 이동) |
| T-06 뒤로 가기 — 서랍 우선(R3) | 3.3 | 경계 | C1: 하위 폴더 `lldA/a/b`에서 서랍 열기 → 뒤로 | 서랍만 닫히고 경로 그대로. 다시 뒤로 → `lldA/a` |
| T-07 뒤로 가기 — 창 안 순서(R3) | 3.3, 창 `onActivityCreated` 4 | 경계 | `lldA/a`에서 (1) 파일 길게 눌러 선택 → speed dial(+) 열기 → 뒤로 ×3 (2) 서랍 열기+선택 상태에서 뒤로 | (1) speed dial 닫힘 → 선택 해제 → 상위 폴더 순 (2) 서랍 먼저 닫힘 |
| T-08 뒤로 가기 — 기본·재시작(R3) | 3.3 | 경계 | (1) 기본 디렉터리 최상위에서 뒤로 (2) 서랍 연 채로 홈 → 앱 복귀 → 뒤로 (3) 회전 뒤 T-06·T-07 반복 (4) API 36 제스처 내비게이션에서 가장자리 쓸기로 같은 경우 — 하위 폴더에서는 앱 안 미리보기, 최상위에서는 "홈으로" 미리보기 | (1) 액티비티 종료(기준과 같음) (2)(3) 순서 유지(멈춤·재시작·재생성 뒤에도 서랍 우선) (4) 미리보기 종류가 기준과 같음 |
| T-09 검색 펼침 뒤로 | 지원 액션바 유지 | 경계 | (a) API 36: 검색 펼침(키보드 닫은 상태)에서 뒤로 (b) API 30 이하: 같은 경우, 서랍도 연 상태에서 뒤로 | (a)(b) 검색이 접힘(기준과 같음). (b) 기준과 같은 순서 — 미확인 시 보고 |
| T-10 툴바 메뉴 | 창 `onOptionsItemSelected`, 호스트 `onNavigationIconClicked` | 정상 | 넘침 메뉴의 보기·정렬·숨김 파일·새로 고침·위로·경로로 이동·새 작업·책갈피·바로가기 각 1회. C4: 메뉴 아이콘 눌러 고정 메뉴 접기/펼치기 | 기준과 같음. C4 접기/펼치기가 설정에 저장돼 회전 뒤에도 유지 |
| T-11 키보드 단축키·#10 | `FileListActivity.onKeyShortcut`, 호스트 `onKeyShortcut` | 정상·오류 | 에뮬레이터 창에 PC 키보드로(또는 `adb shell input keycombination 113 <키코드>`, 113=CTRL_LEFT): Ctrl+R(46) 새로 고침, Ctrl+H(36) 숨김 파일, Ctrl+L(40) 경로 대화상자, Ctrl+A(29) 전체 선택; 선택 중 Ctrl+C(31)·Ctrl+X(52); 붙여넣기 막대에서 Ctrl+V(50); 할당 없는 Ctrl+Q(45) | 앞의 것은 모두 기준과 같게 동작. Ctrl+Q는 아무 일 없음·크래시 없음 |
| T-12 메뉴 키 | (지원 액션바 경로 유지) | 정상 | `adb shell input keyevent 82` | 툴바 넘침 메뉴가 열림(기준과 같음) |
| T-13 권한 흐름 | 호스트 권한 함수 17개, `onResume`, `refreshPanes` | 정상·오류·경계 | `adb uninstall me.zhanghai.android.files` 뒤 새로 설치 → 시작: 모든 파일 접근 근거 대화상자 → 취소(알림 권한 요청으로 이어짐) → 앱 다시 시작 → 확인 → 설정 화면에서 허용 → 돌아오면 목록 새로 고침. 알림 권한: 거부 → 다시 시작 시 근거 대화상자 → "설정에서" 대화상자. 근거 대화상자가 떠 있는 채 회전 → 버튼 동작. 허용 상태에서 `adb shell appops set me.zhanghai.android.files MANAGE_EXTERNAL_STORAGE ignore` → 앱 복귀. API 갈래: API 36 AVD는 "모든 파일 접근"(API 30+)·알림 권한(API 33+) 갈래만 실행된다. `WRITE_EXTERNAL_STORAGE` 갈래(API 23~29: `ensureStorageAccess`의 `else if`, `onShowRequestStoragePermission*`·`requestStoragePermission*`·`onRequestStoragePermission*` 6개)와 API 33 미만의 알림 생략 갈래는 해당 API AVD(예: API 29)로 확인하고, AVD를 만들 수 없으면 **미확인**으로 보고한다(본문을 그대로 옮기는 함수라 코드 검수의 줄 대조로 보완) | 대화상자 순서·문구·결과가 기준과 같음. 한 번만 묻는다(대화상자가 둘 겹치지 않음). 회전 뒤 버튼이 호스트로 결과를 넘김(크래시 없음). 허용 뒤 목록이 새로 고쳐짐 |
| T-14 고정 메뉴 배치(C4) | sw600dp-land 레이아웃 4개 | 정상(의도된 변화) | C4에서 시작·고정 메뉴 펼침/접힘·붙여넣기 막대(복사 후)·speed dial 열기를 기준과 나란히 캡처 | 0.3대로만 다르다: 앱바·하단 막대·speed dial이 고정 메뉴 오른쪽 영역에. 고정 메뉴 맨 위 항목이 상태 표시줄에 가리지 않고, 맨 아래 항목이 내비게이션 막대에 가리지 않음. 목록 마지막 항목이 하단 막대·내비게이션 막대에 가리지 않음. 같은 확인을 (a) 3버튼 내비게이션과 제스처 내비게이션(`adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton` / `...navbar.gestural`) (b) 개발자 옵션 "RTL 레이아웃 방향 강제"(`adb shell settings put global debug.force_rtl 1` 후 앱 재시작, 끝나면 0) (c) 왼쪽 인셋이 있는 구성(기기를 반대 가로 방향으로 돌려 3버튼 내비게이션 막대가 왼쪽에 오게 — 태블릿형 sw600dp 구성은 막대가 하단에 남을 수 있으므로, 그때는 개발자 옵션 "디스플레이 컷아웃 시뮬레이션" 오버레이로 왼쪽 인셋을 만들고, 그래도 인셋이 생기지 않으면 **미확인**으로 적는다)에서 반복 — 기준 APK와 0.3의 차이 외에 같음 |
| T-15 격자 열 수 | 창 `updateSpanCount`, 호스트 `onPersistentDrawerOpenChanged` | 경계 | 격자 보기로 바꾼 뒤 C1·C2·C4(고정 메뉴 펼침/접힘)에서 한 줄 열 수 비교, C4에서 펼침↔접힘 전환 직후 | 기준과 같은 열 수, 전환 직후 바로 바뀜 |
| T-16 회전·상태 유지 | 전체 | 경계 | 하위 폴더 + 선택 2개 + 검색 펼침(검색어 입력) + 복사 목록 있는 상태에서 회전 ×2, 서랍 연 채로 회전 | 경로·선택·검색어·붙여넣기 막대·스크롤 위치가 기준과 같게 유지 |
| T-17 앱 업데이트 뒤 복원 | `FileListActivity.onCreate` 복원 분기 | 오류 | 개발자 옵션 "활동 유지 안 함" 켬(`adb shell settings put global always_finish_activities 1`) → `baseline.apk`로 하위 폴더 연 뒤 홈 → `adb install -r feature.apk` → 최근 앱에서 복귀. 끝나면 `... always_finish_activities 0` | **크래시 없음 + 기본 폴더로 시작**(런처로 연 화면이므로 설정의 기본 디렉터리, 이전에 열었던 하위 폴더가 아님). `adb logcat -b crash -d` 비어 있음. `dumpsys activity top`에 `FileListHostFragment` > `FileListFragment`·`NavigationFragment`만 있고 이전 판 `FileListFragment`가 `android.R.id.content`에 남아 있지 않음. 교체 분기가 실제로 발동했는지는 화면만으로 구분되지 않으므로(미발동이어도 같은 결과), 시험할 때만 `restoreHostFragment()`의 교체 갈래에 임시 `Log.d`를 넣어 logcat으로 확인하고 **커밋 전에 지운다**. 로그가 찍히지 않으면(시스템이 업데이트 때 저장 상태를 버린 경우) 교체 분기는 **미확인**으로 보고한다. 정상 회전(T-16)에서는 교체가 일어나지 않아야 한다(상태 유지) |
| T-18 새 작업·복사 목록 공유 | (변경 없음 확인) | 정상 | 넘침 메뉴 "새 작업" → 새 화면에서 파일 복사 → 원래 화면으로 돌아와 붙여넣기 막대 | 기준과 같이 공유됨(HLD D6) |
| T-19 프로세스 종료 | (변경 없음 확인, A6 편차) | 경계 | 하위 폴더에서 홈 → `adb shell am kill me.zhanghai.android.files` → 복귀 | 기준과 같음(기본 디렉터리 또는 Intent 경로에서 다시 시작 — 편차 #11) |

- 판정: T-14의 0.3 차이 외에 기준 APK와 다른 화면·동작이 하나라도 있으면 실패.
- 기록: 구현 PR 코멘트에 시험별 결과(통과/실패/미확인)와 캡처 파일 이름. 캡처는 `$MAIN_REPO/_workspace/`에 둔다.

## 6. 설계 피드백
설계 문서(SRS·HLD)는 고치지 않았다. PM·설계팀 확인이 필요한 것은 "질문"으로 표시했다.

- **F1 (결정됨: #6으로 미룸, PM 2026-10-28) HLD D2를 #5에서 하면 보이는 변화가 생긴다 → #6으로 미룸**: `setSupportActionBar`는 메뉴 호스팅만이 아니라 툴바에 다음을 한다(appcompat 1.7.1 바이트코드 확인): ① `ToolbarWidgetWrapper` 생성자가 테마 `actionBarStyle`의 `titleTextStyle`·`subtitleTextStyle`·`contentInsetStart/End`·`height`·`popupTheme`·`displayOptions`를 툴바에 적용(창 레이아웃의 `app:subtitleTextAppearance` 등을 덮어씀 — 그래서 지원 액션바를 빼면 제목·부제목 글꼴·여백이 바뀔 수 있음) ② 툴바 메뉴의 단축키(`file_list.xml:84-105` Ctrl+L·R·A·H)를 `AppCompatDelegateImpl.onKeyShortcut`이 처리 ③ 메뉴 키(`AppCompatActivity.dispatchKeyEvent` → `ActionBar.onMenuKeyEvent`)로 넘침 메뉴 열기 ④ 검색 펼침의 뒤로 가기 접기(API 33 미만 `AppCompatDelegateImpl.onBackPressed` → `collapseActionView`, API 33 이상 `Toolbar.setBackInvokedCallbackEnabled(true)`) ⑤ 내비게이션 아이콘 → `android.R.id.home` ⑥ 액티비티 제목 → 툴바 제목. HLD D2·K5는 이를 다루지 않는다. 창이 하나인 #5에서는 지원 액션바가 틀리지 않으므로(D2의 이유는 창이 둘일 때의 문제) 유지하고, D2는 창 2가 생기는 #6에서 하되 HLD에 ①~⑥의 동등성(특히 ① 글꼴 유지 방법)을 적어 달라. **PM 결정(2026-10-28): #6으로 미룸.**
- **F2 (결정됨: 오너 수용, 2026-10-28) 고정 메뉴 화면의 배치 변화는 #5에서 생긴다(D2가 아니라 분리 자체의 결과)**: 서랍(화면 단위)과 앱바·하단 막대·speed dial(창 단위)을 나누는 순간 생긴다. 앱바가 창 안으로 들어가고 고정 메뉴가 화면 위쪽까지 닿는 것은 HLD 5장 D2가 이미 적었고 오너가 수용했다(R8). D2·R8에 없는 것: ① 하단 막대·speed dial — 지금은 고정 메뉴까지 덮는 전체 폭(`layout-sw600dp-land/file_list_fragment_include.xml:10-14`, `:41-43`)이고 분리 뒤에는 창 영역에만 있다 ② 고정 메뉴가 화면 아래 끝까지 닿는다 ③ 초점(Tab)·화면 낭독 순서에서 고정 메뉴가 앱바보다 앞이 된다(뷰 계층 순서). 이슈 #5 본문의 "사용자에게 보이는 동작 변화 없음"의 예외로 ①~③을 받아들일지 확인을 요청했고, **오너가 수용했다**(#5 코멘트 "🗂 PM 결정 — LLD-A").
- **F3 (결정됨: #6으로 미룸, PM 2026-10-28) 격자 열 수를 창 너비로(HLD K5)는 #6으로**: 창이 하나일 때 창 너비 기준으로 바꾸면 고정 메뉴 실제 너비(320dp보다 작을 수 있음, `navigation/NavigationRecyclerView.kt:53-60`)·시스템 인셋 때문에 지금(`screenWidthDp` − 320dp, `:660-665`)과 열 수가 달라지는 화면이 생긴다. 두 창에서만 필요하므로 #6에서 바꾼다.
- **F4 창 레이아웃 파일 이름**: HLD K5의 `res/layout/file_list_pane_fragment.xml` 대신 기존 `file_list_fragment*.xml`을 창 레이아웃으로 쓴다(1장). 문서만 맞추면 된다.
- **F5 R3 방식**: HLD 3.5의 두 방식 대신 "창 전용 디스패처 + 호스트 전달 콜백"을 골랐다(3.2·3.3 근거 — 등록 시점 방식은 `ON_START` 순서 때문에 효과가 없음). HLD 3.5·R3에 반영을 권한다.
- **F6 HLD K2 서술 보강**: 고정 메뉴 열림 설정은 호스트(서랍 열고 닫기)와 창(열 수, 기존 `:577`) **둘 다** 관찰한다. 또 HLD 1.1·3.8의 줄 번호는 759c331에서도 맞다.
- **F7 #10의 실제 영향**: 2.1 분석대로 관찰 가능한 차이는 없을 것으로 본다(미확인, T-11로 확인). #10 본문의 "영향(미확인)"을 구현 PR에서 결과로 닫는다.

결정 기록: F1·F3은 #6으로 미룸(PM), F2는 오너 수용, 검수 m1은 방어 코드 추가(PM, 2.1 `restoreHostFragment`) — 모두 2026-10-28 #5 코멘트 "🗂 PM 결정 — LLD-A". F4~F6은 문서 반영 요청(동작 영향 없음).

## 7. 위험 처리 (HLD 6장)
| ID | 이 단계 처리 |
|---|---|
| R1 | #6(B)로 이월 — `FullDraggableContainer`와 K4 쓸기의 충돌은 K4가 생길 때 확인. 이 단계는 서랍 구조를 그대로 옮겨 서랍 열기 제스처를 T-05로만 확인. |
| R2 | #6(B)로 이월(fastscroll 띠). |
| R3 | **방식 확정**(3장) — 실제 순서 확인은 구현 PR의 T-06~T-09에서 닫는다. |
| R4 | **닫음** — 1장에 변경 파일 10개·범위 명시(5개 초과 공지). 구현 착수 전 PM에게 이 표로 알린다. |
| R5 | #6(B)로 이월(창이 하나라 해당 없음). |
| R6 | #6(B)·#7(C)로 이월. |
| R7·R8 | 닫힘(HLD). R8 범위 확장(F2)도 오너 수용(2026-10-28). |
| R9 | #8(D)로 이월. |
| R10 | #7(C)로 이월. |
| R11 | #8(D)로 이월. |

## 8. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-10-28 | v1 | 최초 작성 | 이슈 #5 LLD 단계(#10 흡수) | PM |
| 2026-10-28 | v1.1 | 0.3·F2 서술 정정(D2·R8이 이미 다룬 것과 새로 확인할 것 구분), T-01·T-13·T-14 보강, R3 표기 "방식 확정" | LLD 1회차 검수 m2·m3·m4 | 검수팀 |
| 2026-10-28 | v1.2 | T-14(c) 왼쪽 인셋 구성 방법 보강 | LLD 2회차 검수 n1 | 검수팀 |
| 2026-10-28 | v1.3 | PM 결정 반영: 0.1(F1·F3 확정), 0.3(F2 오너 수용), 2.1 `restoreHostFragment` 신규(m1)와 `onCreate` 갱신, 1장 표 1행, T-17 기대 결과, 6장 F1~F3 결정 표기, 7장 R8 | #5 "🗂 PM 결정 — LLD-A" | PM |
| 2026-10-28 | v1.4 | 2.1 `restoreHostFragment` 사후조건의 뷰모델 서술 정정, T-17 교체 분기 확인 방법(임시 로그, 미발동 시 미확인), 0.3에 업데이트 직후 상태 손실 한 줄 | LLD 4회차 검수 m-a·m-b·m-c | 검수팀 |
