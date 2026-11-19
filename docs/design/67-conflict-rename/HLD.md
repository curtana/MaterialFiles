# HLD — 충돌 창 회전 시 새 이름 보존 (이슈 #67)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1). v1.1은 경미 지적 2건 반영 |
| 근거 | SRS v1.1 |
| 기준 코드 | `origin/develop` 454206d. 줄 번호는 이 커밋 기준, 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준 |

## 1. 구조 개요
바뀌는 곳은 `filejob/` 안의 충돌 창 Fragment 하나와, 복원 규칙을 담는 같은 패키지의 작은 순수 함수 하나다. 작업 쪽(`filejob/FileJobs.kt`의 충돌 처리, `Args` 생성 `:737-743`)과 창을 띄우는 `filejob/FileJobConflictDialogActivity.kt`는 바꾸지 않는다(SRS A4). 새 모듈 경계는 없다.

```mermaid
flowchart LR
  S[onSaveInstanceState] -->|State: isAllChecked, newName, isNameLayoutVisible| B[(Bundle)]
  B --> C[onCreateDialog]
  C -->|saved State, targetFileName| R[conflictNameRestore 순수 함수]
  R -->|입력란 글자, 펼침, 이름 바꾸기 여부, 체크 상태| C
```

## 2. 구성요소
| 구성요소 | 위치(패키지·파일) | 책임 | 추적 REQ |
|---|---|---|---|
| C1 저장 상태 확장 | `filejob/FileJobConflictDialogFragment.kt` `State`(`:351-354`), `onSaveInstanceState`(`:64-68`) | `State`에 `newName: String?`(새 이름이 있을 때만 그 글자, 없으면 `null`)와 `isNameLayoutVisible: Boolean`을 더해 저장한다. `isAllChecked`는 그대로 | REQ-67-01, REQ-67-02 |
| C2 복원 규칙 | `filejob/` 새 파일(예: `FileJobConflictNameRestore.kt`)의 순수 함수 | 저장 상태(없으면 `null`)와 대상 원래 이름을 받아 복원할 화면 값(입력란 글자, 입력란 펼침, 긍정 버튼이 "이름 바꾸기"인지, "모든 파일에 적용"의 체크·사용 가능)을 돌려준다. Android 타입을 쓰지 않아 JVM 단위 테스트가 가능하다 | REQ-67-01, REQ-67-02 |
| C3 복원 적용 | `filejob/FileJobConflictDialogFragment.kt` `onCreateDialog`(`:70-148`) | C2의 결과를 뷰에 적용한다: 입력란 초기 글자(`:121-122`), `nameLayout` 표시와 화살표 회전(`:108-115`과 같은 최종 값, 애니메이션 없이), `allCheck` 체크·사용 가능(`:136-138` 대체), 긍정 버튼 초기 글자(`:140`) | REQ-67-01, REQ-67-02 |
| C4 불변 부분 | 같은 파일 `:73-107`, `:108-120`(펼침 클릭 리스너. C3는 그 최종 값만 따라 쓴다), `:123-135`, `:213-251`, `companion`, `Args` | 바꾸지 않는다: `Args.isMerge` 판정, 제목·메시지, `replaceWarningText` 조건, 펼침 클릭 동작, 글자 변경 리스너, 끝 아이콘, 버튼 처리, `hasNewName` | REQ-67-03, REQ-67-04 |

## 3. 데이터 흐름·상태
- **저장**: `onSaveInstanceState`에서 `newName = if (hasNewName()) nameEdit 글자 else null`, `isNameLayoutVisible = nameLayout.isVisible`, `isAllChecked = allCheck.isChecked`.
- **복원 규칙(C2)**:

| 저장 상태 | 입력란 글자 | 입력란 펼침 | 긍정 버튼 | 모든 파일에 적용 |
|---|---|---|---|---|
| 없음(처음 열림) | 원래 이름 | 접힘 | `isMerge`에 따라 교체/합치기 | 해제, 사용 가능 |
| `newName == null` | 원래 이름 | 접힘(펼침 저장값은 무시, REQ-67-02) | 교체/합치기 | 저장값, 사용 가능 |
| `newName != null` | `newName` | 저장값(SRS A3) | 이름 바꾸기 | 해제, 사용 불가 |

  규칙의 "이름 바꾸기" 판단은 `hasNewName()`(`:244-251`)과 같다. `newName`은 저장 때 이미 그 조건을 통과한 값만 들어가지만, C2는 방어로 `newName`이 비었거나 원래 이름과 같으면 `null`처럼 다룬다.
