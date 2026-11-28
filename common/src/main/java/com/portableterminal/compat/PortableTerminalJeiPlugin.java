package com.portableterminal.compat;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.client.gui.PortableTerminalScreen;
import com.portableterminal.config.ModConfig;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.network.TerminalPlaceRecipePacket;
import com.portableterminal.platform.ClientSender;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Optional;

/**
 * JEI 可选适配：把终端界面占用的矩形报给 JEI 让它避让，并把配方「+」接到终端的合成网格上。
 *
 * <p>{@code IGuiContainerHandler#getGuiExtraAreas} 的语义就是「JEI 必须绕开的区域」——
 * JEI 的物品列表会据此重新排布，不与我们的界面重叠。</p>
 *
 * <p>合成区不在原版工作台菜单里，JEI 自带的合成转移只认 {@code CraftingMenu}，所以这里显式登记
 * 终端的合成槽与背包槽区间；搬运逻辑仍由 JEI 自己的通用实现完成（含服务端校验与回滚）。</p>
 *
 * <p>JEI 是**可选前置**：只在编译期引用（compileOnly，绝不打包），运行期不写进任何加载器的必装
 * 依赖。JEI 不在场时这个类不会被加载：Fabric 侧靠 {@code jei_mod_plugin} 入口点（只有装了 JEI
 * 才会被查询），NeoForge/Forge 侧靠 JEI 自己扫描 {@code @JeiPlugin} 注解。</p>
 */
@JeiPlugin
public class PortableTerminalJeiPlugin implements IModPlugin {

	@Override
	public Identifier getPluginUid() {
		return Identifier.tryBuild(PortableTerminalMod.MOD_ID, "jei");
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGuiContainerHandler(PortableTerminalScreen.class,
			new IGuiContainerHandler<PortableTerminalScreen>() {
				@Override
				public List<Rect2i> getGuiExtraAreas(PortableTerminalScreen screen) {
					return screen.getExclusionAreas();
				}
			});
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		registration.addRecipeTransferHandler(new CraftingTransferHandler(), RecipeTypes.CRAFTING);
	}

	/**
	 * 终端的合成转移信息：配方槽就是 3×3 网格，来源是玩家背包（主背包 + 快捷栏）。
	 *
	 * <p>{@code canHandle} 读运行期配置：合成功能关掉时 JEI 的「+」直接不可用，
	 * 不会往看不见的格子里塞材料。</p>
	 */
	private static final class CraftingGridTransferInfo
		implements IRecipeTransferInfo<PortableTerminalMenu, RecipeHolder<CraftingRecipe>> {

		@Override
		public Class<? extends PortableTerminalMenu> getContainerClass() {
			return PortableTerminalMenu.class;
		}

		@Override
		public Optional<MenuType<PortableTerminalMenu>> getMenuType() {
			return Optional.of(ModMenuTypes.get());
		}

		@Override
		public mezz.jei.api.recipe.types.IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
			return RecipeTypes.CRAFTING;
		}

		@Override
		public boolean canHandle(PortableTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe) {
			return ModConfig.craftingEnabled;
		}

		@Override
		public List<Slot> getRecipeSlots(PortableTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe) {
			return menu.getInputGridSlots();
		}

		@Override
		public List<Slot> getInventorySlots(PortableTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe) {
			// 终端存储的槽位也算「可用材料」：JEI 按这些槽统计材料够不够，
			// 只给背包槽的话，明明终端里有材料也显示缺料。真正的搬运在 CraftingTransferHandler。
			List<Slot> slots = new java.util.ArrayList<>(menu.slots.size());
			slots.addAll(menu.getTerminalSlots());
			int end = Math.min(PortableTerminalMenu.PLAYER_INV_END, menu.slots.size());
			slots.addAll(menu.slots.subList(PortableTerminalMenu.PLAYER_INV_START, end));
			return slots;
		}
	}

	/**
	 * 「+」的实际执行：只把配方 id 发给服务端，由菜单的摆放逻辑取料。
	 *
	 * <p>JEI 自己搬物品是改客户端槽位，终端材料在服务端聚合，搬不动；而且那样也不会按
	 * 「合成取料来源」取料。走服务端就与配方书同一条路径。</p>
	 */
	private static final class CraftingTransferHandler
		implements IRecipeTransferHandler<PortableTerminalMenu, RecipeHolder<CraftingRecipe>> {

		@Override
		public Class<? extends PortableTerminalMenu> getContainerClass() {
			return PortableTerminalMenu.class;
		}

		@Override
		public Optional<MenuType<PortableTerminalMenu>> getMenuType() {
			return Optional.of(ModMenuTypes.get());
		}

		@Override
		public mezz.jei.api.recipe.types.IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
			return RecipeTypes.CRAFTING;
		}

		@Override
		public IRecipeTransferError transferRecipe(PortableTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe,
				IRecipeSlotsView recipeSlotsView, Player player, boolean maxTransfer, boolean doTransfer) {
			if (!ModConfig.craftingEnabled) {
				return null;
			}
			if (doTransfer) {
				ClientSender.sendToServer(new TerminalPlaceRecipePacket(recipe.id().identifier(), maxTransfer));
			}
			return null;
		}
	}
}
