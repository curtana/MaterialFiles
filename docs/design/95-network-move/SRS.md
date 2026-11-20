# SRS — 네트워크 이동·이름 바꾸기: rename과 MOVE 구분, 바꾸기 이동의 대상 선삭제 없애기 (이슈 #95)

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v2.4) |
| 근거 | 요구사항 정리 v1(이슈 #95) + 오너 결정 2026-11-20 3건(#95 코멘트): "네 개 모두", "둘 다 (가)"(Q1·Q3), **rename/MOVE 구분**(Q4 대체, 아래 인용), **"사본 남기기 (추천)"**(원본 삭제 실패 시), **"모든 이동에 적용"**(REQ-95-09 범위) + PM 결정(Q2) + #55 원칙(원인을 모르면 지우지 않는다) |
| 기준 코드 | `origin/develop` 9374221. 줄 번호는 이 커밋 기준, 경로는 `app/src/main/java/me/zhanghai/android/files/` 기준 |
| 관련 | #55(복사 원본 확인 뒤 대상 삭제, C1/C2), #54(바꾸기 경고), #47, #42 |

> 오너 2026-11-20: "MOVE와 rename을 구분했으면 하는데 같은 폴더 내에서의 변경은 rename이고 다른 폴더로 이동하는 것은 MOVE야. rename의 경우는 시도해서 실패하면 끝이지만 MOVE는 파일을 다운 받으면서 대상 폴더에 동시에 기록해야 하는 것이 너무 당연한 거야. 이걸 시스템이 제공 안 한다고 하지 않는 것은 말이 안 되는 경우야!"

## 1. 목적과 범위

### 1.1 목적
FTP·SFTP·SMB·WebDAV에서 두 동작을 구분한다.
- **이름 바꾸기(rename)**: 같은 폴더 안에서 이름만 바꾼다. 서버 rename만 쓰고, 실패하면 오류로 끝난다.
- **이동(MOVE)**: 다른 폴더로 옮긴다(같은 서버든 아니든). 먼저 서버 rename/move를 시도하고, 서버가 해 주지 않으면(어떤 실패든) 내려받으면서 동시에 대상에 쓰는 스트리밍 복사 뒤 원본 삭제로 대신한다. 지금의 대체 동작을 유지한다.

여기에 더해, '바꾸기' 이동이 서버 rename 전에 기존 대상을 지우는 문제(rename도 대체 복사도 실패하면 기존 대상만 사라짐)를 없앤다(Q1 (가)).

### 1.2 범위
- 이름 바꾸기 경로가 네 백엔드에서 대체 없이 끝나는지 확인(1.4-1). 대체가 있으면 고친다 — 확인 결과 없음.
- 네 백엔드 `*CopyMove.move()`의 바꾸기 처리(대상 선삭제 제거, 서버 덮어쓰기 rename, 실패 시 #55 순서의 대체 복사).
- SMB rename의 ReplaceIfExists를 바꾸기 이동일 때만 켜기.
- WebDAV 다른 서버 이동 결함(1.4-4, REQ-95-06).
- 복사로 대신하는 **모든** 이동의 "원본 삭제 실패 시 되돌리기" 제거(REQ-95-09): 네트워크 네 백엔드, 공급자 간 이동, 로컬·루트, 문서 제공자(SAF). 압축은 읽기 전용이라 이동 대상이 아니다.

### 1.3 범위 밖
- 폰 안 이동(로컬·루트·SAF·압축)과 공급자 간 이동(`ForeignCopyMove`)의 나머지 동작: 지금과 같다. 바뀌는 것은 REQ-95-09(원본 삭제 실패 시 사본 유지)뿐이다. 이들 경로의 바꾸기 시 대상 선삭제(예: `LinuxCopyMove.kt:266-270`, `DocumentCopyMove.kt:94-103`)는 이 작업에서 다루지 않는다.
- 이동의 대체 경로 자체(층 1 제공자 `move()`의 rename 실패 → 복사+삭제, 층 2 `FileJobs.kt` `moveRecursively`의 폴더 만들기·자식별 처리): **유지**(오너 2026-11-20). v1.x의 "같은 서버는 대체 없음" 요구는 철회.
- 서버 안 복사를 서버가 하게 하는 개선(단 SMB는 이미 같은 세션이면 서버 쪽 복사를 쓴다, 1.5).
- 충돌·오류 창의 버튼·문구(Q3 (가), 새 문구 없음).

### 1.4 현재 동작 (코드 읽기)

**1) 이름 바꾸기 — 대체 없음(확인).** `RenameFileJob.run`(`filejob/FileJobs.kt:1917-1923`) → `rename`(`:1926-`) → `moveAtomically`(`:1527-1529`, `LinkOption.NOFOLLOW_LINKS`, `StandardCopyOption.ATOMIC_MOVE`, `REPLACE_EXISTING` 없음). 실패하면 이름 바꾸기 오류 창(`:1943-`)을 띄울 뿐 복사로 가지 않는다. 제공자 쪽 원자 경로:

| 백엔드 | 대상이 있으면 | rename 실패 시(원자) | 대체 코드 앞의 차단 |
|---|---|---|---|
| FTP | `FtpCopyMove.kt:163-165` `FileAlreadyExistsException`(이름 바꾸기는 `REPLACE_EXISTING`을 붙이지 않으므로 여기서 끝나고, 선삭제 블록 `:166-170`에 닿지 않음) | `:177-179` 변환해 던짐 | `:186-188` `AssertionError` |
| SFTP | `SftpCopyMove.kt:272-274` 같음 | `:286-288` | `:295-297` |
| SMB | `SmbCopyMove.kt:195-197` 같음 | `:209-213`(`maybeThrowAtomicMoveNotSupportedException`·`maybeThrowInvalidFileNameException` 포함) | `:220-222` |
| WebDAV | `WebDavCopyMove.kt:174-176` 같음 | `:188-190` | `:197-199` |

네 백엔드 모두 원자 이동에서는 rename 실패가 곧바로 예외이고 `copy()`에 닿지 않는다. 이름 바꾸기는 같은 폴더라 같은 `Authority`이므로 1.4-4의 WebDAV 결함과도 무관하다. → 이름 바꾸기에는 코드 변경이 필요 없다.

**2) 이동 — 두 층의 대체(유지).**
- 층 1: 네 `move()`가 rename을 시도하고, 원자가 아니면 실패를 무시하고 `copy()` 뒤 원본 삭제로 간다(FTP `FtpCopyMove.kt:172-208`, SFTP `SftpCopyMove.kt:281-`, SMB `SmbCopyMove.kt:204-`, WebDAV `WebDavCopyMove.kt:183-219`).
- 층 2: `MoveFileJob.run`(`FileJobs.kt:1423-1446`)이 원자 시도 실패 시 `moveRecursively`(`:1449-1514`)로 가서, 폴더는 `copyForMove`(`:1516-1525`)로 대상 폴더를 만들고 들어가며, 파일은 `moveByCopy`(`:1532-1540`)로 층 1의 비원자 `move()`를 부른다. 폴더는 자식 처리 뒤 `postVisitDirectory`에서 지운다.
- 다른 서버면 각 Client가 rename 요청 전에 실패시키고(FTP `ftp/client/Client.kt:207-209`, SFTP `sftp/client/Client.kt:99-103`, SMB `smb/client/Client.kt:449-456`(다른 공유 포함), WebDAV `webdav/client/Client.kt:114-116`), 층 1이 그 실패를 받아 대체로 간다(WebDAV 제외, 1.4-4).

