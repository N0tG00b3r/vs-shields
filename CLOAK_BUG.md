# Cloak Invisibility Bug — Full Context

**Статус:** Нерешено. Корабль остаётся видимым при активном Cloaking Field Generator.
**Последняя попытка:** Сессия 28. `shipsStartRendering` / `renderShip` / Flywheel-пробы — ни одна не стреляет.

---

## 1. Что работает

- `CloakingFieldGeneratorBlockEntity` потребляет FE корректно.
- `CloakedShipsRegistry` заполняется: `isCloaked(shipId) == true` при активном генераторе.
- `CloakRenderSuppressor.register()` вызывается при старте клиента (`VSShieldsModForgeClient.clientInit`).
- Kotlin `object` инициализируется при первом обращении → все `private val listener` регистрируются.
- Компиляция: `BUILD SUCCESSFUL` (после добавления `hasAnyCloakedShips()` в `CloakedShipsRegistry`).
- `MixinEmbeddingShipVisual`: применяется (поле `loggedShips` видно через reflection в `diagnoseFlywheel()`).

## 2. Что не работает

Ни одна из этих строк **никогда** не появляется в логе:

| Ожидаемая строка | Источник |
|---|---|
| `shipsStartRendering fired (count=1)` | `CloakRenderSuppressor.shipsStartListener` |
| `renderShip event: ship=...` | `CloakRenderSuppressor.preListener` |
| `shipRenderChunks field not found on ...` | `CloakRenderSuppressor.shipsStartListener` |
| `[probe] update(float) called #1` | `MixinEmbeddingShipVisual.probe_update` |
| `[probe] planFrame() called #1` | `MixinEmbeddingShipVisual.probe_planFrame` |
| `[probe] updateEmbedding() called #1` | `MixinEmbeddingShipVisual.afterUpdateEmbedding` |
| `RenderingShipVisual.update: ship=...` | `MixinRenderingShipVisual.afterUpdate` |

Первый log в `shipsStartListener` стоит **до** любых guard-условий — значит событие не эмитируется вообще, а не отфильтровывается.

---

## 3. Архитектура рендера VS2 (установлена через javap 2.4.10)

### 3.1 Глобальный рендерер (VSRenderer)

`org.valkyrienskies.mod.common.config.VSRenderer` — синглтон, определяет тип ванильного рендера:
- `VANILLA` — чистый ванильный рендер
- `OPTIFINE` — OptiFine установлен
- `SODIUM` — Sodium (или совместимый: Rubidium, Embeddium) установлен

Метод `VSRenderer.INSTANCE.getRenderer()` вызывается **при старте** и кэшируется.

### 3.2 Mixin-пакеты (ValkyrienCommonMixinConfigPlugin)

Пакеты загружаются условно:

| Пакет | Условие |
|---|---|
| `vanilla_renderer` | `VSRenderer.INSTANCE.getRenderer() == VANILLA` |
| `flywheel_renderer` | `LoadedMods.getFlywheel() == V1` |
| `flywheel` | `LoadedMods.getFlywheel() == V1` |

Это **независимые** условия. Оба могут быть активны одновременно.

### 3.3 Vanilla renderer path (MixinLevelRendererVanilla)

Файл: `org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer.MixinLevelRendererVanilla`

`@WrapOperation` на `renderChunkLayer` генерирует `redirectRenderChunkLayer`, который:
1. Вызывает `vs$addShipVisibleChunks` → заполняет `shipRenderChunks: WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>>`
2. Вызывает `op.call()` — рендерит обычные мировые чанки
3. **Эмитирует `VSGameEvents.shipsStartRendering`** (ОДИН раз на вызов `renderChunkLayer`)
4. Итерирует `shipRenderChunks.forEach(lambda)` — для каждого корабля:
   - **Эмитирует `VSGameEvents.renderShip`**
   - Рендерит чанки корабля
   - **Эмитирует `VSGameEvents.postRenderShip`**

`shipRenderChunks` — поле, инжектированное в `net.minecraft.client.renderer.LevelRenderer` миксином. Тип: `java.util.WeakHashMap`. Существует **только когда `vanilla_renderer` активен**.

### 3.4 Flywheel renderer path

Когда Flywheel V1 (Create) обнаружен:
- `MixinViewArea` (flywheel_renderer): добавляет секции чанков кораблей в `ViewArea.sectionStorage`
- `EmbeddingShipVisual` (flywheel): создаётся **только для кораблей с `ShipEffect`**
  - `ShipEffect` создаётся **только для кораблей с Create-блоками** (моторы, шестерни и т.д.)
  - `update(float)` проверяет `ship.shipRenderer == FLYWHEEL` перед созданием `RenderingShipVisual`

Дефолтное значение: `VSGameConfig.Client.defaultRenderer = VANILLA` (не FLYWHEEL).

### 3.5 Per-ship renderer

`ShipRendererKt.getShipRenderer(ship, config)` — возвращает настройку для конкретного корабля или глобальный дефолт.
По умолчанию = `ShipRenderer.VANILLA`.

