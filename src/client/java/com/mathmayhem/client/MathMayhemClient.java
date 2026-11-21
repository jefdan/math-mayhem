package com.mathmayhem.client;

import com.mathmayhem.MathMayhem;
import com.mathmayhem.client.config.ConfigManager;
import com.mathmayhem.client.gui.TitleScreenIntegration;
import com.mathmayhem.client.input.Keybinds;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class MathMayhemClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ConfigManager.initialize();
		Keybinds.register();
		TitleScreenIntegration.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ConfigManager.tick();
			Keybinds.tick(client);
		});

		MathMayhem.LOGGER.info("Math Mayhem online. Mode: {}, agent: {}",
				ConfigManager.getMode().id(),
				ConfigManager.isAgentActive() ? "loaded" : "not loaded (worldgen fallback)");

		if (!ConfigManager.isAgentActive()) {
			MathMayhem.LOGGER.info("Full 'all game math' mode needs the JVM argument: {}",
					ConfigManager.jvmArgument());
		}
	}
}
