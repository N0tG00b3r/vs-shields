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
| Ёмкость FE | 200,000 FE (полный мультиблок из 26 Cell/Input) |
| **Пассивное поглощение** | 20% от поглощённого урона за удар, **1500 FE/HP**, выключается при HP <= 1% |
| **Экстренная регенерация** | HP < 20% → `energyStored / 250 HP` одним сбросом, кулдаун 30 сек, со звуком |
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
| CGS: Nail / Steel Nail | 6 / 8 |
| CGS: Blaze Ball | 8 |
| CGS: Incendiary | 12 |
| Трезубец | 15 |
| CGS: Hitscan — Flintlock ball | 15 |
| CGS: Hitscan — Shotgun burst | 16 |
| Фейерверк | 20 |
| CGS: Spear | 20 |
| Wither skull | 25 |
| Ghast fireball | 30 |
| Дракон fireball | 40 |
| CGS: Rocket | 40 |
| CBC: Autocannon round | 8 |
| CBC: Solid shot | 50 |
| CBC: HE | 60 |
| CBC: AP | 80 |
| Alex's Caves Torpedo | 80 |
| CBC: Nuke Shell | 200 |
| Alex's Caves Nuclear Bomb | 500 |
| CGS: Hitscan — Revolver | 8 |
| CGS: Hitscan — Gatling | 4/пуля |
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

### Сессия 11 — Исправление HUD и проблемы маскировки (декомпиляция) 🔄

**Выполнено:**
1.  **HUD Щита (Proximity Fix)**:
    -   Пакет синхронизации расширен (включает world-space AABB корабля).
    -   `ClientShieldManager` теперь хранит границы и поддерживает `containsInflated` проверку.
    -   HUD отображается игрокам в зоне действия щита (+10 блоков), даже если они не на корабле.
2.  **Визуальное выравнивание**:
    -   `ShieldRenderer` теперь использует `shieldPadding` из конфига (10 блоков) по всем осям.
3.  **Исследование маскировки (Mixin²)**:
    -   Произведена декомпиляция VS2 JAR.
    -   Установлена точная сигнатура lambda-миксина: добавлен пропущенный параметр `ObjectList<?>`.
    -   Даже с исправленной сигнатурой маскировка не заработала стабильно.
    -   **Решение**: рецепт `Cloaking Field Generator` снова заблокирован (заменен на некрафтабельный).

**Текущее состояние**:
- Защита и HUD работают корректно.

---

### Сессия 12 — Тестирование маскировки (Chunk Culling & Reflection) ❌

**Проблема:**
- После реализации метода Chunk Culling через Reflection (инъекция в словарь `shipRenderChunks`), корабли всё равно не пропадают из прямой видимости. Метод оказался нерабочим.

**Решение:**
- Разработка маскировки **снова поставлена на паузу** и перенесена в низкий приоритет до появления официального API или ответа от разработчиков Valkyrien Skies 2.
- Блок генератора маскировочного поля убран из творческой вкладки, а рецепт крафта отключен.
- Классы (`LevelRendererCloakMixin`, `CloakedShipsRegistry`, и т.д.) оставлены в кодовой базе как задел на будущее, но фактически выведены из активной игры.

---

### Сессия 13 — Механический Таран (Kinetic Ram) ✅

**Добавлена полноценная интеграция Механического Тарана как структуры.**
- **Мультиблок**: Состоит из контроллера (`KineticRamControllerBlock`) и 6 компонентов (`KineticRamPartBlock`) вокруг ядра (формируют форму "плюса"). Лимит: 1 таран на корабль.
- **Интеграция энергии**: Поддержка Forge Energy (батареи/кабеля) и Create SU (управление вращением). Буфер на 200,000 FE.
- **Новая система столкновений (Minecraft Raycasting + VS2 physics)**: 
  - Вместо коллизий AABB (возникал рассинхрон с VS2), используется нативный `level.clip` (рейкаст). Длина луча зависит от скорости корабля: `Math.max(1.5, speed * 0.1)`.
  - VS2 перехватывает этот рейкаст на уровне движка Minecraft и переводит попадание обратно в локальное пространство кораблей-целей.
