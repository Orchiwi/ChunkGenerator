# Changelog

All notable changes to ChunkGenerator are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `/cg resume <world>` to restart a paused job from where it left
  off, completing the start / stop / resume / cancel quartet that
  was previously missing its resume arm — paused jobs could only be
  picked back up automatically by a server restart with
  `auto-resume: true`. New permission node
  `chunkgenerator.command.resume`.
- `/cg status` and the periodic console log now surface the live
  pipeline pressure as `inflight/target`, so when chunks are being
  submitted but not completing (slow Paper chunk worker, stuck
  futures, far-out generation) the diagnosis is obvious from one
  command.
- README section on container hosts: `MaxRAMPercentage=95` (the
  Pterodactyl default) leaves no room for native memory and will
  kernel-OOM the container even when the JVM heap is far from full;
  documents how to either drop `MaxRAMPercentage` or lower the
  plugin's memory thresholds.
- README section "Maximising sustained throughput" explaining that
  the real ceiling is Paper's `chunk-system.gen-parallelism` in
  `paper-global.yml`, not our queue depth. The plugin now logs the
  resolved throttle values and a per-host gen-parallelism
  recommendation at startup so operators do not have to derive it
  themselves.

### Changed

- `config.yml` flattened and trimmed for public use: a banner at the
  top points operators at `paper-global.yml > chunk-system` (the
  actual speed lever), and the file is split into a short "Basics"
  block (`target-tps`, bossbar/actionbar/console toggles,
  `auto-resume`) and an "Advanced" `throttle` section that most
  servers never need to touch. Dropped the `display`, `monitoring`
  and `persistence` wrapper sections; keys live at the root now.
- Auto-scaled `max-inflight` raised to `max(64, min(cores × 32,
  heapMB / 60))` (was `cores × 16` / `heapMB / 50`). On a 6-core /
  16 GB host the ceiling goes from 96 → 192 inflight, keeping the
  queue full while Paper's chunk pipeline transitions between cached
  and freshly generated regions. Memory budget per inflight chunk
  stays at the conservative 30 MB transient estimate, so 8 GB hosts
  remain capped at 136 by the heap-based ceiling.
- Auto-scaled `start-inflight` raised to `max(16, cores × 6)`, so the
  warm-up phase reaches Paper's worker saturation in a couple of
  seconds rather than throttle-ramping for ten.

### Changed

- Auto-scaled `max-inflight` and `start-inflight` are now tied to
  the core count (Paper processes 3-6 chunks in parallel per host
  regardless of heap size) instead of the heap alone, and use a
  conservative 30 MB/chunk transient estimate rather than 5 MB.
  On a 6 core / 8 GB host the ceiling drops from ~1638 to ~96
  inflight and the start target from 192 to 24 — far tighter, but
  no slower in practice (Paper's queue past the saturation point
  just pinned memory).
- Default `memory-backoff-pct` lowered from 85 to 70 and
  `memory-pause-pct` from 92 to 80 to keep a margin from the
  container RSS limit that heap-based monitoring cannot observe
  directly.

### Fixed

- Kernel-OOM crash on container hosts (Pterodactyl, exit code 137)
  when the throttle saturated to its old auto-scaled ceiling and
  Paper's chunk save thread fell behind: held chunks plus native
  memory exceeded the cgroup limit even while JVM heap stayed under
  the old 85 % backoff threshold.
- ETA no longer overflows to `Long.MAX_VALUE` (displayed as
  `2562047788015215h30m`) when the smoothed speed decays toward zero.
  Below 0.01 chunk/s or for predicted ETAs over a year, the throttle
  returns "?" instead of a meaningless value.
- Speed readout shows two decimals below 10 chunk/s and one decimal
  below 100, so sub-1 chunk/s progress no longer rounds to a flat 0.

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