**3) 바꾸기 이동의 손실 경로(고칠 대상).** 충돌 창 "교체" → `REPLACE_EXISTING`으로 다시 `move()`(`FileJobs.kt:1695`, `:1719`). 네 `move()`가 rename **전에** 대상을 지운다(FTP `:166-170`, SFTP `:275-279`, SMB `:198-202`, WebDAV `:177-181`). 그 뒤 rename과 대체 복사가 모두 실패하면 기존 대상만 사라진다. 대체 복사가 쓰는 `copy()`는 #55 C1/C2로 이미 "원본을 연(확인한) 뒤에 대상 삭제" 순서를 갖고 있지만(`provider/common/SourceCheckedReplace.kt:24` `replaceAfterOpeningSource`, `:57` `replaceAfterCheckingSource`), `move()`의 선삭제가 그 앞에서 대상을 먼저 지우므로 그 보호가 효과가 없다.

**4) WebDAV 다른 서버 이동 결함.** `Client.move`가 다른 서버면 `try` 밖에서 `IOException`을 던지는데(`webdav/client/Client.kt:114-116`), `WebDavCopyMove.move`는 `DavException`만 잡는다(`:187`). dav4jvm `DavException`은 `java.lang.Exception` 하위형이다(javap). 그래서 같은 scheme의 다른 WebDAV 서버로의 파일 이동은 대체로 가지 못하고 오류가 된다(코드 읽기, 실기 미확인, v1.1 검수에서 확인).

