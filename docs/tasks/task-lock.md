---
task: task-lock
status: done
started: 2026-11-04
branch: task-lock
---

<!-- The filename MUST be <branch-name>.md, matching the `branch:` field above. -->

# Package paths: every file's `package` matches its directory

The branch `task-lock` already carries `TaskLockService` (commit `a9413e2`) and the 1.12.30 version bump
(`c4dae37`). This document covers the second task done on it: making every package match its path, and moving
the Task family and `RecaptchaService` out of the top-level `models` / `services` packages.

## 1. Context Card

**Goal (one line):** no source file whose `package` differs from its directory; Task classes and
`RecaptchaService` live in `xcala.play.models` / `xcala.play.services` like every other model and service.

**Definition of Done:**
- [x] The path-vs-package check (§7) reports 0 mismatches over all 152 `.scala` files in `app/` and `test/`.
- [x] No directory under `app/` or `test/` has a dot in its name.
- [x] The jar's file list differs from the base only by the 18 intended class moves (§7).
- [x] `sbt compile Test/compile package` from clean succeeds; no new warnings (base 1, branch 1).
- [x] `sbt testOnly *` is green.
- [x] `CLAUDE.md` describes the new layout.
- [ ] IntelliJ no longer shows "Package name … does not correspond to the file path" — **only the user can check
      this**, after re-importing the project.
- [x] Independent code review read the change: no code defects; two doc inaccuracies fixed (§7).

**Status now:** implemented, verified and reviewed; staged, not committed. Next: commit and push `task-lock`;
then fix consumer imports (§8).

**Key files:**
| File | Why it matters |
|---|---|
| `app/xcala/play/models/Task*.scala` | package `models` → `xcala.play.models` (`TaskBased` lives in `TaskBasedWithEither.scala:10`) |
| `app/xcala/play/services/{TaskService,TaskLockService,RecaptchaService}.scala` | package `services` → `xcala.play.services` |
| `app/views/xcala/play/invisibleButtonExtended.scala.html:2` | injects `RecaptchaService`; must use `_root_.` |
| `app/views/html/xcala/play/` | hand-written Scala helpers in the templates' package |
| `CLAUDE.md` § Source layout | the layout rule for future work |

**Load-bearing facts:**
1. This is a **breaking change for consumers**: any app on ≤1.12.29 that uses `models.{Task,TaskStatus,TaskOwner,
   TaskBased,TaskBasedWithEither}` or `services.{TaskService,RecaptchaService}` stops compiling on 1.12.30 until it
   imports from `xcala.play.*`. `TaskLock`/`TaskLockService` are new in 1.12.30; only icfi-reg's SNAPSHOT uses them (§4).
2. Inside a Twirl template, `xcala.play.x` resolves to `views.html.xcala.play.x`; Scala code needs `_root_.` (§3).
3. 1.12.30 has not been published: CI publishes only from `master`, and `origin/master` is `deffa0e` (§3).

---

## 2. Existing Behaviour (base commit `c4dae37`)

- Source dirs had dotted names: `app/xcala.play/`, `app/xcala.play.cross/`, `app/xcala.play.postgres/`,
  `app/views/xcala.play/` (`find app -type d` at the base).
- 12 files had a `package` that did not match their path even when reading the dot as a separator:
  - `models`: `Task`, `TaskLock`, `TaskOwner`, `TaskStatus`, `TaskBasedWithEither` (in `app/xcala.play/models/`;
    the last file also declares `trait TaskBased`).
  - `services`: `TaskService`, `TaskLockService`, `RecaptchaService` (in `app/xcala.play/services/`).
  - `views.html.xcala.play[.bootstrap3]`: `grid`, `briefHtml`, `DefaultHelpers`, `InputParser`
    (in `app/views/xcala.play/[bootstrap3/]`).
- `invisibleButtonExtended.scala.html:2` injected `services.RecaptchaService`.
- `TaskLockServiceSpec` (package `xcala.play.services`) imported `services.TaskLockService`.

---

## 3. Discovered Logic

### IntelliJ reads a dotted directory as one package segment
**Claim:** `app/xcala.play/utils/X.scala` with `package xcala.play.utils` is flagged as not matching the path
`` `xcala.play`.utils ``. scalac ignores paths, so the build never complained.
**Evidence:** the inspection text the user reported; base build log has no error (§7).
**Confirmed:** 2026-11-04
**Consequence:** the fix is nested directories, not package changes. Every file moved.

