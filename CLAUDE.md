# MeterialFiles

Material Design open-source file manager for Android (NIO2 backend, root/NAS/archive support)

## 일하는 방식
- 이 저장소는 **매니저(메인 세션) + 서브에이전트 팀** 구조로 개발한다. 역할·절차·규칙의 정본은 [docs/PROCESS_ROLES.md](docs/PROCESS_ROLES.md)다 — 작업을 시작하기 전에 0~12장을 읽는다.
- 에이전트 정의: `.claude/agents/`, 실행 통제 후크·잔여 점검: `.claude/tools/`, 반복 워크플로: `.claude/workflows/dev-verify.js`.
- 테스트: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug --console=plain 2>&1 | tail -n 30`
- 개발 순서: PM(요구사항 정리) → 설계팀(SRS → 검수 → HLD → 검수) → 개발팀(함수 중심 LLD → 검수 → 영문 작업지시서) → 개발팀(feature 구현 + 검증 코드 → 검수) → PM이 develop에 머지. 같은 주제 3회면 멈추고 PM 보고(PROCESS_ROLES 3·5장).
- 이슈·마일스톤·PR은 GitHub(curtana/MaterialFiles)로 관리한다. 모든 병합은 PR로. 포크이므로 `gh`에는 `--repo curtana/MaterialFiles`.
- 브랜치: `design/<이슈>-<주제>`·`feature/<이슈>-<주제>`(develop에서 분기) → develop → master(PR) → `release/v<X.Y.Z>`(master에서 분기, 태그·빌드). release에는 직접 커밋하지 않는다 — 수정은 develop을 거쳐 새 release로.
- 설계 문서: `docs/design/<이슈>-<주제>/{SRS,HLD,LLD}.md`(한국어), 작업지시서·코드·커밋 메시지는 영어. 릴리스 노트는 `CHANGELOG.md`의 그 버전 절.
- 임시 파일: `G:\tmp\MeterialFiles\<작업명>`(지워도 되는 것만), 남길 결과: `_workspace/`(git 제외)