### 1.5 대체 복사가 스트리밍인지 (확인)
일반 파일의 대체 복사는 네 백엔드 모두 원본 입력 스트림을 열고 읽는 대로 대상 출력 스트림에 쓴다. 전체를 먼저 내려받지 않는다. 공통 복사 루프는 `provider/common/InputStreamExtensions.kt:15-39`(`DEFAULT_BUFFER_SIZE` 버퍼로 read → write 반복).

| 백엔드 | 원본 | 대상 | 비고 |
|---|---|---|---|
| FTP | `FtpCopyMove.kt:82` `Client.retrieveFile` → `ftp/client/Client.kt:222-232` `retrieveFileStream`(데이터 연결 스트림) | `FtpCopyMove.kt:97` `Client.storeFile` → `Client.kt:309-322` `storeFileStream` | 같은 서버라도 연결 풀(`Client.kt:41`, `acquireClient` `:46-67`)에서 연결을 따로 얻어 동시에 연다. 루프 `FtpCopyMove.kt:103-106` |
| SFTP | `SftpCopyMove.kt:97-102` `Client.openByteChannel(READ)` → `newInputStream()` | `:118-121` `openByteChannel(WRITE·TRUNC·CREAT)` → `newOutputStream()` | 루프 `:122-125` |
| SMB | `smb/client/Client.kt:326-` `copyFile` | 같음 | 같은 세션이면 `sourceFile.serverCopy(…)`(`:409`) — **서버 안 복사, 폰을 거치지 않음**. 다른 세션이면 `FileByteChannel` 스트림 루프(`:413-418`) |
| WebDAV | `WebDavCopyMove.kt:87` `Client.get` → `webdav/client/DavResourceCompat.kt:34-35` `body.byteStream()` | `WebDavCopyMove.kt:102` `Client.put` → `DavResourceCompat.kt:64-` `putCompat`(okio `Pipe` `:70`, one-shot `RequestBody`) | 쓰는 쪽이 파이프로 업로드 요청 본문에 바로 흘러간다. 루프 `WebDavCopyMove.kt:108-111` |

### 1.6 라이브러리 지원 (Gradle 캐시 jar, javap — v1.1 검수에서 재확인됨)
| 라이브러리 | 결과 |
|---|---|
| sshj 0.40.0 | `SFTPClient.rename(String, String, Set<RenameFlags>)`. `OVERWRITE`면 v5+는 플래그, v3에서 서버가 `posix-rename@openssh.com`을 알리면 그 확장, 아니면 요청 전에 `SFTPException`(서버 상태 변화 없음) |
| smbj 0.11.5 | `DiskEntry.rename(String, boolean replaceIfExists)`. 앱은 지금 항상 `true`(`smb/client/Client.kt:469`) |
| dav4jvm 02fe1a95 | `DavResource.move(HttpUrl, forceOverride, cb)`: `false`면 `Overwrite: F`, `true`면 헤더 생략(RFC 4918 10.6에 따라 T) |
| commons-net 3.13.0 | `FTPClient.rename`(RNFR/RNTO)만. 기존 파일 위 RNTO는 서버마다 다름(미확인) |

### 1.7 용어
| 용어 | 뜻 |
|---|---|
| 이름 바꾸기 | 이름 바꾸기 동작(`RenameFileJob`). 같은 폴더 안 |
| 이동 | 이동 작업(`MoveFileJob`). 다른 폴더로 |
| 대체 | 이동에서 서버 rename 실패 뒤 스트리밍 복사 + 원본 삭제 |
| 바꾸기 이동 | 충돌 창 "교체"로 `REPLACE_EXISTING`을 붙인 이동 |
| 덮어쓰기 rename | SFTP `OVERWRITE`, SMB ReplaceIfExists, WebDAV `Overwrite` 생략(=T), FTP 기존 파일 위 RNTO |

