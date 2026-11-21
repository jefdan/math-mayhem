package com.mathmayhem.client.input;

import com.mathmayhem.client.config.ConfigManager;
import com.mathmayhem.client.gui.MathConfigScreen;
import com.mathmayhem.MathMayhem;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class Keybinds {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MathMayhem.MOD_ID, "general"));

	private static final KeyMapping OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.mathmayhem.open",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_F6,
			CATEGORY));

	private static final KeyMapping PANIC = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.mathmayhem.panic",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_F7,
			CATEGORY));

	private Keybinds() {
	}

	public static void register() {
		// Class initialization registers both mappings through KeyMappingHelper.
	}

	public static void tick(Minecraft minecraft) {
		while (OPEN.consumeClick()) {
			Screen current = minecraft.gui.screen();

			if (current instanceof MathConfigScreen configScreen) {
				minecraft.gui.setScreen(configScreen.parent());
			} else if (current == null || current instanceof TitleScreen || current.isPauseScreen()) {
				// F6 now works from the title screen and the pause menu, not just
				// while the in-game HUD is visible.
				minecraft.gui.setScreen(new MathConfigScreen(current));
			}
		}

		while (PANIC.consumeClick()) {
			ConfigManager.panic();

			if (minecraft.gui.screen() instanceof MathConfigScreen configScreen) {
				configScreen.refreshFromConfig();
			}

			if (minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.translatable("message.mathmayhem.panic"));
			}
		}
	}
}
