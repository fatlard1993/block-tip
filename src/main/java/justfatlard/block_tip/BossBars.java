package justfatlard.block_tip;

import justfatlard.block_tip.api.BlockTipApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * Whether the game is already drawing a bar across the top of the screen for this creature.
 *
 * <p>It matters because that is where the card lives too. A dragon named on a card, directly on top
 * of a bar naming the dragon and drawing its health, is the same answer twice in the same place
 * from two things that have clearly never met.
 *
 * <p>Two questions, and the card needs both. Whether the bar belongs to the thing being looked at
 * decides what the card should say - repeating a health bar's own number under it is the card
 * adding nothing. Whether a bar is on screen at all decides where the card should sit, and that one
 * is true far more often: a village, a raid, a timer somebody set. A card that only checked the
 * first sat squarely across the bar every other time.
 *
 * <p>Neither is answerable in general. A boss bar is a field inside whatever owns it, with no tag
 * and no central list; only the ones made by commands can be enumerated. So mods say so themselves,
 * through {@link BlockTipApi#bossBar} and {@link BlockTipApi#bossBarCheck}.
 */
final class BossBars {
	private BossBars() {}

	/**
	 * Whether this player has any bar on screen, whatever it belongs to.
	 *
	 * <p>The ones made by {@code /bossbar} are the only ones the server can find on its own.
	 */
	static boolean anyShowing(ServerPlayer player) {
		for (CustomBossEvent event : player.level().getServer().getCustomBossEvents().getEvents()) {
			if (event.isVisible() && event.getPlayers().contains(player)) return true;
		}
		return BlockTipApi.anyBossBar(player);
	}

	static boolean over(ServerLevel level, Entity entity) {
		// A raycast at a dragon usually lands on a wing or the neck rather than the dragon, and a
		// wing is not something you can look up in the entity registry.
		Entity whole = entity instanceof EnderDragonPart part ? part.parentMob : entity;

		// The dragon's bar belongs to the fight rather than to the dragon, so one standing in an
		// overworld somebody summoned it into has no bar and wants an ordinary card.
		if (whole instanceof EnderDragon) return level.getDragonFight() != null;

		if (whole instanceof WitherBoss) return true;

		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(whole.getType());
		return id != null && BlockTipApi.drawsBossBar(id.toString());
	}
}
