# SRS — Material 3 밝은 테마 보조 글자 대비 (이슈 #27)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과 |
| 근거 | 요구사항 정리 v2 (이슈 #27, 2026-11-19 — v1에 #61 탐색 서랍 부제목 추가) |

## 1. 목적과 범위

Material 3(설정 "Material Design 3" 켬) + 밝은 테마에서 보조 글자 색(`?android:textColorSecondary`)이 배경과 4.5:1 대비를 내지 못한다(실측 `#757780` / `#FAF8FF` = 4.24:1, 이슈 #27 본문). 같은 톤 50 색(`?colorOutline`)을 직접 쓰는 탐색 서랍 항목 부제목도 같은 미달이 예상된다(#61, 요구사항 정리 v2로 흡수). 이 SRS는 이 글자들을 `Visual_Contrast` 기준(작은 글자 4.5:1 이상)에 맞추는 요구사항을 정한다.

- **범위 안**: Material 3 밝은 테마에서 `?android:textColorSecondary`로 그려지는 모든 글자와, 탐색 서랍 항목 부제목(2-1의 T1~T8). 툴바 부제목과 경로 표시줄의 현재가 아닌 항목이 우선 대상이다. 같은 자원을 거치는 그래픽 요소(T9)는 부수 영향으로 함께 확인한다.
- **범위 밖**(요구사항 정리 v2 "범위 밖"): 테마 체계 전반의 재설계, 위 대상이 아닌 다른 글자 역할의 색 변경, `?colorOutline` 속성 자체의 값 변경(스위치 등 다른 요소도 쓴다 — REQ-27-05), 이미 기준을 넘는 테마의 색.

### 1-1. 현재 값의 출처 (근거)

실측값 `#757780`은 Material Components 라이브러리 기본값(`colorOnSurfaceVariant`, 톤 30)이 아니라, **앱이 라이브러리 색 자원을 덮어써 `colorOutline`(톤 50)으로 바꾼 값**이다.

| 단계 | 근거 |
|---|---|
| Material 3 테마의 부모 | `app/src/main/res/values/themes_material3.xml:10` `Theme.Material3.DynamicColors.DayNight.NoActionBar` |
| 라이브러리가 `android:textColorSecondary`에 넣는 자원 | material 1.14.0 `res/values/values.xml:3227`(`Base.V14.Theme.Material3.Light`) → `@color/m3_default_color_secondary_text`; API 31+ `res/values-v31/values-v31.xml:334`(`Theme.Material3.DynamicColors.Light`) → `@color/m3_dynamic_default_color_secondary_text` |
| 라이브러리 원래 값 | `m3_default_color_secondary_text` → `m3_sys_color_light_on_surface_variant`(= neutral variant 톤 30, 정적 `#49454F`) |
| **앱의 덮어쓰기**(업스트림 작성자 Hai Zhang, 2022 저작권 표기, 마지막 변경 커밋 `4cd7710`) | `app/src/main/res/color/m3_default_color_secondary_text.xml:13` → `@color/m3_sys_color_light_outline`; `app/src/main/res/color-v31/m3_dynamic_default_color_secondary_text.xml:13` → `@color/m3_sys_color_dynamic_light_outline` |
| `outline`의 값 | 정적: neutral variant 톤 50 `#79747E`(material 1.14.0 `values.xml:517,684`). 동적(API 31~33): `system_neutral2_500`(`values-v31.xml:111,22`). 동적(API 34+): `system_outline_light`(`values-v34.xml:54`) |
| 탐색 서랍 부제목(Material 3) | `res/values/styles_material3.xml:49` `itemSubtitleTextColor` → `res/color/navigation_item_subtitle_text_color_material3.xml:14` `?colorOutline`(같은 톤 50). 적용: `java/.../navigation/NavigationListAdapter.kt:114,198`. 보조 글자 자원과 **별개 경로**다 |
| 어두운 테마(바꾸지 않음) | `app/src/main/res/color/m3_dark_default_color_secondary_text.xml:13`, `color-v31/m3_dynamic_dark_default_color_secondary_text.xml:13` → `outline`(톤 60) |

검산: Material 3 색은 HCT 톤(= CIE L\*)이 상대 휘도를 정하므로 대비는 색상(hue)과 무관하게 톤만으로 거의 정해진다. 톤 50 글자 / 톤 98 배경(`colorSurface`) = **4.27:1**로 실측 4.24:1과 맞고, 어두운 테마 톤 60 / 톤 6 = 5.85:1도 실측 5.85:1과 맞는다. 즉 배경화면(동적 색)이 바뀌어도 이 미달은 재현된다.

## 2. 기능 요구사항

### 2-1. 대상 목록 (Material 3 밝은 테마)

| # | 요소 | 근거(`파일:줄`) | 색 경로 | 배경(Material 3 밝은 테마) |
|---|---|---|---|---|
| T1 | 파일 목록 툴바 부제목 | `res/layout/file_list_fragment_app_bar_include.xml:36` → `res/values/styles.xml:18`(12dp) → AppCompat 부제목 글자 모양 | `?android:textColorSecondary` | 앱바: 평소 `colorSurface`(톤 98, `themes_material3.xml:43`), 스크롤로 떠오르면 `colorSurfaceContainer`(톤 94, material `values.xml:13525`) |
| T2 | 경로 표시줄의 현재가 아닌 항목 | `java/.../filelist/BreadcrumbLayout.kt:31-38` | 〃 | T1과 같은 앱바 |
| T3 | 파일 목록 항목 보조 글자 | `res/layout/file_item_list.xml:94-95` | 〃 | 목록 `colorSurface`. 선택(checked) 상태는 `colorPrimary` 12% 덧칠(`java/.../ui/CheckableItemBackground.kt:42-43`), 눌림 상태는 `?selectableItemBackground` 물결(`file_item_list.xml:14`) |
| T4 | 기타 목록 항목 보조 글자 | `res/layout/bookmark_directory_item.xml:49`, `storage_item.xml:50`, `color/storage_name.xml:10`, `lan_smb_server_item.xml:49`, `lan_smb_server_loading_item.xml:20`, `about_fragment.xml:244` | 〃 | 화면 또는 대화상자 |
| T5 | 대화상자 안 보조 글자 | `res/layout/permission_item.xml:33,40`, `principal_item.xml:48`(권한·소유자 대화상자), `file_job_conflict_dialog_view.xml:86,162`(충돌 대화상자) | 〃 | 대화상자 `colorSurfaceContainerHigh`(톤 92, material `values.xml:13651`) |
| T6 | 코드에서 읽는 보조 색 | `java/.../ui/ThemedSpeedDialView.kt:159`(빠른 동작 라벨, M3에서 라벨 배경 투명 `:160-162`), `java/.../settings/StandardDirectoryListPreferenceFragment.kt:49` | 〃 | 화면(라벨은 뒤에 비치는 화면) |
| T7 | 라이브러리 레이아웃이 쓰는 경우 | androidx.preference 1.2.1 `res/layout/preference_material.xml:58`(설정 항목 요약, 기본 Preference 배치 `values.xml:62`), `preference.xml:67`, `preference_category_material.xml:59`, `preference_dropdown.xml:74`, `expand_button.xml:58`, `preference_dialog_edittext.xml:41` | 〃 | 설정 화면(`colorSurface`) 또는 대화상자 |
| T8 | 탐색 서랍 항목 부제목(10sp, 저장소 여유 공간 등) | `res/values/styles_material3.xml:48-49` → `res/color/navigation_item_subtitle_text_color_material3.xml:14` | `?colorOutline` 직접 | 서랍 배경 `?colorSurface`(`res/layout/file_list_host_fragment_include.xml:43`, sw600dp-land `:16`)에 고도 덧칠(`java/.../ui/NavigationFrameLayout.kt:38-41` `initializeElevationOverlay`) — 실제 색은 측정으로 정한다 |
| T9 | (그래픽, 부수 영향) 앱바 팝업 메뉴 아이콘 틴트 | `res/values/styles.xml:67` `ThemeOverlay.MaterialFiles.ActionBarPopup`의 `colorControlNormal` | `?android:textColorSecondary` | 팝업 메뉴 배경 |

제외: `?textAppearanceListItemSecondary`만 쓰고 `textColor`를 주지 않는 곳(`res/layout/file_item_grid.xml:123`, `file_job_list_item.xml:41,49`, `lan_smb_server_add_item.xml:21`)은 Material 3에서 `textAppearanceBodyMedium`(material `values.xml:2746,2749`) → `TextAppearance.M3.Sys.Typescale.BodyMedium`(`:5757`, 부모 `TextAppearance.AppCompat.Body1`) → AppCompat `Base.TextAppearance.AppCompat.Body1`의 `?android:textColorPrimary`로 풀리므로 보조 색을 쓰지 않는다. 서랍 부제목의 선택(`state_checked`, `?colorOnSecondaryContainer`)·비활성 항목은 대상이 아니다(REQ-27-06). 비 Material 3 서랍 부제목(`res/color/navigation_item_subtitle_text_color.xml:10-11`)은 Material 3에서 쓰이지 않는다.

### 2-2. 요구사항

| ID | 요구사항 | 우선순위 | 검증 방법 |
|---|---|---|---|
| REQ-27-01 | Material 3 밝은 테마에서 툴바 부제목(T1)은 앱바의 평소 배경과 떠오른 배경 모두에 대해 4.5:1 이상 대비를 내야 한다. | 필수 | 3-1 측정 절차로 두 상태를 각각 측정 |
| REQ-27-02 | Material 3 밝은 테마에서 경로 표시줄의 현재가 아닌 항목(T2)은 REQ-27-01과 같은 두 배경에 대해 4.5:1 이상이어야 한다. 현재 항목(`textColorPrimary`)의 색은 바뀌지 않는다. | 필수 | 3-1 측정 절차 |
| REQ-27-03 | Material 3 밝은 테마에서 2-1의 T3~T7 글자도 각자 실제 배경에 대해 4.5:1 이상이어야 한다. T3은 평소·선택(checked) 상태 모두를 포함한다. | 필수 | 3-1 측정 절차로 T3(평소·선택), T5(권한 또는 충돌 대화상자 중 하나 이상), T6(빠른 동작 라벨), T7(설정 항목 요약 `preference_material.xml:58`) 측정 + 나머지는 같은 자원·같은 배경 종류임을 근거로 계산(HLD에서 정하는 방식) |
| REQ-27-04 | REQ-27-01~03, 09는 동적 색(API 31+, 배경화면에서 색을 뽑는 경우)과 정적 팔레트(API 23~30 또는 동적 색을 못 쓰는 경우) 모두에서 성립해야 한다. 배경화면에 따라 대비가 4.5:1 아래로 떨어지는 색 선택은 허용하지 않는다. | 필수 | 동적 색: 에뮬레이터(API 36) 실측 + 톤 기반 계산. 정적: 정적 팔레트 16진 값으로 계산, 가능하면 API 30 이하 에뮬레이터 실측 |
| REQ-27-05 | 이미 기준을 넘는 테마의 보조 글자 색은 바뀌지 않아야 한다: Material 3 어두운 테마, Material 3 검은 밤 모드, Material 3 몰입(`Immersive`, 어두운 테마 기반) 화면, Material 3 끔의 모든 테마(밝음·어두움·검은 밤, 모든 테마 색). 또 `?colorOutline` 속성 값 자체는 바꾸지 않는다(스위치 틴트 `res/color/switch_track_tint_light_material3.xml:11` 등 다른 사용처가 있다). | 필수 | 변경 전후 해당 색 자원·테마 파일이 같음(diff) + 변경 **전** 빌드와 **후** 빌드에서 Material 3 어두운 테마와 Material 3 끔 밝은 테마의 부제목 화소값을 각각 재어 같음을 확인(이슈 본문 값 `#8F909A`, `#666666`은 참고값) |
| REQ-27-06 | 보조 글자와 서랍 부제목의 비활성(`state_enabled=false`) 상태 색, 서랍 부제목의 선택(`state_checked`) 상태 색은 바뀌지 않아야 한다. | 필수 | 변경 전후 색 상태 목록의 해당 항목이 같음(diff) |
| REQ-27-07 | 바꿀 색은 설계팀이 제안한 후보(HLD) 중 오너가 고른 것이어야 하며, 고른 결과와 일자를 HLD 변경 이력과 이슈 #27에 남긴다. | 필수 | HLD 설계 결정 표·변경 이력과 이슈 코멘트 대조 |
| REQ-27-08 | 고친 뒤 같은 방법(화면 캡처 화소값, 또는 Accessibility Scanner)으로 REQ-27-01~05, 09, 10 대상을 다시 재고, 변경 전·후 값(글자 색, 배경 색, 대비)을 구현 PR에 표로 남긴다. | 필수 | 구현 PR 본문의 측정 표 확인 |
| REQ-27-09 | Material 3 밝은 테마에서 탐색 서랍 항목 부제목(T8)은 실제 서랍 배경(고도 덧칠이 적용된 화소값)에 대해 4.5:1 이상이어야 한다. 모달 서랍(휴대폰)과 고정 서랍(sw600dp 가로) 중 측정 가능한 배치를 모두 잰다. | 필수 | 3-1 측정 절차로 서랍을 열고 부제목 측정. 고정 서랍은 태블릿 에뮬레이터가 없으면 "미측정"으로 기록 |
| REQ-27-10 | (그래픽) T9 팝업 메뉴 아이콘은 변경 뒤에도 팝업 배경에 대해 3:1 이상이어야 한다. | 필수 | 3-1 측정 절차로 툴바 더보기 메뉴의 아이콘 측정 |

## 3. 비기능 요구사항

### 3-1. 측정 절차 (REQ-27-01~05, 08~10 공통)
- 환경: 에뮬레이터 `Medium_Phone_API_36`(#7 T-C09와 같은 환경). 오너 기기에는 설치하지 않는다(설치는 사전 확인 대상).
- 방법: `adb exec-out screencap -p`로 캡처 → 글자 색은 글자 획 안쪽 화소(테두리 안티에일리어싱 화소 제외)의 대표값, 배경 색은 글자 주변 화소의 최빈값 → WCAG 2.x 상대 휘도 대비 `(L1+0.05)/(L2+0.05)`. 대신 Accessibility Scanner를 써도 된다(이슈 #27 재현 절차와 같은 방법).
- 배경이 투명한 글자(T6 빠른 동작 라벨)와 덧칠 배경(T3 선택 상태, T8 서랍)은 글자 바로 주변의 실제 화소 최빈값을 배경으로 쓴다. T6은 빈 폴더가 아닌 파일 목록 위에서 빠른 동작 메뉴를 펼친 상태로 잰다.
- 판정: 작은 글자(18sp 미만, 굵게는 14sp 미만) 4.5:1 이상. 글자 대상은 모두 작은 글자다(T1 12dp, T8 10sp, 목록 보조 글자 14sp 이하). T9(아이콘)은 그래픽 3:1 이상.
- 측정 조건: 설정 → Material Design 3 켬, 밝은 테마. T1·T2는 목록을 맨 위(평소 배경)와 스크롤한 상태(떠오른 배경) 두 번.

### 3-2. 기타
- 지원 API: minSdk 23 이상 전 구간. API 31~33과 34+는 동적 색 자원의 출처가 다르다(1-1 표).
- 권한·루트·네트워크: 해당 없음. 새 문구·새 화면 없음.
- 접근성: 이 변경의 목적 자체가 `Visual_Contrast`(5장) 충족이다. 보조 글자와 기본 글자(`textColorPrimary`)의 구별은 색 후보 비교 항목으로 HLD에서 다룬다(요구사항은 아님).

## 4. 제약과 가정
- **코드 경로가 아닌 자원 문제**: 보조 색은 위의 앱 색 자원 두 개(밝은 테마용)에서 정해진다(1-1). `?android:textColorSecondary`를 쓰는 요소(T1~T7, T9)는 이 자원을 거치므로 이 자원을 바꾸면 함께 바뀐다고 가정한다(REQ-27-03 측정으로 확인). **서랍 부제목(T8)은 이 가정 밖이다** — `?colorOutline`을 직접 쓰므로 별도 변경이 필요하며, `colorOutline` 값 자체는 바꾸지 않는다(REQ-27-05).
- **업스트림 파일**: 고칠 자원은 업스트림(zhanghai/MaterialFiles) 작성자가 만든 파일이다. 업스트림 동기화 때 충돌 가능성이 있다 — HLD 위험 항목에서 다룬다.
- **사용자에게 보이는 색 변경**: 요구사항 정리 v1·v2 "확인이 필요한 점"에 따라 색은 SRS·HLD 검수 뒤 오너가 고른다(REQ-27-07). 오너 결정 전에는 구현에 들어가지 않는다.
- 동적 색은 배경화면마다 다르다. 측정은 에뮬레이터 기본 배경화면 하나로 하고, 다른 배경화면에서의 성립은 톤 기반 계산(1-1 검산)으로 보인다.
- Android 14+의 시스템 "대비" 설정(표준·중간·높음)은 API 34+ 시스템 색 토큰 값을 바꾼다. 측정은 표준 대비에서 한다. 후보에 따라 이 설정을 따르는지가 달라지며 HLD에서 비교한다.

### 4-1. 이력: #61 흡수
- 첫 작성(v1.0) 때 탐색 서랍 부제목(T8)을 범위 밖으로 보고 이슈 #61로 등록했으나, PM이 요구사항 정리 v2로 #27에 흡수했다(#61에 PM 분류 코멘트). 이 SRS는 v2 기준이다.
- 규격 표기 메모: `docs/STANDARDS_TRACEABILITY.md:53` 원문은 "18pt/14pt", A11Y-3(`:95`)과 이 SRS는 sp로 적었다. 단위 해석은 이 SRS에서 새로 하지 않는다(조항 원문 확인 필요). 모든 글자 대상은 14sp 이하라 어느 해석이든 4.5:1이 적용된다.

## 5. 규격 근거 (부록 S)
| 조항 | 요지 | 필수/권장 | 충족 방법 |
|---|---|---|---|
| `Visual_Contrast` (`docs/STANDARDS_TRACEABILITY.md:53`) | 작은 글자 4.5:1, 큰 글자·그래픽 3:1 이상. 확인: Accessibility Scanner | 필수 | REQ-27-01~04, 08, 09, 10 |
| A11Y-3 (`docs/STANDARDS_TRACEABILITY.md:95`) | 18sp 미만(굵게 14sp 미만) 4.5:1, 나머지 3:1 | 권장 | `Visual_Contrast`와 같이 충족 |
| `Theme_Support` | 밝은·어두운 테마 지원 | 필수 | REQ-27-05(통과하는 테마 유지) |

## 6. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1.0 | 최초 작성 | 요구사항 정리 v1 | PM |
| 2026-11-19 | v1.1 | 요구사항 정리 v2 반영(T8 서랍 부제목, REQ-27-09), T9 팝업 아이콘(REQ-27-10), T7 구체화, `textAppearanceListItemSecondary` 제외 근거, T3 선택 상태 배경, T6 측정법, REQ-27-05·06 보강, 4-1 갱신 | SRS 검수 1회 반려(중1 경3), 요구사항 정리 v2 | 검수팀, PM |
| 2026-11-19 | v1.2 | 줄 번호 정정(`preference_material.xml:58`, `styles_material3.xml:49`), 상태 검수 통과 | SRS 검수 2회차 조건부 통과(경미 2) | 검수팀 |
