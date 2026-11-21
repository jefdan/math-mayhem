# Math Mayhem

**Minecraft but the floating-point math is slightly wrong.**

Math Mayhem is a client-side Fabric mod for **Minecraft 26.3** that rewrites Java
bytecode at class-load time. Every selected `FADD`, `DADD`, `FMUL`, `DMUL`,
`FDIV` and `DDIV` instruction can be routed through a guard-railed hook that
scales, offsets, and periodically jitters the result. The practical result is
terrain that folds into floating islands, cliffs that should not exist, and
physics that quietly disagrees with itself.

The mod has two independent paths:

| Path | What it does | Needs a JVM argument? |
| --- | --- | --- |
| **Worldgen Mixin fallback** | Perturbs the final output of `PerlinNoise` / `SimplexNoise`. Stable enough for ordinary play. | No |
| **JVM agent + ASM** | Rewrites actual float/double arithmetic instructions across filtered Minecraft classes. Enables **All game math** mode. | Yes: `-javaagent:...` |

> **Back up your worlds.** This is a world-corruption mod by design. New chunks
> are generated with the current settings; existing chunks are not retroactively
> changed, so chunk borders can look violent. Press **F7** at any time to restore
> normal arithmetic.

---

## Requirements

- Minecraft **26.3**
- Fabric Loader **0.20.1** or newer
- Fabric API **0.165.2+26.3** (or the current 26.3 build)
- Java **25** (required by Minecraft 26.3)
- Client only. The mod does nothing on a dedicated server.

## Quick start (development)

```bash
./gradlew runClient
```

Loom automatically attaches the agent-only jar at
`build/agent/mathmayhem-agent.jar` to the development client, so **All game
math** mode is available immediately. Press **F6** to open the control panel from
the title screen or from in-game, or click **Math Mayhem** at the bottom-left of
the title screen. **F7** performs an emergency safe-off.

## Build

```bash
./gradlew build
```

Outputs:

- `build/libs/mathmayhem-1.0.1.jar` — the Fabric mod. It is **also** a valid JVM
  agent (its manifest contains `Premain-Class`).
- `build/libs/mathmayhem-1.0.1-sources.jar` — sources.
- `build/agent/mathmayhem-agent.jar` — a small agent-only jar for development or
  launchers that prefer a separate agent.

Run the unit tests with:

```bash
./gradlew test
```

## Installation

1. Install Fabric Loader 26.3 and Fabric API.
2. Put `mathmayhem-1.0.1.jar` in `.minecraft/mods/`.
3. Start the game once. The **worldgen fallback** works immediately.
4. To enable **All game math**, add this JVM argument to the launch profile:

```text
-javaagent:/absolute/path/to/.minecraft/mods/mathmayhem-1.0.1.jar
```

Quote the path if it contains spaces:

```text
-javaagent:"/Users/me/Library/Application Support/PrismLauncher/instances/MC 26.3/minecraft/mods/mathmayhem-1.0.1.jar"
```

Where to put it:

- **Official launcher:** Installations → Edit → More options → JVM arguments.
- **Prism / MultiMC:** Settings → Java → JVM arguments.
- **ATLauncher / GDLauncher:** Java/JVM arguments in the instance settings.

The path must point at the **same jar** that is in `mods/`. If you rename or move
the jar, update the argument. The agent is optional; without it the GUI will show
`NO AGENT · SAFE WORLDGEN FALLBACK` and **All game math** behaves like worldgen.

> Fabric has no launcher API that can attach a JVM agent automatically, and
> modern JDKs reject self-attachment by default. Supplying `-javaagent` at launch
> is the supported way to get full bytecode coverage.

## Controls

| Key | Action |
| --- | --- |
| **F6** | Open/close the Math Mayhem panel (title screen, pause menu, or in-game) |
| **F7** | Emergency safe-off: reset dangerous values and disable tampering |

The title screen also has a visible **Math Mayhem** button at the bottom-left, so
you never need to load a world to fix a world-breaking configuration.

The panel is live: scale, offset, operation toggles and jitter take effect
immediately for newly generated chunks. The Done button, closing the screen, or
the periodic autosave writes the config to:

```text
.minecraft/config/mathmayhem.json
```

## The panel

- **Mode**
  - **Worldgen only** — default. Safe filter; instruments noise and terrain
    generation.
  - **All game math** — requires the agent. Instruments filtered gameplay/world
    classes, including entity physics.
  - **Off** — hooks still exist in already-loaded classes but return the raw
    arithmetic result.
- **ADD / MUL / DIV** — independently enable or disable the six rewritten
  opcodes (`FADD`/`DADD`, `FMUL`/`DMUL`, `FDIV`/`DDIV`).
- **Scale** — `0.50` to `1.50` (default `1.03`). The result of a selected
  operation is multiplied by this.
- **Offset** — `-0.100` to `0.100` (default `0.000`). Added after scaling.
- **Jitter ±** — `0.000` to `0.100`. Adds a deterministic pseudo-random offset to
  the scale.