- **Физика пробития**: 
  - **Заряженный удар** (`Powered Break`, >5000 FE): Трата энергии, мгновенное уничтожение чужого блока без потери прочности. Вызывает GLOW-частицы.
  - **Кинетический удар** (Без запаса энергии): Разрушение блока ценой потери -5 HP из 300 возможных у тарана. Вызывает ванильные частицы разрушения блоков.
- **Система кулдауна при критических повреждениях (Depletion)**: 
  - Когда HP опускается до 0, таран *не взрывается*. Вместо этого он моментально уничтожает (растворяет без дропа) свои 6 блоков-компонентов.
  - Контроллер отключается на 1 минуту. В графическом интерфейсе контроллера (GUI) отображается таймер "REBOOTING: Xs". Во время перезагрузки структура не формируется. После кулдауна контроллер возвращает себе 300 HP.
- **Взаимодействие со щитами**: Если таран врезается во вражеский энергетический щит:
  - Теряет 2,000 FE за удар (урон по заряду).
  - Если FE нет, таран получает -10 HP в ответ от щита.
  - В случае заряженного удара щит теряет по 50 HP генератора за каждый "тик" тарана в зоне щита.
- **UI Тарана**: Открывается по ПКМ на блок-контроллер (даже если мультиблок не сформирован). Отображает шкалу Здоровья и шкалу Заряда, а также таймер блокировки при 0 HP.

### Сессия 14 — Electronic Warfare Pivot & Iron/Diamond Generator Visual Overhaul (Forge OBJ) ✅

**Трансформация Механического Тарана в Глушитель Щитов (Shield Jammer)**
- **Мультиблок Глушителя**: Переработана концепция. Теперь это станция радиоэлектронной борьбы (Electronic Warfare). Мультиблок масштабируется и теперь включает блоки `shield_jammer_input`. Емкость энергии зависит от количества блоков каркаса (150,000 FE за блок, до 3,900,000 FE).
- **Механика Глушения**: Отбрасывает физический рейкаст. Использует сканер Valkyrien Skies 2 (`VSGameUtilsKt.getAllShips()`) для поиска вражеских щитов. При перекрытии AABB кораблей глушитель начинает активно сжигать вражеские щиты (-50,000 FE/t у врага) за счёт своей колоссальной энергии.
- **Интерфейс и Кулдауны**: Полностью переработанный GUI с таймерами (`RECHARGING`, статус `DUPLICATE`, `GROUNDED`). Жестко запрещен приём энергии (через `ShieldJammerInputBlockEntity`) во время активной фазы и фазы перезагрузки (до набора 2,500,000 FE), чтобы предотвратить "бесконечное" глушение.
- **Глобальные лимиты**: И Глушитель, и Батарея Щитов теперь имеют жесткий лимит — 1 на корабль.

**Интеграция кастомных 3D-моделей (Forge OBJ Loader Pipeline)**
- **Проблема невидимости/отсечения (Frustum Culling)**: Геометрия моделей (`iron`, `diamond`, `netherite` генераторов и `shield_capacitor`) была создана с центром в нуле, из-за чего движок обрезал их. **Решение**: Написан скрипт, прибавивший +0.5 ко всем вершинам `X` и `Z` в `.obj`.
- **Проблема краша загрузчика**: Наличие сток-строчки `mtllib` внутри `.obj` файлов вызывало жесткий сбой Forge (`NoSuchElementException`), когда материал не находился по старому пути. **Решение**: Референсы `mtllib` прописаны правильно и явно внутри `.obj` (или удалены, где не нужны).
- **Проблема отображения одной детали (Group Overlapping)**: Загрузчик Forge перезаписывал все геометрии с одинаковым именем. `Blockbench` экспортировал все детали как `o cube`. **Решение**: Скриптом все грани пронумерованы (`cube_1`, `cube_2`... `cube_n`).
- **Свойства Блока**: Добавлен атрибут `.noOcclusion()` в `ModBlocks.java` к генераторам и конденсатору, чтобы они не делали соседние блоки прозрачными (culling issue).
- **Настройка JSON и Текстур**: Текстуры и `.mtl` унифицированы под имена блоков (удалили старые заглушки и суффиксы `_plus`, очистили хвосты переименований `kinetic_ram`), включена поддержка прозрачности (`"render_type": "minecraft:cutout"`), **обязательно** поправлена развёртка текстур (`"flip_v": true`, чтобы они не были перевёрнуты вверх ногами) и прописаны глобальные `display` трансформации для инвентаря.

