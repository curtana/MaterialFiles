# LLD — 충돌 창 회전 시 새 이름 보존 (이슈 #67)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1, 경미 1건 v1.1 반영), 구현됨(PR #82, v1.7.5-beta.2) |
| 근거 | SRS v1.2, HLD v1.1 |
| 기준 코드 | `origin/develop` 454206d. 줄 번호는 이 커밋 기준, 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준(테스트는 `app/src/test/java/me/zhanghai/android/files/`) |

## 1. 변경 파일
| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `filejob/FileJobConflictNameRestore.kt` | 신규 | HLD C2: 복원할 화면 값(`FileJobConflictNameRestore`)과 그것을 계산하는 순수 함수 `conflictNameRestoreOf` |
| `filejob/FileJobConflictDialogFragment.kt` | 수정 | HLD C1: `State`에 두 필드, `onSaveInstanceState`. HLD C3: `onCreateDialog`의 `:121-122`, `:136-138`, `:140`만 |
| `filejob/FileJobConflictNameRestoreTest.kt`(test) | 신규 | HLD T1: 3장 표 각 줄 + 방어 경우 |

그 밖의 파일(`FileJobs.kt`, `FileJobConflictDialogActivity.kt`, 레이아웃·문자열)은 바꾸지 않는다(SRS A4, HLD C4).

## 2. 함수 명세

### `filejob.FileJobConflictNameRestore` (data class, 신규)
```kotlin
internal data class FileJobConflictNameRestore(
    val nameText: String,
    val isNameLayoutVisible: Boolean,
    val isRename: Boolean,
    val isAllChecked: Boolean,
    val isAllEnabled: Boolean
)
```
- **추적**: HLD C2 / REQ-67-01, REQ-67-02
- **책임**: 창을 만들 때 뷰에 넣을 값 묶음. `nameText` = `nameEdit` 초기 글자, `isNameLayoutVisible` = `nameLayout` 펼침(화살표 90°/0°도 이 값을 따름), `isRename` = 긍정 버튼 초기 글자가 `R.string.rename`인지, `isAllChecked`/`isAllEnabled` = `allCheck`의 체크·사용 가능.
- **기존 코드와의 관계**: 같은 패키지의 `FileJobEndNotice`(`filejob/FileJobEndNotice.kt:25`, public 제네릭 data class)처럼 Android 타입 없는 data class로 두되, 이 Fragment에서만 쓰므로 `internal`로 해 JVM 테스트에서 `assertEquals`로 비교한다. 문자열 리소스 ID는 담지 않는다(`isMerge`에 따른 교체/합치기 선택은 Fragment `:77`이 그대로 한다).

### `filejob.conflictNameRestoreOf(targetFileName: String, savedNewName: String?, savedIsNameLayoutVisible: Boolean, savedIsAllChecked: Boolean): FileJobConflictNameRestore` (신규, `internal`)
- **추적**: HLD C2(3장 복원 규칙 표) / REQ-67-01, REQ-67-02
- **책임**: 저장 상태와 대상 원래 이름으로 복원할 화면 값을 정한다.
- **입력·전제**: `targetFileName`은 `args.targetFile.path.fileName.toString()`(`:121`과 같은 값). 처음 열릴 때(저장 상태 없음)는 호출자가 `savedNewName = null, savedIsNameLayoutVisible = false, savedIsAllChecked = false`를 넘긴다 — 그러면 표의 "없음" 줄(원래 이름, 접힘, 교체/합치기, 해제·사용 가능)이 "`newName == null`" 줄과 같은 규칙으로 나온다. 메인 스레드, 부작용 없음.
- **출력·사후조건**:
  - `savedNewName`이 "새 이름"(비어 있지 않고 `targetFileName`과 다름)이면 `FileJobConflictNameRestore(savedNewName, savedIsNameLayoutVisible, isRename = true, isAllChecked = false, isAllEnabled = false)`.
  - 아니면(`null`, `""`, `targetFileName`과 같음 — 뒤 둘은 HLD 3장의 방어) `FileJobConflictNameRestore(targetFileName, isNameLayoutVisible = false, isRename = false, isAllChecked = savedIsAllChecked, isAllEnabled = true)`. 펼침 저장값은 무시한다(HLD E3).