- **적용 순서(C3)**: 입력란 초기 글자는 글자 변경 리스너 등록(`:123`) **전에** 넣는다. 리스너는 `requireDialog()`로 긍정 버튼을 찾는데(`:129-131`), `onCreateDialog` 안에서는 창이 아직 Fragment에 연결되지 않았고 긍정 버튼도 창을 보이기 전까지 만들어지지 않는다. 그래서 긍정 버튼 글자는 리스너가 아니라 빌더의 `setPositiveButton` 초기 글자(`:140`)로 정한다. 버튼 클릭 처리 함수(`::onDialogButtonClick`)와 그 안의 `hasNewName()` 판단은 그대로라, 복원 뒤 누르면 RENAME과 새 이름이 간다(REQ-67-01).
- **자동 복원과의 관계**(SRS 1.1 주석 확정): 창 내용 뷰는 `onStart`에서 붙고(`:256-262`), 창 생성 시 입력란에 원래 이름을 다시 넣는다(`:122`). 따라서 지금 코드에서 입력란 값은 재생성 뒤 살아남지 않으며(이슈 #67 재현과 일치), 이 설계는 Android 뷰 계층 자동 복원에 기대지 않고 C1·C3로 명시적으로 복원한다. 만약 LLD 조사에서 자동 복원이 뒤늦게 입력란에 글자를 넣는 경로가 있다고 밝혀지면, 넣는 값이 C1과 같은 글자라 결과는 같다. 다만 그 경로가 `onCreateDialog` 안에서 리스너를 부르면 위 `requireDialog()` 문제가 생기므로 LLD에서 확인한다(6장 R1).
- **스레드·수명주기**: 모두 메인 스레드의 Fragment 수명주기 안이다. 작업 스레드는 창 결과(`Args.listener`)만 기다리며 바뀌지 않는다.

## 4. 외부 인터페이스
- 없음. `Args`(Parcelable, 작업→창)와 `ListenerArgs`(창→작업)는 그대로다. `State`는 이 Fragment의 저장 Bundle 안에서만 쓰는 `private` 클래스라 앱 버전 사이 호환 문제가 없다(재생성은 같은 앱 프로세스 이미지에서 일어난다).

## 5. 설계 결정
| 결정 | 대안 | 고른 이유 |
|---|---|---|
| E1 `State`에 필드를 더해 명시적으로 저장·복원 | 입력란을 창 뷰 계층 자동 복원에 맡김(뷰를 `onCreateDialog`에서 붙이도록 구조 변경) | 뷰를 붙이는 시점(`:253-263`)은 원 작자가 정한 창 구성 방식이라 건드리면 범위가 커진다. 명시적 저장이 작고 테스트 가능하다 |
| E2 `newName`은 새 이름이 있을 때만 저장(`null` 아니면 새 이름) | 입력란 글자를 늘 저장 | REQ-67-02(새 이름 없으면 지금과 같음)를 저장 단계에서 바로 보장한다. 비운 입력란·원래 이름은 지금처럼 원래 이름으로 돌아간다 |
| E3 펼침 상태는 새 이름이 있을 때만 반영 | 늘 반영 | 요구사항 정리 v1 요구사항 2의 문자 그대로 해석(SRS REQ-67-02, SRS 검수 강조점 1에서 확인됨) |
| E4 복원 규칙을 순수 함수(C2)로 분리 | Fragment 안에 직접 씀 | 이 저장소의 단위 테스트는 JUnit만 쓰고 Robolectric이 없다(`app/build.gradle:222`). 순수 함수면 JVM 테스트로 표의 세 줄을 검증할 수 있다 |
| E5 긍정 버튼 글자는 빌더 초기 글자로 | `onStart`에서 버튼을 찾아 바꿈 | 창 생성 한 곳에서 끝나고 `requireDialog()` 시점 문제가 없다 |

## 6. 위험과 미결 사항
- R1: 자동 복원 경로가 입력란 글자를 다시 쓰면서 리스너가 이른 시점에 불릴 가능성. LLD에서 AndroidX `DialogFragment`의 창 상태 복원 순서를 확인하고, AVD 재생성 확인(SRS V1 2와 4의 (a) Activity 파괴·(b) 프로세스 정리)으로 크래시가 없음을 본다. 둘은 복원 경로가 다르다((a)는 같은 프로세스, (b)는 Bundle만 남음) — 이 설계는 Bundle의 `State`만 쓰므로 두 경로 모두 같은 결과를 기대한다.
- R2: 회전 전 화면 키보드가 떠 있었어도 복원 뒤에는 뜨지 않는다(SRS A2, 의도된 비보존).

## 7. 검증
| 항목 | 방법 | 추적 REQ |
|---|---|---|
| T1 | JVM 단위 테스트(`app/src/test/java/me/zhanghai/android/files/filejob/`): C2에 3장 표의 각 줄 + 방어 경우(`newName`이 빈 글자·원래 이름)를 넣고 결과 확인 | REQ-67-01, REQ-67-02 |
| T2 | 빌드: `./gradlew assembleDebug` 와 `testDebugUnitTest` 통과 | 전체 |
| T3 | 코드 검사: C4 범위(`Args`·`getTitle`·`getMessage`·`replaceWarningText` 조건·리스너·버튼 처리) diff 없음 | REQ-67-03, REQ-67-04 |
| T4 | AVD 재생성 확인: SRS 2.1 V1 1~5(회전, "활동 유지 안 함"에 의한 Activity 파괴, `am kill` 프로세스 정리, en·ko). 실제 기기는 쓰지 않는다 | REQ-67-01~04 |

## 8. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1 | 최초 작성 | SRS v1.1 검수 통과 | PM |
| 2026-11-19 | v1.1 | C4에 `:108-120` 펼침 리스너 추가, R1·T4에서 Activity 파괴와 프로세스 정리 구분 | HLD 검수 통과 경미 2건 | reviewer |
