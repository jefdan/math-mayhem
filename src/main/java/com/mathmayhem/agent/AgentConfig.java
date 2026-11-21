package com.mathmayhem.agent;

/**
 * Reads the small, Minecraft-free configuration surface shared between the
 * Fabric client and the JVM agent.
 *
 * <p>The client publishes configuration as system properties. This is
 * deliberate: the agent is loaded by the system class loader while the mod is
 * loaded by Fabric's Knot class loader, so they may not share Java statics.
 * System properties are visible to both and can be updated live.</p>
 */
public final class AgentConfig {
	public static final String PROP_MODE = "mathmayhem.mode";
	public static final String PROP_ACTIVE = "mathmayhem.agent.active";
	public static final String PROP_SCALE = "mathmayhem.scale";
	public static final String PROP_OFFSET = "mathmayhem.offset";
	public static final String PROP_EFFECTIVE_SCALE = "mathmayhem.effectiveScale";
	public static final String PROP_OPS = "mathmayhem.ops";
	public static final String PROP_GUARD = "mathmayhem.guard";
	public static final String PROP_REVISION = "mathmayhem.revision";

	public static final int MODE_OFF = 0;
	public static final int MODE_WORLDGEN = 1;
	public static final int MODE_ALL = 2;

	private AgentConfig() {
	}

	public static int mode() {
		return parseMode(System.getProperty(PROP_MODE, "worldgen"));
	}

	public static int parseMode(String value) {
		if (value == null) {
			return MODE_WORLDGEN;
		}

		return switch (value.toLowerCase(java.util.Locale.ROOT)) {
			case "off", "disabled", "0" -> MODE_OFF;
			case "all", "everything", "2" -> MODE_ALL;
			default -> MODE_WORLDGEN;
		};
	}

	/**
	 * Returns the mode a class must require in order to be transformed, or
	 * {@link #MODE_OFF} when the class should be left completely alone.
	 *
	 * <p>Worldgen classes are always instrumented by an installed agent, even
	 * while the mode is OFF. The injected hooks are inactive while the mode is
	 * OFF, but this lets the player turn worldgen tampering on without a
	 * restart. Broad "all" instrumentation is only performed when the mode is
	 * ALL at the time the class loads.</p>
	 */
	public static int requiredModeFor(String internalClassName) {
		if (internalClassName == null || internalClassName.isEmpty()) {
			return MODE_OFF;
		}

		if (isWorldgen(internalClassName)) {
			return MODE_WORLDGEN;
		}

		if (mode() == MODE_ALL && isAllEligible(internalClassName)) {
			return MODE_ALL;
		}

		return MODE_OFF;
	}

	public static boolean isWorldgen(String name) {
		boolean inWorldgen = name.startsWith("net/minecraft/world/level/levelgen/")
				|| name.startsWith("net/minecraft/world/level/biome/");

		if (!inWorldgen) {
			return false;
		}

		// Structure/feature/placement code uses large amounts of random-driven
		// arithmetic and is a common source of world-generation hangs. Terrain
		// shaping lives in density functions, noise, surface rules, carvers and
		// the chunk generator.
		return !(name.startsWith("net/minecraft/world/level/levelgen/structure/")
				|| name.startsWith("net/minecraft/world/level/levelgen/feature/")
				|| name.startsWith("net/minecraft/world/level/levelgen/placement/")
				|| name.startsWith("net/minecraft/world/level/levelgen/blockpredicate/")
				|| name.startsWith("net/minecraft/world/level/levelgen/material/"));
	}

	public static boolean isAllEligible(String name) {
		if (!name.startsWith("net/minecraft/")) {
			return false;
		}

		// Rendering, input, the client game loop and loader internals are never
		// touched, even in ALL mode. NaN in these paths often means a hard crash
		// or an unrecoverable graphics-device loss.
		if (name.startsWith("net/minecraft/client/")
				|| name.startsWith("net/minecraft/network/")
				|| name.startsWith("net/minecraft/core/")
				|| name.startsWith("net/minecraft/resources/")
				|| name.startsWith("net/minecraft/data/")
				|| name.startsWith("net/minecraft/commands/")
				|| name.startsWith("net/minecraft/advancements/")
				|| name.startsWith("net/minecraft/recipebook/")
				|| name.startsWith("net/minecraft/gametest/")
				|| name.startsWith("net/minecraft/realms/")) {
			return false;
		}

		// Keep server lifecycle, players and networking out of the blast radius.
		if (name.startsWith("net/minecraft/server/")
				&& !name.startsWith("net/minecraft/server/level/")) {
			return false;
		}

		// Structure and feature placement deliberately uses exact chunk-boundary
		// assumptions. Instrumenting this path (as an earlier build did) can
		// make trees/structure pieces read or write outside the current
		// generation region and crash with "Requested chunk unavailable".
		if (name.startsWith("net/minecraft/world/level/levelgen/feature/")
				|| name.startsWith("net/minecraft/world/level/levelgen/placement/")
				|| name.startsWith("net/minecraft/world/level/levelgen/structure/")) {
			return false;
		}

		// Bootstrap and crash-reporting classes must remain trustworthy.
		if (name.equals("net/minecraft/Util")
				|| name.equals("net/minecraft/SharedConstants")
				|| name.equals("net/minecraft/Bootstrap")
				|| name.equals("net/minecraft/DetectedVersion")
				|| name.equals("net/minecraft/WorldVersion")
				|| name.equals("net/minecraft/SystemReport")
				|| name.startsWith("net/minecraft/CrashReport")) {
			return false;
		}

		String simpleName = name.substring(name.lastIndexOf('/') + 1);
		return !(simpleName.endsWith("Thread")
				|| simpleName.endsWith("Exception")
				|| simpleName.endsWith("Error")
				|| simpleName.contains("CrashReport"));
	}
}
