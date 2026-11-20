# LLD — 심볼릭 링크 '바꾸기' 복사가 성공 뒤에도 충돌로 되돌아가는 결함 (이슈 #47)

개발팀이 SRS·HLD를 바탕으로 **함수 단위**로 쓴다. 이 문서가 영문 작업지시서와 구현·검수의 기준이다.

| 항목 | 내용 |
|---|---|
| 상태 | 검수 통과, 구현됨(PR #63, v1.7.5-beta.2) |
| 근거 | SRS v1.2, HLD v1.2 (설계 브랜치 b161ad0, 코드 기준 develop 7cf16b7) |

## 1. 변경 파일
경로 앞부분 `app/src/main/java/me/zhanghai/android/files/`(테스트는 `app/src/test/java/me/zhanghai/android/files/`)은 생략한다.

| 파일 | 신규/수정 | 요지 |
|---|---|---|
| `provider/common/SymbolicLinkCopy.kt` | 신규 | 공용 함수 `createSymbolicLinkForCopy` (2-1) |
| `provider/linux/LinuxCopyMove.kt` | 수정 | `copy`의 링크 분기(152-181)를 공용 함수 호출로 바꿈 (2-2) |
| `provider/sftp/SftpCopyMove.kt` | 수정 | `copy`의 `SYMLINK` 분기(158-188)를 공용 함수 호출로 바꿈 (2-3) |
| `provider/smb/SmbCopyMove.kt` | 수정 | `copy`의 재분석 지점 분기(113-143)를 공용 함수 호출로 바꿈 (2-4) |
| `provider/common/SymbolicLinkCopyTest.kt` (test) | 신규 | 공용 함수 JVM 단위 테스트 (3절) |

5개 파일(HLD 6절, PROCESS_ROLES 11-1 한도 안). `FileJobs.kt`, 일반 파일·폴더 분기, `move`는 건드리지 않는다(SRS 3절).

## 2. 함수 명세

### 2-1. `me.zhanghai.android.files.provider.common.createSymbolicLinkForCopy(replaceExisting: Boolean, createLink: () -> Unit, deleteExisting: () -> Unit): Unit`
- **추적**: HLD 링크 바꾸기 흐름(신규 공용 함수), D1·D2·D3 / REQ-47-01, 02, 03, 04, 05
- **책임**: 복사 중 링크 하나를 만들고, 이름이 이미 있고 '바꾸기'면 지운 뒤 한 번 다시 만들어 **정상 반환**한다.
- **선언**: 최상위 함수, `@Throws(IOException::class)`. `inline`으로 하지 않는다(테스트에서 람다 호출 횟수를 세기 쉽고, 호출부 3곳이라 이득이 없음).
- **입력·전제**:
  - `replaceExisting` — `CopyOptions.replaceExisting`(`provider/common/CopyOptions.kt:12-`) 그대로.
  - `createLink` — 백엔드의 링크 만들기 1회. 실패하면 **이미 `FileSystemException`으로 바꾼 예외**를 던진다. "이름이 이미 있음"은 `java8.nio.file.FileAlreadyExistsException`으로만 나타내야 한다(백엔드 변환 규칙은 2-2~2-4).
  - `deleteExisting` — 백엔드의 대상 지우기 1회. 실패하면 바꾼 예외를 던지고, "대상이 이미 없음"은 `java8.nio.file.NoSuchFileException`으로 나타낸다.
  - 호출 스레드: 파일 작업 서비스의 작업 스레드(동기, 현행과 같음). 상태를 갖지 않는다.
- **출력·사후조건**: 정상 반환이면 대상 경로에 `createLink`가 만든 링크가 있다. 호출부는 이어서 기존 성공 경로(진행량 보고 → 속성 단계)로 간다.
- **오류**:
  - 첫 `createLink`가 `FileAlreadyExistsException`이 아닌 예외 → 그 예외를 그대로 던진다. 지우지 않는다(SFTP v3의 일반 실패가 여기 해당, D2).
  - 첫 `createLink`가 `FileAlreadyExistsException`이고 `replaceExisting == false` → 그 예외를 그대로 던진다(현행 충돌 알림).
  - `deleteExisting`이 `NoSuchFileException`이 아닌 `IOException` → 그 예외에 첫 예외를 `addSuppressed`하고 던진다. 다시 만들지 않는다(REQ-47-02 후단).
  - 다시 만든 `createLink`가 `IOException` → 그 예외에 첫 예외를 `addSuppressed`하고 던진다. 되돌리기·추가 재시도 없음(D3, REQ-47-02).
  - `IOException`이 아닌 예외(런타임 오류)는 잡지 않는다.
- **알고리즘**:
  ```
  try { createLink(); return }
  catch (e: FileAlreadyExistsException) {
      if (!replaceExisting) throw e
      try { deleteExisting() }
      catch (e2: NoSuchFileException) { /* 이미 없음 — 무시 */ }
      catch (e2: IOException) { e2.addSuppressed(e); throw e2 }
      try { createLink() }
      catch (e2: IOException) { e2.addSuppressed(e); throw e2 }
  }
  // 여기서 정상 반환 — 결함 수정의 핵심(현행은 이 자리에서 e를 던진다)
  ```
- **기존 코드와의 관계**: 세 백엔드의 같은 구조(`LinuxCopyMove.kt:158-179`, `SftpCopyMove.kt:163-186`, `SmbCopyMove.kt:118-141`)를 하나로 대체한다. 현행과 다른 점은 다음뿐이다.
  1. (수정) 다시 만들기 성공 뒤 첫 예외를 던지지 않고 정상 반환한다(현행 `LinuxCopyMove.kt:177-178`, `SftpCopyMove.kt:185`, `SmbCopyMove.kt:139-140`).
  2. (부수) suppressed가 실제로 던지는 예외에 남는다. 현행은 백엔드 예외(`SyscallException`/`ClientException`)에 붙인 뒤 `toFileSystemException`으로 새 예외를 만들어 던지므로, suppressed는 던지는 예외에 직접 붙지 않고 그 cause(`initCause` — `SyscallException.kt:69`, sftp `ClientException.kt:47`, smb `ClientException.kt:75`)에 있었다(`getCause().suppressed`로만 도달). 새 코드는 던지는 예외에 직접 붙인다. HLD D3("suppressed는 현행 코드와 같다")와 위치만 한 단계 다르고, 사용자에게 보이는 결과는 같다.

### 2-2. `me.zhanghai.android.files.provider.linux.LinuxCopyMove.copy(source: ByteString, target: ByteString, copyOptions: CopyOptions)` — 링크 분기만 수정
- **추적**: HLD 로컬 링크 분기 / REQ-47-01, 02
- **책임**: `S_ISLNK` 분기(`LinuxCopyMove.kt:152-181`)에서 `readlink`는 그대로 두고, 158-179를 `createSymbolicLinkForCopy` 호출로 바꾼다.
- **입력·전제**: 현행과 같음. `sourceTarget`은 `Syscall.readlink(source)`(153-157) 결과.
- **출력·사후조건**: 공용 함수가 정상 반환하면 180행 `copyOptions.progressListener?.invoke(sourceStat.st_size)`와 184행 이하 속성 단계(`lchown` 등)가 실행된다 — 현행 결함으로 건너뛰던 부분(SRS 1절).
- **오류**: 공용 함수의 예외를 그대로 전파.
- **알고리즘** (람다):
  ```kotlin
  createSymbolicLinkForCopy(
      copyOptions.replaceExisting,
      createLink = {
          try {
              Syscall.symlink(sourceTarget, target)
          } catch (e: SyscallException) {
              e.maybeThrowInvalidFileNameException(target.toString())
              throw e.toFileSystemException(target.toString())
          }
      },
      deleteExisting = {
          try {
              Syscall.remove(target)
          } catch (e: SyscallException) {
              throw e.toFileSystemException(target.toString())
          }
      }
  )
  ```
- **기존 코드와의 관계**:
  - '이미 있음' 판정: 현행 `e.errno == OsConstants.EEXIST`(161) ↔ 변환 후 `FileAlreadyExistsException`. `SyscallException.toFileSystemException`(`SyscallException.kt:58-69`)에서 `FileAlreadyExistsException`이 되는 것은 `EEXIST`뿐이라 같다. '없음' 판정 `ENOENT`(165) ↔ `NoSuchFileException`도 `ENOENT`뿐(66행)이라 같다. 판정을 람다 안의 변환으로 넘기는 것이 HLD "판정은 인자로 받는다"의 구현이다.
  - `maybeThrowInvalidFileNameException`(`SyscallException.kt:43-48`, `EINVAL`일 때만)은 현행처럼 첫 만들기 실패에 적용된다. 다시 만들기 실패에도 적용되게 되지만, 첫 시도가 `EEXIST`였다면 이름은 유효하므로 실제 차이는 없다.
  - 일반 파일·폴더 분기, 51-53행 충돌 판정은 그대로.

### 2-3. `me.zhanghai.android.files.provider.sftp.SftpCopyMove.copy(source: SftpPath, target: SftpPath, copyOptions: CopyOptions)` — `SYMLINK` 분기만 수정
- **추적**: HLD SFTP 링크 분기, D2 / REQ-47-03, 02
- **책임**: `FileMode.Type.SYMLINK` 분기(158-188)에서 `readlink`(159-163)는 그대로 두고 164-186을 공용 함수 호출로 바꾼다.
- **출력·사후조건**: 정상 반환이면 187행 진행량 보고. 그 뒤 속성 단계는 링크면 건너뛰는 현행(`SftpCopyMove.kt:193`) 그대로.
- **오류**: 공용 함수의 예외를 그대로 전파.
- **알고리즘** (람다):
  ```kotlin
  createSymbolicLinkForCopy(
      copyOptions.replaceExisting,
      createLink = {
          try {
              Client.symlink(target, sourceTarget)
          } catch (e: ClientException) {
              throw e.toFileSystemException(target.toString())
          }
      },
      deleteExisting = {
          try {
              Client.remove(target)
          } catch (e: ClientException) {
              throw e.toFileSystemException(target.toString())
          }
      }
  )
  ```
- **기존 코드와의 관계**: '이미 있음' 판정은 현행(168행 `exception is FileAlreadyExistsException`)과 같다 — sftp `ClientException.kt:38-39`에서 `FILE_ALREADY_EXISTS`일 때만(D2, 오너 2026-11-19). v3 서버의 일반 실패(`ClientException.kt:46`)는 2-1 첫 오류 규칙으로 그대로 던져지고 대상은 남는다(HLD 6절). `Client.symlink`/`Client.remove`는 `sftp/client/Client.kt:149`/`86`.

### 2-4. `me.zhanghai.android.files.provider.smb.SmbCopyMove.copy(source: SmbPath, target: SmbPath, copyOptions: CopyOptions)` — 재분석 지점 분기만 수정
- **추적**: HLD SMB 링크 분기 / REQ-47-04, 02
- **책임**: `sourceIsReparsePoint` 분기(113-143)에서 `readSymbolicLink`(114-118)는 그대로 두고 119-141을 공용 함수 호출로 바꾼다.
- **출력·사후조건**: 정상 반환이면 142행 진행량 보고와 그 뒤 속성 단계.
- **오류**: 공용 함수의 예외를 그대로 전파.
- **알고리즘** (람다):
  ```kotlin
  createSymbolicLinkForCopy(
      copyOptions.replaceExisting,
      createLink = {
          try {
              Client.createSymbolicLink(target, sourceReparseData, attributesToCopy)
          } catch (e: ClientException) {
              e.maybeThrowInvalidFileNameException(target.toString())
              throw e.toFileSystemException(target.toString())
          }
      },
      deleteExisting = {
          try {
              Client.delete(target)
          } catch (e: ClientException) {
              throw e.toFileSystemException(target.toString())
          }
      }
  )
  ```
- **기존 코드와의 관계**: 판정은 현행(123행)과 같다 — smb `ClientException.kt:58-59`(`STATUS_OBJECT_NAME_COLLISION`만). `maybeThrowInvalidFileNameException`(`ClientException.kt:42-47`)은 2-2와 같은 이유로 다시 만들기 실패에도 적용되지만 실제 차이 없음. `Client.createSymbolicLink`/`Client.delete`는 `smb/client/Client.kt:177`/`266`.

## 3. 테스트 설계
JUnit 4(`app/build.gradle:222`), 기존 `provider/common/*Test.kt`와 같은 형식. 가짜 람다가 호출 순서를 `MutableList<String>`에 기록하고, 미리 정한 예외 목록을 차례로 던진다(`java8.nio.file` 예외는 순수 JVM 클래스라 JVM에서 만들 수 있음). 실행: `./gradlew testDebugUnitTest --tests '*SymbolicLinkCopyTest'`.

| 테스트 | 대상 함수 | 경우 | 기대 결과 | REQ |
|---|---|---|---|---|
| ⓪ `noExisting_createdOnce` | 2-1 | 첫 만들기 성공 | 정상 반환, 호출 `[create]` | 01 |
| ① `existing_replace_deletesAndRecreates` | 2-1 | 첫 만들기 `FileAlreadyExistsException`, 바꾸기, 지우기·다시 만들기 성공 | **정상 반환**, 호출 `[create, delete, create]`(만들기 2·지우기 1) | 01, 03, 04 |
| ② `existing_replace_recreateFails_throwsWithoutRetry` | 2-1 | ①에서 다시 만들기가 `FileSystemException("x")` | 그 예외(같은 객체)가 던져짐, `suppressed`에 첫 `FileAlreadyExistsException`, 호출 `[create, delete, create]` — 추가 호출 없음 | 02 |
| ③ `existing_replace_deleteNoSuchFile_ignored` | 2-1 | 지우기가 `NoSuchFileException`, 다시 만들기 성공 | 정상 반환, 호출 `[create, delete, create]` | 01, 02 |
| ④ `existing_noReplace_throwsOriginal` | 2-1 | 첫 만들기 `FileAlreadyExistsException`, 바꾸기 아님 | 그 예외(같은 객체), 호출 `[create]` | 01(전제), SRS 4절 |
| ⑤ `existing_replace_deleteFails_throwsWithoutRecreate` | 2-1 | 지우기가 `AccessDeniedException` | 그 예외, `suppressed`에 첫 예외, 호출 `[create, delete]` | 02 |
| ⑥ `otherFailure_replace_notDeleted` | 2-1 | 첫 만들기 일반 `FileSystemException`(SFTP v3 상황), 바꾸기 | 그 예외(같은 객체), 호출 `[create]` — 지우지 않음 | 03(D2) |
| ⑦ `existing_replace_recreateAlreadyExists_throwsWithoutRetry` | 2-1 | ①에서 다시 만들기도 `FileAlreadyExistsException`(경쟁 조건, HLD 6절) | 두 번째 예외(같은 객체)가 던져짐, `suppressed`에 첫 예외, 호출 `[create, delete, create]` — 세 번째 시도 없음 | 02 |

①~⑤는 HLD 4절의 ①~⑤와 같은 번호다. ⓪·⑥·⑦은 LLD에서 더했다(⑦은 HLD 6절 경쟁 조건에서 반복하지 않음을 고정한다. ⑥은 D2의 "일반 실패면 지우지 않음"을 공용 함수 수준에서 고정한다. HLD v1.1의 'SFTP 판정 함수' 테스트와 달리 새 판정 함수를 두지 않는다).

### 3-1. 수정 전 실패 증명 (두 커밋, REQ-47-05)
1. **커밋 1** (`Refactor: Extract symlink copy into common function (#47)`): 2-1 함수를 **현행 동작 그대로** 만든다 — 2-1 알고리즘에서 `catch` 블록 끝(다시 만들기 성공 뒤)에 `throw e`를 둔다(현행 `LinuxCopyMove.kt:177-178`과 같음). 2-2~2-4의 호출부 교체와 3절 테스트 전부를 같은 커밋에 넣는다. 이 커밋에서 `testDebugUnitTest --tests '*SymbolicLinkCopyTest'`를 돌리면 **①·③이 실패**(정상 반환 대신 `FileAlreadyExistsException`)하고 ⓪·②·④·⑤·⑥·⑦은 통과해야 한다 — 이 테스트들은 바뀌지 않아야 할 동작을 고정한다. 출력을 PR에 붙인다.
2. **커밋 2** (`Fix: Finish symlink replace after recreating the link (#47)`): 그 `throw e` 한 줄만 지운다(2-1 알고리즘대로). 같은 명령에서 8개 모두 통과, 출력을 PR에 붙인다.
- 커밋 1은 의도적으로 테스트가 실패하는 중간 커밋이다. PR은 두 커밋을 squash하지 않고 머지한다(PM 판단).

### 3-2. 에뮬레이터 재현·확인 계획 (REQ-47-01, 05)
- 환경: 로컬 AVD `Medium_Phone_API_36`(google_apis_playstore, x86_64 — `adb root` 불가). 오너 기기에는 설치하지 않는다(사전 승인 필요). 디버그 빌드는 debuggable이라 `run-as`로 앱 전용 폴더에 링크를 만들 수 있다(외부 저장소 `/sdcard`는 심볼릭 링크를 지원하지 않음).
- 준비(매 실행 전 초기화):
  ```
  # Git Bash 기준. 바깥을 큰따옴표로 감싸 기기 셸에 한 덩어리로 넘긴다(adb가 인자를 합쳐 다시 해석하므로).
  adb shell "run-as me.zhanghai.android.files sh -c 'rm -rf files/t47 && mkdir -p files/t47/src files/t47/dst && ln -s /system/etc/hosts files/t47/src/a && ln -s /system/bin/sh files/t47/src/b && echo old > files/t47/dst/a && echo old > files/t47/dst/b'"
  ```
- 절차: 앱에서 경로 `/data/user/0/me.zhanghai.android.files/files/t47/src`로 이동 → `a`,`b` 선택 → 복사 → `t47/dst`에서 붙여넣기 → 충돌 창에서 '모두 적용' 체크 + '바꾸기'.
- **수정 전**(develop 빌드, 구현 착수 전): 충돌 창이 다시 뜨거나 작업이 끝나지 않는 것을 화면 캡처·`adb shell run-as ... ls -l files/t47/dst`로 기록하고 취소한다. 증상이 SRS와 다르면(예: 한 번에 끝남) 구현하지 말고 PM에 보고한다(SRS 4절 가정, PROCESS_ROLES 11-1).
- **수정 후**(커밋 2 빌드): 같은 절차에서 작업이 한 번에 끝나고 `ls -l`이 `a -> /system/etc/hosts`, `b -> /system/bin/sh`을 보여야 한다.
- 앱 경로 입력으로 앱 전용 폴더에 들어갈 수 없으면 재현 방법이 막힌 것으로 PM에 보고한다(다른 경로를 임의로 쓰지 않음).
- SFTP·SMB 실서버: 이번 환경에 서버가 없으므로 PR에 "미확인"으로 적는다(SRS REQ-47-03·04).

### 3-3. 검수에서 코드로 확인할 것 (JVM 밖)
- 2-2~2-4의 람다가 각 백엔드 예외를 `toFileSystemException`으로 바꿔 던지는지, `maybeThrowInvalidFileNameException` 위치(Linux·SMB의 `createLink`)가 맞는지(HLD 4절 끝).
- 빌드: `./gradlew assembleDebug` 통과.

## 4. 설계 피드백
- SRS·HLD와 어긋나는 것 없음. 설계팀 문의 없음.
- 참고(설계 변경 아님): ① HLD의 "판정을 인자로 받는다"는 판정 함수 인자가 아니라 **람다 안에서 백엔드 예외를 `FileSystemException`으로 바꾸는 것**으로 구현한다 — 변환 규칙상 `FileAlreadyExistsException`/`NoSuchFileException`이 되는 경우가 현행 판정(`EEXIST`/`ENOENT`, `FILE_ALREADY_EXISTS`, `STATUS_OBJECT_NAME_COLLISION`)과 정확히 같아서 동작이 같다(2-2~2-4). ② 커밋 1에서 실패하는 테스트는 ①·③뿐이다(HLD 4절 "①이 실패함" 기준. ③도 다시 만들기 성공 경로라 함께 실패한다). ⓪·②·④·⑤·⑥·⑦은 수정 전후 모두 통과해야 하는 테스트다. ③ suppressed 위치가 cause에서 던지는 예외로 한 단계 옮겨지는 부수 변화(2-1 끝). HLD D3의 "suppressed는 현행 코드와 같다"와 위치만 다르고 의미(첫 실패를 함께 남김)는 같아서 설계 문의는 하지 않았다. ④ 커밋 1은 공용 함수 수준에서만 "현행 그대로"다(suppressed 위치·`maybeThrowInvalidFileNameException` 범위는 이미 새 형태). 실제 백엔드가 수정 전에 실패한다는 증명은 3-2 에뮬레이터 재현이 맡는다.

## 5. 변경 이력
| 일자 | 버전 | 변경 | 사유 | 요청자 |
|---|---|---|---|---|
| 2026-11-19 | v1 | 최초 작성 | SRS v1.2, HLD v1.2 | PM |
| 2026-11-19 | v1.1 | 2-1 suppressed 현행 설명 정정, 줄 번호 2곳(165·123), 3-2 adb 따옴표, 테스트 ⑦ 추가, 4절 ③④ | LLD 검수 1차(경미 4, 조건부) | reviewer |
| 2026-11-20 | v1.1.1 | 머리표 상태에 구현됨(PR #63) 표시. 내용 변경 없음 | 릴리스 v1.7.5-beta.2 문서 동기화 | 설계팀 |
