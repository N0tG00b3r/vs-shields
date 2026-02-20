    # Claude Code Prompt — VS Energy Shields Addon

## Скопируй всё ниже и вставь в Claude Code в терминале IntelliJ

---

```
Ты — опытный Minecraft Forge мод-разработчик. Мы работаем в проекте на базе Valkyrien Skies Addon Template (VS 2.4+, Forge 1.20.1).

## КОНТЕКСТ ПРОЕКТА

Проект уже настроен: Addon Template склонирован, Gradle синхронизирован, VS2 зависимости подтянуты. Minecraft Development plugin установлен в IntelliJ.

Прежде чем писать код — изучи структуру проекта:
1. Выполни `find . -type f -name "*.java" -o -name "*.kt" -o -name "*.json" -o -name "*.toml" | head -80` чтобы понять текущую структуру
2. Прочитай `build.gradle` (или `build.gradle.kts`) чтобы понять зависимости и версии VS2/VSCore
3. Прочитай `gradle.properties` для версий
4. Прочитай `mods.toml` или `fabric.mod.json` для метаданных мода
5. Найди главный @Mod класс и пойми naming convention проекта

## ЧТО ДЕЛАЕМ

Создаём аддон "VS Energy Shields" (modid: `vsshields`) — систему энергетических щитов для VS2 кораблей.

## АРХИТЕКТУРА

### Блоки:
1. **ShieldGeneratorBlock** — основной блок с BlockEntity. Имеет 3 тира (iron/diamond/netherite). При размещении на VS-корабле создаёт защитный щит вокруг него. Имеет GUI.
2. **ShieldCapacitorBlock** — увеличивает макс. HP щита. Простой блок с BlockEntity, сканирует свой корабль и добавляет HP к генератору.
3. **ShieldEmitterBlock** — направленный блок (как observer). Усиливает щит в направлении, куда смотрит.

### Система щита:
- **ShieldInstance** — POJO: shipId, currentHP, maxHP, radius, rechargeRate, lastHitTime, active
- **ShieldManager** — серверный singleton. HashMap<Long, ShieldInstance>. Тикает каждый серверный тик, обрабатывает перезарядку. Очищается при выгрузке мира.
- **ShieldDamageHandler** — перехватывает урон через:
  - Forge event `ExplosionEvent.Detonate` — отменяет разрушение блоков корабля с щитом, снимает HP
  - Forge event `ProjectileImpactEvent` — перехватывает попадание projectile в зону щита
  - Mixin в `Explosion.explode()` как fallback

### Параметры по тирам:
| Тир | Max HP | Radius | Recharge/tick | Cooldown после удара |
|-----|--------|--------|---------------|---------------------|
| Iron | 100 | 8 блоков | 0.5 HP/tick | 100 тиков (5 сек) |
| Diamond | 250 | 16 блоков | 1.0 HP/tick | 60 тиков (3 сек) |
| Netherite | 500 | 24 блоков | 2.0 HP/tick | 40 тиков (2 сек) |

### Клиентская часть:
- **ShieldRenderer** (BlockEntityRenderer) — рендерит полупрозрачную сферу вокруг корабля. Цвет: синий >50% HP, жёлтый 25-50%, красный <25%.
- **ShieldHudOverlay** — RenderGuiOverlayEvent: показывает HP щита если игрок стоит на корабле с щитом
- Партиклы при попадании по щиту

### Нетворк:
- **ShieldSyncPacket** (S2C) — синхронизирует HP, статус щита с клиентом
- **ShieldTogglePacket** (C2S) — вкл/выкл щита через GUI

## ПРАВИЛА КОДА

1. **Язык: Java** (не Kotlin), если template не на Kotlin
2. Используй DeferredRegister для всех регистраций (блоки, предметы, BlockEntity типы, звуки, creative tabs)
3. Для взаимодействия с VS2 используй:
   - `VSGameUtilsKt.getShipManagingPos(level, blockPos)` — получить Ship для блока
   - `VSGameUtilsKt.getShipObjectManagingPos(level, blockPos)` — получить ShipObject (серверная сторона)
   - `Ship.getId()` — уникальный ID корабля
   - `Ship.getTransform()` — трансформация (позиция, ротация в мире)
   - `Ship.getWorldAABB()` — bounding box корабля в мировых координатах
4. Не используй deprecated API. Если не уверен в сигнатуре метода VS2 — напиши TODO комментарий
5. Все строки в `en_us.json` lang файле
6. Рецепты в JSON datagen или ручные JSON файлы в `data/vsshields/recipes/`
7. Mixin-конфиг: `vsshields.mixins.json`, refmap: `vsshields.refmap.json`

## ПОРЯДОК РАБОТЫ

Делай поэтапно, каждый этап должен компилироваться:

### Этап 1 — Каркас мода
- Переименуй modid и пакеты из template в `vsshields` / `com.mechanicalskies.vsshields`
- Обнови `mods.toml`: name="VS Energy Shields", modid="vsshields", description, authors
- Создай пакетную структуру: registry/, block/, blockentity/, shield/, client/, network/, mixin/
- Главный класс `VSShieldsMod.java` с @Mod("vsshields")
- `ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java` — пустые DeferredRegister'ы
- Проверь что компилируется: `./gradlew build`

### Этап 2 — Shield Generator блок
- `ShieldGeneratorBlock extends Block` — directional (горизонтальное размещение), с BlockEntity
- `ShieldGeneratorBlockEntity extends BlockEntity` — тикающий (implements `TickableBlockEntity` или через `BlockEntityTicker`)
- В tick(): найти свой корабль через VS API, создать/обновить ShieldInstance в ShieldManager
- `ShieldInstance.java` — все данные щита
- `ShieldManager.java` — HashMap, методы register/unregister/getShield/tick
- Регистрация блока, предмета, BlockEntity типа
- Простая blockstate JSON + placeholder модель (куб с текстурой)
- Проверь что компилируется

### Этап 3 — Перехват урона
- `ShieldDamageHandler.java` — подписка на `ExplosionEvent.Detonate` через @SubscribeEvent
- Логика: для каждого affected block проверить, на корабле ли он → есть ли щит → снять HP → убрать блок из списка affected
- Регистрация хендлера через MinecraftForge.EVENT_BUS
- Тест: TNT рядом с кораблём с генератором не должен ломать блоки пока щит жив

### Этап 4 — Нетворк и синхронизация
- `ModNetwork.java` — SimpleChannel
- `ShieldSyncPacket.java` — шлём клиенту: shipId, currentHP, maxHP, active
- Синхронизация каждые 10 тиков или при изменении HP
- На клиенте: `ClientShieldManager` хранит данные для рендера

### Этап 5 — Рендеринг щита
- `ShieldRenderer extends BlockEntityRenderer<ShieldGeneratorBlockEntity>`
- Рисуем полупрозрачную сферу (используй `RenderType.translucent()` или custom RenderType)
- Сфера привязана к позиции корабля в мире (не к позиции блока в shipyard!)
- Трансформация: ship.getTransform().getPositionInWorld()
- Цвет по HP

### Этап 6 — GUI и HUD
- `ShieldGeneratorMenu extends AbstractContainerMenu` — слот топлива, кнопка вкл/выкл
- `ShieldGeneratorScreen extends AbstractContainerScreen` — GUI с HP-баром
- `ShieldHudOverlay` — маленький индикатор HP щита в углу экрана

### Этап 7 — Конденсаторы, эмиттеры, рецепты
- ShieldCapacitorBlock + BlockEntity
- ShieldEmitterBlock + направленность
- Рецепты крафта в JSON
- Creative tab

## ВАЖНЫЕ НЮАНСЫ VS2

- Блоки корабля физически находятся в "shipyard" (координаты ~12M+ по Z). То что видит игрок — проекция. Все BlockEntity операции работают с shipyard координатами.
- `Ship.getTransform()` даёт матрицу из shipyard → world. Для рендера нужно трансформировать позиции.
- VS2 тикает физику в отдельных потоках. Не мутируй данные корабля из game thread напрямую.
- `VSGameUtilsKt` — основной утилитный класс, это Kotlin object, вызывается из Java как `VSGameUtilsKt.methodName()`
- ShipId — это `long`, не UUID.

## ПОСЛЕ КАЖДОГО ЭТАПА

1. Запусти `./gradlew build` и убедись что нет ошибок
2. Если есть ошибки — исправь их до перехода к следующему этапу
3. Кратко скажи что сделано и что дальше

Начинай с Этапа 1. Сначала изучи текущую структуру проекта, потом приступай.
```
