//! render.rs — ФАЗА 3 игрового цикла: отрисовка.
//!
//! ЗОЛОТОЕ ПРАВИЛО: отрисовка только ЧИТАЕТ состояние (`&Game`) и рисует.
//! Она НИКОГДА не меняет мир. Благодаря этому «что происходит в игре» и «как
//! это выглядит» — независимы: можно менять картинку, не боясь сломать логику.
//!
//! Все цвета собраны здесь (палитра). Хочешь перекрасить игру — тебе сюда.

use crate::building::Building;
use crate::config::*;
use crate::game::Game;
use crate::types::{Direction, Item};
use macroquad::prelude::*;

// ── Палитра ──────────────────────────────────────────────────────────
const C_BG: Color = color_u8!(26, 26, 31, 255);
const C_GROUND: Color = color_u8!(38, 41, 46, 255);
const C_ORE: Color = color_u8!(51, 71, 115, 255);
const C_GRID: Color = color_u8!(0, 0, 0, 64);
const C_MINER: Color = color_u8!(230, 140, 38, 255);
const C_BELT: Color = color_u8!(71, 71, 82, 255);
const C_BELT_ARROW: Color = color_u8!(140, 140, 158, 255);
const C_FURNACE: Color = color_u8!(158, 66, 51, 255);
const C_FURNACE_ARROW: Color = color_u8!(242, 191, 89, 255);
const C_CHEST: Color = color_u8!(140, 115, 77, 255);
const C_HINT: Color = color_u8!(179, 179, 199, 255);

/// Цвет предмета живёт здесь, а не в types.rs — это чисто «визуальная» деталь.
fn item_color(item: Item) -> Color {
    match item {
        Item::IronOre => color_u8!(191, 199, 209, 255),
        Item::IronPlate => color_u8!(140, 191, 242, 255),
    }
}

/// Нарисовать весь кадр.
pub fn draw(game: &Game) {
    clear_background(C_BG);

    // Поле: фон клеток + здания.
    for y in 0..GRID_H {
        for x in 0..GRID_W {
            let sx = OFFSET_X + x as f32 * TILE;
            let sy = OFFSET_Y + y as f32 * TILE;
            let tile = game.world.tile(x, y);

            let bg = if tile.ore { C_ORE } else { C_GROUND };
            draw_rectangle(sx, sy, TILE, TILE, bg);
            draw_rectangle_lines(sx, sy, TILE, TILE, 1.0, C_GRID);

            if let Some(b) = &tile.building {
                draw_building(sx, sy, b);
                // Подсказка: бур поставлен не на руду — он ничего не добудет.
                if matches!(b, Building::Miner { .. }) && !tile.ore {
                    draw_rectangle_lines(sx + 2.0, sy + 2.0, TILE - 4.0, TILE - 4.0, 2.0, RED);
                }
            }
        }
    }

    // «Призрак» будущего здания под курсором (клетку посчитал input → game.hover).
    if let Some((tx, ty)) = game.hover {
        let sx = OFFSET_X + tx as f32 * TILE;
        let sy = OFFSET_Y + ty as f32 * TILE;
        draw_rectangle_lines(sx, sy, TILE, TILE, 2.0, WHITE);
        draw_arrow(sx, sy, game.dir, Color::new(1.0, 1.0, 1.0, 0.6));
    }

    draw_hud(game);
}

/// Верхняя панель: что выбрано и подсказки по управлению.
fn draw_hud(game: &Game) {
    let status = format!(
        "Tool: {}   Dir: {}   {}",
        game.tool.name(),
        game.dir.name(),
        if game.paused { "[PAUSED]" } else { "" }
    );
    draw_text(&status, 20.0, 30.0, 28.0, WHITE);
    draw_text(
        "1 Miner  2 Belt  3 Furnace  4 Chest    |    LMB place   RMB remove   R rotate   Space pause",
        20.0,
        55.0,
        20.0,
        C_HINT,
    );
}

/// Отрисовка одного здания. Новый вид здания? Добавь сюда ещё одну ветку —
/// компилятор напомнит об этом, если сделаешь `match` исчерпывающим.
fn draw_building(sx: f32, sy: f32, b: &Building) {
    let pad = 3.0;
    let inner = TILE - 2.0 * pad;
    match b {
        Building::Miner { dir, output, .. } => {
            draw_rectangle(sx + pad, sy + pad, inner, inner, C_MINER);
            draw_arrow(sx, sy, *dir, BLACK);
            if let Some(it) = output {
                draw_item(sx, sy, *it);
            }
        }
        Building::Belt { dir, item } => {
            draw_rectangle(sx + pad, sy + pad, inner, inner, C_BELT);
            draw_arrow(sx, sy, *dir, C_BELT_ARROW);
            if let Some(it) = item {
                draw_item(sx, sy, *it);
            }
        }
        Building::Furnace {
            dir,
            input,
            progress,
            output,
        } => {
            draw_rectangle(sx + pad, sy + pad, inner, inner, C_FURNACE);
            draw_arrow(sx, sy, *dir, C_FURNACE_ARROW);
            let frac = (progress / SMELT_TIME).clamp(0.0, 1.0);
            draw_rectangle(sx + pad, sy + TILE - pad - 4.0, inner * frac, 4.0, YELLOW);
            if let Some(it) = output {
                draw_item(sx, sy, *it);
            } else if let Some(it) = input {
                draw_item(sx, sy, *it);
            }
        }
        Building::Chest { items } => {
            draw_rectangle(sx + pad, sy + pad, inner, inner, C_CHEST);
            draw_text(items.to_string(), sx + 6.0, sy + TILE - 8.0, 22.0, WHITE);
        }
    }
}

/// Треугольник-стрелка в центре клетки, смотрящий в направлении `dir`.
fn draw_arrow(sx: f32, sy: f32, dir: Direction, color: Color) {
    let cx = sx + TILE / 2.0;
    let cy = sy + TILE / 2.0;
    let r = TILE * 0.26;
    let (dx, dy) = dir.delta();
    let (dx, dy) = (dx as f32, dy as f32);
    let (px, py) = (-dy, dx); // перпендикуляр к направлению
    let tip = vec2(cx + dx * r, cy + dy * r);
    let base1 = vec2(
        cx - dx * r * 0.6 + px * r * 0.7,
        cy - dy * r * 0.6 + py * r * 0.7,
    );
    let base2 = vec2(
        cx - dx * r * 0.6 - px * r * 0.7,
        cy - dy * r * 0.6 - py * r * 0.7,
    );
    draw_triangle(tip, base1, base2, color);
}

/// Кружок-предмет в центре клетки.
fn draw_item(sx: f32, sy: f32, item: Item) {
    let cx = sx + TILE / 2.0;
    let cy = sy + TILE / 2.0;
    draw_circle(cx, cy, TILE * 0.16, BLACK);
    draw_circle(cx, cy, TILE * 0.13, item_color(item));
}