- **Period (s)** — `1` to `30` seconds. How often the jitter epoch changes.
- **RNG: Static / Time jitter** — static scale or the periodic jitter.
- **NaN guard** — when on, `NaN`/`Infinity` results become `0` and
  non-finite perturbed values fall back to the raw result. Keep this on unless
  you enjoy hard crashes.
- **Safe Off** — resets `scale`, `offset`, jitter and operation toggles to stable
  defaults and sets the mode to Off. This is the same action as F7.
- **Copy JVM arg**, **Done**.

The panel shows whether the agent is attached and what mode is effectively
active. A red/amber status line is not an error; it means you are in the safe
fallback. If the current values are aggressive (`scale` outside 0.92–1.08, a
large offset, or strong jitter), the panel shows a red warning before you generate
new chunks.

## Crash recovery and lockout protection

World generation can hard-crash if the scale is pushed far outside the stable
range. Three layers protect you:

1. **Title-screen access** — F6 and the **Math Mayhem** title button work before
   any world is loaded.
2. **F7 / Safe Off** — immediately writes safe values (`scale 1.03`, `offset 0`,
   jitter off) and sets mode to Off, so a later re-enable cannot repeat the same
   crash.
3. **Automatic crash recovery** — on the next launch, Math Mayhem checks recent
   `crash-reports/*.txt` files. If a worldgen crash report is newer than the
   config and contains the mod, the config is reset to safe defaults and the mode
   is set to Off. The panel reports that recovery was applied. This runs once;
   saving the recovered config prevents it from repeatedly reverting your changes.

If the game cannot reach the title screen, start with this JVM property to force
safe mode:

```text
-Dmathmayhem.safe=true
```

Or edit the config directly:

```text
.minecraft/config/mathmayhem.json   (development: run/config/mathmayhem.json)
```

Set `"mode": "OFF"`, `"scale": 1.03`, `"offset": 0.0`, `"jitter": false`.

## How it works

### JVM agent path

`MathMayhemAgent.premain` installs a `ClassFileTransformer`. The transformer uses
ASM to visit every class that passes the filters and replaces:

```text
FADD -> invokestatic ArithHooks.fadd(FFI)F
DADD -> invokestatic ArithHooks.dadd(DDI)D
FMUL -> invokestatic ArithHooks.fmul(FFI)F
DMUL -> invokestatic ArithHooks.dmul(DDI)D
FDIV -> invokestatic ArithHooks.fdiv(FFI)F
DDIV -> invokestatic ArithHooks.ddiv(DDI)D
```

The extra `int` is a mode tag:

- `1` = this class was instrumented as worldgen and is active in
  **Worldgen** and **All** modes.
- `2` = this class was instrumented as broad gameplay code and is active only in
  **All** mode.

That tag lets a single already-loaded class obey live mode changes without
retransformation. For example, switching from **All** back to **Worldgen**
silences broad hooks while leaving terrain noise active.

The hook itself:

1. Computes the original operation.
2. Returns it unchanged if the mode/operation is disabled.
3. Converts to `double`, applies `value * scale + offset`, and converts back.
4. Rejects non-finite results and returns zero or the original value.
5. Wraps the whole path in a `Throwable` catch as a final safety net.

The agent and the mod are loaded by different class loaders in Fabric. They do
not share Java statics. Configuration is bridged through system properties
(`mathmayhem.mode`, `mathmayhem.scale`, `mathmayhem.effectiveScale`,
`mathmayhem.offset`, `mathmayhem.ops`, `mathmayhem.guard`). The hook refreshes a
tiny immutable settings snapshot every 100 ms. The game pays one volatile read
per arithmetic operation, not a property lookup.

### Worldgen fallback

Two Mixins target the return values of:

- `net.minecraft.world.level.levelgen.synth.PerlinNoise#get(DDD)F`
- `net.minecraft.world.level.levelgen.synth.SimplexNoise#get(DD)F`
- `net.minecraft.world.level.levelgen.synth.SimplexNoise#get(DDD)F`

When the agent is absent, these outputs are scaled/offset directly. When the
agent is present, the fallback returns the raw value to avoid double distortion.

### Safety filters

Even in **All game math**, the transformer refuses to touch:

- `net.minecraft.client.*` — rendering, input, game loop, screens.
- `net.minecraft.network.*`
- `net.minecraft.core.*`, `resources.*`, `data.*`, `commands.*`,
  `advancements.*`, `recipebook.*`, `gametest.*`, `realms.*`
- server lifecycle classes except `net.minecraft.server.level.*`
- **structure, feature and placement classes** under `levelgen` — these rely on
  exact chunk-boundary behavior and can crash with
  `Requested chunk unavailable during world generation`
- `Util`, `SharedConstants`, `Bootstrap`, crash-report and version classes
- anything outside `net/minecraft/`
- the mod's own `com/mathmayhem.*` classes

