<div align="center">

# Better Mine Team

<!-- 语言切换按钮 -->
[**🇨🇳 中文说明**](#mineteam-reforged) | [**🇬🇧 English**](./README.md)

</div>

---

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.20.1-green?style=flat-square&logo=minecraft)
![NeoForge Version](https://img.shields.io/badge/NeoForge-1.21.1-orange?style=flat-square&logo=neoforge)
![Forge Version](https://img.shields.io/badge/Forge-1.20.1-b45f06?style=flat-square&logo=curseforge)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](https://github.com/i113w/BetterMineTeam/blob/main/LICENSE)

> **须知**
>
> 本模组是 [MineTeam](https://github.com/XiaoHuNao/MineTeam) 的 **非官方重置版**。
>
> 本人在此由衷感谢原作者 [小胡闹(XiaoHuNao)](https://github.com/XiaoHuNao)、原贡献者 [Westernat](https://github.com/westernat) 以及原美术 [Lazy-Pillow](https://github.com/Lazy-Pillow-Minecraft) 的贡献。出于对原作者的尊重，本人已于模组开发过程中，通过向原作者小胡闹发送电子邮件和在贡献者 Westernat 的 MC 百科评论区留言的方式，说明了本模组的开发计划。虽然两人并未对此事明确表态，不过模组的开发及发布仍然完全在 **MIT 协议** 的许可范围之内。
> 
> **关于代码与素材**：本模组使用 AI 辅助开发，保留了队伍图标、PvP 图标素材和部分原模组代码。驯服生物、生物攻击逻辑等大部分代码均得到 **重写与修改**；而队伍管理面板、队友详情面板、生物 AI、生物跟随、重命名、传送功能、末影龙驯服与骑乘、相关指令及相关 GUI 素材均系 **原创**。由于代码变动巨大，本模组 **不兼容** 原模组（因此无法通过 PR 形式合并回原仓库）。
>
> **关于协议**：模组继承原模组的 **MIT 协议**，在完全保留原协议的基础上为原作者和本人添加了署名（原模组仓库的协议中作者并未署名）。协议全文 [详见此处](https://github.com/i113w/BetterMineTeam/blob/main/LICENSE)。
><div style="text-align: right;">
>i113w
></div>

## 简介

**BetterMineTeam** 旨在复刻并强化原模组 **MineTeam** 的核心体验，允许玩家选择加入 16 个不同的队伍，并能够与怪物组队一同作战。

### 核心特性

*   **驯服机制**：
    *   删除了在多模组环境下不稳定的计时器机制。
    *   玩家现在可以直接使用 **金苹果** 右键生物来驯服。
    *   添加了默认驯服材料表和丰富的 **Config 配置项**。
*   **队伍管理**：
    *   新增 **队伍管理面板** 和 **队员详情界面**（需在配置允许的距离内打开）。
    *   在详情界面中，你可以管理队员的 **物品栏、盔甲栏以及主手副手**。
    *   支持切换生物的 **跟随/待命状态**（部分修改过 Goal 的生物可能不生效）、**重命名**，或将生物 **传送 (Teleport)** 至自己的位置。
*   **即时战略指挥系统 (RTS Mode)**：
    *   新增类似 RTS 游戏的俯瞰视角指挥模式。
    *   **指挥模式**: 框选队友下达移动、攻击指令，支持 Attack Move 和智能索敌。
    *   **征召模式**: 拥有 `TeamsLord` 高级权限的玩家可强制征召野生生物加入队伍。
    *   操作方式：
        *   打开队伍管理面板 (默认 `K` 键) → 点击 "RTS Mode" 按钮进入。
        *   WASD 移动视角，鼠标滚轮缩放，Ctrl+鼠标拖动旋转。
        *   左键点击/框选单位，右键地面移动，右键敌人攻击。
        *   按 ESC 退出 RTS 模式。
    *   配置项：可调整单位移动速度、寻路失败阈值等参数。
*   **指令系统**：
    *   `/mngteam get`：返回指定玩家所属队伍及队伍成员列表。
    *   `/mngteam set`：设置队长或授予高级权限。
    *   `/mngteam menu`：快捷打开队伍管理面板（也可通过快捷键`K`键打开）。
*   **末影龙骑乘**：
    *   实现了末影龙的 **驯服与骑乘** 功能。
    *   使用 **金苹果** 右键末影龙即可驯服（需要高级权限）。
    *   空手右键即可骑乘，使用 `Space`键加速，`Shift`键减速。
*   **配置**：
    *   新增了绝大多数功能的配置项，允许整合包作者自由调整。

## 注意事项

*   **Mixin 兼容性**：本模组对 **末影龙的 AI** 进行了 Mixin 修改。虽然理论上兼容 **Savage Ender Dragon** 等同样修改末影龙的模组，但不保证 100% 的兼容性。如遇游戏崩溃，请立即在本模组的 Github 提交 Issue 并提供 `crash-report` 及 `debug.log` 文件。
*   **计分板**：本模组的队伍系统基于原版 **Scoreboard Team**。因此，在与其他大幅修改原版计分板机制的模组共存时，模组可能无法正常运行。一般来说，仅使用原版计分板实现队伍功能的模组都能正常兼容（背包界面的队伍切换按钮不能使用）。如出现相关问题请提交 Issue。

## 安装

1.  准备 **Minecraft 1.21.1** 和加载器 **NeoForge**或**Minecraft 1.20.1**和加载器**Forge**。
2.  请确保 NeoForge 版本在 **21.1.209** 及以上（Forge 最好选用**最新的Recommended Version**）。
3.  从 [Releases](https://github.com/i113w/BetterMineTeam/releases) 页面下载 `.jar` 文件。
4.  将文件放入 `.minecraft/mods` 文件夹。

## 致谢

*   **[XiaoHuNao](https://github.com/XiaoHuNao)**: 原作者。
*   **[Westernat](https://github.com/westernat)**: 原贡献者。
*   **[Lazy-Pillow](https://github.com/Lazy-Pillow-Minecraft)**: 原美术。
*   **Google Gemini**: 程序、美术。
*   **Claude**: 校对、优化。
*   **Chat GPT**: 村民交互方案提供、报错原因核对。
*   **Grok**: 村民交互方案提供、报错原因核对。
*   **豆包**: 许可协议相关建议。
*   **Qwen**: 前程序。