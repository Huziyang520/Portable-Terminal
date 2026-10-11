# Portable Storage

一个基于 Fabric 的 Minecraft 模组，用于在近距离范围内扫描并统一管理附近容器中的物品，提供一个便携式终端界面，支持快速查看、搜索、存取和排序。

## 模组简介

该模组的核心功能是：

- 扫描附近的容器方块实体；
- 汇总并展示附近容器中的物品；
- 在单一界面内快速搜索和筛选；
- 支持向容器内存入和从容器中提取物品；
- 提供排序能力，方便整理库存；
- 通过标签和配置控制服务器/客户端行为。

适合用于：

- 基础生存存储整理；
- 远距离搬运前的快速归档；
- 多个箱子之间的统一管理；
- 轻量级便携式仓储体验。

## 技术栈

- Minecraft：26.2
- Java：25
- 框架：Fabric + Loom
- 依赖：Fabric Loader、Fabric API

## 项目结构

- `src/main/java/com/portablestorage`：主逻辑
- `src/client/java/com/portablestorage`：客户端逻辑
- `src/main/resources`：模组资源、语言文件、数据标签
- `build.gradle`：构建配置
- `gradle.properties`：版本与构建参数

## 主要类说明

- `PortableStorageMod`：模组初始化入口，注册网络包和全局接收器
- `PortableStorageClient`：客户端初始化，注册界面与按键
- `PortableStorageMenu`：核心菜单，负责物品聚合、排序、搜索、存取和滚动
- `ModConfig`：客户端配置保存与加载
- `OpenTerminalPacket`：打开终端的网络包
- `TerminalSlotClickPacket`：终端槽位点击的网络包
- `TerminalItemsSyncPacket`：客户端与服务端同步物品列表

## 运行方式

在项目根目录执行：

```bash
./gradlew build
```

如需直接在开发环境中运行：

```bash
./gradlew runClient
```

## 服务器控制规则

本模组支持通过数据标签控制行为，常见规则包括：

- `portable_storage_disable`：禁用模组
- `portable_storage_limit_range`：限制扫描范围
- `portable_storage_disable_deposit`：禁止存入
- `portable_storage_disable_extract`：禁止提取
- `portable_storage_only_singleplayer`：仅单人有效

这些规则可以由服务端管理员配置并控制运行行为。

## 许可证说明

本项目使用 AGPL-3.0 许可证，同时代码中保留了对 Tom's Simple Storage 的引用声明。使用时请遵守项目内 LICENSE 与相关说明。

## 项目维护建议

- 修改功能时优先保留模组的轻量化设计；
- 新增行为最好能由配置或标签控制；
- 避免把客户端和服务端逻辑绑定得过紧；
- 需要同步更新语言文件、配置说明和 README；
- 发布前确认构建、运行和核心功能已验证。

## 相关文档

- 项目管理与决策记录：`项目管理与决策记录.md`
