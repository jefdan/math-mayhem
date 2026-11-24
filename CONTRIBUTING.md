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

## Releasing

Releases are cut by pushing a tag. `mod_version` in `gradle.properties` is the
single source of truth and the tag must be the same string.

1. Stamp the version with the current UTC date:

   ```bash
   date -u +%Y.%m.%d
   ```

2. Set `mod_version` in `gradle.properties` to that value, and add a matching
   `## <version>` heading to `CHANGELOG.md`. The heading is what becomes the
   release notes. For a second release on the same day, append a sequence
   number (`2026.11.24.1`) instead of reusing the date.
3. Run `./gradlew build` and check the jars in `build/libs/` and
   `build/agent/`.
4. Commit the version bump and the changelog together.
5. Tag that commit and push the tag:

   ```bash
   git tag 2026.11.24
   git push origin 2026.11.24
   ```

The tag push starts the `Release` workflow. It re-checks that the tag matches
`mod_version`, builds and tests the mod, and publishes the jars plus
`SHA256SUMS.txt` to a GitHub Release for that tag. A failing test means no
release is created.

Notes:

- Push tags by name, not with `git push --tags`, so stale local tags are not
  published.
- Creating a release in the GitHub UI does not start the workflow; it only fires
  on a tag push.
- If the tagged commit is broken, delete the tag locally and on the remote, fix,
  and re-tag. Re-running the workflow for an existing tag updates that release
  in place instead of creating a second one.
- Stay on UTC. The version is the UTC release date, not the local one.
- Sequence numbers must not have leading zeros: `2026.11.24.1`, never
  `2026.11.24.01`.

## Reporting issues

Include:

- Minecraft, Fabric Loader, Fabric API, and Math Mayhem versions.
- Whether the JVM agent is attached (`-javaagent`).
- The active mode, scale, offset, and operation toggles.
- `logs/latest.log` and the matching `crash-reports/*.txt` if applicable.
- Steps to reproduce, ideally with a fresh world and known settings.

Do not include Microsoft account tokens, launcher credentials, or personal data.
