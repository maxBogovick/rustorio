//! systems.rs — СИМУЛЯЦИЯ: как мир продвигается на один тик.
//!
//! «Система» — это функция вида `fn(&mut World)`: она читает и меняет данные
//! мира. Это мини-версия того, как устроены большие движки (ECS): данные
//! отдельно (world.rs), логика отдельно (здесь). Один шаг мира — это просто
//! конвейер систем, выполненных по порядку.
//!
//! Хочешь новое поведение мира (например, «руда истощается»)? Пишешь новую
//! систему-функцию и добавляешь её вызов в `step`. Всё.

use crate::types::Item;
use crate::world::World;

/// ОДИН шаг симуляции = конвейер систем по порядку.
/// Порядок важен: сперва здания «поработали», потом предметы поехали дальше.
pub fn step(world: &mut World, dt: f32) {
    run_machines(world, dt);
    move_items(world);
}

/// Система №1: каждое здание делает свою внутреннюю работу
/// (бур копает, печь плавит). Ленты и ящики здесь ничего не делают.
fn run_machines(world: &mut World, dt: f32) {
    for i in 0..world.tiles.len() {
        let ore = world.tiles[i].ore; // копируем bool ДО займа building
        if let Some(b) = &mut world.tiles[i].building {
            b.update(dt, ore);
        }
    }
}

