# SRS — 네트워크 같은 서버 안 이동: rename 실패 시 복사+삭제로 다시 시도하지 않기 (이슈 #95)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1.1). v1.2·v1.3은 3장 결정 기록과 그 잔재 정리(검수 중) |
| 근거 | 요구사항 정리 v1(이슈 #95, PM 2026-11-20) + 오너 결정 2026-11-20("네 개 모두", #95 코멘트) + #55 원칙(원인을 모르면 지우지 않는다, Q6) + 오너 결정 2026-11-20 "둘 다 (가)"(Q1·Q3, #95 코멘트) + PM 결정 2026-11-20(Q2) |
| 기준 코드 | `origin/develop` 9374221. 줄 번호는 이 커밋 기준, 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준이다 |
| 관련 | #55(복사만 변경, Q6), #54(바꾸기 경고), #47(링크 바꾸기), #42(임시 이름 교체 거절) |

## 1. 목적과 범위

### 1.1 목적
FTP·SFTP·SMB·WebDAV에서 같은 서버 안의 이동은 서버 rename/move가 실패하면 그 오류를 무시하고 폰을 거친 복사 뒤 원본 삭제로 다시 시도한다. 오너 판단: rename을 막은 서버는 삭제도 막는 것이 보통이라 이 재시도는 의미가 없고, 시간·데이터 전송만 쓰며 실패 시 반쪽 상태(대상에 복사본, 원본도 남음)를 만든다. 또 '바꾸기' 이동은 rename **전에** 기존 대상을 지우므로 rename이 실패하면 기존 대상만 사라진다. 이 작업은 (1) 같은 서버 안 이동을 rename/move 하나로 끝내고, (2) '바꾸기' 이동에서 기존 대상을 짐작으로 지우지 않게 한다.

### 1.2 범위
- 네 백엔드의 `*CopyMove.move()`(제공자 안 대체 경로).
- `filejob/FileJobs.kt`의 이동 작업(`MoveFileJob`) 중 위 네 백엔드의 같은 서버 이동에 해당하는 대체 경로(1.4-2).
- '바꾸기' 이동의 기존 대상 처리(요구사항 3, 선택은 오너).

### 1.3 범위 밖
- 폰 안 이동(로컬·루트·SAF·압축): 지금과 같다(요구사항 4).
- 서버 안 복사를 서버가 하게 하는 개선(요구사항 정리 "범위 밖").
- 다른 서버 사이 이동의 방식 변경(복사 뒤 원본 삭제를 유지, 요구사항 2). 단 1.4-4의 WebDAV 결함은 요구사항 2를 지키는 데 필요한 만큼만 다룬다(REQ-95-05).
- 충돌 창의 버튼·문구 변경(#54·#55에서 정함). Q3 (가) 결정으로 새 문구는 없다.

### 1.4 현재 동작 (코드 읽기)

**1) 제공자 안 대체 — 네 백엔드가 같은 구조다.** 대상이 있고 바꾸기(`replaceExisting`)면 먼저 대상을 지우고, rename/move를 시도해 실패하면 `atomicMove`가 아닐 때 예외를 버리고 `copy()` 뒤 원본 삭제로 넘어간다.

| 백엔드 | `move()` | 대상 선삭제 | rename 시도·예외 무시 | 복사+원본 삭제 | 서버 호출 |
|---|---|---|---|---|---|
| FTP | `provider/ftp/FtpCopyMove.kt:146` | `:166-170` `Client.delete` | `:172-181` `Client.renameFile` | `:196-208` | `provider/ftp/client/Client.kt:206-219` commons-net `FTPClient.rename`(RNFR/RNTO) |
| SFTP | `provider/sftp/SftpCopyMove.kt:241` | `:275-279` `Client.remove` | `:281-290` `Client.rename` | `:305-` | `provider/sftp/client/Client.kt:96-109` sshj `SFTPClient.rename(String, String)`(플래그 없음. 주석 "won't overwrite an existing file") |
| SMB | `provider/smb/SmbCopyMove.kt:169` | `:198-202` `Client.delete` | `:204-215` `Client.rename` | `:230-` | `provider/smb/client/Client.kt:445-474` smbj `DiskEntry.rename(path, true)`(`:469`, **이미 replaceIfExists=true**) |
| WebDAV | `provider/webdav/WebDavCopyMove.kt:157` | `:177-181` `Client.delete` | `:183-192` `Client.move` | `:207-219` | `provider/webdav/client/Client.kt:113-125` dav4jvm `DavResource.move(url, false)`(`:118`, `Overwrite: F`) |

**대체가 일어나는 때.** 제공자 `move()`는 경로 종류(scheme)가 같을 때만 불린다(`provider/common/PathExtensions.kt:115-121`, 다르면 `ForeignCopyMove`). 같은 종류 안에서 대체로 가는 경우는 둘이다.
- (가) **같은 서버**에서 서버가 rename을 거절(권한·잠금·이름 규칙 등): 모든 백엔드에서 복사+삭제로 간다 — 이 작업이 없애는 경로.
- (나) **다른 서버**(다른 `Authority`): FTP `Client.kt:207-209`, SFTP `Client.kt:99-103`, SMB `Client.kt:449-456`(같은 서버의 **다른 공유**도 `STATUS_NOT_SAME_DEVICE`로 여기 포함), WebDAV `Client.kt:114-116`이 rename 전에 실패시킨다. FTP·SFTP·SMB는 이 예외를 (가)와 똑같이 무시하고 복사+삭제로 간다 — 이 작업이 유지하는 경로. WebDAV는 1.4-4 참고.
- `Authority`의 구성: FTP=protocol·host·port·username·mode·encoding(`provider/ftp/client/Authority.kt:16-21`), SFTP=host·port·username, SMB=host·port·username·domain, WebDAV=protocol·host·port·username. 같은 기계라도 사용자 이름이 다르면 다른 서버로 본다.

**2) 작업 단계의 대체 — `FileJobs.kt`.** 위와 별개로 이동 작업 자체에도 대체가 있다.
- `MoveFileJob.run`(`FileJobs.kt:1423-1446`)은 먼저 `moveAtomically`(`:1527-1529`, `ATOMIC_MOVE`, 바꾸기 없음)를 시도하고, **어떤 `IOException`이든** 잡아 `moveRecursively`로 넘긴다(`:1431-1435`).
- `moveRecursively`(`:1449-1514`)는 원본 트리를 걸으며 폴더마다 `moveAtomically`를 다시 시도하고, 실패하면 `copyForMove`(`:1516-1525`, `useCopy=true`)로 대상에 폴더를 만들고 안으로 들어간다(`:1462-1472`). 파일마다 `moveAtomically` 실패 시 `moveByCopy`(`:1532-1540`, `useCopy=false`)로 제공자 `move()`(비원자)를 불러 1)의 대체를 탄다(`:1478-1488`). 이때 제공자 `move()`가 rename을 한 번 더 시도하므로, 같은 서버 파일은 원자 시도 포함 rename이 두 번 불린다(무해, 검수 지적 3). 폴더는 자식 처리 뒤 `postVisitDirectory`에서 지운다(`:1503-1512`).
- 따라서 같은 서버에서 폴더 rename이 거절되면, 지금은 "대상에 폴더 만들기 → 파일마다 rename 재시도 → 거절되면 복사+삭제 → 원본 폴더 삭제"가 된다. 원자 시도에서 대상이 이미 있는 경우(`FileAlreadyExistsException`)도 같은 길로 들어가 충돌 창(`copyOrMove`의 `FileAlreadyExistsException` 처리, `:1675-`)과 폴더 합치기가 여기서 처리된다 — 이 길은 유지해야 한다.
- 원자 이동(`ATOMIC_MOVE`)일 때 제공자 `move()`는 이미 대체하지 않고 오류를 던진다(예: FTP `:177-179`, SMB `:209-213`).

