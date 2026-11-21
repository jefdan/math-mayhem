package com.mathmayhem.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Rewrites float/double arithmetic instructions into calls to
 * {@link ArithHooks}.
 *
 * <p>Every replacement keeps the operand stack shape identical:</p>
 *
 * <pre>
 * FADD          -> invokestatic fadd(FFI)F
 * DADD          -> invokestatic dadd(DDI)D
 * FMUL          -> invokestatic fmul(FFI)F
 * DMUL          -> invokestatic dmul(DDI)D
 * FDIV          -> invokestatic fdiv(FFI)F
 * DDIV          -> invokestatic ddiv(DDI)D
 * </pre>
 *
 * <p>The extra {@code int} is the minimum mode required for the operation to
 * take effect. It lets a single loaded class obey live mode changes without
 * retransformation: worldgen-injected hooks are active in WORLDGEN and ALL,
 * broad hooks only in ALL.</p>
 */
public final class MathClassTransformer implements ClassFileTransformer {
	private static final String HOOKS = "com/mathmayhem/agent/ArithHooks";
	private static boolean loggedFirstClass;

	@Override
	public byte[] transform(Module module, ClassLoader loader, String className,
			Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {
		if (className == null || classfileBuffer == null) {
			return null;
		}

		final int requiredMode;
		try {
			requiredMode = AgentConfig.requiredModeFor(className);
		} catch (Throwable throwable) {
			return null;
		}

		if (requiredMode == AgentConfig.MODE_OFF) {
			return null;
		}

		try {
			return transformBytes(classfileBuffer, requiredMode);
		} catch (Throwable throwable) {
			// Never let a single hostile/odd class take down class loading.
			System.err.println("[MathMayhem/Agent] Skipped " + className + ": " + throwable);
			return null;
		}
	}

	/**
	 * Package-visible for the transformer unit test. Returns {@code null} when
	 * the class contains no target arithmetic.
	 */
	static byte[] transformBytes(byte[] original, int requiredMode) {
		ClassReader reader = new ClassReader(original);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		RewriteVisitor visitor = new RewriteVisitor(writer, requiredMode);
		reader.accept(visitor, 0);

		if (!visitor.changed) {
			return null;
		}

		synchronized (MathClassTransformer.class) {
			if (!loggedFirstClass) {
				loggedFirstClass = true;
				System.err.println("[MathMayhem/Agent] Arithmetic rewriting active (first class instrumented).");
			}
		}

		return writer.toByteArray();
	}

	private static final class RewriteVisitor extends ClassVisitor {
		private final int requiredMode;
		boolean changed;

		RewriteVisitor(ClassVisitor delegate, int requiredMode) {
			super(Opcodes.ASM9, delegate);
			this.requiredMode = requiredMode;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
			MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);

			if (delegate == null) {
				return null;
			}

			return new MethodVisitor(Opcodes.ASM9, delegate) {
				@Override
				public void visitInsn(int opcode) {
					switch (opcode) {
						case Opcodes.FADD -> replace("fadd", "(FFI)F");
						case Opcodes.DADD -> replace("dadd", "(DDI)D");
						case Opcodes.FMUL -> replace("fmul", "(FFI)F");
						case Opcodes.DMUL -> replace("dmul", "(DDI)D");
						case Opcodes.FDIV -> replace("fdiv", "(FFI)F");
						case Opcodes.DDIV -> replace("ddiv", "(DDI)D");
						default -> super.visitInsn(opcode);
					}
				}

				private void replace(String method, String descriptor) {
					changed = true;
					// Two operand slots are already on the stack; push the mode
					// tag, then consume everything through the static hook.
					super.visitInsn(requiredMode == AgentConfig.MODE_ALL
							? Opcodes.ICONST_2
							: Opcodes.ICONST_1);
					super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, method, descriptor, false);
				}
			};
		}
	}
}
