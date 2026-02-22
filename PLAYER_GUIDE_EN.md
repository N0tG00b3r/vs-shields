# VS Energy Shields — Player Guide

## Quick Start

1.  Craft a **Shield Generator** (Iron, Diamond, or Netherite).
2.  Place it on your Valkyrien Skies 2 ship.
3.  Connect a power source (FE) — any pipes/cables from other mods, or a rotation shaft from the Create mod will work.
4.  Right-click the generator and press "Activate" in the GUI.
5.  An ellipsoidal energy shield will appear around your ship.

---

## Blocks

### Shield Generator

The core block. Creates a protective field around the ship. Three tiers are available:

| Tier | HP | Radius | Regen | FE/tick | FE Capacity |
|------|-----|--------|-------|---------|-------------|
| **Iron** | 100 | 8 blocks | 0.5/tick | 20 | 50,000 |
| **Diamond** | 250 | 16 blocks | 1.0/tick | 50 | 200,000 |
| **Netherite** | 500 | 24 blocks | 2.0/tick | 100 | 500,000 |

**Rules:**
- Only **one generator per ship**. A second one will show a red "Duplicate" message.
- The generator consumes FE every tick. Without power, the shield turns off.
- Shield HP scales with energy level (50% HP at 0% FE, 100% HP at 100% FE).
- After taking damage, recharge starts after a cooldown (5-10 seconds depending on tier).
- If the shield is fully depleted (0 HP), it enters a longer cooldown before it can start regenerating.

### Shield Capacitor

A simple addon block. Place it on the same ship to automatically add **+50 HP** to the shield's maximum health. The effect stacks: 4 capacitors = +200 HP.

### Shield Emitter

Place on the same ship to add **+0.5 HP/tick** to the recharge rate and **+2 blocks** to the shield's radius. The effect stacks.

---

## Shield Battery — Multiblock (3x3x3)

A 3x3x3 multiblock that stores a massive amount of energy and instantly restores shield HP when it takes damage.

### How to Build

```
Front face (controller side):      Middle layer:      Back layer:
 [C] [C] [C]                       [C] [C] [C]       [C] [C] [C]
 [C] [@] [C]  ← @ = Controller     [C] [C] [C]       [C] [C] [C]
 [C] [C] [C]                       [C] [C] [C]       [C] [C] [C]

[C] = Shield Battery Cell or Shield Battery Input
[@] = Shield Battery Controller (facing outward)
```

1.  Place a **Shield Battery Controller** — this is the center of one face of the 3x3x3 cube.
2.  Fill the remaining **26 positions** with **Shield Battery Cell** and/or **Shield Battery Input** blocks.
3.  The Controller must face outward. The multiblock builds BEHIND it.

### Multiblock Blocks

| Block | Description |
|-------|-------------|
| **Shield Battery Controller** | The brain. Right-click to open the GUI and see the status. |
| **Shield Battery Cell** | A structural block. **Increases the battery's capacity.** |
| **Shield Battery Input** | Accepts FE/SU. **Does not increase capacity.** |

**Tip:** Replace a few `Cell` blocks with `Input` blocks for faster charging. Each `Input` has its own internal 50k FE buffer.

### GUI Status

| Status | Meaning |
|--------|---------|
| **Incomplete** (red) | Not all 26 blocks of the structure are in place. |
| **No Shield** (yellow) | The multiblock is formed, but there is no active shield on the ship. |
| **Ready** (green) | It's working! The battery is charged and ready to restore the shield. |
| **Depleted** (red) | Out of energy. Recharge the battery. |

### How It Works

- **Passive Regen:** Every time the shield is hit, the battery instantly restores **20%** of the absorbed damage by consuming FE.
- **Emergency Dump:** If the shield's HP drops below **25%**, the battery dumps **ALL** its remaining energy into the shield for a massive, instant heal (30-second cooldown).

---

## Cloaking Field (WIP - Temporarily Disabled)

*Note: The cloaking field generator is temporarily disabled due to stability issues with other mods.*

The **Cloaking Field Generator** module hides your ship from other players.