- **오류**: 없음(던지지 않음).
- **알고리즘**:
  ```
  if (savedNewName != null && savedNewName.isNotEmpty() && savedNewName != targetFileName)
      return (savedNewName, savedIsNameLayoutVisible, true, false, false)
  return (targetFileName, false, false, savedIsAllChecked, true)
  ```
- **기존 코드와의 관계**: "새 이름" 판정은 `hasNewName()`(`:244-251`)과 같은 조건이다. `hasNewName()`은 뷰를 읽으므로 그대로 두고(HLD C4) 이 함수는 같은 조건을 글자 인자로 다시 쓴다. 새 이름일 때의 체크 해제·사용 불가는 글자 변경 리스너(`:125-128`)와, 긍정 버튼 글자는 `:131`과 같은 결과다.

### `FileJobConflictDialogFragment.State` (수정, `:351-354`)
```kotlin
@Parcelize
private class State(
    val isAllChecked: Boolean,
    val newName: String?,
    val isNameLayoutVisible: Boolean
) : ParcelableState
```
- **추적**: HLD C1 / REQ-67-01, REQ-67-02
- **책임**: 재생성 사이에 `allCheck` 체크, 새 이름(있을 때만), 입력란 펼침을 나른다.
- **기존 코드와의 관계**: `putState`/`getState`(`util/ParcelableState.kt:14-20`) 그대로. `private` 클래스이고 같은 앱 프로세스 이미지 안에서만 풀리므로 필드 추가에 따른 호환 문제가 없다(HLD 4장).

### `FileJobConflictDialogFragment.onSaveInstanceState(outState: Bundle)` (수정, `:64-68`)
- **추적**: HLD C1 / REQ-67-01, REQ-67-02
- **입력·전제**: `binding`이 초기화돼 있다 — 지금도 `:67`이 `binding`을 읽으므로 전제는 바뀌지 않는다.
- **출력·사후조건**: `outState`에 `State(binding.allCheck.isChecked, if (hasNewName()) binding.nameEdit.text.toString() else null, binding.nameLayout.isVisible)`를 넣는다(HLD 3장 "저장", E2).
- **오류**: 없음.
- **알고리즘**: `super.onSaveInstanceState(outState)` 뒤 위 한 줄로 `:67`을 바꾼다.

### `FileJobConflictDialogFragment.onCreateDialog(savedInstanceState: Bundle?): Dialog` (수정, `:70-148`)
- **추적**: HLD C3 / REQ-67-01, REQ-67-02. C4 범위(`:73-120`, `:123-135`)는 바꾸지 않는다 / REQ-67-03, REQ-67-04
- **입력·전제**: 메인 스레드, Fragment 수명주기 안(아래 "R1 확인"으로 창 상태 복원보다 먼저 불림).
- **출력·사후조건**: 반환되는 창의 뷰가 `conflictNameRestoreOf`의 결과와 같다. 글자 변경 리스너(`:123-132`)는 이 함수 안에서 한 번도 불리지 않는다.
- **오류**: 지금과 같음. `savedInstanceState != null`인데 `State`가 없으면 `getState`가 예외를 던지는 것도 지금(`:137`)과 같다.
- **알고리즘**(바뀌는 곳만):
  1. `:77` 다음(빌더 호출 전)에
     ```kotlin
     val targetFileName = targetFile.path.fileName.toString()
     val state = savedInstanceState?.getState<State>()
     val restore = conflictNameRestoreOf(
         targetFileName, state?.newName, state?.isNameLayoutVisible ?: false,
         state?.isAllChecked ?: false
     )
     ```
     를 둔다. `:121`의 `val targetFileName` 선언은 여기로 옮겨 지운다(끝 아이콘 리스너 `:133-135`는 같은 이름을 그대로 쓴다).
  2. `:122`를 다음으로 바꾼다 — 리스너 등록(`:123`) **앞**이다:
     ```kotlin
     binding.nameEdit.setTextWithSelection(restore.nameText)
     binding.nameLayout.isVisible = restore.isNameLayoutVisible
     binding.showNameArrowImage.rotation = if (restore.isNameLayoutVisible) 90f else 0f
     ```
     화살표는 애니메이션 없이 최종 값만 넣는다(`:110-111`과 같은 각도, HLD C3). 포커스·키보드는 주지 않는다(SRS A2).
  3. `:136-138`을 다음으로 바꾼다:
     ```kotlin
     binding.allCheck.isChecked = restore.isAllChecked
     binding.allCheck.isEnabled = restore.isAllEnabled
     ```
  4. `:140`을 `.setPositiveButton(if (restore.isRename) R.string.rename else positiveButtonRes, ::onDialogButtonClick)`로 바꾼다(HLD E5). `positiveButtonRes`(`:77`)와 리스너 안의 `:131`은 그대로다.
