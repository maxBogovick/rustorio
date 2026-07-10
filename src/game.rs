//! game.rs — всё состояние игры в одном месте + продвижение времени.
//!
//! `Game` — это «мешок состояния», который живёт между кадрами: сам мир,
//! выбранный инструмент, направление, пауза. Поля `pub`, потому что их читают
//! и меняют соседние слои (input.rs и render.rs) — здесь это осознанно и удобно.
//!
//! Единственная логика тут — `update`: приём «фиксированный тик + аккумулятор»
//! (см. Gaffer «Fix Your Timestep»). Симуляция идёт строго по TICK, а кадры
//! рисуются с любой частотой. Благодаря этому мир ведёт себя одинаково на
//! быстром и медленном железе.

use crate::config::{MAX_FRAME_TIME, TICK};
use crate::systems;
use crate::types::{Direction, Tool};
use crate::world::World;
use macroquad::prelude::get_frame_time;

pub struct Game {
    pub world: World,
    pub tool: Tool,
    pub dir: Direction,
    pub paused: bool,
    /// Клетка под курсором в этом кадре (её вычисляет `input`, читает `render`).
    /// Так отрисовка остаётся честно «только чтение состояния», без похода к мыши.
    pub hover: Option<(i32, i32)>,
    /// Накопленное реальное время, ещё не «проигранное» в тиках.
    timer: f32,
}

impl Game {
    pub fn new(world: World) -> Self {
        Game {
            world,
            tool: Tool::Miner,
            dir: Direction::East,
            paused: false,
            hover: None,
            timer: 0.0,
        }
    }

    /// Продвинуть симуляцию: набежало время — делаем ровно столько тиков.
    pub fn update(&mut self) {
        if self.paused {
            return;
        }
        // Ограничиваем «наигранное» время сверху: после долгого зависания не
        // пытаемся отработать десятки тиков разом (иначе — «спираль смерти»).
        self.timer = (self.timer + get_frame_time()).min(MAX_FRAME_TIME);
        while self.timer >= TICK {
            self.timer -= TICK;
            systems::step(&mut self.world, TICK);
        }
    }
}
