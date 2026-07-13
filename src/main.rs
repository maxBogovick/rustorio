//! Rustorio — крошечная, но настоящая мини-Factorio на Rust.
//!
//! ЭТОТ ФАЙЛ — точка входа и «сердце движка». Всё остальное — детали в модулях.
//! Игровой цикл — это три расцепленные фазы (паттерн «Game Loop», Nystrom):
//!
//!     input  → update → render
//!     (мышь)   (тики)   (рисуем)
//!
//! ПРАВИЛО ОДНОНАПРАВЛЕННОГО ПОТОКА:
//!   • input  — МЕНЯЕТ мир по действиям игрока;
//!   • update — МЕНЯЕТ мир по правилам симуляции (строго по фикс. тикам);
//!   • render — ТОЛЬКО ЧИТАЕТ мир и рисует, ничего не меняя.
//! Держись этого — и код останется понятным, даже когда игра разрастётся.
//!
//! Карта проекта см. ARCHITECTURE.md. Как играть — README.md.

// Весь проект — безопасный Rust. Запрещаем `unsafe` во всём крейте: если он
// где-то появится, компилятор откажется собирать. Дешёвая гарантия надёжности.
#![forbid(unsafe_code)]

// Объявляем модули. Порядок не важен: Rust сам разберётся с зависимостями.
mod building; // данные и поведение одного здания
mod config; // все настройки
mod game; // состояние игры + продвижение времени
mod input; // фаза 1: ввод
mod render; // фаза 3: отрисовка
mod systems; // фаза 2: симуляция (тик мира)
mod types; // общий словарь: Direction, Item, Tool
mod world; // данные мира (сетка)

use crate::config::{GRID_H, GRID_W, OFFSET_X, OFFSET_Y, TILE};
use crate::game::Game;
use crate::world::World;
use macroquad::prelude::*;

/// Настройки окна. Размер ВЫЧИСЛЯЕМ из сетки, чтобы поле всегда помещалось:
/// поле + поля-отступы слева/справа и сверху/снизу.
fn window_conf() -> Conf {
    let width = (GRID_W as f32 * TILE + 2.0 * OFFSET_X) as i32;
    let height = (GRID_H as f32 * TILE + OFFSET_Y + OFFSET_X) as i32;
    Conf {
        window_title: "Rustorio".to_owned(),
        window_width: width,
        window_height: height,
        high_dpi: true,
        ..Default::default()
    }
}

#[macroquad::main(window_conf)]
async fn main() {
    let mut game = Game::new(World::new(GRID_W, GRID_H));
    let textures = render::load_textures().await; // спрайты грузим один раз, не каждый кадр

    loop {
        input::handle(&mut game); // 1. ввод  → намерения игрока меняют мир
        game.update(); // 2. апдейт → системы двигают мир по тикам
        render::draw(&game, &textures); // 3. рендер → только читаем мир и рисуем

        next_frame().await; // отдать кадр экрану и ждать следующий
    }
}
