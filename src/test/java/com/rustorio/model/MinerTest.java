package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Поведение бура за тики. Чистая модель — без окна, без симуляции. */
class MinerTest {

    /** Контекст одного тика — длительностью ровно в TICK. */
    private static TickContext tick() {
        return new TickContext(Config.TICK);
    }

    @Test
    void minerOnOreDigsAfterEnoughTicks() {
        Miner miner = new Miner(Direction.EAST);
        miner.setOnOre(true);
        assertTrue(miner.outputItem().isEmpty());
        for (int i = 0; i < 10; i++) {
            miner.update(tick());
        }
        assertEquals(Optional.of(Item.IRON_ORE), miner.outputItem());
    }

    @Test
    void minerOffOreNeverDigs() {
        Miner miner = new Miner(Direction.EAST);
        miner.setOnOre(false);
        for (int i = 0; i < 10; i++) {
            miner.update(tick());
        }
        assertTrue(miner.outputItem().isEmpty(), "без руды копать нечего");
    }

    @Test
    void progressGrowsWithTicks() {
        Miner miner = new Miner(Direction.EAST);
        miner.setOnOre(true);
        assertEquals(0f, miner.progressFraction(), 1e-6f);
        miner.update(tick());
        assertTrue(miner.progressFraction() > 0f, "тик добавил прогресс добычи");
    }

    // ── Протокол передачи (L4) ────────────────────────────────────────

    /** Прогнать бур на руде до готовой руды на выходе. */
    private static Miner minerWithOre() {
        Miner miner = new Miner(Direction.EAST);
        miner.setOnOre(true);
        for (int i = 0; i < 10; i++) {
            miner.update(tick());
        }
        return miner;
    }

    @Test
    void minerExposesDugOreAsHandoff() {
        Miner miner = minerWithOre();                // дано: руда добыта
        assertEquals(Optional.of(new Handoff(Item.IRON_ORE, Direction.EAST)),
                miner.output(), "выход — руда в сторону взгляда бура");
    }

    @Test
    void minerDigsAgainAfterOutputRemoved() {
        Miner miner = minerWithOre();                // дано: выход занят, бур застыл
        miner.removeOutput();                        // когда: потребитель забрал руду
        assertTrue(miner.outputItem().isEmpty(), "выход освободился");
        for (int i = 0; i < 10; i++) {
            miner.update(tick());
        }
        assertEquals(Optional.of(Item.IRON_ORE), miner.outputItem(),
                "тогда: бур закрутился снова — это конец клиффхэнгера L3");
    }
}
