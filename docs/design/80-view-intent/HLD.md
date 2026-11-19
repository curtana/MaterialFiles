# HLD — 앱이 떠 있을 때 받은 경로 열기 (이슈 #80)

| 항목 | 내용 |
|---|---|
| 상태 | 작성 중 |
| 근거 | SRS v1.2 (`docs/design/80-view-intent/SRS.md`) |

## 1. 구조 개요
기존 모듈 경계 안에서 끝낸다. `filelist/`의 화면 계층(Activity → Host → Pane)에 "받은 Intent" 경로를 하나 더하고, 시작 경로 규칙은 #11이 만든 순수 함수 파일(`PaneStartPath.kt`)에 모은다. `provider/`, 뷰모델의 이동 기록(`TrailData`), 저장 상태(#11)는 바꾸지 않는다. 새 모듈 경계는 없다.

```mermaid
flowchart LR
  OS[시스템: 작업 재사용] -->|singleTop: onNewIntent| A[FileListActivity]
  A -->|onNewIntent| H[FileListHostFragment]
  H -->|resolveIntentPath| P[PaneStartPath.kt 순수 함수]
  H -->|onResume: activePane.navigateTo| F[FileListFragment 활성 창]
  F -->|onActivityCreated: 같은 resolveIntentPath| P
```

