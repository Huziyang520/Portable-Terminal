package com.portableterminal.network;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.menu.PortableTerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 让 JEI / REI / EMI 的「+」也走服务端的配方摆放。
 *
 * <p>这些配方查看器自己搬物品是直接改客户端的槽位对象，终端的存储是服务端聚合出来的，
 * 改客户端槽位既搬不动存储里的东西，也没法按「合成取料来源」取料。所以这里只把「要摆哪份配方」
 * 送到服务端，由菜单自己的摆放逻辑执行，和配方书那条路径完全一致。</p>
 */
public record TerminalPlaceRecipePacket(Identifier recipeId, boolean maxTransfer) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<TerminalPlaceRecipePacket> TYPE =
		new CustomPacketPayload.Type<>(Identifier.tryBuild(PortableTerminalMod.MOD_ID, "place_recipe"));

	public static final StreamCodec<FriendlyByteBuf, TerminalPlaceRecipePacket> CODEC = StreamCodec.of(
		(buf, value) -> {
			buf.writeUtf(value.recipeId().toString());
			buf.writeBoolean(value.maxTransfer());
		},
		buf -> new TerminalPlaceRecipePacket(Identifier.parse(buf.readUtf()), buf.readBoolean())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handleServer(ServerPlayer player, TerminalPlaceRecipePacket packet) {
		if (!(player.containerMenu instanceof PortableTerminalMenu menu)) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, packet.recipeId());
		RecipeHolder<?> holder = level.getServer().getRecipeManager().byKey(key).orElse(null);
		if (holder == null || !(holder.value() instanceof CraftingRecipe)) {
			return;
		}
		menu.handlePlacement(packet.maxTransfer(), false, holder, level, player.getInventory());
		menu.broadcastChanges();
	}
}
