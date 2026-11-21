package com.mathmayhem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared, Minecraft-independent constants for Math Mayhem.
 *
 * <p>Keep this class free of Minecraft references: the agent side of the mod
 * must be able to load before Minecraft's own classes exist.</p>
 */
public final class MathMayhem {
	public static final String MOD_ID = "mathmayhem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private MathMayhem() {
	}
}
