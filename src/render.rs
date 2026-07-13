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

// ── Палитра (для того, для чего ещё нет спрайта) ────────────────────
const C_BG: Color = color_u8!(26, 26, 31, 255);
const C_GROUND: Color = color_u8!(38, 41, 46, 255);
const C_ORE: Color = color_u8!(51, 71, 115, 255);
const C_GRID: Color = color_u8!(0, 0, 0, 64);
const C_HINT: Color = color_u8!(179, 179, 199, 255);
/// Смена кадров ленты в секунду.
const BELT_ANIM_SPEED: f64 = 4.0;

/// Все спрайты игры, загруженные один раз при старте (см. `load_textures`).
pub struct Textures {
    miner: [Texture2D; 3],
    // Поворот ленты отдельного спрайта не имеет — на повороте просто
    // доворачивается обычная прямая лента (см. `draw_building`).
    // branch_*.png (развилки-фильтры) и underground_*.png уже лежат в
    // resources/, но ещё не подключены — ждут веток/подземных лент в
    // building.rs.
    belt: [Texture2D; 2],
    chest: Texture2D,
    furnace_on: Texture2D,
    furnace_off: Texture2D,
    assembler: Texture2D,
    iron_ore: Texture2D,
    iron_plate: Texture2D,
    gear: Texture2D,
}

async fn load(path: &str) -> Texture2D {
    let tex = load_texture(path)
        .await
        .unwrap_or_else(|e| panic!("не удалось загрузить {path}: {e}"));
    // Nearest — иначе macroquad размажет пиксель-арт при масштабировании до TILE.
    tex.set_filter(FilterMode::Nearest);
    tex
}

/// Загрузить все текстуры из `resources/`. Зовётся один раз в `main`, до
/// игрового цикла (нужен `.await`, поэтому не может жить внутри `draw`).
pub async fn load_textures() -> Textures {
    Textures {
        miner: [
            load("resources/miner_1.png").await,
            load("resources/miner_2.png").await,
            load("resources/miner_3.png").await,
        ],
        belt: [
            load("resources/belt_1.png").await,
            load("resources/belt_2.png").await,
        ],
        chest: load("resources/chest.png").await,
        furnace_on: load("resources/furnace_on.png").await,
        furnace_off: load("resources/furnace_off.png").await,
        assembler: load("resources/assembler.png").await,
        iron_ore: load("resources/iron_ore.png").await,
        iron_plate: load("resources/iron_plate.png").await,
        gear: load("resources/iron_gear.png").await,
    }
}

fn item_texture(textures: &Textures, item: Item) -> &Texture2D {
    match item {
        Item::IronOre => &textures.iron_ore,
        Item::IronPlate => &textures.iron_plate,
        Item::Gear => &textures.gear,
    }
}

/// Нарисовать спрайт, растянув его на всю клетку (пиксель-арт — поэтому
/// `FilterMode::Nearest`, выставленный при загрузке, держит его чётким).
fn draw_sprite(sx: f32, sy: f32, tex: &Texture2D) {
    draw_texture_ex(
        tex,
        sx,
        sy,
        WHITE,
        DrawTextureParams {
            dest_size: Some(vec2(TILE, TILE)),
            ..Default::default()
        },
    );
}

/// Угол поворота спрайта под `dir` — спрайт нарисован «текущим на East»,
/// для остальных направлений его доворачивает.
fn dir_rotation(dir: Direction) -> f32 {
    match dir {
        Direction::East => 0.0,
        Direction::South => std::f32::consts::FRAC_PI_2,
        Direction::West => std::f32::consts::PI,
        Direction::North => -std::f32::consts::FRAC_PI_2,
    }
}

/// То же самое, но повёрнутое вокруг центра клетки — для лент: спрайт
/// нарисован вдоль East/West, для остальных направлений его доворачивает.
fn draw_sprite_rotated(sx: f32, sy: f32, tex: &Texture2D, dir: Direction) {
    draw_texture_ex(
        tex,
        sx,
        sy,
        WHITE,
        DrawTextureParams {
            dest_size: Some(vec2(TILE, TILE)),
            rotation: dir_rotation(dir),
            ..Default::default()
        },
    );
}