## 2. 기능 요구사항
| ID | 요구사항 | 우선순위 | 검증 방법 |
|---|---|---|---|
| REQ-95-01 | 네 백엔드의 이름 바꾸기는 서버 rename만 써야 하고, 실패하면 이름 바꾸기 오류 창으로 끝나야 한다(복사로 대신하지 않음) | 필수 | 코드 검사: 1.4-1 표의 원자 경로가 유지된다(변경 없음). 이 작업의 변경이 원자 경로에 대체를 만들지 않는다 |
| REQ-95-02 | 네 백엔드의 이동은 먼저 서버 rename/move를 시도하고, 실패하면(원인 무관, 같은 서버 포함) 스트리밍 복사(1.5) 뒤 원본 삭제로 대신해야 한다 — 지금 동작 유지 | 필수 | 코드 검사: 층 1·층 2의 대체 경로가 남아 있다. 회귀: 기존 테스트 통과 |
| REQ-95-03 | 바꾸기 이동에서 `move()`는 서버 rename 시도 **전에** 기존 대상을 지우지 않아야 한다 | 필수 | 코드 검사: 네 `move()`의 rename 앞에 대상 삭제 호출이 없다. 단위 테스트(가능하면): 순서 |
| REQ-95-04 | 바꾸기 이동의 서버 rename은 덮어쓰기 rename(1.7)이어야 한다. 덮어쓰기는 **바꾸기 이동이고 대상이 있을 때만** 켠다. 일반 이동과 이름 바꾸기는 덮어쓰지 않는 rename을 쓴다. SMB는 지금 항상 켜는 ReplaceIfExists를 이 규칙에 맞춘다 | 필수 | 코드 검사: 덮어쓰기 인수가 `replaceExisting && 대상 있음`에서만 참. SFTP 3인수 호출, SMB·WebDAV 인수 전달 |
| REQ-95-05 | 덮어쓰기 rename이 실패하면 대체 복사로 가며, 그 복사는 #55 C1/C2 순서(원본을 열거나 확인한 뒤에 기존 대상 삭제)를 따라야 한다. 원본을 열 수 없으면 기존 대상은 남는다 | 필수 | 코드 검사: 대체가 `copy()`의 `replaceAfterOpeningSource`/`replaceAfterCheckingSource` 경로로 가고 그 전에 다른 삭제가 없다 |
| REQ-95-06 | WebDAV의 다른 서버 이동(같은 scheme, 다른 `Authority`)도 REQ-95-02대로 대체로 가야 한다(1.4-4 결함 해소) | 필수 | 코드 검사: 다른 서버 실패가 `WebDavCopyMove.move`의 `catch`에 걸린다 |
| REQ-95-07 | 남는 오류(대체 복사 실패, 원본 삭제 실패 등)는 지금처럼 항목 단위 오류 창(`askCopyError`, 재시도·건너뛰기·취소)으로 처리한다. 새 문구 없음 | 필수 | 코드 검사: `FileJobs.kt` 오류 처리 불변 |
| REQ-95-09 | 복사로 대신하는 모든 이동에서 복사가 성공한 뒤 원본 삭제가 실패하면(`NoSuchFileException`·`ENOENT` 제외 — 지금도 되돌리지 않음), 대상의 복사본을 지우지 않고 남긴 채 원본 삭제 오류를 던져야 한다. 결과: 대상에 새 내용, 원본도 남음(사용자가 정리). 대상: 네트워크 네 백엔드 `move()`(FTP `FtpCopyMove.kt:199-208`, SFTP `SftpCopyMove.kt:308-317`, SMB `SmbCopyMove.kt:233-242`, WebDAV `WebDavCopyMove.kt:210-219`), 공급자 간 이동 `ForeignCopyMove.move`(`provider/common/ForeignCopyMove.kt:164-186`, `IOException` 분기 `:167-175`와 `UnsupportedOperationException` 분기 `:178-184` 둘 다), 로컬·루트 `LinuxCopyMove.move`(`provider/linux/LinuxCopyMove.kt:299-310`, 대상 삭제 `:302-308`; 루트는 `root/RootableFileSystemProvider.kt:104-106` → 루트 프로세스의 `LocalLinuxFileSystemProvider.kt:225` → 같은 함수), 문서 제공자(SAF) `DocumentResolver.moveByCopy`(`provider/document/resolver/DocumentResolver.kt:374-383`, 대상 삭제 `:375-382`). 각 경로의 원본 삭제 실패 `catch` 안의 대상 삭제를 없애고 던지는 예외는 지금과 같게 둔다. 오류는 REQ-95-07의 항목 단위 오류 창으로 보인다 | 필수 | 코드 검사: 위 일곱 곳의 원본 삭제 `catch` 안에 대상 삭제 호출이 없고 원본 삭제 예외를 그대로 던진다. 단위 테스트(가능하면, LLD에서 판단: `ForeignCopyMove`는 기존 `ForeignCopyMoveCleanupTest`처럼 JVM 시험 가능) |
| REQ-95-08 | 폰 안 이동, 공급자 간 이동, 취소(`InterruptedIOException`) 동작은 REQ-95-09 밖에서는 바뀌지 않아야 한다 | 필수 | 코드 검사, 기존 JVM 테스트·`assembleDebug` |