/// Система №2: предметы едут на одну клетку вперёд.
///
/// Приём в две фазы — важнейший урок про borrow checker:
///   а) СНАЧАЛА собираем список передач, только ЧИТАЯ поле;
///   б) ПОТОМ применяем их, меняя поле.
/// Так мы обходим правило «нельзя одолжить две клетки `Vec` изменяемо разом»
/// и получаем честную «одновременную» передачу без артефактов порядка.
fn move_items(world: &mut World) {
    // (откуда, куда, что). `claimed[j]` — в клетку j кто-то уже отдаёт предмет
    // в этот тик; второму нельзя (иначе две ленты пропихнут два предмета в слот).
    let mut moves: Vec<(usize, usize, Item)> = Vec::new();
    let mut claimed = vec![false; world.tiles.len()];

    // Фаза (а): планируем (только чтение).
    for i in 0..world.tiles.len() {
        let Some(b) = &world.tiles[i].building else {
            continue;
        };
        let Some((item, dir)) = b.output() else {
            continue;
        };
        let Some(j) = world.neighbor(i, dir) else {
            continue;
        };
        if claimed[j] {
            continue;
        }
        if let Some(target) = &world.tiles[j].building {
            if target.can_accept(item) {
                moves.push((i, j, item));
                claimed[j] = true;
            }
        }
    }

    // Фаза (б): применяем (можно менять).
    for (i, j, item) in moves {
        if let Some(b) = &mut world.tiles[i].building {
            b.remove_output();
        }
        if let Some(b) = &mut world.tiles[j].building {
            b.accept(item);
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Тесты. Логика мира — чистая, поэтому её легко проверить без окна и мыши.
// Запуск: `cargo test`
// ─────────────────────────────────────────────────────────────────────
#[cfg(test)]
mod tests {
    use super::*;
    use crate::building::Building;
    use crate::config::TICK;
    use crate::types::{Direction, Item, Tool};

    /// Полная цепочка: бур на руде → лента → ящик. Через N тиков в ящике
    /// должен появиться хотя бы один предмет.
    #[test]
    fn miner_belt_chest_delivers_items() {
        let mut w = World::new(3, 1);
        w.tiles[0].ore = true; // руда под буром (в тесте задаём вручную)

        w.place(0, 0, Building::new(Tool::Miner, Direction::East));
        w.place(1, 0, Building::new(Tool::Belt, Direction::East));
        w.place(2, 0, Building::new(Tool::Chest, Direction::East));

        for _ in 0..40 {
            step(&mut w, TICK);
        }

        match &w.tile(2, 0).building {
            Some(Building::Chest { items }) => assert!(*items >= 1, "ящик пуст"),
            _ => panic!("на (2,0) не ящик"),
        }
    }

    /// Бур без руды под собой ничего не производит.
    #[test]
    fn miner_without_ore_produces_nothing() {
        let mut w = World::new(2, 1);
        w.place(0, 0, Building::new(Tool::Miner, Direction::East));
        w.place(1, 0, Building::new(Tool::Chest, Direction::East));

        for _ in 0..40 {
            step(&mut w, TICK);
        }

        match &w.tile(1, 0).building {
            Some(Building::Chest { items }) => assert_eq!(*items, 0),
            _ => panic!("на (1,0) не ящик"),
        }
    }

    /// Печь плавит руду в пластину и отдаёт её дальше.
    #[test]
    fn furnace_smelts_ore_and_hands_off() {
        let mut w = World::new(2, 1);
        w.place(0, 0, Building::new(Tool::Furnace, Direction::East));
        w.place(1, 0, Building::new(Tool::Chest, Direction::East));
        // Кладём руду в печь вручную (обычно это делает лента).
        if let Some(Building::Furnace { input, .. }) = &mut w.tiles[0].building {
            *input = Some(Item::IronOre);
        }

        for _ in 0..40 {
            step(&mut w, TICK);
        }

        match &w.tile(1, 0).building {
            Some(Building::Chest { items }) => assert!(*items >= 1, "печь не выдала пластину"),
            _ => panic!("на (1,0) не ящик"),
        }
    }

    /// Передача предмета — это ПЕРЕмещение, а не копирование: источник пустеет,
    /// приёмник наполняется, предмет не теряется и не дублируется.
    #[test]
    fn item_moves_exactly_once() {
        let mut w = World::new(2, 1);
        w.place(0, 0, Building::new(Tool::Belt, Direction::East));
        w.place(1, 0, Building::new(Tool::Belt, Direction::East));
        if let Some(Building::Belt { item, .. }) = &mut w.tiles[0].building {
            *item = Some(Item::IronOre);
        }

        step(&mut w, TICK);

        assert!(
            matches!(
                &w.tile(0, 0).building,
                Some(Building::Belt { item: None, .. })
            ),
            "источник должен опустеть"
        );
        assert!(
            matches!(
                &w.tile(1, 0).building,
                Some(Building::Belt {
                    item: Some(Item::IronOre),
                    ..
                })
            ),
            "приёмник должен получить ровно тот же предмет"
        );
    }

    /// Две ленты, целящиеся в одну клетку, за тик пропихнут РОВНО один предмет
    /// (защита `claimed`) — второй остаётся у себя, ничего не дублируется.
    #[test]
    fn two_belts_into_one_move_only_one() {
        let mut w = World::new(3, 1);
        w.place(0, 0, Building::new(Tool::Belt, Direction::East)); // → (1,0)
        w.place(1, 0, Building::new(Tool::Belt, Direction::East));
        w.place(2, 0, Building::new(Tool::Belt, Direction::West)); // → (1,0)
        if let Some(Building::Belt { item, .. }) = &mut w.tiles[0].building {
            *item = Some(Item::IronOre);
        }
        if let Some(Building::Belt { item, .. }) = &mut w.tiles[2].building {
            *item = Some(Item::IronPlate);
        }

        step(&mut w, TICK);

        let occupied = (0..3)
            .filter(|&x| {
                matches!(
                    &w.tile(x, 0).building,
                    Some(Building::Belt { item: Some(_), .. })
                )
            })
            .count();
        assert_eq!(occupied, 2, "предмет продублировался или потерялся");
        assert!(
            matches!(
                &w.tile(1, 0).building,
                Some(Building::Belt { item: Some(_), .. })
            ),
            "средняя лента должна получить один предмет"
        );
    }

    /// `neighbor` возвращает `None` на краю поля и корректный индекс внутри.
    #[test]
    fn neighbor_respects_edges() {
        let w = World::new(3, 2);
        assert_eq!(w.neighbor(0, Direction::West), None);
        assert_eq!(w.neighbor(0, Direction::North), None);
        assert_eq!(w.neighbor(0, Direction::East), Some(1));
        assert_eq!(w.neighbor(0, Direction::South), Some(3)); // (0,1) = 1*3+0
    }

    /// Повторная постановка не сбрасывает содержимое, а другой тип не затирает.
    #[test]
    fn place_protects_existing_building() {
        let mut w = World::new(1, 1);
        w.place(0, 0, Building::new(Tool::Belt, Direction::East));
        if let Some(Building::Belt { item, .. }) = &mut w.tiles[0].building {
            *item = Some(Item::IronOre);
        }
        // такая же лента поверх — предмет на месте
        w.place(0, 0, Building::new(Tool::Belt, Direction::East));
        assert!(matches!(
            &w.tile(0, 0).building,
            Some(Building::Belt { item: Some(_), .. })
        ));
        // другой тип поверх — существующее здание НЕ затёрто
        w.place(0, 0, Building::new(Tool::Miner, Direction::East));
        assert!(matches!(
            &w.tile(0, 0).building,
            Some(Building::Belt { .. })
        ));
    }
}
