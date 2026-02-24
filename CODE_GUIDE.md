# VS Energy Shields — Code Guide

## Архитектура

```
common/                          # Architectury — кроссплатформенный код
├── shield/                      # Серверная логика
│   ├── ShieldManager.java       # Singleton: реестр щитов (shipId → ShieldInstance)
│   ├── ShieldInstance.java       # HP, recharge, damage(), DamageListener, energyPercent
│   ├── ShieldTier.java          # Iron/Diamond/Netherite: HP, recharge, cooldowns
│   └── CloakManager.java        # Реестр замаскированных кораблей
├── shield/
│   ├── ShieldManager.java       # Singleton: реестр щитов (shipId → ShieldInstance)
│   ├── ShieldInstance.java      # HP, recharge, damage(), DamageListener, energyPercent
│   ├── ShieldTier.java          # Iron/Diamond/Netherite: HP, recharge, cooldowns
│   ├── CloakManager.java        # Реестр замаскированных кораблей
│   └── GravityFieldRegistry.java # OWNERS + ACTIVE ConcurrentHashMap, getForPlayer()
├── network/
│   ├── ModNetwork.java          # Пакеты: SHIELD_SYNC, HIT, BREAK, REGEN, NUKE_VISUAL, CLOAK, JAMMER, GRAVITY
│   ├── ClientNetworkHandler.java # S2C: щиты, звуки, cloaking, regen
│   ├── ClientShieldManager.java # Клиентский кэш щитов + world AABB
│   └── VSShieldsNetworking.java # CloakStatusPacket регистрация
├── client/
│   ├── ShieldRenderer.java      # BER: эллипсоид с honeycomb + HP-цвет + flicker
│   ├── ShieldEffectHandler.java # Hit/break particles + звуки: hit, collapse, activate, deactivate, regen
│   ├── ShieldHudOverlay.java    # HUD: HP-бар ближайшего щита
│   ├── ShieldAmbientSoundHandler.java # Ambient hum loop
│   ├── VSShieldsModClient.java  # Клиентская инициализация, пакеты S2C
│   ├── CloakRenderState.java    # Thread-local: текущий рендерящийся корабль
│   └── ClientCloakManager.java  # Клиентский кэш замаскированных кораблей
├── block/                       # Блоки: генераторы, конденсатор, эмиттер, батарея, маскировка, джаммер, гравитация
├── blockentity/                 # BE: тики, FE, Create SU, мультиблок батареи, джаммер, гравитация
├── config/ShieldConfig.java     # JSON конфиг (vs_shields.json)
├── registry/ModSounds.java      # SHIELD_HUM, HIT, COLLAPSE, ACTIVATION, DEACTIVATION, REGENERATION
└── mixin/                       # CloakChunkLayerMixin (отключена)

forge/                           # Forge-специфичный код
├── ShieldDamageHandler.kt       # ExplosionEvent.Start + ProjectileImpactEvent + EntityJoinLevel (nukes)
│                                #   + CGS namespace (cgs:/ntgl:) + cbc_nukes namespace
│                                #   + CGS hitscan: reflected GunFireEvent$Pre via IEventBus.addListener()
├── ShieldBarrierHandler.kt      # LevelTickEvent: перехват снарядов на границе щита
│                                #   Entity фильтр: Projectile ИЛИ isCbcEntity() (namespace createbigcannons/cbc/cbc_nukes)
├── ShieldEnergyCapability.kt    # IEnergyStorage для Shield Generator
├── BatteryInputEnergyCapability.kt
├── CloakEnergyCapability.kt
├── GravityFieldEnergyCapability.kt
├── GravityFieldHandler.kt       # onPlayerTick (mayfly) + onLivingFall (cancel)
├── CreateCompat.kt              # SU → FE через reflection
└── VSShieldsModForge.kt         # Forge entrypoint, регистрация EVENT_BUS
```

## Ключевые потоки данных