---

### Сессия 15 — Shield Emitter OBJ + Hexagonal Shield + Redstone + Jammer Disable/Enable ✅

**Shield Emitter OBJ pipeline:**
- Обработан триада `shield_emitter.obj/.mtl/.png` из корня проекта по шаблону Сессии 14 (группы переименованы, вершины +0.5 X/Z, `mtllib` исправлен, `flip_v: true`).
- Blockstate исправлен: добавлены `facing=up/down` (x:270/x:90) и `facing=east/west`.

**Hexagonal shield rendering (ShieldRenderer.java):**
- Исправлен алгоритм honeycomb edge-detection: заменён `sqrt(dx²+dy²)` на cube max-norm `2·max(|dq|,|dr|,|ds|)`, который равномерно = 1.0 по всей границе шестигранника.
- Исправлен баг pre-clamp EDGE_BRIGHTNESS: теперь применяется напрямую.
- Добавлен pole-fade: `finalAlpha *= 0.3 + 0.7·sin(phi)`.
- Параметры прозрачности: `EDGE_BRIGHTNESS=1.2`, `FILL_ALPHA_MULT=0.2`.

**Redstone signal от Shield Generator:**
- `ShieldGeneratorBlock` добавлен `BooleanProperty POWERED`.
- Blockstate расширен: 12 вариантов `facing×powered`.
- BE детектирует попадание по щиту через `shield.getLastHitTick()` vs `lastKnownHitTick` (без конфликта с DamageListener батареи).
- Сигнал S=15 на 20 тиков после каждого попадания. Продлевается при последовательных ударах.

**Jammer Disable/Enable toggle:**
- `ShieldJammerControllerBlockEntity`: добавлен `isEnabled` (default=true), ContainerData slot 9, `disable()` (→forceCooldown) / `enable()`.
- `ShieldJammerMenu`: `SimpleContainerData(9→10)`, добавлен `isEnabled()`.
- `ModNetwork`: добавлен `JAMMER_ENABLE_ID` C2S packet.
- `ShieldJammerScreen`: два кнопки `[Reload Jammer 76px] [Disable/Enable 76px]`. Статус OFFLINE добавлен в приоритетную цепочку.

---

### Сессия 16 — Gravity Field Generator ✅

**Новый блок: `GravityFieldGeneratorBlock`**
- Размещается на корабле VS2, 1 штука на корабль (GravityFieldRegistry отслеживает дубликаты).
- Открывает GUI по ПКМ.

**Механика:**
- Синхронизирует зону действия с AABB щита: использует `ship.getWorldAABB()` + `shieldPadding` из конфига.
- Эффекты применяются всем игрокам внутри этого расширенного AABB корабля.

**Эффекты (настраиваются в GUI):**
| Эффект | Стоимость | Описание |
|--------|-----------|----------|
| Базовый (включён) | 100 FE/тик | поддержание активности |
| Flight | +400 FE/тик | `player.abilities.mayfly=true` → творческий полёт |
| Fall Protection | +100 FE/тик | отмена `LivingFallEvent` для игроков в зоне |
| Максимум | 600 FE/тик | все опции включены |

**Энергия:** 1,000,000 FE буфер, 50,000 FE/tick вход. При нехватке FE — автоотключение.

**GUI:**
- Шкала энергии (синяя, темнеет при разряде).
- `Usage: X FE/t` — текущий расход.
- Статус: ACTIVE / OFFLINE / GROUNDED / DUPLICATE.
- Кнопки: `[Activate/Deactivate (160px)]` | `[Flight: ON/OFF] [Fall Prot: ON/OFF]`.

**Архитектура (common Java):**
- `GravityFieldRegistry.java` — ConcurrentHashMap OWNERS (duplicate detection) + ACTIVE (player lookups).
- `GravityFieldGeneratorBlockEntity.java` — tick обновляет реестр.
- `GravityFieldMenu.java` + `GravityFieldScreen.java`.

