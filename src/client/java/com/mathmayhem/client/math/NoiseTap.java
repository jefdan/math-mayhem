package com.mathmayhem.client.math;

import com.mathmayhem.client.config.ConfigManager;
import com.mathmayhem.client.config.MathConfig;

/**
 * Last-resort worldgen perturbation used when the JVM agent is not installed.
 *
 * <p>It operates on the output of the two leaf noise samplers rather than on
 * their individual arithmetic instructions. That keeps the fallback stable and
 * still gives terrain a strong, unmistakable nudge.</p>
 */
public final class NoiseTap {
	private NoiseTap() {
	}

	public static float tamper(float value) {
		if (ConfigManager.isAgentActive()) {
			// The agent has already rewritten arithmetic in these classes. Do
			// not apply a second, independent distortion.
			return value;
		}

		MathConfig config = ConfigManager.get();

		if (config == null || config.mode == MathConfig.Mode.OFF) {
			return value;
		}

		double scale = ConfigManager.getEffectiveScale();
		double offset = ConfigManager.getEffectiveOffset();

		if (scale == 1.0D && offset == 0.0D) {
			return value;
		}

		double transformed = value * scale + offset;

		if (!Double.isFinite(transformed)) {
			return ConfigManager.isGuardEnabled() ? 0.0F : value;
		}

		float result = (float) transformed;

		if (!Float.isFinite(result)) {
			return ConfigManager.isGuardEnabled() ? 0.0F : value;
		}

		return result;
	}
}
