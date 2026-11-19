# LLD — 교체 충돌 창의 기존 파일 손실 경고 (이슈 #54)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중(v1) |
| 근거 | SRS v1.3, HLD v1.3 (설계 브랜치 `design/54-replace-warning` 2f09bb5) |
| 기준 코드 | `origin/develop` 0941fa7. 충돌 창·레이아웃·문자열 파일은 SRS·HLD 기준 커밋 7cf16b7과 같아 줄 번호가 그대로다. 백엔드 줄 번호는 #47 머지 뒤인 0941fa7 기준이다. 경로는 `app/src/main/` 기준이다 |

## 1. 변경 파일
| 파일 | 신규/수정 | 요지 | HLD |
|---|---|---|---|
| `res/layout/file_job_conflict_dialog_view.xml` | 수정 | 최상위 `LinearLayout`의 첫 자식으로 경고 `TextView` `replaceWarningText` 추가(2-1) | C1 |
| `java/me/zhanghai/android/files/filejob/FileJobConflictDialogFragment.kt` | 수정 | `onCreateDialog`에 표시 조건 한 줄 추가(2-2) | C2 |
| `res/values/strings.xml` | 수정 | `file_job_replace_warning` 추가(2-3) | C3 |
| `res/values-ko/strings.xml` | 수정 | `file_job_replace_warning` 추가(2-3), `file_job_replace_message_format` 문구 고침(2-4) | C3, C4 |
| `app/src/test/java/me/zhanghai/android/files/filejob/FileJobReplaceWarningResourcesTest.kt` | 신규 | 문자열·레이아웃 리소스 JVM 검사(3-1) | — |

