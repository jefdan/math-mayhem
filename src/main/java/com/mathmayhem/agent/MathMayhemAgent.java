package com.mathmayhem.agent;

import java.lang.instrument.Instrumentation;

/**
 * The JVM agent entry point.
 *
 * <p>Add this mod jar to the JVM arguments to enable the full "all operations"
 * mode:</p>
 *
 * <pre>-javaagent:/absolute/path/to/mathmayhem-1.0.0.jar</pre>
 *
 * <p>The agent deliberately catches every throwable from installation. A
 * broken optional agent must never prevent Minecraft from starting; the mod's
 * Mixin-based worldgen fallback still works without it.</p>
 */
public final class MathMayhemAgent {
	private static boolean installed;

	private MathMayhemAgent() {
	}

	public static void premain(String args, Instrumentation instrumentation) {
		install(instrumentation);
	}

	public static void agentmain(String args, Instrumentation instrumentation) {
		install(instrumentation);
	}

	private static synchronized void install(Instrumentation instrumentation) {
		if (installed) {
			return;
		}

		try {
			if (instrumentation == null) {
				System.err.println("[MathMayhem/Agent] Instrumentation was null; worldgen fallback only.");
				System.setProperty(AgentConfig.PROP_ACTIVE, "false");
				return;
			}

			MathClassTransformer transformer = new MathClassTransformer();
			instrumentation.addTransformer(transformer, true);
			installed = true;
			System.setProperty(AgentConfig.PROP_ACTIVE, "true");
			System.err.println("[MathMayhem/Agent] Arithmetic transformer installed. "
					+ "Use -javaagent with the Math Mayhem jar; ALL mode is now available.");
		} catch (Throwable throwable) {
			System.setProperty(AgentConfig.PROP_ACTIVE, "false");
			System.err.println("[MathMayhem/Agent] Could not install arithmetic transformer. "
					+ "Minecraft will continue with the safe worldgen fallback.");
			throwable.printStackTrace(System.err);
		}
	}
}