**3) '바꾸기' 이동의 손실 경로.** 충돌 창에서 "교체"를 고르면 `REPLACE_EXISTING`으로 제공자 `move()`를 다시 부른다(`FileJobs.kt:1695`, `:1719`). 제공자는 대상을 먼저 지우고(1)의 "대상 선삭제" 열) rename한다. rename이 거절되면 지금은 복사로 대신하지만, 요구사항 1로 대체를 없애면 **기존 대상은 지워졌는데 원본은 옮겨지지 않은** 상태로 끝난다. 요구사항 3이 이를 막는다.

**4) WebDAV 다른 서버 이동(결함 의심).** `Client.move`는 다른 서버면 `IOException`을 던지는데(`webdav/client/Client.kt:114-116`, `try` 밖), `WebDavCopyMove.move`는 `DavException`만 잡는다(`:187`). dav4jvm의 `DavException`은 `java.lang.Exception`의 하위형이고 `IOException`이 아니다(javap 확인). 그래서 같은 scheme의 다른 WebDAV 서버로 파일을 이동하면 제공자 대체로 가지 못하고 오류 창이 뜰 것으로 읽힌다(폴더는 `copyForMove`로 만들어짐). 실기 확인은 하지 못했다(미확인). http↔https처럼 scheme이 다르면 `ForeignCopyMove`로 가므로 해당 없다.

