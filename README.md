# Portable Storage

A lightweight portable terminal that lets you browse, search and move items across every container
near you, without opening them one by one.

## What it does

- Scans the containers around you and shows everything they hold in one list.
- Searches by item name or by item ID, including tag lookups such as `#minecraft:logs`.
- Sorts by name, count, ID or mod, in either direction.
- Lets you pull items out with a click, or push them back with shift-click.
- Remembers your sort preference between sessions.

## How to use

1. Press <code>`</code> (grave accent) to open the terminal. You can rebind it under
   *Options → Controls → Key Binds → Portable Storage*.
2. Type in the search box at the top of the terminal to narrow the list.
3. Click the sort buttons on the left of the terminal to change the ordering.
4. Drag the scrollbar, or scroll with the mouse wheel over the grid, to reach further items.
5. Click an item to take a stack out of the nearby containers; shift-click to take as many as fit
   into your inventory. Shift-click a stack in your own inventory to put it back into the
   containers.

## Server rules

Server administrators can control the mod with block tags. When one of the tags below exists and
contains at least one block, the matching rule becomes active:

| Tag | Effect |
| --- | --- |
| `portablestorage:portable_storage_disable` | Disables the whole mod |
| `portablestorage:portable_storage_limit_range` | Caps the scan radius on the server side |
| `portablestorage:portable_storage_disable_deposit` | Blocks depositing items |
| `portablestorage:portable_storage_disable_extract` | Blocks taking items out |
| `portablestorage:portable_storage_only_singleplayer` | Makes the terminal singleplayer-only |

## Client config

The client keeps its settings in `config/portable_storage_client.json`:

| Key | Meaning |
| --- | --- |
| `scanRadius` | How far the terminal looks for containers, from `0` to `16` |
| `masterSwitch` | Turns the terminal off completely when `false` |
| `blacklistBlockIds` | Block IDs that the terminal ignores, for example `minecraft:furnace` |
| `sortConfig` | The sort order last chosen in the terminal |

## License and credits

Released under the GNU Affero General Public License v3.0 (AGPL-3.0); see `LICENSE`.

Parts of the terminal item aggregation, container scanning and GUI layout are based on
[Tom's Simple Storage](https://www.curseforge.com/minecraft/mc-mods/toms-storage) by tom5454,
distributed under the MIT License. See `LICENSE` for the full notice.

---

# 随身终端

一个轻量的随身终端：不用逐个打开箱子，就能浏览、搜索并搬运你附近所有容器里的物品。

## 功能

- 扫描你周围的容器，把里面的物品汇总成一份列表；
- 支持按物品名称或物品 ID 搜索，也支持 `#minecraft:logs` 这样的标签搜索；
- 支持按名称、数量、ID、模组排序，可升可降；
- 点击取出物品，Shift 点击批量取满背包；
- 记住你上次选择的排序方式。

## 使用方法

1. 按 <code>`</code>（反引号）打开终端；可在 *选项 → 控制 → 按键绑定 → 随身终端* 中改键；
2. 在终端顶部的搜索框输入关键词筛选列表；
3. 点击终端左侧的排序按钮切换排序方式；
4. 拖动滚动条，或把鼠标滚轮放在物品区上滚动，查看更后面的物品；
5. 点击物品从附近容器中取出一组，Shift 点击则尽可能多地取进背包；对背包里的物品 Shift
   点击，可把它存回附近的容器。

## 服务器控制

服务器管理员可以用方块标签控制本模组。当下列某个标签存在且至少包含一个方块时，对应规则生效：

| 标签 | 作用 |
| --- | --- |
| `portablestorage:portable_storage_disable` | 完全禁用本模组 |
| `portablestorage:portable_storage_limit_range` | 在服务端限制扫描半径 |
| `portablestorage:portable_storage_disable_deposit` | 禁止存入物品 |
| `portablestorage:portable_storage_disable_extract` | 禁止取出物品 |
| `portablestorage:portable_storage_only_singleplayer` | 仅在单人游戏可用 |

## 客户端配置

客户端配置保存在 `config/portable_storage_client.json`：

| 键 | 含义 |
| --- | --- |
| `scanRadius` | 终端搜索容器的范围，取值 `0` 到 `16` |
| `masterSwitch` | 为 `false` 时完全关闭终端 |
| `blacklistBlockIds` | 终端忽略的方块 ID，例如 `minecraft:furnace` |
| `sortConfig` | 上次在终端中选择的排序方式 |

## 许可证与致谢

本项目以 GNU Affero 通用公共许可证第 3 版（AGPL-3.0）发布，详见 `LICENSE`。

终端的物品聚合、容器扫描与界面布局部分参考了 tom5454 的
[Tom's Simple Storage](https://www.curseforge.com/minecraft/mc-mods/toms-storage)，
该项目以 MIT 许可证发布，完整声明见 `LICENSE`。
