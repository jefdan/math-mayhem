package com.mathmayhem.client.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds a visible Math Mayhem button to the title screen. This exists so a
 * world-breaking configuration can always be corrected before a world is
 * loaded, even if the player does not know the F6 shortcut.
 */
public final class TitleScreenIntegration {
	private TitleScreenIntegration() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof TitleScreen)) {
				return;
			}

			String label = Component.translatable("screen.mathmayhem.title").getString();
			boolean alreadyPresent = screen.children().stream()
					.anyMatch(child -> child instanceof Button button
							&& button.getMessage().getString().equals(label));

			if (alreadyPresent) {
				return;
			}

			Button button = Button.builder(Component.translatable("screen.mathmayhem.title"),
							clicked -> client.gui.setScreen(new MathConfigScreen(screen)))
					.bounds(8, Math.max(8, scaledHeight - 32), 118, 20)
					.tooltip(Tooltip.create(Component.translatable("screen.mathmayhem.tooltip")))
					.build();

			Screens.getWidgets(screen).add(button);
		});
	}
}
