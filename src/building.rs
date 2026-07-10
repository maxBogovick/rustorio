//! building.rs — данные и поведение ОДНОГО здания.
//!
//! Главная идея всей игры: здание — это `enum` со своим состоянием внутри
//! варианта. Логика — это набор маленьких методов, каждый из которых один
//! `match` по этому enum'у. Когда добавишь новый вариант здания, компилятор
//! сам перечислит все `match`, где надо дописать поведение. Это твоя
//! «карта задач» — просто иди по ошибкам компилятора сверху вниз.
//!
//! Зависит только от `types` и `config` (стрелки зависимостей смотрят вниз).

use crate::config::{MINER_TIME, SMELT_TIME};
use crate::types::{Direction, Item, Tool};

/// Всё, что можно поставить на клетку.
///
/// `Debug` — чтобы удобно печатать в тестах/при отладке; `Clone` — пригодится
/// для копирования схем и сохранения (веха M5).
#[derive(Debug, Clone)]
pub enum Building {
    /// Бур: стоит на руде, раз в `MINER_TIME` кладёт руду в `output`,
    /// затем отдаёт её соседу по направлению `dir`.
    Miner {
        dir: Direction,
        cooldown: f32,
        output: Option<Item>,
    },
    /// Лента: держит один предмет и толкает его вперёд по `dir`.
    Belt { dir: Direction, item: Option<Item> },
    /// Печь: принимает руду в `input`, за `SMELT_TIME` плавит её в пластину
    /// (`output`), которую отдаёт соседу по `dir`.
    Furnace {
        dir: Direction,
        input: Option<Item>,
        progress: f32,
        output: Option<Item>,
    },
    /// Ящик: просто копит предметы (счётчик).
    Chest { items: u32 },
}

impl Building {
    /// Создать НОВОЕ здание по выбранному инструменту и направлению.
    /// (Единственное место, где рождаются здания — удобно и предсказуемо.)
    pub fn new(tool: Tool, dir: Direction) -> Building {
        match tool {
            Tool::Miner => Building::Miner {
                dir,
                cooldown: MINER_TIME,
                output: None,
            },
            Tool::Belt => Building::Belt { dir, item: None },
            Tool::Furnace => Building::Furnace {
                dir,
                input: None,
                progress: 0.0,
                output: None,
            },
            Tool::Chest => Building::Chest { items: 0 },
        }
    }

    /// «Внутренняя» работа здания за один шаг симуляции.
    /// `has_ore` — есть ли под зданием залежь руды (нужно буру).
    pub fn update(&mut self, dt: f32, has_ore: bool) {
        // `if ...` после образца — это «страж» (match guard): ветка срабатывает,
        // только когда условие истинно. Иначе управление идёт к следующей ветке.
        match self {
            // Бур копает, пока выход свободен и под ним есть руда.
            Building::Miner {
                cooldown, output, ..
            } if output.is_none() && has_ore => {
                *cooldown -= dt;
                if *cooldown <= 0.0 {
                    *output = Some(Item::IronOre);
                    *cooldown = MINER_TIME;
                }
            }
            // Печь плавит, пока выход свободен и есть сырьё.
            Building::Furnace {
                input,
                progress,
                output,
                ..
            } if output.is_none() && input.is_some() => {
                *progress += dt;
                if *progress >= SMELT_TIME {
                    *output = Some(Item::IronPlate);
                    *input = None;
                    *progress = 0.0;
                }
            }
            // Остальные случаи ничего не делают за тик: лента, ящик, а также
            // бур/печь, у которых страж не сработал (выход занят / нет сырья).
            // Заметь: НЕТ `_` — перечислены все варианты. Добавишь новое здание —
            // компилятор потребует решить, что оно делает за тик (см. `output` и др.).
            Building::Miner { .. }
            | Building::Furnace { .. }
            | Building::Belt { .. }
            | Building::Chest { .. } => {}
        }
    }

    /// Что здание готово отдать наружу и в какую сторону (`None` — нечего).
    ///
    /// Здесь НЕТ `_`: перечислены все варианты явно. Добавишь новое здание —
    /// компилятор потребует дописать и сюда (в этом вся суть «карты задач»).
    pub fn output(&self) -> Option<(Item, Direction)> {
        match self {
            Building::Miner {
                dir,
                output: Some(it),
                ..
            } => Some((*it, *dir)),
            Building::Belt {
                dir,
                item: Some(it),
            } => Some((*it, *dir)),
            Building::Furnace {
                dir,
                output: Some(it),
                ..
            } => Some((*it, *dir)),
            // выход пуст или это ящик — отдавать нечего:
            Building::Miner { output: None, .. }
            | Building::Belt { item: None, .. }
            | Building::Furnace { output: None, .. }
            | Building::Chest { .. } => None,
        }
    }

    /// Может ли здание принять `item` от соседа прямо сейчас?
    pub fn can_accept(&self, item: Item) -> bool {
        match self {
            Building::Belt { item: None, .. } => true,
            Building::Furnace { input: None, .. } => item == Item::IronOre,
            Building::Chest { .. } => true,
            // бур ничего не принимает; занятые лента/печь — тоже нет:
            Building::Miner { .. } | Building::Belt { .. } | Building::Furnace { .. } => false,
        }
    }

    /// Убрать отданный предмет из выхода (после успешной передачи).
    pub fn remove_output(&mut self) {
        match self {
            Building::Miner { output, .. } => *output = None,
            Building::Belt { item, .. } => *item = None,
            Building::Furnace { output, .. } => *output = None,
            Building::Chest { .. } => {}
        }
    }

    /// Принять предмет в себя (после успешной передачи).
    pub fn accept(&mut self, item: Item) {
        match self {
            Building::Belt { item: slot, .. } => *slot = Some(item),
            Building::Furnace { input, .. } => *input = Some(item),
            Building::Chest { items } => *items += 1,
            Building::Miner { .. } => {}
        }
    }

    /// Направление здания (у ящика его нет).
    pub fn dir(&self) -> Option<Direction> {
        match self {
            Building::Miner { dir, .. } => Some(*dir),
            Building::Belt { dir, .. } => Some(*dir),
            Building::Furnace { dir, .. } => Some(*dir),
            Building::Chest { .. } => None,
        }
    }

    /// «Такое же» здание (тот же тип и направление)? Нужно, чтобы рисование
    /// мышью не пересоздавало здание каждый кадр (иначе лента теряла бы предмет).
    pub fn same_kind(&self, other: &Building) -> bool {
        std::mem::discriminant(self) == std::mem::discriminant(other) && self.dir() == other.dir()
    }
}
