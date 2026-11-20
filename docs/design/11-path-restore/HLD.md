# HLD — 프로세스 종료 후 창 경로 복원 (이슈 #11)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과 (v1.3 — 바뀐 절(K3·3.3 예외 이름) 재검수 조건부 통과, PR #60 issuecomment-6999407469), 구현됨(PR #70, v1.7.5-beta.2). v1.4는 R1 결과 기록 |
| 근거 | SRS v1.2 (오너 결정 issuecomment-6999035882 반영: Q1=A 현재 경로만, Q2=가 그 경로에서 기존 오류 표시, Q3=활성 창도 복원 → REQ-11-09) |
| 기준 코드 | `origin/develop` 7cf16b7 (줄 번호 기준) |

## 1. 구조 개요

### 1.1 기존 경계
- 프런트엔드: 화면 `FileListActivity` → 호스트 `FileListHostFragment`(K2, #2) → 창 `FileListFragment` ×1~2, 창마다 `FileListViewModel`, 호스트 범위 `DualPaneViewModel`(#2 HLD K3). 경로 상태는 창 뷰모델의 `TrailLiveData`에만 있다(`FileListViewModel.kt:27`).
- 백엔드: NIO2 `FileSystemProvider`(`provider/`). 경로는 `java8.nio.file.Path`이며 URI로 왕복할 수 있다(`Path.toUri()` `provider/common/ByteStringListPath.kt:213`, `Paths.get(URI)` → 각 provider의 `getPath(uri)`, 예: `provider/sftp/SftpFileSystemProvider.kt:95-100`, `provider/archive/ArchiveFileSystemProvider.kt:74-80`).
- 이 앱은 이미 "경로 → URI 문자열 → 경로" 왕복을 화면 사이 계약으로 쓴다: `Intent.extraPath`(`util/IntentPathExtensions.kt:21-38`) — 새 창에서 열기(`FileListFragment.kt:954-958`)·바로가기(`:1585-1596`)가 모든 경로 종류(로컬·루트·압축·원격·문서)에 이 계약을 쓴다.

### 1.2 새 구조
새 모듈 경계는 만들지 않는다. **창 프래그먼트의 저장 상태(`onSaveInstanceState` Bundle)에 현재 경로의 URI 문자열을, 호스트 프래그먼트의 저장 상태에 활성 창 번호를 넣고**, 창이 시작 경로를 정하는 기존 자리(`FileListFragment.kt:274-335`)에서 "복원 값 → Intent 경로 → 기본 디렉터리" 순으로 고른다. 고르는 규칙은 순수 함수(K3)로 떼어 JUnit으로 시험한다. 활성 창은 호스트가 복원 때 되돌린다(K4). `provider/`는 바꾸지 않는다.

```mermaid
flowchart LR
  subgraph 저장[저장: 화면이 멈출 때]
    VM1[FileListViewModel.currentPath] -->|toUri().toString()| B[(창 프래그먼트 Bundle)]
  end
  subgraph 복원[복원: onActivityCreated, hasTrail=false]
    B -->|문자열| D[K3 decode: URI → Path, 실패 시 null]
    I[Intent 처리: pickOptions·Intent 경로] --> R[K3 resolve]
    D --> R
    S[Settings.FILE_LIST_DEFAULT_DIRECTORY] --> R
    R -->|resetTo| VM2[새 FileListViewModel]
  end
  VM2 --> L[목록 읽기: 기존 비동기, 실패 표시 기존 그대로]
```

## 2. 구성요소
| 구성요소 | 위치(패키지·파일) | 책임 | 추적 REQ |
|---|---|---|---|
| K1 창 프래그먼트 저장·시작 경로 (수정) | `filelist/FileListFragment.kt` — `onSaveInstanceState` 추가, `onActivityCreated` 시작 경로 블록(`:274-335`) 재배치 | (a) **읽기·보관·저장**: `onCreate(savedInstanceState)`에서 Bundle의 복원 값(URI 문자열, 없으면 `null`)을 읽어 프래그먼트의 비공개 필드(예: `restoredPathUri: String?`, 이름은 LLD)에 보관한다. `onSaveInstanceState`에서는 창 뷰모델에 경로가 있으면(`hasTrail`) 현재 경로를 K3 `encode`로 넣고, 경로가 아직 없으면(뷰를 만들기 전에 다시 저장되는 경우) 보관한 복원 값을 그대로 다시 넣어 잃지 않는다. `onCreate`에서 읽는 이유: `onActivityCreated`보다 먼저 불리고 뷰를 만들지 않은 채 다시 저장되는 경우에도 값이 필드에 있어야 하기 때문이다. (b) **복원**: `onActivityCreated`에서 `hasTrail`이 거짓일 때 기존 Intent 처리(선택 조건 `PickOptions`·Intent 경로·압축 루트·`ACTION_VIEW_DOWNLOADS`)를 **지금처럼 모두 수행**한 뒤, 시작 경로만 K3로 고른다(`restored = decode(restoredPathUri)`). `hasTrail`이 참(구성 변경)이면 지금처럼 아무것도 하지 않는다. | 01, 02, 03, 05, 06, 07 |
| K2 `FileListViewModel` (변경 없음) | `filelist/FileListViewModel.kt` | 경로 상태의 주인은 그대로. `resetTo(path)`(`:35`)로 복원 경로를 받는다. 뷰모델 팩토리(`util/FragmentViewModelLazy.kt:15-31`)는 바꾸지 않는다(D1). `:25`의 TODO는 이 작업이 경로 부분을 프래그먼트 저장 상태로 해결하므로 LLD에서 정리 여부를 정한다. | 01, 04 |
| K3 시작 경로 규칙 (새로, 순수 함수) | `filelist/` 새 파일(이름은 LLD, 예: `PaneStartPath.kt`) | `resolve(restored, intentPath, default)` = `restored ?: intentPath ?: default`. `decode(uriString)` = URI 생성 실패(`URISyntaxException`, 기존 `URI::class.createOrLog`가 `null`로 바꿈 `util/IntentPathExtensions.kt:24`)와 `Paths.get(URI)` 예외 — `IllegalArgumentException`(스킴·경로·쿼리 없음, 예: `LocalLinuxFileSystemProvider.kt:64-69`, `FtpFileSystemProvider.kt:96-102`, `ArchiveFileSystemProvider.kt:74-80`), `FileSystemNotFoundException`(설치되지 않은 스킴 — retrofile 1.2.0 백포트 `java8.nio.file.Paths.get(URI)`는 `installedProviders()`에 없으면 이 예외를 던진다, LLD 바이트코드 확인), `ProviderNotFoundException`(백포트는 던지지 않으나 방어적으로 함께 잡는다) — 를 잡아 로그 후 `null`(REQ-11-06). `Error`(예: `AssertionError`)는 잡지 않는다 — 3.3에서 URI로는 닿지 않음을 확인했다. `encode(path)` = `path.toUri().toString()`. `resolve`는 Android 타입에 기대지 않는 형태(제네릭 등)로 두어 JVM JUnit으로 시험한다. | 02, 03, 06 |
| K4 활성 창 저장·복원 (수정) | `filelist/FileListHostFragment.kt` — `onSaveInstanceState` 추가, `onActivityCreated`(`:111-145`) | 호스트 `onSaveInstanceState`에서 `DualPaneViewModel.activePane`을 Bundle에 넣는다. `onActivityCreated`에서 `savedInstanceState`가 있으면 `isDualPaneEnabled` 설정(`:114-115`) **뒤에** 저장 값으로 `setActivePane`(`DualPaneViewModel.kt:22-31`)을 부른다 — 두 창 미사용(선택 모드)이면 창 2 값은 무시돼 창 1로 남고(REQ-11-09 (d)), 같은 값이면 아무것도 하지 않는다(`:27-29`). 구성 변경에서는 저장 직후 재생성이라 저장 값이 현재 값과 같아 결과가 같으므로 뷰모델이 새것인지 가르지 않는다(D7). 창 목록·탐색 서랍이 활성 창을 읽기 전(`:147` `panes = …` 이후, 서랍 리스너 연결 `:148` 전후 순서는 LLD)에 되돌린다. 한 창 모드에서 보이는 창은 기존처럼 활성 창을 따른다(#2 REQ-2-05). | 09 |
| K5 규격 추적표 갱신 | `docs/STANDARDS_TRACEABILITY.md` 2.1 `:46`, 3장 `:119`, 4장 | 구현 PR에서 편차 해소 표시, 2.1 행의 "이 앱에 해당?"·"충족 방법" 열, 4장 #11 행. | 08 |

## 3. 데이터 흐름·상태

### 3.1 경우별 흐름
| 경우 | `savedInstanceState` | 뷰모델 `hasTrail` | K1 동작 | 결과 |
|---|---|---|---|---|
| 새로 시작 (실행기·최근 앱에서 지운 뒤·뒤로 가기로 끝낸 뒤·다른 앱이 경로로 열기) | 없음 | 거짓 | Intent 처리 → K3(`restored=null`) | 지금과 같음: Intent 경로 또는 기본 디렉터리(REQ-11-03, #2 REQ-2-18·19) |
| 구성 변경 (회전·크기 변경) | 있음 | **참** | 시작 경로 블록을 건너뜀(지금과 같음) | 경로·이동 기록·스크롤 그대로(REQ-11-07) |
| 시스템 종료 후 복원 (프로세스 종료, "활동 보관 안함") | 있음 | 거짓 | Intent 처리(선택 조건 포함) → K3(`restored=decode(저장 값)`) | 저장된 경로(REQ-11-01·02). 저장 값이 없거나 깨졌으면 새로 시작과 같음(REQ-11-06) |
| 복원 때 호스트가 창 2를 새로 만드는 경우(이전 판 저장 상태, `FileListHostFragment.kt:137-142`) | 창 2는 없음 | 거짓 | 새로 시작과 같음 | 창 2 기본 디렉터리(지금과 같음) |
| 활성 창 (K4, 호스트) | 호스트 Bundle | — | 있으면 저장 값으로 `setActivePane`, 없으면 처음 값 창 1 | 시스템 종료 후 복원: 저장 시점의 활성 창(REQ-11-09). 새로 시작: 창 1. 구성 변경: 같은 값이라 변화 없음 |

- 두 창은 각자의 프래그먼트 Bundle에 저장하므로 서로 독립이다. 한 창의 복원 실패가 다른 창에 영향을 주지 않는다(REQ-11-05 마지막 문장). 한 창 모드에서 숨은 창도 FragmentManager에 붙어 있어 똑같이 저장·복원된다(#2 HLD D8).
- 이동 기록(Q1 A): 복원은 `resetTo(path)` → `TrailData.of(path)`(`TrailData.kt:59-64`)라 상위 단계가 모두 생겨 뒤로 가기가 상위 폴더로 올라간다(REQ-11-04).
- 반대쪽 창 경로 표시: 복원된 경로가 `currentPathLiveData` 관찰(`FileListFragment.kt:336`) → `onCurrentPathChanged` → 경로 게시판(`:622-626` → `DualPaneViewModel.kt:74-81`)으로 지금처럼 퍼진다. 추가 작업 없음(REQ-11-01).

### 3.2 저장 시점과 값
- 저장 시점: 프래그먼트 `onSaveInstanceState`(시스템이 화면을 멈출 때 부른다). 경로가 바뀔 때마다 따로 쓰지 않는다 — 저장 상태는 이 시점의 값만 쓰이기 때문이다.
- 값: 창마다 문자열 하나(현재 경로 URI). 저장 크기는 경로 길이 수준이라 Binder 한도(SRS 3장)와 무관하다.
- 인증 정보: URI에는 원격 저장소의 사용자 이름·호스트·포트가 들어가고(`provider/sftp/client/Authority.kt:15-27` `toUriAuthority`) 비밀번호·키는 들어가지 않는다. 복원 뒤 연결 때 인증 정보는 지금처럼 저장소 설정에서 얻는다(`app/AppInitializers.kt:73-76`). 이는 기존 `Intent.extraPath`·바로가기와 같은 노출 수준이다(SRS 3장 개인정보).

### 3.3 스레드·수명주기·오류 전파
- 스레드: 저장·복원 모두 메인 스레드. K3 `decode`는 URI 해석과 `Paths.get`만 한다. 원격·압축 provider의 `getPath(uri)`는 파일 시스템 객체를 찾거나 만들기만 하고 연결·파일 열기는 하지 않는다(`SftpFileSystemProvider.kt:74-100`, `ArchiveFileSystemProvider.kt:57-80`) — 지금도 Intent 경로를 같은 방식으로 메인 스레드에서 해석한다(`FileListFragment.kt:141` `argsPath`). 나머지 provider도 확인했다(R2 닫음): `ftp`·`ftps`·`ftpes`(`FtpFileSystemProvider.kt:75-81,96-102`), `smb`(`SmbFileSystemProvider.kt:73-80,94-99`), `webdav`·`webdavs`(`WebDavFileSystemProvider.kt:78-82,96-102`)는 권한(authority)별 파일 시스템 객체를 만들고 경로를 만들 뿐이며, 그 파일 시스템 생성자는 루트 경로 검사만 한다(`smb/SmbFileSystem.kt:31-38`, `webdav/WebDavFileSystem.kt:32-38`, `ftp/FtpFileSystem.kt:32-38`). 로컬 `file`은 `LinuxFileSystemProvider`(`RootableFileSystemProvider.getPath` → `LocalLinuxFileSystemProvider.getPath`, `root/RootableFileSystemProvider.kt:45`, `linux/LocalLinuxFileSystemProvider.kt:64-69`)로 가고, 루트 쪽 `RootFileSystemProvider.getPath`(`root/RootFileSystemProvider.kt:31-33`, `AssertionError`)는 설치된 provider가 아니라(`FileSystemProviders.kt:36-51`, 루트 provider는 `LinuxFileSystemProvider` 안의 위임 대상 `linux/LinuxFileSystemProvider.kt:12-15`) URI로는 닿지 않는다. 앱이 루트로 실행 중이면 로컬·압축 외 provider를 설치하지 않으므로(`FileSystemProviders.kt:39`) 그 스킴은 `FileSystemNotFoundException`(백포트 `Paths.get(URI)`, K3) → `decode` `null`. 문서(`document`)·콘텐츠(`content`)는 검수 1회차에서 입출력 없음이 확인됐다(PR #60 HLD 검수 기록).
- 접근 확인: 복원 판단은 접근을 확인하지 않는다. 경로를 정한 뒤 목록 읽기(기존 `FileListLiveData`, 백그라운드)가 접근하고, 실패하면 기존 실패 표시(`FileListFragment.kt:755-782`: 목록 자리 오류 문구, 툴바 부제 "오류")가 뜬다(Q2 가, REQ-11-05). 새로 고침(`refresh()` `:898`)으로 다시 시도, 뒤로 가기·경로 표시줄로 이동 가능.
- 루트 경로: 경로 해석은 루트 접근을 시작하지 않는다. 목록 읽기 때 기존 루트 규칙이 적용된다(SRS 3장 권한).
- 오류 전파: `decode` 실패는 로그만 남기고 `null` → 새로 시작 규칙. 예외가 화면 생성 밖으로 나가지 않는다(REQ-11-06, `Stability_ANR`).
- 현재 폴더 스크롤 위치(SRS 4.3 Q1 A행 "미확인"): 폴더 이동 때의 스크롤 위치는 뷰모델 `TrailData`에 있어(`FileListFragment.kt:946`, `:791`) 복원 뒤 없다. 반면 `RecyclerView`(`:233-236`, 뷰 ID 있음)는 자기 뷰 상태를 프래그먼트 뷰 상태로 저장·복원하고, `GridLayoutManager`(`LinearLayoutManager` 계열)는 항목이 0개인 동안 대기 상태를 쥐고 있다가 항목이 찼을 때 적용하는 것이 AndroidX의 동작이므로 현재 폴더 위치는 되살아날 가능성이 높다. 다만 목록이 비동기로 다시 차고 어댑터가 비워졌다 채워지는 이 앱의 흐름에서 실제로 되는지는 미확인 — 구현 검증(V2)에서 확인하고 결과를 SRS Q1 A행에 반영한다(R1). **결과(v1.4)**: 복원됨(PR #70 M-17, R1 닫힘). 이것은 Q1 A의 필수 조건이 아니다(REQ-11-04가 스크롤 위치를 요구하지 않는다).

## 4. 외부 인터페이스
- 새 권한·설정 키·파일·Intent 동작 없음(REQ-11-03). `SharedPreferences`·파일 쓰기 없음.
- 저장 상태 Bundle 키: 창 프래그먼트 안의 비공개 키 하나(K1, 이름은 LLD). 호스트 키 하나(K4).
- 시험: JUnit 4(`app/build.gradle:222`, 이미 승인된 테스트 의존성)로 K3 `resolve`. 새 라이브러리 없음.

## 5. 설계 결정
| 결정 | 대안 | 고른 이유 |
|---|---|---|
| D1 저장 자리는 **창 프래그먼트의 `onSaveInstanceState` Bundle** | (a) `FileListViewModel`에 `SavedStateHandle`(`:25` TODO) | (a)는 뷰모델 팩토리를 `CreationExtras`를 넘기는 형태로 바꿔야 하고(`util/FragmentViewModelLazy.kt:15-31`이 extras를 버림), 뷰모델이 Intent 경로와의 우선순위를 알 수 없어 결국 프래그먼트의 시작 경로 블록과 둘로 나뉜다. 복원 값과 Intent 경로의 선택(REQ-11-02)은 이미 시작 경로를 정하는 프래그먼트 블록(`FileListFragment.kt:274-335`)에 있어야 하므로 같은 자리에 두는 것이 최소 변경이다(부록 S). |
| D2 값은 **URI 문자열** | (a) `Path`를 `Parcelable`로(`ParcelableParceler`, `util/Parcelers.kt:23-29`) | 문자열은 복원 때 클래스 해석이 필요 없어 앱 업데이트 등으로 내부 형식이 바뀌어도 읽기 실패가 `decode`의 `null`로 깔끔히 떨어진다(SRS A6, REQ-11-06). 경로 종류 전부에 대해 이미 쓰는 계약(`Intent.extraPath`, 새 창에서 열기·바로가기)이라 왕복이 검증돼 있다. 파일 시스템 객체를 묶지 않아 저장 값이 작다. |
| D3 Intent 처리는 복원 때도 **모두 수행**하고 시작 경로만 고른다 | (a) 복원 값이 있으면 Intent 처리 블록을 건너뜀 | (a)는 선택 조건 `PickOptions`가 그 블록 안에서만 정해져(`:279-319`, `:332-334`) 선택 모드가 깨진다(REQ-11-02). |
| D4 접근 불가 경로는 **그 경로에서 기존 실패 표시** (Q2 가, 오너 결정 issuecomment-6999035882) | 나: 가까운 상위 / 다: 기본 디렉터리 + 알림 | 오너 결정. 새 구성요소 없음. 나·다의 추가 구성요소(v1.0 R4)는 만들지 않는다. |
| D5 **현재 경로만** 저장 (Q1 A, 오너 결정 issuecomment-6999035882) | B: `trail` URI 목록 + `currentIndex` / C: B + 단계별 목록 상태 | 오너 결정. 나중에 B·C로 넓힌다면 K1의 저장 값이 문자열 목록 + 정수(+ C는 `Parcelable` 목록)가 되고 `TrailData`에 그 값으로 만드는 생성 함수가 필요하다(`TrailData.kt:13-17` 생성자는 비공개). D1·D2·D3는 그대로 유지돼 A에서 B·C로 넓힐 때 버리는 것이 없다. |
| D6 접근 확인을 복원 판단에서 하지 않음 | 복원 때 `exists()` 등으로 확인 | 원격·루트 확인은 입출력이라 메인 스레드에서 할 수 없고(REQ-11-06), 확인 결과로 할 일이 Q2 가에서는 없다(목록 읽기가 같은 확인을 하고 실패를 알린다). |
| D7 활성 창은 **호스트 Bundle**에 두고, 복원 때 뷰모델이 새것인지 가르지 않는다 | (a) `DualPaneViewModel`에 `SavedStateHandle` (b) "뷰모델 새로 생김" 표시를 두고 그때만 되돌림 | 호스트 뷰모델도 사용자 지정 팩토리로 만들어져(`FileListHostFragment.kt:87` `viewModels { { DualPaneViewModel() } }`, `util/FragmentViewModelLazy.kt:15-31`이 extras를 버림) (a)는 D1과 같은 팩토리 변경이 필요하다. 창 경로(D1)와 같은 저장 방식으로 맞추는 편이 변경이 작고 읽기 쉽다. 구성 변경 때 저장 값 = 현재 값이고 `setActivePane`은 같은 값이면 아무것도 하지 않아(`DualPaneViewModel.kt:27-29`) (b)의 구분이 필요 없다. |

## 6. 위험과 미결 사항
| ID | 내용 | 처리 |
|---|---|---|
| R1 | (닫힘, PR #70 M-17) 현재 폴더 스크롤 위치가 복원되는지 미확인(3.3) — V2 뒤 같은 항목·같은 위치로 복원됨을 확인했다(API 36 AVD). SRS 4.3 Q1 A행에 반영. | 구현 V2에서 확인해 결과를 SRS Q1 A행에 반영. 안 되더라도 REQ 위반 아님. 오너가 Q1 C를 고르면 단계별 상태와 함께 다룬다. |
| R2 | 모든 provider의 `getPath(uri)`가 입출력 없이 끝나는지. | **닫음**(v1.1): 3.3에 provider별 근거. 루트 `getPath`(`AssertionError`)는 URI로 닿지 않음. LLD는 K3 `decode` 시험 항목으로 잡을 예외 세 가지(K3)와 알 수 없는 스킴·빈 문자열·깨진 URI 입력을 둔다. |
| R3 | 앱 업데이트 뒤 시스템이 이전 저장 상태를 넘기는지(SRS A6). | 미확인. D2로 어느 쪽이든 안전. 확인 의무 없음. |
| R4 | Q2 나·다의 추가 구성요소. | **닫음**(v1.1): 오너가 가를 골라 만들지 않는다(issuecomment-6999035882). |
| R5 | Q3(활성 창). | **닫음**(v1.1): 오너가 복원으로 결정 → K4 무조건, REQ-11-09. |
| R6 | 다른 설계(#47 복사 백엔드, #27 테마 대비, #54 충돌 창)와의 파일 겹침. | 이 설계는 `filelist/FileListFragment.kt`의 시작 경로 블록·저장 상태만 건드린다. #54가 같은 파일의 다른 부분(복사·이동 흐름)을 바꾸면 구현 순서에 따라 병합 충돌만 생길 수 있다 — 기능 간섭 없음. |

## 7. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1.0 | 처음 작성 (Q1 A·Q2 가 권고안 기준, K4는 Q3 조건부) | SRS v1.1 검수 통과 | PM |
| 2026-11-19 | v1.1 | 오너 결정 반영(근거 SRS v1.2): D4·D5 확정, K4 무조건(REQ-11-09)·3.1 활성 창 행·D7 추가, R4·R5 닫음. HLD 검수 1회차 경미 3건 반영: K1 (a)에 복원 값을 `onCreate`에서 읽어 필드에 보관, K3 `decode`가 잡을 예외 명시, 3.3·R2에 ftp/smb/webdav `getPath` 근거와 루트 `getPath` 비도달 확인(R2 닫음) | 오너 결정 issuecomment-6999035882, HLD 검수 1회차(PR #60 PM 기록) | PM·검수팀 |
| 2026-11-19 | v1.2 | D7 근거 인용 정정: 호스트 뷰모델은 사용자 지정 팩토리(`FileListHostFragment.kt:87`)라 `SavedStateHandle` 안도 팩토리 변경이 필요(결론 불변) | HLD 재검수 경미 1건(PR #60 issuecomment-6999126041) | 검수팀 |
| 2026-11-19 | v1.3 | K3·3.3 예외 이름 정정: 설치되지 않은 스킴에 백포트 `Paths.get(URI)`는 `FileSystemNotFoundException`을 던진다(`ProviderNotFoundException` 아님). 잡는 예외 셋은 그대로(설계·동작 불변) | LLD 작성 중 retrofile 1.2.0 `javap -c` 확인(PR #60 LLD e7ab08c F1) | 개발팀 |
| 2026-11-20 | v1.4 | R1 닫음: 현재 폴더 스크롤 위치 복원 확인(PR #70 M-17), 머리표 상태(구현됨). 설계 변경 없음 | 릴리스 v1.7.5-beta.2 문서 동기화 | 설계팀 |