1. Place the cloaking generator on your ship (it doesn't need physical cables, it draws power wirelessly from the main shield generator).
2. Open its GUI and activate it.
3. Your ship will become completely invisible to everyone except the owner (or players currently standing on the ship).

**Features:**
- Cloaking consumes a **massive amount of FE/tick** from the main shield generator. Without enough energy, the cloak turns off.
- The cloak only works while the main shield generator is active.

## Crafting Recipes

### Generators & Modules

**Iron Shield Generator**
```
[Iron Block] [Redstone]   [Iron Block]
[Iron Block] [Glass]      [Iron Block]
[Iron Block] [Redstone]   [Iron Block]
```

**Shield Capacitor**
```
[Iron Ingot]   [Gold Ingot]      [Iron Ingot]
[Redstone Block] [Redstone Block] [Redstone Block]
[Iron Ingot]   [Gold Ingot]      [Iron Ingot]
```

**Shield Emitter**
```
[Iron Ingot] [Redstone Torch] [Iron Ingot]
[Gold Ingot] [Glass]          [Gold Ingot]
[Iron Ingot] [Redstone Torch] [Iron Ingot]
```

### Shield Battery

**Shield Battery Cell (x4)**
```
[Iron Ingot] [Redstone] [Iron Ingot]
[Redstone]   [Glass]    [Redstone]
[Iron Ingot] [Redstone] [Iron Ingot]
```

**Shield Battery Input**
```
[Gold Ingot] [Diamond]          [Gold Ingot]
[Iron Ingot] [Battery Cell]     [Iron Ingot]
[Gold Ingot] [Diamond]          [Gold Ingot]
```

**Shield Battery Controller**
```
[Echo Shard] [Battery Input] [Echo Shard]
[Battery Input] [Netherite Ingot] [Battery Input]
[Echo Shard] [Battery Input] [Echo Shard]
```

### Cloaking

**Cloaking Field Generator**
```
[Phantom Membrane] [Echo Shard]       [Phantom Membrane]
[Netherite Ingot]  [Shield Capacitor] [Netherite Ingot]
[Phantom Membrane] [Echo Shard]       [Phantom Membrane]
```

---

## Energy

### Forge Energy (FE)

Connect any FE source to a generator or battery input:
- Thermal Expansion ducts
- Mekanism cables
- Flux Networks
- Any other mod with `IEnergyStorage`.

### Create SU (Stress Units)

Place a **Create rotation shaft** next to a generator or battery input. Rotation speed is converted to FE:
- **1 FE/tick per 1 RPM**
- A shaft at 256 RPM = 256 FE/tick
- Works from all 6 sides.

---

## Damage Mechanics

- **Projectile Barrier (Force Field):** The shield acts as a physical force field against projectiles (arrows, cannonballs). 
  - **External Threats:** Any projectile striking the shield from the outside is intercepted and destroyed at the shield's boundary.
  - **Friendly Fire Protection:** Projectiles fired from *inside* the shield can freely exit. However, if an internal projectile attempts to strike the ship's own blocks, the shield intercepts it at the hull, preventing friendly fire damage.
- **Explosions (TNT, Creepers, Nukes):** If the center of an explosion is inside an active shield, the explosion is **completely absorbed**. The shield takes damage, but all blocks and entities are protected.

---

## Visual & Audio Effects

### Shield Appearance
- **Honeycomb Pattern:** The shield renders as a sci-fi hexagonal energy grid that slowly rotates around the ship.
- **Color Scales with HP:**
  - **Blue** — HP > 50% (healthy)
  - **Yellow** — HP 25–50% (damaged)
  - **Red** — HP < 25% (critical)
- **Low Energy Distortions:** If the generator's internal energy (FE) drops below 20%, the shield will begin to violently flicker, glitch, and shift to a red-orange hue, warning you that the power supply is failing.

### Impact & Destruction
- **Hit Sparks:** Striking the shield (with a projectile or explosion) creates a burst of electrical sparks exactly at the point of impact on the shield's surface. Heavier damage causes larger bursts.
- **Shield Break:** When HP reaches 0, the shield shatters in a dramatic 1-second animation with sparks flying outward, accompanied by a loud electrical discharge sound.

### Sounds
- Active shields emit a low, vibrating **ambient hum** that can be heard up to 48 blocks away. The sound fades smoothly as you move away from the ship.

### HUD Integration
When you are near a ship with an active shield, an HP bar will appear at the top of your screen.

### Generator GUI
Right-click the generator to see:
- HP bar (color-coded)
- FE bar (orange)
- Status: Active / Inactive
- Shield radius
- Activate / Deactivate button
