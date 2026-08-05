package com.graphics.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.mod.ModId;
import com.rustorio.mod.SkippedMod;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Что игрок читает на первом экране, когда какой-то мод не загрузился. Раньше он не читал ничего:
 * сломанный мод ронял игру целиком, до окна, и единственным сообщением был стек-трейс в консоли.
 */
class MenuStatusTest {

    @Test
    void nothingIsSaidWhenEveryModLoaded() {
        assertNull(MenuStatus.skippedMods(List.of()), "без пропущенных модов строке статуса взяться неоткуда");
    }

    @Test
    void theSkippedModIsNamedSoThePlayerKnowsWhichFolderToRemove() {
        String status = MenuStatus.skippedMods(List.of(new SkippedMod(new ModId("broken_mod"), "some long reason")));

        assertEquals("1 mod skipped: broken_mod — see the log for why.", status);
    }

    @Test
    void aLongListIsCutSoItStillFitsOneRow() {
        List<SkippedMod> many = List.of(
                new SkippedMod(new ModId("mod_a"), "reason"),
                new SkippedMod(new ModId("mod_b"), "reason"),
                new SkippedMod(new ModId("mod_c"), "reason"),
                new SkippedMod(new ModId("mod_d"), "reason"),
                new SkippedMod(new ModId("mod_e"), "reason"));

        String status = MenuStatus.skippedMods(many);

        assertTrue(status != null && status.startsWith("5 mods skipped: mod_a, mod_b, mod_c and 2 more"), status);
    }
}