### Урон → Щит → Эффекты
```
Снаряд летит в мир
  ↓
ShieldBarrierHandler (каждый тик)
  ├── curPos внутри shieldAABB, prevPos снаружи → ВНЕШНИЙ БАРЬЕР
  └── curPos внутри coreAABB, prevPos внутри shieldAABB → FRIENDLY FIRE
  ↓
shield.damage(amount, tick)
  ├── damageListener?.onShieldDamaged() → ShieldBatteryController → regen 20% + sendShieldRegen()
  ↓
ModNetwork.sendShieldHit(server, shipId, interceptX/Y/Z, damage)
  ↓ (S2C пакет)
ShieldEffectHandler.onShieldHit() → ELECTRIC_SPARK + END_ROD + CRIT + FLASH + shield_hit.ogg
  ↓
Если HP ≤ 0: ModNetwork.sendShieldBreak(server, shipId)
  → ShieldEffectHandler.onShieldBreak() → 1-сек анимация разлёта осколков + shield_collapse.ogg
```

### Батарея → Регенерация → Звук
```
ShieldBatteryControllerBlockEntity.onShieldDamaged(absorbed)
  → restoreAmount = absorbed × 0.20
  → feCost = restoreAmount × 200 FE
  → energyStored -= feCost; shield.restoreHP(restoreAmount)
  → VSGameUtilsKt.getShipManagingPos() → ship.getWorldAABB() → center (cx, cy, cz)
  → ModNetwork.sendShieldRegen(server, cx, cy, cz)
  ↓ (S2C SHIELD_REGEN_ID)
ShieldEffectHandler.onShieldRegen(x, y, z) → shield_regeneration.ogg @ ship center
```

### Активация / Деактивация щита
```
ClientNetworkHandler (SHIELD_SYNC_ID handler)
  → prevActive = snapshot of csm.getAllShields() BEFORE csm.clear()
  → csm.clear(); csm.updateShield(...) × N
  → для каждого shipId: сравнить prevActive[id] vs actives[i]
     ├── false → true: ShieldEffectHandler.onShieldActivate(cx, cy, cz) → shield_activation.ogg
     └── true → false: ShieldEffectHandler.onShieldDeactivate(cx, cy, cz) → shield_deactivation.ogg
  (звук не играет при первом входе — prevActive пустой)
```

### Энергия → Щит
```
FE/SU источник → BlockEntity.tick()
  → IEnergyStorage (Forge) или CreateCompat (SU через reflection)
  → ShieldGeneratorBlockEntity: FE → energyPercent → hpScale
  → ModNetwork.sendSyncToAll() (каждые N тиков)
  → ClientShieldManager → ShieldRenderer (flicker при < 20%)
```

### Рендеринг щита
```
ShieldRenderer.render()
  → Ship.getShipAABB() → AABB в shipyard-space
  → ellipsoid (32×48 stacks/slices)
  → hexColor(phi, theta) → honeycomb per-vertex alpha modulation
     └── axial hex coordinates → edge distance → smoothstep
  → HP-based color (blue > yellow > red)
  → Energy flicker (FE < 20%): multi-frequency + dropout + color shift
```

## Сетевые пакеты

| ID | Направление | Данные | Назначение |
|---|---|---|---|
| `shield_sync` | S2C | N × (shipId, HP, maxHP, active, energyPct, worldAABB?) | Периодическая синхронизация |
| `shield_hit` | S2C | shipId, x, y, z, damage | Партиклы + звук попадания |
| `shield_break` | S2C | shipId | Анимация разрушения + звук |
| `shield_regen` | S2C | x, y, z | Звук регенерации (Battery) |
| `shield_toggle` | C2S | shipId, active | Вкл/выкл из GUI |
| `nuke_visual` | S2C | x, y, z | Alex's Caves nuke entity |
| `cloak_toggle` | C2S | shipId, active | Вкл/выкл маскировки |
| `cloak_status` | S2C | shipId, isCloaked | CloakStatusPacket |
| `jammer_reload` | C2S | BlockPos | Ручная перезарядка глушителя |
| `jammer_enable` | C2S | BlockPos, boolean | Вкл/выкл глушителя |
| `gravity_toggle` | C2S | BlockPos, boolean | Вкл/выкл гравитационного поля |
| `gravity_flight_toggle` | C2S | BlockPos, boolean | Переключить режим полёта |
| `gravity_fall_toggle` | C2S | BlockPos, boolean | Переключить защиту от падений |

## Конфигурация (`config/vs_shields.json`)

