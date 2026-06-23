<div align="center">

# 🌲 TreeChopper

**One hit. Whole tree. Falling animation.**

A lightweight Paper/Spigot/Folia plugin that fells entire trees with a single axe swing — logs collapse layer by layer with real physics, leaves decay automatically, and saplings replant themselves.

[![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Spigot%20%7C%20Folia-brightgreen?style=flat-square)](https://papermc.io/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16--26.1.2-red?style=flat-square)](https://modrinth.com/plugin/treechopper-ultra)
[![Java](https://img.shields.io/badge/Java-8%2B%20runtime-orange?style=flat-square)](https://adoptium.net/)
[![Tests](https://img.shields.io/badge/tests-63%20passing-success?style=flat-square)](#)
[![bStats](https://img.shields.io/badge/bStats-live%20metrics-blue?style=flat-square)](https://bstats.org/plugin/bukkit/TreeChopperUltra/31348)
[![Ko-fi](https://img.shields.io/badge/Support%20me-Ko--fi-ff5f5f?style=flat-square&logo=ko-fi&logoColor=white)](https://ko-fi.com/erotoro)

[Features](#features) · [How It Works](#how-it-works) · [Integrations](#integrations) · [Configuration](#configuration) · [Commands & Permissions](#commands--permissions) · [Installation](#installation)

</div>

---

## Features

### Core mechanics
- **One-hit felling** — break any log with an axe and the entire tree collapses instantly
- **Falling animation** — logs break top-to-bottom and fly outward as `FallingBlock` entities with real physics
- **Smart tree separation** — two adjacent trees are treated as independent; chop one and the other keeps all its leaves
- **Automatic leaf decay** — leaves belonging to the chopped tree break after the logs fall; disputed leaves shared with a neighboring tree are left untouched
- **Attached vegetation** — vines and similar canopy/trunk vegetation are cleaned up automatically
- **2×2 mega tree support** — jungle, spruce, and dark oak mega trees are detected and felled as a single unit
- **All vanilla wood types** — oak, birch, spruce, jungle, dark oak, acacia, cherry, mangrove, crimson stem, warped stem, and mushroom stem (including Nether fungi)

### Balance & safety
- **Durability and Unbreaking** — the axe takes one durability hit per log; the Unbreaking enchantment is correctly accounted for probabilistically across the whole tree
- **Fortune & Silk Touch** — enchantments on the axe apply to every log that falls, not just the first one
- **Player-placed log protection** — blocks placed by players are tracked in `placed-logs.yml` and never trigger mass felling, preventing griefing with stacked logs
- **Structure protection** — heuristic detection avoids treating village houses and other generated structures as natural trees
- **Sneak activation mode** — configurable: always active, active only while sneaking, or disabled while sneaking
- **Per-player toggle** — each player can enable or disable the mechanic for themselves with `/treechopper toggle`; state persists across sessions

### Server compatibility
- **Folia support** — work is split into per-chunk batches and scheduled through the Region Scheduler (with Bukkit scheduler fallback), making TreeChopper fully compatible with Folia's threaded region model
- **WorldGuard & GriefPrevention** — felling is blocked inside protected regions; the plugin respects both `CanBuildQuery` and claim ownership checks before breaking any block
- **CoreProtect logging** — every log and leaf broken by the plugin is recorded under the player's name, so `/co rollback` and `/co lookup` work correctly for the full tree
- **Auto-replant** — after a tree is felled a sapling is automatically placed at the base; supports mega trees, configurable inventory consumption, and protection-aware placement

### Statistics & leaderboards
- **Per-player stats** — trees felled and logs broken are tracked per player and shown with `/treechopper stats`
- **Leaderboard** — `/treechopper top` lists the best lumberjacks; ranks are exposed to other plugins
- **PlaceholderAPI** — built-in expansion for scoreboards, tab, holograms and chat (see [Placeholders](#placeholders))
- **Dependency-free storage** — stats live in a tiny embedded file written atomically off the main thread; no database driver, no native libraries, no bloat (the release jar stays ~200 KB). The storage layer is interface-based, so a MySQL backend for cross-server networks can be added without touching the rest of the plugin

### Performance
- **Chunk-batched scheduling** — blocks are grouped by chunk and processed in small batches per tick, keeping TPS impact minimal even on large trees
- **Configurable limits** — `max-logs`, `max-blocks-per-task`, BFS radii, and detection thresholds are all tunable in `config.yml`
- **Async, non-blocking stats** — all stat I/O runs on a dedicated thread; PlaceholderAPI lookups read in-memory caches and never touch disk (Folia-safe)
- **Hot reload** — `/treechopper reload` reloads config, localization, and all services without a server restart

---

## How It Works

```
Player breaks a log with an axe
    │
    ├─ Checks: sneak mode · player toggle · axe durability
    │
    ├─ Traces the trunk down to its base, then up
    │       └─ Detects 2×2 mega trunk at base level
    │
    ├─ BFS collects all connected logs of the same wood type
    │       └─ Restricted by: horizontal distance from axis · branch height · diagonal rules
    │
    ├─ NaturalTreeChecker validates the structure against known structure patterns
    │
    ├─ ProtectionService checks WorldGuard / GriefPrevention for every block
    │
    ├─ BreakPlan is built — hit block first, then remaining logs sorted top-to-bottom
    │       └─ Durability damage modeled probabilistically per Unbreaking level
    │
    ├─ Animation runs — logs fall layer by layer as FallingBlock entities
    │       └─ Each block fires a synthetic BlockBreakEvent so other plugins can react
    │
    └─ After all logs land:
            ├─ Leaves & attached vegetation are collected via BFS and broken in layers
            │       └─ Ownership check prevents touching leaves shared with a neighboring tree
            └─ Auto-replant places the correct sapling at the base (if enabled)
```

---

## Integrations

| Plugin | What it does |
|---|---|
| **WorldGuard** | Blocks felling inside protected regions |
| **GriefPrevention** | Blocks felling inside claimed land |
| **CoreProtect** | Logs every broken block and replanted sapling under the player's name |
| **PlaceholderAPI** | Exposes per-player stats and leaderboard placeholders |
| **bStats** | Anonymous usage metrics are embedded into the release jar for lightweight plugin statistics |

All integrations are **soft dependencies** — the plugin works perfectly without any of them installed. `bStats` is bundled directly into the release jar and does not require any extra setup.

### Placeholders

Requires [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/). Identifier: `treechopper`.

| Placeholder | Description |
|---|---|
| `%treechopper_trees%` | Trees the player has felled |
| `%treechopper_logs%` | Logs the player has broken |
| `%treechopper_rank%` | Player's leaderboard rank (`-` if unranked) |
| `%treechopper_top_name_<n>%` | Name of the #`<n>` leaderboard entry |
| `%treechopper_top_trees_<n>%` | Trees felled by the #`<n>` entry |
| `%treechopper_top_logs_<n>%` | Logs broken by the #`<n>` entry |

> Per-player placeholders (`trees`/`logs`/`rank`) resolve for online players; leaderboard placeholders (`top_*`) work for everyone.

---

## bStats

[![TreeChopper bStats](https://bstats.org/signatures/bukkit/TreeChopperUltra.svg)](https://bstats.org/plugin/bukkit/TreeChopperUltra/31348)

Live usage statistics for TreeChopper are available on the public [bStats page](https://bstats.org/plugin/bukkit/TreeChopperUltra/31348).

---

## Configuration

<details>
<summary><b>config.yml — full reference</b></summary>

```yaml
language:
  default: en          # en · ru · uk · pl · de · fr · es · it · cs
  fallback: en

limits:
  max-logs: 512                  # hard cap on logs per fell
  leaf-search-radius: 6          # BFS depth for connected leaves
  foreign-log-scan-radius: 8     # radius for neighboring-tree detection

performance:
  max-blocks-per-task: 16        # blocks broken per scheduler tick

activation:
  mode: SNEAK_DISABLE            # ALWAYS_ON · SNEAK_DISABLE · SNEAK_ENABLE

player-toggle:
  enabled: true
  default-enabled: true
  save-on-change: true

detection:
  min-leaf-contacts: 4
  min-mega-leaf-contacts: 8
  max-structure-contacts: 4

storage:
  max-placed-logs-file-bytes: 5242880
  max-placed-log-entries: 100000
  max-invalid-placed-log-warnings: 10

protection:
  enabled: true
  check-breaks: true
  check-placement: true
  mode: FAIL_WHOLE_TREE
  use-worldguard: true
  use-griefprevention: true
  debug: false

integrations:
  coreprotect:
    enabled: true
    debug: false

auto-replant:
  enabled: true
  require-sapling: false         # take sapling from player's inventory
  consume-sapling: false
  delay-ticks-after-fell: 20
  replant-mega-trees: true
  mega-mode: four-saplings       # single · four-saplings
  respect-protection: true
  only-natural-trees: true
  disabled-worlds: []
  debug: false
```

</details>

---

## Commands & Permissions

| Command | Permission | Default | Description |
|---|---|---|---|
| `/treechopper reload` | `treechopper.reload` | op | Reloads config, localization, and all services |
| `/treechopper toggle` | `treechopper.toggle` | everyone | Enables or disables tree chopping for yourself |
| `/treechopper stats` | `treechopper.stats` | everyone | Shows your own trees felled, logs broken and leaderboard rank |
| `/treechopper top` | `treechopper.top` | everyone | Shows the top lumberjacks leaderboard |

---

## Installation

1. Download `TreeChopper-1.7.0.jar` and drop it into your server `plugins/` folder.
2. Restart the server — `config.yml` and language files are created automatically.
3. Adjust settings in `config.yml` as needed.
4. Apply changes without restarting: `/treechopper reload`

### Building from source

- Build the release jar with embedded `bStats`: `./gradlew build`
- Output artifact: `build/libs/TreeChopper-1.7.0.jar` (compiled on Java 21, then downgraded to Java 8 bytecode so a single jar runs across the whole supported range)
- **Building from source requires JDK 21**; the produced jar only requires Java 8+ at runtime.

**Requirements:** Paper, Spigot, or Folia · Java 8+ runtime · Minecraft 1.16-26.1.2

---

## Localization

TreeChopper ships with built-in translations for **English**, **Russian**, **Ukrainian**, **Polish**, **German**, **French**, **Spanish**, **Italian**, and **Czech**. Language files live in `plugins/TreeChopper/lang/` and can be edited freely. Supported language codes: `en`, `ru`, `uk`, `pl`, `de`, `fr`, `es`, `it`, `cs`. Set your preferred language in `config.yml` under `language.default`.

---

<div align="center">

---

# 🌲 TreeChopper

**Один удар. Всё дерево. Анимация падения.**

Лёгкий плагин для Paper/Spigot/Folia: руби деревья одним ударом топора — брёвна падают слой за слоем с физикой, листва распадается автоматически, а саженцы подсаживаются сами.

</div>

---

## Возможности

### Основная механика
- **Рубка одним ударом** — сломай любое бревно топором, и всё дерево упадёт
- **Анимация падения** — брёвна ломаются сверху вниз и разлетаются через `FallingBlock` с физикой
- **Умное разделение деревьев** — два дерева рядом обрабатываются независимо; рубишь одно — второе остаётся нетронутым со своей листвой
- **Автоматический распад листвы** — листва срубленного дерева ломается сама; спорные листья рядом с соседним деревом сохраняются
- **Связанная растительность** — лианы и похожая растительность рядом с кроной и стволом убираются автоматически
- **Поддержка мега-деревьев 2×2** — большие тропические деревья, ели и тёмный дуб со стволом 2×2 определяются и рубятся целиком
- **Все типы древесины** — дуб, берёза, ель, тропическое дерево, тёмный дуб, акация, вишня, мангровое дерево, багровый и искажённый стебель, грибной стебель

### Баланс и безопасность
- **Прочность и Нерушимость** — топор получает урон за каждое бревно; зачарование Нерушимость учитывается вероятностно по всему дереву
- **Fortune и Silk Touch** — зачарования топора применяются ко всем упавшим брёвнам, а не только к первому
- **Защита поставленных блоков** — брёвна, поставленные игроком, не запускают массовую рубку; хранятся в `placed-logs.yml`
- **Защита структур** — эвристика не позволяет плагину воспринимать дома деревень и другие постройки как деревья
- **Режим активации** — настраивается: всегда, только при приседании или отключается приседанием
- **Персональный тоггл** — каждый игрок может включить или отключить механику для себя через `/treechopper toggle`; состояние сохраняется между сессиями

### Совместимость с сервером
- **Folia** — задачи разбиваются на батчи по чанкам и выполняются через Region Scheduler (с fallback на Bukkit scheduler)
- **WorldGuard и GriefPrevention** — рубка блокируется внутри защищённых регионов и захваченных территорий
- **CoreProtect** — каждое сломанное бревно и посаженный саженец логируются под именем игрока; `/co rollback` работает корректно для всего дерева
- **Авто-посадка** — после рубки саженец автоматически высаживается у основания; поддержка мега-деревьев, настраиваемый расход инвентаря, уважение защиты

### Статистика и лидерборды
- **Личная статистика** — срубленные деревья и сломанные брёвна считаются по каждому игроку; смотреть через `/treechopper stats`
- **Лидерборд** — `/treechopper top` показывает лучших лесорубов; место в топе доступно другим плагинам
- **PlaceholderAPI** — встроенный экспеншн для скорборда, таба, голограмм и чата (см. [Плейсхолдеры](#плейсхолдеры))
- **Хранилище без зависимостей** — статистика лежит в крошечном встроенном файле, который пишется атомарно вне главного потока; без драйверов БД, без нативных библиотек, без раздувания (релизный джар ~200 КБ). Слой хранения на интерфейсе — MySQL для сетей серверов можно добавить, не трогая остальной плагин

### Производительность
- **Батчинг по чанкам** — блоки группируются по чанкам и обрабатываются небольшими порциями за тик
- **Настраиваемые лимиты** — `max-logs`, `max-blocks-per-task`, радиусы BFS и пороги детекции задаются в `config.yml`
- **Асинхронная статистика** — весь ввод-вывод статистики на отдельном потоке; запросы PlaceholderAPI читают кэш в памяти и не трогают диск (безопасно для Folia)
- **Горячая перезагрузка** — `/treechopper reload` перезагружает конфиг, локализацию и все сервисы без рестарта

---

## Как это работает

```
Игрок ломает бревно топором
    │
    ├─ Проверки: режим активации · тоггл игрока · прочность топора
    │
    ├─ Трассировка ствола вниз до основания и вверх до верхушки
    │       └─ Определение мега-ствола 2×2 на уровне основания
    │
    ├─ BFS собирает все связанные брёвна того же типа
    │       └─ Ограничения: расстояние от оси · высота веток · диагональные шаги
    │
    ├─ NaturalTreeChecker проверяет, не является ли структура постройкой
    │
    ├─ ProtectionService проверяет WorldGuard / GriefPrevention для каждого блока
    │
    ├─ Формируется BreakPlan — сначала ударный блок, потом остальные сверху вниз
    │       └─ Урон прочности рассчитывается вероятностно с учётом Нерушимости
    │
    ├─ Анимация — брёвна падают слоями как FallingBlock-сущности
    │       └─ Каждый блок вызывает синтетический BlockBreakEvent для других плагинов
    │
    └─ После приземления брёвен:
            ├─ Листва и растительность собираются через BFS и ломаются по слоям
            │       └─ 3D-проверка принадлежности не трогает листья соседних деревьев
            └─ Авто-посадка высаживает подходящий саженец у основания (если включена)
```

---

## Интеграции

| Плагин | Что делает |
|---|---|
| **WorldGuard** | Блокирует рубку внутри защищённых регионов |
| **GriefPrevention** | Блокирует рубку внутри захваченных территорий |
| **CoreProtect** | Логирует каждый сломанный блок и посаженный саженец на имя игрока |
| **PlaceholderAPI** | Предоставляет плейсхолдеры личной статистики и лидерборда |

Все интеграции — **мягкие зависимости**: плагин работает без них.

### Плейсхолдеры

Требуется [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/). Идентификатор: `treechopper`.

| Плейсхолдер | Описание |
|---|---|
| `%treechopper_trees%` | Сколько деревьев срубил игрок |
| `%treechopper_logs%` | Сколько брёвен сломал игрок |
| `%treechopper_rank%` | Место игрока в топе (`-`, если вне топа) |
| `%treechopper_top_name_<n>%` | Имя игрока на позиции `<n>` |
| `%treechopper_top_trees_<n>%` | Деревья игрока на позиции `<n>` |
| `%treechopper_top_logs_<n>%` | Брёвна игрока на позиции `<n>` |

> Личные плейсхолдеры (`trees`/`logs`/`rank`) работают для онлайн-игроков; плейсхолдеры лидерборда (`top_*`) — для всех.

---

## Конфигурация

<details>
<summary><b>config.yml — полный справочник</b></summary>

```yaml
language:
  default: ru          # en · ru · uk · pl · de · fr · es · it · cs
  fallback: en

limits:
  max-logs: 512                  # максимум брёвен за одну рубку
  leaf-search-radius: 6          # глубина BFS для связной листвы
  foreign-log-scan-radius: 8     # радиус обнаружения соседних деревьев

performance:
  max-blocks-per-task: 16        # блоков за один тик планировщика

activation:
  mode: SNEAK_DISABLE            # ALWAYS_ON · SNEAK_DISABLE · SNEAK_ENABLE

player-toggle:
  enabled: true
  default-enabled: true
  save-on-change: true

detection:
  min-leaf-contacts: 4
  min-mega-leaf-contacts: 8
  max-structure-contacts: 4

storage:
  max-placed-logs-file-bytes: 5242880
  max-placed-log-entries: 100000
  max-invalid-placed-log-warnings: 10

protection:
  enabled: true
  check-breaks: true
  check-placement: true
  mode: FAIL_WHOLE_TREE
  use-worldguard: true
  use-griefprevention: true
  debug: false

integrations:
  coreprotect:
    enabled: true
    debug: false

auto-replant:
  enabled: true
  require-sapling: false         # брать саженец из инвентаря игрока
  consume-sapling: false
  delay-ticks-after-fell: 20
  replant-mega-trees: true
  mega-mode: four-saplings       # single · four-saplings
  respect-protection: true
  only-natural-trees: true
  disabled-worlds: []
  debug: false
```

</details>

---

## Команды и права

| Команда | Право | По умолчанию | Описание |
|---|---|---|---|
| `/treechopper reload` | `treechopper.reload` | op | Перезагружает конфиг, локализацию и все сервисы |
| `/treechopper toggle` | `treechopper.toggle` | все игроки | Включает или отключает рубку деревьев для себя |
| `/treechopper stats` | `treechopper.stats` | все игроки | Показывает ваши срубленные деревья, сломанные брёвна и место в топе |
| `/treechopper top` | `treechopper.top` | все игроки | Показывает таблицу лучших лесорубов |

---

## Установка

1. Скачай `TreeChopper-1.7.0.jar` и положи его в папку `plugins/` сервера.
2. Перезапусти сервер — `config.yml` и языковые файлы создадутся автоматически.
3. Настрой параметры в `config.yml` под свой сервер.
4. Применяй изменения без рестарта: `/treechopper reload`

**Требования:** Paper, Spigot или Folia · Java 8+ (рантайм) · Minecraft 1.16-26.1.2

> Сборка из исходников требует JDK 21; готовый JAR работает на Java 8+ (байткод понижается через JvmDowngrader).

---

## Локализация

TreeChopper поставляется с встроенными переводами на **английский**, **русский**, **украинский**, **польский**, **немецкий**, **французский**, **испанский**, **итальянский** и **чешский** языки. Файлы лежат в `plugins/TreeChopper/lang/` и редактируются свободно. Поддерживаемые коды языков: `en`, `ru`, `uk`, `pl`, `de`, `fr`, `es`, `it`, `cs`. Выбери язык в `config.yml` в разделе `language.default`.
