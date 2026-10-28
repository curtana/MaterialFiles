# 규격 추적표 — Android app quality guidelines

- 상태: v0.5 (2026-10-28. v0.1~v0.2 PM 보조 작성, v0.3 오너 결정 기록, v0.4 4장 #2 행 REQ ID, v0.5 Q7~Q9 오너 결정 — 5장 이력). 조항 해석·필수 여부 판정은 오너 승인 사항이다(PROCESS_ROLES 부록 S).
- 작성 규칙: 원문 문장은 옮기지 않고 한국어 요지만 적는다. 아래 근거 문서에서 직접 확인하지 못한 내용은 "원문 판독 필요"로 둔다.
- 결과 로직을 바꾸는 PR은 이 표를 같은 PR에서 고친다(부록 S).

## 1. 근거 문서와 판

확인 일자는 모두 2026-10-28(이 문서 작성일에 페이지를 직접 열어 읽음). "페이지 갱신일"은 각 페이지 하단의 Last updated 값이다.

| 약칭 | 문서 이름 | URL | 페이지 갱신일 | 확인 일자 | 항목 ID 체계 |
|---|---|---|---|---|---|
| CQ | Core app quality guidelines | https://developer.android.com/docs/quality-guidelines/core-app-quality | 2026-10-20 | 2026-10-28 | 이름형 ID(예: `Back_Gesture_Nav`) + 시험 ID(`T-…`). 문서에 단계(tier) 구분 없음 |
| AQ | Adaptive app quality guidelines (개요) — 옛 "Large screen app quality guidelines" URL이 이 개요를 보여 준다 | https://developer.android.com/docs/quality-guidelines/large-screen-app-quality | 2026-10-21 | 2026-10-28 | 개요에는 항목 ID 없음. Tier 3(Adaptive ready, 기본)·Tier 2(Adaptive optimized)·Tier 1(Adaptive differentiated). 옛 판은 보관본(2026-01-21 Large screen app quality guidelines, 2026-04-07 Tier 2)으로 남아 있다 |
| AQ3 | Tier 3 — Adaptive ready | https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-3 | 2026-10-21 | 2026-10-28 | 이름형 ID(예: `Config_Changes`) + 시험 ID |
| AQ2 | Tier 2 — Adaptive optimized | https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-2 | 2026-10-21 | 2026-10-28 | 이름형 ID(예: `Touch_Targets`) + 시험 ID |
| GN | Ensure compatibility with gesture navigation | https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav | 2026-10-23 | 2026-10-28 | ID 없음 → 이 문서의 로컬 ID `GN-n` |
| WSC | Use window size classes (Views) | https://developer.android.com/develop/ui/views/layout/use-window-size-classes | 2026-10-23 | 2026-10-28 | ID 없음 → 로컬 ID `WSC-n` |
| A11Y | Make apps more accessible | https://developer.android.com/guide/topics/ui/accessibility/apps | 2026-10-23 | 2026-10-28 | ID 없음 → 로컬 ID `A11Y-n` |
| A11P | Principles for improving app accessibility | https://developer.android.com/guide/topics/ui/accessibility/principles | 2026-10-23 | 2026-10-28 | ID 없음 → 로컬 ID `A11P-n` |
| RTL | Support different languages and cultures | https://developer.android.com/training/basics/supporting-devices/languages | 2026-10-23 | 2026-10-28 | ID 없음 → 로컬 ID `RTL-n` |

읽지 못했거나 읽지 않은 자료(해당 내용은 모두 "원문 판독 필요"):
- `View.setSystemGestureExclusionRects` API 레퍼런스(https://developer.android.com/reference/android/view/View) — 페이지가 커서 해당 메서드 설명을 판독하지 못했다. 제외 영역 크기 한도 등은 미확인.
- Google Play 정책(Developer Policy Center) — 부록 S 범위("Play policy")에 들지만 이번 초안에서 읽지 않았다.
- 접근성 시험 문서(TalkBack 시험 절차), Tier 1 문서, Views용 접근성 원칙 문서.

### 필수/권장 표기 기준 (이 프로젝트의 분류 — 오너 승인 2026-10-28)
원문은 항목마다 "필수(mandatory)" 같은 등급을 붙이지 않는다. CQ 도입부는 이 지침을 모든 앱이 충족해야 할 최소 품질로 소개하고, 항목 문장은 대부분 "앱은 ~한다"는 서술형이다. 그래서 다음과 같이 분류한다.
- **필수**: CQ 항목. 부록 S가 이 프로젝트가 지킬 규격으로 "Core App Quality"를 지정했기 때문이다.
- **권장**: AQ3·AQ2 항목(부록 S가 대화면 지침을 명시하지 않음 — 필수 승격은 오너 결정), 그리고 GN·WSC·A11Y·A11P·RTL의 구현 지침(품질 체크리스트 항목이 아님).
- CQ 항목이라도 이 앱 기능과 무관하면 "이 앱에 해당?"을 "아니오"로 둔다.

## 2. 조항 표

"이슈 #2"는 두 창 파일 목록 기능과의 관련을 뜻한다(4장). 시험 ID는 원문에 적힌 것만 적었다.

### 2.1 Core app quality (CQ) — 필수

수록 범위: 이번 판은 CQ 페이지의 항목 중 이슈 #2 관련 항목과 앱 전반의 핵심 항목만 담았다. 같은 페이지의 나머지 항목은 아직 싣지 않았다 — 예: `App_Switcher`, `Sleep_Resume`, `Lock_Resume`, `Runtime_Permissions`, `Permission_Rationale`, `Sensitive_Permissions`, `Sensitive_Data_Storage`, `Hardware_IDs`, `App_Data_Backup`, `Component_Permissions`, `Component_Protection`, `Network_Security_Configuration`, `App_Startup_Time`, `Rendering_Performance`, `StrictMode_Compliance`, `Android_Platform_Compatibility`, `SDK_Maintenance`, `Production_Build_Quality`, `Power_Management`, `Notification_Quality`, `System_Sharesheet`, `Graphic_Quality`, `Line_Length`, Google Play 항목(`Play_Content_Policies`, `Play_Content_Rating`, `Play_Feature_Graphic`, `Play_Device_References`, `Play_Misleading_Content`, `Play_User_Reviews`) 등. 미수록 항목도 CQ 항목이므로 필수로 보며, 다음 판에서 행으로 채운다.

| ID | 출처 | 요지(직접 인용 금지, 짧은 의역) | 필수/권장 | 이 앱에 해당? | 충족 방법·확인 방법 |
|---|---|---|---|---|---|
| `Back_Button_Nav` | CQ, 시험 T-Back_Nav | 표준 시스템 뒤로 가기를 쓰고, 화면 안에 따로 만든 뒤로 버튼을 두지 않는다. | 필수 | 예(이슈 #2) | 시스템 뒤로 가기를 활성 창의 기존 처리(선택 해제·상위 폴더)에 연결한다. 확인: 두 창 모드·한 창 모드에서 뒤로 가기가 활성 창에만 작용하는지 수동 시험. |
| `Back_Gesture_Nav` | CQ, 시험 T-Back_Nav | 제스처 내비게이션의 뒤로·홈 이동을 지원한다. | 필수 | 예(이슈 #2) | 창 전환 쓸기가 가장자리 뒤로 제스처·하단 홈 제스처를 가로채지 않게 한다(GN-1~3 참조). 확인: API 29 이상·제스처 내비게이션에서 좌우 가장자리 쓸기 → 뒤로 가기만 일어남. |
| `State_Preservation` | CQ, 시험 T-State_Preservation, T-Back_Nav | 앞에서 벗어날 때 상태를 저장하고 돌아오면 복원해 뜻하지 않은 데이터 손실을 막는다. 짧게(수 분) 떠나 있었으면 최대한 그대로, 오래 떠나 있었으면 복원하거나 첫 화면에서 다시 시작해도 된다. | 필수 | 예(이슈 #2) | 두 창의 경로·기록·선택·복사 목록·확인 창을 화면 재생성에 견디게 둔다. 확인: 홈으로 나갔다가 몇 분 안에 복귀, 최근 앱에서 복귀. 앱 프로세스가 종료된 뒤 기본 폴더로 시작하는 기존 동작(SRS A6)은 경로 복원이 없어 편차로 등록했다(3장, 오너 승인 2026-10-28, 해소 이슈 #11). |
| `Orientation_Transitions` | CQ, 시험 T-Orientation_Transitions | 방향 전환·접기/펴기를 빠르게 반복해도 그리기 문제나 상태 손실이 없다. | 필수 | 예(이슈 #2) | 회전 시 한 창↔두 창 전환에서 상태 유지. 확인: 빠른 연속 회전 수동 시험. |
| `Display_State_Parity` | CQ, 시험 T-Orientation_Transitions | 모든 방향·접힘 상태에서 같은 기능과 동작을 제공한다. | 필수 | 예(이슈 #2) | 한 창 모드에서도 두 창 모드와 같은 명령(반대쪽으로 복사·이동, 창 전환, 반대쪽 경로 확인)을 쓸 수 있게 한다. 확인: 세로·가로 각각 명령 목록 비교. |
| `Fullscreen_Display` | CQ, 시험 T-Orientation_Transitions | 두 방향·접힘 상태 모두에서 창을 꽉 채우고 레터박스가 없다(작은 보정은 허용). | 필수 | 예 | 기존 화면 유지. 확인: 가로·세로·분할 화면에서 여백 띠가 없는지. |
| `Consistent_UX` | CQ, 시험 T-Consistent_UX, T-SD_Card | 모든 사용 경우와 폼 팩터에서 일관된 경험을 준다. | 필수 | 예 | 두 창 기능의 동작을 기기 구성(SRS 3.2 C1~C4)에 관계없이 같게 한다. 확인: C1~C4 수동 시험. |
| `Theme_Support` | CQ, 시험 T-Theme_Support | 앱 콘텐츠가 밝은·어두운 테마를 모두 지원한다. | 필수 | 예(이슈 #2) | 활성 창 표시가 두 테마에서 보이게 한다. 확인: 두 테마 각각 수동 확인. |
| `Touch_Target_Size` | CQ, 시험 T-Touch_Target_Size | 터치 대상은 48dp 이상이고, 화면 크기가 달라도 크기·위치가 일관된다. | 필수 | 예(이슈 #2) | 새로 넣는 창 표시·전환 수단·확인 창 버튼을 48dp 이상으로. 확인: Accessibility Scanner 또는 레이아웃 검사. |
| `Visual_Contrast` | CQ, 시험 T-Visual_Contrast | 큰 글자·그래픽은 3:1 이상, 작은 글자(18pt 미만, 굵게는 14pt 미만)는 4.5:1 이상 대비. | 필수 | 예(이슈 #2) | 활성 창 구분 표시와 창 번호 글자의 대비를 맞춘다. 확인: Accessibility Scanner. |
| `Content_Description` | CQ, 시험 T-Content_Description | TextView를 뺀 모든 UI 요소에 contentDescription을 둔다. | 필수 | 예(이슈 #2) | 새 아이콘·버튼·창 표시에 설명을 단다. 확인: Lint·Accessibility Scanner. |
| `Stability_ANR` | CQ, 시험 T-Stability_ANR | 비정상 종료나 UI 스레드 막힘이 없고, 사전 출시 보고서·Android vitals로 확인한다. | 필수 | 예 | 두 창이 각자 목록을 불러올 때 UI 스레드에서 무거운 일을 하지 않는다. 확인: 수동 시험 중 ANR·크래시 없음, StrictMode. |
| `Target_SDK_Version`, `Compile_SDK_Version` | CQ, 시험 T-Target_SDK_Version | Play 요구에 맞는 최신 targetSdk를 지정하고 최신 SDK로 컴파일한다. | 필수 | 예 | 현재 `targetSdk 34`(`app/build.gradle:37`). Play 요구 버전과의 차이는 원문 판독 필요(Play 정책 미판독). 확인: 릴리스 전 Play 요구와 대조. |
| `Minimize_Permissions` | CQ, 시험 T-Minimize_Permissions | 현재 사용 경우에 필요한 권한만 요청한다. | 필수 | 예 | 이슈 #2는 새 권한을 추가하지 않는다(SRS 3.1). 확인: PR의 매니페스트 diff. |
| `Graceful_Degradation` | CQ, 시험 T-Graceful_Degradation | 권한을 거부·회수해도 다른 경로로 계속 동작한다. | 필수 | 예 | 기존 동작 유지. 확인: 저장소 권한 거부 상태에서 앱이 막히지 않는지. |
| `Component_Export` | CQ, 시험 T-Component_Export | 모든 컴포넌트에 `android:exported`를 명시하고 필요한 것만 내보낸다. | 필수 | 예 | 새 컴포넌트를 추가하면 명시. 확인: 매니페스트 검사. |
| `Non_SDK_Interfaces` | CQ, 시험 T-Non_SDK_Interfaces | 비 SDK 인터페이스를 쓰지 않는다(Lint로 찾을 수 있음). | 필수 | 예 | 확인: Lint. 기존 코드의 사용 여부는 원문 판독 필요가 아니라 코드 점검 필요(미확인). |
| `Network_Security_Traffic` | CQ | 모든 통신을 SSL/TLS로 하고 평문 통신을 끈다. | 필수 | 예(앱 전반) | 매니페스트에 `android:usesCleartextTraffic="true"`(`AndroidManifest.xml:57`)가 있고, 앱이 평문 프로토콜(FTP 등)을 지원한다. 편차 후보 — 오너 판단 후 3장 편차 등록부에 올릴지 결정. |
| `Sensitive_Data_Logging` | CQ | 개인·민감 정보를 로그에 남기지 않는다. | 필수 | 예(앱 전반) | 원격 접속 정보·경로 로그 점검 필요(미확인). |

### 2.2 Adaptive app quality (AQ3·AQ2) — 권장

| ID | 출처 | 요지(직접 인용 금지, 짧은 의역) | 필수/권장 | 이 앱에 해당? | 충족 방법·확인 방법 |
|---|---|---|---|---|---|
| `Config_Changes` | AQ3, 시험 T-Config_Orientation, T-Config_State | 표시 영역(전체 화면 또는 멀티 윈도 경계)을 넘치지 않고 채우며, 레터박스·호환 모드가 아니다. 회전·접기/펴기·창 크기 변경 뒤에도 상태(스크롤 위치, 입력 중 글자, 키보드 상태 등)를 유지하거나 복원한다. | 권장 | 예(이슈 #2) | 두 창의 경로·선택·스크롤·검색어를 구성 변경 뒤에도 유지(SRS REQ-2-04·15). 확인: 회전·분할 화면 크기 변경 수동 시험. |
| `Config_Combinations` | AQ3, 시험 T-Config_Combinations | 크기 변경 뒤 회전처럼 구성 변경이 겹쳐도 처리한다. | 권장 | 예(이슈 #2) | 확인: 분할 화면 크기 변경 → 회전 연속 시험. |
| `Multi-Window_Functionality` | AQ3, 시험 T-Multi-Window_Functionality | 멀티 윈도 모드에서 모든 기능이 동작한다. | 권장 | 예(이슈 #2) | 분할 화면에서 창 너비에 따라 한 창·두 창을 다시 판단(SRS REQ-2-03·04). 확인: SRS C3. |
| `Multi-Resume` | AQ3, 시험 T-Multi-Window_Focus, T-Multi-Window_Resources | 포커스를 잃어도 UI를 계속 갱신하고, 배타 자원(카메라·마이크)은 포커스에 따라 놓고 다시 잡는다. | 권장 | 일부(앱 전반) | 파일 작업 진행 표시가 포커스 없는 상태에서도 갱신되는지. 배타 자원은 해당 없음. |
| `Keyboard_Input` | AQ3, 시험 T-Keyboard_Input | 외부 키보드로 글자 입력이 되고, 물리·가상 키보드 전환에 앱이 다시 시작하지 않는다. | 권장 | 예(앱 전반) | 검색·이름 바꾸기 입력. 확인: 외부 키보드 연결·분리 시험. |
| `Mouse_Trackpad_Basic` | AQ3, 시험 T-Mouse_Trackpad_Basic | 마우스·트랙패드로 클릭, 선택, 목록의 세로·가로 스크롤 같은 기본 조작이 된다. | 권장 | 예(이슈 #2) | 쓸기 전용 창 전환은 포인터로 하기 어려우므로 누르는 전환 수단을 함께 둔다. 창 표시 누르기 전환으로 충족하기로 결정됨(REQ-2-20, 요구사항 v2 결정 Q3 — SRS는 PR #3, develop 미머지). |
| `Responsive_adaptive_layouts` | AQ2, 시험 T-Layout_Flow | 창 크기 등급에 따라 레이아웃이 반응·적응하고, 알맞은 곳에 다중 창(multi-pane) 레이아웃을 쓴다. 옛 액티비티 기반 앱은 Activity embedding을 쓰도록 안내한다. | 권장 | 예(이슈 #2) | 두 창 레이아웃이 이 조항의 다중 창 레이아웃에 해당한다. Activity embedding 미사용은 편차 아님(오너 결정, 2026-10-28 — 한 액티비티 안 두 창이 다중 창 레이아웃 권장을 충족). |
| `UI_Secondary_Elements` | AQ2, 시험 T-Layout_Flow | 대화상자·메뉴 같은 보조 요소가 모든 화면 유형·상태에서 알맞게 배치된다(큰 화면에서 버튼·입력란을 전체 폭으로 늘이지 않음, 서랍은 큰 화면에서 펼친 레일 등). | 권장 | 예(이슈 #2) | 복사·이동 확인 창을 큰 화면에서 전체 폭으로 늘이지 않는다. 확인: C2·C4에서 확인 창 모양. |
| `Touch_Targets` | AQ2, 시험 T-Touch_Targets | 터치 대상이 48dp 이상이고 어떤 표시 크기에서도 가려지지 않는다. | 권장 | 예(이슈 #2) | `Touch_Target_Size`와 같이 충족. 두 창 모드에서 좁아진 창의 버튼이 가려지지 않는지 확인. |
| `Keyboard_Navigation` | AQ2, 시험 T-Keyboard_Navigation | 주요 작업 흐름을 Tab·방향키로 끝낼 수 있다. | 권장 | 예(이슈 #2) | 창 전환·반대쪽 복사를 키보드로 할 수 있는지 검토. 확인: 외부 키보드 시험. |
| `Keyboard_Shortcuts` | AQ2, 시험 T-Keyboard_Shortcuts | 선택·잘라내기·복사·붙여넣기·실행 취소·다시 실행에 단축키가 있다. | 권장 | 예(앱 전반) | 기존 지원 여부 미확인. |
| `Keyboard_Exit` | AQ2, 시험 T-Keyboard_Exit | Esc로 대화상자·메뉴 닫기, 검색 지우기, 선택 해제 등 현재 동작을 빠져나온다. | 권장 | 예(이슈 #2) | 확인 창이 Esc로 취소되고 취소는 아무것도 바꾸지 않는지(SRS REQ-2-15). |
| `Context_Menus` | AQ2, 시험 T-Context_Menus | 오른쪽 클릭(트랙패드 보조 탭)으로 컨텍스트 메뉴가 열린다. | 권장 | 예(앱 전반) | 기존 지원 여부 미확인. |

### 2.3 구현 지침 (GN·WSC·A11Y·A11P·RTL) — 권장

| ID | 출처 | 요지(직접 인용 금지, 짧은 의역) | 필수/권장 | 이 앱에 해당? | 충족 방법·확인 방법 |
|---|---|---|---|---|---|
| GN-1 | GN | 뒤로 제스처는 왼쪽·오른쪽 가장자리에서 안쪽으로 쓸기, 홈·빠른 전환은 화면 아래쪽 쓸기다. 그 영역에 있는 앱의 요소·제스처와 겹칠 수 있으며, 문서는 그 대처 방법으로 GN-2·GN-3을 안내한다. | 권장 | 예(이슈 #2) | 가장자리에서 시작한 터치는 창 전환으로 보지 않는다(SRS REQ-2-11(a)). |
| GN-2 | GN | API 29부터 `View.setSystemGestureExclusionRects()`로 일부 영역에서 뒤로 제스처를 끌 수 있다(`ViewCompat`에도 있음). `DrawerLayout`·`SeekBar`는 자동으로 제외 영역을 설정한다. | 권장 | 예(이슈 #2) | 창 전환을 위해 제외 영역을 요청하지 않는다(SRS 5장 방침). 기존 `DrawerLayout`의 자동 제외는 그대로 둔다. |
| GN-3 | GN | 홈·빠른 전환 제스처는 앱이 끌 수 없으며, `WindowInsets.getMandatorySystemGestureInsets()`(API 29)로 그 인식 영역을 알 수 있다. | 권장 | 예(이슈 #2) | 하단 필수 제스처 영역에서 시작한 터치는 창 전환으로 보지 않는다. 확인: 하단 가장자리 쓸기 시험. |
| GN-4 | GN·View 레퍼런스 | 제외 영역 크기 한도(가장자리별 높이 상한 등) | 원문 판독 필요 | 원문 판독 필요 | 원문 판독 필요(View 레퍼런스 미판독, 1장). 제외 영역을 쓰지 않는 한 영향 없음. |
| WSC-1 | WSC | 창 너비 등급: Compact 600dp 미만, Medium 600~840dp 미만, Expanded 840~1200dp 미만, Large 1200~1600dp 미만, Extra-large 1600dp 이상. 높이 등급: 480dp 미만 / 480~900dp 미만 / 900dp 이상. | 권장 | 예(이슈 #2) | 두 창 판단 경계 600dp(SRS REQ-2-03)는 Compact/Medium 경계와 같다. 확인: 경계 599/600dp 시험. |
| WSC-2 | WSC | 등급은 기기 화면 크기가 아니라 앱 창 크기로 정하며, "태블릿인가" 판단에 쓰지 않는다. 회전·멀티태스킹·접기로 앱 실행 중에도 바뀌므로 UI가 따라 바뀌어야 한다. 예시는 Jetpack WindowManager의 `WindowMetricsCalculator`·`WindowSizeClass.compute()`를 `View.onConfigurationChanged()`에서 다시 계산한다. | 권장 | 예(이슈 #2) | 실제 창 너비로 판단하고 구성 변경마다 다시 판단(SRS REQ-2-03·04). 고정 메뉴 너비를 빼는 것은 이 앱의 결정(규격 요구 아님). |
| WSC-3 | WSC | 폰 가로는 너비가 Medium이어도 높이가 Compact인 경우가 많아 두 창 레이아웃이 실용적이지 않다고 설명한다. 너비가 보통 더 중요하지만 높이도 고려해야 한다. | 권장 | 예(이슈 #2) | SRS C2(폰 가로 → 두 창)와 긴장 관계. 높이 조건을 넣을지, 그대로 둘지 설계팀·오너 결정 필요. |
| A11Y-1 | A11Y | 조작 요소의 터치 대상은 최소 48×48dp, 가능하면 더 크게. 마우스·트랙패드처럼 정밀한 입력은 더 작아도 된다. | 권장 | 예(이슈 #2) | `Touch_Target_Size`와 같이 충족. |
| A11Y-2 | A11Y | 의미 있는 요소마다 생김새가 아닌 목적을 말하는 설명을 붙이고, "버튼" 같은 중복 말을 피하며, 목록 안에서는 설명이 서로 달라야 한다. 장식 요소는 접근성 서비스에서 숨긴다. | 권장 | 예(이슈 #2) | 창 표시는 "창 1, 활성"처럼 목적을 읽게(SRS 3.3). 확인: TalkBack 수동 확인. |
| A11Y-3 | A11Y | 글자 대비 목표: 18sp 미만(굵게는 14sp 미만) 4.5:1 이상, 나머지 3:1 이상. | 권장 | 예(이슈 #2) | `Visual_Contrast`와 같이 충족. |
| A11P-1 | A11P | 끌기·목록 항목 쓸기처럼 제스처로만 할 수 있는 동작은 접근성 서비스(TalkBack, Voice Access, Switch Access)에 동작(action)으로 노출해 그 사용자도 실행할 수 있게 한다. | 권장 | 예(이슈 #2) | 쓸기 창 전환에 대응하는 접근성 동작 또는 누르는 전환 수단을 둔다. REQ-2-20(창 표시 누르기 전환·동작 설명, 요구사항 v2 결정 Q3)으로 충족하기로 결정됨(SRS는 PR #3, develop 미머지). 두 창 모드 활성화는 REQ-2-07. Views API 이름은 Views판 문서 원문 판독 필요. |
| A11P-2 | A11P | 색만으로 구분하지 말고 모양·크기, 글자·무늬, 소리·진동 같은 다른 단서를 함께 쓴다. | 권장 | 예(이슈 #2) | 활성 창 표시에 색 외 형태 차이를 쓴다(SRS 3.3, REQ-2-08). |
| A11P-3 | A11P | 동작에 클릭·길게 누르기 설명(label)을 붙이면 TalkBack이 일반적인 제스처 안내 대신 그 설명을 읽는다. 관련 요소는 묶어서 한 번에 읽게 할 수 있다. | 권장 | 예(이슈 #2) | 창 표시를 눌러 전환하는 수단을 두면 그 동작 설명을 붙인다. |
| A11P-4 | — | TalkBack 시험 절차 | 원문 판독 필요 | 원문 판독 필요 | 원문 판독 필요(접근성 시험 문서 미판독, 1장). |
| RTL-1 | RTL | 매니페스트 `<application>`에 `android:supportsRtl="true"`(API 17 이상 대상)를 두고, 레이아웃·코드에서 left/right 대신 start/end를 써서 로캘에 따라 좌우 반전되게 한다. | 권장 | 예(이슈 #2) | 이미 `supportsRtl="true"`(`AndroidManifest.xml:55`). 두 창 배치·창 표시에 start/end를 쓴다. 확인: 개발자 옵션 "RTL 레이아웃 방향 강제". |
| RTL-2 | RTL | 방향별 리소스는 `-ldrtl`/`-ldltr` 한정자로, 단순 drawable은 `android:autoMirrored`로 반전한다. 방향에 따라 뜻이 바뀌는 그림은 직접 반전한다. | 권장 | 예(이슈 #2) | 새 화살표·전환 아이콘을 넣으면 반전 처리. |
| RTL-3 | RTL | 문장에 끼워 넣는 글자(주소·번호 등)는 `BidiFormatter.unicodeWrap()`으로 감싸 표시 순서가 깨지지 않게 한다. | 권장 | 예(이슈 #2) | 확인 창의 "원래 위치 → 대상 경로"와 반대쪽 경로 표시에 적용 검토. |
| RTL-4 | RTL | 레이아웃 방향 변경은 `onRtlPropertiesChanged()`에서 처리하고, 개발자 옵션 "RTL 레이아웃 방향 강제"로 LTR 언어에서도 미리 볼 수 있다. | 권장 | 예 | 확인 방법으로 사용. |
| RTL-5 | — | RTL에서 쓸기 제스처 방향을 반전해야 하는지 | 원문 판독 필요 | 원문 판독 필요 | 확인한 RTL 문서는 화면 배치 방향만 다루고 제스처 방향은 다루지 않는다. SRS A2(제스처 반전)는 요구사항 v2 결정 Q2(오너)로 확정된 요구사항 결정이며 규격 근거는 확인되지 않았다(원문 판독 필요). |

### 2.4 미판독 영역

| ID | 출처 | 요지(직접 인용 금지, 짧은 의역) | 필수/권장 | 이 앱에 해당? | 충족 방법·확인 방법 |
|---|---|---|---|---|---|
| PLAY-1 | Google Play 정책(Developer Policy Center) | 원문 판독 필요 | 원문 판독 필요 | 원문 판독 필요 | 원문 판독 필요(Play 정책 원문 미판독, 1장). CQ 페이지의 Play 항목(`Play_*`)은 CQ 쪽에서 채운다(2.1 수록 범위). |

## 3. 편차 등록부

규격에서 벗어나는 결정은 여기에 올리고 오너 승인 뒤에만 머지한다(부록 S).

| 일자 | 조항 | 편차 | 사유 | 오너 승인 | 해소 이슈 |
|---|---|---|---|---|---|
| 2026-10-28 | `Network_Security_Traffic` (필수) | 평문 통신 허용(`AndroidManifest.xml:57` `usesCleartextTraffic="true"`)을 유지한다. | FTP와 `http://` WebDAV 서버 접속이 앱 기능이며 평문 프로토콜이라 끌 수 없다. 암호화 방식(FTPS·FTPES·SFTP·`https://` WebDAV)은 별도로 제공된다. | 승인 (오너, 2026-10-28) | — |
| 2026-10-28 | `State_Preservation` (필수) | 앱 프로세스가 백그라운드에서 종료된 뒤 다시 열리면 두 창 모두 기본 폴더(또는 Intent 경로)에서 시작하고 이전 창 경로를 복원하지 않는다(기존 동작, `FileListViewModel.kt:25` 경로 상태 미저장, `FileListFragment.kt:282-338` 시작 경로 결정 — 이슈 #2 SRS A6). | 기존 동작이며 이슈 #2(두 창 파일 목록)가 새로 만든 것이 아니다. 이슈 #2 범위 밖 "창 경로의 영구 저장"과 구분되는 상태 복원 작업이라 별도 이슈로 해소한다. | 승인 (오너, 2026-10-28) | #11 |

권장 조항에서 의식적으로 다르게 정한 결정(편차 등록 대상은 아니지만 근거를 남긴다):
- **WSC-3** (2026-10-28, 오너 결정): 폰 가로(너비 600dp 이상, 높이 Compact)에서도 두 창을 표시한다. 이슈 #2 요구사항(너비 600dp 기준)을 유지하며, 높이 조건은 넣지 않는다.

## 4. 기능별 추적

| 이슈 | 관련 조항 ID | REQ ID | 비고 |
|---|---|---|---|
| #2 두 창 파일 목록 — 시스템 뒤로 가기·제스처 충돌 | `Back_Button_Nav`, `Back_Gesture_Nav`, GN-1, GN-2, GN-3, GN-4 | REQ-2-02, REQ-2-09, REQ-2-10, REQ-2-11 | 제외 영역을 요청하지 않으면 GN-4는 영향 없음 |
| #2 — 큰 화면·창 크기·멀티 윈도 | `Fullscreen_Display`, `Consistent_UX`, `Display_State_Parity`, `Multi-Window_Functionality`, `Responsive_adaptive_layouts`, WSC-1, WSC-2, WSC-3 | REQ-2-03, REQ-2-04, REQ-2-05, REQ-2-06, REQ-2-12, REQ-2-17, REQ-2-20 | WSC-3: 폰 가로도 두 창 유지(오너 결정, 3장) |
| #2 — 상태 유지(회전·구성 변경) | `State_Preservation`, `Orientation_Transitions`, `Config_Changes`, `Config_Combinations` | REQ-2-04, REQ-2-05, REQ-2-15, REQ-2-18 (SRS A6) | |
| #2 — 접근성(터치 대상·쓸기 대체·TalkBack·색 외 구분) | `Touch_Target_Size`, `Touch_Targets`, `Visual_Contrast`, `Content_Description`, `Theme_Support`, `Mouse_Trackpad_Basic`, `Keyboard_Navigation`, A11Y-1, A11Y-2, A11Y-3, A11P-1, A11P-2, A11P-3, A11P-4 | REQ-2-07, REQ-2-08, REQ-2-15, REQ-2-17, REQ-2-20 (SRS 3.3) | A11P-1은 REQ-2-20(창 표시 누르기 전환·동작 설명, 요구사항 v2 결정 Q3)으로 충족하기로 결정됨. 두 창 모드 활성화는 REQ-2-07 |
| #2 — 확인 대화상자 | `UI_Secondary_Elements`, `Keyboard_Exit` | REQ-2-14, REQ-2-15, REQ-2-21 | 복사·이동 전 확인 창 자체를 요구하는 조항은 확인한 문서에 없다 — 확인 창은 규격 요구가 아닌 요구사항 결정이다 |
| #2 — RTL | RTL-1, RTL-2, RTL-3, RTL-4, RTL-5 | REQ-2-06, REQ-2-07, REQ-2-11의 방향(SRS A2), REQ-2-15·17의 경로 표시 (SRS 3.5) | RTL-5는 원문 판독 필요 |

## 5. 변경 이력

| 일자 | 판 | 내용 | 작성 |
|---|---|---|---|
| 2026-10-28 | v0.1 | 초안. CQ(2026-10-20 판), AQ·AQ3·AQ2(2026-10-21 판), GN·WSC·A11Y·A11P·RTL(2026-10-23 판) 근거로 작성. | PM 보조 |
| 2026-10-28 | v0.2 | 검수 1회차(PR #4, 조건부 통과) 경미 지적 반영: 필수/권장 기준의 원문 서술 정정, `Fullscreen_Display` 시험 ID 추가, 2.1 수록 범위·미수록 CQ 항목 명시, GN-1 의무 서술 완화, SRS v4 결정(REQ-2-20, Q2·Q3) 참조. | PM 보조 |
| 2026-10-28 | v0.3 | 오너 결정 기록: 필수/권장 분류 승인, `Network_Security_Traffic` 편차 등록(평문 통신 유지), WSC-3 폰 가로 두 창 유지. | PM |
| 2026-10-28 | v0.4 | 머리 상태 표기를 판과 맞춤. 2.2 `Mouse_Trackpad_Basic`·2.3 A11P-1·RTL-5 행의 상태 표현을 결정 사항으로 정리. 4장 #2 행의 REQ ID 열을 SRS v4.4(브랜치 `design/2-dual-pane`, PR #3) 기준으로 채움. 접근성 행 비고를 결정 사항(A11P-1 → REQ-2-20)으로 고침 | 설계팀 |
| 2026-10-28 | v0.5 | 오너 결정 Q7~Q9 반영: 3장 편차 등록부에 "해소 이슈" 열 추가와 `State_Preservation` 편차 행(해소 #11), 2.1 `State_Preservation` 행의 "오너 확인" 표현 정리, 2.2 `Responsive_adaptive_layouts` 비고를 "편차 아님(오너 결정)"으로 | 설계팀 |
