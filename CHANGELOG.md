# Changelog

All notable changes to ChunkGenerator are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-beta.1] - 2026-05-15

First beta release after a round of production tuning on real
hosts. The plugin now ships with auto-scaled throttle defaults
calibrated against an actual container-host crash, six chunk-visit
patterns, a resume command, and a simplified config aimed at public
use.

### Added

- **Traversal patterns** as an optional last argument to
  `/cg start`. Six patterns: `center` (default, spiral outward from
  the zone center), `edge` (concentric rings inward from the outer
  perimeter to the center), and `north` / `south` / `east` /
  `west` (linear row or column sweep starting at the named edge).
  All patterns visit the same set of chunks; only the order
  differs, and the saved iterator index is pattern-aware so a
  paused job resumes in its own sequence.
- `/cg resume <world>` to restart a paused job from where it left
  off, completing the start / stop / resume / cancel quartet.
  Paused jobs were previously only reactivated by a server restart
  with `auto-resume: true`. New permission node
  `chunkgenerator.command.resume`.
- `inflight/target` pipeline pressure reading in `/cg status` and
  the periodic console log. When chunks are being submitted but
  not completing (slow Paper chunk worker, stuck futures, far-out
  generation) the diagnosis is obvious from one command.
- Startup log lines that report the resolved throttle values and
  recommend a `paper-global.yml > chunk-system > worker-threads`
  setting matching the host's core count, so operators do not have
  to derive the Paper-side tuning themselves.
- README sections on **container hosts and `MaxRAMPercentage`**
  (Pterodactyl, Docker — heap-based monitoring cannot observe the
  cgroup RSS ceiling) and on **maximising sustained throughput**
  (the real ceiling is Paper's chunk worker count, not the
  plugin's queue depth).

### Changed

- `config.yml` flattened and trimmed for public use. A short
  **Basics** block at the top (`target-tps`,
  bossbar / actionbar / console toggles, `auto-resume`) and an
  **Advanced** `throttle` section that most servers never need to
  touch. A banner at the very top points operators at
  `paper-global.yml > chunk-system` for the actual speed lever.
  The `display`, `monitoring` and `persistence` wrapper sections
  are gone — every knob lives at the root now.
- Auto-scaled defaults retuned against production data and tied to
  the core count rather than the heap alone, with a conservative
  30 MB-per-inflight-chunk transient estimate.
  - `max-inflight = max(64, min(cores × 32, heapMB / 60))`
  - `start-inflight = max(16, cores × 6)`
- Default memory thresholds lowered: `memory-backoff-pct`
  85 → 70, `memory-pause-pct` 92 → 80, to keep a margin from the
  container RSS limit that heap-based monitoring cannot see
  directly.

### Fixed

- **Kernel-OOM crash on container hosts** (Pterodactyl, exit code
  137) when the throttle saturated to its previous auto-scaled
  ceiling and Paper's chunk save thread fell behind: held chunks
  plus native memory exceeded the cgroup limit even while JVM heap
  stayed under the old 85 % backoff threshold.
- ETA no longer overflows to `Long.MAX_VALUE` (rendered as
  `2562047788015215h30m`) when the smoothed speed decays toward
  zero. Below 0.01 chunk/s or for predicted ETAs over a year, the
  status returns `?` instead of a meaningless number.
- Speed readout shows two decimals below 10 chunk/s and one decimal
  below 100, so sub-1 chunk/s progress no longer rounds to a flat
  `0`.

### Migration

- Delete `plugins/ChunkGenerator/config.yml` after upgrading so the
  new flat layout (with the `paper-global.yml` tuning banner)
  regenerates. Old YAMLs still load but stay on the previous keys
  that the plugin no longer reads.

## [0.1.0-alpha.2] - 2026-05-14

### Added

- Memory pressure circuit breaker on the throttle: the controller
  scales down above `memory-backoff-pct` of max heap and pauses new
  chunk submissions entirely above `memory-pause-pct`, resuming
  automatically once heap pressure clears.
- Auto-scaled defaults for the inflight target derived from
  `availableProcessors()` and `maxMemory()` at startup, so the plugin
  saturates the chunk system pipeline on the host without manual
  tuning.

### Changed

- Throttle now drives the count of concurrent chunk requests in
  flight rather than a fixed chunks-per-tick budget. This matches
  Paper's asynchronous chunk system and removes the budget-loss bug
  that capped throughput when the inflight ceiling was reached.
- Speed readout labelled `chunk/s` instead of `c/s` across BossBar,
  ActionBar, console and `/cg status` / `/cg list`.

### Removed

- `throttle.max-chunks-per-tick` and `throttle.min-chunks-per-tick`
  config keys, replaced by `throttle.max-inflight`,
  `throttle.min-inflight` and `throttle.start-inflight`. The hardcoded
  `MAX_INFLIGHT = 200` ceiling on concurrent chunk loads is also
  gone.
- The `display`, `monitoring` and `persistence` wrapping sections in
  `config.yml`: `bossbar`, `actionbar`, `console`, `target-tps`,
  `auto-resume`, `monitoring-poll-ms`, `save-throttle-seconds` and
  `save-throttle-chunks` now live at the root for a flatter,
  easier-to-skim file. Existing configs need a regen (delete
  `plugins/ChunkGenerator/config.yml`) or a manual key migration.

## [0.1.0-alpha.1] - 2026-05-14

Initial alpha. Functional and self-contained but kept on the alpha
channel until shape iteration, throttle behaviour and resume logic
have been validated on production-scale worlds.

### Features

- Pre-generation jobs over **square**, **circle** and **rectangle**
  zones with an optional explicit center (default: world spawn).
- TPS-aware auto-throttle: the controller adapts chunks-per-tick to
  hold the configured target TPS.
- One job per world; jobs across worlds run in parallel.
- Live BossBar for staff (`chunkgenerator.bossbar`), ActionBar for
  the launching player, periodic console logs, and `/cg status`
  snapshots — all with generation speed, TPS, process CPU and JVM
  RAM readings.
- Atomic per-world YAML persistence (`plugins/ChunkGenerator/jobs/`)
  with automatic resume after restart.
- Paper, Purpur and Folia support via a reflection-based scheduler
  bridge — no Folia API required on the compile classpath.
- LuckPerms integration with `/op` fallback when LuckPerms is absent.

### Requirements

- Paper, Purpur or Folia 1.21.x (API target `26.1.2`).
- Java 25.
