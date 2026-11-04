# CLAUDE.md

Guidance for working in this repository.

## What this is

`xcala-play` (`com.xcala %% xcala-play`) is a **reusable web-application library** for Scala + Play +
ReactiveMongo. It is structured like a Play app but published as a JAR and consumed by downstream
apps (paperds, icfi-reg, icfi-int-reg, icfi-subsidies, icfi-org-subsidies, …). It provides base
CRUD controllers, data services, server-side pagination/criteria, localization, Bootstrap form/grid
views, BSON/Form extensions, and shared front-end assets.

- Scala **2.13.16**, sbt **1.10.11**, `PlayScala` plugin.

## Source layout

Every directory is one package segment, and every file's `package` matches its path (IntelliJ flags a mismatch;
scalac does not). Never name a directory with a dot (`xcala.play/`): IntelliJ reads it as the single package
`` `xcala.play` ``.

- `app/xcala/play/` — core, **MongoDB/ReactiveMongo** based: `controllers`, `services`, `models`, `extensions`,
  `utils`.
- `app/xcala/play/postgres/` — **PostgreSQL/Slick** variants: `controllers`, `services`, `entities`, `models`,
  `utils`.
- `app/xcala/play/cross/` — code shared between the Mongo and Postgres stacks.
- `app/views/xcala/play/` — Twirl templates (`*.scala.html`), compiled to `views.html.xcala.play.*`; referenced in
  templates as `xcala.play.inputRich` etc. Inside a template a bare `xcala.…` resolves to `views.html.xcala.…`, so
  refer to Scala code as `_root_.xcala.play.…`.
- `app/views/html/xcala/play/` — hand-written Scala helpers that live in the templates' package
  `views.html.xcala.play[.bootstrap3]` (`grid`, `briefHtml`, `DefaultHelpers`, `InputParser`).
- `shared-assets/public/…` — front-end assets served to consuming apps (see below).
- `test/xcala/play/` — specs2 tests.
- `Task`, `TaskLock`, `TaskStatus`, `TaskOwner`, `TaskBased`, `TaskBasedWithEither` are in `xcala.play.models`;
  `TaskService`, `TaskLockService`, `RecaptchaService` in `xcala.play.services`. Up to 1.12.29 all but `TaskLock` /
  `TaskLockService` (new in 1.12.30) were in the top-level `models` / `services`. `TaskLockService` keeps one lock per label (`taskLocks`, `_id` = the label): take it before starting a
  background task that must not run twice at once.

## Build, test, run

```
sbt compile
sbt test                               # uses conf/local-test.conf; needs a local MongoDB
sbt "testOnly *SomeSpec"
sbt publishLocal                       # installs to ~/.ivy2/local for local consumers
```

- Tests are **specs2** and need MongoDB. Test config is selected with
  `-Dtest.config=<file>` (`conf/local-test.conf` locally, `conf/docker-test.conf` in CI).
- `-Dci=true` disables the semanticdb compiler plugin (IDE-only); CI always sets it.

## Publishing (nexus)

- Version lives in `build.sbt` (`version := "..."`). Nexus releases are **immutable** —
  `withOverwrite` is commented out — so **bump the version for every publish**; you cannot re-push a
  version that already exists.
- Publishing happens in CI, not locally (see below). `publishTo` / credentials point at
  `nexuss.darkube.ir` using user `ci` + `NEXUS_KEY`.

## Front-end assets (important)

- Shared JS/CSS live under `shared-assets/public/xcala/…` and are packaged into the jar via
  `Compile / unmanagedResourceDirectories += baseDirectory.value / "shared-assets"`, landing at
  `public/xcala/…` inside the jar.
- Consuming apps load them with `@routes.Assets.at("xcala/…")` — Play's `Assets` controller resolves
  `/public/…` from the classpath, which includes this jar. No route wiring is needed downstream.
- `xcala/javascripts/disableOnSubmit.js` — double-submit guard plus the "comet" loading indicator on the
  clicked submit button (button keeps its size; CSS injected by the script; `window.showButtonLoading` for
  native `form.submit()` paths such as `invisibleButtonExtended`; bfcache `pageshow` reset). Consumers include
  it with `?v=N` — when it changes, consumers must bump `N` in every include as well as the xcala version.
- **Gotcha:** the CI build image (`Dockerfile`) must `ADD` every source dir the build needs. It
  `ADD`s `project`, `build.sbt`, `app`, `conf`, `shared-assets`, `test`. If you add a new top-level
  dir that feeds packaging, add a matching `ADD` line — otherwise CI silently publishes a jar
  missing those files (this exact omission of `shared-assets` shipped assetless 1.12.24/1.12.25).

## CI / CD (`.gitlab-ci.yml`, `Dockerfile`)

Three stages on `ketab`:

1. **build** — builds two images from `Dockerfile`: `--target builder` (main) and `--target test`
   (adds `test/`), pushes both to `registry.hamdocker.ir/ketab`.
2. **test** — runs `sbt -Dci=true testOnly * -- -Dtest.config=/opt/conf/docker-test.conf` in the
   test image, with a `mongo:4.4.19` service.
3. **publish** — **only on `master`**, runs `sbt -Dci=true publish` from the builder image.

So: merge/land on `master` → CI publishes the version in `build.sbt` to nexus.

## Git

- **Default remote is `ketab`** (`git@hamgit.ir:ketab/xcala-play.git`) — push here; its CI publishes
  the library. `origin` and `amir` are GitHub mirrors.
- `master` is the release branch (publish runs only there).

## Downstream import surface

`build.sbt` injects implicit imports so consumers don't repeat them:
- `routesImport` adds `BSONObjectID`, `xcala.play.extensions.Bindables._`, i18n `Messages`.
- `TwirlKeys.templateImports` adds BSON types, `xcala.play.models._`, `PersianUtils._`,
  `MultilangHelper._`, etc. — available in all downstream Twirl templates.
