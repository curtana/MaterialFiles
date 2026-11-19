# LLD — Material 3 밝은 테마 보조 글자 대비 (이슈 #27)

개발팀이 SRS·HLD를 바탕으로 쓴다. 이 변경은 **자원 전용**이라(HLD 1장) "함수" 단위 대신 **자원 항목 단위**(C1·C2·C3)로 명세하고, 측정(C4)과 회귀 테스트는 함수 단위로 명세한다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과 |
| 근거 | SRS v1.3, HLD v1.2 (오너 결정 후보 B, 2026-11-19) |

## 1. 변경 파일

| 파일 | 신규/수정 | 요지 | HLD |
|---|---|---|---|
| `app/src/main/res/color/m3_default_color_secondary_text.xml` | 수정(`:13` 한 줄) | 기본 항목 `m3_sys_color_light_outline` → `m3_ref_palette_neutral_variant40` | C1 |
| `app/src/main/res/color-v31/m3_dynamic_default_color_secondary_text.xml` | 수정(`:13` 한 줄) | 기본 항목 `m3_sys_color_dynamic_light_outline` → `m3_ref_palette_dynamic_neutral_variant40` | C2 |
| `app/src/main/res/color/navigation_item_subtitle_text_color_material3.xml` | 수정(`:14` 한 줄) | 기본 항목 `?colorOutline` → `?android:textColorSecondary` | C3 |
| `app/src/test/java/me/zhanghai/android/files/theme/SecondaryTextColorResourceTest.kt` | 신규 | 자원 XML을 읽어 C1~C3 기본 항목 값과 불변 항목(비활성·선택·어두운 테마)을 확인하는 JVM 단위 테스트 | C1~C3, REQ-27-05·06 |
| `$MAIN_REPO/_workspace/wo/27-contrast/measure/contrast.py` | 신규(**저장소 밖**) | 화면 캡처 PNG에서 글자·배경 색과 WCAG 대비를 계산 | C4 |
| 구현 PR 본문 | — | 변경 전·후 측정 표 | C4, REQ-27-08 |

**바꾸지 않는 파일**(diff 0줄, REQ-27-05): `res/color/m3_dark_default_color_secondary_text.xml`, `res/color-v31/m3_dynamic_dark_default_color_secondary_text.xml`, `res/values/themes.xml`, `res/values/themes_material3.xml`, `res/values-night/themes_material3.xml`, `res/values/styles_material3.xml`, `res/color/navigation_item_subtitle_text_color.xml`, 모든 레이아웃·Kotlin 코드(`res/layout/*`, `java/**`). `CHANGELOG.md`는 릴리스 때 설계팀이 쓴다(이 작업 범위 밖).

### 1-1. 확인한 사실 (구현 전제)
- 라이브러리 자원 존재(material 1.14.0, Gradle 캐시 `transformed/material-1.14.0/res`):
  - `values/values.xml:516` `<color name="m3_ref_palette_neutral_variant40">#ff605d66</color>`
  - `values-v31/values-v31.xml:21` `<color name="m3_ref_palette_dynamic_neutral_variant40">@android:color/system_neutral2_600</color>`
  - `values-v34/`에는 `neutral_variant40`의 재정의가 없다(검색 결과 0건). 즉 API 34+에서도 `system_neutral2_600` 그대로이고, 현재 값(`m3_sys_color_dynamic_light_outline` → API 34+ `system_outline_light`, `values-v34.xml:54`)과 달리 시스템 "대비" 설정을 따르지 않는다(HLD 4-1 후보 성질 표와 일치).