**Forge обработчики:**
- `GravityFieldHandler.kt` — `PlayerTickEvent` (flight), `LivingFallEvent` (fall cancel).
- `GravityFieldEnergyCapability.kt` — FE capability.

**Ресурсы:**
- Текстура: 16×16 PNG — тёмно-синяя, три концентрических бирюзовых/голубых кольца, яркое ядро.
- Рецепт: `[Iron Block][Ender Pearl][Iron Block] / [Ender Pearl][Diamond Block][Ender Pearl] / [Iron Block][Ender Pearl][Iron Block]`.
- Добавлен в CreativeTab.

---

---

### Сессия 17 — Фикс текстур OBJ-блоков и обработка новых моделей ✅

**Исправлены проблемы отображения текстур у 7 блоков:**

**Корневая причина**: блоки с `forge:obj` loader требуют `"flip_v": true` — без него Blockbench-текстуры отображаются перевёрнутыми. Также в `shield_jammer_frame.json` был неверный UUID материала (`m_46abdeac` от gravity_generator вместо `m_5b1efc91`).

**Обработаны новые OBJ из корня проекта** по пайплайну Сессии 14:
- `gravity_field_generator.obj` → `gravity_field_generator_model.obj` (группы переименованы, +0.5 X/Z, mtllib исправлен)
- `shield_jammer_frame.obj` → `shield_jammer_frame_model.obj`
- `shield_jammer_controller.obj` → `shield_jammer_controller_model.obj` (переведён с `orientable` на `forge:obj`)
- `shield_jammer_input.obj` → `shield_jammer_input_model.obj` (переведён с `orientable` на `forge:obj`)

**Добавлен `"flip_v": true`** во все JSON-модели OBJ-блоков:
`shield_battery_cell`, `shield_battery_input`, `shield_battery_controller`, `shield_jammer_frame`, `shield_jammer_controller`, `shield_jammer_input`, `gravity_field_generator`

**Исправлен occlusion culling**: добавлен `.noOcclusion()` для `shield_jammer_controller` в `ModBlocks.java` — устранены "дыры" в соседних блоках по краям модели.

**Пайплайн OBJ задокументирован** в `CLAUDE.md`, `CODE_GUIDE.md` и `PROJECT_STATUS.md`.

---

### Сессия 18 — Звуки щита ✅

Добавлены 5 звуковых эффектов. OGG-файлы скопированы из корня проекта в `common/src/main/resources/assets/vs_shields/sounds/`.

| Событие | Файл | Триггер |
|---------|------|---------|
| `shield_hit` | `1hit.ogg` | При попадании снаряда / взрыва в щит |
| `shield_collapse` | `2collapse.ogg` | При разрушении щита (HP → 0) |
| `shield_activation` | `3activation.ogg` | При включении щита через GUI |
| `shield_deactivation` | `3deactivation.ogg` | При выключении щита через GUI |
| `shield_regeneration` | `4regeneration.ogg` | При пассивной регенерации от Shield Battery |

**Изменённые файлы:**
- `sounds.json` — 5 новых записей
- `registry/ModSounds.java` — 5 новых `RegistrySupplier<SoundEvent>`
- `network/ModNetwork.java` — добавлен `SHIELD_REGEN_ID` (S2C) + `sendShieldRegen()`
- `client/ShieldEffectHandler.java` — sound calls в `onShieldHit/onShieldBreak`; новые методы `onShieldActivate/Deactivate/Regen`
- `network/ClientNetworkHandler.java` — детекция переключения `active` в SHIELD_SYNC; приёмник `SHIELD_REGEN_ID`
- `blockentity/ShieldBatteryControllerBlockEntity.java` — реализована логика в `onShieldDamaged`: 20% absorbed → `restoreHP()`, FE cost 200/HP, отправка `SHIELD_REGEN_ID`

---

### Сессия 19 — Переработка логики Shield Battery + фикс звука активации ✅

