// Portions of this code (terminal item click handling)
// are based on work by tom5454 (Tom's Simple Storage Mod),
// distributed under the MIT License. See LICENSE.toms-simple-storage for details.
package com.portableterminal.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.platform.Services;

public record TerminalSlotClickPacket(Identifier itemId, int button, boolean shift) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TerminalSlotClickPacket> TYPE =
		new CustomPacketPayload.Type<>(Identifier.tryBuild(PortableTerminalMod.MOD_ID, "terminal_slot_click"));

	public static final StreamCodec<FriendlyByteBuf, TerminalSlotClickPacket> CODEC = StreamCodec.of(
		(buf, val) -> {
			buf.writeIdentifier(val.itemId);
			buf.writeByte(val.button);
			buf.writeBoolean(val.shift);
		},
		buf -> new TerminalSlotClickPacket(
			buf.readIdentifier(),
			buf.readByte(),
			buf.readBoolean()
		)
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static final Identifier DEPOSIT_MARKER = Identifier.tryBuild("portableterminal", "deposit");

	public static TerminalSlotClickPacket deposit() {
		return new TerminalSlotClickPacket(DEPOSIT_MARKER, 0, false);
	}

	public boolean isDeposit() {
		return DEPOSIT_MARKER.equals(this.itemId);
	}

	/** Server side handling; invoked by the loader's packet bootstrap. */
	public static void handleServer(ServerPlayer player, TerminalSlotClickPacket packet) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!(menu instanceof PortableTerminalMenu psm)) return;

		ItemStack carried = menu.getCarried();

		if (packet.isDeposit()) {
			if (carried.isEmpty()) return;
			ItemStack remaining = psm.insertIntoContainers(carried.copy());
			menu.setCarried(remaining);
			menu.broadcastChanges();
			Services.PLATFORM.sendToPlayer(player, TerminalItemsSyncPacket.fromItemStacks(psm.getAggregatedItems()));
			return;
		}

		ItemStack template = null;
		for (ItemStack stack : psm.getAggregatedItems()) {
			Identifier stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (stackId != null && stackId.equals(packet.itemId())) {
				template = stack;
				break;
			}
		}
		if (template == null || template.isEmpty()) return;

		int count;
		if (packet.shift()) {
			count = Math.min(template.getMaxStackSize(), 64);
		} else {
			count = switch (packet.button()) {
				case 0 -> Math.min(template.getMaxStackSize(), 64);
				case 1 -> Math.min(32, (template.getCount() + 1) / 2);
				default -> 1;
			};
		}

		if (packet.shift()) {
			// 原版 shift 取物在背包放不下时是「什么都不发生」，而
			// Inventory.placeItemBackInInventory 会把装不下的部分丢到地上，
			// 所以先算清楚背包还能装多少，只取这么多。
			int space = freeSpaceIn(player, template);
			if (space <= 0) {
				return;
			}
			count = Math.min(count, space);
		}

		ItemStack extracted = psm.extractFromContainers(template, count);
		if (!extracted.isEmpty()) {
			if (packet.shift()) {
				ItemStack leftover = extracted.copy();
				player.getInventory().add(leftover);
				if (!leftover.isEmpty()) {
					// 兜底：放回容器，绝不丢出
					psm.insertIntoContainers(leftover);
				}
			} else {
				if (!carried.isEmpty()) {
					if (ItemStack.isSameItemSameComponents(carried, extracted)) {
						int space = carried.getMaxStackSize() - carried.getCount();
						int add = Math.min(space, extracted.getCount());
						carried.grow(add);
						extracted.shrink(add);
						if (!extracted.isEmpty()) {
							psm.insertIntoContainers(extracted);
						}
					} else {
						psm.insertIntoContainers(carried);
						menu.setCarried(extracted);
					}
				} else {
					menu.setCarried(extracted);
				}
			}
		}

		menu.broadcastChanges();
		Services.PLATFORM.sendToPlayer(player, TerminalItemsSyncPacket.fromItemStacks(psm.getAggregatedItems()));
	}

	/** 玩家背包（36 格，不含装备与副手）还能装下多少个这种物品。 */
	private static int freeSpaceIn(ServerPlayer player, ItemStack template) {
		Inventory inventory = player.getInventory();
		int space = 0;
		for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
			ItemStack inSlot = inventory.getItem(i);
			if (inSlot.isEmpty()) {
				space += template.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(inSlot, template)) {
				space += Math.max(0, inSlot.getMaxStackSize() - inSlot.getCount());
			}
		}
		return space;
	}
}