- 동적 자원은 `values-v31`에만 있으므로 C2(`color-v31/`)에서만 참조한다. C1(`color/`, API 23+)은 정적 자원만 참조한다 — 바꿔 쓰면 API 23~30에서 aapt 링크 오류가 난다.
- C1·C2·C3 모두 이미 `tools:ignore="PrivateResource"`가 있다(`:11`). 새 속성 추가 없음.
- 측정 환경: `~/.android/avd/`에 `Medium_Phone_API_36`(과 `Medium_Phone_API_36_b`)만 있다. **API 30 이하 AVD는 없다** → 정적 팔레트는 실측 불가, "미측정"으로 기록(HLD 4-2 5단계). 시스템 이미지 내려받기·새 AVD 만들기는 하지 않는다.
- 프로젝트 venv에 Pillow가 없다(`import PIL` 실패). 측정 스크립트는 **표준 라이브러리만**(`zlib`, `struct`) 쓴다 — 새 라이브러리 승인 요청 없음.
- 단위 테스트 기반은 JUnit 4만 있다(`app/build.gradle:222`, Robolectric 없음). 자원 해석은 JVM 테스트로 재현할 수 없으므로 테스트는 **자원 XML의 정적 내용**만 확인하고, 실제 화면 색은 C4 측정으로 확인한다.

## 2. 자원 항목 명세

### 2-1. C1 `res/color/m3_default_color_secondary_text.xml` — 기본 항목
- **추적**: HLD C1, D1 / REQ-27-01, 02, 03, 04, 06, 07, 10
- **책임**: API 23~30(동적 색 없음)에서 Material 3 밝은 테마의 `?android:textColorSecondary` 활성 기본색을 neutral variant 톤 40(`#605D66`)으로 정한다.
- **입력·전제**: 라이브러리 `Base.V14.Theme.Material3.Light`가 `android:textColorSecondary`에 `@color/m3_default_color_secondary_text`를 넣고(material `values.xml:3227`, SRS 1-1), 앱의 같은 이름 자원이 이를 덮는다.
- **출력·사후조건**: `:13`이 정확히 다음과 같다(다른 줄은 바이트 단위로 그대로).
  ```xml
      <item android:color="@color/m3_ref_palette_neutral_variant40" />
  ```
  `:12` 비활성 항목(`state_enabled="false"`, `alpha=@dimen/material_emphasis_disabled`, `m3_sys_color_light_on_surface`)은 그대로(REQ-27-06, HLD D3).
- **오류**: 자원 이름 오타·라이브러리 개명 시 `mergeDebugResources`/aapt 링크 실패(HLD R2) — 빌드로 즉시 드러난다.
- **알고리즘**: `:13`의 `@color/m3_sys_color_light_outline`을 `@color/m3_ref_palette_neutral_variant40`으로 바꾼다. 기존 표기(`" />"` 앞 공백 포함)를 유지한다.
- **기존 코드와의 관계**: 업스트림 덮어쓰기 파일(`4cd7710`, HLD R3). `ColorStateList`의 기본색은 상태 지정이 없는 항목(`:13`)이다 — `BreadcrumbLayout.kt:31-38`, `ThemedSpeedDialView.kt:159`, `StandardDirectoryListPreferenceFragment.kt:49`의 `getColorByAttr`도 이 기본색을 읽으므로 코드 변경이 필요 없다(HLD 2장).

### 2-2. C2 `res/color-v31/m3_dynamic_default_color_secondary_text.xml` — 기본 항목
- **추적**: HLD C2, D1 / REQ-27-01, 02, 03, 04, 06, 07, 10
- **책임**: API 31+(동적 색)에서 같은 기본색을 동적 neutral variant 톤 40(`system_neutral2_600`)으로 정한다.
- **입력·전제**: 라이브러리 `Theme.Material3.DynamicColors.Light`(material `values-v31.xml:334`)가 `@color/m3_dynamic_default_color_secondary_text`를 쓴다. 동적 색을 쓸 수 없는 기기에서는 라이브러리가 정적 테마로 떨어져 C1이 쓰인다(SRS 3-2·REQ-27-04).
- **출력·사후조건**: `:13`이 정확히 다음과 같다(이 파일의 기존 표기는 `"/>"` 앞 공백 없음 — 그대로 따른다).
  ```xml
      <item android:color="@color/m3_ref_palette_dynamic_neutral_variant40"/>
  ```
  `:12` 비활성 항목은 그대로.
