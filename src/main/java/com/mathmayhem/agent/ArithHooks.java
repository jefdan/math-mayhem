package com.mathmayhem.agent;

import java.util.Locale;

/**
 * Runtime hooks injected by {@link MathClassTransformer}.
 *
 * <p>This class is loaded in more than one class loader in some setups (once by
 * the JVM agent/system loader and once through Fabric's mod class loader). It
 * therefore keeps no cross-loader state: every copy observes the same system
 * properties and updates its own tiny immutable settings snapshot.</p>
 *
 * <p>No Minecraft classes are referenced here. The hot path is a single volatile
 * read, a mode check, and ordinary arithmetic.</p>
 */
public final class ArithHooks {
	private static final int OP_ADD = 1;
	private static final int OP_MUL = 2;
	private static final int OP_DIV = 4;

	private static final Settings FALLBACK = new Settings(
			AgentConfig.MODE_WORLDGEN, 1.03D, 0.0D, OP_ADD | OP_MUL, true);

	private static volatile Settings settings = readSettings();

	private ArithHooks() {
	}

	static {
		Thread refresher = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Thread.sleep(100L);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return;
				}

				try {
					settings = readSettings();
				} catch (Throwable ignored) {
					settings = FALLBACK;
				}
			}
		}, "MathMayhem-Settings");
		refresher.setDaemon(true);
		refresher.start();
	}

	public static float fadd(float left, float right, int requiredMode) {
		float raw = left + right;

		try {
			Settings current = settings;

			if (!current.active(requiredMode)) {
				return raw;
			}

			if ((current.ops & OP_ADD) == 0) {
				return current.guard ? guardFloat(raw) : raw;
			}

			return perturbFloat(raw, current);
		} catch (Throwable throwable) {
			return safeFloat(raw);
		}
	}

	public static float fmul(float left, float right, int requiredMode) {
		float raw = left * right;

		try {
			Settings current = settings;

			if (!current.active(requiredMode)) {
				return raw;
			}

			if ((current.ops & OP_MUL) == 0) {
				return current.guard ? guardFloat(raw) : raw;
			}

			return perturbFloat(raw, current);
		} catch (Throwable throwable) {
			return safeFloat(raw);
		}
	}

	public static float fdiv(float left, float right, int requiredMode) {
		float raw = left / right;

		try {
			Settings current = settings;

			if (!current.active(requiredMode)) {
				return raw;
			}

			if ((current.ops & OP_DIV) == 0) {
				return current.guard ? guardFloat(raw) : raw;
			}

			return perturbFloat(raw, current);
		} catch (Throwable throwable) {
			return safeFloat(raw);
		}
	}

	public static double dadd(double left, double right, int requiredMode) {
		double raw = left + right;

		try {
			Settings current = settings;

			if (!current.active(requiredMode)) {
				return raw;
			}

			if ((current.ops & OP_ADD) == 0) {
				return current.guard ? guardDouble(raw) : raw;
			}

			return perturbDouble(raw, current);
		} catch (Throwable throwable) {
			return safeDouble(raw);
		}
	}

	public static double dmul(double left, double right, int requiredMode) {
		double raw = left * right;

		try {
			Settings current = settings;

			if (!current.active(requiredMode)) {
				return raw;
			}

			if ((current.ops & OP_MUL) == 0) {
				return current.guard ? guardDouble(raw) : raw;
			}

			return perturbDouble(raw, current);
		} catch (Throwable throwable) {
			return safeDouble(raw);
		}
	}

	public static double ddiv(double left, double right, int requiredMode) {
		double raw = left / right;

		try {
			Settings current = settings;

			if (!current.active(requiredMode)) {
				return raw;
			}

			if ((current.ops & OP_DIV) == 0) {
				return current.guard ? guardDouble(raw) : raw;
			}

			return perturbDouble(raw, current);
		} catch (Throwable throwable) {
			return safeDouble(raw);
		}
	}

	/**
	 * Forces an immediate configuration read. Useful for tests and for the
	 * attach path; normal operation uses the background refresher.
	 */
	public static void refreshNow() {
		settings = readSettings();
	}

	private static float perturbFloat(float raw, Settings current) {
		if (!Float.isFinite(raw)) {
			return current.guard ? 0.0F : raw;
		}

		if (current.scale == 1.0D && current.offset == 0.0D) {
			return raw;
		}

		double transformed = raw * current.scale + current.offset;

		if (!Double.isFinite(transformed)) {
			return raw;
		}

		float result = (float) transformed;
		return Float.isFinite(result) ? result : raw;
	}

	private static double perturbDouble(double raw, Settings current) {
		if (!Double.isFinite(raw)) {
			return current.guard ? 0.0D : raw;
		}

		if (current.scale == 1.0D && current.offset == 0.0D) {
			return raw;
		}

		double transformed = raw * current.scale + current.offset;
		return Double.isFinite(transformed) ? transformed : raw;
	}

	private static float guardFloat(float value) {
		return Float.isFinite(value) ? value : 0.0F;
	}

	private static double guardDouble(double value) {
		return Double.isFinite(value) ? value : 0.0D;
	}

	private static float safeFloat(float value) {
		return Float.isFinite(value) ? value : 0.0F;
	}

	private static double safeDouble(double value) {
		return Double.isFinite(value) ? value : 0.0D;
	}

	private static Settings readSettings() {
		try {
			int mode = AgentConfig.parseMode(System.getProperty(AgentConfig.PROP_MODE, "worldgen"));

			double scale = parseDouble(System.getProperty(AgentConfig.PROP_EFFECTIVE_SCALE),
					parseDouble(System.getProperty(AgentConfig.PROP_SCALE), 1.03D));
			scale = clamp(scale, 0.1D, 3.0D);

			double offset = clamp(parseDouble(System.getProperty(AgentConfig.PROP_OFFSET), 0.0D), -1.0D, 1.0D);
			int ops = parseOps(System.getProperty(AgentConfig.PROP_OPS, "add,mul"));
			boolean guard = Boolean.parseBoolean(System.getProperty(AgentConfig.PROP_GUARD, "true"));

			return new Settings(mode, scale, offset, ops, guard);
		} catch (Throwable throwable) {
			return FALLBACK;
		}
	}

	private static double parseDouble(String value, double fallback) {
		if (value == null) {
			return fallback;
		}

		try {
			double parsed = Double.parseDouble(value);
			return Double.isFinite(parsed) ? parsed : fallback;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int parseOps(String value) {
		int mask = 0;

		for (String token : value.toLowerCase(Locale.ROOT).split(",")) {
			switch (token.trim()) {
				case "add", "fadd", "dadd" -> mask |= OP_ADD;
				case "mul", "fmul", "dmul" -> mask |= OP_MUL;
				case "div", "fdiv", "ddiv" -> mask |= OP_DIV;
				default -> {
				}
			}
		}

		return mask;
	}

	private record Settings(int mode, double scale, double offset, int ops, boolean guard) {
		boolean active(int requiredMode) {
			if (mode == AgentConfig.MODE_OFF) {
				return false;
			}

			if (requiredMode == AgentConfig.MODE_ALL) {
				return mode == AgentConfig.MODE_ALL;
			}

			return mode == AgentConfig.MODE_WORLDGEN || mode == AgentConfig.MODE_ALL;
		}
	}
}
