package justfatlard.block_tip;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Which mod a thing came from.
 *
 * <p>The one question a name genuinely cannot answer. With forty mods loaded, "is that vanilla, and
 * if not, whose is it" comes up constantly and there is nowhere in the game that says.
 *
 * <p>Vanilla answers with silence. It is what everyone assumes already, so printing it under every
 * stone block would be a line that is always there and never news, which is the opposite of the
 * point: the line appears exactly when the assumption is wrong.
 */
public final class ModNames {
	private ModNames() {}

	private static final String VANILLA = "minecraft";

	/** Namespace to display name. Resolved once each: the answer cannot change while the game runs. */
	private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

	/**
	 * @param id a registry id such as {@code "better-trees-justfatlard:oak_leaf_stairs"}
	 * @return the mod's display name, or empty for vanilla and for anything unattributable
	 */
	public static String of(String id) {
		int colon = id.indexOf(':');
		if (colon <= 0) return "";

		String namespace = id.substring(0, colon);
		if (VANILLA.equals(namespace)) return "";

		return CACHE.computeIfAbsent(namespace, ModNames::lookUp);
	}

	/**
	 * Falls back to the namespace itself rather than to nothing: a block whose mod cannot be found
	 * is still worth attributing, and "cloud-kingdoms-justfatlard" says most of what the name would.
	 */
	private static String lookUp(String namespace) {
		return FabricLoader.getInstance().getModContainer(namespace)
			.map(container -> container.getMetadata().getName())
			.orElse(namespace);
	}
}
