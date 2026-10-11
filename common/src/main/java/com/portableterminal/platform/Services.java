package com.portableterminal.platform;

import java.util.ServiceLoader;

/**
 * Service loader based indirection: the shared code talks to IPlatformHelper,
 * and each loader module ships its own implementation through META-INF/services.
 */
public final class Services {

	public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

	private Services() {
	}

	public static <T> T load(Class<T> clazz) {
		return ServiceLoader.load(clazz, Services.class.getClassLoader())
				.findFirst()
				.orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
	}
}
