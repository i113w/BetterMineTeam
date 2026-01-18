<div align="center">

# Better Mine Team

<!-- Language Switch Buttons -->
[**🇨🇳 中文说明**](./README_CN.md) | [**🇬🇧 English**](#better-mine-team)

</div>

---

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.20.1-green?style=flat-square&logo=minecraft)
![NeoForge Version](https://img.shields.io/badge/NeoForge-1.21.1-orange?style=flat-square&logo=neoforge)
![Forge Version](https://img.shields.io/badge/Forge-1.20.1-b45f06?style=flat-square&logo=curseforge)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](https://github.com/i113w/BetterMineTeam/blob/main/LICENSE)

> **Notice**
>
> This mod is an **unofficial Reforged** version of [MineTeam](https://github.com/XiaoHuNao/MineTeam).
>
> I sincerely thank the original author [XiaoHuNao](https://github.com/XiaoHuNao), original contributor [Westernat](https://github.com/westernat), and original artist [Lazy-Pillow](https://github.com/Lazy-Pillow-Minecraft) for their contributions. Out of respect for the original author, I have communicated the development plan of this mod by sending an email to XiaoHuNao and leaving a comment in Westernat's section on MCMod. Although neither has explicitly responded, the development and release of this mod remain strictly within the scope of the **MIT License**.
> 
> **Code & Assets**: This mod uses AI assistance for development. It retains team icons, PvP icons, and some code from the original mod. However, most code regarding creature taming and attack logic has been **rewritten or modified**. The Team Management Panel, Team Member Details Interface, creature AI, follow logic, renaming, teleportation functions, Ender Dragon taming/riding, related commands, and related GUI assets are **original works**. Due to extensive code changes, it is **not compatible** with the original mod.
>
> **License**: The mod inherits the **MIT License** from the original mod. Based on retaining the original license completely, attributions for both the original author and myself have been added (the author was not attributed in the original repository's license). For the full text of the license, please [see here](https://github.com/i113w/BetterMineTeam/blob/main/LICENSE).
><div style="text-align: right;">
>i113w
></div>

## Introduction

**BetterMineTeam** retains the core functionality of the original **MineTeam**, allowing players to join 16 different teams and team up with monsters to fight together.

### Key Features

*   **Revamped Taming System**:
    *   Removed the unstable timer mechanic from the original mod.
    *   Players can now tame creatures directly by right-clicking with a **Golden Apple**.
    *   Added default taming materials and extensive **Config options**.
*   **Advanced Team Management**:
    *   Added a **Team Management Panel** and a **Member Details Interface**.
    *   The details interface allows you to manage the member's **inventory, armor slots, and main/off-hand items**.
    *   You can toggle the creature's **Follow/Stay state**, **Rename** them, or **Teleport** the creature to your position (requires being within configurable range).
*   **Command System**:
    *   `/mngteam get`: Returns the team and members of a specified player.
    *   `/mngteam set`: Sets the captain or grants advanced permissions.
    *   `/mngteam menu`: Opens the Team Management Panel.
*   **Ender Dragon Riding**:
    *   Added Ender Dragon **Taming and Riding** functionality.
    *   Tame the Ender Dragon by right-clicking with a **Golden Apple** (requires advanced permissions).
    *   Ride it by right-clicking with an empty hand. Use `Space` to accelerate and `Shift` to decelerate.
*   **Configuration**:
    *   Added CONFIG options for most features, allowing modpack authors to tweak settings freely.

## Notes

*   **Mixin Compatibility**: This mod uses Mixin to modify the **Ender Dragon's AI**. While it is theoretically compatible with mods like **Savage Ender Dragon**, 100% compatibility is not guaranteed. If the game crashes, please immediately submit an Issue on GitHub with the `crash-report` and `debug.log` files.
*   **Scoreboard Teams**: This mod relies on vanilla **Scoreboard Teams**. It may malfunction with mods that completely overhaul the scoreboard system. Mods that stick to vanilla scoreboard logic are generally compatible, though the **team switching button** in the inventory GUI will not be available. Please report any issues via the Issue tracker.

## Installation

1.  Prepare **Minecraft 1.21.1** with **NeoForge**, or **Minecraft 1.20.1** with **Forge**.
2.  Ensure the NeoForge version is **21.1.209** or higher (For Forge, the **latest Recommended Version** is suggested).
3.  Download the `.jar` file from the [Releases](https://github.com/i113w/BetterMineTeam/releases) page.
4.  Place the file into the `.minecraft/mods` folder.

## Credits

*   **[XiaoHuNao](https://github.com/XiaoHuNao)**: Original Author.
*   **[Westernat](https://github.com/westernat)**: Original Contributor.
*   **[Lazy-Pillow](https://github.com/Lazy-Pillow-Minecraft)**: Original Artist.
*   **Google Gemini**: Programming, Art.
*   **Claude**: Proofreading, Optimization.
*   **Chat GPT**: Villager interaction solutions, error cause verification.
*   **Grok**: Villager interaction solutions, error cause verification.
*   **Doubao**: License-related suggestions.
*   **Qwen**: Former Programmer.