Основные секции:
- `general` — syncInterval, shieldPadding, hpScaleMin/Max
- `tiers` — HP, recharge, cooldowns для Iron/Diamond/Netherite
- `damage` — explosionPowerFactor, таблица урона по entity type (`projectiles` map), `projectileClassPatterns` map, `cbcSolidShot`, `cbcHE`, `cbcAP`, `cbcAutocannon`, `alexsCavesNukeDamage`, `moddedProjectileDefault`, `unknownProjectileDefault`
- `energy` — FE capacity/consumption per tier, Create SU ratio
- `battery` — cell capacity, passive regen rate, emergency threshold
- `cgs` — `gatlingBullet` (4), `revolverShot` (8), `flintlockBall` (15), `shotgunBurst` (16), `enableHitscan`

**merge() backfill**: `ShieldConfig.merge()` итерирует все ключи дефолтного `projectiles` и добавляет отсутствующие в загруженный конфиг. Это позволяет новым записям (cgs:*, cbc_nukes:*) появляться в существующих конфигах без ручного редактирования.

## Двойной барьер (ShieldBarrierHandler)

Два слоя защиты:
1. **shieldAABB** = worldAABB + padding → внешний барьер, останавливает входящие снаряды
2. **coreAABB** = worldAABB без padding → внутренний барьер, блокирует дружественный огонь

Детекция пересечения: `xOld/yOld/zOld` vs `x/y/z` (не entity ID tracking).
Точка перехвата: ray-AABB intersection для точного hit effect на поверхности.

**CBC Shell поддержка**: `NukeShellProjectile` и другие CBC снаряды НЕ являются подклассами `net.minecraft.world.entity.projectile.Projectile` — они наследуют `FuzedBigCannonProjectile` (CBC-класс). Поэтому `ProjectileImpactEvent` для них не срабатывает. Перехват через `isCbcEntity()`:

```kotlin
private fun isCbcEntity(entity: Entity): Boolean {
    if (entity is LivingEntity) return false
    if (entity is ItemEntity)   return false
    val namespace = ForgeRegistries.ENTITY_TYPES.getKey(entity.type)?.namespace ?: return false
    return namespace == "createbigcannons" || namespace == "cbc" || namespace == "cbc_nukes"
}
```

Урон для CBC назначается через `getProjectileDamage()` по namespace+path — autocannon, solid shot, HE, AP, nuke shell.

## Honeycomb рендеринг

Процедурный per-vertex: `(phi, theta)` → equirectangular UV → axial hex coordinates → cube rounding → edge distance → `smoothstep` → alpha/brightness modulation.

Параметры:
- `HEX_SCALE = 8` (количество ячеек)
- `EDGE_WIDTH = 0.12` (толщина линий)
- `EDGE_BRIGHTNESS = 1.8` (яркость рёбер)
- `FILL_ALPHA_MULT = 0.4` (прозрачность заливки)
- Медленное вращение: 1 оборот / 60 сек

## Пайплайн OBJ-моделей (Forge OBJ Loader)

Все кастомные 3D-блоки используют `"loader": "forge:obj"`.

### Рабочий процесс

```
Blockbench → экспорт OBJ → корень проекта (blockname.obj + .mtl + .png)
                ↓
        Python-скрипт обработки:
          1. Переименовать группы: "o cube" → "o cube_1", "o cube_2", ...
          2. +0.5 к X и Z всех вершин (Blockbench: центр в 0, Minecraft: нужен 0–1)
          3. Исправить строку mtllib → "blockname_model.mtl"
          4. Сохранить → models/block/blockname_model.obj
                ↓
        Создать blockname_model.mtl:
          newmtl <uuid_из_usemtl_в_obj>
          map_Kd vs_shields:block/blockname
                ↓
        Создать blockname.json (forge:obj, flip_v: true, правильный UUID)
                ↓
        .noOcclusion() в ModBlocks.java (если модель не полный куб)
                ↓
        Скопировать .obj/.mtl/.json в bin/main/...
```

### Шаблон JSON модели

