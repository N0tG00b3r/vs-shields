# VS Energy Shields — Статус проекта

## Обзор
Аддон для Valkyrien Skies 2 — система энергетических щитов для кораблей.
**ModID:** `vs_shields` | **Пакет:** `com.mechanicalskies.vsshields` | **MC:** 1.20.1 | **VS2:** 2.4.10
**Автор:** LennyPane

## Архитектура
Multiloader (Architectury): `common` (Java) + `forge` (Kotlin entry points) + `fabric` (Kotlin entry points).
Реестры через `dev.architectury.registry.registries.DeferredRegister` в common модуле.

## Выполненные этапы

### Этапы 1–6 (основной функционал) ✅
- Каркас, генераторы (3 тира), перехват урона (Forge), нетворк, рендер щита, GUI+HUD, конденсаторы, эмиттеры, рецепты, мультиблок батареи, базовый клоакинг.

### Сессия 7 — Баг-фиксы и доработки ✅
- **Краш на сервере**: `ModNetwork.initClient()` вызывался на сервере. Исправлено: клиентская логика перенесена в `VSShieldsModClient`, `ModNetwork` очищен от клиентских ссылок.
- **Cloaking Generator зарядка**: добавлен `tickCloakKineticInput` в `CreateCompat` и зарегистрирован хук.
- **Баланс батареи**: ёмкость батареи теперь зависит от количества `ShieldBatteryCellBlock` в структуре.
- **Кулдаун истощения**: при падении HP до 0 щит уходит в долгий кулдаун (5-10 сек) перед началом регенерации.
- **Пробитие щита (взрывы)**: если урон от взрыва превышает HP щита, он больше не поглощается полностью, а проходит по кораблю.
- **Границы щита**: урон блокируется только для блоков и сущностей, физически находящихся внутри эллипсоида щита.

