package com.portablestorage;

import com.portablestorage.menu.ModMenuTypes;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic entry point. Each platform module calls {@link #init()} from its own entry point.
 */
public class PortableStorageMod {
	public static final String MOD_ID = "portablestorage";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static void init() {
		ModMenuTypes.register();
	}

	public static Identifier id(String path) {
		return Identifier.tryBuild(MOD_ID, path);
	}
}
