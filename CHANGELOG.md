# Changelog

All notable changes to ChunkGenerator are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