## 3. 결정 기록
| 질문 | 결정 | 출처 |
|---|---|---|
| 적용 백엔드 | FTP·SFTP·SMB·WebDAV 넷 | 오너 2026-11-20 "네 개 모두" |
| Q1 바꾸기 이동의 기존 대상 | (가) 서버 덮어쓰기 rename, 앱은 먼저 지우지 않음. 실패하면 대체 복사는 #55 순서. 대체 복사가 성공한 뒤 원본 삭제가 실패하면 대상의 복사본을 남긴다(REQ-95-09). 바꾸기 이동이면 이때 기존 대상은 새 내용(원본의 사본)으로 바뀌어 있고 원본도 남으므로 내용 손실은 없다 | 오너 "둘 다 (가)", rename/MOVE 구분 코멘트, "사본 남기기 (추천)" |
| Q2 WebDAV 다른 서버 결함 | 범위 안 | PM |
| Q3 오류 단위 | (가) 항목 단위, 기존 오류 창, 새 문구 없음 | 오너 "둘 다 (가)" |
| Q4 같은 서버 구조상 rename 불가 | 해당 없음 — 이동은 항상 대체 | 오너 rename/MOVE 구분 코멘트 |
| v1.x "같은 서버 이동은 대체 없음" | **철회**. 이름 바꾸기만 대체 없음 | 같음 |

## 4. 비기능 요구사항
- minSdk 23~ 그대로. 새 권한·라이브러리·문자열 없음.
- 동시성: 덮어쓰기를 바꾸기 이동에서만 켜므로 일반 이동에서 존재 확인과 rename 사이에 생긴 대상을 조용히 덮어쓰는 경쟁을 만들지 않는다(SMB는 지금 있는 그 경쟁이 없어진다).

## 5. 제약과 가정
- **실서버 시험 불가**: 검증은 코드 검사와 JVM 단위 테스트(가능한 범위)로 하고 수동 항목은 미확인으로 남긴다. 오너 기기 설치가 필요하면 미리 묻는다.
- 서버별 덮어쓰기 동작은 미확인이다. 결과는 "서버가 바꿈" 또는 "rename 실패 → 대체 복사(#55 순서)" 둘이고, 어느 쪽에서도 앱이 원본 확인 전에 대상을 지우지 않는다.
- 대체 복사 성공 뒤 원본 삭제가 실패하면 지금 코드는 대상 복사본을 지운다(되돌리기). 바꾸기 이동이면 그때 기존 대상은 이미 새 내용으로 바뀐 뒤라 복사본까지 지워져 사라진다. REQ-95-09로 되돌리기를 없애 복사본을 남긴다(오너 "사본 남기기").
- 되돌리기는 제공자 `move()`에만 있다. `FileJobs.kt`에는 같은 것이 없다: `moveRecursively`의 폴더 원본 삭제(`postVisitDirectory`, `FileJobs.kt:1500-1510` → `delete()` 헬퍼 `:1348-`)는 실패 시 삭제 오류 처리만 하고 대상을 지우지 않는다. 같은 되돌리기는 공급자 간 이동·로컬·루트·문서 제공자에도 있고, 오너 "모든 이동에 적용"에 따라 모두 REQ-95-09에 넣었다.

## 6. 규격 근거 (부록 S)
| 조항 | 요지 | 필수/권장 | 충족 방법 |
|---|---|---|---|
| RFC 959 4.1.3 RNFR/RNTO | 대상이 있을 때 동작은 정하지 않음 | 해당 | 서버 응답대로. 실패하면 대체 |
| draft-ietf-secsh-filexfer-02 6.5, OpenSSH PROTOCOL `posix-rename@openssh.com` | v3 rename은 대상 있으면 오류, 확장은 POSIX rename | 해당 | sshj `OVERWRITE` |
| MS-FSCC 2.4.37 FileRenameInformation | ReplaceIfExists | 해당 | smbj `rename(path, replace)` |
| RFC 4918 9.9, 10.6 | MOVE, `Overwrite` 기본 T | 해당 | dav4jvm `move(url, overwrite)` |

