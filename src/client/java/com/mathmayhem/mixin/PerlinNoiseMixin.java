package com.mathmayhem.mixin;

import com.mathmayhem.client.math.NoiseTap;

import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stable, agent-free terrain distortion. The agent path replaces this by
 * rewriting actual FADD/DADD/FMUL/DMUL/FDIV/DDIV instructions inside worldgen
 * classes, but this Mixin keeps the mod useful with a plain drag-and-drop jar.
 */
@Mixin(PerlinNoise.class)
public abstract class PerlinNoiseMixin {
	@Inject(method = "get(DDD)F", at = @At("RETURN"), cancellable = true)
	private void mathmayhem$tamperPerlin3d(double x, double y, double z,
			CallbackInfoReturnable<Float> callback) {
		float original = callback.getReturnValue();
		float tampered = NoiseTap.tamper(original);

		if (Float.floatToIntBits(original) != Float.floatToIntBits(tampered)) {
			callback.setReturnValue(tampered);
		}
	}
}