## 2. 구성요소
| 구성요소 | 위치(패키지·파일) | 책임 | 추적 REQ |
|---|---|---|---|
| C1 시작 방식 | `app/src/main/AndroidManifest.xml:64-68` `FileListActivity` | `android:launchMode="singleTop"`을 더한다. 같은 작업의 맨 위가 `FileListActivity`이면 시스템이 새 화면을 쌓지 않고 `onNewIntent`로 넘긴다. root intent와 같든(1.2 ③ (a)) 다르든((b)) 같은 결과라 V0 결과에 기대지 않는다. | REQ-80-01 |
| C2 Activity 전달 | `filelist/FileListActivity.kt` | `onNewIntent(intent)`를 재정의해 `fragment.onNewIntent(intent)`로 넘긴다. `setIntent`는 부르지 않는다(D5). | REQ-80-01, 07 |
| C3 Host 처리 | `filelist/FileListHostFragment.kt` | `onNewIntent(intent)`: Intent를 분류한다(C4). ① 선택 모드 Intent이거나 경로가 없으면 아무것도 하지 않는다. ② 이 화면이 선택 모드 화면이면(`viewModel.isDualPaneEnabled == false`, `FileListHostFragment.kt:115-116`) 경로를 바꾸지 않고 D4대로 새 작업에 둘러보기 화면을 연다. ③ 둘러보기 화면이면 경로를 보류 값으로 두었다가 `onResume`에서 `activePane.navigateTo(path)`와 `closeNavigationDrawer()`를 부른다. 탐색 서랍의 책갈피 선택과 같은 경로다(`FileListHostFragment.kt:350-352`, `369-371`). | REQ-80-01, 02, 04, 05, 06 |
| C4 Intent 경로 규칙 | `filelist/PaneStartPath.kt`(기존, #11) | 두 순수 함수를 더한다. `isPickAction(action: String?): Boolean`은 지금 두 곳에 나뉜 선택 모드 판정(`FileListHostFragment.kt:276-281`, `FileListFragment.kt:298-327`)을 하나로 모은다. `resolveIntentPath(action, path, mimeType, downloadsPath: () -> Path): Path?`는 `FileListFragment.kt:335-349`의 선택 모드가 아닌 갈래(다운로드 폴더, 압축 루트 `createArchiveRootPath`)를 그대로 옮긴다. 둘 다 파일 시스템 입출력이 없다(`isArchiveFile`은 MIME 판정뿐, `PathExtensions.kt:22`). | REQ-80-01, 04, 08 |
| C5 창 시작 경로 | `filelist/FileListFragment.kt:295-354` | 시작 경로 계산의 선택 모드가 아닌 갈래를 C4 호출로 바꾼다(동작 불변). `resolvePaneStartPath`의 우선순위(복원 > Intent > 기본, `PaneStartPath.kt:22-26`)는 그대로다. | REQ-80-07, 08 |
| C6 검증 | `app/src/test/.../filelist/PaneStartPathTest.kt`(기존) | C4의 JUnit: 폴더 VIEW·압축 VIEW·`ACTION_VIEW_DOWNLOADS`·선택 모드 4종·`ACTION_MAIN`·경로 없는 VIEW | REQ-80-08 |

## 3. 데이터 흐름·상태
1. **받기**
   - 시스템이 맨 위의 `FileListActivity`에 `onNewIntent`를 부른다. 문서상 그 전에 화면은 `onPause` 상태가 되고, 뒤에 `onResume`이 온다.
   - C2는 그 Intent를 C3로 넘긴다.
   - C3는 C4로 경로를 구해 Host 필드 `pendingIntentPath: Path?`에 둔다. 메인 스레드에서 입출력 없이 끝난다.
2. **적용**
   - Host `onResume`에서 보류 값이 있으면 꺼내 비우고 `activePane.navigateTo(path)`를 부른다.
   - `onResume`까지 미루는 이유가 있다. `FileListFragment.navigateTo`는 뷰에 묶인 `layoutManager`(`FileListFragment.kt:173`, `254`)를 쓴다. 그런데 시스템 복원 직후에는 창 뷰가 만들어지기 전에 `onNewIntent`가 올 수 있다(수명주기 순서 onCreate → onStart → onNewIntent → onResume).
   - 활성 창은 `viewModel.activePane` 그대로다. 다른 창에는 아무 호출도 없다(REQ-80-02).
3. **뒤로 가기**
   - `navigateTo`는 `TrailData.navigateTo`(`TrailData.kt:18-41`)로 이동 기록을 그 경로의 상위 폴더 목록으로 만든다. 앞부분이 같으면 경로 표시줄 아래 단계와 스크롤 위치가 남는다.
   - 뒤로 가기는 기존 `navigateUp`(`FileListFragment.kt:274-285`)이 그대로 맡는다(REQ-80-03).
4. **오류**
   - 경로를 읽을 수 없으면 기존 목록 읽기(비동기)가 실패를 내고 `onFileListChanged`(`FileListFragment.kt:778-805`)가 오류를 보인다(REQ-80-04).
   - 경로가 없으면 ①에서 끝나므로 화면이 바뀌지 않는다.
5. **상태 저장**
   - 보류 값은 저장하지 않는다. `onNewIntent`와 `onResume` 사이에 상태 저장이 끼는 일은 정상 수명주기에 없다.
   - 적용 뒤의 경로는 #11의 창 경로 저장(`FileListFragment.kt:200-208`)이 그대로 맡으므로, 나중에 복원하면 받은 뒤의 경로가 돌아온다(REQ-80-07).

## 4. 외부 인터페이스
- **Manifest**: `FileListActivity`에 `launchMode="singleTop"`(C1). Intent 필터·새 Intent 종류는 바꾸지 않는다(SRS 1.4).
- **Intent별 결과**

  | Intent | 결과 |
  |---|---|
  | 실행기 `ACTION_MAIN` | 이제 `onNewIntent`로 오지만 C3 ①에서 무시한다. 화면은 그대로다(REQ-80-06). |
  | 앱 안 "새 창에서 열기"(`FLAG_ACTIVITY_NEW_DOCUMENT\|FLAG_ACTIVITY_MULTIPLE_TASK`) | 새 작업이라 영향이 없다. |
  | 폴더 바로 가기(`FLAG_ACTIVITY_CLEAR_TASK`) | 작업을 지우고 새로 만들므로 영향이 없다. |
  | `FLAG_ACTIVITY_NEW_TASK` 없이 다른 앱이 보낸 Intent | 보낸 앱의 작업 맨 위가 그 앱이라 영향이 없다(SRS 1.2 ④). |
  | 결과를 바라는 선택 모드 시작(`startActivityForResult`·`registerForActivityResult`) | `singleTop`이어도 맨 위 인스턴스로 넘겨지지 않고 새 화면이 열린다(AOSP `ActivityStarter`는 맨 위로 넘길 때 `resultTo == null`을 요구한다). 이 앱의 선택 모드 계약(`FileListActivity.OpenFileContract` 등)을 부르는 곳은 `FileListActivity` 밖의 화면이다(`PathPreference.kt:27`, `EditSftpServerFragment.kt:47`, `EditBookmarkDirectoryDialogFragment.kt:34`는 `EditBookmarkDirectoryDialogActivity` 안). |

- **권한·설정 키·파일 형식**: 새로 없다.

## 5. 설계 결정
| 결정 | 대안 | 고른 이유 |
|---|---|---|
| D1 `launchMode="singleTop"` + `onNewIntent` | (가) `singleTask`/`singleInstance` (나) 표준 모드 유지 후 새로 쌓인 화면이 이전 화면에 경로를 넘기고 스스로 끝냄 (다) 다른 앱이 보낼 때 `FLAG_ACTIVITY_SINGLE_TOP`을 달기 | (가)는 "새 창에서 열기"의 여러 작업과 다른 앱 작업에 뜨는 화면(SRS 1.2 ④) 동작을 바꾼다. (나)는 (a)(같은 Intent)에서 시스템이 아무 화면도 만들지 않아 고칠 수 없다. (다)는 보내는 쪽을 이 앱이 정할 수 없다. `singleTop`은 (a)·(b)를 모두 `onNewIntent` 하나로 모으고, 결과를 바라는 시작과 다른 작업은 건드리지 않는다. |
| D2 적용은 Host `onResume`에서 | `onNewIntent`에서 바로 적용 | 창 뷰가 없을 때 `navigateTo`가 `layoutManager` 초기화 전 접근으로 죽을 수 있다(3장 2). `onResume`은 `onNewIntent` 뒤에 반드시 온다. |
| D3 `navigateTo`(앱 안 이동과 같음) | `navigateToRoot`/`resetTo`(새로 연 것처럼 이동 기록을 다시 만듦) | SRS REQ-80-01은 활성 창 자신의 상태가 탐색 서랍의 책갈피 선택과 같게 바뀌기를 요구한다. 그 경로가 `navigateTo`다. 뒤로 가기 결과(상위 폴더)는 두 방식이 같다(REQ-80-03). 경로 표시줄 아래 단계를 남겨 앱 안 이동과 같게 둔다. |
| D4 선택 화면이 맨 위일 때는 새 작업으로 둘러보기 화면을 연다(`FileListActivity.createViewIntent(path)`에 `openInNewTask`와 같은 플래그, `FileListFragment.kt:977-981`) | (가) 무시 (나) 같은 작업에 쌓기 | (가)는 받은 경로가 보이지 않아 요구사항 2에 어긋난다. (나)는 `singleTop`에서 같은 컴포넌트를 다시 시작하면 그 선택 화면에 다시 넘겨져 쌓을 수 없다. 앱에 이미 있는 "새 창에서 열기" 방식이라 새 동작을 만들지 않는다. 경로는 C4로 이미 정해졌으므로 Intent는 `createViewIntent(path)`로 다시 만든다. 그래서 압축 루트 경로도 그대로 간다. |
| D5 `setIntent`를 부르지 않음 | `setIntent(intent)` | `getIntent()`는 처음 만들 때(`FileListActivity.kt:31-33`)와 이전 버전 상태 복원(`:41-55`)에서만 읽힌다. 복원 때는 저장 경로가 Intent보다 앞선다(REQ-11-02). 처음 Intent를 그대로 두면 #11 복원 입력이 지금과 같아진다(REQ-80-07). 시스템이 복원 때 넘기는 Intent는 앱의 `setIntent`와 상관이 없다. |
| D6 규칙을 `PaneStartPath.kt`에 둠 | 새 파일 | #11이 시작 경로 규칙을 Android 없이 시험하려고 만든 파일이다(`PaneStartPath.kt:15`). 같은 규칙의 다른 입력이라 한곳에 둔다. `Environment` 호출은 `downloadsPath` 인자로 밖에 둔다. |

## 6. 위험과 미결 사항
| ID | 위험·미결 | 대응 |
|---|---|---|
| R1 | 결과를 바라는 시작이 `singleTop`에서 맨 위 인스턴스로 넘겨지지 않는다는 판단은 AOSP 코드 기억에 따른 것이다(미확인). minSdk 23~ 전 버전에서 같은지도 미확인이다. | LLD 단계 V4에서 확인한다. 앱 안 경로 고르기(설정 기본 디렉터리, SFTP 키 파일, 책갈피 폴더)가 지금처럼 결과를 돌려주는지 본다. 지금 `FileListActivity` 안에서 선택 계약을 부르는 곳은 없다(4장). 그래서 넘겨지더라도 영향받는 경로는 다른 앱이 이 앱 작업에 `NEW_TASK`로 선택 Intent를 보내는 경우뿐이고, 그때 시스템은 결과를 바로 취소한다. |
| R2 | SRS 1.2 ③ (a)·(b)·④와 표준 모드의 `onNewIntent` 여부(V0)는 미확인이다. | D1은 V0 결과에 기대지 않는다. V0는 LLD 시작 때 원인 기록용으로 한다. |
| R3 | 화면이 `onPause` 없이 `onNewIntent`를 받는 기기·버전이 있으면 D2의 적용이 다음 `onResume`까지 늦어진다. | 이미 resumed이면 바로 적용하도록 LLD에서 Host 수명주기 상태(`isResumed`)를 본다. 뷰가 있을 때만 바로 적용한다. |
| R4 | `launchMode` 변경은 Manifest 수준이라 Intent를 보내는 앱 쪽의 모든 경로에 영향이 있다. | 4장 표의 각 행을 V1~V5로 수동 확인한다(SRS 4.2). |

## 7. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | v1 | 최초 작성 | SRS v1.2 | PM |
