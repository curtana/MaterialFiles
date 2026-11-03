# HLD — 파일 작업 표시(달리는 선 사람) (이슈 #30)

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 |
| 근거 | SRS v1.1 (검수 통과, PR #32 2회차) |
| 기준 코드 | `origin/develop` 4437e43 — 줄 번호는 이 커밋 기준 |

## 1. 구조 개요

### 1.1 지금 구조
- **작업 실행(`filejob/`)**: 화면은 `FileJobService`의 companion 시작 함수(`FileJobService.kt:115-196`)를 부른다. companion `startJob`(`:104-113`, 메인 스레드)은 서비스가 있으면 바로 넘기고, 없으면 `pendingJobs`에 쌓은 뒤 서비스를 시작한다(서비스 `onCreate`가 비움, `:42-44`). 서비스의 `startJob`(`:54-67`)은 캐시 스레드 풀에 `job.runOn(this)` 뒤 `runningJobs.remove(job)`을 실행하는 일을 넘긴다. 취소는 알림 버튼 → `FileJobReceiver`(`FileJobReceiver.kt:16-18`) → companion `cancelJob`(`FileJobService.kt:198-202`) → 서비스 `cancelJob`(`:69-74`, `Future.cancel(true)`로 끼어들기). 서비스가 끝나면 남은 작업을 모두 취소한다(`:76-87`).
- **작업 끝**: `FileJob.runOn`(`FileJob.kt:19-33`)이 `run()`을 부르고 `InterruptedIOException`(취소)과 `Exception`(실패, 토스트)을 잡아 삼킨다. 결과를 밖으로 돌려주지 않는다. 바깥에 드러난 상태는 메인 스레드에서 읽는 개수 `runningJobCount`(`FileJobService.kt:100-102`)뿐이고 관찰할 수 없다.
- **오류 창**: 작업 본문은 오류 때 `showErrorDialog`(`FileJobs.kt:499-526`)로 창을 띄워 답을 기다리고, 답에 따라 다시 시도·건너뛰기·취소한다.
- **화면(`filelist/`)**: `FileListHostFragment`가 창(`FileListFragment`) 한두 개와 두 창 상태(`DualPaneViewModel` — 활성 창 `:10-31`, 창 모드 `:40-51`)를 가진다. 툴바는 창마다 앱바 안에 있다(`res/layout/file_list_fragment_app_bar_include.xml:27-36`, 두 배치 모두 이 include를 쓴다: `res/layout/file_list_fragment_include.xml:21`, `res/layout-sw600dp-land/file_list_fragment_include.xml:22`). 툴바는 지원 액션바가 아니라 창별 메뉴 호스트다(`FileListFragment.kt:199-201`). 창은 부모의 `DualPaneViewModel`을 공유한다(`FileListFragment.kt:144`).
- **설정(`settings/`)**: 불리언 설정은 `Settings`의 `BooleanSettingLiveData`(예: `FILE_LIST_ANIMATION`, `Settings.kt:124-127`) + `res/values/donottranslate_prefs.xml`의 키·기본값(`:52-53`) + `res/xml/settings.xml`의 `SwitchPreferenceCompat`(`:44-47`) 세 곳으로 만든다.

### 1.2 새 구조
```mermaid
flowchart LR
  subgraph filejob["filejob/ (작업 쪽)"]
    S["FileJobService (K2)\nstart / cancel / finally / onDestroy"]
    J["FileJob.runOn (K3)\n성공 여부 반환"]
    E["showErrorDialog (K4)\n건너뛴 오류 기록"]
    T["FileJobStateTracker (K1)\n메인 스레드 · LiveData"]
    B["FileJobBatch (K1a)\n순수 집계"]
  end
  subgraph filelist["filelist/ (화면 쪽)"]
    H["FileListHostFragment (K6)\n표시 제어 · 타이머"]
    L["FileJobIndicatorTimeline (K5)\n순수 시간 규칙"]
    VM["DualPaneViewModel (K7)\n표시 단계 LiveData"]
    P["FileListFragment ×2 (K8)\n표시할 창 판정"]
  end
  V["ui/FileJobIndicatorView (K9)\n선 사람 그리기"]
  SET["Settings.FILE_JOB_INDICATOR (K10)"]
  E --> J --> S --> T
  T --- B
  T -- "state LiveData" --> H
  SET --> H
  H --- L
  H -- "phase" --> VM --> P --> V
  H -- "결과 소비" --> T
```
- 작업 쪽은 **알리기만** 한다: 시작(K2, 메인 스레드)·끝(K2 `finally`, 메인 스레드로 넘김)·취소(K2). 작업 본문의 분기·반환값은 그대로다(SRS C-1).
- 화면 쪽은 관찰한 상태로 **표시 단계**(숨김·달리기·만세·넘어짐)를 시간 규칙(K5)에 따라 정해 화면 하나에 하나만 그린다.
- 새 경계: `FileJobStateTracker`(K1)는 `filejob/`에 둔다. 상태의 주인이 작업 쪽이고, 화면 쪽(`filelist/`)은 지금처럼 `filejob/`의 공개 API만 부른다(지금도 `FileListFragment`가 `FileJobService`를 부른다). 그림 뷰(K9)는 툴바 안 범용 위젯이라 기존 툴바 위젯들(`ui/CrossfadeSubtitleToolbar.kt`, `ui/OverlayToolbar.kt`)과 같은 `ui/`에 둔다.

## 2. 구성요소

| 구성요소 | 위치(패키지·파일) | 책임 | 추적 REQ |
|---|---|---|---|
| K1 상태 통로 | `filejob/FileJobStateTracker.kt` (새 파일, `object`) | 메인 스레드 전용. `onJobStarted(id)`, `onJobFinished(id, isSuccessful)`, `consumeResult(seq)`를 받아 K1a로 집계하고 `stateLiveData: LiveData<FileJobState>`로 내보낸다. 모든 변경은 `setValue`(메인)라 순서가 보존되고 합쳐지지 않는다. | REQ-30-01, 04, 15 |
| K1a 묶음 집계 | `filejob/FileJobBatch.kt` (새 파일, 안드로이드 의존 없음) | 진행 중 작업 id 집합, 이번 묶음의 실패 여부, 끝난 묶음 결과 `FinishedBatch(seq, isSuccessful)?`. 규칙: 모르는 id·이미 끝난 id의 끝 보고는 무시(중복 무시), 집합이 비면 결과를 만들고 seq를 1 올림(앞 결과는 덮어씀 — SRS A8 "마지막 묶음만"), 새 시작이 오면 남은 결과를 지움(REQ-30-08), `consume(seq)`는 seq가 같을 때만 결과를 지움. 상태 값 `FileJobState(runningCount, finished: FinishedBatch?)`. | REQ-30-01, 04, 08, 15 |
| K2 서비스 연결 | `filejob/FileJobService.kt` (기존) | ① companion `startJob`(`:104-113`) 처음에 `onJobStarted(job.id)` ② 실행기 작업(`:57-63`)을 `try { isSuccessful = job.runOn(this) } finally { 메인으로 넘겨 onJobFinished(job.id, isSuccessful) }`로 감싼다 — `isSuccessful` 기본값 `false`라 `Throwable`로 끝나도 실패로 보고되며, 예외는 지금처럼 그대로 위로 나간다(잡지 않음) ③ companion `cancelJob`(`:198-202`)에서 대기 목록에서 지운 작업, 서비스 `cancelJob`(`:69-74`)·`onDestroy`(`:76-87`)에서 지운 작업마다 `onJobFinished(id, false)`. 메인으로 넘기기는 기존 `mainExecutorCompat`(`compat/ContextCompat.kt:48`). `runningJobs`·잠금 처리는 바꾸지 않는다(4장 R5). | REQ-30-01, 04, 16 |
| K3 결과 반환 | `filejob/FileJob.kt` `runOn` (기존, `:19-33`) | 반환형을 `Boolean`(성공 여부)으로: `run()`이 끝나고 K4 기록이 없으면 `true`, `InterruptedIOException`·`Exception` 경로는 `false`. 잡는 범위·토스트·`finally`의 알림 지우기는 그대로. 기록용 필드 `hasUnresolvedError`(K4가 씀)를 둔다. | REQ-30-04, 16 |
| K4 건너뛴 오류 기록 (SRS Q2 임시 기본값) | `filejob/FileJobs.kt` `showErrorDialog` (기존, `:499-526`) | 답을 받은 뒤 `action != POSITIVE`(다시 시도가 아님)이면 `hasUnresolvedError = true`. 반환값·예외는 그대로다. 작업 본문의 호출부(`:1021-1055`, `:1392-1431`, `:2293-2310` 등)는 바꾸지 않는다. 오류 창 하나의 함수라 모든 작업에 같이 적용된다. Q2가 "성공"으로 결정되면 이 구성요소를 뺀다. | REQ-30-04 |
| K5 표시 시간 규칙 | `filelist/FileJobIndicatorTimeline.kt` (새 파일, 안드로이드 의존 없음) | 입력: 현재 시각(ms, 주입), 설정 켬 여부, `FileJobState`. 출력: 표시 단계 `FileJobIndicatorPhase`(`HIDDEN`·`RUNNING`·`SUCCEEDED`·`FAILED`), 다음에 다시 계산할 시각, 소비할 seq. 규칙은 3.3. 상수 `MIN_RUNNING_MILLIS = 1000`, `RESULT_MILLIS = 1500`(SRS A4). | REQ-30-05, 06, 07, 08, 10, 11, 15 |
| K6 표시 제어 | `filelist/FileListHostFragment.kt` (기존, `onActivityCreated` `:103-` 의 관찰 등록 자리) | 뷰 수명 동안 K5 인스턴스 하나를 가진다(화면 단위 — 창 이동에도 시간이 이어짐, 화면 재생성 시 새로, SRS REQ-30-07). `FileJobStateTracker.stateLiveData`와 `Settings.FILE_JOB_INDICATOR`를 `viewLifecycleOwner`로 관찰하고(시작됨 상태일 때만 받음 = "화면이 보일 때"), K5 결과를 K7에 쓴다. 다음 시각은 뷰의 `postDelayed`로 예약하고 멈춤 때 지운다. K5가 결과 단계로 들어가면 `consumeResult(seq)`, 설정이 꺼진 채 결과를 받으면 바로 `consumeResult(seq)`(SRS A7). | REQ-30-03, 07, 11, 15 |
| K7 표시 단계 전달 | `filelist/DualPaneViewModel.kt` (기존) | `fileJobIndicatorPhaseLiveData: LiveData<FileJobIndicatorPhase>`(초기 `HIDDEN`)와 설정 함수. 두 창이 같은 값을 본다. 기존 활성 창·창 모드 LiveData와 같은 방식(`:10-51`). | REQ-30-03 |
| K8 표시할 창 | `filelist/FileJobIndicator.kt` (새 파일, 순수 함수) + `filelist/FileListFragment.kt` (기존, 관찰 등록 `:348-358` 근처) | `getFileJobIndicatorPaneIndex(isDualPaneEnabled, isDualPane, activePane)` = 두 창 모드면 `1`(끝 쪽 창), 아니면 `activePane`(보이는 창). 선택 모드(창 2 없음)는 `0`. 각 창은 K7 단계·창 모드·활성 창을 관찰해 `자기 index == 판정값`이면 그 단계를, 아니면 `HIDDEN`을 자기 K9에 준다. 기존 창 표시 갱신(`schedulePaneIndicatorUpdate`, `:598-`)과 같은 관찰 자리에 붙인다. | REQ-30-02, 03, 14 |
| K9 선 사람 뷰 | `ui/FileJobIndicatorView.kt` (새 파일) + 자세 계산 `ui/StickFigurePose.kt` (새 파일, 순수 함수) | `phase`를 받아 그린다. 3.4·3.5. `HIDDEN`이면 `GONE`(자리 없음). 누르기·초점 없음, 상태 설명과 알림 영역(live region). | REQ-30-02, 05, 06, 09, 10, 12, 13, 14 |
| K9a 툴바 배치 | `res/layout/file_list_fragment_app_bar_include.xml` (기존, `toolbar` `:27-36`) | `toolbar`의 자식으로 K9를 `layout_gravity="end"`, 기본 `visibility="gone"`으로 둔다. `Toolbar`는 끝 쪽 정렬 자식을 메뉴 보기의 시작 쪽에 놓아 메뉴 항목을 가리지 않는다(R1에서 LLD가 확인). `overlayToolbar`(`:38-47`)에는 두지 않는다(SRS A1·Q3). | REQ-30-02, 14 |
| K10 설정 | `settings/Settings.kt`(`FILE_LIST_ANIMATION` 뒤, `:124-127`), `res/values/donottranslate_prefs.xml`(`:52-53` 뒤), `res/xml/settings.xml`(인터페이스 분류, `:44-47` 뒤), `res/values/strings.xml`·`res/values-ko/strings.xml` | `FILE_JOB_INDICATOR: SettingLiveData<Boolean>` = `BooleanSettingLiveData(R.string.pref_key_file_job_indicator, R.bool.pref_default_value_file_job_indicator)`, 키 `key_file_job_indicator`, 기본 `true`. 설정 화면 `SwitchPreferenceCompat` 제목 "파일 작업 표시"·요약 "파일 작업 중 툴바에 달리는 사람을 표시"(문구 LLD 확정). | REQ-30-11 |
| K11 모션 줄이기 판정 | `util/AnimatorScale.kt` (새 파일, 이름 LLD) | `isReducedMotion(context)`: API 26 이상 `ValueAnimator.areAnimatorsEnabled()`의 반대, 미만은 `Settings.Global.ANIMATOR_DURATION_SCALE`를 읽어(기본 1) 0이면 참. 순수 부분 `isReducedMotion(scale: Float) = scale == 0f`는 단위 테스트. K9가 단계가 바뀔 때·나타날 때 부른다(SRS REQ-30-10). | REQ-30-10 |
| K12 문자열 | `res/values/strings.xml`, `res/values-ko/strings.xml` | 낭독 문구 셋("File operation in progress"/"파일 작업 진행 중", "File operation finished"/"파일 작업 완료", "File operation failed or canceled"/"파일 작업 실패 또는 취소됨"), 설정 제목·요약. | REQ-30-11, 12 |

### 2.1 하위 이슈 분배
나누지 않는다. 바뀌는 곳이 작업 쪽 세 파일(K2~K4)과 화면 쪽 여섯 곳으로 작고, 상태 통로와 화면이 함께 있어야 수동 확인이 된다. LLD 하나, feature PR 하나(`feature/30-job-runner`)로 한다.

## 3. 데이터 흐름·상태

### 3.1 작업 하나의 일생 (스레드)
| 순간 | 스레드 | 부르는 곳 | K1 보고 |
|---|---|---|---|
| 시작 함수 호출 | 메인(`@MainThread`, `FileJobService.kt:104`) | K2 ① | `onJobStarted(id)` |
| 대기 중 취소 | 메인(`:198-200`) | K2 ③ | `onJobFinished(id, false)` |
| 실행 중 취소 | 메인(`FileJobReceiver`는 메인에서 받음) | K2 ③ | `onJobFinished(id, false)` — 즉시. 끼어들기 뒤 스레드가 늦게 끝나며 보내는 보고는 중복이라 무시(K1a) |
| 본문 끝(성공·실패·취소·`Throwable`) | 작업 스레드 | K2 ② `finally` | 메인으로 넘겨 `onJobFinished(id, isSuccessful)` |
| 서비스 끝 | 메인(`onDestroy`) | K2 ③ | 남은 작업마다 `onJobFinished(id, false)` |

- `id`는 `FileJob.id`(`FileJob.kt:14`, `Random().nextInt()`)다. 같은 순간 두 작업의 id가 겹칠 확률은 무시할 만하지만 겹치면 집계가 하나로 합쳐진다(R4).
- 작업 스레드는 시작·끝에 한 번씩 메인으로 넘길 뿐이다(SRS 3.2). 진행률은 다루지 않는다.

### 3.2 결과 판정 (SRS REQ-30-04 ↔ 코드)
| 끝난 길 | `runOn` 반환(K3) | 판정 |
|---|---|---|
| `run()` 정상 반환, `hasUnresolvedError == false` | `true` | 성공 |
| `run()` 정상 반환, `hasUnresolvedError == true`(오류 창에서 건너뛰기·닫기, 텍스트 저장의 오류 창 취소 `FileJobs.kt:2309`) | `false` | 실패(Q2 임시 기본값) |
| `InterruptedIOException`(`FileJob.kt:24`) — 알림 취소, 오류·충돌 창 "취소" | `false` | 취소 |
| 그 밖의 `Exception`(`FileJob.kt:27`) | `false` | 실패 |
| `Throwable`(`Exception` 아님) — `runOn` 밖으로 나감 | 반환 없음 → K2의 기본값 `false` | 실패 |
| 실행 전·중 취소, 서비스 끝 | (K2 ③이 직접 보고) | 취소 |
충돌 창의 "건너뛰기"(`FileJobs.kt:1350`)는 `showConflictDialog`라 K4에 걸리지 않아 성공으로 남는다(SRS Q2). 묶음 판정은 K1a: 묶음 안 하나라도 `false`면 실패.

### 3.3 표시 시간 규칙 (K5)
상태: `phase`, `runningSince`(이 화면에서 달리기를 보이기 시작한 시각), `resultUntil`, `shownSeq`.

| 지금 | 입력 | 다음 |
|---|---|---|
| 아무 단계 | 설정 꺼짐 | `HIDDEN`, 받은 결과는 소비(A7) |
| `HIDDEN`·`SUCCEEDED`·`FAILED` | `runningCount > 0` | `RUNNING`, `runningSince = now`(결과 중단, REQ-30-08) |
| `RUNNING` | `runningCount > 0` | 그대로 |
| `HIDDEN` | `runningCount == 0`, `finished != null` | `RUNNING`, `runningSince = now`(화면 밖에서 끝난 결과도 달리기부터, REQ-30-15) |
| `RUNNING` | `runningCount == 0`, `finished != null`, `now - runningSince < 1000` | 그대로, `runningSince + 1000`에 다시 계산(REQ-30-07) |
| `RUNNING` | `runningCount == 0`, `finished != null`, 1초 지남 | `SUCCEEDED`/`FAILED`, `resultUntil = now + 1500`, `consume(seq)` |
| `RUNNING` | `runningCount == 0`, `finished == null`(다른 화면이 이미 소비, A3) | `HIDDEN` |
| `SUCCEEDED`·`FAILED` | `now >= resultUntil` | `HIDDEN` |
| `SUCCEEDED`·`FAILED` | 소비 뒤 상태가 `finished == null`로 바뀜 | 그대로(자기 소비이므로 `resultUntil`까지 유지) |
- 화면 단위: K5는 K6(호스트 뷰 수명)에 있다. 창 모드·보이는 창이 바뀌면 K8이 그리는 창만 바뀌고 K5 상태는 이어진다(REQ-30-03·07). 회전은 뷰를 새로 만들므로 새 K5 — 진행 중이면 다시 1초부터, 이미 소비된 결과는 다시 나오지 않는다(REQ-30-15).
- 화면이 멈추면(LiveData 관찰 중지) K6가 예약을 지운다. 다시 시작되면 받은 최신 상태로 K5를 다시 계산한다. 결과 단계에서 멈췄다 돌아오면 `resultUntil`이 지났으면 숨김.
- 시간은 `SystemClock.uptimeMillis()`(K6가 주입). Animator 길이 배율은 시간 규칙에 쓰지 않는다(SRS A4).

### 3.4 그리기 (K9)
- **방식**: `View`를 상속한 직접 그리기. 머리(원)와 몸·팔 둘·다리 둘(선 다섯)을 `Canvas.drawCircle`·`drawLine`으로 그린다. 자세는 관절 좌표(단위 상자 0~1)를 돌려주는 순수 함수 `StickFigurePose`(`running(t)`, `cheering(t)`, `fallen(t)`, `t`는 0~1 위상)로 계산한다. 애니메이션은 `ValueAnimator`(달리기는 약 600ms 주기 무한 반복, 만세는 팔 올리기 약 300ms 뒤 정지, 넘어짐은 약 400ms 동안 몸을 눕힘 뒤 정지)로 위상을 바꾸고 `invalidate()`. 사라질 때 약 150ms 서서히 사라짐(모션 줄이기면 없음).
- **모션 줄이기**(K11 참): 애니메이터를 돌리지 않고 정지 자세(`running(0.25)`·`cheering(1)`·`fallen(1)`)를 한 번 그린다. 사라짐 효과 없음. 시간 규칙(K5)은 같다.
- **크기**: 그림 24dp, 뷰는 너비 32dp × 툴바 높이(세로 가운데). 선 굵기 2dp, 끝 둥글게.
- **색**: 뷰의 테마(앱바 `?actionBarTheme`)에서 `colorControlNormal`을 읽는다 = `?android:textColorPrimary`(`res/values/styles.xml:61-64`) — 툴바 아이콘과 같다(REQ-30-13). 색 상태 목록이면 기본 상태 색.
- **RTL**: `layoutDirection`이 RTL이면 그리기 전에 가로 반전(`canvas.scale(-1, 1, 중심)`) — 끝 쪽을 향해 달림(SRS 3.5, `RTL-2`).
- **멈춤**: 애니메이터는 `phase != HIDDEN` + 붙어 있음 + `isShown`일 때만 돈다. `onDetachedFromWindow`·`onVisibilityAggregated(false)`에서 멈춘다(SRS 3.2). 툴바가 위로 밀려 가려진 경우(A2)는 뷰가 붙어 보이는 상태라 계속 돈다 — 작은 뷰라 비용은 작다(R3).

### 3.5 접근성 (K9, SRS REQ-30-09·12)
- `isClickable = false`, `isFocusable = false`(누름 효과·키보드 초점 없음, REQ-30-09). 화면 낭독기는 설명이 있는 뷰에 탐색 초점을 줄 수 있으므로 `importantForAccessibility = yes`.
- 설명: 단계마다 K12 문구를 `contentDescription`으로. 역할 말("버튼"·"그림") 없음. 클릭 동작을 노출하지 않으므로 "두 번 눌러 활성화" 안내가 없다.
- 알림: `ViewCompat.setAccessibilityLiveRegion(POLITE)`. 설명은 단계가 바뀔 때만 바꾼다 — 같은 단계의 반복 설정은 건너뛴다(반복 낭독 없음). `View.announceForAccessibility`는 API 36에서 사용 중단이라 쓰지 않는다. 창 이동(K8)으로 뷰가 바뀌면 새 뷰가 나타나며 같은 문구를 한 번 더 읽을 수 있다(R2).
- 가려짐(A1·A2) 중에도 단계 변화 알림은 뷰가 붙어 있으면 난다 — 선택 모드에서 `overlayToolbar`는 위에 겹쳐 보이고 아래 `toolbar`는 그대로 두며 입력 초점만 막는다(`ui/OverlayToolbarActionMode.kt:22-38` — `descendantFocusability`만 바꿈). 검색이 펼쳐진 동안 `Toolbar`가 일반 자식을 잠시 떼는 경우는 알림이 나지 않을 수 있다(R2).

### 3.6 수명주기·오류 전파
- K1은 프로세스 메모리에만 있다(SRS A5). 서비스가 다시 만들어져도 K1은 그대로다.
- K1·K5·K8·K11은 예외를 던지지 않는다. K2의 `finally` 보고가 실패하는 경우는 없다(메인 실행기에 넣기만 함). 작업 본문의 예외 전파는 지금과 같다 — K2는 잡지 않는다.

## 4. 외부 인터페이스
- **설정 키**: `key_file_job_indicator`(불리언, 기본 `true`) — 새 SharedPreferences 항목. 백업·이전 버전 영향 없음(없으면 기본값).
- **새 공개 API**(앱 내부): `FileJobStateTracker.stateLiveData`, `FileJobState`, `FileJobBatch`, `FileJobIndicatorPhase`, `getFileJobIndicatorPaneIndex`.
- **바뀌는 시그니처**: `FileJob.runOn(service): Unit` → `Boolean`. 부르는 곳은 `FileJobService.kt:58` 하나다.
- Intent·Provider·권한·파일 포맷: 없음.
- **추적표**: `docs/STANDARDS_TRACEABILITY.md` 4장에 #30 행(SRS 5장 조항, REQ-30-01~16)을 **feature PR에서** 추가한다 — #29 설계 PR #31이 같은 표의 머리 상태 줄·4장 끝·5장 이력을 고치는 중이라 지금 넣으면 충돌한다(부록 S "결과 로직을 바꾸는 PR은 같은 PR에서"는 feature PR이 충족).

### 4.1 #29와의 조율점
| 파일 | #29(PR #31 HLD)가 바꾸는 곳 | 이 설계가 바꾸는 곳 | 판단 |
|---|---|---|---|
| `filelist/FileListLiveData.kt` | 적재 로직 | 없음 | 겹침 없음 |
| `filelist/FileListFragment.kt` | `onFileListChanged`(`:707-739`) — 툴바 부제목 포함 | 관찰 등록(`:348-358` 근처)과 K9 바인딩 필드(`Binding`, `:1617-1660`) | 같은 파일, 다른 자리. 나중에 머지되는 쪽이 다시 맞춘다. 부제목은 건드리지 않는다. |
| `res/layout/file_list_fragment_app_bar_include.xml` | 없음(#29 HLD는 레이아웃을 바꾸지 않음) | `toolbar`에 자식 추가 | 겹침 없음 |
| `docs/STANDARDS_TRACEABILITY.md` | v0.7, 4장 #29 행 | feature PR에서 #30 행 | 순서대로(#29 먼저면 v0.8) |
- 진행 중 제목이 줄 때 #29의 부제목("Loading…"·항목 수)도 같은 너비 안에서 줄임표 처리된다(Toolbar 기본). 부제목 문자열·애니메이션(`ui/CrossfadeSubtitleToolbar.kt`)은 그대로다.

## 5. 설계 결정
| 결정 | 대안 | 고른 이유 |
|---|---|---|
| D1 상태 통로는 메인 스레드 전용 `object` + `LiveData`(K1), 작업 스레드는 메인으로 넘겨 보고 | (가) `runningJobs`를 `MutableLiveData`로 바꾸기 (나) `StateFlow` (다) `postValue` | (가)는 서비스가 없을 때(대기 중) 관찰할 수 없고 서비스 수명에 묶인다. (다)는 값이 합쳐져 짧은 작업의 시작·끝이 사라진다. (나)는 가능하나 화면 쪽이 모두 LiveData라(`DualPaneViewModel`, `Settings`) 맞춘다. 메인 전용이라 잠금이 필요 없다. |
| D2 결과는 `runOn`의 반환값(K3) + `finally` 기본값 실패(K2) | `FileJob`에 결과 필드를 두고 서비스가 읽기 | 반환값이면 결과를 빠뜨릴 수 없다. `finally`+기본값이면 `Throwable`도 실패로 끝나고 예외 전파는 지금과 같다(SRS REQ-30-04). |
| D3 건너뛴 오류는 `showErrorDialog` 한 곳에서 기록(K4) | 호출부마다 기록 | 호출부가 열 곳 이상이고 작업 본문을 바꾸지 않는다는 제약(SRS C-1). 한 곳이면 Q2 결정에 따라 넣고 빼기 쉽다. |
| D4 그리기는 직접 그리는 `View` + 순수 자세 함수(K9) | (가) `AnimatedVectorDrawable` 경로 변형 (나) Lottie | 정지 자세 셋(모션 줄이기)을 같은 함수로 그대로 얻고, 자세를 단위 테스트할 수 있다. (가)는 경로 변형용 경로를 손으로 맞춰야 하고 배율 0에서 끝 프레임으로 건너뛰어 정지 자세를 따로 만들어야 한다. (나)는 새 의존성(PROCESS_ROLES 10장)이고 이 크기·자세 수에 비해 크다. 새 의존성 없음(SRS C-3). |
| D5 시간 규칙은 호스트(K6)에, 그리기는 창(K8·K9)에 | 창마다 자기 시간 규칙 | 화면에 하나(REQ-30-03)이고 창 이동 시 1초가 이어져야 한다(REQ-30-07). 시간 규칙을 순수 클래스로 떼어 단위 테스트한다(SRS C-5). |
| D6 툴바 자식(K9a) | (가) 메뉴 항목 `actionLayout` (나) 호스트 위 겹침 뷰 | (가)는 창마다 메뉴 갱신(`FileListFragment.kt:380-` `menuProvider`)과 묶이고 더 보기 메뉴로 밀려 들어갈 수 있다. (나)는 끝 쪽 메뉴 항목을 가린다(REQ-30-02 위반). 툴바 자식은 `GONE`이면 자리가 없다(REQ-30-14). |
| D7 두 창 모드는 창 2, 한 창 모드는 보이는 창(K8) | 활성 창의 툴바 | 요구사항 "우측 상단" = 화면 끝 쪽. 활성 창을 따라가면 두 창 모드에서 왼쪽으로 옮겨 다닌다. 창 2는 LTR·RTL 모두 끝 쪽이다(#2 SRS A2). |
| D8 결과 소비는 결과 단계 진입 때(K6) | 결과 단계가 끝날 때 | 진입 때 소비해야 회전·다른 화면에서 다시 보이지 않는다(REQ-30-15, A3). 진입 뒤 회전하면 결과가 잘리는데, 다시 보이는 것보다 낫다고 본다. |

## 6. 위험과 미결 사항
| ID | 내용 | 대응 |
|---|---|---|
| R1 | `Toolbar`가 `layout_gravity="end"` 자식을 메뉴 보기의 시작 쪽에 놓는지(androidx `Toolbar.onLayout`), 검색이 펼쳐질 때 일반 자식을 떼는지는 이 저장소에 소스가 없어 확인하지 않았다(미확인). | LLD에서 androidx 소스로 확인. 다르면 `toolbar`를 감싸는 배치로 바꾸되 메뉴를 가리지 않는다는 REQ-30-02는 유지. |
| R2 | 알림 영역: 창 이동으로 새 뷰가 나타날 때 같은 문구를 한 번 더 읽을 수 있다. 검색 펼침 중에는 알림이 빠질 수 있다. | 창 이동은 사용자의 조작 직후라 한 번 더 읽어도 반복 낭독(같은 상태에서 계속)은 아니라고 본다. TalkBack 수동 확인(REQ-30-12)으로 확인하고, 문제면 LLD에서 창 이동 시 새 뷰의 알림 영역을 한 프레임 늦게 켠다. |
| R3 | 폰에서 툴바가 위로 밀려 가려져도 애니메이터가 돈다(3.4). | 24dp 선 다섯 개라 비용이 작다. 필요하면 LLD에서 앱바 오프셋으로 멈춘다. |
| R4 | `FileJob.id`가 `Random().nextInt()`라 동시에 도는 두 작업의 id가 겹칠 수 있다(알림 id도 같은 값을 쓰는 기존 동작). | 겹치면 두 작업이 하나로 세어져 먼저 끝난 쪽에서 묶음이 끝날 수 있다. 확률이 무시할 만하고 기존 알림도 같은 가정이라 받아들인다. |
| R5 | 범위 밖 결함: 작업이 `Throwable`로 끝나면 `runningJobs.remove`·잠금 갱신이 건너뛰어진다(`FileJobService.kt:57-63`이 `finally`가 아님). | 이 설계는 K1 쪽에서만 실패로 끝낸다(표시는 멈춤). `runningJobs`·잠금은 바꾸지 않고 별도 이슈로 올린다(PM 보고). |
| R6 | SRS Q1~Q3 오너 결정 전이다. | Q1 제외로 결정되면 K2 ①에 작업 종류 판정 하나(`OpenFileJob`·`InstallApkJob` 등은 `onJobStarted`를 부르지 않음 — 끝 보고는 모르는 id라 무시됨). Q2 성공으로 결정되면 K4 삭제. Q3 보이기로 결정되면 `overlayToolbar`에도 K9를 두고 K8 판정에 선택 모드를 더한다. |
| R7 | #29와 같은 파일(`FileListFragment.kt`) 수정. | 4.1 조율점. |

## 7. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-03 | v1 | 초안 | SRS v1.1 검수 통과 | 설계팀 |
