# LLD — 파일 작업 표시(달리는 선 사람) (이슈 #30)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1.2 — PR #32 LLD 검수 2회차, 614ad6a), v1.3 #38·#40 개정 — 검수 통과(PR #41 issuecomment-6619906340, cf8b79e), v1.4 구현 결과 반영 — 코드 검수와 함께 검수 대기(0.2 R11·#40, 3.3 작업 준비·실행 메모. E-0~E-8 결과 표는 PR #41 issuecomment-6620946615, 검수 범위 v1.3: 0.2 R9·R11·#40, 0.3, 1.1, 2.12, 2.15, 3.1 U-A02, 3.2 M-08·M-16·M-18·M-19, 3.3, 4장 FB-6. 함께: HLD v1.5 3.4.1 마지막 항목·R11, FB-6 반영 줄(HLD v1.6 R9·R11, SRS v1.5 REQ-30-10)) |
| 근거 | SRS v1.3, HLD v1.3 (`docs/design/30-job-runner/`, PR #32 — v1.3은 이 LLD의 FB-1~FB-5 반영, 74dff1a), 오너 결정 이슈 #30 issuecomment-6617754053, #34 흡수(PM 결정). v1.3: SRS v1.5, HLD v1.6, 이슈 #38 요구사항 정리 v1, #40 흡수(PM 분류) |
| 기준 코드 | `origin/develop` 4437e43 — 인용 줄 번호는 이 커밋 기준. 단 v1.3에서 쓴 곳(0.2 R9 정정·R11·#40, 1.1, 2.12의 #38·#40 항목, 2.15, 3.3)은 #30 구현이 들어간 a1a8a3e 기준, 프레임워크는 android-35 소스 |
| 이슈 | #30 (흡수 #34). 하위 이슈 없음(HLD 2.1) — feature 브랜치 `feature/30-job-runner` 하나. v1.3: #38 (흡수 #40) — `feature/38-battery-saver` 한 브랜치에서 설계·구현(PM 지시) |
| HLD 구성요소 | K1·K1a·K2~K12 전부 |
| 추적 REQ | REQ-30-01~16 (v1.3 개정은 REQ-30-10, SRS 3.1·3.2) |

## 0. 범위와 전제

### 0.1 이 단계가 하는 것 / 하지 않는 것
- **한다**: 작업 쪽 상태 통로(K1·K1a), 서비스 연결과 #34 `finally` 정리(K2), `runOn` 결과 반환(K3), 오류 창 미해결 기록(K4), 화면 쪽 시간 규칙(K5)·표시 제어(K6)·단계 전달(K7)·표시할 창(K8), 선 사람 뷰와 자세(K9·K9a), 설정(K10), 모션 줄이기 판정(K11), 문자열(K12), 순수 부분 단위 시험(3장).
- **하지 않는다**: 작업 본문(`FileJobs.kt`의 각 작업 `run()`과 호출부)의 분기·반환값·예외(SRS C-1). 알림(REQ-30-16). `filelist/FileListLiveData.kt`와 `FileListFragment.kt`의 목록 적재·로딩 표시(`onFileListChanged` `:707-739`)는 건드리지 않는다(SRS C-2, 0.4).

### 0.2 HLD 미결(R1·R8~R11)과 #40 요약
| ID | 결론 | 근거 | 절 |
|---|---|---|---|
| R1 | `Toolbar`는 `CUSTOM` 자식 중 끝 쪽 정렬(LTR `RIGHT`, RTL `LEFT`) 자식을 **메뉴 보기를 놓은 다음** 그 안쪽(시작 쪽)에 놓는다 → 메뉴를 가리지 않는다. 검색 펼침(`expandItemActionView`) 때는 `EXPANDED`가 아니고 메뉴가 아닌 자식을 모두 **떼어**(`removeViewAt`) 숨겼다가 접을 때 다시 붙인다 → 선 사람 뷰는 떼어졌다 붙는다(분리·재부착 처리 필요, 2.12). | 프레임워크 `android/widget/Toolbar.java`(SDK sources android-35) `onLayout` `:1863`(메뉴) → `:2008-2011`(`addCustomViewsWithGravity(RIGHT)` 뒤 `layoutChildRight`), `addCustomViewsWithGravity` `:2134-2160`, `removeChildrenForExpandedActionView` `:2230-2241`, `expandItemActionView` `:2477`. 앱이 쓰는 androidx `appcompat:1.8.0`(`app/build.gradle:135`)의 `androidx/appcompat/widget/Toolbar.class`를 `javap -c`로 확인: `onLayout`이 `mMenuView`를 바이트 오프셋 208~252에서 배치한 뒤 1291·1349·1407에서 `addCustomViewsWithGravity`, `Toolbar$ExpandedActionViewMenuPresenter.expandItemActionView`가 `removeChildrenForExpandedActionView`(오프셋 184), `collapseItemActionView`가 `addChildrenForExpandedActionView`(68)를 부름 — 같은 구조. | 2.12, 2.14 |
| R8 | ① 뷰 초기화에서 `importantForAccessibility = YES`·`accessibilityLiveRegion = POLITE`·`contentDescription = null`을 정한다. ② `HIDDEN`이 되면 **바로** `contentDescription = null`. ③ 나타날 때는 `visibility = VISIBLE`을 **먼저** 하고 그 뒤 설명을 넣는다(HLD R8 문장의 순서와 반대 — 4장 FB-3). | `View.setContentDescription`(android-35 `View.java:11886-11903`)은 같은 값이면 아무 일도 하지 않고 돌아가며(`:11887-11893`), 값이 처음 생길 때 `importantForAccessibility`가 `AUTO`면 라이브 영역 내용 변경 대신 하위 트리 변경만 보낸다(`:11896-11898`) → ①. 라이브 영역 뷰의 변경 알림은 `notifyViewAccessibilityStateChangedIfNeeded`(`:15792-15796`)가 `sendAccessibilityEventUnchecked`로 보내는데 `sendAccessibilityEventUncheckedInternal`은 `isShown()`이 거짓이면 버린다(`:9032-9034`) → 보이기 전에 설명을 넣으면 그 알림이 사라진다 → ③. ②로 숨김 중에 옛 설명이 남지 않으므로 "나타날 때 옛 설명을 읽지 않는다"(HLD R8의 목적)는 ②가 지킨다. | 2.12 |
| R9 | **API 문서상 그렇다(구현 미확인).** API 26 이상 `ValueAnimator.areAnimatorsEnabled()`는 문서 주석에 따르면 절전 모드에서도 거짓이다. 값은 정적 `sDurationScale == 0`의 반대이고, `sDurationScale`은 창 관리자의 `getCurrentAnimatorScale()`(시작 때)과 `onAnimatorScaleChanged`(바뀔 때)로 채워진다. API 문서가 "개발자 옵션의 배율 0 **또는 절전 모드**(모든 애니메이션을 끔)"로 바뀐다고 명시한다. 창 관리자 쪽 구현(절전 모드가 배율을 0으로 보고하는 코드)은 SDK sources에 없어 **미확인**. API 23~25 경로(설정값 읽기)는 절전 모드를 반영하지 않는다 — 그 버전에서 절전 모드가 애니메이터를 끄는지 자체가 **미확인**. HLD R9 대응대로 넓게 판정(API 26 이상은 `areAnimatorsEnabled`)하고, SRS 1.4 "모션 줄이기" 정의를 설계팀이 넓힌다(4장 FB-1). **(v1.3 정정, #38)** 위 API 문서 서술은 실제 기기와 다르다: 오너 태블릿(Android 11)은 절전 모드가 켜져도 애니메이션 정책이 꺼져 있어 애니메이터가 돌았다. 그래서 K11이 `powerManager.isPowerSaveMode`를 따로 읽어 모든 API에서 같게 판정한다(2.15, SRS 1.4 ③, HLD R9 v1.4). | android-35 `android/animation/ValueAnimator.java:396-411`(문서 주석과 `return !(sDurationScale == 0)`), `:312-313`·`:338-339`, `android/view/WindowManagerGlobal.java:214-215`·`:236-240`. | 2.15 |
| R10 | K6 `updateFileJobIndicator`는 `consumeResult`를 **마지막 문장**에서 부르고, K5 `update`는 같은 입력을 다시 받아도 같은 출력을 내며(멱등) 자기가 보인 결과(`shownSeq`)를 두 번 소비하라고 하지 않는다. 상태 관찰 콜백 안에서 소비하면 `LiveData`가 재귀 호출하지 않고 콜백이 끝난 뒤 다시 돌린다. 설정 관찰·타이머·시작 콜백에서 소비하면 상태 관찰 콜백이 **동기적으로 중첩 호출**된다 — 그때 바깥 호출은 할 일을 다 마친 상태다. | androidx `lifecycle-livedata-core:2.11.0`(`app/build.gradle:140`) `LiveData.dispatchingValue` `javap -c`: 오프셋 0~12 — `mDispatchingValue`가 참이면 `mDispatchInvalidated = true` 후 반환(재귀 없음). | 2.7, 2.8 |
| R11 | **미확인 유지(HLD v1.5부터, PM 판단) — 구현 단계 에뮬레이터 확인으로 닫는다.** 절전 모드가 Animator를 끄는 기기에서 꺼짐 방송이 Animator 복구보다 먼저 오면 K11이 아직 참이라 다음 재판정(단계 변경·재부착)까지 정지 그림이 남는다. 타이머를 두지 않는다. 사전 확인(API 36 AVD, 이번 LLD에서 실행, 앱 없이 `dumpsys power`만): 이 재현에 쓸 설정 키는 `animation_disabled`가 아니라 `disable_animation`이다(4장 FB-6). 방송·복구 순서 자체는 아직 미확인이다. **(v1.4 실행 결과)** E-6에서 5회 중 1회 재현: 꺼짐 방송 때 `areAnimatorsEnabled()`가 아직 거짓이라 정지로 남았다. 고치지 않고 PM에게 보고한다(HLD R11). | 3.3 사전 확인 표, E-6. 시스템 서버 쪽 순서는 SDK 소스에 없음(HLD R11). | 2.12, 3.3 E-6 |
| #40 | **프레임워크 소스로 확인, 기기 재현도 확인(v1.4, E-7: 검색 펼침·회전 모두 수정 전 실패, 수정 후 통과).** `View.dispatchDetachedFromWindow`는 `onDetachedFromWindow()`를 `mAttachInfo`를 지우기 전에 부르므로 콜백 안에서 `isAttachedToWindow`가 아직 참이다. 부모에서 제거될 때 `isShown()`도 참이다. 그래서 `updatePoseAnimator(false)`의 `shouldRun`이 참이 되어 취소하지 못한다. 조건을 보지 않고 멈춘다(2.12 `stopPoseAnimator`). | android-35 `View.java:22935-22953`, 이슈 #40 근거(`ViewGroup.java:5627`·`:5636`), `FileJobIndicatorView.kt:125-126`·`:134-137`·`:171-175`. | 2.12 |

### 0.3 사용자에게 보이는 변화
- **의도된 변화**: 파일 작업 중 툴바 끝 쪽(메뉴 바로 앞)에 24dp 선 사람이 달리고, 끝나면 만세 또는 넘어짐 1.5초 뒤 사라진다. 설정 > 인터페이스에 "파일 작업 표시" 스위치(기본 켬).
- **그 결과로 생기는 변화**: 진행·결과 동안 제목·부제목 영역이 32dp 줄어 줄임표가 생길 수 있다(REQ-30-14). TalkBack이 상태 변화를 한 번씩 읽는다. 절전 모드(모든 API, 기기의 애니메이션 정책과 무관)에서는 정지 그림이 되고, 작업 중 켜고 끄면 바로 따른다(#38, REQ-30-10). 끈 뒤 결과가 사라질 때는 모션 줄이기가 아니므로 기존처럼 서서히 사라진다(2.12).
- **바뀌지 않는 것**: 작업이 없을 때의 툴바(뷰가 `GONE`), 알림·토스트·오류·충돌 창, 작업 결과, `runningJobCount`(`FileJobService.kt:100-102`).

### 0.4 #29와 겹치는 줄 (SRS C-2, HLD 4.1)
#29 구현 브랜치 `origin/feature/29-sftp-list-reload`가 `FileListFragment.kt`에서 바꾸는 곳(develop 기준 줄): `:173` 뒤 필드 3줄, `:350-361`의 `paneModeLiveData`·`activePaneLiveData` 관찰 블록 안 각 1줄(`updateFileListObserved()`), `:367` 뒤 1줄, `:602` 뒤 함수(`updateFileListObserved`), `:707-745`(`onFileListChanged`). `FileListLiveData.kt`·`DualPaneLayout.kt`(`isPaneShown` 추가)도 바꾼다.

이 LLD는 충돌을 피하려고 HLD K8의 "기존 창 표시 갱신과 같은 관찰 자리(`:348-358`)"를 쓰지 **않는다**(4장 FB-5): 관찰 등록은 `onActivityCreated` 끝(`:382` `})` 뒤, `:383` `}` 앞)에 함수 호출 한 줄, 새 함수는 `onActivityCreated`와 `menuProvider`(`:385`) 사이, 필드는 `Binding`(`:1617-1660`). 모두 #29가 바꾸는 줄과 붙어 있지 않다(가장 가까운 #29 변경 `:367`과 15줄 떨어짐). `FileListHostFragment.kt`·`DualPaneViewModel.kt`·앱바 레이아웃은 #29가 바꾸지 않는다. `isPaneShown`(#29)과 이 LLD의 `getFileJobIndicatorPaneIndex`(2.10)는 목적이 달라(보이는가 / 어느 하나에 둘까) 공유하지 않는다.

## 1. 변경 파일 (PROCESS_ROLES 11-1 범위 공지 — 5개 초과)

| # | 파일 | 신규/수정 | 요지 | 구성요소 |
|---|---|---|---|---|
| 1 | `app/src/main/java/me/zhanghai/android/files/filejob/FileJobBatch.kt` | 신규 | `FileJobState`, `FinishedBatch`, `FileJobBatch`(순수 집계) | K1a |
| 2 | `app/src/main/java/me/zhanghai/android/files/filejob/FileJobStateTracker.kt` | 신규 | 메인 스레드 `object`, `stateLiveData` | K1 |
| 3 | `app/src/main/java/me/zhanghai/android/files/filejob/FileJobService.kt` | 수정 | 시작·취소·끝·서비스 끝 보고, `finally` 정리(#34), 순수 보조 `runJobAndReport` | K2 |
| 4 | `app/src/main/java/me/zhanghai/android/files/filejob/FileJob.kt` | 수정 | `runOn(): Boolean`, `hasUnresolvedError` | K3 |
| 5 | `app/src/main/java/me/zhanghai/android/files/filejob/FileJobs.kt` | 수정 | `showErrorDialog`만(`:499-526`) — 미해결 기록 | K4 |
| 6 | `app/src/main/java/me/zhanghai/android/files/filejob/FileJobErrorAction.kt` | 수정 | 순수 판정 `isFileJobErrorResolved` | K4 |
| 7 | `app/src/main/java/me/zhanghai/android/files/filelist/FileJobIndicator.kt` | 신규 | `FileJobIndicatorPhase`, `getFileJobIndicatorPaneIndex` | K5·K8 |
| 8 | `app/src/main/java/me/zhanghai/android/files/filelist/FileJobIndicatorTimeline.kt` | 신규 | 순수 시간 규칙 | K5 |
| 9 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListHostFragment.kt` | 수정 | 표시 제어(관찰·타이머·소비) | K6 |
| 10 | `app/src/main/java/me/zhanghai/android/files/filelist/DualPaneViewModel.kt` | 수정 | 표시 단계 `LiveData` | K7 |
| 11 | `app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt` | 수정 | 표시할 창 관찰·그리기 전달, `Binding` 필드 | K8 |
| 12 | `app/src/main/java/me/zhanghai/android/files/ui/StickFigurePose.kt` | 신규 | `Joint`, `StickFigurePose`(순수 자세 함수) | K9 |
| 13 | `app/src/main/java/me/zhanghai/android/files/ui/FileJobIndicatorView.kt` | 신규 | 선 사람 뷰 | K9 |
| 14 | `app/src/main/java/me/zhanghai/android/files/util/AnimatorScale.kt` | 신규 | `Context.isReducedMotion()`, 순수 `isReducedMotion(Float)` | K11 |
| 15 | `app/src/main/java/me/zhanghai/android/files/settings/Settings.kt` | 수정 | `FILE_JOB_INDICATOR`(`:124-127` 뒤) | K10 |
| 16 | `app/src/main/res/values/donottranslate_prefs.xml` | 수정 | 키·기본값(`:53` 뒤) | K10 |
| 17 | `app/src/main/res/xml/settings.xml` | 수정 | `SwitchPreferenceCompat`(`:47` 뒤) | K10 |
| 18 | `app/src/main/res/values/strings.xml` | 수정 | 설정 제목·요약, 낭독 문구 셋(5개) | K10·K12 |
| 19 | `app/src/main/res/values-ko/strings.xml` | 수정 | 위의 한국어 5개 | K10·K12 |
| 20 | `app/src/main/res/layout/file_list_fragment_app_bar_include.xml` | 수정 | `toolbar`(`:27-36`) 자식으로 뷰 | K9a |
| 21 | `docs/STANDARDS_TRACEABILITY.md` | 수정 | 4장 #30 행(HLD 4장 — feature PR에서) | — |
| 22~28 | `app/src/test/java/me/zhanghai/android/files/{filejob/FileJobBatchTest, filejob/FileJobServiceTest, filejob/FileJobErrorActionTest, filelist/FileJobIndicatorTimelineTest, filelist/FileJobIndicatorTest, ui/StickFigurePoseTest, util/AnimatorScaleTest}.kt` | 신규 | 3장 | 시험 |

HLD 2장 표에 없던 파일: 6(K4 판정을 순수 함수로 떼어 시험하려고), 3의 `runJobAndReport`(#34 `finally` 경로 시험용). 둘 다 HLD 구성요소 안의 분리이고 동작은 HLD 그대로다.

### 1.1 #38·#40 변경 파일 (v1.3)
| 파일 | 신규/수정 | 요지 | HLD |
|---|---|---|---|
| `app/src/main/java/me/zhanghai/android/files/util/AnimatorScale.kt` | 수정 | `Context.isReducedMotion()`에 절전 모드, 순수 함수 추가 | K11 |
| `app/src/main/java/me/zhanghai/android/files/ui/FileJobIndicatorView.kt` | 수정 | 수신기, 재판정·전환, 분리 시 취소(#40) | K9, 3.4.1 |
| `app/src/test/java/me/zhanghai/android/files/util/AnimatorScaleTest.kt` | 수정 | U-A02 | K11 |

새 의존성 없음, 권한·문자열·레이아웃 변경 없음, `RuntimeBroadcastReceiver.kt`는 그대로 쓴다.

## 2. 함수 명세

### 2.1 `filejob.FileJobBatch` (K1a) — 새 파일 `FileJobBatch.kt`

```kotlin
data class FinishedBatch(val seq: Int, val isSuccessful: Boolean)
data class FileJobState(val runningCount: Int, val finished: FinishedBatch?)

class FileJobBatch {
    private val runningJobIds = mutableSetOf<Int>()
    private var hasFailure = false
    private var finished: FinishedBatch? = null
    private var nextSeq = 1
    val state: FileJobState get() = FileJobState(runningJobIds.size, finished)
    fun start(id: Int): Boolean
    fun finish(id: Int, isSuccessful: Boolean, isResultKept: Boolean): Boolean
    fun consume(seq: Int): Boolean
}
```
안드로이드 의존 없음. 스레드 안전하지 않다 — K1이 메인 스레드에서만 부른다.

#### `start(id: Int): Boolean`
- **추적**: HLD K1a / REQ-30-01, 08
- **책임**: 작업 하나의 시작을 센다. 진행 중 작업이 없을 때의 시작은 새 묶음을 연다.
- **입력·전제**: `id` = `FileJob.id`(임의 `Int`).
- **출력·사후조건**: 반환 = 상태가 바뀌었는가. `runningJobIds`가 비어 있었으면 먼저 `finished = null`, `hasFailure = false`(앞 묶음 결과를 지움, REQ-30-08). 그 뒤 `runningJobIds.add(id)`.
- **오류**: 없음. 이미 진행 중인 `id`(HLD R4 충돌)면 아무것도 바꾸지 않고 `false`.
- **알고리즘**: `if (id in runningJobIds) return false; if (runningJobIds.isEmpty()) { finished = null; hasFailure = false }; runningJobIds += id; return true`
- **기존 코드와의 관계**: 없음(새 집계). R4는 HLD대로 받아들인다.

#### `finish(id: Int, isSuccessful: Boolean, isResultKept: Boolean): Boolean`
- **추적**: HLD K1a / REQ-30-01, 04, 15, SRS A7·A8
- **책임**: 작업 하나의 끝을 센다. 마지막 작업이 끝나면 묶음 결과를 만든다.
- **입력·전제**: `isResultKept` = 지금 표시 설정이 켜져 있는가(K1이 넣는다 — 4장 FB-2).
- **출력·사후조건**: 반환 = 상태가 바뀌었는가. 진행 중이 아닌 `id`(모르는 id, 이미 끝난 id)는 무시하고 `false`(중복 무시 = 먼저 온 보고가 이김, HLD 3.1). 지운 뒤 `!isSuccessful`이면 `hasFailure = true`. 집합이 비면 `finished = if (isResultKept) FinishedBatch(nextSeq, !hasFailure) else null`, `nextSeq++`, `hasFailure = false`. 앞 결과가 남아 있어도 덮어쓴다(마지막 묶음만, A8 — 사실 새 시작이 이미 지웠으므로 남아 있을 수 없다).
- **오류**: 없음.
- **알고리즘**: `if (!runningJobIds.remove(id)) return false; if (!isSuccessful) hasFailure = true; if (runningJobIds.isEmpty()) { finished = if (isResultKept) FinishedBatch(nextSeq, !hasFailure) else null; ++nextSeq; hasFailure = false }; return true`
- **기존 코드와의 관계**: 없음.

#### `consume(seq: Int): Boolean`
- **추적**: HLD K1a / REQ-30-15, SRS A3
- **책임**: 화면이 보여 주기 시작한 결과를 지워 다른 화면·재생성 뒤에 다시 나오지 않게 한다.
- **출력·사후조건**: `finished?.seq == seq`일 때만 `finished = null`, `true`. 아니면(이미 소비됨, 더 새 결과로 바뀜) `false`.
- **오류**: 없음.
- **알고리즘**: 위 한 줄.

### 2.2 `filejob.FileJobStateTracker` (K1) — 새 파일 `FileJobStateTracker.kt`

```kotlin
object FileJobStateTracker {
    private val batch = FileJobBatch()
    private val _stateLiveData = MutableLiveData(batch.state)
    val stateLiveData: LiveData<FileJobState> get() = _stateLiveData
    @MainThread fun onJobStarted(id: Int)
    @MainThread fun onJobFinished(id: Int, isSuccessful: Boolean)
    @MainThread fun consumeResult(seq: Int)
}
```
- **추적**: HLD K1, D1 / REQ-30-01, 04, 11, 15
- **책임**: 메인 스레드에서 K1a를 갱신하고 바뀌었을 때만 `setValue`로 내보낸다(합쳐지지 않음 — `postValue` 금지, D1).
- **입력·전제**: 셋 다 메인 스레드(`@MainThread`). 작업 스레드는 K2가 메인으로 넘겨 부른다.
- **출력·사후조건**: 
  - `onJobStarted(id)`: `if (batch.start(id)) _stateLiveData.value = batch.state`
  - `onJobFinished(id, isSuccessful)`: `if (batch.finish(id, isSuccessful, Settings.FILE_JOB_INDICATOR.valueCompat)) _stateLiveData.value = batch.state` — 설정이 꺼진 채 끝난 묶음의 결과는 만들지 않는다(A7, 4장 FB-2). `SettingLiveData`는 공유 설정 변경 수신기를 늘 등록해 두므로(`settings/SettingLiveData.kt` `init()`·`onSharedPreferenceChanged`) 관찰자가 없어도 값이 최신이다. 값은 메모리에서 읽는다(디스크 읽기 없음 — 최초 적재는 `Settings` 초기화 때).
  - `consumeResult(seq)`: `if (batch.consume(seq)) _stateLiveData.value = batch.state`
- **오류**: 없음(던지지 않음, HLD 3.6). 메인이 아닌 스레드에서 부르면 `LiveData.setValue`가 `IllegalStateException` — 호출부(K2)가 막는다.
- **알고리즘**: 위.
- **기존 코드와의 관계**: `object` + `LiveData`는 `settings/Settings.kt`의 방식. `filejob/`이 `settings/`를 처음 참조한다(FB-2).

### 2.3 `filejob.FileJobService` (K2) — 수정

#### `internal fun runJobAndReport(run: () -> Boolean, onFinished: (isSuccessful: Boolean) -> Unit)` (파일 최상위, 새)
- **추적**: HLD K2 ②, R5 / REQ-30-04, #34
- **책임**: 작업 실행 뒤 성공 여부와 함께 끝 처리를 **반드시** 한 번 부른다. `Throwable`로 끝나도 실패로 부르고, 예외는 그대로 다시 던진다.
- **입력·전제**: `run` = `{ job.runOn(this) }`, `onFinished` = 정리·보고. 작업 스레드.
- **출력·사후조건**: `onFinished`가 정확히 한 번, `run`이 끝난 뒤 불린다. 인자는 `run`의 반환값, `run`이 던졌으면 `false`.
- **오류**: `run`이 던진 것을 잡지 않는다(그대로 위로). `onFinished`가 던지면 그것이 나간다(`run`이 던진 것을 가린다 — `onFinished`는 던지지 않게 만든다).
- **알고리즘**: `var isSuccessful = false; try { isSuccessful = run() } finally { onFinished(isSuccessful) }`
- **기존 코드와의 관계**: 지금 실행기 람다(`:57-63`)의 `runOn` 뒤 정리를 `finally`로 옮기는 틀. 안드로이드 의존이 없어 JVM 시험(U-S01~04)이 된다.

#### `private fun startJob(job: FileJob)` (서비스, `:54-67`)
- **추적**: HLD K2 ② / REQ-30-04, 16, #34
- **책임**: 기존 실행에 `finally` 정리와 끝 보고를 더한다.
- **입력·전제**: 메인 스레드(`onCreate` `:42-44`, companion `startJob` `:108`).
- **출력·사후조건**: 실행기 람다가 다음이 된다:
  ```kotlin
  val future = executorService.submit {
      runJobAndReport({ job.runOn(this) }) { isSuccessful ->
          synchronized(runningJobs) {
              runningJobs.remove(job)
              updateWakeWifiLockLocked()
          }
          mainExecutorCompat.execute { FileJobStateTracker.onJobFinished(job.id, isSuccessful) }
      }
  }
  ```
  `runningJobs[job] = future`·`updateWakeWifiLockLocked()`(`:64-65`)와 바깥 `synchronized`(`:56`)는 그대로.
- **오류**: 작업이 `Throwable`로 끝나도 `runningJobs`에서 지워지고 잠금이 갱신되며(#34 해소) 실패로 보고된다. 예외는 지금처럼 실행기의 `Future`에 담긴다(잡지 않음).
- **알고리즘**: 위.
- **기존 코드와의 관계**: `:57-63`을 대체. `mainExecutorCompat`(`compat/ContextCompat.kt:48-49`)는 서비스가 끝난 뒤에도 메인 루퍼로 넘긴다. 서비스 끝 뒤 늦게 끝난 작업이 `updateWakeWifiLockLocked()`를 부르는 것은 지금도 같다(`runOn`이 끼어들기 예외를 삼키고 정상 반환하므로 `:59-62`가 돈다).

#### `private fun cancelJob(id: Int)` (서비스, `:69-74`)
- **추적**: HLD K2 ③, 3.1 / REQ-30-04
- **책임**: 실행 중 취소한 작업을 즉시 실패(취소)로 보고한다.
- **입력·전제**: 메인 스레드(`FileJobReceiver` → companion `cancelJob`).
- **출력·사후조건**: `synchronized` 안에서 `val entry = runningJobs.removeFirst { it.key.id == id }`, `entry?.value?.cancel(true)`, 잠금 갱신(지금과 같음). 블록 밖에서 `if (entry != null) FileJobStateTracker.onJobFinished(id, false)`. 이미 끝나 지워진 작업은 보고하지 않는다(HLD K2 ③ "지운 작업마다"). 끼어들기 뒤 작업 스레드가 보내는 보고는 K1a가 무시한다.
- **오류**: 없음.
- **기존 코드와의 관계**: `util/CollectionExtensions.kt:65-66` `removeFirst(predicate)`가 지운 항목을 돌려준다.

#### `override fun onDestroy()` (`:76-87`)
- **추적**: HLD K2 ③ / REQ-30-04
- **책임**: 서비스 끝으로 취소한 작업마다 실패를 보고한다.
- **출력·사후조건**: `synchronized` 안에서 지우며 `val canceledJobIds = mutableListOf<Int>()`에 `id`를 모은다. 블록 밖에서 `canceledJobIds.forEach { FileJobStateTracker.onJobFinished(it, false) }`. 나머지(`instance = null`, `cancel(true)`, 잠금 갱신)는 그대로.
- **오류**: 없음.

#### companion `private fun startJob(job: FileJob, context: Context)` (`:104-113`)
- **추적**: HLD K2 ① / REQ-30-01, SRS Q1(모두 포함)
- **출력·사후조건**: 첫 문장에 `FileJobStateTracker.onJobStarted(job.id)`. 나머지 그대로. 작업 종류에 따른 제외 없음. 대기 중(`pendingJobs`)인 작업도 이때 센다(SRS 1.4 "진행 중"). 끝 보고는 메인으로 넘겨지므로 시작 보고보다 앞서지 않는다.

#### companion `fun cancelJob(id: Int)` (`:198-202`)
- **추적**: HLD K2 ③ / REQ-30-01, 04
- **출력·사후조건**: `pendingJobs.removeFirst { it.id == id }`가 작업을 돌려주면 `FileJobStateTracker.onJobFinished(id, false)`. 그 뒤 `instance?.cancelJob(id)`(그 안에서 보고). 
- **기존 코드와의 관계**: `util/CollectionExtensions.kt:53-63`이 지운 원소를 돌려준다.

`runningJobCount`(`:100-102`)·`jobCount`(`:51-52`)·`updateWakeWifiLockLocked`(`:91-93`)는 바꾸지 않는다(REQ-30-16, `settings/RootStrategyPreference.kt:35`).

### 2.4 `filejob.FileJob` (K3) — 수정 `FileJob.kt`

#### `internal var hasUnresolvedError: Boolean` (새 필드, 기본 `false`)
- **추적**: HLD K3·K4 / REQ-30-04(SRS Q2)
- **책임**: 이 작업이 오류 창에서 "다시 시도" 외의 답을 받은 적이 있는가.
- **입력·전제**: 작업 스레드에서만 쓰고 읽는다(K4는 `runBlocking`이 돌아온 뒤 작업 스레드에서 쓴다, `runOn`도 같은 스레드). `@Volatile` 불필요.

#### `fun runOn(service: FileJobService): Boolean` (`:19-33`, 반환형 `Unit` → `Boolean`)
- **추적**: HLD K3, D2, 3.2 / REQ-30-04, 16
- **책임**: 작업 본문을 실행하고 성공 여부를 돌려준다.
- **출력·사후조건**: `try { run(); !hasUnresolvedError }`, `catch (InterruptedIOException) { printStackTrace(); false }`, `catch (Exception) { printStackTrace(); showToast(e.toString()); false }`, `finally { notificationManager.cancel(id) }` — `try`를 식으로 쓴다. 잡는 범위·토스트·`finally`·`// TODO` 주석은 그대로.
- **오류**: `Exception`이 아닌 `Throwable`은 지금처럼 밖으로 나간다(K2가 실패로 처리).
- **기존 코드와의 관계**: 부르는 곳은 `FileJobService.kt:58` 하나(HLD 4장).

### 2.5 `filejob.FileJobs.kt` `showErrorDialog` (K4) — 수정 `:499-526`

#### `private fun FileJob.showErrorDialog(title, message, readOnlyFileStore, showAll, positiveButtonText, negativeButtonText, neutralButtonText): ErrorResult`
- **추적**: HLD K4, D3 / REQ-30-04, 16 (SRS Q2 오너 결정)
- **책임**: 기존대로 창을 띄워 답을 받고, 답이 "다시 시도"가 아니면 `hasUnresolvedError = true`.
- **입력·전제**: 작업 스레드. 인자·시그니처 그대로.
- **출력·사후조건**: 반환값(`ErrorResult`)과 `InterruptedException` → `InterruptedIOException` 변환은 그대로. 식 본문(`= try { … }`)을 블록 본문으로 바꾼다:
  ```kotlin
  // Captured before waiting for the answer, because the configuration (locale) may change meanwhile.
  val isPositiveRetry = positiveButtonText?.toString() == getString(R.string.retry)
  val result = try { /* 기존 runBlocking … */ } catch (e: InterruptedException) { throw InterruptedIOException().apply { initCause(e) } }
  if (!isFileJobErrorResolved(result.action, isPositiveRetry)) {
      hasUnresolvedError = true
  }
  return result
  ```
- **오류**: 기다리다 끼어들면 지금처럼 `InterruptedIOException`(기록하지 않음 — 어차피 취소).
- **알고리즘**: 위. 비교를 창을 띄우기 **전에** 하는 이유: 답을 기다리는 동안 언어가 바뀌면 서비스 자원이 새 언어로 바뀌어(서비스도 구성 변경을 받는다) 뒤에서 비교하면 "다시 시도"를 놓칠 수 있다. 호출부가 같은 순간 같은 자원으로 만든 글과 비교하므로 언어와 무관하다(HLD D3).
- **기존 코드와의 관계**: 호출부 13곳(`:706`, `:919`, `:1021`, `:1201`, `:1241`, `:1392`, `:1563`, `:1663`, `:1808`, `:1954`, `:2074`, `:2202`, `:2293` 등)은 바꾸지 않는다. 양성 버튼이 "건너뛰기"인 두 창(`:1201-1224`, `:1241-1264`)에서 POSITIVE는 미해결로 기록된다. 양성 버튼이 없는 압축 오류 창(`:706-722`, NEGATIVE·CANCELED → `InterruptedIOException`)도 기록되지만 결과는 어차피 실패다. `showConflictDialog`(`:547-`)는 바꾸지 않는다(충돌 "건너뛰기" = 성공).

### 2.6 `filejob.FileJobErrorAction.kt` (K4 순수 판정) — 수정

#### `internal fun isFileJobErrorResolved(action: FileJobErrorAction, isPositiveRetry: Boolean): Boolean` (파일 최상위, 새)
- **추적**: HLD K4 / REQ-30-04
- **책임**: 오류 창의 답이 오류를 해결하는 쪽(다시 시도)인가.
- **출력·사후조건**: `action == FileJobErrorAction.POSITIVE && isPositiveRetry`.
- **오류**: 없음.
- **기존 코드와의 관계**: 열거형(`:8-13`) 옆. 안드로이드 의존 없음(시험 U-E01~03).

### 2.7 `filelist.FileJobIndicatorTimeline` (K5) — 새 파일 `FileJobIndicatorTimeline.kt`

```kotlin
class FileJobIndicatorTimeline {
    class Output(
        val phase: FileJobIndicatorPhase,
        val nextUpdateMillis: Long?,   // 다시 계산할 절대 시각(uptime ms), 없으면 null
        val seqToConsume: Int?         // K6가 소비할 결과, 없으면 null
    )
    var phase: FileJobIndicatorPhase = FileJobIndicatorPhase.HIDDEN; private set
    private var runningSince = 0L
    private var resultUntil = 0L
    private var shownSeq: Int? = null
    private var isRestarting = false
    fun stop()
    fun update(nowMillis: Long, isEnabled: Boolean, state: FileJobState): Output
    companion object { const val MIN_RUNNING_MILLIS = 1000L; const val RESULT_MILLIS = 1500L }
}
```
안드로이드 의존 없음. 시각은 주입한다(K6가 `SystemClock.uptimeMillis()`).

#### `fun stop()`
- **추적**: HLD 3.3 "화면이 멈추면" / REQ-30-07, 15
- **책임**: 화면이 멈췄음을 기록한다. 다음 `update`가 "다시 시작" 규칙을 적용한다.
- **출력·사후조건**: `isRestarting = true`. 다른 상태는 그대로.
- **기존 코드와의 관계**: HLD는 "다시 시작 입력"을 K6가 준다고 했다. 시작 콜백과 `LiveData` 재전달 중 어느 것이 먼저 오는지에 상관없이 첫 `update`가 규칙을 적용하도록, 멈춤 때 표시하고 다음 계산에서 소비하는 방식으로 정한다.

#### `fun update(nowMillis: Long, isEnabled: Boolean, state: FileJobState): Output`
- **추적**: HLD K5, 3.3 / REQ-30-05, 06, 07, 08, 11, 15, SRS A3·A7
- **책임**: 지금 단계와 다음 계산 시각, 소비할 결과를 정한다.
- **입력·전제**: `nowMillis`는 단조 증가(같은 값 반복 허용). 메인 스레드에서 부르지만 클래스 자체는 스레드와 무관.
- **출력·사후조건**: 아래 알고리즘. **멱등**: 같은 `nowMillis`·입력으로 다시 부르면 같은 `phase`·`nextUpdateMillis`를 내고 `seqToConsume`은 처음에만 낸다(R10).
- **오류**: 없음.
- **알고리즘** (위에서 처음 맞는 것):
  1. `!isEnabled` → `phase = HIDDEN`, `isRestarting = false`, `Output(HIDDEN, null, state.finished?.seq)` (A7: 받은 결과 소비).
  2. `isRestarting`이면 `isRestarting = false`; 이때 `phase == RUNNING`이면 `runningSince = nowMillis`(멈춘 동안은 보인 달리기로 치지 않음). 결과 단계는 `resultUntil`을 그대로 둔다.
  3. `state.runningCount > 0` → `phase != RUNNING`이면 `phase = RUNNING`, `runningSince = nowMillis`(결과 중단, REQ-30-08). `Output(RUNNING, null, null)`.
  4. (`runningCount == 0`) `phase`가 `SUCCEEDED`·`FAILED`:
     - `state.finished != null && state.finished.seq != shownSeq`(멈춘 동안 새 묶음이 시작·끝남) → `phase = RUNNING`, `runningSince = nowMillis`, `Output(RUNNING, nowMillis + MIN_RUNNING_MILLIS, null)`.
     - `nowMillis < resultUntil` → `Output(phase, resultUntil, null)` (자기 소비 뒤 `finished == null` 포함 — HLD 3.3 "그대로").
     - 아니면 `phase = HIDDEN` 하고 5로.
  5. `phase == HIDDEN`: `state.finished != null && state.finished.seq != shownSeq` → `phase = RUNNING`, `runningSince = nowMillis`, `Output(RUNNING, nowMillis + MIN_RUNNING_MILLIS, null)`(화면 밖에서 끝난 결과도 달리기부터, REQ-30-15). 아니면 `Output(HIDDEN, null, null)`.
  6. `phase == RUNNING`:
     - `state.finished == null`(다른 화면이 소비, A3) → `phase = HIDDEN`, `Output(HIDDEN, null, null)`.
     - `nowMillis - runningSince < MIN_RUNNING_MILLIS` → `Output(RUNNING, runningSince + MIN_RUNNING_MILLIS, null)`(REQ-30-07).
     - 아니면 `phase = if (finished.isSuccessful) SUCCEEDED else FAILED`, `resultUntil = nowMillis + RESULT_MILLIS`, `shownSeq = finished.seq`, `Output(phase, resultUntil, finished.seq)`(결과 진입 때 소비, HLD D8).
- **기존 코드와의 관계**: HLD 3.3 표를 그대로 옮기고 두 가지를 정했다: 결과 단계 중 `shownSeq`와 다른 결과가 오면(멈춘 동안 새 묶음) 다시 달리기(4 첫 항목, FB-4), 결과 단계에서 시간이 다 되면 같은 호출에서 5를 다시 본다(멈춘 동안 끝난 결과를 놓치지 않음).

### 2.8 `filelist.FileListHostFragment` (K6) — 수정

#### 새 필드
```kotlin
private lateinit var fileJobIndicatorTimeline: FileJobIndicatorTimeline
private val fileJobIndicatorUpdateRunnable = Runnable { updateFileJobIndicator() }
```

#### `onActivityCreated(savedInstanceState: Bundle?)` 끝(`:185-189` 뒤)에 추가
- **추적**: HLD K6, D5 / REQ-30-03, 07, 11, 15
- **책임**: 뷰 수명마다 새 시간 규칙을 만들고 관찰을 건다.
- **출력·사후조건**:
  ```kotlin
  // A new timeline for each view, so that the minimum running time continues across pane changes
  // but restarts after recreation.
  fileJobIndicatorTimeline = FileJobIndicatorTimeline()
  viewModel.setFileJobIndicatorPhase(FileJobIndicatorPhase.HIDDEN)
  FileJobStateTracker.stateLiveData.observe(viewLifecycleOwner) { updateFileJobIndicator() }
  Settings.FILE_JOB_INDICATOR.observe(viewLifecycleOwner) { updateFileJobIndicator() }
  viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) { updateFileJobIndicator() }
      override fun onStop(owner: LifecycleOwner) {
          binding.root.removeCallbacks(fileJobIndicatorUpdateRunnable)
          fileJobIndicatorTimeline.stop()
      }
  })
  ```
  `setFileJobIndicatorPhase(HIDDEN)`: `DualPaneViewModel`은 회전 뒤에도 남아 옛 단계(예: 만세)를 갖고 있으므로 새 뷰의 창이 그것을 그리지 않게 지운다(HLD D8 — 결과가 잘림).
- **오류**: 없음.
- **기존 코드와의 관계**: 기존 `viewLifecycleOwner` 관찰(`:152-189`)과 같은 방식. `FileListFragment.kt:374-382`의 `DefaultLifecycleObserver` 사용례.

#### `private fun updateFileJobIndicator()`
- **추적**: HLD K6, R10 / REQ-30-03, 05~08, 11, 15
- **책임**: K5를 다시 계산해 K7에 쓰고 다음 계산을 예약하고, 결과를 소비한다.
- **입력·전제**: 메인 스레드. 뷰가 있어야 한다.
- **출력·사후조건**:
  ```kotlin
  if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
  val nowMillis = SystemClock.uptimeMillis()
  val output = fileJobIndicatorTimeline.update(
      nowMillis, Settings.FILE_JOB_INDICATOR.valueCompat, FileJobStateTracker.stateLiveData.valueCompat
  )
  viewModel.setFileJobIndicatorPhase(output.phase)
  val root = binding.root
  root.removeCallbacks(fileJobIndicatorUpdateRunnable)
  output.nextUpdateMillis?.let {
      root.postDelayed(fileJobIndicatorUpdateRunnable, (it - nowMillis).coerceAtLeast(0))
  }
  // Must be last, because consuming may call this again synchronously.
  output.seqToConsume?.let { FileJobStateTracker.consumeResult(it) }
  ```
  관찰 콜백의 인자 대신 현재 값을 읽는다(어느 콜백에서 불려도 같은 입력).
- **오류**: 없음. 시작됨 상태가 아니면 아무것도 하지 않는다(멈춤 뒤 남은 예약 대비 — `onStop`이 이미 지운다).
- **알고리즘**: 위. **재진입(R10)**: 상태 관찰 콜백 안에서 `consumeResult`를 부르면 `LiveData.dispatchingValue`가 재귀하지 않고 콜백 뒤 다시 돌린다(0.2 R10 근거). 설정 관찰·타이머·시작 콜백에서 부르면 상태 관찰 콜백이 동기적으로 이 함수를 다시 부른다 — 바깥 호출은 마지막 문장에 있으므로 남은 일이 없고, 안쪽 호출은 `finished == null`(자기 소비)을 받아 K5 4번 "그대로"를 낸다(시험 U-L08). 둘 다 같은 결과다.
- **기존 코드와의 관계**: 시각은 `postDelayed`와 같은 시계(`uptimeMillis`)라 예약이 어긋나지 않는다.

### 2.9 `filelist.DualPaneViewModel` (K7) — 수정 (`:40-51` 뒤)

```kotlin
private val _fileJobIndicatorPhaseLiveData = MutableLiveData(FileJobIndicatorPhase.HIDDEN)
val fileJobIndicatorPhaseLiveData: LiveData<FileJobIndicatorPhase> get() = _fileJobIndicatorPhaseLiveData
val fileJobIndicatorPhase: FileJobIndicatorPhase get() = _fileJobIndicatorPhaseLiveData.valueCompat
fun setFileJobIndicatorPhase(phase: FileJobIndicatorPhase)
```
- **추적**: HLD K7 / REQ-30-03
- **책임**: 화면 하나의 표시 단계를 두 창이 같이 보게 한다.
- **출력·사후조건**: `setFileJobIndicatorPhase`는 같은 값이면 돌아가고(`setPaneMode` `:46-51`과 같음), 아니면 `value = phase`.
- **오류**: 없음.

### 2.10 `filelist.FileJobIndicator.kt` (K5·K8) — 새 파일

```kotlin
enum class FileJobIndicatorPhase { HIDDEN, RUNNING, SUCCEEDED, FAILED }
```

#### `internal fun getFileJobIndicatorPaneIndex(isDualPaneEnabled: Boolean, isDualPane: Boolean, activePane: Int): Int`
- **추적**: HLD K8, D7 / REQ-30-03
- **책임**: 선 사람을 그릴 창 번호.
- **출력·사후조건**: `!isDualPaneEnabled` → `0`(고르기 모드, 창 하나). `isDualPane` → `1`(끝 쪽 창). 아니면 `activePane`(보이는 창).
- **오류**: 없음.
- **기존 코드와의 관계**: `filelist/PaneIndicator.kt`의 `getPaneIndicatorMode`와 같은 순수 함수 형식.

### 2.11 `filelist.FileListFragment` (K8) — 수정

#### `onActivityCreated` 끝(`:382` `})` 뒤)에 `observeFileJobIndicator()` 한 줄
- 0.4의 이유로 기존 관찰 블록(`:349-358`)에 넣지 않는다.

#### `private fun observeFileJobIndicator()` (새, `onActivityCreated` 뒤)
- **추적**: HLD K8 / REQ-30-02, 03, 14
- **책임**: 단계·창 모드·활성 창이 바뀔 때 자기 뷰를 갱신하게 한다.
- **출력·사후조건**:
  ```kotlin
  val viewLifecycleOwner = viewLifecycleOwner
  dualPaneViewModel.fileJobIndicatorPhaseLiveData.observe(viewLifecycleOwner) { updateFileJobIndicator() }
  dualPaneViewModel.paneModeLiveData.observe(viewLifecycleOwner) { updateFileJobIndicator() }
  dualPaneViewModel.activePaneLiveData.observe(viewLifecycleOwner) { updateFileJobIndicator() }
  ```
  같은 `LiveData`에 관찰자를 하나 더 두는 것은 기존 관찰에 영향이 없다.

#### `private fun updateFileJobIndicator()` (새)
- **추적**: HLD K8 / REQ-30-02, 03, 14
- **출력·사후조건**:
  ```kotlin
  val paneIndex = getFileJobIndicatorPaneIndex(
      dualPaneViewModel.isDualPaneEnabled, dualPaneViewModel.paneMode.isDualPane,
      dualPaneViewModel.activePane
  )
  binding.fileJobIndicator.phase =
      if (args.paneIndex == paneIndex) dualPaneViewModel.fileJobIndicatorPhase
      else FileJobIndicatorPhase.HIDDEN
  ```
  그리는 창이 바뀌면 옛 창의 뷰는 `HIDDEN`(사라짐), 새 창의 뷰는 그 단계로 나타난다(HLD R2 — 새 뷰가 같은 문구를 한 번 더 읽을 수 있음, 수동 확인 M-11).
- **오류**: 없음.
- **기존 코드와의 관계**: `updatePaneIndicator`(`:607-`)가 같은 값들을 같은 방식으로 읽는다. 게시 지연(`schedulePaneIndicatorUpdate` `:598-601`)은 쓰지 않는다 — 누르기 처리와 무관하다.

#### `Binding` (`:1617-1660`)
- 생성자 인자 `val fileJobIndicator: FileJobIndicatorView`를 `toolbar` 다음에 더하고 `appBarBinding.fileJobIndicator`를 넘긴다.

### 2.12 `ui.FileJobIndicatorView` (K9) — 새 파일(#30), v1.3에서 수정(#38·#40)

```kotlin
class FileJobIndicatorView : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    var phase: FileJobIndicatorPhase   // 기본 HIDDEN
}
```
생성자 형식은 `ui/CrossfadeSubtitleToolbar.kt:34-40`.

#### `init`
- **추적**: HLD 3.4·3.5, R8 / REQ-30-09, 12, 13
- **출력·사후조건**:
  - `isClickable = false`, `isLongClickable = false`, `isFocusable = false`(누름 효과·키보드 초점·클릭 동작 없음 — "두 번 눌러 활성화" 안내 없음).
  - `importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES` — 설명보다 **먼저**(R8 ①, `View.java:11896-11898`).
  - `accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE` — API 19부터 있는 `View` 속성이라 minSdk 23에서 `ViewCompat` 없이 바로 쓴다(HLD 3.5의 `ViewCompat.setAccessibilityLiveRegion`과 같은 효과).
  - `contentDescription = null`, `visibility = GONE`(레이아웃 값과 같게).
  - 선 붓: `Paint(ANTI_ALIAS_FLAG)`, `style = STROKE`, `strokeWidth = context.dpToDimension(2)`, `strokeCap = ROUND`, `strokeJoin = ROUND`, `color = context.getColorByAttr(androidx.appcompat.R.attr.colorControlNormal)`(`util/ContextExtensions.kt:116-117` — 색 상태 목록의 기본 색, HLD 3.4). 머리는 같은 붓의 원(채우지 않음).
  - 자세 애니메이터: `ValueAnimator.ofFloat(0f, 1f)`, 갱신마다 `posePhase = animatedFraction; invalidate()`.
- **기존 코드와의 관계**: 뷰의 `context`는 앱바의 `android:theme="?actionBarTheme"`(`file_list_fragment_app_bar_include.xml:18`)를 받은 문맥이라 `colorControlNormal` = `?android:textColorPrimary`(`res/values/styles.xml:61-64`). 테마가 바뀌면 화면이 다시 만들어지므로 색을 다시 읽지 않는다.

#### `var phase: FileJobIndicatorPhase` (설정자)
- **추적**: HLD K9, 3.4, 3.5, R8 / REQ-30-05, 06, 10, 12, 14
- **책임**: 단계를 그림·보임·설명·애니메이션에 반영한다.
- **입력·전제**: 메인 스레드. 뷰가 떼어져 있어도(검색 펼침 R1) 불릴 수 있다.
- **출력·사후조건**:
  - 같은 값 → 아무것도 안 함(반복 낭독 없음, REQ-30-12).
  - **순서 규칙**: 맨 먼저 `field = value`(새 단계)와 `animator.cancel()`을 하고, `isReducedMotion`·`posePhase`를 정한 **뒤에** `visibility`를 바꾼다. `visibility` 대입은 동기로 `onVisibilityChanged` → `updatePoseAnimator(restart = false)`를 부르므로, 그 안에서 새 단계와 새 판정만 보이게 하기 위해서다(옛 단계로 애니메이터가 시작되지 않음).
  - `HIDDEN`으로: `contentDescription = null`(R8 ②); 자세 애니메이터 취소; `isReducedMotion = context.isReducedMotion()`; 모션 줄이기이거나 `!isShown`이면 `animate().cancel(); visibility = GONE; alpha = 1f`; 아니면 `animate().alpha(0f).setDuration(FADE_OUT_MILLIS = 150).withEndAction { visibility = GONE; alpha = 1f }`.
  - `RUNNING`·`SUCCEEDED`·`FAILED`로 (이 순서대로): `animator.cancel()`; `animate().cancel(); alpha = 1f`; `isReducedMotion = context.isReducedMotion()`(나타날 때·자세 바뀔 때마다 판정, REQ-30-10); `posePhase = if (isReducedMotion) STATIC_POSE_PHASE[phase] else 0f`(정지 자세: 달리기 `0.25f`, 만세·넘어짐 `1f` — HLD 3.4); `visibility = VISIBLE`; **그 다음** `contentDescription = getString(설명)`(R8 ③) — 설명 = `RUNNING` → `R.string.file_job_indicator_running_description`, `SUCCEEDED` → `…_succeeded_description`, `FAILED` → `…_failed_description`; `updatePoseAnimator(restart = true)`; `invalidate()`.
- **오류**: 없음.
- **알고리즘**: 위. 사라짐 효과 중 새 단계가 오면 `animate().cancel()`로 멈추고 `alpha = 1f`로 되돌린다.
- **기존 코드와의 관계**: `View.animate()`(ViewPropertyAnimator)는 배율 0에서 끝으로 바로 건너뛰지만 모션 줄이기는 위에서 먼저 걸러 효과 자체를 쓰지 않는다(REQ-30-10 "서서히 사라지는 효과 없이").

#### `private fun updatePoseAnimator(restart: Boolean)`
- **추적**: HLD 3.4 "멈춤", R3 / SRS 3.2
- **책임**: 애니메이터를 돌릴 조건일 때만 돌린다.
- **출력·사후조건**: `shouldRun = phase != HIDDEN && !isReducedMotion && isAttachedToWindow && windowVisibility == VISIBLE && isShown`. `isShown`은 조상 뷰의 보임만 보고 창 보임을 반영하지 않으므로 `windowVisibility`를 따로 본다(이 조건이 있어야 `onWindowVisibilityChanged` 훅이 실효가 있다. 액티비티가 멈추면 decor가 INVISIBLE이 되어 `isShown`도 거짓이 되지만 그 경로에 기대지 않는다).
  - `!shouldRun` → `stopPoseAnimator()`(v1.3에서 뗌, 동작 같음).
  - `shouldRun && (restart || !animator.isStarted)` → `animator.cancel()`(시작 중이면 멈춤) 뒤 단계에 맞게 설정하고 `start()`: `RUNNING` — `duration = RUNNING_CYCLE_MILLIS (600)`, `repeatCount = INFINITE`, `repeatMode = RESTART`, `interpolator = LinearInterpolator()`; `SUCCEEDED` — `300`, `repeatCount = 0`, `FastOutSlowInInterpolator()`; `FAILED` — `400`, `repeatCount = 0`, `FastOutSlowInInterpolator()`(`CrossfadeSubtitleToolbar.kt:25`의 보간기). 결과 단계에서 다시 붙거나 다시 보이는 경우(`restart == false`)는 이미 끝 자세이므로 다시 돌리지 않는다: `restart == false && phase != RUNNING && posePhase == 1f`면 시작하지 않는다.
- **오류**: 없음.

#### `onAttachedToWindow()` / `onDetachedFromWindow()` / `onVisibilityChanged(changedView, visibility)` / `onWindowVisibilityChanged(visibility)`
- **추적**: HLD 3.4 "멈춤", R1 / SRS 3.2
- **책임**: 붙음·보임이 바뀔 때 애니메이터 조건을 다시 본다.
- **출력·사후조건**: 각각 `super` 뒤 `updatePoseAnimator(restart = false)`. 단 v1.3에서 붙음·떼어짐은 아래 두 항목으로 바뀐다. `onVisibilityChanged`는 조상의 보임 변화에도 불린다(`ViewGroup.dispatchVisibilityChanged`). 검색 펼침으로 떼어질 때 멈추고 다시 붙을 때 돈다(R1).
- **기존 코드와의 관계**: HLD 3.4의 `onVisibilityAggregated`는 API 24부터라 minSdk 23(`app/build.gradle:34`)에서 쓸 수 없어 위 두 콜백으로 대신한다(4장 FB-3).

#### `private val powerSaveModeReceiver: RuntimeBroadcastReceiver` (새 필드, #38)
- **추적**: HLD 3.4.1 수신기, D9 / REQ-30-10
- **값**: `RuntimeBroadcastReceiver(IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { updateReducedMotion() } }, context)`. 기본 `RECEIVER_NOT_EXPORTED`(`util/RuntimeBroadcastReceiver.kt:18`). 시스템 방송을 같은 도우미로 받는 선례는 `ftpserver/FtpServerUrl.kt:32-39`이고, API 36에서 실제로 오는지는 E-2가 확인한다.
- **위치**: `init`(`FileJobIndicatorView.kt:110`)보다 위. `onReceive`는 메인 스레드에서 돈다(핸들러를 주지 않음, `compat/ContextCompat.kt:81-85`).
- **수명**: 뷰의 `context`로 등록·해제한다. 액티비티 파괴 때는 창 제거(`ActivityThread.java:6019`)로 떼어짐이 먼저 와서 새지 않는다. 해제 전에 큐에 들어간 방송은 버려진다(`LoadedApk.java:1655`·`:1793`).

#### `private fun updateReducedMotion()` (새, #38)
- **추적**: HLD 3.4.1 재판정 (나)·(다)와 전환 표 / REQ-30-10, 12
- **책임**: K11을 다시 읽어 값이 바뀌었으면 표대로 그림만 바꾼다.
- **입력·전제**: 메인 스레드. 수신기와 `onAttachedToWindow`에서만 부른다. `phase` 설정자(재판정 (가))는 지금처럼 필드를 직접 정하고 이 함수를 부르지 않는다.
- **출력·사후조건**: `isReducedMotion`이 K11 값과 같다. `phase`·`contentDescription`·`drawnPhase`는 바꾸지 않는다(낭독 없음, REQ-30-12). K5·K6·K7은 호출되지 않는다(시간 규칙 불변).
- **오류**: 없음.
- **알고리즘** (`isReducedMotion`은 필드, `new`는 새로 읽은 값):
```kotlin
val new = context.isReducedMotion()
if (new == isReducedMotion) return
isReducedMotion = new
if (phase == HIDDEN) {
    if (new && visibility != GONE) { animate().cancel(); visibility = GONE; alpha = 1f }
    return
}
if (new) { poseAnimator.cancel(); posePhase = getStaticPosePhase(phase); invalidate() }
else updatePoseAnimator(false)
```
- **HLD 3.4.1 표 대조**:

| 지금 단계 | 거짓 → 참 | 참 → 거짓 |
|---|---|---|
| `RUNNING` | `cancel`, 위상 `0.25`, 다시 그림 | `updatePoseAnimator(false)`: 애니메이터가 시작 전이라 `:134-137`을 지나 `:143-162`에서 시작한다. 시작 때 `setCurrentPlayTime(0)`이 갱신 콜백을 불러 위상이 0이 된다(`ValueAnimator.java:1145-1156`). 붙음·보임 조건이 안 맞으면 위상 `0.25`로 있다가 맞을 때 시작한다 |
| `SUCCEEDED`·`FAILED` | `cancel`, 위상 1, 다시 그림 | `updatePoseAnimator(false)`: `shouldRun`이면 위상 1 분기(`:138-141`)로 재생하지 않고, 아니면 `stopPoseAnimator`로 위상 1. 그림 변화 없음 |
| `HIDDEN`, 서서히 사라지는 중 | `animate().cancel()`로 사라짐과 `withEndAction`을 함께 지우고 직접 `GONE`, `alpha = 1` | 해당 없음(사라지는 중이면 필드가 이미 거짓, `:68`) |
| `HIDDEN`, `GONE` | 필드만 | 필드만 |

  `visibility = GONE` 대입은 동기로 `onVisibilityChanged` → `updatePoseAnimator(false)`를 부르는데 `phase == HIDDEN`이라 취소만 한다.
- **결과 동작 중 절전 모드를 끈 뒤 사라질 때**: 사라짐은 `phase` 설정자의 재판정 (가)(`:66`)를 따르므로 거짓이면 150ms 서서히 사라진다. 그림은 끝까지 정지이고, 그 시점은 모션 줄이기가 아니므로 REQ-30-10 "서서히 사라지는 효과 없이"에 걸리지 않는다. SRS 수동 항목 "정지 그림 그대로 사라짐"과 맞다.
- **기존 코드와의 관계**: `getStaticPosePhase`(`:121-122`)를 재사용한다. `updatePoseAnimator`의 정지 분기는 달리기 위상을 고치지 않으므로(`:127-133`) 여기서 정한다. `context.isReducedMotion()`은 절전 모드를 프로세스 캐시로 읽고(`PowerManager.java:1936-1938`) 방송 한 번에 뷰당 한 번만 부른다. 캐시 무효화가 방송보다 먼저인지는 SDK 소스에 없어 **미확인**이고 E-2가 확인한다.

#### `private fun stopPoseAnimator()` (새, `updatePoseAnimator`의 `:128-131`을 옮김, #40)
- **추적**: HLD 3.4 "멈춤"(#40) / SRS 3.2, REQ-30-15
- **출력·사후조건**: 애니메이터가 멈춰 있다. 결과 단계면 `posePhase = 1f`. `RUNNING`·`HIDDEN`은 위상 그대로.
- **오류**: 없음. `cancel()`은 시작 전이면 아무 일도 하지 않는다.

#### `onAttachedToWindow()` (수정, #38)
- **추적**: HLD 3.4.1 분리 중 변화·(다) / REQ-30-10
- **순서**: `super` → `powerSaveModeReceiver.register()` → `updateReducedMotion()` → `updatePoseAnimator(false)`. 등록을 먼저 해서 등록과 읽기 사이의 전환을 놓치지 않는다. 그 사이에 방송이 와도 메인 스레드에서 이 콜백 뒤에 돌고, 값이 같으면 아무것도 하지 않는다.
- **첫 붙음**: `HIDDEN`·`GONE`이라 필드만 바뀐다. 회전 뒤 새 뷰는 필드가 기본 `false`인 채 붙고 `phase` 설정자가 다시 판정한다(M-05와 같은 경로).

#### `onDetachedFromWindow()` (수정, #38·#40)
- **추적**: HLD 3.4 "멈춤"(#40), 3.4.1 수신기 / SRS 3.2, REQ-30-10, 15
- **순서**: `super` → `powerSaveModeReceiver.unregister()` → `stopPoseAnimator()`. `updatePoseAnimator(false)`는 부르지 않는다(0.2 #40 — 이 콜백 안에서는 `shouldRun`이 참으로 계산될 수 있다).
- **결과 단계에서 분리·재부착될 때**: 취소만 하면 위상이 1 아래로 남아 재부착 때 `:139`를 지나 만세가 처음부터 다시 재생된다(REQ-30-15 위반). 그래서 `cancel`만이 아니라 `stopPoseAnimator`로 위상을 1로 둔다.

| 떼어지는 순간 | 수정 전 | 수정 후 | 다시 붙은 뒤 |
|---|---|---|---|
| 결과, 재생 중(위상 < 1) | 떨어진 채 끝까지 돌아 위상 1. 남은 시간 안에 다시 붙으면 나머지를 이어 재생 | 바로 위상 1 | 끝 자세, 다시 재생 안 함 — **남은 시간 안에 다시 붙은 경우에만 나머지 동작(최대 300·400ms)을 건너뛴다.** 창이 가려질 때의 기존 경로(`:127-131`)와 같다 |
| 결과, 재생 끝(위상 1) | 그대로 | 그대로 | 같음 |
| 결과, 모션 줄이기 | 애니메이터 없음, 위상 1 | 같음 | 같음. 떨어진 동안 절전 모드가 꺼져도 다시 재생 안 함(표) |
| `RUNNING` | 떨어져서도 계속 돎(결함) | 멈춤, 위상 그대로 | 위상 0부터 달림. 수정 전과 차이는 위상 연속성뿐이고 떨어진 동안 그림이 안 보여 알아볼 수 없다 |
| `HIDDEN`, 사라지는 중 | 사라짐 계속 | 같음(자세 애니메이터는 이미 멈춤, `:62`) | 같음 |

  시간 규칙은 K5가 정하므로 어느 경우도 바뀌지 않는다. 떨어진 동안 `phase`가 바뀌면 지금처럼 설정자가 처리한다(`:54-100`).

#### `onDraw(canvas: Canvas)`
- **추적**: HLD 3.4 / REQ-30-02, 05, 06, 13, SRS 3.5
- **출력·사후조건**: `phase == HIDDEN`이면 그리지 않음. 자세 = `RUNNING` → `StickFigurePose.running(posePhase)`, `SUCCEEDED` → `cheering(posePhase)`, `FAILED` → `fallen(posePhase)`. 그림 상자 = 한 변 `FIGURE_SIZE = 24dp`, 뷰 가운데(`left = (width - size) / 2`, `top = (height - size) / 2`). `layoutDirection == LAYOUT_DIRECTION_RTL`이면 `canvas.withScale(-1f, 1f, width / 2f, height / 2f)` 안에서 그림(SRS 3.5 `RTL-2`). 단위 좌표 `(u, v)` → `(left + u * size, top + v * size)`. 머리 `drawCircle(head, headRadius * size)`, 선 다섯 `drawLine`(목→엉덩이, 목→손 둘, 엉덩이→발 둘).
- **오류**: 없음.
- **기존 코드와의 관계**: `androidx.core.graphics.withScale`(core-ktx, 이미 의존).

크기는 레이아웃(K9a)에서 너비 32dp × `match_parent`(툴바 높이)로 정하고 `onMeasure`를 바꾸지 않는다.

### 2.13 `ui.StickFigurePose` (K9 자세) — 새 파일 `StickFigurePose.kt`

```kotlin
data class Joint(val x: Float, val y: Float)
data class StickFigurePose(
    val head: Joint, val headRadius: Float, val neck: Joint, val hip: Joint,
    val hand1: Joint, val hand2: Joint, val foot1: Joint, val foot2: Joint
) {
    companion object {
        fun running(t: Float): StickFigurePose
        fun cheering(t: Float): StickFigurePose
        fun fallen(t: Float): StickFigurePose
    }
}
```
안드로이드 의존 없음(`android.graphics.PointF`를 쓰지 않음 — JVM 시험에서 쓸 수 없다). 좌표는 단위 상자 0~1, `x`는 오른쪽(달리는 방향, LTR 기준), `y`는 아래쪽. 아래 수치는 출발값이며 구현 중 보기 좋게 조정할 수 있다 — 시험(3장 U-P)은 수치가 아니라 성질만 본다.

#### `running(t: Float): StickFigurePose`
- **추적**: HLD 3.4 / REQ-30-02, 10
- **책임**: 달리기 한 주기 중 위상 `t`의 자세.
- **입력·전제**: `t`는 아무 실수 — `t - floor(t)`로 0~1에 접는다(주기).
- **출력·사후조건**: 몸을 앞으로 기울인 자세. 머리 `(0.58, 0.18)` 반지름 `0.11`, 목 `(0.53, 0.33)`, 엉덩이 `(0.45, 0.62)`. `s = sin(2πt)`. 팔: 목에서 길이 `0.26`, 아래 방향 기준 각 `±0.9·s` 라디안(`hand = neck + 0.26·(sin θ, cos θ)`), 손1은 `+`, 손2는 `−`. 다리: 엉덩이에서 길이 `0.34`, 각 `∓0.7·s`(발1은 `−`, 발2는 `+` — 팔과 반대). 모든 점이 상자 안.
- **오류**: 없음(NaN 입력은 정의하지 않음 — 부르는 곳이 애니메이터 값만 넘긴다).

#### `cheering(t: Float): StickFigurePose`
- **추적**: HLD 3.4 / REQ-30-05, 10
- **책임**: 팔을 내린 자세(`t = 0`)에서 두 팔을 머리 위로 올린 자세(`t = 1`)까지.
- **입력·전제**: `t`를 0~1로 자른다(`coerceIn`).
- **출력·사후조건**: 곧게 선 몸: 머리 `(0.5, 0.22)` 반지름 `0.11`, 목 `(0.5, 0.36)`, 엉덩이 `(0.5, 0.64)`, 발 `(0.38, 0.96)`·`(0.62, 0.96)`. 팔 길이 `0.26`, 위쪽 기준 벌림 각 `α = lerp(2.8, 0.5, t)` 라디안(`hand = neck + 0.26·(±sin α, −cos α)`) — `t = 0`이면 손이 목 아래, `t = 1`이면 손이 머리 중심보다 위. 좌우 대칭(`x = 0.5` 기준).

#### `fallen(t: Float): StickFigurePose`
- **추적**: HLD 3.4 / REQ-30-06, 10
- **책임**: 선 자세(`t = 0`)에서 누운 자세(`t = 1`)까지.
- **입력·전제**: `t`를 0~1로 자른다.
- **출력·사후조건**: 관절마다 선 자세(= `cheering(0)`)와 누운 자세 사이 직선 보간 `lerp(a, b, t)`. 누운 자세: 머리 `(0.16, 0.80)` 반지름 `0.11`, 목 `(0.30, 0.82)`, 엉덩이 `(0.60, 0.85)`, 손 `(0.36, 0.64)`·`(0.44, 0.96)`, 발 `(0.90, 0.72)`·`(0.93, 0.92)`. 머리가 시작 쪽(LTR 왼쪽)으로 넘어간다. 두 끝 자세가 상자 안이므로 보간도 상자 안(볼록).
- **오류**: 없음.

### 2.14 레이아웃 `file_list_fragment_app_bar_include.xml` (K9a) — 수정

`toolbar`(`:27-36`)를 자기 닫는 태그에서 여는·닫는 태그로 바꾸고 자식 하나:
```xml
<me.zhanghai.android.files.ui.FileJobIndicatorView
    android:id="@+id/fileJobIndicator"
    android:layout_width="32dp"
    android:layout_height="match_parent"
    android:layout_gravity="end|center_vertical"
    android:visibility="gone" />
```
- **추적**: HLD K9a, D6, R1 / REQ-30-02, 14
- `GONE`이면 `Toolbar`가 측정·배치하지 않는다(`shouldLayout` — `Toolbar.java:1768`, `:2145`) → 자리 없음(REQ-30-14). 끝 쪽 정렬이라 메뉴 다음 시작 쪽에 놓인다(0.2 R1). `overlayToolbar`(`:38-47`)에는 두지 않는다(SRS A1·Q3).

### 2.15 `util.AnimatorScale.kt` (K11) — 새 파일(#30), v1.3에서 수정(#38)

#### `fun Context.isReducedMotion(): Boolean`
- **추적**: HLD K11, 3.4.1, R9 / REQ-30-10, SRS 1.4 ①②③, 3.1
- **책임**: 지금 애니메이션을 정지 그림으로 바꿔야 하는가.
- **입력·전제**: 메인 스레드(K9). 
- **출력·사후조건**: `isReducedMotion(powerManager.isPowerSaveMode, areAnimatorsDisabled())`. `powerManager`는 `app/SystemServices.kt:54`. 모든 지원 API(23+)에서 같은 식이다.
- **오류**: 없음(설정이 없으면 기본 1 → 거짓).
- **알고리즘**: 위. 앱의 `settings.Settings`와 이름이 겹치므로 `android.provider.Settings`는 정규 이름으로 쓴다.
- **기존 코드와의 관계**: 새 파일. API 23~25의 설정값 읽기(`Settings.Global.getFloat`)는 설정 제공자를 거친다. 프로세스 안 캐시 여부와 디스크 입출력 여부는 **미확인**(SDK 소스로 확인하지 않음). 부르는 횟수가 단계가 바뀔 때만이라(묶음당 몇 번) 메인 스레드 막힘(`Stability_ANR`, SRS 3.2) 위험은 작다고 보며, 구현 단계에서 StrictMode 경고가 나면 보고한다.

#### `private fun Context.areAnimatorsDisabled(): Boolean` (새, 기존 `isReducedMotion()` 본문 `AnimatorScale.kt:17-25`를 옮김)
- **추적**: SRS 1.4 ①②, 3.1
- **출력·사후조건**: API 26 이상 `!ValueAnimator.areAnimatorsEnabled()`. 미만 `isReducedMotion(android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f))`. 동작 변경 없음.

#### `internal fun isReducedMotion(isPowerSaveMode: Boolean, areAnimatorsDisabled: Boolean): Boolean` (새, HLD K11의 "이름 LLD")
- **추적**: HLD K11 / SRS C-5, REQ-30-10 단위 테스트
- **출력·사후조건**: `isPowerSaveMode || areAnimatorsDisabled`. 안드로이드 API를 부르지 않아 JVM에서 시험한다(U-A02).

#### `internal fun isReducedMotion(animatorDurationScale: Float): Boolean`
- **출력·사후조건**: `animatorDurationScale == 0f`.

파일 머리 KDoc(`:12-15`)은 "절전 모드(모든 API) 또는 Animator가 돌지 않는 상태"로 고친다. API 26+ 설명의 "e.g. battery saver"는 기기 정책에 따르므로 뺀다.

### 2.16 설정·문자열 (K10·K12)

| 곳 | 내용 |
|---|---|
| `Settings.kt` `:124-127` 뒤 | `val FILE_JOB_INDICATOR: SettingLiveData<Boolean> = BooleanSettingLiveData(R.string.pref_key_file_job_indicator, R.bool.pref_default_value_file_job_indicator)` |
| `donottranslate_prefs.xml` `:53` 뒤 | `<string name="pref_key_file_job_indicator">key_file_job_indicator</string>`, `<bool name="pref_default_value_file_job_indicator">true</bool>` |
| `settings.xml` `:47` 뒤 | `<SwitchPreferenceCompat android:key="@string/pref_key_file_job_indicator" android:title="@string/settings_file_job_indicator_title" android:summary="@string/settings_file_job_indicator_summary" android:defaultValue="@bool/pref_default_value_file_job_indicator" />` |
| `strings.xml` `:784` 뒤 | `settings_file_job_indicator_title` "File operation indicator", `settings_file_job_indicator_summary` "Show a running figure in the toolbar during file operations" |
| `strings.xml` 파일 작업 문자열 쪽 | `file_job_indicator_running_description` "File operation in progress", `file_job_indicator_succeeded_description` "File operation finished", `file_job_indicator_failed_description` "File operation failed or canceled" |
| `values-ko/strings.xml` 같은 자리 | "파일 작업 표시", "파일 작업 중 툴바에 달리는 사람을 표시", "파일 작업 진행 중", "파일 작업 완료", "파일 작업 실패 또는 취소됨" |

- **추적**: HLD K10·K12 / REQ-30-11, 12, SRS C-4. 낭독 문구에 "그림"·"버튼" 없음(REQ-30-12). 다른 언어는 채우지 않는다(SRS C-4).

## 3. 테스트 설계

### 3.1 단위 시험 (JVM, `junit:junit:4.13.2`)

| 테스트 | 대상 함수 | 경우 | 기대 결과 |
|---|---|---|---|
| U-B01 | `FileJobBatch` | `start(1)`, `finish(1, true, true)` | `state == FileJobState(0, FinishedBatch(1, true))`, 두 호출 모두 `true` |
| U-B02 | `FileJobBatch` | `start(1)`, `start(2)`, `finish(1, true)`, `finish(2, false)` (`isResultKept = true`) | 첫 끝 뒤 `runningCount 1, finished null`; 끝에 `FinishedBatch(1, false)` |
| U-B03 | `FileJobBatch` | 성공 + 취소(`false`) 순서 바꿔 두 번 | 둘 다 `isSuccessful == false` |
| U-B04 | `FileJobBatch` | 같은 id 끝 두 번(`false` 뒤 `true`) | 두 번째 `false` 반환, 상태 불변(먼저 온 보고가 이김, 결과 실패) |
| U-B05 | `FileJobBatch` | 시작 안 한 id 끝 | `false`, 상태 불변 |
| U-B06 | `FileJobBatch` | 대기 중 취소: `start(1)`, `finish(1, false)` | `FinishedBatch(1, false)` |
| U-B07 | `FileJobBatch` | 묶음1 실패로 끝 → `start(2)` | `start` 뒤 `finished == null`(REQ-30-08); 묶음2 성공으로 끝 → `FinishedBatch(2, true)`(실패 표시 초기화) |
| U-B08 | `FileJobBatch` | 소비 없이 묶음 둘이 끝남 | 마지막 `FinishedBatch(2, …)`만 남음(A8) |
| U-B09 | `FileJobBatch` | `consume(1)` 맞는 seq / `consume(7)` 다른 seq / 두 번째 `consume(1)` | `true`·`finished null` / `false`·불변 / `false` |
| U-B10 | `FileJobBatch` | 진행 중 id로 다시 `start`(R4) | `false`, `runningCount` 그대로 |
| U-B11 | `FileJobBatch` | `isResultKept = false`로 마지막 끝 | `FileJobState(0, null)`; 다음 묶음 seq는 2(번호는 진행) |
| U-S01 | `runJobAndReport` | `run`이 `true` | `onFinished(true)` 한 번 |
| U-S02 | `runJobAndReport` | `run`이 `false` | `onFinished(false)` 한 번 |
| U-S03 | `runJobAndReport` | `run`이 `AssertionError`(Exception 아님)를 던짐 | `onFinished(false)` 한 번, 같은 `AssertionError`가 밖으로(`assertThrows`·동일 객체) — #34 |
| U-S04 | `runJobAndReport` | 순서 | `onFinished`가 `run`이 끝난 뒤 불림(기록 목록 `["run", "finished"]`) |
| U-E01 | `isFileJobErrorResolved` | `POSITIVE`, `isPositiveRetry = true` | `true` |
| U-E02 | `isFileJobErrorResolved` | `POSITIVE`, `false`(양성 = 건너뛰기 창) | `false` |
| U-E03 | `isFileJobErrorResolved` | `NEGATIVE`·`NEUTRAL`·`CANCELED` × `true`·`false` | 모두 `false` |
| U-L01 | `FileJobIndicatorTimeline` | 0.2초 묶음: t=0 진행1 → t=200 `finished(1,true)` → t=1000 → t=2500 | `RUNNING(next null)` → `RUNNING(next 1000, 소비 없음)` → `SUCCEEDED(next 2500, 소비 1)` → `HIDDEN(next null)` (REQ-30-05·07) |
| U-L02 | 같음 | 긴 묶음: t=0 진행 → t=5000 성공 끝 | t=5000에 바로 `SUCCEEDED`, `next 6500`, 소비 1 |
| U-L03 | 같음 | 실패 묶음 | `FAILED`(REQ-30-06) |
| U-L04 | 같음 | 경계: 끝 t=999, t=1000에 다시 | t=999 `RUNNING next 1000`; t=1000 결과(`>=`) |
| U-L05 | 같음 | 결과 중 새 시작: t=1000 `SUCCEEDED` → t=1500 진행1 → t=1600 성공 끝 → t=2500 | t=1500 `RUNNING`; t=1600 `RUNNING next 2500`; t=2500 `SUCCEEDED`(REQ-30-08) |
| U-L06 | 같음 | 최소 시간 채우는 중 새 시작: t=200 끝 → t=500 진행 → t=3000 끝 | t=500 `RUNNING`(시작 시각 0 유지); t=3000 바로 결과 |
| U-L07 | 같음 | `HIDDEN`에서 `finished(3)` 받음(화면 밖에서 끝남) | `RUNNING next now+1000`; 1초 뒤 결과·소비 3(REQ-30-15) |
| U-L08 | 같음 | 재진입: 결과 진입(소비 1) 직후 같은 시각에 `finished null`로 / `finished(1)` 그대로로 다시 | 둘 다 같은 단계, `next == resultUntil`, 소비 없음(R10) |
| U-L09 | 같음 | 달리기 대기 중 다른 화면이 소비(`finished null`) | `HIDDEN`(A3) |
| U-L10 | 같음 | 설정 꺼짐: 진행 중 / `finished(2)` | `HIDDEN` / `HIDDEN`·소비 2; 다시 켜고 진행 중이면 `RUNNING`, 그때부터 1초(A7, REQ-30-11) |
| U-L11 | 같음 | 멈춤-달리기: t=0 진행 → t=300 `stop()` → t=10000 `finished` | `RUNNING next 11000`; t=11000 결과(멈춘 시간 제외) |
| U-L12 | 같음 | 멈춤-결과: t=1000 결과(until 2500) → t=1200 `stop()` → t=2000 / 다른 경우 t=3000 | `SUCCEEDED next 2500` / `HIDDEN` |
| U-L13 | 같음 | 멈춤-결과 중 새 묶음 끝(`finished(2)`) → t=2000 | `RUNNING next 3000` |
| U-L14 | 같음 | 쉼: 진행 0, `finished null` | `HIDDEN`, `next null`, 소비 없음 |
| U-L15 | 같음 | 같은 입력 `RUNNING` 두 번(t=0, t=400) | `runningSince` 그대로(끝 뒤 `next 1000`) |
| U-L16 | 같음 | `HIDDEN`에서 `finished(1)`이 자기가 이미 보인 seq(`shownSeq == 1`, 소비 전 재전달) | `HIDDEN` 유지, `next null`, 소비 없음 |
| U-L17 | 같음 | 멈춤 뒤 진행 중: t=0 진행 → t=300 `stop()` → t=5000 진행 중 그대로 → t=5000 `finished` 받음 | t=5000 `RUNNING`(`runningSince = 5000`으로 다시 셈); 끝 받은 호출은 `next 6000` |
| U-I01 | `getFileJobIndicatorPaneIndex` | `isDualPaneEnabled = false` × 나머지 | `0` |
| U-I02 | 같음 | 두 창 모드 × 활성 0·1 | `1` |
| U-I03 | 같음 | 한 창 모드 × 활성 0·1 | `activePane` |
| U-P01 | `StickFigurePose` 셋 | `t = 0, 0.05, …, 1` | 모든 관절과 머리 원(`x ± r`, `y ± r`)이 `[0, 1]` 안 |
| U-P02 | `running` | `running(0)` vs `running(1)`, `running(0.3)` vs `running(1.3)` | 같음(오차 1e-4) — 주기 |
| U-P03 | `running` | `running(0.25)` vs `running(0.75)` | 손1↔손2, 발1↔발2가 서로 바뀐 값(오차 1e-4) |
| U-P04 | `running` | `running(0.25)`(정지 자세) | 두 발 `x` 차이 ≥ 0.2(보폭이 보임) |
| U-P05 | `cheering` | `t = 0` / `t = 1` | 두 손 `y` > 목 `y` / 두 손 `y` < 머리 중심 `y` |
| U-P06 | `cheering` | `t` 0→1 증가 | 손 `y`가 줄어들기만 함, 매 `t`에서 `x = 0.5` 기준 좌우 대칭 |
| U-P07 | `fallen` | `t = 0` / `t = 1` | 머리 `y` < 엉덩이 `y` < 발 `y`(선 자세) / `|머리 y − 엉덩이 y| < 0.15`이고 머리 `x` < 엉덩이 `x`(누움, 시작 쪽) |
| U-P08 | `cheering`·`fallen` | `t = -0.5`, `1.5` | `t = 0`, `1`과 같음(자름) |
| U-A01 | `isReducedMotion(Float)` | `0f` / `1f`·`0.5f`·`10f` | `true` / `false` |
| U-A02 | `isReducedMotion(Boolean, Boolean)` | 절전 모드 켬·끔 × Animator 꺼짐·켬 네 조합 | (끔, 켬)만 `false`, 나머지 셋 `true` |

뷰의 전환(2.12)은 `View`·`ValueAnimator`·`PowerManager`가 필요한데 JVM 시험 의존성은 JUnit뿐이다(`app/build.gradle:222`). 새 의존성을 들이지 않고 3.3 에뮬레이터 확인으로 검증한다.

### 3.2 수동 확인 (기기 — 구현 단계 검수와 오너 확인)

| 테스트 | REQ | 절차 | 기대 |
|---|---|---|---|
| M-01 | 01·02 | 큰 파일 복사 | 바로 툴바 끝 쪽에서 달림, 메뉴 아이콘·더 보기 눌림 |
| M-02 | 05·07 | 새 폴더 만들기 | 약 1초 달리고 만세 1.5초, 사라짐(화면 녹화 ±0.3초) |
| M-03 | 04·06 | 복사 중 알림 취소 / 쓰기 금지 폴더로 복사해 오류 창 "취소" / "건너뛰기" | 셋 다 넘어짐 |
| M-03a | 01·04, K2 ③ | 큰 복사 여럿을 걸어 일부가 서비스 생성 전 대기(`pendingJobs`)일 때 그 알림 취소 / 진행 중 앱 강제 종료 없이 서비스 끝(`adb shell am stopservice`로 `FileJobService` 중지) | 둘 다 넘어짐, 진행 중으로 남지 않음(선 사람이 사라짐) |
| M-04 | 04 | 같은 이름 충돌 창 "건너뛰기" | 만세 |
| M-05 | 03 | 복사 둘 동시, 두 창 모드, 한 창 모드 쓸기 전환, 회전 | 하나, 창 2에만, 보이는 창으로 옮겨 감(달리기 이어짐), 회전 뒤 계속 달림 |
| M-06 | 08 | 만세 중 새 복사 | 바로 달리기 |
| M-07 | 09 | 선 사람 누름, TalkBack 초점 | 변화 없음, "두 번 눌러 활성화" 없음 |
| M-08 | 10 | `adb shell settings put global animator_duration_scale 0`(뒤에 되돌림), 절전 모드(모든 API, 기기의 애니메이션 정책이 꺼진 상태 필수 — 3.3 E-0) | 정지 달리기 → 정지 만세(실패·취소면 정지 넘어짐) → 바로 사라짐, 시간은 REQ-30-05~07과 같음 |
| M-09 | 11 | 설정 끔·켬(진행 중), 설정 화면에서 끈 채 작업이 끝난 뒤 켜고 돌아옴 | 바로 사라짐·바로 달림, 돌아와도 결과 없음(A7, FB-2) |
| M-10 | 12 | TalkBack: 복사 시작·끝 | "파일 작업 진행 중" → "파일 작업 완료" 한 번씩, 반복 없음 |
| M-11 | 12, HLD R2 | TalkBack 켜고 진행 중 한 창 모드 창 전환 | 같은 문구 최대 한 번 더(반복 낭독 아님). 문제면 FB 등록 |
| M-12 | 13 | 밝은·어두운 × Material 3 켬·끔 | 대비 3:1 이상(Accessibility Scanner) |
| M-13 | 14 | 작업 없을 때 툴바 스크린숏 비교(이전 빌드) | 같음 |
| M-14 | 15 | 큰 복사 → 홈 → 끝난 뒤 복귀 / 만세 중 회전 | 1초 달리고 만세 / 다시 만세 안 함 |
| M-15 | SRS 3.5 | RTL 강제 | 툴바 왼쪽(메뉴 오른쪽)에서 왼쪽을 향해 달림 |
| M-16 | R1, #40 | 진행 중 검색 펼침 → 접음 | 펼친 동안 안 보임(떨어진 동안 애니메이터 멈춤 — E-7), 접으면 그때 단계로 보임 |
| M-17 | 16, #34 | 복사·이동·삭제·압축·압축 풀기·이름 바꾸기 각 1회 | 알림·결과·창 이전과 같음 |
| M-18 | 10, 12 | TalkBack 켜고 복사 중 절전 모드 켬·끔 | 낭독이 다시 나오지 않음. 에뮬레이터 이미지에 TalkBack이 없으면 **미확인**으로 적고 오너 기기 확인으로 넘긴다 |
| M-19 | 10 | 오너 태블릿(`low_power=1`, `animation_disabled=false`)에서 M-08 절전 모드 경로와 E-2. 설치는 PM이 오너 동의를 받은 뒤에만 | 정지 그림, 켜고 끄면 바로 따름. API 33 미만에서 `RECEIVER_NOT_EXPORTED` 등록으로 이 방송이 오는지는 API 36인 E-2로 닫히지 않고 이 항목만 닫는다(androidx `ContextCompat.registerReceiver` 내부 경로 미확인) |

### 3.3 에뮬레이터 확인 (구현 단계, #38·#40)
개발팀이 PR 검수 요청 전에 실행하고 결과(명령·출력 끝부분·스크린숏 판독)를 PR 코멘트에 붙인다. 물리 기기에는 adb 명령을 보내지 않는다 — 모든 adb는 `-s emulator-<port>`.

**띄우기**: `adb devices`로 이미 떠 있는 에뮬레이터를 확인하고 끄지 않는다. 쓰지 않은 AVD(`Medium_Phone_API_36`, 쓰이고 있으면 `Medium_Phone_API_36_b`)를 빈 포트로 띄운다: `emulator -avd <AVD> -port <port> -no-window -no-audio -read-only -no-snapshot-save -no-boot-anim`. 끝나면 자기 포트만 `adb -s emulator-<port> emu kill`.

**절전 모드 켜고 끄기** (사전 확인 표 때문에 배터리 값을 먼저 정한다):
- 켬: `cmd battery unplug; cmd battery set level 50; settings put global low_power 1`
- 끔: `settings put global low_power 0`
- 정리: `settings delete global battery_saver_constants; settings put global low_power 0; settings put global animator_duration_scale 1; cmd battery reset`
- 켬·끔 뒤마다 `dumpsys power`의 `Battery saver state machine` 절 `Enabled=`를 확인한다. `Enabled=true`가 아닌 채 낸 결과는 무효다.

**사전 확인** (2026-11-04, LLD 단계에서 `Medium_Phone_API_36`, API 36 실행. 앱 없이 `dumpsys power`만 읽음):

| 설정 | `Enabled` | `Policy 'effective'`의 `disable_animation` |
|---|---|---|
| 배터리 100, `cmd battery unplug` + `low_power 1` | **false** (`mSettingBatterySaverEnabled=true`인데 꺼짐) | false |
| 배터리 50 + `low_power 1` (`battery_saver_constants` 값 `disable_animation=true`) | true | true |
| 배터리 50 + `low_power 1` (값 `animation_disabled=true`) | true | **false** — 이 키는 API 36에서 무시됨 |
| 배터리 100 + `cmd power set-mode 1` (값 없음) | true | false |

결론: ① API 36의 `dumpsys power` 키 이름은 `disable_animation`이다(오너 태블릿 Android 11 출력은 `animation_disabled`). 뜻이 같은 값으로 보고 대조한다. ② 재현 설정은 `disable_animation=true`다. ③ 배터리 100에서는 `low_power 1`만으로 켜지지 않는다. 절전 정책 값까지만 확인했고 앱 쪽 Animator 상태는 확인하지 않았다(E-6에서 본다).

**움직임 판독**: `adb -s emulator-<port> exec-out screencap -p` 5장을 이어서 찍어 툴바 끝 쪽 같은 영역을 비교한다. 달리면 자세가 달라지고, 정지면 다섯 장이 같다.

**작업 준비**: 작은 파일이 많은 폴더를 `/storage/emulated/0/Download`에 만들고(4000개×64 KB, 복사 90~200초. 큰 파일 하나는 빠르고 저장 공간이 모자랐다), 디버그 APK를 `-s`로 설치, `appops set me.zhanghai.android.files MANAGE_EXTERNAL_STORAGE allow`.

**실행 메모 (v1.4)**: ① API 36의 기본 절전 정책은 `enable_night_mode=true`라 절전을 켜고 끌 때 화면이 다시 만들어진다. 그러면 새 뷰의 재판정만 보이므로, 뷰 안 전환(E-2·E-3·E-3a·E-5·E-6)은 `battery_saver_constants`에 `enable_night_mode=false`를 넣고 본다. E-0·E-1은 기본값으로 본다. ② 검색 접기는 BACK 두 번이다(첫 번째는 키보드만 닫음). ③ `uiautomator dump`는 절전 방송을 되풀이하게 하는 것으로 보인다(dump를 멈추면 12초간 0회, 추정). 재판정은 값이 같으면 아무것도 하지 않아 동작 영향은 없었다. ④ 결과 시간(1.5초) 안의 조작은 기기 안 `logcat | while read` 감시 스크립트로 보낸다.

| 테스트 | 근거 | 절차 | 기대 |
|---|---|---|---|
| E-0 | REQ-30-10 ③ 전제 | 절전 켬 → `dumpsys power` 저장 | `Enabled=true`, effective `disable_animation=false`. 애니메이션 정책이 꺼진 채 절전 모드가 켜진 상태, 즉 오너 태블릿과 같은 조건임을 보이는 증거로 PR에 붙인다 |
| E-1 | #38 요구 1·2 | 절전 켠 채 복사 → 끝까지 | 정지 달리기 → 정지 만세 → 사라짐, 위 판독으로 정지 |
| E-1a | REQ-30-10 | E-1을 복사 중 알림 취소로 | 정지 달리기 → 정지 넘어짐 |
| E-2 | #38 요구 3, 3.4.1 `RUNNING` | 절전 끈 채 복사 → 달림 확인 → 켬 → 판독 → 끔 → 판독 | 켬 뒤 정지(화면 조작 없이), 끔 뒤 다시 달림. API 36에서 `RECEIVER_NOT_EXPORTED` 수신과 캐시 무효화 순서도 이것으로 확인된다 |
| E-3 | 3.4.1 결과 줄 | 절전 켠 채 짧은 작업 → 만세 1.5초 안에 끔 | 만세 정지 그대로, 다시 움직이지 않음. 끈 뒤라 사라질 때 150ms 서서히 사라지는 것은 정상이다(2.12) |
| E-3a | 3.4.1 결과 줄 | 절전 끈 채 결과 중 켬 (`animator_duration_scale 10`으로 재생을 늘림) | 바로 끝 자세로 정지 |
| E-4 | 3.4.1 분리 중 변화, REQ-30-10 끝 문장 | 복사 중 절전 변경 후 회전 / 복사 중 검색 펼침 → 절전 변경 → 접음 / 홈 → 변경 → 복귀 | 셋 다 바뀐 상태대로 그림(켬이면 정지, 끔이면 달림) |
| E-5 | 3.4.1 사라지는 중 | `animator_duration_scale 10`(사라짐 약 1.5초), 절전 끈 채 짧은 작업 → 사라지는 중 켬 | 바로 사라지고, 다음 작업에서 정상으로 나타남(`alpha` 복구). 끝나면 배율 되돌림 |
| E-6 | HLD R11 | `settings put global battery_saver_constants disable_animation=true` → 절전 켬 → effective `disable_animation=true` 확인 → 복사 중 끔 → 판독, 5회. 커밋하지 않는 임시 로그로 `updateReducedMotion` 때 `isPowerSaveMode`·`areAnimatorsEnabled()` 값을 찍는다 | 켠 상태에서 `areAnimatorsEnabled()`가 거짓이어야 재현이 성립한다. 다시 달리면 R11 닫음. 정지로 남으면 횟수와 함께 **PM에게 보고하고 고치지 않는다**(HLD R11). 켠 상태에서도 참이면(값이 Animator를 끄지 않음) 그 사실을 적고 R11은 미확인으로 둔다 |
| E-7 | #40 회귀, PROCESS_ROLES 11-1 | 커밋하지 않는 임시 로그: 갱신 콜백에 `Log.d("FJI", "update ${System.identityHashCode(this)}")`, `onDetachedFromWindow`에 `shouldRun` 입력값. **수정 전 코드**로 복사 중 검색 펼침 → logcat, **수정 후**로 반복. 회전도 같은 방법으로 | 수정 전: 펼친 뒤에도 같은 해시로 `update`가 계속됨(실패). 수정 후: 펼친 뒤 멈춤(통과). 회전은 수정 전에 재현되지 않으면 그대로 적는다(이슈 #40 "미확인"). 원인 가설: 콜백 안 `isAttachedToWindow` 참으로 `shouldRun` 참. 끝나면 임시 로그 제거를 `git diff`로 확인 |
| E-8 | #40, REQ-30-15 | `animator_duration_scale 10`(만세 3초, 시간 규칙은 1.5초 그대로), E-7 임시 로그. 짧은 작업 → 만세 시작 직후 검색 펼침 → 바로 BACK으로 접음(1초 안) | 분리 시점에 `update` 로그가 멈추고, 다시 붙은 뒤 끝 자세, `update` 없음(처음부터 다시 재생 안 함). 결과 시간 안에 못 접으면 다시 시도하고, 안 되면 **미확인**으로 적는다 |

## 4. 설계 피드백
설계팀(`designer`)에 문의한 항목과 결과. SRS·HLD는 개발팀이 고치지 않는다.

| ID | 내용 | 근거 | 문의·결과 |
|---|---|---|---|
| FB-1 | R9: API 26 이상 판정(`areAnimatorsEnabled`)은 절전 모드도 포함해 SRS 1.4 "모션 줄이기"(배율 0) 정의보다 넓다. HLD R9 대응("다르면 SRS 용어 정의를 설계팀이 고친다")에 따라 SRS 1.4 정의를 넓혀야 한다. | 0.2 R9 | **수용·반영** — SRS v1.3 1.4 "모션 줄이기" ①(배율 0, 모든 API)·②(API 26+ `areAnimatorsEnabled()` 거짓, 절전 등), 3.1, REQ-30-10 보조 수동 확인. HLD v1.3 K11·3.4, R9 닫음. 설계팀 판단: 오너 결정 범위 안(시스템이 Animator를 끄면 어차피 움직이지 못함) — PM이 오너에게 알림 권장 (74dff1a) |
| FB-2 | SRS A7("꺼져 있는 동안 끝난 묶음의 결과는 켠 뒤 보이지 않는다")을 HLD K6("설정이 꺼진 채 결과를 받으면 바로 소비")만으로는 지킬 수 없다: 설정 화면은 다른 액티비티라 파일 목록 화면이 멈춘 동안 K6가 결과를 받지 못하고, 거기서 끄고 → 묶음 끝 → 켜고 돌아오면 결과가 보인다. LLD는 K1 `onJobFinished`가 묶음 끝 때 설정을 읽어 꺼져 있으면 결과를 만들지 않게 했다(2.1 `isResultKept`, 2.2). 그 결과 `filejob/`이 `settings/`를 참조한다(HLD 1.2 그림에 없는 화살표). | `filejob/` 안 `settings` 참조 없음(현재), `SettingLiveData.kt` `init()`이 수신기를 늘 등록 | **수용·반영** — HLD v1.3 1.2 그림(`SET → T`)·새 경계 문단(`filejob/`→`settings/` 참조 이유), K1·K1a(REQ-30-11 추적), K6 소비 규칙 범위 한정. SRS 변경 없음 (74dff1a) |
| FB-3 | K9 세부: ① R8 — HLD R8 둘째 문장("설명은 뷰를 보이게 하기 전에 설정")과 반대로 보인 **뒤** 설정한다(보이지 않는 뷰의 알림은 버려짐). 목적(옛 설명 안 읽음)은 숨길 때 지우기로 지킨다. ② HLD 3.4의 `onVisibilityAggregated`는 API 24부터라 `onVisibilityChanged`+`onWindowVisibilityChanged`로 대신한다. ③ `ViewCompat.setAccessibilityLiveRegion` 대신 `View.accessibilityLiveRegion`(API 19). | 0.2 R8, `app/build.gradle:34` | **수용·반영** — HLD v1.3 R8(보인 뒤 설명, 닫음), 3.4 멈춤 콜백, 3.5 `View.accessibilityLiveRegion` (74dff1a) |
| FB-4 | K5 표(HLD 3.3)에 없는 경우: 결과 단계 중 멈춘 사이 새 묶음이 시작·끝나 `finished`가 자기가 보인 것과 다른 seq로 오면 다시 달리기부터(REQ-30-08·15의 연장). 이를 위해 K5가 `shownSeq`를 가진다. | 2.7 4번 | **수용·반영** — HLD v1.3 3.3 표에 `shownSeq` 행·조건, 멈춤 복귀 규칙 (74dff1a) |
| FB-5 | HLD K8·4.1은 관찰 등록을 `FileListFragment.kt:348-358`(기존 창 표시 관찰 블록)에 둔다고 했으나, #29 구현 브랜치가 바로 그 블록 안 줄(`:353`, `:359` 근처)을 바꾸어 충돌한다. LLD는 별도 함수로 `:382` 뒤에서 등록한다(0.4). HLD 4.1 표의 "이 설계가 바꾸는 곳"을 맞춰야 한다. | `git diff origin/develop...origin/feature/29-sftp-list-reload -- …/FileListFragment.kt` | **수용·반영** — HLD v1.3 K8 위치·4.1 표 (74dff1a) |
| FB-6 | HLD R11의 재현 설정 `battery_saver_constants animation_disabled=true`는 API 36에서 무시된다. 키는 `disable_animation`이다. `dumpsys power` 출력 키도 버전마다 달라 SRS REQ-30-10의 "`animation_disabled=false`인 기기"(HLD R9 포함)는 API 36에서 글자 그대로는 찾을 수 없다. LLD 3.3은 실측 키로 쓴다. | 3.3 사전 확인 표 | **수용·반영** — HLD v1.6 R9·R11, SRS v1.5 REQ-30-10 (0ff9699) |

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-04 | v1 | 초안 | SRS v1.2·HLD v1.2 검수 통과, R1·R8~R10 해소, #34 흡수 | 개발팀 |
| 2026-11-04 | v1.1 | 근거를 SRS·HLD v1.3으로, 4장 FB-1~FB-5 결과 기록 | 설계팀 답(74dff1a) | 개발팀 |
| 2026-11-04 | v1.2 | 2.5 호출부 13곳, 2.12 `shouldRun`에 `windowVisibility`·`phase` 설정자 순서 규칙·`start()` 전 `cancel()`, 0.2 R9 단정 완화, U-L16·U-L17·M-03a 추가, 2.15 설정 읽기 입출력 미확인 | LLD 검수 1회차 경미 m1~m6 (PR #32 issuecomment-6618249202) | 검수팀 |
| 2026-11-04 | v1.3 | 근거 SRS v1.5·HLD v1.6. 0.2 R9 정정·R11·#40, 0.3, 1.1, 2.12 수신기·`updateReducedMotion`·`stopPoseAnimator`·붙음·떼어짐, 2.15 절전 모드와 순수 함수, U-A02, M-08·M-16·M-18·M-19, 3.3 에뮬레이터 확인(사전 확인 포함), FB-6 | 이슈 #38 요구사항 정리 v1, #40 흡수(PM 분류). 검수 경미 1~4 반영(PR #41 issuecomment-6619884831) | 오너·PM |
| 2026-11-04 | v1.4 | 3.3 작업 준비·실행 메모, 0.2 R11·#40 실행 결과 | 구현 단계 에뮬레이터 확인 결과 | 개발팀 |
