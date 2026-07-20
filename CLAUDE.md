# CLAUDE.md

## Git

- **Default remote is `ketab`** (`git@hamgit.ir:ketab/xcala-play.git`). Push here by default.
  CI on `ketab` publishes the library to nexus. Other remotes: `origin` and `amir` (GitHub mirrors).

## Publishing / assets

- Version lives in `build.sbt` (`version := "..."`). Releases on nexus are **immutable**
  (`withOverwrite` is commented out), so bump the version for every publish.
- Shared web assets (JS/CSS served to consuming apps) live in `shared-assets/public/...` and are
  packaged into the jar via `Compile / unmanagedResourceDirectories += baseDirectory.value / "shared-assets"`.
  Consuming apps reference them through `@routes.Assets.at("...")` (Play resolves `/public/...` from the classpath).
- The CI build image is `Dockerfile`: it must `ADD` every source dir it needs. If a new top-level
  dir feeds the build (e.g. `shared-assets`), add a matching `ADD` line or CI will publish without it.