Worldgen mode is narrower still: only `net.minecraft.world.level.levelgen.*` and
`net.minecraft.world.level.biome.*`, excluding structure, feature, placement,
block-predicate and material-rule packages. That keeps worldgen weird rather than
deadlocked.

> Existing chunks that were already generated with extremely high settings can
> remain internally inconsistent even after you switch to safe values. If a world
> keeps crashing, create a fresh world after resetting the settings.

### Agent failure behavior

`premain` catches every `Throwable`. If ASM or instrumentation is unavailable,
the agent logs the problem, marks itself inactive, and lets Minecraft start.
The Mixin fallback still works. A failure in a single class transform is caught
and that class is skipped; it is never allowed to abort class loading.

## Project layout

```text
src/main/java/com/mathmayhem/
  MathMayhem.java                 shared constants/logger
  agent/
    MathMayhemAgent.java          premain/agentmain entry point
    MathClassTransformer.java     ASM FADD/DADD/... rewriter
    ArithHooks.java               hot arithmetic hooks + settings snapshot
    AgentConfig.java              class filters and mode tags
src/client/java/com/mathmayhem/
  client/
    MathMayhemClient.java         client entry point
    config/MathConfig.java        serializable settings
    config/ConfigManager.java     persistence + cross-classloader properties
    gui/MathConfigScreen.java     F6 panel
    gui/OptionSlider.java         live slider widget
    gui/TitleScreenIntegration.java  visible title-screen button
    input/Keybinds.java           F6 / F7
    math/NoiseTap.java            Mixin fallback math
  mixin/
    PerlinNoiseMixin.java
    SimplexNoiseMixin.java
src/main/resources/fabric.mod.json
src/client/resources/mathmayhem.client.mixins.json
```

## Tests

`MathClassTransformerTest` generates a class containing all six target opcodes,
runs it through the real transformer, loads the rewritten bytes, and checks:

- all six operations are perturbed correctly;
- mode tags allow a live downgrade from All to Worldgen;
- operation toggles and Off leave the raw value;
- NaN/Infinity are guarded;
- classes with no target arithmetic are untouched;
- render/network classes are excluded and worldgen classes are included.

All tests pass with Java 25 and ASM 9.10.1.

## Troubleshooting

**The panel says “NO AGENT”.**
The `-javaagent` argument is missing, points at the wrong file, or uses a path
with unquoted spaces. Use the **Copy JVM arg** button, paste it into the launcher,
and restart. The path must point at the mod jar itself.

**The game crashed immediately after adding the argument.**
Remove the `-javaagent` argument and start normally. The worldgen fallback still
works. Check `logs/latest.log` for `[MathMayhem/Agent]` lines.

**“All game math” does not affect already-loaded classes.**
Switch to All **before** loading a world for full coverage. Going from Off/Worldgen
to All at runtime only covers classes that load afterward. Restart for complete
coverage. Scale/offset/operation changes are always live.

**The game crashes when I open a world.**
Restart. The crash-recovery scan should reset Math Mayhem to safe defaults and
disable it. Open the panel from the title screen with F6 (or the **Math Mayhem**
button), choose a smaller scale (e.g. `1.005`), disable DIV, then re-enable
Worldgen mode. If recovery did not trigger, press F7, use **Safe Off**, or start
with `-Dmathmayhem.safe=true`.

**Terrain is too broken / I cannot move.**
Press F7. Then choose a smaller scale (e.g. `1.005`), turn off DIV, and regenerate
new chunks. Existing chunks will remain altered.

**Frame rate dropped.**
All mode instruments far more methods than worldgen mode. Switch back to
Worldgen, or disable MUL and DIV. The hooks are cheap per call, but worldgen calls
them millions of times.

## Limitations

- Client-only. A dedicated server generates its own terrain; the client cannot
  change it.
- Only newly generated chunks are affected.
- Existing chunks are never rewritten.
- All mode intentionally excludes rendering, networking, and structure/feature
  placement to avoid hard crashes; it is “all gameplay math”, not literally
  every class in the JVM.
- This is not a shader or a resource pack; it changes arithmetic itself.

## Repository and Gitea

The default branch is `main`. Generated files (`build/`, `.gradle/`, `run/`) are
ignored, so the repository contains source, Gradle wrapper files, the mod icon,
and documentation only.

A Gitea Actions workflow lives at
[`.gitea/workflows/build.yml`](.gitea/workflows/build.yml). It builds and tests
the project on push and pull request. If your runner uses a different label,
change `runs-on: ubuntu-latest` in that file.

To push a fresh clone to your own Gitea instance:

```bash
git remote add origin https://your-gitea-host/owner/mathmayhem.git
git push -u origin main
```

The built jar is intentionally not committed. After a successful build, attach
`build/libs/mathmayhem-1.0.1.jar` (and optionally
`build/agent/mathmayhem-agent.jar`) to a Gitea release.

See [CONTRIBUTING.md](CONTRIBUTING.md) for development workflow and
[CHANGELOG.md](CHANGELOG.md) for release history.

## License

MIT. See [LICENSE](LICENSE).
