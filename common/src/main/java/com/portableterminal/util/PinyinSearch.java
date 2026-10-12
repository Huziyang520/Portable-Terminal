package com.portableterminal.util;

import java.lang.reflect.Method;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.platform.Services;

/**
 * Optional bridge to Just Enough Characters (mod id {@code jecharacters}).
 *
 * <p>JEC is a CurseForge-only mod with no Maven, so it is reached purely by reflection: no compile
 * dependency, no bundled classes, and the mod keeps working (plain substring matching) when it is
 * absent. Its public entry point is {@code me.towdium.jecharacters.utils.Match#contains(String,
 * CharSequence)}, which understands pinyin, initials and full-width input.</p>
 */
public final class PinyinSearch {

	private static final String MATCH_CLASS = "me.towdium.jecharacters.utils.Match";
	private static Method contains;

	static {
		if (Services.PLATFORM.isModLoaded("jecharacters")) {
			try {
				contains = Class.forName(MATCH_CLASS).getMethod("contains", String.class, CharSequence.class);
			} catch (Throwable t) {
				PortableTerminalMod.LOGGER.warn("Just Enough Characters is loaded but its match API was not found", t);
			}
		}
	}

	private PinyinSearch() {
	}

	/** True when {@code query} matches {@code text}, falling back to a case insensitive substring. */
	public static boolean matches(String text, String query) {
		if (text == null || query == null || query.isEmpty()) {
			return false;
		}
		if (contains != null) {
			try {
				return (Boolean) contains.invoke(null, text, query);
			} catch (Throwable t) {
				PortableTerminalMod.LOGGER.debug("JEC match failed, falling back to substring", t);
			}
		}
		return text.toLowerCase().contains(query);
	}
}