```json
{
  "loader": "forge:obj",
  "flip_v": true,
  "model": "vs_shields:models/block/blockname_model.obj",
  "render_type": "minecraft:cutout",
  "textures": {
    "particle": "vs_shields:block/blockname",
    "<usemtl_uuid_из_obj>": "vs_shields:block/blockname"
  },
  "display": {
    "gui":                   { "rotation": [30, 225, 0], "translation": [0,0,0],   "scale": [0.625,0.625,0.625] },
    "ground":                { "rotation": [0,  0,   0], "translation": [0,3,0],   "scale": [0.25, 0.25, 0.25]  },
    "fixed":                 { "rotation": [0,  0,   0], "translation": [0,0,0],   "scale": [0.5,  0.5,  0.5]   },
    "thirdperson_righthand": { "rotation": [75, 45,  0], "translation": [0,2.5,0], "scale": [0.375,0.375,0.375] },
    "thirdperson_lefthand":  { "rotation": [75, 225, 0], "translation": [0,2.5,0], "scale": [0.375,0.375,0.375] },
    "firstperson_righthand": { "rotation": [0,  45,  0], "translation": [0,0,0],   "scale": [0.40, 0.40, 0.40]  },
    "firstperson_lefthand":  { "rotation": [0,  225, 0], "translation": [0,0,0],   "scale": [0.40, 0.40, 0.40]  }
  }
}
```

### Критичные правила

| Правило | Последствие нарушения |
|---------|----------------------|
| `"flip_v": true` | Текстуры перевёрнуты (Blockbench экспортирует V в image-space) |
| UUID в JSON = `usemtl` в OBJ | Текстура не применяется вообще |
| Группы переименованы `cube_1`... | Forge перезаписывает геометрию одноимённых групп |
| +0.5 по X/Z | Frustum culling обрезает модель |
| `.noOcclusion()` | "Дыры" в соседних блоках по краям |
| Исходник в корне проекта | Доступен для повторной обработки при обновлении текстуры |

### Восстановление после ошибок

- Оригинальные OBJ с UV-атласами — в корне проекта (`blockname.obj`)
- Резервная копия последней успешной сборки — `common/build/resources/main/assets/vs_shields/models/block/`
- **Никогда** не нормализовать UV-атлас вручную — это уничтожает UV-развёртку модели

---

## Совместимость с модами

### Create: Big Cannons (CBC)

CBC снаряды (`FuzedBigCannonProjectile`) НЕ наследуют Minecraft `Projectile` — перехватываются через `isCbcEntity()` в `ShieldBarrierHandler`. Урон по path в `getProjectileDamage()`:

| path-паттерн | Значение конфига | Дефолт |
|---|---|---|
| `autocannon` / `auto_cannon` | `cbcAutocannon` | 8 HP |
| `he` / `explosive` | `cbcHE` | 60 HP |
| `ap` / `armor_piercing` | `cbcAP` | 80 HP |
| иное (solid shot) | `cbcSolidShot` | 50 HP |
| `cbc_nukes:nuke_shell` | `projectiles["cbc_nukes:nuke_shell"]` | 200 HP |

**NukeShell flow**: снаряд перехватывается `isCbcEntity()` ещё до вызова `nukeKaboom()` → урон щиту + `discard()`. Параллельно `onEntityJoinLevel` перехватывает `alexscaves:nuclear_explosion`, если она успела заспавниться за пределами AABB.

### Create: Gunsmithing (CGS)

**Projectile entities** (cgs:/ntgl: namespace) — `ProjectileImpactEvent` + namespace-блок в `getProjectileDamage()`:

| registry path | HP |
|---|---|
| `cgs:nail` | 6 |
| `cgs:nail_steel` | 8 |
| `cgs:blaze_ball` | 8 |
| `cgs:incendiary` | 12 |
| `cgs:spear` | 20 |
| `cgs:rocket` | 40 |

**Hitscan weapons** (Gatling, Revolver, Flintlock, Shotgun) — reflected `GunFireEvent$Pre`:
- Регистрация через `tryRegisterCgsHitscanHandler(eventBus)` в `VSShieldsModForge.kt`
- `Class.forName("com.nukateam.ntgl.common.event.GunFireEvent$Pre")` — silent no-op если CGS не установлен
- `AABB.clip(eyePos, rayEnd)` возвращает `Optional.empty()` если стрелок внутри AABB → дружественный огонь разрешён автоматически

---

## Gravity Field Generator

Устройство создаёт зону гравитационного поля для игроков на корабле.

### Архитектура

```
GravityFieldGeneratorBlockEntity.tick()
  → VSGameUtilsKt.getShipManagingPos(level, pos) → ServerShip
  → GravityFieldRegistry.registerOwner(shipId, pos) → duplicate check
  → Вычисление cost = COST_BASE(100) + flight(400) + fall(100) FE/тик
  → Если energy < cost → isActive = false
  → GravityFieldRegistry.update(shipId, GravityFieldState(flightEnabled, fallProt, worldAABB, padding))
```

