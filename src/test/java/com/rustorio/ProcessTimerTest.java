package com.rustorio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Общий счётчик готовности {@link Furnace} и {@link Lab} — крутит голые тики, без домена. */
class ProcessTimerTest {

    @Test
    void staysFalseWhileCountingDown() {
        ProcessTimer timer = new ProcessTimer(3);
        assertFalse(timer.tick(3));
        assertFalse(timer.tick(3));
    }

    @Test
    void becomesTrueExactlyOnTheInitialTimeTick() {
        ProcessTimer timer = new ProcessTimer(2);
        assertFalse(timer.tick(2));
        assertTrue(timer.tick(2));
    }

    @Test
    void resetsToTheTimeGivenAtCompletion() {
        ProcessTimer timer = new ProcessTimer(1);
        assertTrue(timer.tick(5)); // готово сразу — и сброс на 5, а не обратно на 1

        assertFalse(timer.tick(5));
        assertFalse(timer.tick(5));
        assertFalse(timer.tick(5));
        assertFalse(timer.tick(5));
        assertTrue(timer.tick(5));
    }

    @Test
    void restoreSetsCooldownDirectlyForSaveLoad() {
        ProcessTimer timer = new ProcessTimer(10);
        timer.restore(1);

        assertEquals(1, timer.cooldown());
        assertTrue(timer.tick(7));
    }
}
