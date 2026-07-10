//! input.rs — ФАЗА 1 игрового цикла: ввод.
//!
//! Единственная задача: превратить действия игрока (клавиши, мышь) в изменения
//! состояния игры. Здесь НИЧЕГО не рисуется. Мир меняем только через методы
//! `Game`/`World` (`place`/`remove`) — это и есть «правильное взаимодействие с миром».

use crate::building::Building;
use crate::config::{OFFSET_X, OFFSET_Y, TILE};
use crate::game::Game;
use crate::types::Tool;
use crate::world::World;
use macroquad::prelude::*;

/// Пиксель мыши → координата клетки (если курсор над полем).
/// Это «маппинг ввода», поэтому живёт в input.rs, а не в состоянии игры.
fn hovered_tile(world: &World) -> Option<(i32, i32)> {
    let (mx, my) = mouse_position();
    let tx = ((mx - OFFSET_X) / TILE).floor() as i32;
    let ty = ((my - OFFSET_Y) / TILE).floor() as i32;
    if world.in_bounds(tx, ty) {
        Some((tx, ty))
    } else {
        None
    }
}

pub fn handle(game: &mut Game) {
    // Сразу запоминаем клетку под курсором — ею воспользуются и постройка ниже,
    // и отрисовка «призрака» (render читает game.hover, а не лезет к мыши сам).
    game.hover = hovered_tile(&game.world);

    // Выбор инструмента (клавиши 1..4).
    if is_key_pressed(KeyCode::Key1) {
        game.tool = Tool::Miner;
    }
    if is_key_pressed(KeyCode::Key2) {
        game.tool = Tool::Belt;
    }
    if is_key_pressed(KeyCode::Key3) {
        game.tool = Tool::Furnace;
    }
    if is_key_pressed(KeyCode::Key4) {
        game.tool = Tool::Chest;
    }

    // Поворот и пауза.
    if is_key_pressed(KeyCode::R) {
        game.dir = game.dir.rotate_cw();
    }
    if is_key_pressed(KeyCode::Space) {
        game.paused = !game.paused;
    }

    // Строительство мышью. ЛКМ можно держать и вести линию лент.
    if let Some((x, y)) = game.hover {
        if is_mouse_button_down(MouseButton::Left) {
            game.world.place(x, y, Building::new(game.tool, game.dir));
        }
        if is_mouse_button_down(MouseButton::Right) {
            game.world.remove(x, y);
        }
    }
}
