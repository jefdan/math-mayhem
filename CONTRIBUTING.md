# Contributing to Math Mayhem

Thanks for helping make Minecraft's arithmetic a little less trustworthy.

## Requirements

- Java **25** (Minecraft 26.3 requires it)
- Git
- No local Minecraft installation is required for development; Fabric Loom
  downloads the development client and assets into the Gradle cache.

## Local workflow

```bash
# Compile, run tests, and produce the mod jar + dev agent jar
./gradlew build

# Launch the development client with the agent attached automatically
./gradlew runClient
```

The development game directory is `run/`. It is ignored by Git because it can
contain worlds, logs, crash reports, options, and account-adjacent data. Never
commit it.

## Making changes

1. Create a branch from `main`.
2. Keep changes focused and explain the behavior they affect.
3. Run `./gradlew build` before opening a pull request.
4. If you touch the agent, filters, or hooks, test a development client and
   include the relevant log/crash-report details in the pull request.
5. Do not commit generated output, `run/`, `.gradle/`, crash reports, or local
   launcher configuration.

## Code style

- Java 25, tab indentation (4-column visual width).
- Keep Minecraft references out of `com.mathmayhem.agent`; the agent must load
  before the game and must remain usable across class loaders.
- Treat the arithmetic hot path as performance-sensitive. Avoid per-operation
  property lookups, allocations, or logging.
- Safety filters and NaN/Infinity handling are part of the feature. Do not remove
  them to make an effect stronger.

## World safety

Math Mayhem intentionally corrupts worlds. When testing aggressive settings:

- Use a dedicated test world or a backup.
- Prefer creative mode for inspection.
- Keep the **Safe Off** path working: F7, the title-screen panel, the in-game
  **Safe Off** button, and automatic crash recovery.

## Reporting issues

Include:

- Minecraft, Fabric Loader, Fabric API, and Math Mayhem versions.
- Whether the JVM agent is attached (`-javaagent`).
- The active mode, scale, offset, and operation toggles.
- `logs/latest.log` and the matching `crash-reports/*.txt` if applicable.
- Steps to reproduce, ideally with a fresh world and known settings.

Do not include Microsoft account tokens, launcher credentials, or personal data.
