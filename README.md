![Parabola](https://img.shields.io/badge/Mod-Parabola-blueviolet?style=for-the-badge)
![Version](https://img.shields.io/badge/Version-1.0.0-gold?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-brightgreen?style=for-the-badge)
![Fabric](https://img.shields.io/badge/Loader-Fabric%200.18.4-blue?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge)
![Environment](https://img.shields.io/badge/Environment-Client--Side-lightgrey?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

# Parabola

> Physics-accurate trajectory preview for every projectile — with a real-time impact scope in the corner of your screen.

---

## Features

- **Real-time arc preview** — tick-by-tick physics simulation draws a dotted trajectory arc in the world as you aim
- **Impact block highlight** — wireframe outline marks the exact block the projectile will hit
- **HUD mini-view** — semi-transparent panel in the bottom-right corner shows:
  - Distance to impact (`◎ 34.7m`)
  - 2D cross-section of blocks around the impact point
  - Block name at the impact site
  - Color-coded strip per projectile type
- **Multishot support** — three simultaneous arcs for crossbows with Multishot (center + ±10°), labeled `×3`
- **Firework rocket physics** — acceleration-based simulation with gunpowder count → lifetime calculation
- **Riptide detection** — replaces the panel with `⚡ Riptide` when conditions are met

---

## Supported Projectiles

| Projectile | Color | Notes |
|---|---|---|
| Bow arrow | 🟡 Yellow | Scales with draw progress |
| Crossbow arrow | 🟠 Orange | Requires charged crossbow |
| Crossbow firework | 🔴 Red | Acceleration-based physics |
| Trident | 🩵 Cyan | Riptide handled separately |
| Ender Pearl | 🟣 Purple | |
| Snowball / Egg | ⚪ White | |

---

## Installation

1. Install [Fabric Loader 0.18.4+](https://fabricmc.net/use/installer/) for Minecraft 26.1.2
2. Download **Fabric API 0.150.0+26.1.2** and place it in your `mods/` folder
3. Download **Cloth Config 26.1.154+fabric** and place it in your `mods/` folder
4. Download **ModMenu 18.0.0-beta.1** and place it in your `mods/` folder
5. Place `parabola-1.0.0.jar` in your `mods/` folder

---

## Configuration

Open the **ModMenu** screen → click **Parabola** → configure:

| Option | Default | Description |
|---|---|---|
| Enable Parabola | ✅ true | Master toggle |
| Show World Trajectory Arc | ✅ true | In-world dotted line & impact box |
| Show HUD Mini-view | ✅ true | Bottom-right scope panel |

Config is saved to `.minecraft/config/parabola.json`.

---

## Building from Source

Requires **JDK 25** and **Gradle 9.4.0**.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

---

## Dependencies

| Dependency | Version |
|---|---|
| Fabric API | 0.150.0+26.1.2 |
| Cloth Config | 26.1.154+fabric |
| ModMenu | 18.0.0-beta.1 |

---

## License

[MIT](LICENSE) © 2026 Beady
