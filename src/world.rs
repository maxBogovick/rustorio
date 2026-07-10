//! world.rs — ДАННЫЕ мира и запросы к ним.
//!
//! Мир — это плоский `Vec<Tile>`; клетку (x, y) находим по индексу
//! `y * width + x`. Здесь НЕТ симуляции (она в systems.rs) и НЕТ отрисовки
//! (она в render.rs). Только хранение + простые операции: поставить, убрать,
//! прочитать, найти соседа. Это «единый источник правды» об игровом поле.

use crate::building::Building;
use crate::types::Direction;

/// Одна клетка поля.
pub struct Tile {
    /// Есть ли под клеткой залежь железной руды (бур копает только тут).
    pub ore: bool,
    /// Здание на клетке (или пусто).
    pub building: Option<Building>,
}

/// Всё игровое поле.
pub struct World {
    pub width: i32,
    pub height: i32,
    /// `pub(crate)`: поле видно системам (systems.rs), но закрыто от внешнего
    /// мира. Это осознанный компромисс — системам нужен прямой доступ к данным.
    pub(crate) tiles: Vec<Tile>,
}

impl World {
    /// Создать поле и раскидать несколько залежей руды.
    pub fn new(width: i32, height: i32) -> Self {
        let mut tiles = Vec::with_capacity((width * height) as usize);
        for _ in 0..width * height {
            tiles.push(Tile {
                ore: false,
                building: None,
            });
        }

        let mut world = World {
            width,
            height,
            tiles,
        };

        // Круглые залежи руды: (центр_x, центр_y, радиус). Карта детерминированная.
        let patches = [(6, 5, 3), (9, 14, 3), (25, 6, 4), (28, 15, 3)];
        for (cx, cy, r) in patches {
            for y in (cy - r)..=(cy + r) {
                for x in (cx - r)..=(cx + r) {
                    let (dx, dy) = (x - cx, y - cy);
                    if dx * dx + dy * dy <= r * r && world.in_bounds(x, y) {
                        let i = world.idx(x, y);
                        world.tiles[i].ore = true;
                    }
                }
            }
        }
        world
    }

    /// Клетка в пределах поля?
    pub fn in_bounds(&self, x: i32, y: i32) -> bool {
        x >= 0 && y >= 0 && x < self.width && y < self.height
    }

    /// Индекс клетки в плоском массиве.
    fn idx(&self, x: i32, y: i32) -> usize {
        (y * self.width + x) as usize
    }

    /// Прочитать клетку (для отрисовки и запросов).
    ///
    /// # Panics
    /// Паникует, если `(x, y)` вне поля. Вызывающий обязан заранее проверить
    /// `in_bounds` (все текущие вызовы идут строго по валидному диапазону).
    pub fn tile(&self, x: i32, y: i32) -> &Tile {
        debug_assert!(self.in_bounds(x, y), "tile() вне границ: ({x}, {y})");
        &self.tiles[self.idx(x, y)]
    }

    /// Поставить здание. Безопасно к координатам вне поля (просто ничего не делает).
    ///
    /// Два правила защиты:
    /// - НЕ затираем здание ДРУГОГО типа (чтобы протаскивание ленты не сносило
    ///   случайно бур/печь — для смены типа сперва снеси клетку ПКМ);
    /// - НЕ пересоздаём точно такое же (тип+направление), иначе лента сбрасывала
    ///   бы предмет каждый кадр, пока держишь ЛКМ.
    pub fn place(&mut self, x: i32, y: i32, b: Building) {
        if !self.in_bounds(x, y) {
            return;
        }
        let i = self.idx(x, y);
        if let Some(existing) = &self.tiles[i].building {
            if std::mem::discriminant(existing) != std::mem::discriminant(&b) {
                return; // другой тип — не трогаем
            }
            if existing.same_kind(&b) {
                return; // ровно такое же — не пересоздаём
            }
        }
        self.tiles[i].building = Some(b);
    }

    /// Убрать здание с клетки. Безопасно к координатам вне поля.
    pub fn remove(&mut self, x: i32, y: i32) {
        if !self.in_bounds(x, y) {
            return;
        }
        let i = self.idx(x, y);
        self.tiles[i].building = None;
    }

    /// Индекс соседней клетки в направлении `dir`, если она в пределах поля.
    /// `pub(crate)`: этим пользуется systems.rs при передаче предметов.
    pub(crate) fn neighbor(&self, i: usize, dir: Direction) -> Option<usize> {
        let x = (i as i32) % self.width;
        let y = (i as i32) / self.width;
        let (dx, dy) = dir.delta();
        let (nx, ny) = (x + dx, y + dy);
        if self.in_bounds(nx, ny) {
            Some(self.idx(nx, ny))
        } else {
            None
        }
    }
}
