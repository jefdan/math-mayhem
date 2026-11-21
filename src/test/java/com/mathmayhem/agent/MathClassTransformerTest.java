package com.mathmayhem.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class MathClassTransformerTest {
	private static final String FIXTURE = "fixture/ArithmeticFixture";

	@BeforeEach
	void configure() {
		System.setProperty(AgentConfig.PROP_MODE, "worldgen");
		System.setProperty(AgentConfig.PROP_SCALE, "1.1");
		System.setProperty(AgentConfig.PROP_OFFSET, "0");
		System.setProperty(AgentConfig.PROP_OPS, "add,mul,div");
		System.setProperty(AgentConfig.PROP_GUARD, "true");
		System.clearProperty(AgentConfig.PROP_EFFECTIVE_SCALE);
		ArithHooks.refreshNow();
	}

	@AfterEach
	void reset() {
		System.clearProperty(AgentConfig.PROP_MODE);
		System.clearProperty(AgentConfig.PROP_SCALE);
		System.clearProperty(AgentConfig.PROP_OFFSET);
		System.clearProperty(AgentConfig.PROP_OPS);
		System.clearProperty(AgentConfig.PROP_GUARD);
		System.clearProperty(AgentConfig.PROP_EFFECTIVE_SCALE);
		ArithHooks.refreshNow();
	}

	@Test
	void rewritesFloatAndDoubleArithmeticAndPreservesVerification() throws Exception {
		byte[] rewritten = MathClassTransformer.transformBytes(fixtureBytes(), AgentConfig.MODE_WORLDGEN);
		assertNotNull(rewritten, "fixture should contain target arithmetic");

		Class<?> fixture = new ByteLoader().define(FIXTURE, rewritten);

		assertEquals(5.5F, invokeFloat(fixture, "fadd", 2.0F, 3.0F), 0.0001F);
		assertEquals(5.5D, invokeDouble(fixture, "dadd", 2.0D, 3.0D), 0.0001D);
		assertEquals(6.6F, invokeFloat(fixture, "fmul", 2.0F, 3.0F), 0.0001F);
		assertEquals(6.6D, invokeDouble(fixture, "dmul", 2.0D, 3.0D), 0.0001D);
		assertEquals(2.2F, invokeFloat(fixture, "fdiv", 6.0F, 3.0F), 0.0001F);
		assertEquals(2.2D, invokeDouble(fixture, "ddiv", 6.0D, 3.0D), 0.0001D);
	}

	@Test
	void modeTagsAllowLiveDowngradeFromAllToWorldgen() throws Exception {
		byte[] worldgen = MathClassTransformer.transformBytes(fixtureBytes(), AgentConfig.MODE_WORLDGEN);
		byte[] broad = MathClassTransformer.transformBytes(fixtureBytes(), AgentConfig.MODE_ALL);
		assertNotNull(worldgen);
		assertNotNull(broad);

		Class<?> worldgenFixture = new ByteLoader().define(FIXTURE, worldgen);
		Class<?> broadFixture = new ByteLoader().define(FIXTURE, broad);

		System.setProperty(AgentConfig.PROP_MODE, "worldgen");
		ArithHooks.refreshNow();
		assertEquals(5.5F, invokeFloat(worldgenFixture, "fadd", 2.0F, 3.0F), 0.0001F);
		assertEquals(5.0F, invokeFloat(broadFixture, "fadd", 2.0F, 3.0F), 0.0001F);

		System.setProperty(AgentConfig.PROP_MODE, "all");
		ArithHooks.refreshNow();
		assertEquals(5.5F, invokeFloat(broadFixture, "fadd", 2.0F, 3.0F), 0.0001F);
	}

	@Test
	void operationTogglesAndOffModeLeaveRawValue() throws Exception {
		byte[] rewritten = MathClassTransformer.transformBytes(fixtureBytes(), AgentConfig.MODE_WORLDGEN);
		Class<?> fixture = new ByteLoader().define(FIXTURE, rewritten);

		System.setProperty(AgentConfig.PROP_OPS, "mul");
		ArithHooks.refreshNow();
		assertEquals(5.0F, invokeFloat(fixture, "fadd", 2.0F, 3.0F), 0.0001F);
		assertEquals(6.6F, invokeFloat(fixture, "fmul", 2.0F, 3.0F), 0.0001F);

		System.setProperty(AgentConfig.PROP_MODE, "off");
		System.setProperty(AgentConfig.PROP_OPS, "add,mul,div");
		ArithHooks.refreshNow();
		assertEquals(5.0F, invokeFloat(fixture, "fadd", 2.0F, 3.0F), 0.0001F);
	}

	@Test
	void guardConvertsNonFiniteResultsToZero() throws Exception {
		byte[] rewritten = MathClassTransformer.transformBytes(fixtureBytes(), AgentConfig.MODE_WORLDGEN);
		Class<?> fixture = new ByteLoader().define(FIXTURE, rewritten);

		assertEquals(0.0F, invokeFloat(fixture, "fdiv", 0.0F, 0.0F), 0.0001F);
		assertEquals(0.0D, invokeDouble(fixture, "ddiv", 1.0D, 0.0D), 0.0001D);
	}

	@Test
	void classesWithoutTargetArithmeticAreNotModified() {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/NoMath", null, "java/lang/Object", null);
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				"inc", "(I)I", null, null);
		method.visitCode();
		method.visitVarInsn(Opcodes.ILOAD, 0);
		method.visitInsn(Opcodes.ICONST_1);
		method.visitInsn(Opcodes.IADD);
		method.visitInsn(Opcodes.IRETURN);
		method.visitMaxs(2, 1);
		method.visitEnd();
		writer.visitEnd();

		assertNull(MathClassTransformer.transformBytes(writer.toByteArray(), AgentConfig.MODE_WORLDGEN));
	}

	@Test
	void filtersProtectRenderingAndAllowWorldgen() {
		assertTrue(AgentConfig.isWorldgen("net/minecraft/world/level/levelgen/synth/PerlinNoise"));
		assertTrue(AgentConfig.isWorldgen("net/minecraft/world/level/levelgen/densityfunction/DensityFunction"));
		assertFalse(AgentConfig.isWorldgen("net/minecraft/world/level/levelgen/structure/StructureStart"));
		assertFalse(AgentConfig.isWorldgen("net/minecraft/client/renderer/GameRenderer"));

		assertFalse(AgentConfig.isAllEligible("net/minecraft/client/renderer/GameRenderer"));
		assertFalse(AgentConfig.isAllEligible("net/minecraft/network/Packet"));
		assertFalse(AgentConfig.isAllEligible("com/mojang/blaze3d/platform/Window"));
		assertTrue(AgentConfig.isAllEligible("net/minecraft/world/entity/Entity"));
		assertTrue(AgentConfig.isAllEligible("net/minecraft/server/level/ServerLevel"));
	}

	private static float invokeFloat(Class<?> type, String method, float a, float b) throws Exception {
		Method target = type.getMethod(method, float.class, float.class);
		return (float) target.invoke(null, a, b);
	}

	private static double invokeDouble(Class<?> type, String method, double a, double b) throws Exception {
		Method target = type.getMethod(method, double.class, double.class);
		return (double) target.invoke(null, a, b);
	}

	private static byte[] fixtureBytes() {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, FIXTURE, null,
				"java/lang/Object", null);

		addBinary(writer, "fadd", "f", Opcodes.FLOAD, Opcodes.FADD, Opcodes.FRETURN, "(FF)F");
		addBinary(writer, "dadd", "d", Opcodes.DLOAD, Opcodes.DADD, Opcodes.DRETURN, "(DD)D");
		addBinary(writer, "fmul", "f", Opcodes.FLOAD, Opcodes.FMUL, Opcodes.FRETURN, "(FF)F");
		addBinary(writer, "dmul", "d", Opcodes.DLOAD, Opcodes.DMUL, Opcodes.DRETURN, "(DD)D");
		addBinary(writer, "fdiv", "f", Opcodes.FLOAD, Opcodes.FDIV, Opcodes.FRETURN, "(FF)F");
		addBinary(writer, "ddiv", "d", Opcodes.DLOAD, Opcodes.DDIV, Opcodes.DRETURN, "(DD)D");

		writer.visitEnd();
		return writer.toByteArray();
	}

	private static void addBinary(ClassWriter writer, String name, String type, int load,
			int opcode, int returnOpcode, String descriptor) {
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				name, descriptor, null, null);
		method.visitCode();
		method.visitVarInsn(load, 0);
		method.visitVarInsn(load, type.equals("d") ? 2 : 1);
		method.visitInsn(opcode);
		method.visitInsn(returnOpcode);
		method.visitMaxs(4, 4);
		method.visitEnd();
	}

	private static final class ByteLoader extends ClassLoader {
		ByteLoader() {
			super(ArithHooks.class.getClassLoader());
		}

		Class<?> define(String name, byte[] bytes) {
			return defineClass(name.replace('/', '.'), bytes, 0, bytes.length);
		}
	}
}