- **기존 코드와의 관계**: 이 밖의 줄은 그대로다. 버튼 처리(`onDialogButtonClick`, `:213-242`)는 뷰의 `hasNewName()`으로 판단하므로, 복원된 새 이름이 입력란에 있으면 RENAME과 그 이름이 간다(REQ-67-01). 새 이름이 없으면 지금과 같은 MERGE_OR_REPLACE/SKIP이다(REQ-67-02).

### R1 확인 — 창 상태 복원 순서와 리스너가 일찍 불릴 가능성 (HLD 6장 R1 해소)
확인한 것: 앱이 쓰는 `androidx.fragment:fragment:1.9.1`(`app/build.gradle:139`의 `fragment-ktx:1.9.1`, Gradle 캐시의 `fragment-1.9.1.aar` `classes.jar`를 `javap -c -p`로 역어셈블)과 SDK 소스 `sources/android-35`의 `android/app/Dialog.java`, `com/android/internal/policy/PhoneWindow.java`.

1. `DialogFragment.onGetLayoutInflater` → `prepareDialog` → `onCreateDialog`(이 Fragment의 `:70`). 여기서 우리 코드가 입력란 글자를 넣고 리스너를 등록한다.
2. 그 뒤 `DialogFragment.performCreateView`(이 Fragment처럼 `mView == null`일 때)와 `onViewStateRestored`가 `savedInstanceState.getBundle("android:savedDialogState")`가 있으면 `Dialog.onRestoreInstanceState`를 부른다.
3. `Dialog.onRestoreInstanceState`(`Dialog.java:514-525`)는 `dispatchOnCreate`(AlertDialog 내용·버튼 생성) → `mWindow.restoreHierarchyState` → 저장 때 보이던 창이면 `show()` 순이다. `PhoneWindow.restoreHierarchyState`(`PhoneWindow.java:2274-2296`)는 `mContentParent` 아래의 뷰에만 상태를 돌려준다.
4. `binding.root`는 `onStart`(`:253-263`)에서야 창의 `scrollView` 안 `LinearLayout`에 붙는다. `DialogFragment.onStart`가 먼저 `Dialog.show()`를 부르고, 우리 `onStart`가 `super.onStart()` 뒤 붙인다. 즉 3의 계층 복원 시점에 `nameEdit`은 창 계층에 없다.

결론:
- 창 뷰 계층 자동 복원은 `nameEdit` 글자에 닿지 않는다(SRS 1.1 주석·HLD 3장 확정). 따라서 `doAfterTextChanged`(`:123`)가 자동 복원으로 불리는 경로는 없고, HLD가 걱정한 `onCreateDialog` 안 `requireDialog()` 실패도 없다. 리스너는 우리가 넣는 초기 글자보다 뒤에 등록되므로 2단계(알고리즘 2)도 리스너를 부르지 않는다.
- 저장 때 `nameEdit`에 포커스가 있었으면 3에서 `findViewById(nameEdit)`가 `null`이라 경고 로그(`PhoneWindow.java:2292-2294`)만 남고 포커스는 복원되지 않는다. 이는 SRS A2(포커스 비보존)와 같고 지금도 있는 동작이다.
- API 28 미만에서는 `onSaveInstanceState`가 `onStop`보다 먼저라 `DIALOG_SHOWING_TAG`가 참일 수 있어 3의 `show()`가 `onStart` 전에 불린다. `show()`는 글자를 바꾸지 않으므로 결론은 같다.
- Activity 파괴(SRS V1 4(a))와 프로세스 정리(4(b))는 모두 같은 `savedInstanceState` Bundle로 1→4를 거친다. 우리 복원은 `State`만 쓰므로 두 경로의 결과가 같다. 크래시 없음은 AVD 확인(3장 A1~A4)으로 본다.

