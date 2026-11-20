package com.portableterminal;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared entry point. The loader modules own everything that has to happen at a loader specific
 * moment — packet registration and menu type registration both live in their own phases.
 */
public class PortableTerminalMod {
	public static final String MOD_ID = "portableterminal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.tryBuild(MOD_ID, path);
	}
}
