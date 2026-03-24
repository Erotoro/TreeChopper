![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Spigot%20%7C%20Folia-green.svg)
![Java](https://img.shields.io/badge/java-21%2B-orange.svg)
![Version minecraft](https://img.shields.io/badge/Version_Minecraft_1.21+-red.svg)
[![Support me](https://img.shields.io/badge/Support%20me-Ko--fi-ff5f5f?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/erotoro)

# TreeChopper

Lightweight Paper/Spigot/Folia plugin for instant tree chopping with a falling animation.

Hit a tree with any axe and the whole tree is chopped at once, logs fly away with physics, and leaves with attached tree vegetation break automatically.

---

## Features

- **One-hit tree chopping** — break one log with an axe and the whole tree falls
- **Falling animation** — logs break layer by layer from top to bottom and spread out using `FallingBlock`
- **Smart tree separation** — nearby trees are detected independently; chop one and the other stays intact with its leaves
- **Automatic leaf decay** — leaves from the chopped tree break automatically after the logs fall; disputed leaves near another tree stay untouched
- **Attached vegetation support** — vines and similar tree vegetation near the canopy or trunk are removed too
- **2x2 mega tree support** — large jungle trees, spruce trees, and dark oak trees with a `2x2` trunk are detected and chopped as a whole
- **All wood types** — oak, birch, spruce, jungle, dark oak, acacia, cherry, mangrove, crimson stem, warped stem, and mushroom stem
- **Durability and Unbreaking support** — the axe takes durability damage for each broken log and Unbreaking is respected
- **Placed log protection** — logs placed by players are stored in `placed-logs.yml` and do not trigger mass chopping
- **Structure protection** — the detection logic tries to avoid treating village houses and other generated structures as natural trees
- **Folia compatibility** — work is split into small batches and executed through a compatible scheduler
- **Reload command** — `/treechopper reload` reloads config and stored data without a full server restart

## How It Works

1. A player breaks a log with any axe.
2. The plugin traces the trunk down to the base and up to the top.
3. It checks for a `2x2` mega trunk at the base level.
4. It collects the tree through connected-log search with restrictions:
   - only the same wood type;
   - horizontal distance is limited from the trunk axis;
   - branches cannot drop below the lower part of the tree;
   - some downward diagonal steps are blocked.
5. It checks whether the found structure looks like a natural tree and not a building.
6. Logs are broken with a falling animation, while respecting drops and other plugins through synthetic `BlockBreakEvent`s.
7. Leaves and attached vegetation are collected separately and broken in layers after the logs fall.
8. For disputed leaves, a 3D ownership check compares distance to the current trunk and foreign trunks so nearby trees are not damaged.

## Requirements

- **Server software:** Paper, Spigot, or Folia
- **Java:** 21+
- **Minecraft version:** 1.21+

## Installation

1. Download `TreeChopper-1.3.jar` and place it into your server `plugins` folder.
2. Restart the server.

Additional notes:

- after first launch, `config.yml` will be created with limits, detection settings, and storage parameters;
- to apply changes without restarting, use `/treechopper reload`.

---

# TreeChopper

Легковесный плагин для Paper/Spigot/Folia: мгновенная рубка деревьев с анимацией падения.

Ударь по дереву любым топором, и всё дерево сломается сразу, брёвна разлетятся с физикой, а листва и связанная растительность распадутся автоматически.

---

## Возможности

- **Рубка одним ударом** — сломай одно бревно топором, и всё дерево упадёт
- **Анимация падения** — брёвна ломаются послойно сверху вниз и разлетаются в стороны через `FallingBlock`
- **Умное разделение деревьев** — два дерева рядом определяются независимо; рубишь одно, второе остаётся целым со своей листвой
- **Автоматический распад листвы** — листва срубленного дерева ломается автоматически после падения брёвен; спорные листья рядом с другим деревом сохраняются
- **Связанная растительность** — лианы и похожая древесная растительность рядом с кроной и стволом тоже убираются
- **Поддержка мега-деревьев 2x2** — большие тропические деревья, ели и тёмный дуб со стволом `2x2` определяются и рубятся целиком
- **Все типы древесины** — дуб, берёза, ель, тропическое дерево, тёмный дуб, акация, вишня, мангровое дерево, багровый и искажённый стебель, грибной стебель
- **Прочность и Нерушимость** — топор получает урон за каждое сломанное бревно, зачарование Нерушимость учитывается
- **Защита поставленных блоков** — брёвна, поставленные игроком, сохраняются в `placed-logs.yml` и не запускают массовую рубку
- **Защита структур** — логика распознавания старается не считать деревнями, домами и другими постройками обычные деревья
- **Совместимость с Folia** — задачи разбиваются на небольшие пачки и запускаются через совместимый scheduler
- **Команда перезагрузки** — `/treechopper reload` перечитывает конфиг и данные без полного рестарта сервера

## Как Это Работает

1. Игрок ломает бревно любым топором.
2. Плагин трассирует ствол вниз до основания и вверх до верхушки.
3. Проверяет наличие мега-ствола `2x2` на уровне основания.
4. Собирает дерево через поиск связанных брёвен с ограничениями:
   - только тот же тип древесины;
   - ограничение по горизонтальному расстоянию от оси ствола;
   - ветки не опускаются ниже нижней части дерева;
   - запрещены некоторые диагональные шаги вниз.
5. Проверяет, похоже ли найденное образование на натуральное дерево, а не на структуру.
6. Брёвна ломаются с анимацией падения послойно, с учётом дропа и защиты других плагинов через синтетические `BlockBreakEvent`.
7. Листва и связанная растительность собираются отдельно и ломаются по слоям после падения брёвен.
8. Для спорной листвы выполняется 3D-проверка принадлежности: лист сравнивается с расстоянием до своего и чужого ствола, и соседние деревья не задеваются.

## Требования

- **Ядра:** Paper, Spigot или Folia
- **Java:** 21+
- **Версия Minecraft:** 1.21+

## Установка

1. Скачай `TreeChopper-1.3.jar` и положи его в папку `plugins` сервера.
2. Перезапусти сервер.

Дополнительно:

- после первого запуска появится `config.yml` с лимитами, настройками распознавания и storage-параметрами;
- для применения изменений без рестарта можно использовать `/treechopper reload`.
