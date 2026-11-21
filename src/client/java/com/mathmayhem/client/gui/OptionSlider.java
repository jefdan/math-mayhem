package com.mathmayhem.client.gui;

import java.util.Locale;
import java.util.function.DoubleConsumer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A compact vanilla-styled slider with a fixed decimal range and a live value
 * callback.
 */
public final class OptionSlider extends AbstractSliderButton {
	private final String label;
	private final double min;
	private final double max;
	private final int decimals;
	private final DoubleConsumer responder;

	public OptionSlider(int x, int y, int width, int height, String label,
			double min, double max, double initial, int decimals, DoubleConsumer responder) {
		super(x, y, width, height, Component.empty(), normalize(initial, min, max));
		this.label = label;
		this.min = min;
		this.max = max;
		this.decimals = decimals;
		this.responder = responder;
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": "
				+ String.format(Locale.ROOT, "%." + decimals + "f", actualValue())));
	}

	@Override
	protected void applyValue() {
		responder.accept(actualValue());
	}

	private double actualValue() {
		return min + (max - min) * value;
	}

	private static double normalize(double value, double min, double max) {
		if (max <= min) {
			return 0.0D;
		}

		return Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min)));
	}
}