### Bare `xcala.…` inside a template resolves under `views.html`
**Claim:** in `app/views/xcala/play/*.scala.html`, `xcala.play.services.RecaptchaService` fails with
"object services is not a member of package views.html.xcala.play". `_root_.xcala.play.services.RecaptchaService`
compiles.
**Evidence:** branch build error at `invisibleButtonExtended.scala.html:2:64`, then exit 0 after adding `_root_.`.
**Confirmed:** 2026-11-04
**Consequence:** any template that references library Scala code must use `_root_.`. Recorded in `CLAUDE.md`.

### Twirl derives the template package from the directory path
**Claim:** moving templates from `views/xcala.play/` to `views/xcala/play/` keeps the generated package
`views.html.xcala.play`.
**Evidence:** jar diff in §7 shows no change under `views/`.
**Confirmed:** 2026-11-04

### 1.12.30 is unpublished
**Claim:** `task-lock` is not merged. `origin/master` is `deffa0e`, and CI publishes only from `master`.
**Evidence:** `git log --oneline -1 origin/master` → `deffa0e Merge branch 'io-either' into 'master'`;
`git branch -a --contains HEAD` → only `task-lock`, `origin/task-lock`.
**Confirmed:** 2026-11-04
**Consequence:** the package move ships in 1.12.30; no extra version bump is needed.

---

## 4. Blast Radius

Consumers on this machine, found by grep of their `app/` and `test/` (local clones, may be behind their remotes):

| Affected | How | Evidence | Risk |
|---|---|---|---|
| `icfi-reg` (on `1.12.30-SNAPSHOT`) | imports `services.TaskLockService`, `services.TaskService`, `models.{TaskBasedWithEither,TaskOwner,TaskStatus}` | `app/repositories/ProductImportTaskRepository.scala:6`, `app/controller/front/DynamicFormController.scala:5-7`, `test/repositories/ProductImportTaskRepositorySpec.scala:11`, `test/controller/front/BookHouseImportTaskSpec.scala:9` | stops compiling until imports change |
| `icfi-reg`, `icfi-int-reg`, `icfi-org-subsidies`, `icfi-subsidies`, `paperds`, `tibf`, `tibf-hr` (all on 1.12.28 except icfi-reg) | reference `RecaptchaService` / `TaskService` / Task models, mostly through `import services._` / `import models._` | `grep -rlE '\b(TaskService\|TaskLockService\|RecaptchaService\|TaskBasedWithEither\|TaskOwner\|TaskStatus)\b'` over their `app/` | compile errors on upgrade; fix is an added `import xcala.play.services._` / `xcala.play.models._` or explicit imports |
| Apps that inject `views.html.xcala.play.invisibleButtonExtended` | constructor param type is now `xcala.play.services.RecaptchaService` | `invisibleButtonExtended.scala.html:2` | none with Guice injection; manual construction must change |
| `shop` | has its own `services.TaskService`, `services.RecaptchaService`, `models.TaskBased` | no `xcala-play` dependency in its `build.sbt` | not affected |

The class moves remove the split-package risk: before, an app with its own `models.Task` or
`services.RecaptchaService` would clash with the jar's class of the same name.

---

## 5. Target Logic

| Aspect | Before (§2) | After |
|---|---|---|
| Source dirs | `app/xcala.play*/`, `app/views/xcala.play/` | `app/xcala/play/{,cross/,postgres/}`, `app/views/xcala/play/` |
| Task family (incl. `TaskBased`) | package `models` / `services` | `xcala.play.models` / `xcala.play.services` |
| `RecaptchaService` | package `services` | `xcala.play.services` |
| Scala helpers in views | `app/views/xcala.play/` | `app/views/html/xcala/play/` (package unchanged) |
| All other classes | — | same fully-qualified names (jar diff, §7) |

---

## 6. Decision Log

### D-001 — Move Task family and RecaptchaService into `xcala.play.*` — 2026-11-04 — status: accepted
**Context:** they were deliberately in the top-level `models` / `services` packages so apps could import them like
their own (old `CLAUDE.md`).
**Decision:** the user chose consistency with every other model and service, and accepted that consumers must
change their imports.
**Rejected alternatives:**
- Keep the packages, move the files to `app/models/`, `app/services/` — rejected by the user: it fixes the path
  but keeps the inconsistency.
**Consequences:** breaking change in 1.12.30; every consumer upgrading needs import changes (§4).

### D-002 — Do the work on `task-lock`, keep version 1.12.30 — 2026-11-04 — status: accepted
**Context:** a separate branch would need its own release.
**Decision:** the user asked to do it on `task-lock`. 1.12.30 is unpublished (§3), so it ships both changes.