**`GravityFieldRegistry`** (`shield/` пакет):
- `OWNERS: ConcurrentHashMap<Long, BlockPos>` — `putIfAbsent` для атомарного определения дублей
- `ACTIVE: ConcurrentHashMap<Long, GravityFieldState>` — AABB + флаги эффектов
- `getForPlayer(Player)` — перебор `ACTIVE.values()`, проверка `state.containsPlayer(player)` (AABB + padding)
- `clear()` — вызывается при `onServerStopping` (VSShieldsModForge.kt)

**`GravityFieldHandler.kt`** (Forge, `MinecraftForge.EVENT_BUS`):
- `onPlayerTick` (Phase.END, server-side, ServerPlayer): если `!abilities.instabuild` → выставляет `mayfly` по `getForPlayer()`, вызывает `onUpdateAbilities()`
- `onLivingFall` (LivingFallEvent): если player в поле с fallProtection → `event.isCanceled = true`

**ContainerData** (8 слотов):
- 0 = isActive, 1 = isDuplicate, 2 = flightEnabled, 3 = fallProtectionEnabled
- 4-5 = energyStored (split 16-bit low/high), 6-7 = maxEnergy

### Синхронизация радиуса со щитом

Радиус поля = `ship.getWorldAABB()` + `ShieldConfig.get().getGeneral().shieldPadding` — то же самое расширение, которое использует `ShieldRenderer`. Эффект поля покрывает ровно ту же зону, что и щит.

### Сетевые пакеты (C2S)

| ID | Данные | Действие |
|---|---|---|
| `gravity_toggle` | BlockPos, boolean | вкл/выкл устройства |
| `gravity_flight_toggle` | BlockPos, boolean | переключить полёт |
| `gravity_fall_toggle` | BlockPos, boolean | переключить защиту от падения |

---

## Глушитель Щитов (Shield Jammer) - Electronic Warfare

Механика физического тарана полностью переработана в систему Радиоэлектронной Борьбы. Контроллер `ShieldJammerControllerBlockEntity` сканирует 3x3x3 структуру, которая может содержать рамки (`shield_jammer_frame`) и входы энергии (`shield_jammer_input`).

### Electronic Warfare Scanner (Интеграция с VS2)
1. **Зона действия**: Локальный центр глушителя переводится в координаты мира. 
2. **Поиск целей**: Используется `VSGameUtilsKt.getAllShips(level)`. Все корабли кроме своего сканируются на наличие активного щита через `ShieldManager`.
3. **World Space AABB Перекрытие**: Вычисляется пересечение bounding box корабля противника с зоной действия глушителя на основе преобразований матриц `getShipToWorld()`.
4. **Ультимативное Подавление**: Если найдена цель, глушитель потребляет колоссальные объёмы своей накопленной энергии, нанося прямой урон генератору вражеского щита вызовом `drainEnergyFromJammer(amount)`. В этот момент отрисовываются частицы `SONIC_BOOM`.

### Батареи и Кулдаун (Depletion & Rebooting)
Емкость глушителя динамически вычисляется по количеству рамок в 3x3x3 (до 3.9M FE). Когда энергия падает до нуля, контроллер выключается и система уходит в `REBOOTING` стейт. Входные блоки `ShieldJammerInputBlockEntity` перекрывают подачу энергии из внешних сетей только в момент *Активного* глушения, позволяя системе зарядиться с нуля во время перезагрузки. В GUI отображается точный таймер и статусы ошибок (`DUPLICATE`, `GROUNDED`).

### Disable/Enable Toggle

Поле `isEnabled` (ContainerData слот 9) полностью выключает глушитель без разрушения структуры:
- `disable()` → `isEnabled=false` + вызывает `forceCooldown()` (тот же 60-секундный кулдаун что и `Reload`)
- `enable()` → `isEnabled=true` + `setChanged()` — устройство возобновит работу после перезарядки
- В `serverTick`: если `!isEnabled` → `isActive=false; return;` (раньше любой другой логики)
- Статус в GUI: `OFFLINE (DISABLED)` отображается серым цветом, кнопки Reload/Enable-Disable активны
- NBT backward compat: `!tag.contains("IsEnabled") || tag.getBoolean("IsEnabled")` — дефолт `true`
- C2S пакет: `jammer_enable` (BlockPos, boolean enable)