**Фикс звука активации щита:**
- **Баг:** `ClientNetworkHandler` читал предыдущее состояние `active` из `ClientShieldManager`, который не хранит inactive-щиты (при `active=false, existing=null` запись не создаётся). Переход `false→true` не детектировался.
- **Исправление:** Добавлен статический `lastKnownActiveState: ConcurrentHashMap<Long, Boolean>` в `ClientNetworkHandler` — персистентный map, который не зависит от `csm.clear()` и хранит последнее известное состояние каждого щита включая `false`. Обновляется после каждого синка, удаляет корабли через `retainAll`.

**Переработка Shield Battery — два режима:**

| Режим | Триггер | Звук | Стоимость |
|-------|---------|------|-----------|
| Пассивное поглощение | Каждый удар (`onShieldDamaged`) | ❌ нет | 500 FE/HP, восстанавливает 20% урона |
| Экстренная регенерация | HP < 20% в `serverTick` | ✅ `shield_regeneration` | 250 FE/HP, весь доступный заряд за раз |

- **Экстренная формула:** `HP = energyStored / 250`, ограничено дефицитом HP. Кулдаун 600 тиков (30 сек) — не застревает в цикле при 0 HP.
- Предыдущий код `onShieldDamaged` (200 FE/HP + звук на каждый удар) заменён.

**Изменённые файлы:**
- `blockentity/ShieldBatteryControllerBlockEntity.java` — новые константы `FE_PER_HP_PASSIVE=500`, `FE_PER_HP_EMERGENCY=250`, `EMERGENCY_HP_THRESHOLD=0.20`, `EMERGENCY_COOLDOWN_TICKS=600`; `onShieldDamaged` — только поглощение без звука; emergency regen перенесён в `serverTick`
- `network/ClientNetworkHandler.java` — `lastKnownActiveState` вместо `prevActive` из csm

---

### Сессия 20 — Баланс Батареи Щита и Фиксы Добычи ✅

**Баланс Батареи Щита:**
- Снижена базовая ёмкость контроллера с 500,000 FE до 200,000 FE.
- Увеличена стоимость пассивного поглощения (20% reduction) с 500 FE до 1500 FE за 1 HP.
- Отключено пассивное поглощение, когда здоровье щита падает до 1% и ниже, чтобы позволить щиту разрушиться (предотвращает застревание на 0.01 HP при постоянном огне).

**Интеграция с модом Create:**
- Добавлен хук `tickGravityFieldInput` в `CreateCompat.kt`.
- Теперь **Gravity Field Generator** может напрямую конвертировать `SU` (Stress Units) от соседних валов и моторов в `FE` (1 RPM = 1 FE/t), аналогично генераторам щита.

**Исправление лут-таблиц (Block Drops):**
- Обнаружено отсутствие JSON-файлов лут-таблиц (loot tables) у нескольких блоков, из-за чего они исчезали при разрушении.
- Созданы недостающие таблицы для: `gravity_field_generator`, `shield_jammer_controller`, `shield_jammer_frame`, `shield_jammer_input`. Были добавлены проверки `minecraft:survives_explosion`.

**Исправление требований к добыче (Mineability Tags):**
- Генератор гравитации ошибочно мог разрушаться железной киркой.
- Блок удалён из `needs_iron_tool.json` и добавлен в новый тег-файл `needs_diamond_tool.json`, сохраняя прикрепление к общим инструментам `mineable/pickaxe`.

---

### Сессия 21 — Create Gunsmithing + CBC fix ✅

**Защита от Create: Gunsmithing (Options B + C):**

| Оружие | Тип | Метод перехвата | Урон по щиту |
|--------|-----|----------------|-------------|
| Nailgun | Projectile entity | `ProjectileImpactEvent` | 6–8 HP |
| Launcher (ракеты) | Projectile entity | `ProjectileImpactEvent` | 40 HP |
| Blazegun / Incendiary | Projectile entity | `ProjectileImpactEvent` | 8–12 HP |
| Gatling | Hitscan | `GunFireEvent$Pre` (reflection) | 4 HP/пуля |
| Revolver | Hitscan | `GunFireEvent$Pre` (reflection) | 8 HP |
| Flintlock | Hitscan | `GunFireEvent$Pre` (reflection) | 15 HP |
| Shotgun | Hitscan | `GunFireEvent$Pre` (reflection) | 16 HP (весь залп) |