### D-003 — Scala helpers to `app/views/html/xcala/play/` — 2026-11-04 — status: accepted
**Context:** `grid`, `briefHtml`, `DefaultHelpers` and `InputParser` must stay in `views.html.xcala.play[.bootstrap3]`,
so templates reach them by short name.
**Decision:** keep their package and give them a directory that matches it.
**Rejected alternatives:**
- Leave them next to the templates — rejected: IntelliJ would still flag them.
- Change their package — rejected: breaks every template and consumer that calls them.

---

## 7. Verification

| What | Command | Result | Date |
|---|---|---|---|
| Base build | `sbt -Dci=true clean compile Test/compile package` on `c4dae37` | exit 0; 1 warning: `app/xcala.play/utils/PersianSort.scala:10:26: constructor Locale in class Locale is deprecated (since 19)` | 2026-11-04 |
| Branch build | same, on the working tree | exit 0; 1 warning: the same `PersianSort.scala:10:26` deprecation | 2026-11-04 |
| Tests | `sbt -Dci=true "testOnly * -- -Dtest.config=conf/local-test.conf"` | `Passed: Total 51, Failed 0, Errors 0, Passed 51` | 2026-11-04 |
| Path vs package | for each `.scala` in `app/`, `test/`: dir path with `/`→`.` vs concatenated `package` clauses | `checked=152`, no mismatch; `find app test -type d -name '*.*'` empty | 2026-11-04 |
| Jar contents | `unzip -Z1` of base and branch jar, `diff` | 500 → 498 entries. Removed `models/`, `services/` dir entries and 18 classes under them; the same 18 added under `xcala/play/models/` and `xcala/play/services/`. Nothing else differs. | 2026-11-04 |

| Only intended content changes | `git add -A && git diff --cached -M --stat` | 184 files; 171 pure renames (`\| 0`); content changes only in the 8 Task/Recaptcha files, `invisibleButtonExtended.scala.html` (1 line), `TaskLockServiceSpec.scala` (1 line), `CLAUDE.md`, the two task docs | 2026-11-04 |
| Independent review | `feature-dev:code-reviewer`, read the files itself | no code defect; flagged `TaskBased` missing from the moved-class lists and the "up to 1.12.29" wording for `TaskLock*` — both fixed in `CLAUDE.md` and here | 2026-11-04 |

**Not covered by tests:**
- IntelliJ inspection — needs the user's IDE.
- Consumers compiling against the new jar — not done in this task (§8).

---

## 8. Open Questions & Risks

- [ ] Should each consumer's import fix be done now (starting with `icfi-reg`, already on `1.12.30-SNAPSHOT`), or
      when each next upgrades?
- [ ] `build.sbt` sets `versionScheme := Some("early-semver")`, under which 1.12.29 → 1.12.30 claims binary
      compatibility, but this release is source- and binary-breaking. Matters only for a library that depends on
      xcala-play transitively; none found among local consumers. Bump to 1.13.0 instead?
- [ ] `CLAUDE.md` says the default remote is `ketab`; in this clone it is `origin` (`git remote -v`). Not changed
      in this task.

---

## 9. Changes Made So Far

**Committed / pushed:** staged (`git add -A`), not committed.

### xcala-play, branch `task-lock`

| File | Change |
|---|---|
| `app/xcala.play/**` → `app/xcala/play/**` | `git mv`, contents unchanged except below |
| `app/xcala.play.cross/**` → `app/xcala/play/cross/**` | `git mv` |
| `app/xcala.play.postgres/**` → `app/xcala/play/postgres/**` | `git mv` |
| `app/views/xcala.play/**` → `app/views/xcala/play/**` | `git mv` (templates) |
| `app/views/html/xcala/play/{grid,briefHtml}.scala`, `…/bootstrap3/{DefaultHelpers,InputParser}.scala` | `git mv` from the views dir, contents unchanged |
| `app/xcala/play/models/Task{,Lock,Owner,Status,BasedWithEither}.scala` | `package xcala.play.models`; dropped now same-package imports |
| `app/xcala/play/services/{TaskService,TaskLockService,RecaptchaService}.scala` | `package xcala.play.services`; `import models._` → `import xcala.play.models._`; dropped same-package imports |
| `app/views/xcala/play/invisibleButtonExtended.scala.html:2` | `_root_.xcala.play.services.RecaptchaService` |
| `test/xcala/play/services/TaskLockServiceSpec.scala` | dropped `import services.TaskLockService` |
| `CLAUDE.md` | Source layout section rewritten |
| `docs/tasks/task-lock.md`, `docs/tasks/README.md` | new |

### Deliberately not changed

- `README.md:61` — links to a file at a fixed historical commit of another repo; still valid.
- Consumer repos — see §8.

### Remaining work

1. Commit and push `task-lock`.
2. Consumers: change imports when upgrading to 1.12.30.