---

## 4. Гипотезы (от наиболее к наименее вероятной)

### Гипотеза A: Установлен Rubidium / Embeddium / Sodium (НАИБОЛЕЕ ВЕРОЯТНО)

Rubidium и Embeddium — Forge-порты Sodium. VS2 определяет их как `VSRenderer.SODIUM`.
Следствие: `vanilla_renderer` mixin-пакет **не загружается** → `MixinLevelRendererVanilla` неактивен → `shipsStartRendering` и `renderShip` не эмитируются никогда.

Ship rendering при Sodium: Sodium использует свой pipeline (`SodiumWorldRenderer`), а не vanilla `LevelRenderer`. Чанки кораблей добавляются через `MixinViewArea`, но рендерятся Sodium-системой без VS2-событий.

**Диагностика:** Найти в логе `[vs_shields/cloak_flywheel] EmbeddingShipVisual found: ...` и рядом проверить, есть ли в логе VS2 строки про `VSRenderer` — обычно VS2 логирует свой renderer при старте.

**Или:** Добавить в `CloakRenderSuppressor.register()`:
```kotlin
try {
    val vsRenderer = Class.forName("org.valkyrienskies.mod.common.config.VSRenderer")
        .getMethod("getRenderer").invoke(
            vsRenderer.getMethod("getINSTANCE").invoke(null)
        )
    LOGGER.info("VSRenderer = {}", vsRenderer)
} catch (e: Exception) {
    LOGGER.warn("Could not read VSRenderer: {}", e.toString())
}
```

### Гипотеза B: Flywheel active, корабль без Create-блоков

Если VSRenderer == VANILLA и Create установлен:
- vanilla_renderer **и** flywheel_renderer активны одновременно
- Для кораблей **без Create-блоков**: нет ShipEffect → нет EmbeddingShipVisual → Flywheel проводок не стреляет
- Такие корабли рендерятся vanilla_renderer через `shipRenderChunks`
- `shipsStartRendering` ДОЛЖЕН стрелять

Если эта гипотеза верна и события всё равно не стреляют → что-то глубже в VS2 event system.

### Гипотеза C: VS2 event system broken / mod version mismatch

`VSGameEvents.shipsStartRendering` — `EventEmitterImpl`. Если VS2 версия не 2.4.10, или Forge-порт события не инициализирован, listeners могут быть зарегистрированы но listener-list не вызываться.

### Гипотеза D: MixinLevelRendererVanilla conflicted by another mod

Другой мод (OptiFine-совместимый или Flywheel-base мод) мог переопределить `renderChunkLayer` и устранить VS2's WrapOperation.

---

## 5. Попытки решения (история)

| Сессия | Метод | Результат |
|--------|-------|-----------|
| 9 | `VS2ShipRenderMixin` — `@Pseudo` mixin на `MixinLevelRendererVanilla.lambda$redirectRenderChunkLayer$3` | `InvalidInjectionException` при старте |
| 10 | Фикс параметра `Object chunks` в mixin (должен быть `ObjectList`) | Mixin применился, корабль всё ещё виден |
| 11 | Декомпиляция, уточнение сигнатуры lambda | Нестабильно, отключено |
| 12 | `LevelRendererCloakMixin` — reflection удаление из `shipRenderChunks` в `ShipsStartRenderingEvent` | Не помогло (события не стреляли) |
| 27 | `MixinShipEmbeddingManager` — перехват `updateAllShips()` RETURN, вызов `transforms(scale(0.0001f))` | Mixin активен (лог есть), корабль не исчезает |
| 27 | `MixinEmbeddingShipVisual` — инъекция в `updateEmbedding()` RETURN | Mixin применён, пробы НИКОГДА не стреляют |
| 27 | `MixinRenderingShipVisual` — `setVisible(false)` для instances | Пробы никогда не стреляют |
| 28 | `CloakRenderSuppressor` — listener на `shipsStartRendering` + reflection удаление из `shipRenderChunks` | `shipsStartRendering` НИКОГДА не стреляет |

---

## 6. Текущее состояние кода

### Активные классы:

- `CloakRenderSuppressor.kt` — слушатели `shipsStartRendering` (primary) + `renderShip` (fallback GL mask)
- `MixinEmbeddingShipVisual.java` — инъекция в `updateEmbedding()`, `@Shadow getShip()`
- `MixinRenderingShipVisual.java` — `setVisible(false)` через reflection на instances
- `CloakedShipsRegistry.java` — `Set<Long>` замаскированных кораблей
- `CloakShimmerRenderer.java` — shimmer-эффект для игрока на корабле (РАБОТАЕТ — использует BER, не зависит от рендер-событий)
- `LevelRendererCloakMixin.java` — УСТАРЕЛ, не в mixin-конфиге, можно удалить

### Mixin конфиг (`vs_shields.mixins.json`):
```json
"client": [
  "TacticalNetheriteHelmForgeMixin",
  "MixinEmbeddingShipVisual",
  "MixinRenderingShipVisual"
]
```

