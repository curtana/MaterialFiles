# Work Order template

The dev team writes this **in English** from the review-approved LLD and posts it as a **comment on the GitHub issue** after PM approval. Revisions are new comments with a bumped version (v1 → v2 …); never edit an old version. Implementation and review follow the latest approved version.

```markdown
## 📋 Work Order v1

| Item | Value |
|---|---|
| Issue | #<number> (milestone v<X.Y.Z>) |
| Branch | feature/<number>-<topic> (base: develop) |
| Design docs | docs/design/<number>-<topic>/{SRS,HLD,LLD}.md @ <develop commit> |
| Review | required / self-check (PROCESS_ROLES §4) |
| Approved by PM | <YYYY-MM-DD> |

### Goal
<What must exist when this is done, in 1–3 sentences>

### Scope (from LLD)
| LLD § | Function | New/Modified | File |
|---|---|---|---|

### Steps
1. <Concrete step, referencing the LLD section>

### Tests to write
- <Test name — LLD §3 case — expected result>

### Definition of Done
- [ ] Every function in Scope implemented as specified in the LLD (deviations raised as design feedback, not silently changed)
- [ ] Tests from "Tests to write" added and passing
- [ ] Build passes (`JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug --console=plain 2>&1 | tail -n 30`)
- [ ] PR to develop with a review request listing focus points and important notes
- [ ] Leftover check (`check_leftovers.sh`) output attached to the final report

### Constraints
- <Task-specific constraints — what not to do and why>
```

## Procedure
1. Dev team: check `WORK_HOLD` / `일시정지` label → write the work order from the approved LLD → send the draft to PM.
2. PM: approve (or return with comments) → the dev team posts it with `gh issue comment <number> -F <file>`.
3. Any later change to scope goes back through the LLD (and SRS/HLD if needed) first, then a new work order version.