### 1.5 라이브러리 지원 확인 (Gradle 캐시, javap)
| 라이브러리(버전, `app/build.gradle`) | 확인한 것 | 결과 |
|---|---|---|
| sshj 0.40.0(`:167`) | `SFTPClient.rename(String, String, Set<RenameFlags>)`, `RenameFlags{OVERWRITE, ATOMIC, NATIVE}`, `SFTPEngine.rename` 바이트코드 | 프로토콜 v5 이상이면 플래그를 그대로 보낸다. v5 미만(OpenSSH는 v3)에서 `OVERWRITE`를 주면 서버가 `posix-rename@openssh.com`을 알리는 경우 **자동으로 그 확장 요청을 보낸다**. 알리지 않으면 요청을 보내기 **전에** `SFTPException("RENAME-FLAGS are not supported…")`을 던진다(서버 상태 변화 없음). `supportsServerExtension(name, domain)`도 공개되어 있다 |
| smbj 0.11.5(`:160`) | `DiskEntry.rename(String, boolean)`, `rename(String, boolean, long)` 바이트코드 | 둘째 인수가 `FileRenameInformation`의 ReplaceIfExists로 들어간다. 이 앱은 이미 `true`를 넘긴다(`smb/client/Client.kt:469`). 폴더 대상을 덮어쓰는지는 서버 구현에 달림(Windows는 폴더 대상 거절로 알려짐, **미확인**) |
| dav4jvm 02fe1a95(`:116`) | `DavResource.move(HttpUrl, boolean, ResponseCallback)` 바이트코드 | `forceOverride=false`면 `Overwrite: F` 헤더를 붙이고, `true`면 헤더를 붙이지 않는다. RFC 4918 10.6에 따라 헤더가 없으면 `T`로 처리된다. 서버가 실제로 덮어쓰는지·거절하는지는 서버 구현(**미확인**) |
| commons-net 3.13.0(`:184`) | `FTPClient.rename(String, String)`, `features()`, `hasFeature(...)` | RNFR/RNTO만 보낸다. 덮어쓰기 rename 명령이나 그 지원 여부를 알리는 표준 FEAT 항목은 없다(RFC 959는 RNTO 대상이 있을 때를 정하지 않음). 기존 파일 위로의 RNTO는 서버마다 다르다(덮어쓰는 서버·거절하는 서버가 있다고 알려짐, 서버별 **미확인**) |

