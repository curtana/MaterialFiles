# LLD — 앱 안 파일 작업 목록과 취소 (이슈 #36)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과(v1.3, PR #37 6회차 1d0ef5f) |
| 근거 | SRS v2.5, HLD v1.6 (`docs/design/36-job-list/`, PR #37), 요구사항 정리 v2~v7(이슈 #36), #44 PM 분류 |
| 기준 코드 | `origin/develop` a1a8a3e — 인용 줄 번호는 이 커밋 기준. `app/` 트리는 `design/36-job-list`와 같다 |
| 이슈 | #36, #39(흡수, 요구사항 v6), #44(흡수, PM 분류) — feature 브랜치 `feature/36-job-list` 하나 |
| HLD 구성요소 | K1~K20 전부 |
| 추적 REQ | REQ-36-01~19 |
| 파일 경로 약어 | `filejob/`·`filelist/`·`provider/`는 `app/src/main/java/me/zhanghai/android/files/` 아래, `res/`는 `app/src/main/res/` |

## 0. 범위와 전제

### 0.1 이 단계가 하는 것 / 하지 않는 것
- **한다**: 끝난 방식 판정, 작업별 정보·진행 값 통로, 목록 화면, 취소 표시 점검, 정리 실패·결과 모름 통로, 작업 끝 토스트 하나(요구사항 v7, #39 포함), 저장 듣는 함수의 예외 경로(#44).
- **하지 않는다**: 백엔드의 지우기 호출·조건·순서 변경(HLD D5·D10, 표시 지우기 한 줄은 K14 예외). 알림 내용·주기(SRS 1.3). #30 묶음 규칙과 `FileJobBatch`(REQ-36-09). "바꾸기" 선삭제(#42). 시간 초과의 오류 창·다시 시도(요구사항 v6). 원격이 죽은 작업이 끝나지 않는 문제(#43, #36 머지 뒤 별도 PR).

### 0.2 HLD 미결 해소
| ID | 결론 | 절 |
|---|---|---|
| R6 RTL 화살표 | 방향별 문자열 자원 둘을 두고 화면 방향으로 고른다. 이름은 `BidiFormatter`로 감싼다. 확인은 RTL 강제 수동 시험 T7 | 2.12 `titleOf` |
| R9·m5 토스트 | **닫음(요구사항 v5).** 토스트와 줄 표시로 충분하다. T5는 관찰 기록만 한다 | 0.4 |
| L2·L4 결과 모름 | 둘 다 앱이 알아챌 수 있어 알린다. L3만 한계로 남는다 | 0.6 |
| R4·#39 | 흡수. 끝난 방식의 근거를 `isCanceled` 하나로 모으고 끝 토스트에 합친다 | 0.7, 0.8, 2.1, 2.2 |
| R13 줄과 토스트 어긋남 | 닫음. 끝 토스트를 `runningJobs`에서 뺀 뒤로 옮긴다 | 0.8, 2.4 |
| R15 쓰기 창 "취소" | PM 결정 대기. SRS대로 "실패"로 둔다 | 4장 |
| R11 표시 켜진 채 정리 | 다섯 곳에서 표시를 지운다 | 2.14 |
| R12 듣기 해제 시점 | 완료 콜백 또는 죽음. 원격 시작 전 취소의 좁은 창은 잘못 알리는 쪽으로 받아들인다 | 2.17 |
| R10 듣는 함수 | 던지지 않고 기다리지 않는 것을 옵션 한 곳에서 보장한다. 한 번만 부르는 것도 같은 곳이다 | 0.3, 2.17 |
| R1·R2·R3·R5 | 수동 시험에서 관찰·기록한다. 코드 변경 없음. R5(같은 id)는 K8 표·K9 줄 모두 먼저 온 쪽을 유지한다 | 2.8, 2.9, 3.2 |
| K6·K12 표시 규칙 | 줄 상태를 순수 값 `FileJobRowStatus`로 정하고, 화면은 그 값을 문자열 자원으로 바꾸기만 한다 | 2.9 |

### 0.3 R10 — 듣는 함수 안전 (K17)
`IRemoteCallback.sendResult`는 `oneway`가 아니다(`app/src/main/aidl/me/zhanghai/android/files/util/IRemoteCallback.aidl`). 루트 모드에서는 원격의 정리 스레드가 `finally` 안에서 앱의 응답을 기다린다. `RemoteCallback.sendResult`는 `RemoteException`만 잡고(`util/RemoteCallback.kt:23-28`), 앱 쪽 `Stub`은 지역 콜백이 던진 것을 그대로 바인더로 넘긴다(`:34-38`).

| 위험 | 대응 | 확인 |
|---|---|---|
| 듣는 함수의 예외가 `finally`의 원래 예외를 덮는다 | 옵션 생성자에서 `safeOnce`로 감싼다. `Exception`을 잡아 스택만 찍는다. 원격 쪽 `sendResult`도, 앱 쪽 스텁이 부르는 함수도 이 감싼 함수다 | U-K02·U-K03, U-M04 |
| 듣는 함수가 원격 정리를 오래 붙잡는다 | 앱 쪽 함수 `FileJob.onCleanupFailed`가 하는 일은 `synchronized` 기록(입출력 없음, 잠금 안에서 부르는 외부 호출 없음)과 메인으로 넘기기뿐이다. 메인의 끝을 기다리지 않는다 | 코드 검수 체크리스트 3.3 |
| 앱이 죽어 `sendResult`가 실패한다 | `RemoteException`은 `RemoteCallback`이 잡고, 나머지 `RuntimeException`은 `safeOnce`가 잡는다 | 코드 검수, U-K03 |
| 옵션을 다시 만드는 곳에서 듣는 함수를 빠뜨린다 | `CopyOptions` 생성자의 새 인자에 **기본값을 주지 않는다** — 빠뜨리면 컴파일이 안 된다. `toArray`·`toCopyOptions`를 오가도 같은 함수를 넘긴다 | U-K04·U-K05, U-M05 |
| 같은 실패를 두 번 알린다 | 옵션 인스턴스당 한 번(`safeOnce`), 작업·경로당 한 번(`FileJobCleanupFailures`) | U-K01, U-F06 |

`Error`는 잡지 않는다(`OutOfMemoryError` 등은 숨기지 않는다). 변경 범위는 `provider/` 13개 파일이고, 그중 정리 `catch` 수정은 7곳이다(2.17 표).

### 0.4 R9·m5 — 닫음 (요구사항 v5)
오너 결정: 토스트와 줄 표시로 충분하고, 놓치는 경우는 사용자 몫이다. 이력·영구 표시·알림 추가를 만들지 않는다. T5는 어떤 설계도 정하지 않으며 관찰로만 남는다.

| 경우 | 앱 알림 | 앱 위치 | 기록 |
|---|---|---|---|
| T5a | 켬 | 앞 | 토스트 보임 여부, 줄 표시 |
| T5b | 끔 | 앞 | 같음 |
| T5c | 켬 | 뒤(홈으로 나감) | 토스트 보임 여부 |
| T5d | 끔 | 뒤 | 토스트 보임 여부 |

절차는 HLD T3의 SFTP 정리 실패와 같다. 기기의 API 수준을 함께 적어 구현 PR에 싣는다.

### 0.5 #42(미설계)와 겹치는 곳
#42는 아직 설계 전이라 실제 충돌은 그 설계에서 확정된다. 지금 보이는 겹침은 다음과 같다.

| 겹치는 곳 | #36이 하는 일 | #42 설계가 알아야 할 것 |
|---|---|---|
| `LinuxCopyMove.kt:83-123`, `ForeignCopyMove.kt:43-76`, `SftpCopyMove.kt:75-119`, `SmbCopyMove.kt:72-88`·`smb/client/Client.kt:373-408`, `FtpCopyMove.kt:70-88`, `WebDavCopyMove.kt:75-93`, `DocumentResolver.kt:137-171` | 정리 `catch`에 듣는 함수 호출 한 줄, SMB·SAF는 인자 하나 | 같은 함수의 일반 파일 분기를 #42가 고칠 수 있다. 임시 파일 정리로 옮겨도 듣는 함수 호출을 유지해야 REQ-36-16이 산다 |
| `filejob/FileJobs.kt` `copyOrMove`의 `attemptTarget` | 토스트 이름은 작업 쪽이 아는 최종 대상 이름이다 | #42 뒤에는 남는 조각이 임시 이름일 수 있다. 토스트가 가리키는 이름을 #42 설계에서 다시 정해야 한다 |
| `CopyOptions` 생성자 | 인자 추가(기본값 없음) | #42가 옵션을 더하면 같은 일곱 곳이 또 바뀐다 |
| SRS L1 | 문서상 한계로 둔다 | #42가 들어가면 L1이 풀리고, HLD 3.3의 "바꾸기" 문단과 T1 기대를 고쳐야 한다 |

순서 제안(결정은 PM): #36을 먼저 머지하고 #42를 그 위에서 설계한다. #42가 먼저면 위 일곱 곳을 #36 구현이 다시 맞춰야 한다. PR #41(#38)은 `docs/design/30-job-runner/` 문서만 바꾸므로 겹치지 않는다(`gh pr diff 41 --name-only`).

### 0.6 L2·L3·L4 — 앱이 "결과 모름"을 알 수 있는가 (요구사항 v5)
근거는 코드와 smbj 0.11.5·sshj 0.40.0 바이트코드(`javap`)다. 실기는 T12·T13으로 확인한다.

| 한계 | 앱이 알 수 있나 | 근거 | 결론 |
|---|---|---|---|
| L2 루트 원격 프로세스 죽음 | **있다** | 원격 완료 콜백은 취소 뒤에도 정리가 끝난 다음에 온다(`RemoteFileSystemProviderInterface.kt:91-98`). 바인더 죽음은 `linkToDeath`로 알 수 있다(`RemoteInterface.kt:33`이 이미 쓴다) | 완료 콜백 전에 죽으면 듣는 함수를 부른다(2.17). 원격이 죽고 사용자가 취소하지 않으면 작업이 끝나지 않아(#43) 취소할 때 알린다 |
| L4 SMB 닫기 실패 | **있다** | `Open.close`→`Share.closeFileId`→`sendReceive`로 실패를 삼키지 않고, 이미 닫힘은 성공으로 친다(`SUCCESS_OR_CLOSED`) | `deleteOnClose` 성공 뒤 닫기가 던질 때만 알린다(2.17). 닫기 응답이 성공인데 서버가 안 지운 경우만 알 수 없다 |
| L3 앱 프로세스 죽음 | 없다 | 알릴 프로세스가 없다 | 한계로 남는다 |

**SMB 취소마다 "지우지 못함"을 알리지 않는 조건.** smbj `Futures.get`은 `InterruptedException`을 잡으면 끼어들기 표시를 다시 켠다. 그대로 두면 정리의 `deleteOnClose`와 닫기가 곧바로 던져 일반 취소마다 잘못 알린다. 그래서 정리 앞에서 표시를 지운다(2.14). L4는 진짜 예외가 났을 때만 알리므로 PM이 금지한 "확인할 수 없어서 매번 알림"이 아니다. 일반 취소는 v7의 "취소됨" 토스트 하나이고 지우지 못함 줄은 없다. 지운 뒤에도 그 줄이 일반 취소마다 붙는지는 코드로 확정할 수 없어 T13으로 본다. 붙으면 고치지 않고 PM에 올린다.

**예외, SRS L6(PM 수용).** 세션이 다른 SMB 간 복사에서 쓰기 쪽이 끼어들리면 `smb/client/FileByteChannel.kt:105-108`의 `closeSafe()`가 대상 핸들을 먼저 닫아 `deleteOnClose`가 실패한다. 이때는 취소마다 지우지 못함 줄이 날 수 있으나 조각이 실제로 남으므로 진짜 알림이다(코드 읽기 근거, 실기 미확인). 알려진 한계로 두고 `closeSafe`는 바꾸지 않는다.

### 0.7 #39 — 끝난 방식의 근거 (요구사항 v6)
v6은 "`isCanceled`가 거짓이면 토스트 + 실패"다. 그런데 오류·충돌 창의 "취소"는 `throw InterruptedIOException()`이고 서비스를 거치지 않아 표시가 꺼져 있다(`FileJobs.kt:728` 등 14곳). 그대로 두면 창의 취소가 "실패"로 보여 v3과 어긋난다. 그래서 14곳이 표시를 켜고 던지게 한다(`canceledByUser`, 2.16). `isCanceled`가 곧 "취소됨"의 유일한 근거다.

| 끝 | `isCanceled` | 끝난 방식 | 끝 토스트 |
|---|---|---|---|
| 목록·알림의 취소 | 켜짐(`FileJobService.kt:80`) | 취소됨 | "취소됨" |
| 오류·충돌 창의 "취소" | 켜짐(`canceledByUser`) | 취소됨 | "취소됨" |
| **서비스 종료**(`FileJobService.kt:90-105`) | 켜짐(`:99`) | **취소로 친다**, 취소됨(REQ-36-08) | "취소됨"(프로세스가 살아 있으면), #39 문구 없음 |
| 시간 초과 등 | 꺼짐 | 실패 | "실패" + #39 문구 |

작업 스레드에 끼어드는 곳은 서비스 둘뿐이고 둘 다 끼어들기 앞에 표시를 켠다. 그래서 진짜 끼어들기가 "사용자가 취소하지 않음"으로 잘못 읽히지 않는다. 서비스 종료는 사용자 조작이 아니지만 REQ-36-08이 취소로 분류하고, v7은 취소를 늘 알린다.

### 0.8 끝 토스트 — 요구사항 v7
작업 하나에 끝 토스트 하나다. 목록 창, #30 표시 설정(`Settings`를 읽지 않는다), 알림 설정과 무관하다.

| 종류 (`FileJobType`) | 실패·취소 | 완료 토스트 | 근거 |
|---|---|---|---|
| `COPY`, `EXTRACT`, `MOVE`, `ARCHIVE`, `DELETE` | 냄 | 냄 | v7 원문 |
| `SAVE` | 냄 | 새로 안 냄 | 기존 성공 토스트 `FileJobs.kt:1735`, 겹치지 않게 |
| `WRITE` | 냄 | 새로 안 냄 | 기존 성공 토스트 `viewer/text/TextEditorFragment.kt:269`, 겹치지 않게 |
| `CREATE`, `RENAME`, `OPEN`, `INSTALL_APK` | 냄 | 생략 | v7 원문, 결과가 바로 보임 |
| `SET_OWNER`, `SET_GROUP`, `SET_MODE`, `SET_SELINUX_CONTEXT`, `RESTORE_SELINUX_CONTEXT` | 냄 | 재귀일 때만 | 오래 걸릴 수 있음. 개발팀 분류, PM 확인 사항 |

**하나로 합치는 곳**

| 끝나는 길 | 지금까지의 토스트 | v7 |
|---|---|---|
| `runOn`의 일반 예외 | `e.toString()`(`FileJob.kt:36`) | "실패" + 같은 글 한 토스트 |
| 취소 아닌 `InterruptedIOException` | #39 문구 | "실패" + #39 문구 한 토스트 |
| 취소 뒤의 일반 예외(예: `ClosedByInterruptException`) | `e.toString()` | "취소됨" 하나. 원시 예외는 보이지 않는다 |
| 오류·충돌 창 "취소" | 없음 | "취소됨" 하나 |
| 건너뛰기로 끝남 | 없음 | "실패" 하나 |
| 저장 성공 | `:1735` | 그대로 하나, 끝 토스트 없음 |
| 글 편집기 저장 성공 | `TextEditorFragment.kt:269` | 그대로 하나, 끝 토스트 없음 |
| 글 편집기 저장 실패·취소 | 없음. `ActionState.Error`는 `finishWritingFile()`만 부른다(`TextEditorFragment.kt:273-274`) | 끝 토스트 하나. **겹치지 않는다**(PM 확인 요청의 답) |
| 정리 실패 | 따로 | 취소·실패 끝 토스트에 한 줄. 성공이면 버림 |

**합칠 수 없는 경우 하나**: 루트 모드 취소. 앱이 원격 정리를 기다리지 않으므로(HLD D5·D11) 끝 토스트가 먼저 나가고, 늦은 정리 실패나 원격 죽음은 지우지 못함 토스트를 따로 낸다. 끝난 뒤 온 새 사실이다.

**줄과 토스트를 맞추는 법(R13)**: 끝 토스트를 `runOn` 안이 아니라 서비스 콜백에서 `runningJobs`에서 뺀 **뒤에** 낸다. 그 전에 취소가 닿았으면 같은 잠금 안에서 표시가 켜져 있어 결과가 취소가 되고, 그 뒤에는 `cancelJob`이 찾지 못한다. `runJobAndReport`가 `Throwable`에도 콜백을 부르므로 `Error`도 "실패" 토스트 뒤 그대로 나간다.

**대가**: 동시 작업·"모두 취소"에서 토스트가 쌓인다. 정리 뒤에 나가므로 줄의 "취소됨"보다 늦을 수 있다(smbj 60초 등 시간 제한까지). 관찰은 T15.

### 0.9 #44와 #43
- #44: 저장이 예외로 끝나면 듣는 함수가 불리지 않아 `writeFileState`가 `Running`에 남고 저장 메뉴가 꺼진 채다(`FileJobs.kt:2266-2269`). 던져도 `listener(false)`를 메인에서 한 번 부르고 같은 예외를 다시 던진다(2.20). 끝난 방식은 그대로다.
- #43: 이 PR에서 고치지 않는다. #36 머지 뒤 별도 PR이고 `RemoteCopyOutcomeWatch`를 재사용한다. 그래서 그 클래스는 죽음 때 부를 함수만 받고 정리 실패를 모른다(2.17).

## 1. 변경 파일 (PROCESS_ROLES 11-1 범위 공지 — 5개 초과)

| 파일 | 신규/수정 | 요지 | 구성요소 |
|---|---|---|---|
| `filejob/FileJobOutcome.kt` | 신규 | 끝난 방식과 판정 | K1 |
| `filejob/FileJob.kt` | 수정 | `runOn` 반환형, 취소 표시, 정리 실패 기록 | K2·K16·K18 |
| `filejob/FileJobService.kt` | 수정 | 시작 정보, 끝 보고, 취소 표시 | K3·K4·K16 |
| `filejob/FileJobInfo.kt` | 신규 | 종류·이름 | K5 |
| `filejob/FileJobProgress.kt` | 신규 | 진행 값과 표시 규칙 | K6 |
| `filejob/FileJobs.kt` | 수정 | 진행 값 알리기, 취소 점검, 옵션 달기, 압축 정리 기록 | K7·K16·K18 |
| `filejob/FileJobEntryTable.kt` | 신규 | 진행 중 항목 표(순수) | K8 |
| `filejob/FileJobStateTracker.kt` | 수정 | 표·듣는 쪽 연결 | K8 |
| `filejob/FileJobCleanupFailures.kt` | 신규 | 정리 실패 기록 규칙(순수) | K18 |
| `filejob/FileJobEndNotice.kt` | 신규 | 끝 토스트 내용(순수)과 글 만들기 | K19 |
| `filejob/FileJobListRows.kt` | 신규 | 줄 규칙(순수) | K9 |
| `filejob/FileJobListViewModel.kt` | 신규 | 목록 뷰모델 | K10 |
| `filejob/FileJobListDialogFragment.kt` | 신규 | 목록 화면 | K11 |
| `filejob/FileJobListAdapter.kt` | 신규 | 줄 어댑터 | K12 |
| `res/layout/file_job_list_dialog.xml`, `res/layout/file_job_list_item.xml` | 신규 | 배치 | K11·K12 |
| `res/values/strings.xml`, `res/values-ko/strings.xml` | 수정 | 문자열 | K12·K13·K18 |
| `res/menu/file_list.xml`, `filelist/FileListFragment.kt` | 수정 | 메뉴 항목 | K13 |
| `provider/common/CleanupFailureCopyOption.kt` | 신규 | 복사 옵션 | K17 |
| `provider/common/CopyOptions.kt` | 수정 | 필드 추가 | K17 |
| `provider/common/ForeignCopyMove.kt`, `provider/linux/LinuxCopyMove.kt`, `provider/sftp/SftpCopyMove.kt`, `provider/smb/SmbCopyMove.kt`, `provider/smb/client/Client.kt`, `provider/ftp/FtpCopyMove.kt`, `provider/webdav/WebDavCopyMove.kt`, `provider/document/DocumentCopyMove.kt`, `provider/document/resolver/DocumentResolver.kt` | 수정 | 듣는 함수 호출·전달, 표시 지우기 | K14·K17 |
| `provider/remote/RemoteCopyOutcomeWatch.kt` | 신규 | 완료·죽음 중 먼저 온 것(순수) | K17 |
| `provider/remote/RemoteFileSystemProvider.kt` | 수정 | `copy`·`move`의 죽음 듣기 | K17 |
| `docs/STANDARDS_TRACEABILITY.md` | 수정 | 4장 #36 행 | K15 |
| `app/src/test/java/me/zhanghai/android/files/filejob/` 아래 `FileJobOutcomeTest`·`FileJobProgressTest`·`FileJobInfoTest`·`FileJobEntryTableTest`·`FileJobListRowsTest`·`FileJobCancelCheckTest`·`FileJobCleanupFailuresTest`·`FileJobRunBodyTest`·`FileJobEndNoticeTest` | 신규 | 단위 테스트 | 3장 |
| `app/src/test/java/me/zhanghai/android/files/filejob/FileJobServiceTest.kt` | 수정 | 형 변경, 기대는 같음 | K3 |
| `app/src/test/java/me/zhanghai/android/files/provider/common/CleanupFailureCopyOptionTest.kt`, `ForeignCopyMoveCleanupTest.kt`, `app/src/test/java/me/zhanghai/android/files/provider/remote/RemoteCopyOutcomeWatchTest.kt` | 신규 | 단위 테스트 | 3장 |

새 의존성 없음. `androidx.core.text.BidiFormatter`와 Material `LinearProgressIndicator`는 이미 있는 라이브러리다(`app/build.gradle:149`, `res/layout/add_lan_smb_server_fragment.xml:33`).

## 2. 함수 명세

### 2.1 K1 `FileJobOutcome` — `filejob/FileJobOutcome.kt`
```kotlin
enum class FileJobOutcome {
    SUCCEEDED, CANCELED, FAILED;
    companion object { fun of(hasUnresolvedError: Boolean, thrown: Throwable?, isCanceled: Boolean): FileJobOutcome }
}
```
#### `FileJobOutcome.of(hasUnresolvedError, thrown, isCanceled)`
- **추적**: K1 / REQ-36-08, 17, 18
- **책임**: 작업 본문의 끝을 끝난 방식으로 바꾼다.
- **입력·전제**: `thrown`은 본문이 던진 것, 정상 끝이면 `null`. `isCanceled`는 그 순간의 취소 표시. 스레드 무관, 부수 효과 없음.
- **오류**: 없음.
- **알고리즘**: `isCanceled`면 무엇으로 끝나든 `CANCELED`. 아니면 `thrown == null`일 때 `hasUnresolvedError`면 `FAILED`, 아니면 `SUCCEEDED`. `thrown`이 있으면 `FAILED`.
- **기존 코드와의 관계**: `FileJob.kt:27-37`의 `Boolean` 판정을 대체한다. 형을 가리지 않으므로 okio의 `InterruptedIOException("deadline reached")`도 `SocketTimeoutException`도 표시가 꺼져 있으면 실패다(HLD D3).

### 2.2 K2 `FileJob` 끝 처리 — `filejob/FileJob.kt:24-41`
#### `FileJob.runOn(service: FileJobService): FileJobOutcome`
- **추적**: K2 / REQ-36-08, 13
- **책임**: 본문을 돌리고 본문의 끝난 방식을 돌려준다.
- **입력·전제**: 작업 스레드.
- **오류**: `Exception`이 아닌 `Throwable`은 지금처럼 위로 나간다. K3이 `FAILED`로 콜백을 부른다.
- **알고리즘**: `this.service = service`, `isEndClaimedByRun = tryClaimEnd()`, `try { return runBody() } finally { service.notificationManager.cancel(id) }`.
- **기존 코드와의 관계**: 호출부는 `FileJobService.kt:61`뿐이다. `:36`의 `service.showToast(e.toString())`는 없어지고 끝 토스트로 옮겨 간다.

#### `internal fun FileJob.runBody(): FileJobOutcome`
- **추적**: K2 / REQ-36-08, 17
- **책임**: 본문의 끝을 판정하고 던진 것을 남긴다. `service` 없이 JVM에서 시험할 수 있게 뗀 함수다.
- **출력·사후조건**: `thrown` 필드(작업 스레드 전용).
- **알고리즘**: `try { run(); of(hasUnresolvedError, null, isCanceled) }`, `catch InterruptedIOException`과 `catch Exception` 모두 스택 출력(그대로), `thrown = e`, `of(false, e, isCanceled)`.
- **오류**: `Throwable`은 지나간다.

#### `internal fun FileJob.endOf(bodyOutcome: FileJobOutcome): FileJobEnd`
```kotlin
class FileJobEnd(val outcome: FileJobOutcome, val notice: FileJobEndNotice<Path>?, val separateFailures: List<Path>)
```
- **추적**: K2·K18·K19 / REQ-36-16, 18, HLD R13
- **책임**: 최종 끝난 방식과 낼 토스트를 정한다. 안드로이드 호출 없음.
- **입력·전제**: 작업 스레드, `runningJobs`에서 뺀 뒤.
- **알고리즘**:
  1. `outcome = if (isCanceled) CANCELED else bodyOutcome`.
  2. `failures = cleanupFailures.onJobFinished(outcome == SUCCEEDED)`.
  3. `isEndClaimedByRun`이면 `notice = fileJobEndNoticeOf(info.notifiesCompletion, outcome, thrown, failures)`, `separateFailures` 비움. 서비스가 이미 토스트를 냈으면(시작 전 취소와 겹침) `notice = null`, `separateFailures = failures`.

#### `internal fun FileJob.finishEnd(bodyOutcome: FileJobOutcome): FileJobOutcome`
- **추적**: K2·K4 / REQ-36-18
- **알고리즘**: 본문 전체를 `try`로 감싼다. `val end = endOf(bodyOutcome)`, `end.notice?.let { notifyEnd(it) }`, `end.separateFailures.forEach { notifyCleanupFailure(it) }`, `end.outcome`을 돌려준다. `catch (e: Exception)`: 스택 출력 후 `if (isCanceled) CANCELED else bodyOutcome`을 돌려준다.
- **오류**: 던지지 않는다. 콜백의 `finally`에서 원래 예외를 덮지 않아야 하고, 그 뒤 `onJobFinished` 보고가 반드시 나가야 줄과 #30 표시기가 남지 않기 때문이다.

#### `internal lateinit var info: FileJobInfo`, `tryClaimEnd(): Boolean`
- **추적**: K4·K19 / REQ-36-18
- `info`는 companion `startJob`이 넣는다. `FileJob`을 만드는 곳은 그 시작 함수들뿐이다.
- `tryClaimEnd`는 `AtomicBoolean.compareAndSet(false, true)`. `runOn` 시작과 서비스의 시작 전 취소 중 먼저 온 쪽만 참이다.

#### `internal fun FileJob.notifyCanceledWithoutRun(context: Context)`
- **추적**: K4·K19 / REQ-36-18
- **알고리즘**: `if (tryClaimEnd()) showFileJobEndToast(context, info, fileJobEndNoticeOf(false, CANCELED, null, emptyList())!!)`. 완료가 아니므로 `null`이 되지 않는다.
- **오류**: `Exception`을 삼킨다.

### 2.3 K3 `runJobAndReport` — `filejob/FileJobService.kt:231-238`
#### `runJobAndReport(run: () -> FileJobOutcome, onFinished: (FileJobOutcome) -> Unit)`
- **추적**: K3 / REQ-36-08
- **책임**: 본문을 돌리고 항상 한 번 보고한다.
- **입력·전제**: 작업 스레드.
- **출력·사후조건**: `onFinished`가 정확히 한 번 불린다. 기본값 `FAILED`.
- **오류**: 본문이 던진 것을 그대로 다시 던진다.
- **알고리즘**: 지금과 같고 형만 바뀐다.

### 2.4 K4·K16 `FileJobService` — `filejob/FileJobService.kt`
#### companion `startJob(job: FileJob, info: FileJobInfo, context: Context)` (`:123-132`)
- **추적**: K4·K8 / REQ-36-02, 03
- **입력·전제**: 메인.
- **출력·사후조건**: `job.info = info`, `FileJobStateTracker.onJobStarted(job.id, info, isPending = instance == null)`를 부른 뒤 지금처럼 넘기거나 쌓는다.
- **오류**: 지금과 같다.
- **알고리즘**: 시작 함수 15개(`:134-215`)가 정보를 만들어 넘긴다. 줄 번호 순서로:

| 시작 함수 | 정보 |
|---|---|
| `archive` | `FileJobInfo.of(ARCHIVE, sources, archiveFile)` |
| `copy` | `FileJobInfo.of(copyTypeOf(sources.all { it.isArchivePath }), sources, targetDirectory)` |
| `create` | `of(CREATE, listOf(path), null)` |
| `delete` | `of(DELETE, paths, null)` |
| `move` | `of(MOVE, sources, targetDirectory)` |
| `installApk` | `of(INSTALL_APK, listOf(file), null)` |
| `open` | `of(OPEN, listOf(file), null)` |
| `rename` | `of(RENAME, listOf(path), null)` |
| `restoreSeLinuxContext` | `of(RESTORE_SELINUX_CONTEXT, listOf(path), null, recursive)` |
| `save` | `of(SAVE, listOf(source), target.parent)` — 알림과 같이 원본 이름과 대상 폴더(`FileJobs.kt:1728-1729`) |
| `setGroup`·`setMode`·`setOwner`·`setSeLinuxContext` | `of(SET_GROUP`·`SET_MODE`·`SET_OWNER`·`SET_SELINUX_CONTEXT, listOf(path), null, recursive)` |
| `write` | `of(WRITE, listOf(file), null)` |

#### `onCreate()` (`:43-45`)
- **추적**: K8 ⑤ / REQ-36-02
- **알고리즘**: 대기 목록에서 꺼낸 작업마다 `FileJobStateTracker.onJobRunning(job.id)`를 부른 뒤 `startJob(job)`. 메인.

#### `startJob(job: FileJob)` (`:55-74`)
- **추적**: K2·K4 / REQ-36-08, 18, HLD R13
- **알고리즘**: 콜백이 `bodyOutcome`을 받는다. `synchronized(runningJobs) { remove; 잠금 갱신 }`(그대로) **다음에** `val outcome = job.finishEnd(bodyOutcome)`, 그 뒤 `onJobFinished(job.id, outcome)`을 메인으로 넘긴다. 토스트·줄 표시 보고가 끝 보고보다 먼저 메인에 간다(U-R13).

#### `cancelJob(id: Int)` (`:76-88`), `onDestroy()` (`:90-105`), companion `cancelJob(id: Int)` (`:218-223`)
- **추적**: K4·K16 / REQ-36-05, 06, 08
- **책임**: 취소 표시를 끼어들기 **앞에** 켜고 `CANCELED`를 보고한다.
- **출력·사후조건**: `cancelJob`은 `it?.key?.isCanceled = true` 다음 `it?.value?.cancel(true)`. `onDestroy`는 `entry.key.isCanceled = true` 다음 `entry.value.cancel(true)`. 세 곳의 보고를 `onJobFinished(id, CANCELED)`로 바꾼다. 대기 중 취소는 스레드가 없으므로 표시를 켜지 않는다. 서비스 종료는 취소로 친다(0.7). 셋 모두 `job.notifyCanceledWithoutRun(...)`을 부른다: 서비스 쪽은 `this`, companion의 대기 중 취소는 `application`(`app/AppProvider.kt:14`). 스레드가 이미 돌고 있으면 `tryClaimEnd`가 거짓이라 아무 일도 없고, 끝 토스트는 스레드가 낸다.
- **오류**: 없음. 찾지 못한 id는 지금처럼 보고하지 않는다(`:85`).
- **기존 코드와의 관계**: `Future.cancel(true)`와 잠금은 그대로다. `runningJobCount`도 그대로다(REQ-36-13).

### 2.5 K5 `FileJobInfo` — `filejob/FileJobInfo.kt`
```kotlin
enum class FileJobType { COPY, EXTRACT, MOVE, ARCHIVE, DELETE, CREATE, RENAME, OPEN, INSTALL_APK, SAVE, WRITE,
    SET_OWNER, SET_GROUP, SET_MODE, SET_SELINUX_CONTEXT, RESTORE_SELINUX_CONTEXT }
data class FileJobInfo(val type: FileJobType, val subjectName: String, val subjectCount: Int, val destinationName: String?, val isRecursive: Boolean)
```
#### `FileJobInfo.notifiesCompletion: Boolean`
- **추적**: K5·K19 / REQ-36-18
- **알고리즘**: 0.8의 표. `when(type)`을 `else` 없이 쓴다(새 값이 생기면 컴파일 오류).

#### `FileJobInfo.of(type: FileJobType, subjects: List<Path>, destination: Path?, isRecursive: Boolean = false): FileJobInfo`
- **추적**: K5 / REQ-36-03
- **책임**: 경로에서 표시용 값을 만든다.
- **입력·전제**: 메인. 입출력 없음.
- **출력·사후조건**: `subjectName` = 첫 항목의 `fileJobName()`, 비었으면 `""`. `subjectCount` = 개수. `destinationName` = `destination?.fileJobName()`. 값에 `Path`를 남기지 않는다.
- **오류**: 던지지 않는다(메인에서 앱을 죽이지 않도록 `first()` 대신 `firstOrNull()`).

#### `copyTypeOf(isAllArchive: Boolean): FileJobType`
- **알고리즘**: 참이면 `EXTRACT`, 아니면 `COPY`. 호출부의 식은 `FileJobs.kt:744`와 같다.

#### `Path.fileJobName(): String`
- **책임**: 경로의 마지막 이름. 루트는 구분자, 압축 파일 루트는 압축 파일 기본 이름.
- **알고리즘**:
  1. `isArchivePath && isAbsolute && nameCount == 0`이면 `archiveFile.asByteStringListPath().fileNameByteString!!.asFileName().baseName.toString()`. 뒤쪽 식은 `getTargetFileName`(`FileJobs.kt:175-186`)과 같다.
  2. 그 밖은 `plainFileJobName()`.
- **기존 코드와의 관계**: `createArchiveRootPath()`(`provider/archive/PathArchiveExtensions.kt:27-28`)는 부르지 않는다 — 메인에서 파일 시스템 객체를 만들 수 있어서다. 압축 루트 경로는 절대 경로이고 이름 수가 0이므로(`ArchiveFileSystem.kt:36`) 같은 판단이다. `FileJobs.kt`의 기존 private 함수는 그대로 둔다.

#### `Path.plainFileJobName(): String`
- **알고리즘**: `isAbsolute && nameCount == 0`이면 `fileSystem.separator`, 아니면 `fileName?.toString() ?: ""`. `FileJobs.kt:168-173`과 같은 규칙이다.

### 2.6 K6 `FileJobProgress` — `filejob/FileJobProgress.kt`
```kotlin
enum class FileJobProgressKind { SCANNING, SIZE, COUNT }
data class FileJobProgress(val kind: FileJobProgressKind, val fileCount: Int, val transferredFileCount: Int, val size: Long, val transferredSize: Long)
```
#### `FileJobProgress.percent: Int?`
- **추적**: K6 / REQ-36-04
- **출력·사후조건**: 0~100 또는 `null`(불확정).
- **알고리즘**: `SCANNING` → `null`. `SIZE` → `size <= 0`이면 `null`, 아니면 `(transferredSize * 100 / size).coerceIn(0, 100).toInt()`. `COUNT` → `fileCount <= 1`이면 `null`(`FileJobs.kt:405-410`), 아니면 `(transferredFileCount.toLong() * 100 / fileCount).coerceIn(0, 100).toInt()`.
- **오류**: 던지지 않는다. `size`가 `TransferInfo.skipFile`(`FileJobs.kt:453`)로 음수가 되어도 불확정이다. 크기가 92 PB를 넘으면 곱셈이 넘칠 수 있으나 `coerceIn`으로 범위만 지킨다.

#### `FileJobProgress.currentFileIndex: Int`
- **알고리즘**: `(transferredFileCount + 1).coerceAtMost(fileCount)`. 알림의 항목 위치(`FileJobs.kt:365-366`, `:414`)와 같다.

#### `internal class FileJobProgressThrottle(intervalMillis: Long)`, `shouldReport(nowMillis: Long): Boolean`
- **추적**: K7 / SRS 3.2
- **알고리즘**: 마지막 시각 + 간격 < `nowMillis`이면 시각을 갱신하고 참. 처음에는 참. 작업 스레드 전용.

### 2.7 K7·K16·K18 `FileJobs.kt`
#### `private fun FileJob.reportProgress(progress: FileJobProgress)` (신규)
- **추적**: K7 / REQ-36-04, 12
- **책임**: 값을 메인으로 넘긴다.
- **입력·전제**: 작업 스레드 또는 바인더 스레드(루트 모드 진행 콜백). 값은 불변이다.
- **출력·사후조건**: `service.mainExecutorCompat.execute { FileJobStateTracker.onJobProgress(id, progress) }`.
- **오류**: 없음. 메인은 모르는 id를 무시한다.

#### 알림 함수 네 곳
거르기를 통과한 **바로 다음 줄**에 한 줄씩 넣는다. 그 아래 알림 코드는 바꾸지 않는다.

| 함수 | 넣는 곳 | 값 |
|---|---|---|
| `postScanNotification` (`:302-310`) | `:305` 뒤 | `if (scanInfo.shouldReportProgress()) reportProgress(FileJobProgress(SCANNING, scanInfo.fileCount, 0, scanInfo.size, 0))` |
| `postTransferSizeNotification` (`:340-388`) | `:348` 뒤 | `FileJobProgress(SIZE, fileCount, transferredFileCount, size, transferredSize)` |
| `postTransferCountNotification` (`:390-424`) | `:398` 뒤 | `FileJobProgress(COUNT, fileCount, transferredFileCount, 0, 0)` |
| `postWriteNotification` (`:2325-2341`) | `:2328` 뒤 | `FileJobProgress(SIZE, fileCount, transferredFileCount, size, transferredSize)` |

#### `ScanInfo.shouldReportProgress(): Boolean` (신규, `:312-338`)
- **추적**: K7 / REQ-36-04, SRS 3.2
- **책임**: 훑기 값을 0.5초에 한 번 이하로 거른다. 훑기의 알림 거르기는 100개마다 무조건 통과시켜(`:330`) 더 자주 통과할 수 있어서다(4장 FB-1).
- **알고리즘**: `ScanInfo`가 `FileJobProgressThrottle(NOTIFICATION_INTERVAL_MILLIS)`를 가지고 `shouldReport(System.currentTimeMillis())`를 돌려준다. `ScanInfo`는 `private`이라 규칙을 시험할 수 있는 `internal` 클래스로 뗀다(2.6).

#### `private fun FileJob.throwIfInterrupted()` (`:230-234`)
- **추적**: K16 / REQ-36-05
- **알고리즘**: 본문을 `throwIfCanceledOrInterrupted()` 호출로 바꾼다. 호출부는 그대로다.

#### `showUserAction` (`:485`), `showErrorDialog` (`:500`), `showConflictDialog` (`:555`)
- **추적**: K16 / REQ-36-05, 13
- **알고리즘**: 창을 띄우기 전에 `throwIfCanceled()`. `showErrorDialog`는 첫 문장으로, `showUserAction`·`showConflictDialog`는 식 본문을 유지해 `try` 블록의 첫 문장으로 넣는다. `showConflictDialog`는 이미 `IOException`을 선언하고, 나머지 둘도 `InterruptedIOException`을 선언한다.
- **오류**: 취소된 작업에서 `InterruptedIOException`.
- **기존 코드와의 관계**: 취소되지 않은 작업의 동작은 같다(C-1). 이미 떠 있는 창은 닫지 않는다(HLD R1).

#### `copyOrMove` (`:1192-`)
- **추적**: K16·K18 / REQ-36-05, 16
- **알고리즘**: `do` 안의 첫머리(`:1286` `retry = false` 다음)에 다음을 더한다.
  1. `throwIfInterrupted()` — "모두 바꾸기"로 재시도할 때 취소된 작업이 기존 파일을 지우지 않게 한다(4장 FB-2).
  2. `val attemptTarget = target` — 이름 바꾸기로 `target`이 바뀌어도 이 시도의 대상을 잡아 둔다.
  3. 옵션 목록(`:1287-1299`)에 `this += CleanupFailureCopyOption { onCleanupFailed(attemptTarget) }`.
  4. `copyTo`/`moveTo`가 끝난 직후(`:1306` 다음, `incrementTransferredFileCount` 앞) `cleanupFailures.onWritten(attemptTarget)`.
- **오류**: 지금과 같다. 옵션은 시도마다 새로 만든다.
- **기존 코드와의 관계**: 분기·반환값은 그대로다. 열기·APK 설치의 캐시 복사도 이 함수를 지나므로 같은 알림 대상이다(드문 경우이며 조각을 숨기지 않는 쪽).

#### `ArchiveFileJob.run` 정리 (`:645-655`)
- **추적**: K14·K18 / REQ-36-10, 16
- **알고리즘**: `:646` `if (!successful)` 안 첫 문장에 `Thread.interrupted()`(2.14). `:649`·`:651`의 두 `catch`에서 `printStackTrace` 다음에 `onCleanupFailed(archiveFile)`. `deleteIfExists`이므로 이미 없으면 `catch`에 오지 않는다.

#### 오류·충돌 창의 "취소" 14곳
- **추적**: K16 / REQ-36-08, 17
- **알고리즘**: `FileJobs.kt:728`·`:944`·`:1062`·`:1238`·`:1278`·`:1375`·`:1439`·`:1589`·`:1701`·`:1846`·`:1992`·`:2112`·`:2242`·`:2318`의 `throw InterruptedIOException()`을 `throw canceledByUser()`로 바꾼다. 끼어들기를 옮기는 `:496`·`:528`·`:577`은 그대로다(그 경로는 서비스가 이미 표시를 켰다). `:232`는 `throwIfCanceledOrInterrupted`로 옮겨 간다.

#### `internal fun FileJob.onCleanupFailed(path: Path)` (신규)
- **추적**: K18 / REQ-36-16, HLD R10
- **책임**: 실패 보고를 받아 기록하거나, 작업이 이미 끝났으면 규칙대로 곧바로 알린다.
- **입력·전제**: 작업 스레드 또는 바인더 스레드.
- **출력·사후조건**: `if (cleanupFailures.onFailed(path)) notifyCleanupFailure(path)`.
- **오류**: 던지지 않는다. 기다리지 않는다.

#### `internal fun FileJob.notifyCleanupFailure(path: Path)` (신규)
- **추적**: K18 / REQ-36-16
- **책임**: 끝 토스트에 합칠 수 없는 경우의 지우지 못함 토스트와 줄 표시를 메인으로 넘긴다.
- **입력·전제**: 바인더 스레드(늦은 원격 보고, 죽음 듣기) 또는 작업 스레드(`finishEnd`).
- **출력·사후조건**: `service.mainExecutorCompat.execute { service.showToast(text, Toast.LENGTH_LONG); FileJobStateTracker.onJobCleanupFailed(id) }`.
- **오류**: 전체를 `try`로 감싸 `Exception`을 스택만 찍고 삼킨다.
- **알고리즘**: `text = cleanupFailureTextOf(service, listOf(path))`(2.19).

#### `internal fun FileJob.notifyEnd(notice: FileJobEndNotice<Path>)` (신규)
- **추적**: K19 / REQ-36-16, 18
- **입력·전제**: 작업 스레드.
- **출력·사후조건**: `showFileJobEndToast(service, info, notice)`. `notice.cleanupFailures`가 있으면 메인으로 `FileJobStateTracker.onJobCleanupFailed(id)`.
- **오류**: 전체를 `try`로 감싸 `Exception`을 삼킨다. 콜백 안이고 그 뒤 끝 보고가 나가야 하기 때문이다.

### 2.8 K8 `FileJobEntryTable`·`FileJobStateTracker`
```kotlin
data class FileJobEntry(val id: Int, val info: FileJobInfo, val progress: FileJobProgress?, val isPending: Boolean)
```
`FileJobStateTracker`는 `Settings`·`MutableLiveData` 때문에 JVM 단위 테스트가 어렵다. 표 규칙만 `FileJobEntryTable`로 뺀다.

#### `FileJobEntryTable` (신규, 메인 전용, `LinkedHashMap<Int, FileJobEntry>`)
- **추적**: K8 / REQ-36-02, 04, 07, 08
- `start(id, info, isPending): FileJobEntry?` — 이미 있으면 `null`(R5), 아니면 항목을 넣어 돌려준다.
- `setRunning(id): Boolean` — 있고 대기 중일 때만 끄고 `true`.
- `setProgress(id, progress): Boolean` — 있을 때만 바꾸고 `true`. 넣을 때 대기 중도 끈다.
- `finish(id): Boolean` — 있으면 지우고 `true`.
- `entries: List<FileJobEntry>` — 시작 순서의 복사본. 값 갱신은 키 순서를 바꾸지 않는다.
- **오류**: 던지지 않는다.

#### `FileJobStateTracker` (`filejob/FileJobStateTracker.kt`, 메인 전용)
```kotlin
interface Listener { fun onStarted(entry: FileJobEntry); fun onRunning(id: Int); fun onProgress(id: Int, progress: FileJobProgress)
    fun onFinished(id: Int, outcome: FileJobOutcome); fun onCleanupFailed(id: Int) }
```
- **추적**: K8 / REQ-36-02, 04, 07, 08, 09, 12, 16
- `onJobStarted(id, info, isPending)`: 기존 `batch.start` 처리 그대로. `table.start(...)`가 값을 주면 `onStarted`.
- `onJobRunning(id)`: `table.setRunning(id)`이 참이면 `onRunning`.
- `onJobProgress(id, progress)`: `table.setProgress`가 참이면 `onProgress`.
- `onJobFinished(id, outcome)`: `batch.finish(id, outcome == SUCCEEDED, Settings.FILE_JOB_INDICATOR.valueCompat)` 처리 그대로. `table.finish(id)`가 참이면 `onFinished`.
- `onJobCleanupFailed(id)`: 표와 관계없이 `onCleanupFailed`.
- `runningEntries: List<FileJobEntry>`: `table.entries`.
- `addListener`·`removeListener`: 목록에 넣고 뺀다. 알릴 때는 복사본을 돈다(콜백 안에서 해제해도 안전).
- **오류**: 던지지 않는다.
- **기존 코드와의 관계**: `stateLiveData`·`consumeResult`·`FileJobBatch`는 바꾸지 않는다(REQ-36-09, `FileJobBatchTest` 그대로 통과).

### 2.9 K9 `FileJobListRows` — `filejob/FileJobListRows.kt`
```kotlin
sealed interface FileJobRowStatus {
    object Pending; object Running; data class Scanning(val fileCount: Int, val size: Long)
    data class Size(val percent: Int, val transferredSize: Long, val size: Long, val fileIndex: Int?, val fileCount: Int)
    data class Count(val percent: Int, val fileIndex: Int, val fileCount: Int)
    data class Finished(val outcome: FileJobOutcome, val hasCleanupFailure: Boolean)
}
data class FileJobRow(val id: Int, val info: FileJobInfo, val status: FileJobRowStatus)
```
#### `fileJobRowStatusOf(isPending, progress, outcome, hasCleanupFailure): FileJobRowStatus`
- **추적**: K9 / REQ-36-04, 07, 16
- **책임**: 줄 상태를 정한다.
- **알고리즘**, 위에서부터 처음 맞는 것:
  1. `outcome != null` → `Finished`.
  2. `isPending` → `Pending`.
  3. `progress == null` → `Running`.
  4. `kind == SCANNING` → `Scanning(fileCount, size)`.
  5. `percent == null` → `Running`.
  6. `SIZE` → `Size(percent, transferredSize, size, currentFileIndex if fileCount > 1 else null, fileCount)`.
  7. `COUNT` → `Count(percent, currentFileIndex, fileCount)`.
- **오류**: 던지지 않는다.

#### `FileJobRow.isActive: Boolean`
- `status !is Finished`. 취소 버튼 활성과 "모두 취소" 대상의 기준이다.

#### `class FileJobListRows(initialEntries: List<FileJobEntry>)` (메인 전용)
- **추적**: K9 / REQ-36-02, 06, 07, 15, 16
- 내부에 줄별 가변 상태를 시작 순서로 두고, 모든 변경 함수는 바뀌었는지를 `Boolean`으로 돌려준다.
- `onStarted(entry)`: 같은 id의 줄이 있으면 무시(R5), 없으면 끝에 추가.
- `onRunning(id)`: 진행 중 줄만.
- `onProgress(id, progress)`: 진행 중 줄만. 대기 중도 끈다. 끝난 줄은 무시.
- `onFinished(id, outcome)`: 진행 중 줄만 기록. 이미 끝난 줄·없는 줄은 무시(먼저 온 것이 이김).
- `onCleanupFailed(id)`: 끝난 줄·진행 중 줄 모두 표시. 없는 줄은 무시.
- `rows: List<FileJobRow>`: 불변 스냅숏.
- `activeIds: List<Int>`: 끝나지 않은 줄의 id 복사본.
- **기존 코드와의 관계**: 안드로이드 의존 없음. 연 순간의 `runningEntries`만 받으므로 이미 끝난 작업은 줄이 없다.
- **한계**: 목록을 연 시점에 정리 실패 보고가 이미 메인을 지나 줄이 아직 진행 중인 경우(오류 창 "취소"의 끝 직전, 메인 한 틱 안)는 표시를 잃는다. 토스트는 그대로 뜬다. 끝 보고와 정리 보고가 연달아 메인에 오므로 드물어 표에 표시를 두지 않는다.

### 2.10 K10 `FileJobListViewModel` — `filejob/FileJobListViewModel.kt`
#### 생성자·`onCleared()`
- **추적**: K10 / REQ-36-02, 07, 14
- **입력·전제**: 메인. 스냅숏과 등록이 같은 메인 틱에 일어나 사이에 사건을 놓치지 않는다.
- **알고리즘**: `rows = FileJobListRows(FileJobStateTracker.runningEntries)`, `_rowsLiveData = MutableLiveData(rows.rows)`, 듣는 쪽 등록. 콜백마다 `rows`의 해당 함수가 참이면 `_rowsLiveData.value = rows.rows`. `onCleared`에서 해제.

#### `cancel(id: Int)`·`cancelAll()`
- **추적**: K10 / REQ-36-05, 06
- **알고리즘**: `cancel` → `FileJobService.cancelJob(id)`. `cancelAll` → `rows.activeIds`(복사본)를 돌며 같은 호출. 호출 중에 듣는 쪽이 `rows`를 바꿔도 안전하다.
- **오류**: 이미 끝난 id는 `cancelJob`이 아무것도 하지 않는다.

### 2.11 K11 `FileJobListDialogFragment` — `filejob/FileJobListDialogFragment.kt`
#### `onCreateDialog(savedInstanceState): Dialog`
- **추적**: K11 / REQ-36-01, 02, 06, 14, 15
- **알고리즘**:
  1. `MaterialAlertDialogBuilder(requireContext(), theme)`, 제목 `R.string.file_job_list_title`.
  2. `FileJobListDialogBinding` 부풀리기, `LinearLayoutManager`, 어댑터(`viewModel::cancel`), `(recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false` — 0.5초마다 줄이 깜빡이지 않고, 같은 뷰가 다시 묶여 낭독 조건을 지킨다.
  3. `setPositiveButton(R.string.close, null)`, `setNeutralButton(R.string.file_job_list_cancel_all, null)`.
  4. `create()`한 대화상자의 `setOnShowListener`에서 중립 버튼 클릭을 `viewModel.cancelAll()`로 바꾸고(`ColorPreferenceDialogFragment.kt:83-84`) `updateCancelAllButton()`.
  5. `viewModel.rowsLiveData.observe(this) { onRowsChanged(it) }`.
- **오류**: 없음.
- **기존 코드와의 관계**: `PermissionListDialogFragment.kt:41-63`과 같은 꼴. 뷰모델은 `viewModels { { FileJobListViewModel() } }`.

#### `onRowsChanged(rows: List<FileJobRow>)`
- **알고리즘**: `adapter.replace(rows, false)`, `binding.emptyView.isVisible = rows.isEmpty()`, `updateCancelAllButton()`.

#### `updateCancelAllButton()`
- **알고리즘**: `(dialog as AlertDialog?)?.getButton(BUTTON_NEUTRAL)?.isEnabled = viewModel.rowsLiveData.valueCompat.any { it.isActive }`. 보이기 전이면 `null`이라 건너뛰고, 표시 리스너가 다시 부른다.

#### companion `show(fragment: Fragment)`
- **알고리즘**: `FileJobListDialogFragment().show(fragment)`(`util/DialogFragmentExtensions.kt:11-13`, 자식 프래그먼트 관리자). 인자 없음.

#### 배치 `res/layout/file_job_list_dialog.xml`
`permission_list_dialog.xml`에서 `progress`·`errorText`를 뺀 꼴이다. `emptyView` 글은 `R.string.file_job_list_empty`.

### 2.12 K12 `FileJobListAdapter` — `filejob/FileJobListAdapter.kt`
`ui/ListAdapter.kt`를 상속한다. `ItemCallback`: 같은 항목 = `id` 같음, 같은 내용 = `==`. 바뀐 줄만 다시 묶인다.

#### `onBindViewHolder(holder, position)`
- **추적**: K12 / REQ-36-03, 04, 05, 07, 16
- **입력·전제**: 메인, 입출력 없음.
- **알고리즘**:
  1. `titleText.text = row.info.getTitle(context, holder.isRtl)`.
  2. `cancelButton.isEnabled = row.isActive`, 비활성 `alpha = 0.38f`, `contentDescription = getString(R.string.file_job_list_cancel_description_format, 대상 글)`. 클릭: `bindingAdapterPosition`이 `RecyclerView.NO_POSITION`이면 무시하고, 아니면 `getItem(position).id`로 어댑터 콜백 호출.
  3. `status`로 나눈다.
     - `Finished`: `statusText`·`progress` `GONE`, `outcomeText` **`VISIBLE`을 먼저** 한 뒤 글이 다를 때만 `text`를 넣는다. 같은 글을 다시 넣어도 `TextView`가 내용 변경 사건을 내어 live region이 다시 읽을 수 있기 때문이다(근거는 플랫폼 기억, 미확인 — T4로 확인).
     - 그 밖: `outcomeText` `GONE`, `statusText`·`progress` `VISIBLE`, `statusText.text = statusTextOf(status)`. `Size`·`Count`는 `progress.setProgressCompat(percent, false)`, 나머지는 `progress.isIndeterminate = true`.
- **오류**: 없음.

#### `internal fun FileJobInfo.getTitle(context: Context, isRtl: Boolean): String`
목록 줄과 끝 토스트가 함께 쓴다(같은 파일).
- **추적**: K12·K19 / REQ-36-03, 18, HLD R6, SRS 3.5
- **알고리즘**:
  1. 이름: `subjectCount <= 1`이면 `subjectName`, 아니면 복수형 `file_job_list_subject_multiple_format`(`subjectName`, `subjectCount - 1`).
  2. 목적지가 있으면 `file_job_list_destination_format`(LTR "→") 또는 `file_job_list_destination_format_rtl`(RTL "←")로 잇는다. 고르는 기준은 `ViewHolder`의 `isRtl`로, `onCreateViewHolder`에서 붙어 있는 부모의 `parent.layoutDirection == View.LAYOUT_DIRECTION_RTL`로 정한다(저장소 선례는 뷰 기준, `DualPaneLayout.kt:232`). 두 이름은 `BidiFormatter.getInstance().unicodeWrap`으로 감싼다.
  3. `file_job_list_title_format` = 종류 글 " · " 대상 글.

#### `typeTextOf(type)`, `statusTextOf(status)`, `outcomeTextOf(finished)`
- **알고리즘**: `when`으로 자원에 대응한다(`else` 없이, 새 값이 생기면 컴파일 오류). 크기는 `asFileSize().formatHumanReadable(context)`(`file/FileSize.kt:23`). 개수는 기존 알림처럼 `%,d`. `outcomeTextOf`는 `hasCleanupFailure`면 `file_job_list_outcome_cleanup_failed_format`으로 덧붙인다.

#### 배치 `res/layout/file_job_list_item.xml`
가로 `LinearLayout`(`?dialogPreferredPadding` 시작 쪽 여백). 왼쪽 세로 묶음 `layout_weight=1`: `titleText`(한 줄, 가운데 줄임), `statusText`, `outcomeText`(`accessibilityLiveRegion="polite"`, `gone`), `progress`(`LinearProgressIndicator`, `android:max="100"`). 끝 쪽 `cancelButton`: `ImageButton`, 48dp×48dp, `@drawable/close_icon_control_normal_24dp`, `?selectableItemBackgroundBorderless`. 모두 start/end 속성.

#### 문자열 (기본 영어 + `values-ko`)
| 이름 | 한국어 |
|---|---|
| `file_job_list_title` | 파일 작업 |
| `file_job_list_cancel_all` | 모두 취소 |
| `file_job_list_empty` | 진행 중인 파일 작업 없음 |
| `file_job_list_cancel_description_format` | %1$s 취소 |
| `file_job_list_title_format` | %1$s · %2$s |
| `file_job_list_subject_multiple_format`(복수형) | %1$s 외 %2$,d개 |
| `file_job_list_destination_format`, `_rtl`(`translatable="false"`) | %1$s → %2$s, %1$s ← %2$s |
| `file_job_list_type_*` 16개 | 복사, 압축 풀기, 이동, 압축, 삭제, 새로 만들기, 이름 바꾸기, 열기, APK 설치, 저장, 쓰기, 소유자 바꾸기, 그룹 바꾸기, 권한 바꾸기, SELinux 컨텍스트 바꾸기, SELinux 컨텍스트 복원 |
| `file_job_list_status_pending`, `_running` | 대기 중, 진행 중 |
| `file_job_list_status_scanning_format`(복수형) | 준비 중 · %1$,d개 · %2$s |
| `file_job_list_status_size_format` | %1$d%% · %2$s / %3$s |
| `file_job_list_status_size_multiple_format` | %1$d%% · %2$s / %3$s · 파일 %4$,d / %5$,d |
| `file_job_list_status_count_format` | %1$d%% · %2$,d / %3$,d |
| `file_job_list_outcome_succeeded`, `_canceled`, `_failed` | 완료, 취소됨, 실패 |
| `file_job_list_outcome_cleanup_failed_format` | %1$s · 일부 파일을 지우지 못함 |
| `file_job_end_toast_format` | %1$s: %2$s (대상 글: 끝난 방식 글, 끝난 방식 글은 `file_job_list_outcome_*`) |
| `file_job_cleanup_failed_toast_format` | “%2$s”의 “%1$s”을(를) 지우지 못함 |
| `file_job_cleanup_failed_multiple_toast_format`(복수형) | “%2$s”의 “%1$s” 외 %3$,d개를 지우지 못함 |
| `file_job_interrupted_toast` | 파일 작업이 끝나기 전에 중단되었습니다. 네트워크 시간 초과 때문일 수 있습니다. (영어: File operation was interrupted before finishing, possibly due to a network timeout) |

### 2.13 K13 메뉴 — `res/menu/file_list.xml`, `filelist/FileListFragment.kt`
- **추적**: K13 / REQ-36-01
- `file_list.xml`: `action_refresh`(`:89-94`) 뒤에 `action_file_jobs`, `orderInCategory=100`, `showAsAction="never"`, 제목 `file_job_list_title`.
- `MenuBinding`(`:1717-`): 필드 `fileJobsItem` 추가.
- `onToolbarMenuItemSelected`(`:480-`): `R.id.action_refresh` 가지(`:536`) 뒤에 `R.id.action_file_jobs -> { FileJobListDialogFragment.show(this); true }`.
- `updateFileJobsMenuItem()` (신규): `menuBinding`이 초기화 전이면 돌아가고, `menuBinding.fileJobsItem.isVisible = viewModel.pickOptions == null`. `:425`와 `:969`에서 `updateSelectAllMenuItem()` 다음에 부른다.

### 2.14 K14 대상 파일 정리 — 끼어들기 표시 지우기
- **추적**: K14 / REQ-36-10, 16, HLD R11·D12
- **책임**: 표시가 켜진 채 정리에 닿지 않게 한다. 지우기 호출·조건·순서는 그대로다(HLD D5).
- **근거**: smbj `Futures.get`과 sshj `Promise.tryRetrieve`는 `InterruptedException`을 잡은 뒤 표시를 다시 켠다(바이트코드 확인). okio는 표시를 지우지 않은 채 던진다(HLD R11). 표시가 켜져 있으면 정리의 대기가 곧바로 던져 조각이 남고 K17이 잘못 알린다.

| 곳 | 위치 | 줄 |
|---|---|---|
| SFTP | `SftpCopyMove.kt:108` `finally` 첫 문장(대상 닫기 앞) | `if (!successful) Thread.interrupted()` |
| WebDAV | `WebDavCopyMove.kt:82` `finally` 첫 문장(대상 닫기 앞, PUT 응답을 기다리게 한다) | 같음 |
| 공급자 간 | `ForeignCopyMove.kt:62` `finally` 첫 문장(대상 닫기 앞) | 같음 |
| SMB | `smb/client/Client.kt:402` `if (!successful)` 안 첫 문장(`deleteOnClose` 앞, 닫기는 그 뒤) | `Thread.interrupted()` |
| 압축 | `FileJobs.kt:646` `if (!successful)` 안 첫 문장(채널은 안쪽 `use`가 이미 닫음) | `Thread.interrupted()` |

- **오류**: 없음. 표시를 지워도 작업의 멈춤은 `isCanceled`가 맡는다(K16). 복사가 이미 실패한 경로만 지나므로 취소되지 않은 작업은 같다(SRS C-1).
- **대가**: 취소된 작업 스레드가 닫기에서 서버 응답을 라이브러리 시간 제한까지 기다릴 수 있다(smbj는 `SmbConfig.DEFAULT_TIMEOUT` 60초이고 앱이 `SmbConfig`를 만드는 곳이 없다. sshj·okhttp는 미확인). 줄은 이미 "취소됨"이고 화면을 막지 않는다.
- **넣지 않는 곳**: 로컬(`syscall`은 표시를 보지 않음), FTP(`commons-net` 소켓 입출력, 실기 미확인), SAF(`ContentResolver` 호출). 실기 확인은 T1·T13.
- **남는 관찰**: SFTP 쓰기 쪽이 끼어들리면 `FileByteChannel`의 `closeSafe`가 표시가 켜진 채 돌아 핸들이 서버에 남을 수 있다. 열린 파일 삭제를 막는 서버에서만 토스트가 난다(T13). 압축은 안쪽 `use`(`FileJobs.kt:634-635`)의 채널 닫기가 표시가 켜진 채 돌아 같은 일이 원격 압축 파일에 생길 수 있다. 표시를 그 앞에서 지우려면 `use` 구조를 바꿔야 하므로 지금은 T13에서 관찰한다.

### 2.15 K15 추적표
`docs/STANDARDS_TRACEABILITY.md` 4장에 #36 행을 넣는다: SRS 5장 조항, REQ-36-01~16, 새 권한·편차 없음. 구현 PR에서 한다.

### 2.16 K16 `FileJob` 취소 표시 — `filejob/FileJob.kt`
```kotlin
@Volatile internal var isCanceled = false
```
#### `internal fun canceledByUser(): InterruptedIOException`
- **추적**: K16 / REQ-36-08, 17
- **출력·사후조건**: `isCanceled = true` 후 새 `InterruptedIOException`을 돌려준다(던지는 쪽은 호출부). 작업 스레드에서만 부른다.
- **기존 코드와의 관계**: 표시는 이후 창 앞 점검과 K18에도 그대로 쓰인다. 이미 끝나 가는 작업이라 달라지는 동작은 끝난 방식과 정리 실패 알림뿐이다.

#### `internal fun throwIfCanceled()`
- **추적**: K16 / REQ-36-05
- **알고리즘**: `isCanceled`면 `InterruptedIOException`.

#### `internal fun throwIfCanceledOrInterrupted()`
- **추적**: K16 / REQ-36-05, 13
- **알고리즘**: `Thread.interrupted() || isCanceled`면 `InterruptedIOException`. `Thread.interrupted()`를 **먼저** 불러 표시를 지운다(지금과 같이 한 번 던진 뒤 정리가 표시에 걸리지 않게).
- **오류**: 위 예외뿐이다. 취소되지 않았고 끼어들기가 없으면 아무 일도 없다.
- **한계**: 표시를 보는 곳은 파일 사이의 점검과 창 앞이다. 라이브러리가 끼어들기를 소비하고 오류 없이 쓰기를 이어 가면 그 파일은 끝까지 쓰인 뒤 멈춘다. 다음 파일로는 넘어가지 않으며 온전한 파일이 남는다(SRS A3). T2에서 관찰한다.

### 2.17 K17 `CleanupFailureCopyOption` — `provider/common/`
```kotlin
class CleanupFailureCopyOption(listener: () -> Unit) : CopyOption, Parcelable {
    val listener: () -> Unit = safeOnce(listener)
}
```
#### `internal fun safeOnce(listener: () -> Unit): () -> Unit`
- **추적**: K17 / REQ-36-16, HLD R10
- **책임**: 한 번만 부르고 던지지 않는 함수로 감싼다.
- **입력·전제**: 어느 스레드든, 여러 스레드에서 불려도 된다.
- **출력·사후조건**: 첫 호출만 `listener`를 부른다(`AtomicBoolean.compareAndSet`).
- **오류**: `listener`의 `Exception`을 잡아 스택만 찍는다. 던진 호출도 "불린 것"으로 치며 다시 부르지 않는다.
- **기존 코드와의 관계**: 감싼 함수를 또 감싸도 안쪽 표시가 같아 의미가 같다(`toArray`가 새 옵션을 만들 때).

#### `writeToParcel(dest, flags)`·`private constructor(source: Parcel)`·`CREATOR`
- **추적**: K17 / REQ-36-16
- **알고리즘**: 쓰기는 `dest.writeParcelable(RemoteCallback { listener() }, flags)`. 읽기는 `readParcelable<RemoteCallback>()!!`로 받아 `this({ callback.sendResult(Bundle()) })` — 원격 쪽에서도 `safeOnce`로 감싸진다.
- **오류**: 원격 호출 실패는 0.3의 표대로 삼킨다.
- **기존 코드와의 관계**: `ProgressCopyOption.kt:19-37`과 같은 꼴. `ParcelableCopyOptions.kt:33-37`은 그대로 넘긴다. 인자가 없어 `ParcelableArgs`가 필요 없다.

#### `CopyOptions`·`toArray()`·`toCopyOptions()` (`CopyOptions.kt`)
- **추적**: K17 / REQ-36-16
- **알고리즘**: 생성자 끝에 `val cleanupFailureListener: (() -> Unit)?`(기본값 없음). `toArray`: 있으면 `options += CleanupFailureCopyOption(it)`. `toCopyOptions`: `option is CleanupFailureCopyOption -> cleanupFailureListener = option.listener`. 이 가지가 없으면 모든 복사가 `UnsupportedOperationException`이 되므로 U-K04로 지킨다.
- **다시 만드는 곳**: `CopyOptions.kt:67`, `ForeignCopyMove.kt:138`, `LinuxCopyMove.kt:285`, `SftpCopyMove.kt:276`, `SmbCopyMove.kt:220`, `WebDavCopyMove.kt:166`, `FtpCopyMove.kt:161` — 모두 `copyOptions.cleanupFailureListener`를 넘긴다.

#### 백엔드 정리 `catch`
기존 줄은 그대로 두고 아래 호출만 더한다. 함수는 항상 `CleanupFailureCopyOption`에서 왔으므로 호출 쪽에 `try`를 더하지 않는다.

| 백엔드 | 곳 | 더하는 줄 | "이미 없음" |
|---|---|---|---|
| 로컬 | `LinuxCopyMove.kt:119-121` | `if (e.errno != OsConstants.ENOENT) copyOptions.cleanupFailureListener?.invoke()` | ENOENT |
| 공급자 간 | `ForeignCopyMove.kt:69-73` 두 `catch` | `copyOptions.cleanupFailureListener?.invoke()` | `deleteIfExists`라 오지 않음 |
| SFTP | `SftpCopyMove.kt:117-119` | `if (e.toFileSystemException(target.toString()) !is NoSuchFileException) …invoke()` | `:81-84`와 같은 꼴 |
| SMB | `smb/client/Client.kt:405-407` | `cleanupFailureListener?.invoke()` | 해당 없음(SRS L4) |
| FTP | `FtpCopyMove.kt:86-88` | `…invoke()`, 구별하지 않음 | HLD 3.3.1 |
| WebDAV | `WebDavCopyMove.kt:91-93` | `if (e.toFileSystemException(target.toString()) !is NoSuchFileException) …invoke()` | `webdav/DavExceptionExtensions.kt:23` |
| SAF | `DocumentResolver.kt:166-168` | `if (e2.toFileSystemException(targetPath.toString()) !is NoSuchFileException) cleanupFailureListener?.invoke()` | `:358`과 같은 꼴 |

인자 전달:
- SMB: `Client.copyFile`(`Client.kt:317-324`) 끝에 `cleanupFailureListener: (() -> Unit)?`. 호출 `SmbCopyMove.kt:85-88`. 닫기는 아래 함수로 바꾼다.
- SAF: `DocumentResolver.copy`·`copyApi24`·`copyManually`·`move`·`moveApi24`·`moveByCopy`에 같은 인자. 호출 `DocumentCopyMove.kt:44`·`:76`. 
- 대상 파일을 온전히 쓴 뒤의 삭제 실패(`ForeignCopyMove.kt:149-163`, `LinuxCopyMove.kt:298`, `DocumentResolver.kt:128`·`:363` 등)는 깨진 파일이 아니므로 듣는 함수를 부르지 않는다.

#### `internal fun Closeable.closeReportingFailure(shouldReport: () -> Boolean, listener: (() -> Unit)?)` — `CleanupFailureCopyOption.kt`
- **추적**: K17 / REQ-36-16, SRS L4
- **책임**: 닫기 실패를 알리고 같은 예외를 그대로 던진다.
- **알고리즘**: `try { close() } catch (e: Exception) { if (shouldReport()) listener?.invoke(); throw e }`.
- **호출**: `Client.kt:372`의 `targetFile.use {`를 `Closeable { targetFile.closeReportingFailure({ isDeleteRequested }, cleanupFailureListener) }.use {`로 바꾼다. `isDeleteRequested`는 `:404`의 `deleteOnClose()`가 성공한 뒤 켠다. 일반 취소는 닫기가 성공하므로 부르지 않는다. 지우기 표시 자체가 실패한 경우는 이미 `:405-407`에서 알렸고 `safeOnce`가 한 번으로 줄인다.
- **기존 코드와의 관계**: `use`의 예외 합치기(`addSuppressed`)는 그대로다.

#### `class RemoteCopyOutcomeWatch(onDied: () -> Unit)` — `provider/remote/RemoteCopyOutcomeWatch.kt`
- **추적**: K17 / REQ-36-16, SRS L2, HLD D11·R12
- **책임**: 완료와 죽음 중 먼저 온 것만 따른다. 안드로이드 의존 없음. #43 후속이 코루틴 재개에 재사용하므로 정리 실패를 모르고 죽음 때 부를 함수만 받는다.
- `onCompleted(): Boolean`: 처음이면 `true`(`AtomicBoolean.compareAndSet`).
- `onRemoteDied()`: 처음이면 `onDied()`. 어느 스레드든 된다. #36에서 `onDied`는 `CleanupFailureCopyOption.listener`라 던지지 않는다.

#### `RemoteFileSystemProvider.startCopyWatch(onDied: (() -> Unit)?): () -> Unit` (신규, private)
- **추적**: K17 / REQ-36-16, SRS L2
- **책임**: 시도마다 원격 바인더의 죽음을 듣고, 끝낼 함수를 돌려준다.
- **입력·전제**: 작업 스레드.
- **출력·사후조건**: `onDied`가 `null`이면 아무 일도 안 하는 함수.
- **오류**: `remoteInterface.get()`의 `RemoteFileSystemException`은 그대로 나간다(지금도 같은 호출이 같은 예외를 낸다). `linkToDeath`의 `RemoteException`(이미 죽음)은 듣지 않는 함수를 돌려준다. 이어지는 호출이 실패하므로 조각이 생기지 않는다.
- **알고리즘**: `onDied ?: return {}`, `val binder = remoteInterface.get().asBinder()`, `watch = RemoteCopyOutcomeWatch(onDied)`, `recipient = DeathRecipient { watch.onRemoteDied() }`, `binder.linkToDeath(recipient, 0)`, `return { if (watch.onCompleted()) binder.unlinkToDeath(recipient, 0) }`.

#### `RemoteFileSystemProvider.copy`·`move` (`:124-149`, `:152-178`)
- **추적**: K17 / REQ-36-16
- **알고리즘**: 맨 앞에 `val stopWatch = startCopyWatch(options.firstNotNullOfOrNull { (it as? CleanupFailureCopyOption)?.listener })`. 완료 `RemoteCallback`의 첫 줄에서 `stopWatch()`. `try`에 `InterruptedException` 가지 뒤로 `catch (e: Exception) { stopWatch(); throw e }`를 더한다. `call`은 원격이 돌려준 임의의 `IOException`도 던지고(`IInterfaceExtensions.kt:27`), 그 경우 원격이 돌지 않거나 이미 끝났다. `stopWatch`는 `onCompleted`로 걸러 두 번 불려도 안전하다. `InterruptedException` 가지는 그대로이며 `stopWatch`를 부르지 않는다 — 앱이 취소로 빠져나간 뒤에도 원격 완료나 죽음을 기다린다.
- **오류**: 지금과 같다. 재개되지 않는 문제는 고치지 않는다(#43).
- **해제 시점(R12)**: 완료 콜백 또는 죽음. 원격 작업 시작 전에 `future.cancel(true)`가 닿으면 콜백이 오지 않아 다음 죽음에서 늦게 잘못 알릴 수 있다. 캐시 풀이라 창이 좁고 잘못 알리는 쪽이라 받아들인다(요구사항 v5).
- **비용**: 파일당 `linkToDeath`·`unlinkToDeath` 한 쌍. 파일당 이미 바인더 왕복이 있어 작다. 알림은 `onCleanupFailed`로 가므로 2.18의 규칙을 그대로 따른다: 작업이 돌고 있으면 기록해 끝 토스트에 싣고, 이미 끝났으면 따로 알린다.

### 2.18 K18 `FileJobCleanupFailures<T>` — `filejob/FileJobCleanupFailures.kt`
`FileJob`에 `internal val cleanupFailures = FileJobCleanupFailures<Path>()`. 안드로이드 의존 없음, 함수 모두 `@Synchronized`, 잠금 안에서 외부를 부르지 않는다.

#### `onFailed(item: T): Boolean`
- **추적**: K18 ① / REQ-36-16
- **출력·사후조건**: 지금 따로 알려야 하면 `true`.
- **알고리즘**: 이미 알린 항목이면 `false`. 작업이 돌고 있으면 대기에 넣고 `false`(취소 중에도, 끝 토스트에 합치려고). 끝났으면: 성공으로 끝났으면 `false`, 아니면 알린 항목에 넣고 `true`.

#### `onWritten(item: T)`
- **추적**: K18 ② / REQ-36-16
- **알고리즘**: 대기에서 뺀다.

#### `onJobFinished(isSuccessful: Boolean): List<T>`
- **추적**: K18 ③ / REQ-36-16, 18
- **출력·사후조건**: 끝 상태를 기록한다. 성공이면 대기를 비우고 빈 목록. 아니면 대기 전부를 알린 항목으로 옮겨 시작 순서로 돌려준다.
- **기존 코드와의 관계**: "취소되지 않은 작업은 버린다"가 "성공으로 끝난 작업만 버린다"로 바뀌었다(요구사항 v7).

### 2.19 K19 `FileJobEndNotice` — `filejob/FileJobEndNotice.kt`
```kotlin
sealed interface FileJobFailureDetail { object Interrupted; data class Error(val text: String) }
data class FileJobEndNotice<T>(val outcome: FileJobOutcome, val detail: FileJobFailureDetail?, val cleanupFailures: List<T>) { val isLong: Boolean }
```
#### `fun <T> fileJobEndNoticeOf(notifiesCompletion: Boolean, outcome: FileJobOutcome, thrown: Throwable?, cleanupFailures: List<T>): FileJobEndNotice<T>?`
- **추적**: K19 / REQ-36-16, 17, 18
- **책임**: 토스트 내용을 정한다. 순수 함수, 안드로이드 의존 없음.
- **알고리즘**:
  1. `SUCCEEDED`: `notifiesCompletion`이 거짓이면 `null`. 참이면 세부·정리 실패 없는 내용(성공에는 정리 실패를 싣지 않는다).
  2. `CANCELED`: 세부 없음, `cleanupFailures` 실음. 던진 것은 보이지 않는다.
  3. `FAILED`: `thrown`이 `InterruptedIOException`이면 `Interrupted`, 그 밖에 있으면 `Error(thrown.toString())`, 없으면 세부 없음. `cleanupFailures` 실음.
- `isLong` = 세부가 있거나 `cleanupFailures`가 있음.
- **오류**: 던지지 않는다.

#### `internal fun showFileJobEndToast(context: Context, info: FileJobInfo, notice: FileJobEndNotice<Path>)`
- **추적**: K19 / REQ-36-18
- **입력·전제**: 어느 스레드든. `Context.showToast`가 메인으로 넘긴다(`util/ContextExtensions.kt:236-250`).
- **알고리즘**: 첫 줄 `file_job_end_toast_format`(`info.getTitle`, 끝난 방식 글). 세부가 있으면 다음 줄(`Interrupted`면 `file_job_interrupted_toast`, `Error`면 그 글). 정리 실패가 있으면 다음 줄 `cleanupFailureTextOf`. `isLong`이면 `LENGTH_LONG`, 아니면 `LENGTH_SHORT`. `isRtl`은 `context.resources.configuration.layoutDirection`으로 정한다(글만 만들므로 뷰가 없다).

#### `internal fun cleanupFailureTextOf(context: Context, paths: List<Path>): String`
- **알고리즘**: 첫 경로의 `plainFileJobName()`과 `(parent ?: path).plainFileJobName()`. 하나면 `file_job_cleanup_failed_toast_format`, 여럿이면 복수형에 나머지 개수.

### 2.20 K20 `WriteFileJob.run` — `filejob/FileJobs.kt:2266-2269` (#44)
#### `internal inline fun runReportingResult(block: () -> Boolean, report: (Boolean) -> Unit)` — `filejob/FileJob.kt`
- **추적**: K20 / REQ-36-19
- **책임**: 본문이 값을 돌려주든 던지든 결과를 한 번 알린다.
- **알고리즘**: `val result = try { block() } catch (e: Throwable) { report(false); throw e }`, `report(result)`. `report` 자체는 `try` 밖에서 부르므로 두 번 불리지 않는다.
- **오류**: 본문이 던진 것을 그대로 다시 던진다.

#### `WriteFileJob.run()`
- **알고리즘**: `runReportingResult({ write(file, content) }) { successful -> listener?.let { mainExecutor.execute { it(successful) } } }`.
- **기존 코드와의 관계**: 값을 돌려주는 경로는 그대로다. 던지는 경로의 끝난 방식은 `runBody`가 지금처럼 정한다. `TextEditorViewModel.kt:140-154`와 `TextEditorFragment.kt:273-274`는 바꾸지 않는다: `Error`가 되어 `finishWritingFile()`로 메뉴가 돌아오고 토스트를 더 내지 않는다.

## 3. 테스트 설계

### 3.1 단위 테스트
| 테스트 | 대상 함수 | 경우 | 기대 결과 |
|---|---|---|---|
| U-O01 | `FileJobOutcome.of` | 정상 끝, 오류 없음, 표시 꺼짐 | `SUCCEEDED` |
| U-O02 | | 정상 끝, 건너뛴 오류, 표시 꺼짐 | `FAILED` |
| U-O03 | | `InterruptedIOException`, 표시 켜짐 | `CANCELED` |
| U-O04 | | `InterruptedIOException`, 표시 꺼짐 | `FAILED` |
| U-O05 | | `SocketTimeoutException`, 표시 꺼짐 / 켜짐 | `FAILED` / `CANCELED` |
| U-O06 | | `IOException`, `RuntimeException`, 표시 꺼짐 | `FAILED` |
| U-O07 | | `ClosedByInterruptException`, 표시 켜짐 | `CANCELED` |
| U-O08 | | 정상 끝, 표시 켜짐 | `CANCELED` |
| U-J01 | `FileJob.runBody` | 표시 없는 `SocketTimeoutException` | `FAILED`, `thrown` 남음 (#39) |
| U-J02 | | 서비스 취소: 표시 켠 뒤 `InterruptedIOException` | `CANCELED` |
| U-J03 | | 창 취소: 본문이 `throw canceledByUser()` | `CANCELED`, 표시 켜짐 |
| U-J04 | | 정상 끝, `hasUnresolvedError` 참/거짓 | `FAILED`/`SUCCEEDED` |
| U-J05 | | `Error` | 그대로 던짐, `thrown` 없음(끝 토스트는 세부 없는 "실패", SRS 2.1) |
| U-J06 | `FileJob.endOf` | 본문 `SUCCEEDED` 뒤 표시가 켜짐(R13) | `CANCELED`, 취소 내용 |
| U-J07 | | 표시 없는 `InterruptedIOException` 끝 | `FAILED`, 세부 `Interrupted` 토스트 하나 (#39) |
| U-J08 | | `IOException` 끝 | `FAILED`, 세부 `Error` 토스트 하나 |
| U-J09 | | 표시 켠 뒤 `IOException` | `CANCELED`, 세부 없음(원시 예외 안 보임) |
| U-J10 | | `tryClaimEnd`에 짐 + 기록 있음 | `notice` `null`, `separateFailures`에 기록 |
| U-J13 | `FileJob.finishEnd` | `info`를 넣지 않아 `endOf`가 던짐, 표시 꺼짐 / 켜짐 | 던지지 않음, `bodyOutcome` / `CANCELED` |
| U-J11 | `runReportingResult` (#44) | 값 참/거짓 | `report` 그 값으로 한 번 |
| U-J12 | | `InterruptedIOException`, `Error` | `report(false)` 한 번, 같은 예외 |
| U-N01 | `fileJobEndNoticeOf` | `SUCCEEDED`, 알림 참 | 내용, 짧음 |
| U-N02 | | `SUCCEEDED`, 알림 거짓 | `null` |
| U-N03 | | `SUCCEEDED`, 기록 있음 | 정리 실패 없음 |
| U-N04 | | `CANCELED`, 알림 거짓 | 내용 있음(모든 종류), 짧음 |
| U-N05 | | `FAILED`, 알림 거짓, 던진 것 없음 | 내용 있음, 세부 없음, 짧음 |
| U-N06 | | `FAILED` + `InterruptedIOException`·`SocketTimeoutException` | `Interrupted`, 김 |
| U-N07 | | `FAILED` + `IOException` | `Error(toString)`, 김 |
| U-N08 | | `CANCELED` + `IOException` | 세부 없음 |
| U-N09 | | `CANCELED`·`FAILED` + 기록 | 기록 실림, 김 |

| U-S01~04 | `runJobAndReport` | 기존 네 경우, 형만 바꿈 | 성공 `SUCCEEDED`, 실패 `FAILED`, `Throwable`은 `FAILED` 보고·재던짐, 순서 그대로 |
| U-S05 | | `CANCELED` 반환 | `CANCELED` 한 번 |
| 기존 `FileJobBatchTest` | `FileJobBatch` | 변경 없음 | 그대로 통과(REQ-36-09) |
| U-P01 | `percent` | `SIZE` 50/100 | 50 |
| U-P02 | | `SIZE` `size` 5 GB(`Int` 초과), 2.5 GB | 50 |
| U-P03 | | `SIZE` `size` 0, 음수 | `null` |
| U-P04 | | `SIZE` 넘침: 전송 > 전체 | 100 |
| U-P05 | | `COUNT` 3/10 | 30 |
| U-P06 | | `COUNT` `fileCount` 1, 0 | `null` |
| U-P07 | | `SCANNING` | `null` |
| U-P08 | `currentFileIndex` | 0/10, 9/10, 10/10 | 1, 10, 10 |
| U-P09 | `FileJobProgressThrottle` | 처음, 간격 안, 간격 뒤, 정확히 간격 | 참, 거짓, 참, 거짓 |
| U-I01 | `FileJobInfo.of` | 경로 하나 | 이름, 개수 1, 목적지 `null` |
| U-I02 | | 여러 개 + 목적지 | 첫 이름, 개수, 목적지 이름 |
| U-I03 | | 빈 목록 | `""`, 0, 던지지 않음 |
| U-I04 | `plainFileJobName` | 절대 루트 | 구분자 |
| U-I05 | `copyTypeOf` | 참, 거짓 | `EXTRACT`, `COPY` |
| U-I06 | `FileJobType` | 값 개수 | 16 |
| U-I07 | `notifiesCompletion` | 16종 전부, 속성 5종은 재귀·비재귀 | 0.8의 표 |
| U-T01 | `FileJobEntryTable` | 시작 순서 셋, 가운데 진행 갱신 | `entries` 순서 불변 |
| U-T02 | | 같은 id 다시 시작 | `null`, 먼저 값 유지 |
| U-T03 | | 끝난 뒤 진행 값·`setRunning` | `false`, 표 불변 |
| U-T04 | | 두 번 끝남, 없는 id 끝남 | 두 번째 `false` |
| U-T05 | | 대기 중 시작 → `setRunning` | `isPending` 꺼짐 |
| U-T06 | | 대기 중 시작 → 바로 `finish`(대기 중 취소 경로) | `true`, 표에서 사라짐 |
| U-R01 | `fileJobRowStatusOf` | 대기 중, 값 없음 | `Pending` |
| U-R02 | | 진행 중, 값 없음 | `Running` |
| U-R03 | | `SCANNING` | `Scanning` 수·크기 |
| U-R04 | | `SIZE` 파일 1개, 10개 | `Size`, `fileIndex` `null`, 값 |
| U-R05 | | `COUNT` 1개 / 10개 | `Running` / `Count` |
| U-R06 | | `SIZE` 크기 0 | `Running` |
| U-R07 | | 끝 + 값 있음 | `Finished` 우선 |
| U-R08 | `FileJobListRows` | 연 순간 둘 + 새 시작 | 세 줄, 시작 순서 |
| U-R09 | | 빈 시작 | `rows`·`activeIds` 빔 |
| U-R10 | | 끝 → 진행 값 | 줄 유지, 값 불변, `false` |
| U-R11 | | 끝 → 다른 끝 | 먼저 온 것 유지 |
| U-R12 | | 끝난 줄에 정리 실패 | `hasCleanupFailure` 참, `true` |
| U-R13 | | 진행 중 줄에 정리 실패 → 끝 | 끝난 뒤 참 |
| U-R14 | | 없는 줄 보고 넷(진행·끝·정리·실행) | `false`, 줄 불변 |
| U-R15 | | 세 줄 중 하나 끝 | `activeIds` 둘, 끝난 줄 제외, 복사본 |
| U-R16 | | 같은 id 다시 시작(R5) | 줄 하나, `false` |
| U-R17 | | 대기 중 줄 → `onRunning`, 대기 중 줄 → `onProgress` | 둘 다 `Pending`이 아님 |
| U-C06 | `tryClaimEnd` | 두 번 | 첫 번만 참 |
| U-C01 | `throwIfCanceled` | 꺼짐 / 켜짐 | 없음 / 던짐 |
| U-C02 | `throwIfCanceledOrInterrupted` | 둘 다 꺼짐 | 없음 |
| U-C03 | | 끼어들기만 | 던짐, 끼어들기 표시 지워짐 |
| U-C04 | | 취소 표시만(끼어들기 소비됨) | 던짐 |
| U-C05 | | 둘 다 | 던짐, 표시 지워짐 |
| U-F01 | `FileJobCleanupFailures` | 도는 중 실패 → 취소로 끝남 | `false`, 끝에서 목록으로 |
| U-F02 | | 도는 중 실패 → 실패로 끝남 | `false`, 끝에서 목록으로 (v7) |
| U-F03 | | 도는 중 실패 → 성공으로 끝남 | 빈 목록 |
| U-F04 | | 실패 → 다시 시도로 씀 → 취소·실패로 끝남 | 빈 목록 |
| U-F05 | | 항목 둘 | 시작 순서로 둘 다 |
| U-F06 | | 같은 항목 두 번 → 끝 → 또 늦게 | 끝에서 한 번, 늦은 것 `false` |
| U-F07 | | 취소로 끝난 뒤 새 항목 | `true` (루트 늦은 보고) |
| U-F08 | | 실패로 끝난 뒤 새 항목 | `true` |
| U-F09 | | 성공으로 끝난 뒤 새 항목 | `false` |

| U-K01 | `CleanupFailureCopyOption.listener` | 두 번 호출 | 듣는 함수 한 번 |
| U-K02 | | 듣는 함수가 `RuntimeException` | 던지지 않음 |
| U-K03 | | 던진 뒤 다시 호출 | 다시 부르지 않음, 던지지 않음 |
| U-K04 | `toCopyOptions` | 옵션 있음 / 없음 | 함수 있음(거부하지 않음) / `null` |
| U-K05 | `toArray`→`toCopyOptions` 왕복 | 호출 두 번 | 원래 함수 한 번 |
| U-K06 | `toArray` | 듣는 함수 없음 | 옵션 없음 |
| U-K07 | `closeReportingFailure` | 닫기 던짐, 표시 요청함 | 듣는 함수 한 번, 같은 예외 |
| U-K08 | | 닫기 던짐, 표시 요청 안 함 | 안 불림, 같은 예외 |
| U-K09 | | 닫기 성공 | 안 불림(일반 취소, L4) |
| U-W01 | `RemoteCopyOutcomeWatch` | 완료 → 죽음 | `onCompleted` 참, 안 불림 |
| U-W02 | | 죽음 → 완료 | 한 번, `onCompleted` 거짓 |
| U-W03 | | 죽음 두 번 | 한 번 |
| U-M01 | `ForeignCopyMove.copy` (가짜 공급자) | 쓰기 중 `InterruptedIOException`, 지우기 `IOException` | 듣는 함수 한 번, 원래 예외가 나감 |
| U-M02 | | 지우기 `UnsupportedOperationException` | 한 번 |
| U-M03 | | 쓰기 실패, 지우기 성공 / 복사 성공 | 안 불림 |
| U-M04 | | 듣는 함수가 던짐 | 원래 예외가 나감 |
| U-M05 | `ForeignCopyMove.move` | 옵션을 다시 만드는 조건(`COPY_ATTRIBUTES` 없음)에서 U-M01 | 한 번, 원본 안 지움 |
| U-M06 | `ForeignCopyMove.copy` | 쓰기가 표시를 켠 채 `InterruptedIOException`, 닫기·지우기가 표시를 기록 | 둘 다 표시 꺼짐(K14) |

`FileJob` 테스트는 `run()`을 시험마다 정하는 하위 클래스로 만든다(`service`를 쓰지 않는 함수만 부른다). U-I04와 U-M은 `app/src/test/java/me/zhanghai/android/files/filelist/CopyMoveConfirmationTest.kt:176`·`:226`과 같은 꼴의 가짜 경로·파일 시스템을 테스트 파일에 둔다(거기 것은 `private`이고 `provider()`가 `UnsupportedOperationException`을 던진다). U-M에는 `readAttributes`·`newInputStream`·`newOutputStream`·`delete`가 실제로 도는 가짜 `FileSystemProvider`를 새로 쓴다. `isArchivePath` 분기는 JVM에서 `ArchivePath`를 만들지 않으므로 코드 검수와 수동 시험 T8로 본다. `startCopyWatch`의 바인더 연결은 T12와 코드 검수로 본다. 네트워크 재현은 하지 않는다(요구사항 v6). JNI를 쓰는 `LinuxCopyMove`와 다른 백엔드의 `catch`는 코드 검수로, 듣는 함수 호출의 대표 시험은 U-M으로 한다.

`assembleDebug`는 단위 테스트를 돌리지 않는다. 구현 검증 명령은 `testDebugUnitTest`와 `assembleDebug` 둘 다다.

### 3.2 수동 시험
HLD 3.6 T1~T6에 다음을 더한다.

| 항목 | 절차 | 확인 | 추적 |
|---|---|---|---|
| T5 세분 | 0.4의 네 경우 | 관찰 기록만, 설계를 정하지 않음 | REQ-36-12, 16 |
| T7 테마·RTL | 네 테마 조합, RTL 강제, 복사 줄 표시 | 대비, 화살표가 목적지 쪽을 가리킴, 버튼 끝 쪽 | SRS 3.3~3.5 |
| T8 줄 내용 | 파일 3개 복사, 압축 파일 전체 풀기, 삭제, 이름 바꾸기, 저장, 글 편집기 저장 | 종류·이름·진행 글, 압축 파일 이름 | REQ-36-03, 04 |
| T9 목록 수명 | "완료" 줄이 있는 채 회전·홈 복귀·닫기, 두 창 모드 각 메뉴, 고르기 모드 | REQ대로 | REQ-36-01, 14, 15 |
| T10 표시기 | 알림의 값과 목록의 값 비교, 복사 후 취소에 선 사람 넘어짐 | 값 같음, #30 불변 | REQ-36-04, 09 |
| T14 루트 일반 복사 | 루트 모드에서 취소 없이 복사·이동 | 성공. 옵션의 `Parcel` 오류는 모든 루트 복사를 깨는데 JVM에서 시험할 수 없다 | K17, REQ-36-13 |
| T17 저장 메뉴 (#44) | 글 편집기에서 큰 파일 저장 중 목록·알림에서 취소 | 저장 메뉴가 다시 켜지고 다시 저장됨. 토스트는 "취소됨" 하나 | REQ-36-19, 18 |
| T11 진행 막대 전환 | 큰 복사: 훑기 → 크기 | 불확정 → 확정 전환에 오류·멈춤 없음. Material 1.14 소스를 읽지 못해 이 시험이 근거다(미확인) | K12 |

HLD T12(루트 원격 죽음)·T13(SMB·SFTP 일반 취소는 "취소됨" 하나, 지우지 못함 줄 없음)·T15(끝 토스트)·T16(대기 중 취소)을 함께 돈다. T13에서 일반 취소마다 지우지 못함 줄이 붙으면 고치지 말고 PM에 올린다(L6은 수용됨). HLD R1(떠 있는 오류 창)·R2(SMB 서버 복사·루트 정리)·R5는 T1·T2 중 관찰해 PR에 적는다.

### 3.3 코드 검수 체크리스트 (R10·C-1)
1. `provider/` diff가 표 2.14·2.17의 줄, 인자 전달, `Client.kt:372` 닫기 교체, `RemoteFileSystemProvider`의 듣기뿐이고 지우기 호출·조건·순서가 그대로다.
2. 토스트를 내는 곳이 `showFileJobEndToast`, `notifyCleanupFailure`, 기존 저장 `:1735`뿐이고 `Settings`를 읽지 않는다. 듣는 함수를 부르는 곳이 정리 `catch` 일곱 곳, `closeReportingFailure`, `RemoteCopyOutcomeWatch`, `FileJobs.kt:649`·`:651`뿐이다(`grep -n "cleanupFailureListener\|onCleanupFailed"`).
3. `onCleanupFailed`·`notifyCleanupFailure`·`notifyEnd`·`FileJobCleanupFailures`에 입출력·대기(`runBlocking`, `Future.get`, `CountDownLatch`, 잠금 안 외부 호출)가 없다.
4. `FileJobs.kt` diff에서 취소되지 않은 작업의 분기·반환값·오류 창 선택지가 바뀌지 않았다(`WriteFileJob.run`의 듣는 함수만 예외). 창 "취소" 14곳이 모두 `canceledByUser()`다(`grep -n "throw InterruptedIOException()"`에 남는 것은 `.apply`가 붙은 `:496`·`:528`·`:577`뿐이다).
5. 알림 함수 네 곳은 값 알리기 한 줄만 늘었다.
6. `isCanceled`를 `Future.cancel(true)` 앞에서 켠다.

## 4. 설계 피드백
아래는 SRS v2.5·HLD v1.6까지 반영되었다(설계팀 c80b30f).

| ID | 내용 | 처리 |
|---|---|---|
| FB-1 | 훑기 알림 거르기는 100개마다 무조건 통과한다(`FileJobs.kt:330`). SRS 3.2의 "0.5초에 한 번 이하"를 지키려면 따로 거른다. | 2.6·2.7, HLD K7 |
| FB-2 | HLD K16 점검 지점에 `copyOrMove`의 재시도 첫머리가 없었다. "모두 바꾸기"로 재시도하면 취소된 작업이 기존 파일을 지울 수 있다(`FileJobs.kt:1323-1326`). | 2.7, HLD K16 |
| FB-3 | HLD K18 ①이 오류 창 "취소" 뒤의 늦은 보고를 잃었다. | 표시를 켜는 것으로 해소(2.16), HLD K18 |
| FB-4 | HLD K8 콜백에 대기 중 → 진행 중 알림이 없었다. | `onRunning`, HLD K8 |
| FB-5 | HLD 3.1 "같은 작업 스레드"는 루트 진행 콜백(바인더 스레드)에 맞지 않는다. 표에 없는 id는 무시하므로 결론은 같다. | 동작 영향 없음 |
| FB-6 | HLD 3.3.1의 SAF 인자 전달에 `moveApi24`가 빠졌다(`DocumentResolver.kt:318`). | 2.17, HLD 3.3.1 |
| FB-7 | HLD T3 "로컬은 단위 테스트"는 `LinuxCopyMove`가 JNI라 달성할 수 없다(검수 1회차 F1). | U-M + 코드 검수, SRS REQ-36-16·HLD T3 |
| FB-8 | smbj·sshj가 끼어들기 표시를 다시 켜 정리가 곧바로 실패한다. okio도 표시를 지우지 않는다. | 2.14, HLD K14·D12·R11 |
| FB-9 | 창 "취소"를 표시 없이 두면 v6 규칙에서 "실패"가 된다. 압축 정리도 표시 지우기가 필요하다. | 0.7, 2.14, 2.16, HLD K16·K14 |
| FB-10 | 끝 토스트를 `runOn` 끝에 두면 R13이 남고 `Error`를 따로 잡아야 한다. | 서비스 콜백으로 옮김(0.8, 2.2, 2.4), HLD K2·D13·R13 |

### PM 결정 대기
- **R15**: 쓰기 창의 "취소" 버튼(`FileJobs.kt:2309`, `:2317`)은 던지지 않아 끝난 방식이 "실패"다. 요구사항 v3의 "창에서 취소하면 취소"와 어긋난다. 이 LLD는 SRS대로 "실패"로 둔다. "취소됨"으로 하려면 `:2317`의 `NEGATIVE`에서 표시만 켜면 된다(반환값 그대로).
- **재귀 속성 작업의 완료 토스트**: 0.8 표의 개발팀 분류다.
- **R16**: 대기 중 취소(`FileJobService.kt:219`)와 스레드 시작 전 `Future.cancel(true)`는 `WriteFileJob.run`을 부르지 않아 #44 수정이 닿지 않는다. 창은 좁다. PM 지시대로 `run`만 고친다. 막으려면 `notifyCanceledWithoutRun`에서 듣는 함수도 `false`로 불러야 한다.
- **R13의 남은 창**: 마지막 점검 뒤 스레드가 `runningJobs`에서 빠지기 전에 취소가 닿으면 일이 다 끝났어도 줄과 토스트가 "취소됨"이다. 모든 종류에 해당한다. 저장·쓰기는 그때 기존 성공 토스트 뒤에 "취소됨"이 하나 더 나온다. 파일은 온전하다. 기록만 한다.

### 설계 범위 밖으로 남기는 관찰 (고치지 않음)
- 원격이 죽은 작업이 취소 전까지 끝나지 않는 문제는 #43이다(#36 머지 뒤 별도 PR). 이 LLD는 듣기만 더한다.
- 충돌 창의 건너뛰기·닫힘은 `hasUnresolvedError`를 켜지 않아 "완료"로 끝난다(SRS REQ-36-08). 기존 동작이다.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-04 | v1.0 | 첫 판 | SRS v2.2, HLD v1.3 | PM |
| 2026-11-04 | v1.1 | 요구사항 v5·v6 반영: L2·L4 알림(0.6, 2.17), #39 흡수(0.7, 2.1, 2.2, 2.16), 표시 지우기(2.14), R9 닫음. 검수 1회차 F2~F5 반영 | 오너 결정, LLD 1회차 조건부 | PM·검수팀 |
| 2026-11-04 | v1.2 | 검수 2회차 지적 1·3·4·5 반영: L6 PM 판단 사항(0.6), 압축 닫기 관찰(2.14), 해제 `catch`를 `Exception`으로(2.17), 시간 제한 값(2.14) | LLD 2회차 조건부 | 검수팀 |
| 2026-11-04 | v1.3 | 요구사항 v7 끝 토스트(0.8, 2.2, 2.18, 2.19), R13 닫음, #44 흡수(2.20), L6 수용, #43 재사용 대비 | 오너 결정 v7, PM 분류 #44·#43 | PM·오너 |