## 3. 테스트 설계

### 3.1 JVM 단위 테스트 — `FileJobConflictNameRestoreTest`
`targetFileName = "a.txt"`. 기대값은 `FileJobConflictNameRestore(nameText, isNameLayoutVisible, isRename, isAllChecked, isAllEnabled)`.

| 테스트 | 대상 함수 | 경우(정상·경계·오류) | 기대 결과 |
|---|---|---|---|
| U-R01 | `conflictNameRestoreOf` | 처음 열림: `(null, false, false)` | `("a.txt", false, false, false, true)` |
| U-R02 | 〃 | 새 이름 없음, 체크 저장: `(null, false, true)` | `("a.txt", false, false, true, true)` |
| U-R03 | 〃 | 새 이름 없음, 펼침 저장(펼치기만 함): `(null, true, false)` | `("a.txt", false, false, false, true)` — 펼침 무시(E3) |
| U-R04 | 〃 | 새 이름, 펼침: `("b.txt", true, false)` | `("b.txt", true, true, false, false)` |
| U-R05 | 〃 | 새 이름, 접힘(A3): `("b.txt", false, false)` | `("b.txt", false, true, false, false)` |
| U-R06 | 〃 | 새 이름인데 체크 저장값 참(저장 시점 경합 방어): `("b.txt", true, true)` | `("b.txt", true, true, false, false)` |
| U-R07 | 〃 | 방어: 빈 글자 `("", true, true)` | `("a.txt", false, false, true, true)` |
| U-R08 | 〃 | 방어: 원래 이름 `("a.txt", true, false)` | `("a.txt", false, false, false, true)` |

**실패 확인(구현 전)**: 먼저 `conflictNameRestoreOf`를 지금 동작 그대로(`return FileJobConflictNameRestore(targetFileName, false, false, savedIsAllChecked, true)`)인 임시 몸체로 두고 테스트를 돌려 **U-R04·U-R05·U-R06이 실패**하고 나머지가 통과함을 확인·기록한다(지금 코드가 새 이름을 잃는다는 것의 재현). 그 뒤 2장 알고리즘으로 바꿔 8개 모두 통과를 확인한다. 명령:
`JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests 'me.zhanghai.android.files.filejob.FileJobConflictNameRestoreTest' --console=plain 2>&1 | tail -n 30`

### 3.2 빌드·코드 검사
| 항목 | 방법 | 기대 결과 |
|---|---|---|
| B1 | `./gradlew assembleDebug`와 `testDebugUnitTest`(전체) | 통과, 기존 테스트 깨짐 없음 |
| B2 | `git diff origin/develop -- filejob/FileJobConflictDialogFragment.kt` 검사 | 바뀐 곳은 `:64-68`, `:77` 뒤 선언, `:121-122`, `:136-138`, `:140`, `State`뿐. `Args`·`getTitle`·`getMessage`·`:83` 경고 조건·`:108-120`·`:123-135`·`:213-251` 변경 없음(HLD T3, REQ-67-03·04) |

### 3.3 AVD 확인 (SRS 2.1 V1, HLD T4) — 구현 단계에서 실시
로컬 AVD만 쓴다. `adb devices`로 `emulator-<포트>`를 확인하고 모든 `adb` 명령에 `-s emulator-<포트>`를 붙인다. 실제 기기(R54N102PRWV)는 쓰지 않는다. 다른 작업이 같은 에뮬레이터를 쓰고 있으면 PM에게 순서를 묻는다.