### Сессия 8 — Стабилизация и рефакторинг ✅
- **Краш миксина маскировки**: многократные попытки починить миксин провалились. **Функция маскировки временно полностью удалена из мода** для обеспечения стабильности.
- **Проблема с уроном от ТНТ**: исправлена логика в `ShieldDamageHandler`. Теперь стандартные взрывы (ТНТ, криперы) корректно поглощаются щитом.
- **Проблема с уроном от ядерного оружия**: исправлена логика. Теперь кастомные сущности-взрывы (Alex's Caves) отслеживаются через `EntityJoinLevelEvent` и корректно уничтожаются при попадании в щит.
- **Рефакторинг размера щита**:
    - Полностью удалена концепция "радиуса" щита.
    - Щит теперь **визуально соответствует размеру корабля** (его AABB) с отступом в 5 блоков по высоте.
    - Логика урона упрощена: щит защищает **весь** корабль, без проверки границ.
- **Исправлен сетевой протокол**: устранён рассинхрон между клиентом и сервером, вызывавший вылеты игроков.

---

## Текущие параметры

### Тиры генераторов
| Тир | Max HP | Регенерация | Cooldown | Depletion Cooldown | FE ёмкость | FE/тик |
|-----|--------|-------------|----------|--------------------|------------|---------|
| Iron | 100 | 0.5/тик | 100 тиков | 200 тиков (10s) | 50,000 | 20 |
| Diamond | 250 | 1.0/тик | 60 тиков | 140 тиков (7s) | 200,000 | 50 |
| Netherite | 500 | 2.0/тик | 40 тиков | 100 тиков (5s) | 500,000 | 100 |

### Shield Battery параметры
| Параметр | Значение |
|----------|----------|
| Ёмкость FE | (500,000 / 26) * кол-во ячеек |
| Пассивное восстановление | 20% от поглощённого урона за удар |
| FE за 1 HP | 200 (hpPerFE = 0.005) |
| Порог экстренного сброса | 25% HP |
| Кулдаун экстренного сброса | 600 тиков (30 сек) |
| Буфер Input блока | 50,000 FE |

### Урон по щитам
**Взрывы** (формула `power² × 5.5`):
| Источник | Power | Урон |
|----------|-------|------|
| Крипер | 3 | 49.5 |
| TNT | 4 | 88 |
| Заряженный крипер / End Crystal | 6 | 198 |

**Снаряды** (детекция: registry namespace → class name → fallback):
| Источник | Урон |
|----------|------|
| Снежок / Яйцо / Эндерпёрл | 1 |
| Стрела | 5 |
| Спектральная стрела | 6 |
| Шалкер / Огненный шар (блейз) | 8 |
| CGS: Nail | 10 |
| CGS: Fireball / Incendiary | 15 |
| Трезубец | 15 |
| Фейерверк | 20 |
| CGS: Spear / Default CGS bullet | 20 |
| Wither skull | 25 |
| Ghast fireball | 30 |
| Дракон fireball | 40 |
| CGS: Rocket | 40 |
| CBC: Solid shot | 50 |
| CBC: HE | 60 |
| CBC: AP | 80 |
| Alex's Caves Torpedo | 80 |
| CBC: Nuke Shell | 200 |
| Alex's Caves Nuclear Bomb | 500 |
| Любой модовый (не minecraft:) | 10 |
| Неизвестный ванильный | 3 |

### Бонусы вспомогательных блоков
| Блок | Бонус |
|------|-------|
| Shield Capacitor | +50 max HP |
| Shield Emitter | +0.5 recharge/tick |

---

### Сессия 9 — Исправление краша рендер-миксина маскировки ✅

**Проблема:** `InvalidInjectionException` при старте клиента:
```
@Inject on vs_shields$onRenderShip could not find any targets matching 'renderShip'
in net.minecraft.client.renderer.LevelRenderer
```

**Корневая причина (установлена через декомпиляцию VS2 2.4.10 JAR):**
- VS2 **не инжектирует** метод `renderShip` в `LevelRenderer`. Это был неверный guess.
- `MixinLevelRenderer` в forge-пакете VS2 содержит только `dontClipTileEntities`.
- Реальный рендер кораблей — в `MixinLevelRendererVanilla` (`mod_compat.vanilla_renderer`),
  через `@WrapOperation` на `renderChunkLayer`. Каждый корабль рендерится через lambda
  `lambda$redirectRenderChunkLayer$3(PoseStack, d, d, d, LevelRenderer, RenderType, Matrix4f, ClientShip, ObjectList)`.

**Решение:**
1. Удалены оба `LevelRendererMixin.java` (forge + fabric) — файлы с неверным injection target.
2. Созданы `VS2ShipRenderMixin.java` (forge + fabric) — `@Pseudo` миксин на
   `org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer.MixinLevelRendererVanilla`,
   инжектируется в `lambda$redirectRenderChunkLayer$3` с `cancellable = true, require = 0`.
   Это единственный способ отменить рендер конкретного корабля через Mixin без VS2 cancellable API.
3. Обновлены mixin-конфиги: `LevelRendererMixin` → `VS2ShipRenderMixin` в обоих конфигах.
4. **Исправлен `ShieldRenderer.java`:** заменена сломанная reflection-based читалка `AABBic`
   на корректный вызов интерфейсных методов JOML: `shipAABB.minX()/.minY()/.minZ()/.maxX()/.maxY()/.maxZ()`.
   (`AABBic` — интерфейс, у него нет полей `minX` — только методы без `get`-префикса.)

**Исследованный VS2 Public API для рендера (для справки):**
- `VSGameEvents.INSTANCE.getRenderShip()` → `EventEmitterImpl<ShipRenderEvent>` — стреляет
  ДО рендера чанков корабля (не отменяемо, но хук для рисования своего контента поверх).
- `VSGameEvents.INSTANCE.getPostRenderShip()` → стреляет ПОСЛЕ.
- `ShipRenderEvent` содержит: `getPoseStack()`, `getShip(): ClientShip`, `getRenderType()`,
  `getCamX/Y/Z()`, `getProjectionMatrix()`.
- `VSClientGameUtils.multiplyWithShipToWorld(PoseStack, ClientShip)` — применяет
  ship→world transform к PoseStack (для standalone rendering).
- `ClientShip.getRenderTransform()` — интерполированный transform для рендера.
- `ClientShip.getRenderAABB()` — AABB в мировых координатах (не shipyard!).
- Для standalone-эффектов (вне BER) лучше использовать `VSGameEvents.getRenderShip()`.
- BER-рендер работает в shipyard-координатах — `ship.getShipAABB()` там совпадает по пространству.

**Сборка:** `BUILD SUCCESSFUL` после всех изменений.

### Сессия 10 — Рендер клоака и текстурный фикс ✅

**Проблема 1: Чёрно-фиолетовый блок (missing texture)**
- Причина: `cloaking_field_generator.json` содержал варианты `facing=north/south/east/west`, но у `CloakingFieldGeneratorBlock` нет blockstate-свойства FACING — MC не находил подходящий вариант.
- Исправление: заменено на `"": { "model": "vs_shields:block/cloaking_field_generator" }`.

**Проблема 2: Маскировка не работала (корабль виден)**
- Причина: `VS2ShipRenderMixin` имел параметр `Object chunks` в handler-методе. Реальный тип последнего параметра lambda — `ObjectList` из FastUtil, а не `java.lang.Object`. Несовпадение дескрипторов → Mixin не применял инъекцию. `require=0` скрывал ошибку.
- Исправление: убран параметр `Object chunks` (последний перед CallbackInfo). Mixin допускает отбрасывание trailing-параметров — хендлер теперь заканчивается на `ClientShip ship, CallbackInfo ci`.
- Дополнительно: убран дубликат `import LevelRenderer` в Fabric-миксине.

**Новое: Shimmer-эффект для игрока на корабле**
- Создан `CloakShimmerRenderer.java` — `BlockEntityRenderer<CloakingFieldGeneratorBlockEntity>`.
- Рендерит полупрозрачную пульсирующую сферу (бирюзовая, alpha ~0.06–0.085) вокруг AABB корабля.
- Виден только игроку, находящемуся **на** замаскированном корабле.
- Зарегистрирован в `VSShieldsModClient.initClient()` через `BlockEntityRendererRegistry`.
- Координатное пространство BER = shipyard-space, поэтому `ship.getShipAABB()` используется напрямую с поправкой `center - blockPos` (аналогично `ShieldRenderer`).

**Сборка:** `BUILD SUCCESSFUL` после всех изменений.

---

## Известные проблемы / TODO
1. **Нет Fabric damage handler** — на Fabric щит не защищает (нет Forge events, нужны миксины)
2. **CGS hitscan пули** — обычные пули CGS (из gun library ntgl) могут не файрить `ProjectileImpactEvent`, если используют hitscan. Может понадобиться подписка на кастомный ивент gun library
3. **Клоакинг lambda-target хрупкий** — `lambda$redirectRenderChunkLayer$3` — compiler-generated имя, специфичное для VS2 2.4.10. При обновлении VS2 может сломаться. `require=0` защищает от краша, но маскировка перестанет работать молча. Нужно мониторить при апдейте VS2.

## Структура файлов (ключевые изменения)

```
common/src/main/java/com/mechanicalskies/vsshields/
├── shield/
│   └── ShieldInstance.java          # Логика щита без радиуса
├── client/
│   └── ShieldRenderer.java          # Рендер по AABB корабля (AABBic.minX() etc.)
└── ...

forge/src/main/java/.../forge/mixin/
├── VS2ShipRenderMixin.java          # @Pseudo мixin → MixinLevelRendererVanilla (VS2)
└── (LevelRendererMixin.java удалён)

fabric/src/main/java/.../fabric/mixin/
├── VS2ShipRenderMixin.java          # то же, для Fabric
└── (LevelRendererMixin.java удалён)

forge/src/main/kotlin/.../forge/
├── ShieldDamageHandler.kt             # Упрощённая логика урона без координат
└── ...
```

---

## Сборка
```bash
./gradlew build --no-daemon
```
- **Forge JAR**: `forge/build/libs/vs-shields-*.jar`
- **Fabric JAR**: `fabric/build/libs/vs-shields-*.jar`
