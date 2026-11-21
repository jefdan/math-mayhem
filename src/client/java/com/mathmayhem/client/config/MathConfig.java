package com.mathmayhem.client.config;

import java.util.Locale;

/**
 * Serializable client configuration. Values are deliberately simple and
 * bounded; the agent and the worldgen fallback both sanitize them again.
 */
public final class MathConfig {
	public static final double MIN_SCALE = 0.50D;
	public static final double MAX_SCALE = 1.50D;
	public static final double MIN_OFFSET = -0.10D;
	public static final double MAX_OFFSET = 0.10D;
	public static final double MIN_JITTER = 0.0D;
	public static final double MAX_JITTER = 0.10D;
	public static final double MIN_JITTER_SECONDS = 1.0D;
	public static final double MAX_JITTER_SECONDS = 30.0D;

	public Mode mode = Mode.WORLDGEN;
	public double scale = 1.03D;
	public double offset = 0.0D;
	public boolean add = true;
	public boolean mul = true;
	public boolean div = false;
	public boolean jitter = false;
	public double jitterAmount = 0.01D;
	public double jitterSeconds = 5.0D;
	public boolean nanGuard = true;

	public void sanitize() {
		if (mode == null) {
			mode = Mode.WORLDGEN;
		}

		scale = clampFinite(scale, MIN_SCALE, MAX_SCALE, 1.03D);
		offset = clampFinite(offset, MIN_OFFSET, MAX_OFFSET, 0.0D);
		jitterAmount = clampFinite(jitterAmount, MIN_JITTER, MAX_JITTER, 0.01D);
		jitterSeconds = clampFinite(jitterSeconds, MIN_JITTER_SECONDS, MAX_JITTER_SECONDS, 5.0D);
	}

	/**
	 * True when the current values are far outside the range that reliably
	 * generates structures. Used by crash recovery and the UI warning.
	 */
	public boolean isAggressive() {
		if (mode == Mode.OFF) {
			return false;
		}

		return scale > 1.08D
				|| scale < 0.92D
				|| Math.abs(offset) > 0.01D
				|| (jitter && jitterAmount > 0.02D);
	}

	/** Restores the known-stable defaults and turns tampering off. */
	public void safeOff() {
		mode = Mode.OFF;
		scale = 1.03D;
		offset = 0.0D;
		add = true;
		mul = true;
		div = false;
		jitter = false;
		jitterAmount = 0.01D;
		jitterSeconds = 5.0D;
		nanGuard = true;
	}

	public String opsProperty() {
		StringBuilder builder = new StringBuilder();

		if (add) {
			builder.append("add");
		}

		if (mul) {
			if (builder.length() > 0) {
				builder.append(',');
			}

			builder.append("mul");
		}

		if (div) {
			if (builder.length() > 0) {
				builder.append(',');
			}

			builder.append("div");
		}

		return builder.toString();
	}

	public String scaleText() {
		return String.format(Locale.ROOT, "%.3f", scale);
	}

	public String offsetText() {
		return String.format(Locale.ROOT, "%.3f", offset);
	}

	public String jitterText() {
		return String.format(Locale.ROOT, "%.3f", jitterAmount);
	}

	private static double clampFinite(double value, double min, double max, double fallback) {
		if (!Double.isFinite(value)) {
			return fallback;
		}

		return Math.max(min, Math.min(max, value));
	}

	public enum Mode {
		WORLDGEN("worldgen", "Worldgen only"),
		ALL("all", "All game math"),
		OFF("off", "Off");

		private final String id;
		private final String displayName;

		Mode(String id, String displayName) {
			this.id = id;
			this.displayName = displayName;
		}

		public String id() {
			return id;
		}

		public String displayName() {
			return displayName;
		}

		public Mode next() {
			return switch (this) {
				case WORLDGEN -> ALL;
				case ALL -> OFF;
				case OFF -> WORLDGEN;
			};
		}
	}
}
