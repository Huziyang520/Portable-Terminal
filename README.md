# 📦 Portable Terminal

A lightweight portable terminal: browse, search and move items across every container around you
without opening them one by one.

---

## ✨ What it does

- 🔭 **One list for everything** — scans the containers around you and shows their contents together.
- 🔍 **Search** — by item name, ID, mod, tag or tooltip text, with prefixes (see below).
- ↕️ **Sorting** — by name, count, ID or mod, ascending or descending.
- 🖱️ **Moving items** — click to take a stack, right-click for half (up to 32), shift-click to fill
  your inventory; shift-click your own items to store them back.
- 💾 **Remembers** — your sort order and window size are kept between sessions.

## 🎮 How to use

1. Press <code>`</code> (grave accent) to open the terminal; rebind it under
   *Options → Controls → Key Binds → Portable Terminal*.
2. Type in the search box at the top to narrow the list.
3. Use the buttons on the left to change the sort order or the number of visible rows.
4. Drag the scrollbar, or scroll over the grid, to reach further items.
5. Click an item to take it out of the nearby containers; shift-click to take as many as fit in
   your inventory. Shift-click a stack in your own inventory to put it back.

### 🔍 Search syntax

Type one or more words; every word has to match. A word with a prefix searches another field:

| Prefix | Finds items |
| --- | --- |
| `@name` | from that mod — mod ID or mod name (`@sophisticatedstorage`) |
| `#tag` | carrying that item tag, with or without a namespace (`#logs`, `#c:ores`) |
| `$text` | whose tooltip contains the text (`$fortune`, `$when in main hand`) |
| `"a b"` | whose name contains the quoted phrase, spaces included |

Without a prefix the item name and its ID are searched. Pinyin (full spelling or initials) works
when Just Enough Characters is installed.

## ⚙️ Settings

Open the config screen from the mod list. Every setting lives in one place:

| Setting | Meaning |
| --- | --- |
| 🎚️ Master switch | Turns the terminal off completely |
| 🔭 Scan radius | How far it looks for containers, `0`–`128` (default `16`) |
| 📐 Rows | How many item rows the terminal shows |
| 🚫 Ignored blocks | Block IDs the terminal skips, for example `minecraft:furnace` |
| 📥 Insert order | Which container receives items first: nearest, farthest, or by coordinates |

## 📥 Requirements

| Loader | Needed |
| --- | --- |
| Fabric | Fabric API, Forge Config API Port |
| NeoForge | — |
| Forge | Neo Configuration Screen Port |

## 🛡️ Server control

Server administrators can control the mod with block tags. A rule becomes active when the tag
exists and contains at least one block:

| Tag | Effect |
| --- | --- |
| `portableterminal:portable_terminal_disable` | Disables the whole mod |
| `portableterminal:portable_terminal_limit_range` | Caps the scan radius on the server side |
| `portableterminal:portable_terminal_disable_deposit` | Blocks depositing items |
| `portableterminal:portable_terminal_disable_extract` | Blocks taking items out |
| `portableterminal:portable_terminal_only_singleplayer` | Makes the terminal singleplayer-only |

## 📄 License and credits

Released under the GNU Affero General Public License v3.0 (AGPL-3.0); see `LICENSE`.

Parts of the terminal item aggregation, container scanning and GUI layout are based on
[Tom's Simple Storage](https://www.curseforge.com/minecraft/mc-mods/toms-storage) by tom5454,
distributed under the MIT License. See `LICENSE` for the full notice.

---

# 📦 随身终端

一个轻量的随身终端：不用逐个打开箱子，就能浏览、搜索并搬运你附近所有容器里的物品。

## ✨ 功能

- 🔭 **汇总成一份列表** —— 扫描你周围的容器，把它们装的东西合在一起显示。
- 🔍 **搜索** —— 按名称、ID、模组、标签或提示框文本查找，支持前缀（见下）。
- ↕️ **排序** —— 按名称、数量、ID 或模组，可升可降。
- 🖱️ **搬运物品** —— 左键取一组，右键取一半（最多 32 个），Shift 点击取满背包；
  对自己背包里的物品 Shift 点击即可存回附近容器。
- 💾 **记住习惯** —— 排序方式与窗口行数会保留到下次。

## 🎮 使用方法

1. 按 <code>`</code>（反引号）打开终端；可在 *选项 → 控制 → 按键绑定 → 随身终端* 中改键。
2. 在终端顶部的搜索框输入关键词筛选列表。
3. 用左侧按钮切换排序方式或显示行数。
4. 拖动滚动条，或把滚轮放在物品区上滚动，查看更后面的物品。
5. 点击物品即可从附近容器中取出；Shift 点击尽可能多地取进背包；
   对背包里的物品 Shift 点击可存回容器。

### 🔍 搜索语法

可以输入多个词，每个词都要命中。带前缀的词改为搜索对应字段：

| 前缀 | 作用 |
| --- | --- |
| `@名称` | 来自该模组的物品，模组 ID 或模组名都行（`@sophisticatedstorage`） |
| `#标签` | 带该物品标签的物品，可省略命名空间（`#logs`、`#c:ores`） |
| `$文本` | 提示框里含该文本的物品（`$fortune`、`$在主手时`） |
| `"甲 乙"` | 名称含该短语（引号内空格不拆词） |

不带前缀时按物品名称与注册 ID 匹配；装了通用拼音搜索后支持全拼与首字母。

## ⚙️ 设置

在模组列表里打开配置界面，全部设置都在一处：

| 设置 | 含义 |
| --- | --- |
| 🎚️ 总开关 | 关闭后终端完全不可用 |
| 🔭 扫描半径 | 搜索容器的范围，`0`–`128`（默认 `16`） |
| 📐 行数 | 终端一次显示几行物品 |
| 🚫 忽略的方块 | 终端跳过的方块 ID，例如 `minecraft:furnace` |
| 📥 存入顺序 | 存入时先放进哪个容器：由近到远、由远到近、按坐标 |

## 📥 前置

| 加载器 | 需要安装 |
| --- | --- |
| Fabric | Fabric API、Forge Config API Port |
| NeoForge | 无 |
| Forge | Neo Configuration Screen Port |

## 🛡️ 服务器控制

服务器管理员可以用方块标签控制本模组。当某个标签存在且至少包含一个方块时，对应规则生效：

| 标签 | 作用 |
| --- | --- |
| `portableterminal:portable_terminal_disable` | 完全禁用本模组 |
| `portableterminal:portable_terminal_limit_range` | 在服务端限制扫描半径 |
| `portableterminal:portable_terminal_disable_deposit` | 禁止存入物品 |
| `portableterminal:portable_terminal_disable_extract` | 禁止取出物品 |
| `portableterminal:portable_terminal_only_singleplayer` | 仅在单人游戏可用 |

## 📄 许可证与致谢

本项目以 GNU Affero 通用公共许可证第 3 版（AGPL-3.0）发布，详见 `LICENSE`。

终端的物品聚合、容器扫描与界面布局部分参考了 tom5454 的
[Tom's Simple Storage](https://www.curseforge.com/minecraft/mc-mods/toms-storage)，
该项目以 MIT 许可证发布，完整声明见 `LICENSE`。