## 7. 요구사항 추적
| 요구(오너·정리) | REQ |
|---|---|
| 이름 바꾸기는 시도해서 실패하면 끝 | REQ-95-01 |
| 이동은 서버 rename 먼저, 안 되면 스트리밍 복사 뒤 삭제 | REQ-95-02, 06 |
| 바꾸기 이동에서 짐작으로 지우지 않기(Q1 (가)) | REQ-95-03, 04, 05 |
| 남는 오류는 항목 단위(Q3 (가)) | REQ-95-07 |
| 원본 삭제 실패 시 사본 남기기(모든 이동) | REQ-95-09 |
| 폰 안 이동은 지금과 같음 | REQ-95-08 |

## 8. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-20 | 1.0 | 최초 작성 | 요구사항 정리 v1 | PM |
| 2026-11-20 | 1.1 | REQ-95-06에 덮어쓰기 플래그는 바꾸기 이동에서만, REQ-95-02에 항목 단위 해석 명시와 Q3 추가, 1.4-2에 rename 이중 시도 메모 | SRS 검수 조건부 통과 경미 지적 1~3(PR #96) | 검수팀 |
| 2026-11-20 | 1.2 | 3장에 결정 기록(Q1 (가), Q3 (가), Q2 범위 안), REQ-95-06 우선순위 칸 갱신 | 오너 결정 "둘 다 (가)"(#95 코멘트), PM Q2 | PM(오너 결정 전달) |
| 2026-11-20 | 1.3 | REQ-95-06 본문의 조건문을 확정 문장으로, 1.3의 "Q1 결정에 따라"를 Q3 결정으로 | HLD 검수 지적 3(PR #96) | 검수팀 |
| 2026-11-20 | 2.0 | 요구 재정의: 이름 바꾸기(대체 없음, 확인만)와 이동(대체 유지, 같은 서버 포함)을 구분. "같은 서버 이동은 대체 없음" 철회. REQ 재번호(01 이름 바꾸기, 02 이동 대체 유지, 03~05 바꾸기 이동, 06 WebDAV, 07 오류 단위, 08 불변). 1.4-1 원자 경로 확인표, 1.5 스트리밍 확인표 추가, 3장을 결정 기록표로 정리 | 오너 2026-11-20: "MOVE와 rename을 구분했으면 하는데 … rename의 경우는 시도해서 실패하면 끝이지만 MOVE는 파일을 다운 받으면서 대상 폴더에 동시에 기록해야 하는 것이 너무 당연한 거야"(#95 코멘트, Q4 대체) | PM(오너 결정 전달) |
| 2026-11-20 | 2.1 | 1.5 SFTP 줄 범위 정정, 1.4-1 FTP 칸 표현 명확화, 3장 Q1 행에 원본 삭제 실패 예외 명시 | v2.0 검수 조건부 통과 경미 1·3·4(PR #96) | 검수팀 |
| 2026-11-20 | 2.2 | REQ-95-09 추가(대체 뒤 원본 삭제 실패 시 복사본 유지, 되돌리기 제거), 3장 Q1 행·5장 예외 문구를 새 요구로 교체, FileJobs·ForeignCopyMove의 같은 동작 여부 기록, 추적표 갱신 | 오너 원문 2026-11-20: "사본 남기기 (추천)". PM 요약(#95 코멘트): 대체 복사가 성공하고 원본 삭제만 실패하면 대상의 사본을 남기고 원본 삭제 오류를 알린다 | PM(오너 결정 전달) |
| 2026-11-20 | 2.3 | 5장 FileJobs 인용 정리(`postVisitDirectory` `:1500-1510`, `delete()` `:1348-`), 변경 이력 2.2의 오너 원문과 PM 요약 구분 | v2.2 검수 조건부 통과 경미 2건(PR #96) | 검수팀 |
| 2026-11-20 | 2.4 | REQ-95-09를 복사로 대신하는 모든 이동(공급자 간·로컬·루트·SAF 포함, 줄 인용)으로 넓힘, 1.2·1.3·REQ-95-08·5장 갱신, 다른 경로의 바꾸기 선삭제는 범위 밖 명시 | 오너 원문 2026-11-20: "모든 이동에 적용". PM 요약(#95 코멘트): 사본 남기기 규칙(REQ-95-09)을 복사로 대신하는 모든 이동에 적용, `ForeignCopyMove.move` 포함 | PM(오너 결정 전달) |
