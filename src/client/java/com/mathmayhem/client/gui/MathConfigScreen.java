package com.mathmayhem.client.gui;

import com.mathmayhem.client.config.ConfigManager;
import com.mathmayhem.client.config.MathConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Live control panel for Math Mayhem.
 *
 * <p>All values are published immediately. Generation changes apply to chunks
 * that are generated after the change; existing chunks are untouched.</p>
 */
public final class MathConfigScreen extends Screen {
	private static final int CYAN = 0xFF39E6FF;
	private static final int AMBER = 0xFFFFC857;
	private static final int RED = 0xFFFF5C7A;
	private static final int GREEN = 0xFF72F1A7;
	private static final int MUTED = 0xFF9AA7B8;
	private static final int WHITE = 0xFFFFFFFF;

	private final MathConfig config = ConfigManager.get();
	private final Screen parent;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int leftX;
	private int rightX;
	private int columnWidth;
	private int rowY;

	private Button modeButton;
	private Button addButton;
	private Button mulButton;
	private Button divButton;
	private Button jitterButton;
	private Button guardButton;

	public MathConfigScreen() {
		this(null);
	}

	public MathConfigScreen(Screen parent) {
		super(Component.translatable("screen.mathmayhem.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(380, Math.max(240, this.width - 20));
		panelHeight = Math.min(244, Math.max(190, this.height - 20));
		panelX = (this.width - panelWidth) / 2;
		panelY = (this.height - panelHeight) / 2;

		leftX = panelX + 16;
		columnWidth = Math.max(92, (panelWidth - 32 - 14) / 2);
		rightX = leftX + columnWidth + 14;
		rowY = panelY + 50;

		int toggleWidth = Math.max(28, (columnWidth - 8) / 3);

		modeButton = Button.builder(Component.empty(), button -> {
			config.mode = config.mode.next();
			ConfigManager.changed();
			updateButtonLabels();
		}).bounds(leftX, rowY, columnWidth, 20).build();

		addButton = Button.builder(Component.empty(), button -> {
			config.add = !config.add;
			ConfigManager.changed();
			updateButtonLabels();
		}).bounds(leftX, rowY + 24, toggleWidth, 20).build();

		mulButton = Button.builder(Component.empty(), button -> {
			config.mul = !config.mul;
			ConfigManager.changed();
			updateButtonLabels();
		}).bounds(leftX + toggleWidth + 4, rowY + 24, toggleWidth, 20).build();

		divButton = Button.builder(Component.empty(), button -> {
			config.div = !config.div;
			ConfigManager.changed();
			updateButtonLabels();
		}).bounds(leftX + (toggleWidth + 4) * 2, rowY + 24, toggleWidth, 20).build();

		jitterButton = Button.builder(Component.empty(), button -> {
			config.jitter = !config.jitter;
			ConfigManager.changed();
			updateButtonLabels();
		}).bounds(leftX, rowY + 48, columnWidth, 20).build();

		guardButton = Button.builder(Component.empty(), button -> {
			config.nanGuard = !config.nanGuard;
			ConfigManager.changed();
			updateButtonLabels();
		}).bounds(leftX, rowY + 72, columnWidth, 20).build();

		addRenderableWidget(modeButton);
		addRenderableWidget(addButton);
		addRenderableWidget(mulButton);
		addRenderableWidget(divButton);
		addRenderableWidget(jitterButton);
		addRenderableWidget(guardButton);

		addRenderableWidget(new OptionSlider(rightX, rowY, columnWidth, 20,
				"Scale", MathConfig.MIN_SCALE, MathConfig.MAX_SCALE, config.scale, 3, value -> {
					config.scale = value;
					ConfigManager.changed();
				}));

		addRenderableWidget(new OptionSlider(rightX, rowY + 24, columnWidth, 20,
				"Offset", MathConfig.MIN_OFFSET, MathConfig.MAX_OFFSET, config.offset, 3, value -> {
					config.offset = value;
					ConfigManager.changed();
				}));

		addRenderableWidget(new OptionSlider(rightX, rowY + 48, columnWidth, 20,
				"Jitter ±", MathConfig.MIN_JITTER, MathConfig.MAX_JITTER, config.jitterAmount, 3, value -> {
					config.jitterAmount = value;
					ConfigManager.changed();
				}));

		addRenderableWidget(new OptionSlider(rightX, rowY + 72, columnWidth, 20,
				"Period (s)", MathConfig.MIN_JITTER_SECONDS, MathConfig.MAX_JITTER_SECONDS,
				config.jitterSeconds, 1, value -> {
					config.jitterSeconds = value;
					ConfigManager.changed();
				}));

		int buttonsY = panelY + panelHeight - 30;
		int resetWidth = 72;
		int copyWidth = 118;
		int doneWidth = 64;
		int total = resetWidth + copyWidth + doneWidth + 16;
		int start = panelX + (panelWidth - total) / 2;

		addRenderableWidget(Button.builder(Component.literal("Safe Off"), button -> {
			ConfigManager.panic();
			this.rebuildWidgets();
		}).bounds(start, buttonsY, resetWidth, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Copy JVM arg"), button -> {
			if (this.minecraft != null) {
				this.minecraft.keyboardHandler.setClipboard(ConfigManager.jvmArgument());

				if (this.minecraft.player != null) {
					this.minecraft.player.sendOverlayMessage(
							Component.translatable("message.mathmayhem.copied"));
				}
			}
		}).bounds(start + resetWidth + 8, buttonsY, copyWidth, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
				.bounds(start + resetWidth + copyWidth + 16, buttonsY, doneWidth, 20).build());

		updateButtonLabels();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);

		if (panelWidth <= 0 || panelHeight <= 0) {
			return;
		}

		graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF39E6FF);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xEE0D1320);
		graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 32, 0x5528D7F0);

		graphics.centeredText(this.font, Component.literal("MATH MAYHEM"), this.width / 2, panelY + 8, CYAN);

		boolean agent = ConfigManager.isAgentActive();
		String status;
		int statusColor;

		if (agent) {
			status = switch (config.mode) {
				case ALL -> "AGENT ACTIVE  ·  ALL GAME MATH";
				case WORLDGEN -> "AGENT ACTIVE  ·  WORLDGEN FILTER";
				case OFF -> "AGENT ACTIVE  ·  DISABLED";
			};
			statusColor = config.mode == MathConfig.Mode.OFF ? MUTED : GREEN;
		} else {
			status = config.mode == MathConfig.Mode.ALL
					? "NO AGENT  ·  ALL MODE FALLS BACK TO WORLDGEN"
					: "NO AGENT  ·  SAFE WORLDGEN FALLBACK";
			statusColor = config.mode == MathConfig.Mode.OFF ? MUTED : AMBER;
		}

		graphics.centeredText(this.font, Component.literal(status), this.width / 2, panelY + 22, statusColor);

		if (config.isAggressive()) {
			graphics.centeredText(this.font,
					Component.literal("Warning: extreme values can crash world generation."),
					this.width / 2, panelY + 34, RED);
		} else if (ConfigManager.wasCrashRecovered()) {
			graphics.centeredText(this.font,
					Component.literal("Crash recovery: safe defaults loaded and tampering disabled."),
					this.width / 2, panelY + 34, AMBER);
		}

		int noteY = panelY + panelHeight - 82;
		graphics.text(this.font, "Changes apply live to newly generated chunks.", leftX, noteY, MUTED, false);
		graphics.text(this.font, "Existing chunks keep their terrain. Back up worlds.", leftX, noteY + 12, MUTED, false);
		graphics.text(this.font, "F6 panel  ·  F7 safe-off", leftX, noteY + 24, 0xFF6C7A90, false);
	}

	@Override
	public void removed() {
		ConfigManager.save();
		super.removed();
	}

	@Override
	public void onClose() {
		ConfigManager.save();

		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	public void refreshFromConfig() {
		this.rebuildWidgets();
	}

	public Screen parent() {
		return this.parent;
	}

	private void updateButtonLabels() {
		Component modeValue = Component.literal(config.mode.displayName())
				.withStyle(config.mode == MathConfig.Mode.OFF ? ChatFormatting.GRAY
						: config.mode == MathConfig.Mode.ALL ? ChatFormatting.RED : ChatFormatting.AQUA);
		modeButton.setMessage(Component.literal("Mode: ").append(modeValue));

		setToggleLabel(addButton, "ADD", config.add);
		setToggleLabel(mulButton, "MUL", config.mul);
		setToggleLabel(divButton, "DIV", config.div);

		jitterButton.setMessage(Component.literal("RNG: ").append(Component
				.literal(config.jitter ? "Time jitter" : "Static")
				.withStyle(config.jitter ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY)));

		guardButton.setMessage(Component.literal("NaN guard: ").append(Component
				.literal(config.nanGuard ? "ON" : "OFF")
				.withStyle(config.nanGuard ? ChatFormatting.GREEN : ChatFormatting.RED)));
	}

	private static void setToggleLabel(Button button, String name, boolean enabled) {
		button.setMessage(Component.literal(name + ": ").append(Component
				.literal(enabled ? "ON" : "OFF")
				.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)));
	}
}