### Что отключено в игре:
- `CloakingFieldGenerator` убран из творческой вкладки и рецептов (код остаётся)

---

## 7. Следующие шаги (для будущих итераций)

### Шаг 0: Диагностика — определить VSRenderer и моды

Добавить в `CloakRenderSuppressor.register()` диагностику:
```kotlin
LOGGER.info("CloakRenderSuppressor: registering listeners")
// Читаем VSRenderer
try {
    val cls = Class.forName("org.valkyrienskies.mod.common.config.VSRenderer")
    val inst = cls.getDeclaredField("INSTANCE").also { it.isAccessible = true }.get(null)
    val renderer = inst.javaClass.getMethod("getRenderer").invoke(inst)
    LOGGER.info("VSRenderer detected = {}", renderer)
} catch (e: Exception) {
    LOGGER.warn("Could not read VSRenderer: {}", e)
}
// Читаем LoadedMods
try {
    val lm = Class.forName("org.valkyrienskies.mod.common.util.LoadedMods")
    val fw = lm.getMethod("getFlywheel").invoke(null)
    LOGGER.info("LoadedMods.flywheel = {}", fw)
} catch (e: Exception) {
    LOGGER.warn("Could not read LoadedMods: {}", e)
}
```

### Шаг 1 (если VSRenderer == SODIUM / Гипотеза A)

Sodium-путь не использует `LevelRenderer.renderChunkLayer`. Нужна одна из:

**Вариант A1: Mixin на `SodiumWorldRenderer` (Rubidium/Embeddium)**
Найти метод рендера чанков в `me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer` (или его Forge-форк `org.embeddedt.embeddium`). Инжектироваться перед рендером секции и пропускать секции замаскированных кораблей.

**Вариант A2: Mixin на `ViewArea` (VS2 flywheel_renderer)**
`MixinViewArea` добавляет корабельные секции в `sectionStorage`. Создать свой mixin на `ViewArea.setSection` или переопределить логику добавления — пропускать секции замаскированных кораблей.

**Вариант A3: Force-register ShipEffect для всех кораблей**
Если Flywheel V1 активен, принудительно создать `ShipEffect` для каждого нового корабля (даже без Create-блоков) через `VisualizationHelper.queueAdd`. Тогда `EmbeddingShipVisual` будет создан, и `MixinEmbeddingShipVisual` сработает.

**Вариант A4: Entity-based hiding**
VS2 создаёт entity для кораблей? Если да — скрыть через entity renderer. Требует изучения.

### Шаг 2 (если VSRenderer == VANILLA, Гипотеза B/C)

Если `shipsStartRendering` всё равно не стреляет при VSRenderer == VANILLA:

- Проверить, не перезаписывает ли другой мод `renderChunkLayer` так, что VS2's `@WrapOperation` теряется
- Попробовать собственный `@WrapOperation` / `@Inject` на `LevelRenderer.renderChunkLayer` — если он стреляет, значит метод вызывается, но VS2 событие не эмитируется
- Проверить версию VS2 в рантайме

### Шаг 3 (альтернативный — без событий)

Обойти всю систему событий и работать напрямую с chunk visibility:

```java
// В ServerTick или специальном handler:
// Для каждого замаскированного корабля — выгрузить его чанки на клиенте?
// Отправить PacketSetChunkCacheCenter с фиктивными координатами?
```

Очень инвазивно, потребует глубокого изучения Minecraft chunk loading протокола.

### Шаг 4 (официальный путь — спросить разработчиков VS2)

Открыть issue/discussion в репозитории VS2 с вопросом:
- Как подавить рендер конкретного корабля на клиенте?
- Есть ли официальный API для этого?
- Почему `VSGameEvents.shipsStartRendering` не стреляет в определённых конфигурациях?

---

## 8. Ключевые файлы JAR для анализа

| JAR / Класс | Путь в кеше Gradle |
|---|---|
| VS2 forge JAR | `~/.gradle/caches/modules-2/files-2.1/org.valkyrienskies/valkyrienskies-120-forge/2.4.10/*/valkyrienskies-120-forge-2.4.10.jar` |
| `MixinLevelRendererVanilla` | `org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer` |
| `ValkyrienCommonMixinConfigPlugin` | `org.valkyrienskies.core.mixin` |
| `VSGameEvents` | `org.valkyrienskies.mod.common.hooks` |
| `EmbeddingShipVisual` | `org.valkyrienskies.mod.compat.flywheel` |
| `MixinViewArea` | `org.valkyrienskies.mod.mixin.mod_compat.flywheel_renderer` |
| Flywheel 1.x JAR | `~/.gradle/caches/.../flywheel-forge-1.20.1-1.0.5.jar` |
| Rubidium JAR (если установлен) | папка mods/ сервера/клиента |

---

## 9. Версии

| Компонент | Версия           |
|---|------------------|
| Minecraft | 1.20.1           |
| VS2 | 2.4.10           |
| Forge | 47.3.0           |
| Create (Flywheel) | Create 6.0.8     |
| Rubidium/Embeddium | Embeddium 0.3.31 |
