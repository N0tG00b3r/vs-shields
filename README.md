# VS Energy Shields

Energy shield addon for [Valkyrien Skies 2](https://github.com/ValkyrienSkies/Valkyrien-Skies-2) ships.
Minecraft 1.20.1 | Forge & Fabric

## What it does

Place a Shield Generator on a VS2 ship and it wraps the entire vessel in a protective energy bubble. The shield absorbs explosions and projectiles, recharges over time, and can be toggled on/off through a GUI.

## Blocks

### Shield System
| Block | Description |
|-------|-------------|
| **Iron Shield Generator** | 100 HP, 8-block radius, 0.5 HP/tick recharge |
| **Diamond Shield Generator** | 250 HP, 16-block radius, 1.0 HP/tick recharge |
| **Netherite Shield Generator** | 500 HP, 24-block radius, 2.0 HP/tick recharge |
| **Shield Capacitor** | +50 max HP per capacitor on the same ship |
| **Shield Emitter** | +0.5 HP/tick recharge, +2 radius per emitter |

### Shield Battery (3x3x3 Multiblock)
| Block | Description |
|-------|-------------|
| **Shield Battery Controller** | Center of the multiblock, stores 500k FE, provides passive + emergency HP regen |
| **Shield Battery Cell** | Structural block, 26 needed to complete the multiblock |
| **Shield Battery Input** | Energy input — accepts FE from pipes/cables and SU from Create shafts |

### Utility
| Block | Description |
|-------|-------------|
| **Cloaking Field Generator** | Makes the ship invisible (high energy cost, no damage protection) |

## Features

- Shields block TNT explosions and projectiles (arrows, fireballs, mod weapons, etc.)
- Visual ellipsoid around the ship — color shifts from blue (healthy) to yellow to red (critical)
- HUD overlay shows shield HP when standing on a shielded ship
- GUI to monitor shield status and toggle on/off
- Recharge cooldown after taking damage (varies by tier)
- Capacitors and emitters stack — place more for stronger shields
- Shield Battery multiblock provides passive regen (20% per hit) and emergency dump (all FE to HP at <25%)
- Cloaking field generator for stealth ships
- Forge Energy (FE) powered — connect to any FE source
- Create mod integration — SU from rotation shafts converts to FE
- Nuke support — CBC Nuke Shell (200 dmg), Alex's Caves Nuclear Bomb/Explosion (500 dmg), Torpedo (80 dmg)
- Configurable via `config/vs_shields.json`

## Crafting

**Iron Shield Generator**
```
[Iron Block] [Redstone]   [Iron Block]
[Iron Block] [Glass]      [Iron Block]
[Iron Block] [Redstone]   [Iron Block]
```

**Diamond Shield Generator**
```
[Diamond Block] [Redstone Block] [Diamond Block]
[Diamond Block] [Glass]          [Diamond Block]
[Diamond Block] [Redstone Block] [Diamond Block]
```

**Netherite Shield Generator**
```
[Netherite Block] [Nether Star]    [Netherite Block]
[Netherite Block] [Diamond Block]  [Netherite Block]
[Netherite Block] [Nether Star]    [Netherite Block]
```

**Shield Capacitor**
```
[Iron Ingot]   [Redstone Block] [Iron Ingot]
[Copper Block] [Glass]          [Copper Block]
[Iron Ingot]   [Redstone Block] [Iron Ingot]
```

**Shield Emitter**
```
[Iron Ingot] [Redstone Torch] [Iron Ingot]
[Gold Ingot] [Glass]          [Gold Ingot]
[Iron Ingot] [Redstone Torch] [Iron Ingot]
```

**Shield Battery Controller**
```
[Gold Block]     [Redstone Block] [Gold Block]
[Redstone Block] [Diamond]        [Redstone Block]
[Gold Block]     [Redstone Block] [Gold Block]
```

**Shield Battery Cell** (yields 4)
```
[Iron Ingot] [Redstone]     [Iron Ingot]
[Redstone]   [Copper Block] [Redstone]
[Iron Ingot] [Redstone]     [Iron Ingot]
```

**Shield Battery Input** (yields 2)
```
[Iron Ingot] [Redstone Block] [Iron Ingot]
[Gold Ingot] [Copper Block]   [Gold Ingot]
[Iron Ingot] [Redstone Block] [Iron Ingot]
```

**Cloaking Field Generator**
```
[Obsidian]    [Ender Pearl]   [Obsidian]
[Ender Pearl] [Diamond Block] [Ender Pearl]
[Obsidian]    [Ender Pearl]   [Obsidian]
```

## Installation

1. Install Minecraft 1.20.1 with Forge 47.4+ or Fabric
2. Install [Valkyrien Skies 2](https://modrinth.com/mod/valkyrien-skies) (2.4.0+)
3. Install [Architectury API](https://modrinth.com/mod/architectury-api) (9.1.12+)
4. For Fabric: install [Fabric API](https://modrinth.com/mod/fabric-api)
5. Drop `vs-shields-<version>.jar` into your `mods/` folder

## Building from source

```bash
git clone <repo-url>
cd VS_Shields
./gradlew build --no-daemon
```

JARs will be in `forge/build/libs/` and `fabric/build/libs/`.

## Requirements

- Minecraft 1.20.1
- Valkyrien Skies 2 (2.4.0+)
- Architectury API (9.1.12+)
- Forge 47.4+ or Fabric Loader 0.14.24+

## Optional Dependencies

- **Create** — SU from rotation shafts powers shield generators and battery inputs
- **Create Big Cannons (CBC)** — custom damage values for CBC projectiles
- **Create Gunsmithing (CGS)** — custom damage values for CGS projectiles

## Author

**LennyPane**

## License

All Rights Reserved