| 단계 | 절차 | 기대 결과 |
|---|---|---|
| A0 | `adb -s emulator-<포트> install -r app/build/outputs/apk/debug/<apk>`. 회전은 자동 회전을 끄고(`adb -s … shell settings put system accelerometer_rotation 0`) `settings put system user_rotation 1`(가로)/`0`(세로)으로 한다 | 설치됨 |
| A1 회전, 새 이름 | 교체 충돌(`a.txt` → `a.txt` 있는 폴더) 창에서 펼쳐 `b.txt` 입력 → 회전 | 입력란 펼침·화살표 90°·글자 `b.txt`, 긍정 버튼 "이름 바꾸기", "모든 파일에 적용" 해제·흐림, 교체 경고 보임. 긍정 → `b.txt` 생성, `a.txt` 그대로 |
| A1' | A1을 입력 후 접은 채 회전 | 접힘·화살표 0°, 버튼 "이름 바꾸기" |
| A2 회전, 새 이름 없음 | (i) 펼치지 않음+"모든 파일에 적용" 체크, (ii) 펼치기만, (iii) 비움 → 각각 회전 | 접힘, 글자 `a.txt`, 버튼 "교체", 체크는 (i)만 켜짐·사용 가능 |
| A3 합치기 | 같은 이름 폴더를 그 폴더가 있는 곳으로 복사 → A1·A2(i) 반복 | 새 이름이면 "이름 바꾸기", 아니면 "합치기", 교체 경고 없음 |
| A4 Activity 파괴 | 개발자 옵션 "활동 유지 안 함"(`adb -s … shell settings put global always_finish_activities 1`) → A1 상태에서 홈 → 최근 앱 → 확인 후 `0`으로 되돌림 | A1과 같음, 크래시 없음 |
| A5 프로세스 정리 | A1 상태에서 홈 → `adb -s … shell am kill me.zhanghai.android.files` → 최근 앱 | A1과 같음. 작업 서비스와 함께 정리돼 창이 다시 안 뜨면 그 사실을 기록하고 A4로 갈음(SRS V1 4(b)) |
| A6 언어 | ko(`adb -s … shell cmd locale set-app-locales me.zhanghai.android.files --locales ko-KR` 또는 시스템 언어)·en 각각 A1 | 경고·버튼 글자가 각 언어로 보이고 결과 같음 |
| A7 재생성 없음 | 회전 없이 펼치기·입력·끝 아이콘(원래 이름 되돌림)·각 버튼 | 지금과 같음(REQ-67-04) |

증거: 각 단계 스크린숏(`adb -s … exec-out screencap -p`)을 `$MAIN_REPO/_workspace/wo/67-conflict-rename/`에 두고 PR 코멘트에 결과 표를 적는다. `logcat`에서 `AndroidRuntime` 예외가 없음을 붙인다.

## 4. 설계 피드백
- 없음. HLD 6장 R1은 2장 "R1 확인"으로 해소했다(자동 복원은 입력란에 닿지 않고, 리스너는 `onCreateDialog` 안에서 불리지 않음). SRS·HLD 수정 요청 없음.
- 참고(범위 안, 변경 없음): `onSaveInstanceState`가 `binding` 초기화 전에 불릴 수 있는지는 지금 코드(`:67`)와 같은 전제라 이번에 바꾸지 않는다.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1 | 최초 작성(R1 확인 포함) | SRS v1.2·HLD v1.1 검수 통과 | PM |
| 2026-11-19 | v1.1 | `FileJobEndNotice` 인용에 public 제네릭임을 밝히고 `internal` 이유 추가, 상태 갱신 | LLD 검수 통과 경미 1건 | reviewer |
| 2026-11-20 | v1.1.1 | 머리표 상태에 구현됨(PR #82) 표시. 내용 변경 없음 | 릴리스 v1.7.5-beta.2 문서 동기화 | 설계팀 |
