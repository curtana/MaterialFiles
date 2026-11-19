# SRS — Material 3 밝은 테마 보조 글자 대비 (이슈 #27)

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 |
| 근거 | 요구사항 정리 v1 (이슈 #27, 2026-11-19) |

## 1. 목적과 범위

Material 3(설정 "Material Design 3" 켬) + 밝은 테마에서 보조 글자 색(`?android:textColorSecondary`)이 배경과 4.5:1 대비를 내지 못한다(실측 `#757780` / `#FAF8FF` = 4.24:1, 이슈 #27 본문). 이 SRS는 그 색을 `Visual_Contrast` 기준(작은 글자 4.5:1 이상)에 맞추는 요구사항을 정한다.

- **범위 안**: Material 3 밝은 테마에서 `?android:textColorSecondary`로 그려지는 모든 글자(2-1의 목록). 툴바 부제목과 경로 표시줄의 현재가 아닌 항목이 우선 대상이다.
- **범위 밖**(요구사항 정리 v1 "범위 밖"): 테마 체계 전반의 재설계, `?android:textColorSecondary`가 아닌 다른 글자 역할의 색 변경(예: `?colorOutline`을 직접 쓰는 글자 — 4-1 참고), 이미 기준을 넘는 테마의 색.

### 1-1. 현재 값의 출처 (근거)

실측값 `#757780`은 Material Components 라이브러리 기본값(`colorOnSurfaceVariant`, 톤 30)이 아니라, **앱이 라이브러리 색 자원을 덮어써 `colorOutline`(톤 50)으로 바꾼 값**이다.

| 단계 | 근거 |
|---|---|
| Material 3 테마의 부모 | `app/src/main/res/values/themes_material3.xml:10` `Theme.Material3.DynamicColors.DayNight.NoActionBar` |
| 라이브러리가 `android:textColorSecondary`에 넣는 자원 | material 1.14.0 `res/values/values.xml:3227`(`Base.V14.Theme.Material3.Light`) → `@color/m3_default_color_secondary_text`; API 31+ `res/values-v31/values-v31.xml:334`(`Theme.Material3.DynamicColors.Light`) → `@color/m3_dynamic_default_color_secondary_text` |
| 라이브러리 원래 값 | `m3_default_color_secondary_text` → `m3_sys_color_light_on_surface_variant`(= neutral variant 톤 30, 정적 `#49454F`) |
| **앱의 덮어쓰기**(업스트림 작성자 Hai Zhang, 2022 저작권 표기, 마지막 변경 커밋 `4cd7710`) | `app/src/main/res/color/m3_default_color_secondary_text.xml:13` → `@color/m3_sys_color_light_outline`; `app/src/main/res/color-v31/m3_dynamic_default_color_secondary_text.xml:13` → `@color/m3_sys_color_dynamic_light_outline` |
| `outline`의 값 | 정적: neutral variant 톤 50 `#79747E`(material 1.14.0 `values.xml:684,517`). 동적(API 31~33): `system_neutral2_500`(`values-v31.xml:111,22`). 동적(API 34+): `system_outline_light`(`values-v34.xml:54`) |
| 어두운 테마(바꾸지 않음) | `app/src/main/res/color/m3_dark_default_color_secondary_text.xml:13`, `color-v31/m3_dynamic_dark_default_color_secondary_text.xml:13` → `outline`(톤 60) |

검산: Material 3 색은 HCT 톤(= CIE L\*)이 상대 휘도를 정하므로 대비는 색상(hue)과 무관하게 톤만으로 거의 정해진다. 톤 50 글자 / 톤 98 배경(`colorSurface`) = **4.27:1**로 실측 4.24:1과 맞고, 어두운 테마 톤 60 / 톤 6 = 5.85:1도 실측 5.85:1과 맞는다. 즉 배경화면(동적 색)이 바뀌어도 이 미달은 재현된다.

## 2. 기능 요구사항

### 2-1. 대상 목록 (Material 3 밝은 테마에서 `?android:textColorSecondary`를 쓰는 글자)

| # | 요소 | 근거(`파일:줄`) | 배경(Material 3 밝은 테마) |
|---|---|---|---|
| T1 | 파일 목록 툴바 부제목 | `res/layout/file_list_fragment_app_bar_include.xml:36` → `res/values/styles.xml:18`(12dp) → AppCompat 부제목 글자 모양의 `?android:textColorSecondary` | 앱바: 평소 `colorSurface`(톤 98, `themes_material3.xml:43`), 스크롤로 떠오르면 `colorSurfaceContainer`(톤 94, material `m3_comp_app_bar_on_scroll_container_color`) |
| T2 | 경로 표시줄의 현재가 아닌 항목 | `java/.../filelist/BreadcrumbLayout.kt:31-38` | T1과 같은 앱바 |
| T3 | 파일 목록 항목 보조 글자 | `res/layout/file_item_list.xml:94-95` | 목록(`colorSurface`) |
| T4 | 기타 목록 항목 보조 글자 | `res/layout/bookmark_directory_item.xml:49`, `storage_item.xml:50`, `color/storage_name.xml:10`, `lan_smb_server_item.xml:49`, `lan_smb_server_loading_item.xml:20`, `about_fragment.xml:244` | 화면 또는 대화상자 |
| T5 | 대화상자 안 보조 글자 | `res/layout/permission_item.xml:33,40`, `principal_item.xml:48`(권한·소유자 대화상자), `file_job_conflict_dialog_view.xml:86,162`(충돌 대화상자) | 대화상자 `colorSurfaceContainerHigh`(톤 92, material `m3_comp_dialog_container_color`) |
| T6 | 코드에서 읽는 보조 색 | `java/.../ui/ThemedSpeedDialView.kt:159`(빠른 동작 라벨, M3에서 배경 투명 `:160-162`), `java/.../settings/StandardDirectoryListPreferenceFragment.kt:49` | 화면 |
| T7 | 라이브러리 스타일이 암묵적으로 쓰는 경우 | 설정 화면 Preference 요약 글자 등(AppCompat·Preference 스타일의 `?android:textColorSecondary`) | 화면 |

### 2-2. 요구사항

| ID | 요구사항 | 우선순위 | 검증 방법 |
|---|---|---|---|
| REQ-27-01 | Material 3 밝은 테마에서 툴바 부제목(T1)은 앱바의 평소 배경과 떠오른 배경 모두에 대해 4.5:1 이상 대비를 내야 한다. | 필수 | 3-1 측정 절차로 두 상태를 각각 측정 |
| REQ-27-02 | Material 3 밝은 테마에서 경로 표시줄의 현재가 아닌 항목(T2)은 REQ-27-01과 같은 두 배경에 대해 4.5:1 이상이어야 한다. 현재 항목(`textColorPrimary`)의 색은 바뀌지 않는다. | 필수 | 3-1 측정 절차 |
| REQ-27-03 | Material 3 밝은 테마에서 2-1의 T3~T7 글자도 각자 실제 배경에 대해 4.5:1 이상이어야 한다. 가장 어두운 배경은 대화상자(톤 92)이므로 대화상자 글자(T5)를 반드시 포함해 측정한다. | 필수 | 3-1 측정 절차로 T3, T5(권한 또는 충돌 대화상자 중 하나 이상), T7(설정 요약) 측정 + 자원 해석 계산(HLD에서 정하는 방식)으로 나머지 확인 |
| REQ-27-04 | REQ-27-01~03은 동적 색(API 31+, 배경화면에서 색을 뽑는 경우)과 정적 팔레트(API 23~30 또는 동적 색을 못 쓰는 경우) 모두에서 성립해야 한다. 배경화면에 따라 대비가 4.5:1 아래로 떨어지는 색 선택은 허용하지 않는다. | 필수 | 동적 색: 에뮬레이터(API 36) 실측 + 톤 기반 계산. 정적: 정적 팔레트 16진 값으로 계산, 가능하면 API 30 이하 에뮬레이터 실측 |
| REQ-27-05 | 이미 기준을 넘는 테마의 보조 글자 색은 바뀌지 않아야 한다: Material 3 어두운 테마, Material 3 검은 밤 모드, Material 3 몰입(`Immersive`, 어두운 테마 기반) 화면, Material 3 끔의 모든 테마(밝음·어두움·검은 밤, 모든 테마 색). | 필수 | 변경 전후 해당 색 자원 파일이 같음(diff) + Material 3 어두운 테마와 Material 3 끔 밝은 테마에서 부제목 화소값이 변경 전 실측(`#8F909A`, `#666666`)과 같음 |
| REQ-27-06 | 보조 글자의 비활성(`state_enabled=false`) 상태 색은 바뀌지 않아야 한다. | 필수 | 변경 전후 색 상태 목록의 비활성 항목이 같음(diff) |
| REQ-27-07 | 바꿀 색은 설계팀이 제안한 후보(HLD) 중 오너가 고른 것이어야 하며, 고른 결과와 일자를 HLD 변경 이력과 이슈 #27에 남긴다. | 필수 | HLD 설계 결정 표·변경 이력과 이슈 코멘트 대조 |
| REQ-27-08 | 고친 뒤 같은 방법(화면 캡처 화소값, 또는 Accessibility Scanner)으로 REQ-27-01~05 대상을 다시 재고, 변경 전·후 값(글자 색, 배경 색, 대비)을 구현 PR에 표로 남긴다. | 필수 | 구현 PR 본문의 측정 표 확인 |

## 3. 비기능 요구사항

### 3-1. 측정 절차 (REQ-27-01~05, 08 공통)
- 환경: 에뮬레이터 `Medium_Phone_API_36`(#7 T-C09와 같은 환경). 오너 기기에는 설치하지 않는다(설치는 사전 확인 대상).
- 방법: `adb exec-out screencap -p`로 캡처 → 글자 색은 글자 획 안쪽 화소(테두리 안티에일리어싱 화소 제외)의 대표값, 배경 색은 글자 주변 화소의 최빈값 → WCAG 2.x 상대 휘도 대비 `(L1+0.05)/(L2+0.05)`. 대신 Accessibility Scanner를 써도 된다(이슈 #27 재현 절차와 같은 방법).
- 판정: 작은 글자(18sp 미만, 굵게는 14sp 미만) 4.5:1 이상. 모든 대상은 작은 글자다(T1 12dp, 목록 보조 글자 14sp 이하).
- 측정 조건: 설정 → Material Design 3 켬, 밝은 테마. T1·T2는 목록을 맨 위(평소 배경)와 스크롤한 상태(떠오른 배경) 두 번.

### 3-2. 기타
- 지원 API: minSdk 23 이상 전 구간. API 31~33과 34+는 동적 색 자원의 출처가 다르다(1-1 표).
- 권한·루트·네트워크: 해당 없음. 새 문구·새 화면 없음.
- 접근성: 이 변경의 목적 자체가 `Visual_Contrast`(5장) 충족이다. 보조 글자와 기본 글자(`textColorPrimary`)의 구별은 색 후보 비교 항목으로 HLD에서 다룬다(요구사항은 아님).

## 4. 제약과 가정
- **코드 경로가 아닌 자원 문제**: 보조 색은 위의 앱 색 자원 두 개(밝은 테마용)에서 정해진다(1-1). 같은 속성을 쓰는 모든 요소(T1~T7)가 한 자원을 거치므로 자원 한 곳을 바꾸면 모두 함께 바뀐다고 가정한다. 이 가정은 REQ-27-03 측정으로 확인한다.
- **업스트림 파일**: 고칠 자원은 업스트림(zhanghai/MaterialFiles) 작성자가 만든 파일이다. 업스트림 동기화 때 충돌 가능성이 있다 — HLD 위험 항목에서 다룬다.
- **사용자에게 보이는 색 변경**: 요구사항 정리 v1 "확인이 필요한 점"에 따라 색은 SRS·HLD 검수 뒤 오너가 고른다(REQ-27-07). 오너 결정 전에는 구현에 들어가지 않는다.
- 동적 색은 배경화면마다 다르다. 측정은 에뮬레이터 기본 배경화면 하나로 하고, 다른 배경화면에서의 성립은 톤 기반 계산(1-1 검산)으로 보인다.
- Android 14+의 시스템 "대비" 설정(표준·중간·높음)은 API 34+ 시스템 색 토큰 값을 바꾼다. 측정은 표준 대비에서 한다. 후보에 따라 이 설정을 따르는지가 달라지며 HLD에서 비교한다.

### 4-1. 관련 발견 (범위 밖, 이슈로 등록)
- 탐색 서랍 항목 부제목(저장소 여유 공간 등, 10sp)은 Material 3에서 `?android:textColorSecondary`가 아니라 `?colorOutline`을 직접 쓴다(`res/values/styles_material3.xml:48` → `res/color/navigation_item_subtitle_text_color_material3.xml:14`). 톤 50이라 톤 98 배경에서도 4.27:1로 같은 미달이 예상되지만(미측정), 요구사항 정리 v1이 다른 글자 역할을 범위 밖으로 두었으므로 별도 이슈 #61로 등록했다(PM 분류 대기).

## 5. 규격 근거 (부록 S)
| 조항 | 요지 | 필수/권장 | 충족 방법 |
|---|---|---|---|
| `Visual_Contrast` (`docs/STANDARDS_TRACEABILITY.md:53`) | 작은 글자 4.5:1, 큰 글자·그래픽 3:1 이상. 확인: Accessibility Scanner | 필수 | REQ-27-01~04, 08 |
| A11Y-3 (`docs/STANDARDS_TRACEABILITY.md:95`) | 18sp 미만(굵게 14sp 미만) 4.5:1, 나머지 3:1 | 권장 | `Visual_Contrast`와 같이 충족 |
| `Theme_Support` | 밝은·어두운 테마 지원 | 필수 | REQ-27-05(통과하는 테마 유지) |

## 6. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1.0 | 최초 작성 | 요구사항 정리 v1 | PM |
