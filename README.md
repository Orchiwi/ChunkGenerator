# ChunkGenerator

ChunkGenerator pre-generates world chunks in a configurable shape (square,
circle, rectangle) while keeping server TPS healthy through an adaptive
throttle. It is built for Paper, Purpur and Folia, persists progress
across restarts, and surfaces live speed, CPU and RAM metrics in-game and
on the console.

## Features

- Square, circle and rectangle zones with a per-job center (defaults to
  the world spawn).
- Auto-tuned throttle: the controller increases or decreases the number
  of chunks loaded per tick to keep the configured target TPS.
- One job per world; multiple worlds can pre-generate concurrently.
- BossBar progress for staff with `chunkgenerator.bossbar`, ActionBar for
  the player who launched the job, periodic console logs, and
  `/cg status` snapshots.
- Live readout of generation speed, server TPS, process CPU usage and
  JVM RAM utilisation.
- Automatic resume on restart for jobs that were running when the
  server stopped.
- LuckPerms integration with `/op` fallback when LuckPerms is absent.

## Build

```
./gradlew build
```

The shaded jar lands in `build/libs/ChunkGenerator-X.Y.Z.jar`.

## Run a development server

```
./gradlew runServer
```

The first run drops a Paper server in `run/`; accept the EULA in
`run/eula.txt`, then re-run.

## Requirements

- Paper, Purpur or Folia targeting Minecraft 1.21.x (API `26.1.2`).
- Java 25.

## Commands

| Command | Description |
|---|---|
| `/cg start <world> <shape> <size> [centerX centerZ]` | Start a job. `shape` ∈ `square\|circle\|rectangle`. For square/circle `size` is the radius in blocks; for rectangle pass `<halfWidth> <halfLength>`. |
| `/cg stop <world>` | Pause a running job (state preserved on disk). |
| `/cg cancel <world>` | Cancel a job and remove its state file. |
| `/cg status [world]` | Show a snapshot of progress, speed, ETA, TPS, CPU and RAM. |
| `/cg list` | List all known jobs (running and paused). |
| `/cg reload` | Reload `config.yml` and `messages.yml`. |
| `/cg help` | Print the help message. |

Alias: `/chunkgen`.

## Permissions

| Node | Default | Description |
|---|---|---|
| `chunkgenerator.admin` | op | Parent: grants every child below. |
| `chunkgenerator.command.start` | op | Start jobs. |
| `chunkgenerator.command.stop` | op | Pause jobs. |
| `chunkgenerator.command.cancel` | op | Cancel jobs. |
| `chunkgenerator.command.status` | op | Query job status. |
| `chunkgenerator.command.list` | op | List jobs. |
| `chunkgenerator.command.reload` | op | Reload config. |
| `chunkgenerator.bossbar` | op | Receive the live BossBar. |

## Configuration

See `config.yml` for the full set of throttle, display, monitoring and
persistence options.

Key knobs:

- `throttle.target-tps` — TPS the auto-tuner aims to keep. Lower it on
  shared hardware, raise it on dedicated machines.
- `throttle.max-chunks-per-tick` / `min-chunks-per-tick` — hard limits
  for the adaptive controller.
- `display.bossbar.color` — Adventure BossBar color name.
- `persistence.auto-resume-on-startup` — when `false`, paused jobs are
  loaded but not started automatically after a restart.

All player-facing strings live in `messages.yml` with the standard `&`
color codes; missing keys fall back to bundled defaults.

## License

MIT — see `LICENSE`.