### 1.6 용어
| 용어 | 뜻 |
|---|---|
| 같은 서버 | 원본과 대상의 제공자 종류가 같고 `Authority`가 같은 경우. SMB는 추가로 **같은 공유**여야 한다(다른 공유는 SMB rename이 원리상 불가해 "다른 저장소"로 본다, 1.4-1(나)) |
| 서버 rename | FTP RNFR/RNTO, SFTP `SSH_FXP_RENAME`(또는 `posix-rename@openssh.com`), SMB `FileRenameInformation`, WebDAV `MOVE` |
| 대체(fallback) | rename 실패 뒤 복사(내려받기·올리기) + 원본 삭제로 다시 시도하는 것 |
| 바꾸기 이동 | 충돌 창 "교체"로 `REPLACE_EXISTING`을 붙인 이동 |

## 2. 기능 요구사항
| ID | 요구사항 | 우선순위 | 검증 방법 |
|---|---|---|---|
| REQ-95-01 | FTP·SFTP·SMB·WebDAV의 같은 서버(1.6) 안 이동에서, 제공자 `move()`는 서버 rename이 실패하면 그 오류를 던져야 하며 복사+원본 삭제로 다시 시도하지 않아야 한다. 대상이 이미 있는 경우의 `FileAlreadyExistsException`(바꾸기 아님)은 지금처럼 던진다 | 필수 | 코드 검사: 네 `move()`에서 같은 서버 rename 실패 후 `copy(`·원본 삭제 호출로 가는 경로가 없다. JVM 단위 테스트(가짜 클라이언트로 가능하면): rename 실패 시 copy·delete 호출 없음, 던진 예외가 rename 예외 |
| REQ-95-02 | 이동 작업(`MoveFileJob`)에서 네 백엔드의 같은 서버 항목은 rename이 대상 존재 외의 이유로 실패하면 폴더 만들기·자식별 재시도·복사로 넘어가지 않고, 그 항목에 대해 지금의 복사·이동 오류 창(`askCopyError`, 재시도·건너뛰기·취소, `FileJobs.kt:1781-`)을 보여야 한다. 원본은 그대로 남는다. (해석: 요구사항 1의 "알리고 끝낸다"를 **항목 단위**로 읽었다 — "건너뛰기"를 고르면 나머지 항목은 계속된다. 작업 전체를 멈추는 뜻이면 3장 Q3에서 오너가 정한다) | 필수 | 코드 검사: 같은 서버 네트워크 항목의 rename 실패가 `copyForMove`·`moveByCopy`의 복사로 이어지지 않는다. 수동(서버 있을 때, 미확인): 이동 권한이 없는 폴더를 같은 서버 다른 폴더로 이동 → 오류 창, 대상에 빈 폴더·복사본이 생기지 않음, 원본 유지 |
| REQ-95-03 | 사용자에게 보이는 오류는 rename 자체의 오류(서버 응답에서 온 메시지)여야 한다. 뒤이은 복사 오류로 가려지면 안 된다 | 필수 | 코드 검사: 오류 창에 넘기는 예외가 rename 호출의 예외. 단위 테스트: 예외 메시지 보존 |
| REQ-95-04 | 같은 서버에서 대상이 이미 있을 때의 흐름(충돌 창, 폴더 합치기 — 자식을 하나씩 rename하고 빈 원본 폴더를 지움, 다른 이름으로)은 지금처럼 동작해야 한다 | 필수 | 코드 검사: `FileAlreadyExistsException` 경로 불변. 수동(미확인): 같은 이름 폴더가 있는 곳으로 같은 서버 이동 → 합치기 창 → 합치기 후 원본 폴더 없어짐 |
| REQ-95-05 | 다른 서버(1.6의 같은 서버가 아닌 경우, SMB 다른 공유 포함) 사이 이동은 네 백엔드 모두 복사 뒤 원본 삭제로 동작해야 한다. WebDAV의 다른 서버 이동(1.4-4)도 여기에 맞아야 한다 | 필수 | 코드 검사: 다른 `Authority`의 실패가 대체 경로로 간다(WebDAV는 예외형 문제 해소). 단위 테스트: 다른 서버 판정 시 copy+delete 호출 |
| REQ-95-06 | 바꾸기 이동에서 앱은 서버 rename이 성공하기 **전에** 기존 대상을 지우지 않아야 한다(#55 원칙). 3장 Q1 (가) 결정대로 서버의 덮어쓰기 rename을 쓰고, 서버가 거절하면 오류(REQ-95-02의 오류 창)로 끝내며 기존 대상과 원본은 둘 다 그대로 남는다. 덮어쓰기 rename(SFTP `OVERWRITE`, WebDAV `Overwrite` 생략, SMB ReplaceIfExists)은 **바꾸기 이동(`replaceExisting`)일 때만** 써야 한다 — 일반 이동에 쓰면 존재 확인과 rename 사이에 생긴 대상을 조용히 덮어쓴다(4장 경쟁 조건). SMB는 지금 일반 이동에도 `true`를 넘기므로(`smb/client/Client.kt:469`) 이 조건에 맞춘다 | 필수(Q1 (가) 확정) | 코드 검사: 네 `move()`의 바꾸기 경로에 rename 전 대상 삭제 호출이 없다. 덮어쓰기 플래그가 `replaceExisting`일 때만 켜진다. 단위 테스트: rename 실패 시 대상 delete 호출 없음 |
| REQ-95-07 | 폰 안 이동(로컬·루트·SAF·압축)과 공급자 간 이동(`ForeignCopyMove`)의 동작은 바뀌지 않아야 한다 | 필수 | 코드 검사: `FileJobs.kt` 변경이 네 백엔드의 같은 서버 판정에만 걸린다. 기존 단위 테스트 통과 |
| REQ-95-08 | 취소(`InterruptedIOException`)는 지금처럼 즉시 작업을 끝내야 한다 | 필수 | 코드 검사: 기존 `InterruptedIOException` 재던지기 유지 |

## 3. 오너 결정이 필요한 점

> **결정(2026-11-20)**: Q1 **(가)** 서버 덮어쓰기 rename, 앱은 기존 대상을 먼저 지우지 않는다(오너). Q3 **(가)** 항목 단위, 기존 오류 창, 새 문구 없음(오너). Q2 WebDAV 다른 서버 결함은 **범위 안**(REQ-95-05, PM). 아래 선택지는 기록으로 남긴다.

### Q1 — 바꾸기 이동에서 기존 대상을 어떻게 다룰까 (요구사항 3, REQ-95-06)

| 백엔드 | (가) 서버 덮어쓰기 rename에 맡김 **[추천]** | (나) 지우고 rename(지금 순서 유지) | (다) 덮어쓰기 rename이 없는 서버에선 바꾸기 이동 거절 | (라) 대상을 임시 이름으로 치운 뒤 rename |
|---|---|---|---|---|
| SMB | smbj ReplaceIfExists(이미 `true`). 선삭제만 없앤다. 서버가 한 번에 바꾼다 | 가능 | 해당 없음(지원함) | 가능 |
| WebDAV | `move(url, true)` → `Overwrite` 헤더 생략(=T). 서버가 바꾼다 | 가능 | 해당 없음(지원함) | 가능 |
| SFTP | `rename(…, OVERWRITE)` → 서버가 `posix-rename@openssh.com`을 알리면 그걸로 바꾼다(OpenSSH는 알림). 알리지 않는 v3 서버는 sshj가 보내기 전에 실패 → 오류, 대상 유지 | 가능 | 확장이 없는 서버면 거절(결과는 (가)와 같음) | 가능 |
| FTP | 기존 파일 위로 바로 RNTO. 덮어쓰는 서버는 성공, 거절하는 서버는 오류·대상 유지. 미리 알 방법 없음 | 가능 | 미리 알 수 없어 **FTP 바꾸기 이동은 항상 거절**하게 됨 | 가능 |

사용자에게 의미하는 것:
- **(가)** 기존 대상은 서버가 새 파일로 바꿀 때만 사라진다. 어떤 서버(덮어쓰기를 거절하는 FTP 서버, 확장 없는 오래된 SFTP 서버, 폴더 대상을 거절하는 SMB 서버 등)에서는 바꾸기 이동이 오류로 끝나고, 사용자는 대상을 직접 지우거나 "다른 이름"·"건너뛰기"를 골라야 한다. 데이터 손실 없음. 새 선택지·문구 불필요(오류 창은 서버 메시지를 보여 줌. 더 친절한 문구는 선택 사항).
- **(나)** 지금처럼 대부분 서버에서 바꾸기 이동이 된다. 하지만 대체가 없어지므로 rename이 거절되면 **기존 대상은 지워지고 원본은 그대로**인 상태로 끝난다(#54 경고 "잃을 수 있다"가 실제로 일어남). #55 원칙과 충돌.
- **(다)** 안전하지만 FTP에서는 바꾸기 이동을 아예 못 한다(덮어쓰기를 지원하는 FTP 서버에서도). SMB·WebDAV·OpenSSH SFTP 결과는 (가)와 같다.
- **(라)** 덮어쓰기 rename이 없는 서버에서도 바꾸기가 된다: 대상을 `이름.임시`로 rename → 원본을 대상 이름으로 rename → 성공하면 임시본 삭제, 실패하면 임시본을 원래 이름으로 되돌린다. 되돌리기·임시본 삭제가 실패하면 임시 이름 파일이 서버에 남는다(손실은 아님). rename을 3번 하므로 느리고, 작업 중 다른 기기에서 임시 이름이 보인다. #42에서 오너가 거절한 "임시 이름 교체"(복사용)와 비슷한 방식이라 같은 판단이 적용될 수 있다.

**추천: (가)**, 네 백엔드 모두. 이유: 오너의 #95 판단("거절하는 서버에 우회 재시도는 의미 없음")과 #55 원칙("원인을 모르면 지우지 않는다")에 모두 맞고, 코드가 가장 작다(SMB는 이미 지원, WebDAV·SFTP는 인수 하나). 바꾸기 이동이 안 되는 서버가 생기는 대가는 오류로 알려지고 데이터를 잃지 않는다.

### Q2(확인) — WebDAV 다른 서버 이동(1.4-4)
요구사항 2("지금처럼 복사 뒤 원본 삭제")는 WebDAV에서 지금도 그렇게 동작한다는 전제인데, 코드상으로는 같은 scheme의 다른 WebDAV 서버 이동이 오류가 되는 것으로 읽힌다(미확인). REQ-95-05는 이를 고쳐 네 백엔드를 같게 하는 쪽으로 썼다. PM이 범위 안으로 볼지 별도 이슈로 뺄지 정해 주면 된다(추천: 범위 안 — 같은 `move()` 안의 몇 줄이고 요구사항 2를 지키는 데 필요).

### Q3 — "오류를 알리고 끝낸다"의 단위 (REQ-95-02)
- (가) **항목 단위 [추천]**: 실패한 항목에 지금의 오류 창(재시도·건너뛰기·취소)을 띄운다. 건너뛰면 나머지 항목은 계속 옮긴다. 복사 오류와 같은 창이라 새 문구·버튼이 없다.
- (나) 작업 단위: 첫 rename 실패에서 작업 전체를 멈추고 오류를 알린다. 여러 개를 옮길 때 나머지가 하나도 안 옮겨진다.

## 4. 비기능 요구사항
- 지원 API: minSdk 23~ 그대로. 새 권한·새 라이브러리 없음(1.5의 기존 API만).
- 루트/비루트 무관(네트워크 백엔드).
- 성능: 같은 서버 이동 실패가 즉시 오류가 되므로 지금보다 빠르거나 같다. 불필요한 데이터 전송이 없어진다.
- 동시 작업: 서버 상태를 미리 조회한 뒤 rename하는 지금 구조의 경쟁 조건(조회와 rename 사이 다른 기기의 변경)은 그대로다(새로 만들지 않는다).

## 5. 제약과 가정
- **실서버 시험 불가**: 이 작업에서 쓸 수 있는 FTP·SFTP·SMB·WebDAV 서버가 없다. 검증은 코드 검사와 JVM 단위 테스트(가짜 클라이언트를 꽂을 수 있는 범위)로 하고, 수동 항목은 "미확인"으로 남긴다. 오너 기기 설치가 필요하면 미리 묻는다.
- 서버별 덮어쓰기 동작(1.5의 미확인 항목)은 문서·코드로만 판단했다. (가)를 고르면 그 차이는 "성공 또는 오류(손실 없음)"로만 나타난다는 것이 이 설계의 전제다.
- "rename을 막은 서버는 삭제도 막는다"는 오너 판단을 전제로 한다. 그렇지 않은 서버(rename만 막고 쓰기·삭제는 허용)에서는 지금 되던 이동이 오류가 된다 — 오너가 받아들인 결과다(2026-11-20).
- 같은 서버 판정은 제공자 종류와 `Authority`(SMB는 공유 포함) 비교로 충분하다고 가정한다. 같은 기계를 다른 주소·사용자로 연결한 경우는 다른 서버로 보고 복사+삭제한다(지금과 같음).

## 6. 규격 근거 (부록 S)
| 조항 | 요지 | 필수/권장 | 충족 방법 |
|---|---|---|---|
| RFC 959 4.1.3 RNFR/RNTO | 이름 바꾸기. 대상이 있을 때의 동작은 정하지 않음 | 해당 | FTP는 서버 응답을 그대로 따른다(Q1 (가)) |
| draft-ietf-secsh-filexfer-02(SFTP v3) 6.5 | `SSH_FXP_RENAME`; 대상이 있으면 오류 | 해당 | 덮어쓰기는 `posix-rename@openssh.com`(OpenSSH PROTOCOL 문서) 경유, sshj가 처리 |
| MS-FSCC 2.4.37 FileRenameInformation | ReplaceIfExists 플래그 | 해당 | smbj `rename(path, true)` |
| RFC 4918 9.9 MOVE, 10.6 Overwrite | `Overwrite` 없으면 T. T면 서버가 대상 삭제 후 이동 | 해당 | dav4jvm `move(url, true)` |

## 7. 요구사항 추적
| 요구사항 정리 v1 | REQ |
|---|---|
| 1. 같은 서버는 rename만, 실패면 오류 | REQ-95-01, 02(3장 Q3), 03, 04, 08 |
| 2. 다른 서버는 복사 뒤 삭제 | REQ-95-05 |
| 3. 바꾸기 이동에서 짐작으로 지우지 않기 | REQ-95-06, 3장 Q1 |
| 4. 폰 안 이동은 지금과 같음 | REQ-95-07 |

## 8. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | 1.0 | 최초 작성 | 요구사항 정리 v1 | PM |
| 2026-11-20 | 1.1 | REQ-95-06에 덮어쓰기 플래그는 바꾸기 이동에서만, REQ-95-02에 항목 단위 해석 명시와 Q3 추가, 1.4-2에 rename 이중 시도 메모 | SRS 검수 조건부 통과 경미 지적 1~3(PR #96) | 검수팀 |
| 2026-11-20 | 1.2 | 3장에 결정 기록(Q1 (가), Q3 (가), Q2 범위 안), REQ-95-06 우선순위 칸 갱신 | 오너 결정 "둘 다 (가)"(#95 코멘트), PM Q2 | PM(오너 결정 전달) |
| 2026-11-20 | 1.3 | REQ-95-06 본문의 조건문을 확정 문장으로, 1.3의 "Q1 결정에 따라"를 Q3 결정으로 | HLD 검수 지적 3(PR #96) | 검수팀 |