- **오류**: C1과 같다.
- **알고리즘**: `:13`의 `@color/m3_sys_color_dynamic_light_outline`을 `@color/m3_ref_palette_dynamic_neutral_variant40`으로 바꾼다.
- **기존 코드와의 관계**: 1-1의 API 34+ 동작 차이(시스템 대비 설정 미추종)는 오너가 고른 후보 B의 알려진 성질이다(HLD 4-1). 동작 변경으로 PR "중요사항"에 적는다.

### 2-3. C3 `res/color/navigation_item_subtitle_text_color_material3.xml` — 기본 항목
- **추적**: HLD C3, D2, 3-1 / REQ-27-09, 05, 06
- **책임**: Material 3 서랍 항목 부제목의 활성·비선택 색을 `?android:textColorSecondary`의 기본색(= C1·C2, 어두운 테마에서는 어두운 자원)으로 따르게 한다.
- **입력·전제**: `styles_material3.xml:49` `itemSubtitleTextColor` → 이 파일. 어댑터가 테마가 적용된 `TypedArray`에서 `a2.getColorStateList(R.styleable.NavigationViewExtra_itemSubtitleTextColor)`(`NavigationListAdapter.kt:114`)로 읽고 `binding.subtitleText.setTextColor(...)`(`:198`)로 건다. 상태 목록 항목의 `?attr`이 다른 `ColorStateList`로 풀리면 그 기본색이 쓰인다(같은 방식의 선례: `res/color/navigation_item_subtitle_text_color.xml:11`, 비 Material 3에서 이미 동작 중).
- **출력·사후조건**: `:14`가 정확히 다음과 같다.
  ```xml
      <item android:color="?android:textColorSecondary" />
  ```
  `:12` 비활성(`alpha=@dimen/material_emphasis_disabled`, `?colorOutline`, `state_enabled="false"`)과 `:13` 선택(`?colorOnSecondaryContainer`, `state_checked="true"`)은 그대로이고, **항목 순서도 그대로**다(상태 목록은 위에서부터 첫 일치 항목을 쓰므로 순서가 바뀌면 선택·비활성 상태 색이 바뀐다 — REQ-27-06).
- **오류**: 속성을 풀 수 없으면(테마 없이 읽힌 경우) 기본색이 0(투명)이 될 수 있다 → C4 측정 T8에서 글자가 보이지 않는 것으로 드러난다. 현재 읽는 경로는 테마가 있는 `TypedArray`이므로 해당 없음으로 본다.
- **알고리즘**: `:14`의 `?colorOutline`을 `?android:textColorSecondary`로 바꾼다. `:12`의 `?colorOutline`은 바꾸지 않는다.
- **기존 코드와의 관계**: 어두운 테마에서 `?colorOutline`과 `?android:textColorSecondary` 기본색이 같은 자원(`m3_sys_color_dark_outline` / `m3_sys_color_dynamic_dark_outline`)이라 어두운 테마 서랍 부제목은 바뀌지 않는다(HLD 3-1) — C4 측정 M-D1로 확인.

## 3. 측정(C4) 명세 — `_workspace/wo/27-contrast/measure/contrast.py`

저장소에 넣지 않는다(HLD C4). venv 파이썬 `G:\workspace\MeterialFiles\venv\Scripts\python.exe`로 실행, 표준 라이브러리만 쓴다.

### 3-1. `read_png(path: str) -> tuple[int, int, list[tuple[int, int, int]]]`
- **추적**: HLD C4, 4-2 3단계 / REQ-27-08
- **책임**: `adb exec-out screencap -p` PNG를 RGB 화소 배열로 읽는다.
- **입력·전제**: 8비트, 색 형식 2(RGB) 또는 6(RGBA), 비인터레이스. 그 밖이면 `ValueError`.
- **출력**: `(width, height, pixels)`, `pixels[y*width + x] = (r, g, b)`(알파는 버린다 — 화면 캡처는 불투명).
- **알고리즘**: 시그니처 확인 → 청크 순회(`IHDR`, 모든 `IDAT` 이어붙이기, `IEND`) → `zlib.decompress` → 행마다 필터 0~4(None/Sub/Up/Average/Paeth) 복원.

