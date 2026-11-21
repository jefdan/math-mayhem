package com.mathmayhem.mixin;

import com.mathmayhem.client.math.NoiseTap;

import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimplexNoise.class)
public abstract class SimplexNoiseMixin {
	@Inject(method = "get(DD)F", at = @At("RETURN"), cancellable = true)
	private void mathmayhem$tamperSimplex2d(double x, double y,
			CallbackInfoReturnable<Float> callback) {
		float original = callback.getReturnValue();
		float tampered = NoiseTap.tamper(original);

		if (Float.floatToIntBits(original) != Float.floatToIntBits(tampered)) {
			callback.setReturnValue(tampered);
		}
	}

	@Inject(method = "get(DDD)F", at = @At("RETURN"), cancellable = true)
	private void mathmayhem$tamperSimplex3d(double x, double y, double z,
			CallbackInfoReturnable<Float> callback) {
		float original = callback.getReturnValue();
		float tampered = NoiseTap.tamper(original);

		if (Float.floatToIntBits(original) != Float.floatToIntBits(tampered)) {
			callback.setReturnValue(tampered);
		}
	}
}
