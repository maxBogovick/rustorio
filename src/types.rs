//! types.rs — общий «словарь» игры.
//!
//! Базовые типы предметной области: направление, предмет, инструмент.
//! Здесь СПЕЦИАЛЬНО нет ни macroquad, ни отрисовки, ни логики симуляции —
//! только чистые данные. Это нижний слой: от него зависят все, он — ни от кого.
//! (Держать «словарь» чистым — важная привычка: его легко читать и тестировать.)

// ─────────────────────────────────────────────────────────────────────
/// Куда «смотрит» здание (бур/лента/печь отдают предмет в эту сторону).
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Direction {
    North,
    East,
    South,
    West,
}

impl Direction {
    /// Смещение по сетке. Ось Y растёт вниз (как на экране), поэтому North = -1.
    pub fn delta(self) -> (i32, i32) {
        match self {
            Direction::North => (0, -1),
            Direction::East => (1, 0),
            Direction::South => (0, 1),
            Direction::West => (-1, 0),
        }
    }

    /// Повернуть по часовой стрелке (клавиша R).
    pub fn rotate_cw(self) -> Direction {
        match self {
            Direction::North => Direction::East,
            Direction::East => Direction::South,
            Direction::South => Direction::West,
            Direction::West => Direction::North,
        }
    }

    /// Короткое имя для интерфейса.
    pub fn name(self) -> &'static str {
        match self {
            Direction::North => "N",
            Direction::East => "E",
            Direction::South => "S",
            Direction::West => "W",
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
/// Предметы, которые бегают по лентам.
/// Добавляешь предмет? Только сюда (и цвет в render.rs). Больше нигде.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Item {
    IronOre,
    IronPlate,
}

// ─────────────────────────────────────────────────────────────────────
/// Что выбрано в панели (клавиши 1..4). «Чертёж», по которому строим здание.
#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Tool {
    Miner,
    Belt,
    Furnace,
    Chest,
}

impl Tool {
    pub fn name(self) -> &'static str {
        match self {
            Tool::Miner => "Miner",
            Tool::Belt => "Belt",
            Tool::Furnace => "Furnace",
            Tool::Chest => "Chest",
        }
    }
}