5개 파일. `FileJobs.kt`, 버튼·`onDialogButtonClick`, `getTitle`·`getMessage`, 백엔드(`provider/`), 다른 `values-*` 파일, 테마·색 파일(#27 영역 `themes*.xml`·`colors*.xml`)은 건드리지 않는다(REQ-54-03·05·06, SRS 3장).

## 2. 함수·리소스 명세

### 2-1. 레이아웃 `file_job_conflict_dialog_view.xml` — `TextView` `replaceWarningText`
- **추적**: HLD C1, E1·E2·E5 / REQ-54-01, 02, SRS 3장(접근성·테마·대비), D5(O3)
- **책임**: 경고 문구를 창 메시지 바로 아래, 기존·새 파일 줄 위에 메시지와 같은 본문 글자로 보인다.
- **위치**: 최상위 `LinearLayout`(8-13행)의 첫 자식, 지금 15행의 대상 줄 `LinearLayout` 앞. `onStart`가 이 레이아웃 전체를 창 스크롤 안 `LinearLayout`의 메시지 뒤에 붙이므로(`FileJobConflictDialogFragment.kt:251-261`) 경고는 메시지 바로 다음에 온다.
- **속성**(이 순서로 쓴다):
  ```xml
  <TextView
      android:id="@+id/replaceWarningText"
      style="?materialAlertDialogBodyTextStyle"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:paddingStart="?dialogPreferredPadding"
      android:paddingEnd="?dialogPreferredPadding"
      android:paddingTop="8dp"
      android:text="@string/file_job_replace_warning"
      android:visibility="gone" />
  ```
- **글자 모양(HLD v1.3 C1·E2, 설계 피드백 F1로 반영됨)**: `?textAppearanceListItem`이 아니라 창 메시지와 **같은 스타일 속성** `?materialAlertDialogBodyTextStyle`을 쓴다. 근거(Material 1.14.0, `app/build.gradle:149`):
  - 창 메시지 `TextView`는 두 테마 계열 모두 `style="?attr/materialAlertDialogBodyTextStyle"`이다(라이브러리 `res/layout/mtrl_alert_dialog.xml`·`m3_alert_dialog.xml`의 `@android:id/message`). 근거 원본: Gradle 캐시의 `com.google.android.material/material/1.14.0/.../material-1.14.0.aar`(풀린 사본 `~/.gradle/caches/<버전>/transforms/<해시>/transformed/material-1.14.0/res/`)의 `layout/mtrl_alert_dialog.xml`·`layout/m3_alert_dialog.xml`과 `values/values.xml`의 스타일 `MaterialAlertDialog.MaterialComponents.Body.Text`·`MaterialAlertDialog.Material3.Body.Text`, 매크로 `m3_comp_dialog_supporting_text_type/color`, 색 `color/material_on_surface_emphasis_medium.xml`.
  - 기본 테마(Material Design 3 설정 꺼짐, `res/values/donottranslate_prefs.xml:40`, 테마 부모 `Theme.MaterialComponents.DayNight`, `res/values/themes.xml:10`)에서 이 속성은 `MaterialAlertDialog.MaterialComponents.Body.Text` = `?textAppearanceBody2`(14sp) + `material_on_surface_emphasis_medium`(colorOnSurface 60%)이다. Material 3 테마에서는 `MaterialAlertDialog.Material3.Body.Text` = `?textAppearanceBodyMedium`(14sp) + `?colorOnSurfaceVariant`이다.
  - 반면 `?textAppearanceListItem`은 기본 테마에서 AppCompat Subhead(16sp, 주 글자색), Material 3에서 TitleMedium(16sp, `res/values/styles_material3.xml:35-38`)이라 메시지보다 크고 진하다. 경고가 메시지보다 두드러져 O3("강조 없이 일반 본문 글자")·D5와 어긋난다.
  - 같은 스타일이므로 색은 메시지와 같은 테마 속성에서 오고, 새 색·`textColor` 지정이 없다(HLD E2의 "새 색 없음", SRS 5장 `Visual_Contrast` "기존 창 메시지와 같은 색 속성"). `?android:textColorSecondary`를 쓰지 않으므로 #27의 대비 결함과 무관하다.
  - 스타일 속성은 창의 테마 문맥에서 풀린다: `binding`은 `MaterialAlertDialogBuilder`의 `context`(창 테마 오버레이가 적용된 문맥)로 inflate된다(`FileJobConflictDialogFragment.kt:77,81`).
- **여백(HLD C1이 LLD에 넘긴 항목)**:
  - 좌우: `paddingStart/End="?dialogPreferredPadding"` — 메시지(`paddingLeft/Right="?attr/dialogPreferredPadding"`)·아래 파일 줄(18-19행)과 글자 시작선이 맞는다.
  - 위: `paddingTop="8dp"`. 메시지에는 아래 여백이 없고(라이브러리 레이아웃의 메시지 `TextView`에 세로 padding 없음, 버튼이 있어 `textSpacerNoButtons`는 숨김) 경고가 붙으면 한 문단처럼 이어진다. 8dp는 이 레이아웃이 이미 쓰는 세로 간격(178-179·236-237행 `paddingTop/Bottom="8dp"`)과 같아 새 치수를 만들지 않는다.
  - 아래: 0. 대상 줄은 높이 72dp(`two_line_list_item_height`, `res/values/dimens.xml:35`)에 내용이 가운데 정렬되어(21행) 위쪽에 이미 빈 공간이 있다. 지금 메시지와 대상 줄 사이 간격이 이 빈 공간뿐이므로, 아래 여백 0이면 경고와 대상 줄 사이가 지금의 메시지-대상 줄 간격과 같다.
  - `gone`이면 padding도 자리를 차지하지 않아 합치기 창 배치는 지금과 같다(REQ-54-02).
  - 확인: 3-2 M1 캡처에서 메시지-경고 간격이 한 줄 간격보다 조금 넓게(문단 구분) 보이고, 경고-대상 줄 간격이 현행 빌드의 메시지-대상 줄 간격과 같은지 본다. 어긋나면 위 여백만 4dp 단위로 조정하고 PR 강조점에 적는다(레이아웃 구조·스타일은 바꾸지 않는다).
- **접근성**: 일반 `TextView`라 TalkBack이 창 내용 순서(제목→메시지→경고→파일 줄)로 읽는다. 아이콘이 없어 `contentDescription` 대상이 없다(SRS 5장 `Content_Description`). `importantForAccessibility` 등 추가 속성 없음.
- **기존 코드와의 관계**: 바인딩 클래스 `FileJobConflictDialogViewBinding`에 `replaceWarningText: TextView`가 자동 생성된다(viewBinding). 다른 뷰의 속성은 바꾸지 않는다. 15행 대상 줄에는 `layout_marginTop`이 없으므로 그대로 둔다.

### 2-2. `me.zhanghai.android.files.filejob.FileJobConflictDialogFragment.onCreateDialog(savedInstanceState: Bundle?): Dialog` — 한 줄 추가
- **추적**: HLD C2, E4 / REQ-54-01, 02, 03, 04, 06, D1(O1)·D2(O2)
- **책임**: 교체 충돌이면 경고를 보이고 합치기 충돌이면 숨긴다.
- **입력·전제**: UI 스레드. `args.sourceFile`·`args.targetFile`은 null이 아니다(현행 `Args`). `isMerge`는 75행에서 이미 계산된다(정의 310-314, `FileJobs.kt:1380-1386`과 같은 식).
- **출력·사후조건**: `binding.replaceWarningText.isVisible == !isMerge`. 다른 뷰·버튼·`State`는 현행과 같다.
- **오류**: 새 예외 없음.
- **알고리즘**: 81행 `binding = FileJobConflictDialogViewBinding.inflate(...)` **바로 다음 줄**에 다음 한 줄을 넣는다.
  ```kotlin
  binding.replaceWarningText.isVisible = !isMerge
  ```
  - `isVisible`은 이미 import된 `androidx.core.view.isVisible`(113행에서 사용)이다. 새 import 없음.
  - 이름 입력 처리(`doAfterTextChanged`, 121-130)는 건드리지 않는다 → 긍정 버튼이 "이름 바꾸기"로 바뀌어도 경고가 그대로다(D2, REQ-54-04).
  - 회전 등 재생성 때 `onCreateDialog`가 같은 `Args`로 다시 돌아 같은 결과가 난다. `State`(354-357)·`onSaveInstanceState`(64-68)에 저장할 것이 없다(REQ-54-04).
- **기존 코드와의 관계**: `getTitle`·`getMessage`(283-308)는 바꾸지 않는다 → 백그라운드 알림(`FileJobs.kt:612-635`, `app/BackgroundActivityStarter.kt:39-73`) 문구 불변(REQ-54-06). `setPositiveButton/NegativeButton/NeutralButton`(138-140)·`onDialogButtonClick`(211-240) 불변(REQ-54-03). `isMerge`의 private 선언도 바꾸지 않는다(#55 조각 B가 이 함수를 고친다 — SRS 4장).

### 2-3. 문자열 `file_job_replace_warning` (en, ko)
- **추적**: HLD C3, 4.2, E3 / REQ-54-01, 05
- **위치**: 각 파일의 `file_job_replace_source_name` 바로 다음 줄(영어 237행 뒤, 한국어 206행 뒤).
- **문구(확정 — HLD 4.2 제안 그대로)**:
  ```xml
  <!-- res/values/strings.xml -->
  <string name="file_job_replace_warning">Replacing deletes the existing file first. If the operation is interrupted, the existing file may be lost.</string>
  <!-- res/values-ko/strings.xml -->
  <string name="file_job_replace_warning">교체하면 기존 파일을 먼저 지웁니다. 작업이 도중에 중단되면 기존 파일을 잃을 수 있습니다.</string>
  ```
  - 서식 인자 없음, 작은따옴표·`@`·`?`로 시작하는 문자 없음(이스케이프 불필요).
  - 한국어는 버튼 이름 "교체"(`res/values-ko/strings.xml:35`)와 같은 낱말(REQ-54-05).
  - `translatable` 속성을 달지 않는다(다른 로캘은 #21에서 번역, 그때까지 영어로 대체).
- **폴더를 가리키는 심볼릭 링크가 대상일 때(HLD 4.2 미확인 항목의 결론)**: 폴더 판정이 링크를 따르지 않으므로(`FileJobConflictDialogFragment.kt:311-312`, `FileJobs.kt:1380-1381`) 이 경우는 교체 충돌이고 경고가 보인다. 교체 시 백엔드는 **링크만 지우고 링크가 가리키는 폴더와 그 내용은 지우지 않는다**(코드 읽기로 확인, 실행 확인 안 함):

  | 백엔드 | 교체 시 대상 지우기 | 링크에 대한 동작 | 판정 |
  |---|---|---|---|
  | 로컬·루트 | `LinuxCopyMove.kt:57-66`(파일 원본)·`136-145`(폴더 원본) → `Syscall.remove` → JNI `remove(3)`(`jni/syscall.c:1451-1461`) | bionic `remove`는 먼저 `unlink`를 쓰고 `EISDIR`일 때만 `rmdir`. `unlink`는 마지막 경로 요소의 링크를 따르지 않으므로 링크 자체만 지운다 | 링크만(코드·libc 명세) |
  | 공급자 간 | `ForeignCopyMove.kt:44`·`88`·`102` → `target.deleteIfExists()` → 대상 공급자의 `delete`(로컬은 `LocalLinuxFileSystemProvider.kt:183-192`의 `Syscall.remove`) | 대상 백엔드의 `delete`를 따른다 | 대상 백엔드와 같음 |
  | SFTP | `SftpCopyMove.kt:79`·`144` → `Client.remove`(`sftp/client/Client.kt:86-94`) | `lstat`으로 종류를 보고 링크(DIRECTORY 아님)는 `unlink` | 링크만 |
  | SMB | `SmbCopyMove.kt:77`·`99` → `Client.delete`(`smb/client/Client.kt:266-290`) | `FILE_OPEN_REPARSE_POINT`로 열어 `FILE_DELETE_ON_CLOSE` — 재분석 지점(링크) 자체를 지운다. 재분석 지점은 `SYMBOLIC_LINK`로 분류(`SmbFileAttributes.kt:44-45`) | 링크만 |
  | FTP | `ftp/client/Client.kt:169-188` — 목록상 디렉터리가 아니면 `deleteFile`(DELE) | 링크에 대한 DELE 처리는 서버 구현에 달림 | **미확인**(서버 의존) |
  | WebDAV | `webdav/client/Client.kt:102-110` — `DavResource.delete` | 앱의 "링크"는 리다이렉트 응답(`WebDavFileSystemProvider.kt:310-321`). 그 자원에 대한 DELETE 처리는 서버 구현에 달림 | **미확인**(서버 의존) |
  | 문서 제공자·압축 | 링크 없음 / 대상 불가 | — | 해당 없음 |

  따라서 경고 문구 "기존 파일을 먼저 지웁니다"는 링크 대상에서도 참이다(지워지는 "기존 파일"은 링크). 문구를 바꾸지 않는다. FTP·WebDAV 미확인은 경고의 정확성에는 영향이 없다(지우는 범위가 링크보다 넓어져도 "잃을 수 있다"는 참). 이 표는 정보이며 이 작업에서 백엔드를 바꾸지 않는다.

### 2-4. 문자열 `file_job_replace_message_format` (ko만) — 문구 고침(#58)
- **추적**: HLD C4, 4.2 / REQ-54-07
- **위치**: `res/values-ko/strings.xml:204`. 키·인자 수(`%1$s` 하나) 불변.
- **문구(확정 — HLD 4.2 제안 그대로)**:
  ```xml
  <string name="file_job_replace_message_format">“%1$s” 폴더에 같은 이름의 파일이 이미 있습니다.\n파일을 교체하면 대상 파일의 내용을 덮어씁니다.</string>
  ```
  - `%1$s`는 대상의 부모 폴더 이름(`FileJobConflictDialogFragment.kt:307`). 따옴표는 영어와 같은 “ ”(U+201C/U+201D).
  - 둘째 줄은 현행과 글자 하나 다르지 않게 그대로 둔다.
- **영향**: 이 문자열은 창 메시지이면서 백그라운드 알림 본문이다(`getMessage`). 한국어 알림 본문도 같이 고쳐진다 — REQ-54-06은 "경고를 알림에 넣지 않음"이고 REQ-54-07의 오역 고침은 의도된 변경이다.

## 3. 테스트 설계

### 3-1. JVM 단위 테스트 `FileJobReplaceWarningResourcesTest` (신규)
이 창은 계측 테스트 기반이 없고 단위 테스트는 JUnit 4만 쓴다(`app/build.gradle:222`, Robolectric 없음). 그래서 표시 조건 자체는 코드 검사·수동 확인으로 보고, 리소스 계약만 JVM에서 검사한다. 리소스 파일을 `File(...).readText()`로 읽고 정규식으로 찾는다(Gradle 단위 테스트의 작업 폴더는 `app/`, 경로는 `src/main/res/...`). XML 파서를 쓰지 않는다(android.jar 스텁과 엉키지 않게, 의존성 없음).

| 테스트 | 대상 | 경우 | 기대 결과 |
|---|---|---|---|
| U-W01 `warning_definedInEnglishAndKorean` | 2-3 | 정상 | `values/strings.xml`·`values-ko/strings.xml`에 `file_job_replace_warning`이 각각 정확히 1개, 값이 2-3 문구와 같음 |
| U-W02 `warning_hasNoFormatArgs` | 2-3 | 경계 | 두 값에 `%`가 없음(서식 인자 없음 — `getString` 인자 없이 써도 안전) |
| U-W03 `warning_koreanUsesReplaceWord` | 2-3 | 정상 | 한국어 값이 `values-ko`의 `replace` 문자열 값("교체")을 포함(REQ-54-05) |
| U-W04 `warning_notInOtherLocales` | 2-3 | 경계 | `src/main/res/values-*/strings.xml` 중 `values`·`values-ko` 밖의 어느 파일에도 `name="file_job_replace_warning"`이 없음(REQ-54-05) |
| U-W05 `koreanReplaceMessage_namesFolder` | 2-4 | 정상 | 한국어 `file_job_replace_message_format` 값이 2-4 문구와 같고, `%1$s`가 정확히 1번, 영어 값도 `%1$s` 1번(인자 수 일치, REQ-54-07) |
| U-W06 `layout_warningIsFirstAndHidden` | 2-1 | 정상 | `file_job_conflict_dialog_view.xml`에서 `@+id/replaceWarningText`가 `@+id/targetIconImage`보다 앞에 있고, 그 `TextView` 요소 안에 `android:visibility="gone"`·`style="?materialAlertDialogBodyTextStyle"`·`@string/file_job_replace_warning`이 있음 |

- 회귀 확인: U-W01·U-W05·U-W06은 구현 전(develop)에서 실패하고 구현 뒤 통과해야 한다. 구현 PR에 두 실행 결과를 붙인다.
- 실행: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "me.zhanghai.android.files.filejob.FileJobReplaceWarningResourcesTest" --console=plain` 뒤 전체 `testDebugUnitTest`와 `assembleDebug`.

### 3-2. 코드 검사(검수 항목)
| ID | 확인 | 기대 |
|---|---|---|
| C-01 | `FileJobConflictDialogFragment.kt` diff | 81행 뒤 `binding.replaceWarningText.isVisible = !isMerge` 한 줄만 추가. `getTitle`·`getMessage`·`isMerge`·버튼·`onDialogButtonClick` 불변(REQ-54-03·06) |
| C-02 | `git diff --stat develop` | 1장의 5개 파일만. `FileJobs.kt`·`provider/`·다른 `values-*`·테마/색 파일 없음 |
| C-03 | 레이아웃 diff | 2-1 속성 외 다른 뷰 변경 없음, `textColor`·새 색·아이콘 없음 |

### 3-3. 수동 확인 — 로컬 AVD 캡처
- **기기**: 로컬 에뮬레이터만(`Medium_Phone_API_36` 등, `emulator -list-avds`). 모든 `adb` 명령에 `-s emulator-<포트>`를 붙이고, `adb devices`에 실기기가 보여도 그쪽에 설치·실행하지 않는다. 오너 기기 설치 금지.
- **준비**: `assembleDebug` APK를 AVD에 설치. 시험 파일은 `adb -s emulator-5554 shell`로 `/sdcard/Download/w54/`에 만든다:
  `src/a.txt`, `dst/a.txt`(파일↔파일), `src/d1/`(안에 `x.txt`)와 `dst/d1`(파일, 폴더→파일), `src/d2/`(안에 `x.txt`)와 `dst/d2/`(안에 `x.txt`, 합치기).
- **언어**: 앱 설정의 언어(`settings/LocalePreference.kt`)로 English/한국어를 바꾼다. **테마**: 기본(Material Design 3 꺼짐, 밝은 테마)에서 찍고, 추가 2장만 어두운 테마·Material Design 3로 찍는다.
- **캡처 저장**: `$MAIN_REPO/_workspace/wo/54-replace-warning/screens/<ID>-<en|ko>.png`(`adb -s … exec-out screencap -p`). PR 코멘트에 파일 이름과 관찰 결과를 적는다.

| ID | 경우 | 조작 | 기대(en·ko 각각) | REQ |
|---|---|---|---|---|
| M1 | 파일↔파일(복사) | `src/a.txt`를 `dst/`로 복사 | 메시지 아래 경고가 보임. 경고 글자 크기·색이 메시지와 같음. 여백이 2-1 확인 기준에 맞음. ko는 메시지 첫 줄이 "“dst” 폴더에 같은 이름의 파일이 이미 있습니다." | 01, 05, 07, D5 |
| M2 | 폴더→파일(이동) | `src/d1`을 `dst/`로 이동 | 경고가 보임, 버튼 "Replace"/"교체" | 01 |
| M3 | 합치기 | `src/d2`를 `dst/`로 복사 → 합치기 창 캡처 → "합치기" → 안의 `x.txt` 충돌 창 캡처 | 합치기 창에는 경고 없음, 배치가 현행과 같음. 안의 파일 충돌 창에는 경고 있음 | 02 |
| M4 | 이름 바꾸기 | M1 창에서 "Select a new name…"을 펼쳐 `b.txt` 입력 → 캡처 → 화면 회전 → 캡처 | 긍정 버튼이 "Rename"/"이름 바꾸기"로 바뀌어도 경고 유지, 회전 뒤에도 유지 | 04, D2 |
| M5 | 어두운 테마(en만) | M1과 같음 | 경고가 메시지와 같은 색으로 읽힘 | SRS 5장 Theme_Support |
| M6 | Material Design 3(en만) | M1과 같음 | 경고가 메시지와 같은 글자 모양 | E2, D5 |

- 필수 캡처: M1~M4 × en·ko = 8장(M3·M4는 2장씩이라 실제 12장) + M5·M6 2장.
- M1~M4 중 하나에서 "교체"를 실제로 눌러 한 번에 진행되고 별도 확인 창이 없음을 본다(REQ-54-03).
- 압축 풀기 충돌은 같은 창·같은 조건(`!isMerge`)이라 캡처하지 않는다(REQ-54-01은 코드 검사로 충족).
- 대비(HLD v1.3 6장 미확인 항목): 경고는 메시지와 같은 스타일이므로 메시지와 같은 대비다. M1(밝은)·M5(어두운)·M6(MD3) 캡처에서 ① 경고 글자 픽셀 색이 메시지 글자와 같은지 ② 글자 획 중심 픽셀 색과 창 배경 색으로 WCAG 대비비를 계산해 4.5:1 이상인지 본다(venv 파이썬 스크립트로 계산, 값을 PR 코멘트에 적는다). 미달이면 메시지 자체의 문제이므로 이 작업에서 색을 바꾸지 않고 별도 이슈(`분류대기`)로 올린다(새 색 금지 유지).

## 4. 설계 피드백
- **F1 — 경고 글자 모양(HLD C1·E2 "`?textAppearanceListItem`, 색 지정 없음" → LLD `style="?materialAlertDialogBodyTextStyle"`)**: HLD 6장이 "메시지와 어울리지 않으면 기존 테마 속성 안에서 글자 모양만 바꾼다"고 열어 둔 항목이다. 라이브러리 리소스로 확인한 결과 `?textAppearanceListItem`은 두 테마 모두 메시지(14sp, 중간 강조색)보다 크고(16sp) 진해(주 글자색) O3 "강조 없음"과 어긋난다(2-1 근거). 메시지와 같은 스타일 속성을 쓰면 글자 모양과 색이 모두 메시지와 같아지고, 새 색 정의나 `textColor` 지정은 여전히 없다. 다만 스타일이 색을 포함하므로 HLD E2의 "글자색을 따로 지정하지 않는다(기본 본문 색)" 표현과 글자 그대로는 다르다 — 설계팀에 문의함. **결과(2026-11-19)**: 설계팀이 받아들여 HLD를 v1.3으로 고침(C1·E2를 `?materialAlertDialogBodyTextStyle`·별도 색 지정 없음으로, `?textAppearanceListItem`은 기각안으로; e899599, 검수 통과 2f09bb5). 대비 4.5:1 확인은 LLD 3-3으로 넘겨받음. LLD 변경 없음.
- **F2 — 기준 커밋**: SRS·HLD는 7cf16b7 기준이고 그 뒤 #47(0941fa7)이 `LinuxCopyMove.kt`·`SftpCopyMove.kt`·`SmbCopyMove.kt`를 바꿨다. 충돌 창·레이아웃·문자열 줄 번호는 변하지 않았고, SRS 1.1의 백엔드 줄 번호(예: `LinuxCopyMove.kt:56-65`, 136-144)는 한두 줄 어긋난다. 요구사항에는 영향 없음 — 정보로만 남긴다.
- **F3 — HLD 4.2 미확인(링크 대상 교체)**: 2-3 표로 로컬·루트·SFTP·SMB는 링크만 지움(코드 읽기), FTP·WebDAV는 서버 의존 미확인으로 정리했다. 문구 변경 불필요.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1.1 | 근거를 HLD v1.3으로, F1 결과 기록, 라이브러리 근거 경로 추가, 3-3에 대비비 계산(4.5:1, 미달 시 별도 이슈) 추가 | 설계 문의 F1 결과(HLD v1.3) | 개발팀 |
| 2026-11-19 | v1 | 최초 작성 | SRS v1.3·HLD v1.2 검수 통과 | PM |