Hitscan-обработчик регистрируется через Forge `IEventBus.addListener()` с рефлексией — NTGL не является compile-time dependency. Если CGS/NTGL не установлен — тихий no-op. Стреляющий внутри щита не блокируется (AABB.clip() возвращает empty когда eyePos внутри).

**Исправлена детекция CBC Nuke Shell (`cbc_nukes`):**

Корневая причина: `NukeShellProjectile extends FuzedBigCannonProjectile` (CBC) — **не** является subclass `net.minecraft.world.entity.projectile.Projectile`. Поэтому:
- `ShieldBarrierHandler` (`it is Projectile`) → НЕ ловил снаряд
- Forge `ProjectileImpactEvent` → НЕ стрелял для CBC снарядов
- Снаряд долетал до корабля, `nukeKaboom()` спавнил `alexscaves:nuclear_explosion` где попало → `onEntityJoinLevel` не находил корабль если взрыв снаружи AABB

**Исправление:** `ShieldBarrierHandler` теперь также перехватывает entity с namespace `createbigcannons`, `cbc`, `cbc_nukes` через новый `isCbcEntity()` хелпер:
```kotlin
entity is Projectile || isCbcEntity(entity)
// isCbcEntity: namespace == "createbigcannons" || "cbc" || "cbc_nukes", не LivingEntity, не ItemEntity
```

**CBC Autocannon support:** Добавлен паттерн `path.contains("autocannon")` → `cbcAutocannon = 8.0 HP`. Весь CBC (включая автопушку) теперь полностью блокируется.

**Исправлен merge() конфига:** Теперь при загрузке существующего `vs_shields.json` новые записи в `projectiles` map (например, `cgs:nail`, `cbc_nukes:nuke_shell`) автоматически дополняются в существующий файл — ранее они добавлялись только в новый файл.

**Изменённые файлы:**
- `forge/ShieldBarrierHandler.kt` — расширен фильтр entity на CBC namespace, переменная `proj: Projectile` → `entity: Entity`, метод `interceptProjectile` → `interceptEntity`
- `forge/ShieldDamageHandler.kt` — добавлены блоки `cbc_nukes` namespace и `autocannon` path
- `common/config/ShieldConfig.java` — `cbcAutocannon = 8.0`, `CgsConfig` с per-weapon hitscan damage, merge()-fix для projectiles map, `cgs:*` entries в defaults

---

### Сессия 22 — Полное покрытие CBC + Nuke Visual на щите ✅

**Полный реестр CBC снарядов (CBCEntityTypes):**

Извлечён из JAR (`javap -verbose CBCEntityTypes.class`). Добавлены явные записи в `ShieldConfig.createDefault()` для снарядов, которые не матчились path-паттернами:

| registry name | Урон | Причина явной записи |
|---|---|---|
| `createbigcannons:shrapnel_shell` | 55 HP | нет "he"/"ap" в path → раньше solid shot |
| `createbigcannons:fluid_shell` | 55 HP | нет "he"/"ap" в path → раньше solid shot |
| `createbigcannons:bag_of_grapeshot` | 30 HP | нет паттерна → раньше solid shot |
| `createbigcannons:drop_mortar_shell` | 40 HP | нет паттерна → раньше solid shot |
| `createbigcannons:mortar_stone` | 20 HP | нет паттерна → раньше solid shot |
| `createbigcannons:smoke_shell` | 10 HP | нет паттерна → раньше solid shot |
| `createbigcannons:machine_gun_bullet` | 8 HP | "machine_gun" не содержит "autocannon" → **50 HP** раньше |

**machine_gun_bullet исправление:** В `getProjectileDamage()` CBC-блок добавлен `path.contains("machine_gun")` рядом с "autocannon" — пуля пулемёта теперь 8 HP (cbcAutocannon) вместо 50 HP (solid shot).

**Nuke Shell → nuclear visual:**

`interceptEntity()` в `ShieldBarrierHandler.kt` теперь вызывает `ModNetwork.sendNukeVisual()` при перехвате снаряда из namespace `cbc_nukes` — полноценная ядерная вспышка прямо на поверхности щита, идентично поведению при блокировке `alexscaves:nuclear_explosion`.

