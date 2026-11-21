package com.mathmayhem.client.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mathmayhem.MathMayhem;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Owns the live client configuration, persists it, and publishes a small set of
 * system properties that the JVM agent can see across class loaders.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static MathConfig config = new MathConfig();
	private static Path configPath;
	private static long revision;
	private static boolean initialized;
	private static boolean dirty;
	private static long jitterEpoch = Long.MIN_VALUE;
	private static double jitterRoll;

	private static volatile double effectiveScale = 1.03D;
	private static volatile double effectiveOffset;
	private static volatile MathConfig.Mode effectiveMode = MathConfig.Mode.WORLDGEN;
	private static volatile boolean guardEnabled = true;
	private static volatile boolean crashRecoveryApplied;
	private static volatile String crashRecoveryFile;

	private ConfigManager() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}

		initialized = true;
		configPath = FabricLoader.getInstance().getConfigDir().resolve(MathMayhem.MOD_ID + ".json");

		if (Files.isRegularFile(configPath)) {
			try {
				MathConfig loaded = GSON.fromJson(Files.readString(configPath), MathConfig.class);

				if (loaded != null) {
					config = loaded;
				}
			} catch (Exception exception) {
				MathMayhem.LOGGER.warn("Could not read {}; using defaults.", configPath, exception);
			}
		}

		config.sanitize();

		if (Boolean.getBoolean("mathmayhem.safe")) {
			config.safeOff();
			MathMayhem.LOGGER.warn("mathmayhem.safe is set; starting with tampering disabled.");
		}

		recoverFromRecentWorldgenCrash();
		applyVolatileState();
		updateJitter(System.currentTimeMillis(), true);
		publishAll();
		save();
	}

	public static MathConfig get() {
		return config;
	}

	public static double getEffectiveScale() {
		return effectiveScale;
	}

	public static double getEffectiveOffset() {
		return effectiveOffset;
	}

	public static MathConfig.Mode getMode() {
		return effectiveMode;
	}

	public static boolean isGuardEnabled() {
		return guardEnabled;
	}

	public static boolean isAgentActive() {
		return Boolean.parseBoolean(System.getProperty("mathmayhem.agent.active", "false"));
	}

	public static boolean wasCrashRecovered() {
		return crashRecoveryApplied;
	}

	public static String crashRecoveryFile() {
		return crashRecoveryFile;
	}

	/**
	 * Called whenever a widget changes a value. Makes values live immediately;
	 * disk persistence is deferred until the screen closes or the game ticks
	 * after a short delay.
	 */
	public static void changed() {
		config.sanitize();
		applyVolatileState();
		jitterEpoch = Long.MIN_VALUE;
		updateJitter(System.currentTimeMillis(), true);
		publishAll();
		dirty = true;
	}

	public static void tick() {
		updateJitter(System.currentTimeMillis(), false);

		if (dirty && configPath != null) {
			// Save at most around once per second so slider dragging does not
			// hammer the disk. The exact tick count is not important.
			if ((System.currentTimeMillis() & 0x3FFL) < 50L) {
				save();
			}
		}
	}

	public static synchronized void save() {
		if (configPath == null) {
			return;
		}

		try {
			Files.createDirectories(configPath.getParent());
			Files.writeString(configPath, GSON.toJson(config));
			dirty = false;
		} catch (IOException exception) {
			MathMayhem.LOGGER.warn("Could not save {}", configPath, exception);
		}
	}

	/**
	 * Emergency stop. This deliberately resets the numerical values as well as
	 * the mode, so re-enabling later cannot immediately reproduce a crash.
	 */
	public static void panic() {
		config.safeOff();
		crashRecoveryApplied = false;
		crashRecoveryFile = null;
		changed();
		save();
	}

	public static String jvmArgument() {
		Path path = modPath();

		if (path == null) {
			return "-javaagent:/absolute/path/to/mathmayhem-1.0.1.jar";
		}

		String value = path.toAbsolutePath().toString();

		if (value.indexOf(' ') >= 0) {
			value = '"' + value + '"';
		}

		return "-javaagent:" + value;
	}

	public static Path modPath() {
		return FabricLoader.getInstance().getModContainer(MathMayhem.MOD_ID)
				.flatMap(container -> container.getOrigin().getPaths().stream().findFirst())
				.map(Path::toAbsolutePath)
				.orElse(null);
	}

	public static Path configPath() {
		return configPath;
	}

	/**
	 * A worldgen crash can leave an extreme configuration persisted. On the
	 * next launch, detect a crash report newer than the config and automatically
	 * return to the safe configuration. This runs once: saving the recovered
	 * config makes it newer than the old crash report.
	 */
	private static void recoverFromRecentWorldgenCrash() {
		if (config.mode == MathConfig.Mode.OFF || !config.isAggressive()) {
			return;
		}

		Path crashDirectory = FabricLoader.getInstance().getGameDir().resolve("crash-reports");

		if (!Files.isDirectory(crashDirectory)) {
			return;
		}

		long configTime = 0L;

		try {
			if (Files.isRegularFile(configPath)) {
				configTime = Files.getLastModifiedTime(configPath).toMillis();
			}
		} catch (IOException ignored) {
		}

		long cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L;

		try (Stream<Path> stream = Files.list(crashDirectory)) {
			List<Path> reports = stream
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".txt"))
					.sorted(Comparator.comparingLong(ConfigManager::lastModified).reversed())
					.limit(8)
					.toList();

			for (Path report : reports) {
				long reportTime = lastModified(report);

				if (reportTime < cutoff) {
					break;
				}

				if (reportTime <= configTime) {
					continue;
				}

				try {
					if (Files.size(report) > 2_000_000L) {
						continue;
					}

					String text = Files.readString(report);

					if (text.contains("Exception generating new chunk") && text.contains("mathmayhem")) {
						config.safeOff();
						crashRecoveryApplied = true;
						crashRecoveryFile = report.getFileName().toString();
						MathMayhem.LOGGER.warn(
								"Detected a recent worldgen crash in {}; Math Mayhem was reset to safe defaults "
										+ "and disabled. The panel is available from the title screen with F6.",
								report.getFileName());
						return;
					}
				} catch (IOException exception) {
					MathMayhem.LOGGER.debug("Could not inspect crash report {}", report, exception);
				}
			}
		} catch (IOException exception) {
			MathMayhem.LOGGER.debug("Could not scan the crash-reports directory.", exception);
		}
	}

	private static long lastModified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException ignored) {
			return 0L;
		}
	}

	private static void applyVolatileState() {
		effectiveMode = config.mode;
		effectiveOffset = config.offset;
		guardEnabled = config.nanGuard;
	}

	private static void updateJitter(long now, boolean force) {
		double nextScale = config.scale;

		if (config.jitter && config.jitterAmount > 0.0D) {
			long periodMillis = Math.max(1_000L, (long) (config.jitterSeconds * 1_000.0D));
			long epoch = Math.floorDiv(now, periodMillis);

			if (force || epoch != jitterEpoch) {
				jitterEpoch = epoch;
				jitterRoll = hashToUnit(epoch * 0x9E3779B97F4A7C15L + 0x1234_5678_9ABC_DEF0L) * 2.0D - 1.0D;
			}

			nextScale = config.scale + jitterRoll * config.jitterAmount;
		} else {
			jitterEpoch = Long.MIN_VALUE;
		}

		nextScale = Math.max(0.1D, Math.min(3.0D, nextScale));

		if (nextScale != effectiveScale) {
			effectiveScale = nextScale;
			setProperty("mathmayhem.effectiveScale", format(nextScale));
		}
	}

	private static void publishAll() {
		setProperty("mathmayhem.mode", config.mode.id());
		setProperty("mathmayhem.scale", format(config.scale));
		setProperty("mathmayhem.offset", format(config.offset));
		setProperty("mathmayhem.effectiveScale", format(effectiveScale));
		setProperty("mathmayhem.ops", config.opsProperty());
		setProperty("mathmayhem.guard", Boolean.toString(config.nanGuard));
		setProperty("mathmayhem.revision", Long.toString(++revision));
	}

	private static void setProperty(String key, String value) {
		try {
			System.setProperty(key, value);
		} catch (SecurityException ignored) {
			// A hardened launcher can refuse property writes. The GUI still
			// works, but the agent may not see live changes.
		}
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.9f", value);
	}

	private static double hashToUnit(long value) {
		long z = value + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z = z ^ (z >>> 31);
		return (z >>> 11) * 0x1.0p-53;
	}
}