/// Нарисовать весь кадр.
pub fn draw(game: &Game, textures: &Textures) {
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
                draw_building(sx, sy, b, textures, tile.ore);
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

/// Красим стрелку-треугольник по тому же условию, по которому здание
/// реально продвигается в `Building::update` — зелёная, пока идёт работа,
/// красная, когда здание простаивает (нет сырья/руды или выход уже занят).
const C_WORKING: Color = GREEN;
const C_IDLE: Color = RED;

fn arrow_color(active: bool) -> Color {
    if active {
        C_WORKING
    } else {
        C_IDLE
    }
}

/// Отрисовка одного здания. Новый вид здания? Добавь сюда ещё одну ветку —
/// компилятор напомнит об этом, если сделаешь `match` исчерпывающим.
/// `has_ore` — есть ли под клеткой руда (нужно только буру).
fn draw_building(sx: f32, sy: f32, b: &Building, textures: &Textures, has_ore: bool) {
    let pad = 3.0;
    let inner = TILE - 2.0 * pad;
    match b {
        Building::Miner {
            dir,
            cooldown,
            output,
        } => {
            // 3 кадра анимации бура: показываем прогресс добычи (cooldown
            // считает от MINER_TIME вниз к 0), а не бег поwall-clock — так
            // кадр честно отражает состояние мира, а не время на экране.
            let progress = (1.0 - cooldown / MINER_TIME).clamp(0.0, 0.999);
            let frame = (progress * 3.0) as usize;
            draw_sprite(sx, sy, &textures.miner[frame]);
            let active = output.is_none() && has_ore;
            draw_arrow(sx, sy, *dir, arrow_color(active));
            if let Some(it) = output {
                draw_item(sx, sy, *it, textures);
            }
        }
        Building::Belt { dir, item } => {
            // 2 кадра анимации бегущей ленты, листаем по настенным часам —
            // в отличие от бура тут нечего «честно» отражать в состоянии
            // мира, лента просто едет, пока не на паузе. Отдельного спрайта
            // для поворота нет — на повороте доворачивается тот же прямой
            // кадр под нужный `dir`, как и на прямом участке.
            let frame = ((get_time() * BELT_ANIM_SPEED) as i64).rem_euclid(2) as usize;
            draw_sprite_rotated(sx, sy, &textures.belt[frame], *dir);
            if let Some(it) = item {
                draw_item(sx, sy, *it, textures);
            }
        }
        Building::Furnace {
            dir,
            input,
            progress,
            output,
        } => {
            // Огонь горит, пока в печи реально что-то плавится.
            let tex = if input.is_some() {
                &textures.furnace_on
            } else {
                &textures.furnace_off
            };
            draw_sprite(sx, sy, tex);
            let active = input.is_some() && output.is_none();
            draw_arrow(sx, sy, *dir, arrow_color(active));
            let frac = (progress / SMELT_TIME).clamp(0.0, 1.0);
            draw_rectangle(sx + pad, sy + TILE - pad - 4.0, inner * frac, 4.0, YELLOW);
            if let Some(it) = output {
                draw_item(sx, sy, *it, textures);
            } else if let Some(it) = input {
                draw_item(sx, sy, *it, textures);
            }
        }
        Building::Chest { items } => {
            draw_sprite(sx, sy, &textures.chest);
            draw_text(items.to_string(), sx + 6.0, sy + TILE - 8.0, 22.0, WHITE);
        }
        Building::Assembler {
            dir,
            input,
            progress,
            output,
        } => {
            draw_sprite(sx, sy, &textures.assembler);
            let active = input.is_some() && output.is_none();
            draw_arrow(sx, sy, *dir, arrow_color(active));
            let frac = (progress / SMELT_TIME).clamp(0.0, 1.0);
            draw_rectangle(sx + pad, sy + TILE - pad - 4.0, inner * frac, 4.0, YELLOW);
            if let Some(it) = output {
                draw_item(sx, sy, *it, textures);
            } else if let Some(it) = input {
                draw_item(sx, sy, *it, textures);
            }
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

/// Иконка предмета поверх здания — маленький спрайт в углу клетки, чтобы не
/// закрывать собой сам спрайт здания.
fn draw_item(sx: f32, sy: f32, item: Item, textures: &Textures) {
    let size = TILE * 0.4;
    let tex = item_texture(textures, item);
    draw_texture_ex(
        tex,
        sx + TILE - size - 2.0,
        sy + TILE - size - 2.0,
        WHITE,
        DrawTextureParams {
            dest_size: Some(vec2(size, size)),
            ..Default::default()
        },
    );
}