**Урон Nuke Shell = Nuclear Bomb (500 HP):**

- `cbc_nukes:nuke_shell` переведён с 200 → **500 HP** (= `alexsCavesNukeDamage`)
- Fallback-ветка `path.contains("nuke")` в `ShieldDamageHandler` теперь ссылается на `cfg.damage.alexsCavesNukeDamage` — оба пути синхронизированы через один параметр конфига

**Изменённые файлы:**
- `forge/ShieldBarrierHandler.kt` — `sendNukeVisual` в `interceptEntity()` для cbc_nukes
- `forge/ShieldDamageHandler.kt` — `machine_gun` в autocannon-check, nuke fallback → `alexsCavesNukeDamage`
- `common/config/ShieldConfig.java` — 7 новых CBC записей + `cbc_nukes:nuke_shell` = 500, `BonusConfig.emitterRegenCost`
- `CODE_GUIDE.md` — секции CBC/CGS совместимости, двойной барьер, конфиг cgs
- `PLAYER_GUIDE_EN.md` / `PLAYER_GUIDE_RU.md` — полная таблица CBC снарядов

---

### Сессия 23 — Инструменты, Баланс Гравитационных Мин и Документация ✅
- **Ship Analyzer & Tactical Helm**: Реализована система сканирования кораблей и HUD-подсветки через шлем. Игроки и генераторы подсвечиваются (glowing) через блоки.
- **Баланс Гравитационной Мины**:
    - Добавлена фаза взведения (`Phase.PRE_ARMED`) на 3 секунды с нарастающим звуковым сигналом.
    - Реализовано правило "Break on Impact": мина, попавшая в корабль в фазе полета, разрушается без взрыва (защита от тактики "ракет").
    - Добавлена перезарядка 5 секунд (100 тиков) для лаунчера гравитационных мин.
- **Усиление физики**: Увеличен множитель силы взрыва (5x) и эффект рычага (torqe leverage 40x), что вызывает сильную дестабилизацию корабля при детонации Armed мины.
- **Синхронизация Entity**: Исправлен визуальный рассинхрон мин (spawn packet) и масштаб лаунчера в инвентаре (0.35x).
- **Документация**: Обновлены гайды `PLAYER_GUIDE_EN.md` и `PLAYER_GUIDE_RU.md`, добавлены описания новых инструментов и полные рецепты крафта.

## Известные проблемы / TODO
1. **Нет Fabric damage handler** — на Fabric щит не защищает (нет Forge events, нужны миксины).
2. **Остаточный аутлайн от анализатора** — при использовании Ship Analyzer игрок иногда продолжает подсвечиваться собственным сканером (residual glowing effect).
3. **Планируемые звуки (TODO):**
    - Звук выстрела лаунчера (тяжёлый пневматический выброс).
    - Звук полёта мины (низкочастотный гравитационный гул).
    - Звук детонации (мощный схлопывающийся гравитационный взрыв, а не обычный TNT).
    - Звук сканирования Ship Analyzer (электронный "ping").
**Реализовано (ранее было в TODO):**
- ✅ Redstone signal при получении урона щитом — реализовано в Сессии 15 (`ShieldGeneratorBlock` выдаёт redstone сигнал при ударе через `BlockState` компаратор)

## Структура файлов (ключевые изменения)

```
common/src/main/java/com/mechanicalskies/vsshields/
├── shield/
│   └── ShieldInstance.java          # Логика щита без радиуса
├── client/
│   ├── ShieldRenderer.java          # Рендер по AABB корабля
│   ├── CloakShimmerRenderer.java    # Эффект мерцания внутри маскировки
│   └── CloakedShipsRegistry.java    # Менеджер списка скрытых кораблей
└── mixin/
    └── LevelRendererCloakMixin.java # Chunk Culling миксин (Priority 2000, Reflection)

forge/src/main/kotlin/.../forge/
├── ShieldDamageHandler.kt             # Упрощённая логика урона без координат
└── ...
```

## Сборка
```bash
./gradlew build --no-daemon
```
- **Forge JAR**: `forge/build/libs/vs-shields-*.jar`
- **Fabric JAR**: `fabric/build/libs/vs-shields-*.jar`