### 3-2. `relative_luminance(rgb) -> float`, `contrast(a, b) -> float`
- **책임**: WCAG 2.x 상대 휘도와 대비비. 채널 `c/255`가 0.04045 이하면 `/12.92`, 아니면 `((c+0.055)/1.055)^2.4`; `L = 0.2126R + 0.7152G + 0.0722B`; 대비 `(max+0.05)/(min+0.05)`.
- **검산**(스크립트 자체 시험, `--selftest`): `contrast((0x75,0x77,0x80),(0xFA,0xF8,0xFF))`가 4.24±0.01(이슈 #27 실측), `contrast((0x60,0x5D,0x66),(0xFE,0xF7,0xFF))`가 6.15±0.02(HLD 4-1 B 정적·앱바 평소), 흑/백 21.0.

### 3-3. `measure(png, box, min_count=3) -> (text_rgb, bg_rgb, ratio)`
- **추적**: SRS 3-1 방법 / REQ-27-01~05, 08~10
- **책임**: 상자 `box=(x0, y0, x1, y1)`(끝 미포함) 안에서 배경색과 글자(또는 아이콘) 획 색을 뽑아 대비를 낸다.
- **입력·전제**: 상자는 대상 글자 한 줄(또는 아이콘 하나)과 그 주변 여백만 포함해야 한다 — 다른 글자·구분선이 들어가면 결과가 틀린다. 상자 좌표는 3-4의 `uiautomator dump` 경계값에서 얻는다.
- **알고리즘**:
  1. 배경 `bg` = 상자 안 화소 최빈값(SRS 3-1 "주변 화소의 최빈값", 덧칠 배경도 실제 화소 그대로).
  2. 상자 안에서 `bg`와 다른 각 색의 `contrast(색, bg)`를 구하고 최대값 `cmax`를 찾는다.
  3. 글자 색 `text` = `contrast(색, bg) ≥ 0.9·cmax`이고 `min_count`회 이상 나온 색들 중 최빈값(획 안쪽 화소. 안티에일리어싱 테두리는 대비가 낮아 제외된다). 후보가 없으면 `min_count` 조건을 빼고 같은 규칙.
  4. `ratio = contrast(text, bg)`, 소수 둘째 자리.
- **오류**: 상자가 이미지 밖이면 `ValueError`. 상자 안이 한 색뿐이면 `ValueError("no foreground")`.

### 3-4. 명령줄
- `contrast.py measure <png> <x0> <y0> <x1> <y1> <label>` → `label,#RRGGBB(text),#RRGGBB(bg),ratio` 한 줄(CSV)을 표준출력에.
- `contrast.py bounds <uiautomator.xml> <text-or-resource-id>` → 일치하는 노드의 `bounds="[x0,y0][x1,y1]"`를 `x0 y0 x1 y1`로(여러 개면 줄마다). 상자는 이 경계 그대로 쓴다(TextView 경계는 글자 줄과 안쪽 여백만 포함). 아이콘(T9)도 해당 `ImageView`/메뉴 항목 아이콘 노드 경계를 쓴다.
- `contrast.py --selftest` → 3-2 검산.

### 3-5. 측정 절차 (HLD 4-2 구체화 — **로컬 AVD만**)

**환경 고정**
1. 실제 기기 금지: 모든 `adb` 명령은 `adb -s emulator-<포트>`로 대상을 명시한다. 시작 전 `adb devices -l`에 나온 일련번호가 `emulator-`로 시작하는지 확인하고, 실제 기기가 함께 연결돼 있어도 그쪽으로는 아무것도 하지 않는다. 오너 기기 설치는 하지 않는다(SRS 3-1, HLD 4-2 1단계).
2. AVD: `Medium_Phone_API_36`. 다른 작업(#11·#54·#55)이 같은 AVD를 쓰고 있으면(`adb devices`에 이미 떠 있고 다른 작업의 앱 상태가 걸려 있으면) 데이터를 지우지 말고 PM에게 순서를 묻는다. `-wipe-data`·AVD 새로 만들기·시스템 이미지 내려받기 금지.
3. 기본 배경화면 그대로, 시스템 대비 "표준"(`adb shell settings get secure contrast_level`이 `0.0` 또는 null — 다르면 측정하지 말고 기록 후 PM 보고), 글꼴 크기·디스플레이 크기 기본값. 밝음/어두움은 `adb shell cmd uimode night no|yes`로 바꾼다. 앱 테마 설정은 "시스템 따름"(기본값)으로 두고, 설정 → Material Design 3 켬/끔은 앱 UI로 바꾼다.
4. 캡처 이름: `$MAIN_REPO/_workspace/wo/27-contrast/measure/<before|after>/<측정ID>.png`, 같은 이름의 `<측정ID>.xml`(`adb shell uiautomator dump /sdcard/ui.xml` → `adb pull`)을 함께 남긴다.

**빌드 두 개**
5. 변경 전: develop(구현 브랜치 분기 원점 커밋)의 `assembleDebug` APK. 변경 후: feature 브랜치 APK. 같은 applicationId(`me.zhanghai.android.files`)·같은 디버그 서명이라 `adb -s … install -r <apk>`로 데이터(설정 포함)를 유지한 채 바꿔 깐다. 변경 전 측정을 모두 마친 뒤 변경 후를 깐다.
6. 테스트용 내용: 내부 저장소에 `27-contrast` 폴더를 만들고 파일 20개 이상(스크롤 가능), 그 안에 같은 이름 파일 하나를 복사해 둔다(충돌 대화상자용). `adb shell` 로 `/sdcard/27-contrast/`에 만든다. 앱에 저장소 권한이 없으면 앱 UI에서 허용한다.

**측정 목록** (밝은 테마 + Material 3 켬, 변경 전·후 각각. "상자"는 3-4 `bounds`로 얻는다)

| 측정ID | 화면·상태 | 대상(상자) | 기준 | REQ |
|---|---|---|---|---|
| M-T1a | `27-contrast` 폴더 목록 맨 위 | 툴바 부제목(파일 수 등) | 4.5 | 01, 04 |
| M-T2a | 같은 화면 | 경로 표시줄의 현재가 아닌 항목 하나 | 4.5 | 02 |
| M-T3a | 같은 화면 | 파일 항목 보조 글자 하나(`file_item_list.xml:94-95`) | 4.5 | 03 |
| M-T1b·M-T2b | 목록을 스크롤해 앱바가 떠오른 상태 | T1·T2 | 4.5 | 01, 02 |
| M-T3b | 항목 하나 길게 눌러 선택(checked) | 선택된 항목 보조 글자 | 4.5 | 03 |
| M-T6 | 파일 목록 위에서 빠른 동작(FAB) 펼침 | 빠른 동작 라벨 하나(뒤에 목록이 비치는 위치) | 4.5 | 03 |
| M-T5 | 같은 파일을 같은 폴더에 복사·붙여넣기 → 충돌 대화상자 (불가하면 속성 → 권한 대화상자) | 대화상자 보조 글자 하나(`file_job_conflict_dialog_view.xml:86` 또는 `permission_item.xml:33`) | 4.5 | 03 |
| M-T7 | 설정 화면 | 설정 항목 요약 하나(`preference_material.xml:58`) | 4.5 | 03 |
| M-T8 | 서랍 열기(모달, 휴대폰) | 저장소 항목 부제목(여유 공간) | 4.5 | 09 |
| M-T9 | 툴바 더보기 메뉴 펼침 | 아이콘이 있는 메뉴 항목의 아이콘(없으면 "해당 없음" 기록) | 3.0 | 10 |

불변 확인 (변경 전·후 각각, REQ-27-05)

| 측정ID | 조건 | 대상 | 판정 |
|---|---|---|---|
| M-D1 | Material 3 켬 + 어두운 테마(`uimode night yes`) | T1(맨 위), T8 | 전·후 글자 화소값 **동일**(16진 일치) |
| M-L0 | Material 3 끔 + 밝은 테마 | T1(맨 위), T8 | 전·후 동일 |
| M-D5 | Material 3 켬 + 어두운 테마 | 충돌(또는 권한) 대화상자 보조 글자 | 기록만(HLD R4, 아래 규칙) |

**판정·중단 규칙**
7. **서랍 규칙(HLD R1)**: 변경 후 M-T8이 4.5 미만이면 **구현을 멈춘다** — 그 상태로 PR 검수를 요청하지 않고, 측정값(글자·배경 16진, 대비, 캡처 경로)을 `designer`에게 문의하고 PM에게 "설계 변경 요청"으로 올린다(후보 변경은 오너 결정 사항, REQ-27-07). 같은 규칙을 다른 M-T 대상이 4.5(M-T9는 3.0) 미만일 때도 적용한다.
8. **어두운 대화상자 규칙(HLD R4)**: M-D5가 4.5 미만이면 이 PR에서 고치지 않고(범위 밖, REQ-27-05), 중복 검색(`gh issue list --repo curtana/MaterialFiles --state all --search "dark dialog contrast"`) 뒤 `분류대기` 라벨로 이슈를 등록하고(제목 `[developer] Material 3 dark theme dialog secondary text below 4.5:1`, 본문에 측정값·캡처 경로·`m3_dark_default_color_secondary_text.xml:13`·HLD R4) 즉시 PM에게 번호를 보고한다. 4.5 이상이면 PR 표에 값만 남긴다.
9. M-D1·M-L0의 전·후 값이 다르면 REQ-27-05 위반이다 — 원인(C3 해석 등)을 확인하기 전에는 진행하지 않는다.
10. 고정 서랍(sw600dp 가로, REQ-27-09·HLD R5)과 정적 팔레트(API 23~30, REQ-27-04·HLD 4-2 5단계)는 "미측정"으로 적고 근거를 한 줄씩 남긴다: 고정 서랍은 같은 C3·같은 `?colorSurface` 배경, 정적은 HLD 4-1 정적 16진 계산값(B: 6.15 / 5.55 / 5.27, 서랍은 측정된 배경으로 다시 계산한 값). 다른 배경화면은 톤 계산 근거 한 줄(HLD 4-2 5단계 하위 항목).
11. 서랍 배경 덧칠 여부(HLD 4-1 미확인 사항)는 M-T8의 배경 16진 값으로 기록한다(`#FEF7FF` 계열 톤 98이면 덧칠 꺼짐).

**PR 표** (REQ-27-08, HLD 4-2 6단계): 열 = 측정ID, 대상, 상태, 글자 색(전), 글자 색(후), 배경 색, 대비(전), 대비(후), 기준, 판정. 그 아래 M-D1·M-L0 동일성, M-D5 값과 처리, 미측정 항목과 근거, 측정 환경(AVD·API·대비 설정·두 빌드의 커밋)을 적는다. 캡처 파일은 `_workspace/`에 두고 PR에는 경로만 적는다.

## 4. 테스트 설계

### 4-1. `me.zhanghai.android.files.theme.SecondaryTextColorResourceTest` (JVM, JUnit 4)
- **추적**: HLD C1~C3, D3 / REQ-27-05, 06 (오너가 고른 값 자체는 REQ-27-07로 HLD 4-1과 이슈 #27 코멘트를 대조해 확인한다. 이 테스트는 그 값이 자원에 들어갔는지만 고정한다)
- **책임**: 자원 XML의 항목 값과 순서를 고정해, 색 결정이 바뀌거나 불변 항목이 실수로 바뀌면 빌드 테스트에서 드러나게 한다.
- **입력·전제**: Gradle 단위 테스트의 작업 디렉터리는 모듈 디렉터리(`app/`)다. 파일은 `File("src/main/res/<폴더>/<이름>.xml")`로 읽는다(없으면 `assertTrue(file.exists())`로 실패시켜 경로 문제를 숨기지 않는다).
- **도우미** `private fun items(path: String): List<Map<String, String>>`: `DocumentBuilderFactory`(`isNamespaceAware = true`)로 파싱해 `<selector>`의 `<item>` 자식마다 android 이름공간(`http://schemas.android.com/apk/res/android`) 속성을 `로컬이름 → 값` 맵으로, 문서 순서대로 돌려준다.

| 테스트 | 대상 | 경우 | 기대 결과 | 변경 전 |
|---|---|---|---|---|
| U-27-01 `staticSecondaryText_defaultIsNeutralVariant40` | C1 | 정상 | 항목 2개. `[1]` = `{color=@color/m3_ref_palette_neutral_variant40}`(상태 속성 없음) | 실패 |
| U-27-02 `staticSecondaryText_disabledUnchanged` | C1 `:12` | 불변 | `[0]` = `{state_enabled=false, alpha=@dimen/material_emphasis_disabled, color=@color/m3_sys_color_light_on_surface}` | 통과 |
| U-27-03 `dynamicSecondaryText_defaultIsDynamicNeutralVariant40` | C2 | 정상 | `color-v31/…` 항목 2개. `[1]` = `{color=@color/m3_ref_palette_dynamic_neutral_variant40}`, `[0]`은 `m3_sys_color_dynamic_light_on_surface` 비활성 항목 그대로 | 실패 |
| U-27-04 `drawerSubtitle_defaultFollowsSecondaryText` | C3 | 정상·순서 | 항목 3개, 순서대로 `{alpha=@dimen/material_emphasis_disabled, color=?colorOutline, state_enabled=false}`, `{color=?colorOnSecondaryContainer, state_checked=true}`, `{color=?android:textColorSecondary}` | 실패 |
| U-27-05 `darkSecondaryText_unchanged` | 불변 파일 | 회귀 방지 | `color/m3_dark_default_color_secondary_text.xml` `[1].color = @color/m3_sys_color_dark_outline`, `color-v31/m3_dynamic_dark_default_color_secondary_text.xml` `[1].color = @color/m3_sys_color_dynamic_dark_outline` | 통과 |

- 변경 전 실패 확인: 테스트를 먼저 추가해 develop 상태 자원에서 U-27-01·03·04가 실패하고 02·05가 통과함을 실행으로 확인한 뒤 자원을 바꾸고, 다섯 개 모두 통과함을 확인한다(PROCESS_ROLES 11장 회귀 테스트 규칙).
- 실행: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'me.zhanghai.android.files.theme.SecondaryTextColorResourceTest' --console=plain 2>&1 | tail -n 30`, 전체: `./gradlew :app:testDebugUnitTest assembleDebug --console=plain 2>&1 | tail -n 30`.
- 한계: 이 테스트는 실제 해석된 색(테마 적용, 동적 색)을 확인하지 않는다 — 그것은 3장 측정이 맡는다.

### 4-2. 측정 스크립트 자체 시험
- `contrast.py --selftest`(3-2 검산 세 가지)가 통과해야 측정값을 쓴다. 측정 시작 전에 한 번 실행해 출력을 PR 측정 절에 붙인다.

### 4-3. diff 확인 (REQ-27-05, 06)
- `git diff --stat <develop 분기점>..HEAD -- app/src/main/res app/src/main/java`가 C1·C2·C3 세 파일, 각 `1 insertion(+), 1 deletion(-)`만 보여야 한다. 출력을 PR에 붙인다.

## 5. 설계 피드백
- 없음(SRS·HLD와 어긋나는 점을 찾지 못했다). 참고로 확인한 사항: HLD 4-1의 "API 34+ 시스템 대비 설정 미추종"은 라이브러리 `values-v34`에 `neutral_variant40` 재정의가 없는 것으로 확인했다(1-1). 정적 팔레트 실측은 API 30 이하 AVD가 없어 HLD 4-2 5단계의 "미측정" 경로를 탄다.

## 6. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1.0 | 최초 작성 | SRS v1.3, HLD v1.2 | PM |
| 2026-11-19 | v1.1 | 상태를 검수 통과로, 4-1 추적에서 REQ-27-07을 분리 | LLD 검수 1회차 조건부 통과(경미 [기계] 2건). [판단] 1건(C1·C2를 함께 쓰는 `textColorTertiary`·어두운 테마 `*Inverse` 부수 영향)은 PM 판단 대기 | 검수팀 |
