package com.rustorio;

/** Снести здание в клетке — и откатить, вернув НА МЕСТО именно снесённый объект. */
public final class RemoveAction implements PlayerAction {

    private final int x;
    private final int y;

    /** Что снесли — запоминаем в {@link #apply}, чтобы вернуть в {@link #undo}. */
    private Building removed;

    public RemoveAction(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean apply(World world) {
        removed = world.removeBuilding(x, y);
        return removed != null;
    }

    @Override
    public void undo(World world) {
        // world.restore, не world.place: правила постройки уже были пройдены, когда здание
        // ставили в первый раз — переспрашивать их сейчас незачем (тот же приём, что и в
        // загрузке сохранения, урок 10).
        world.restore(x, y, removed);
    }
}
