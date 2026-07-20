package com.rustorio;

/**
 * Лента: держит ОДИН предмет и раз в тик подталкивает его к соседу СПРАВА.
 *
 * <p>Самый простой конвейер, какой только бывает: без выбора направления игроком (везёт всегда
 * в одну сторону, {@code +x}), без очереди из нескольких предметов сразу — только «занято» или
 * «пусто». Чтобы предмет проехал дальше одной клетки, ставь ленты подряд: каждая подхватывает
 * то, что выронила соседняя слева, и через тик передаёт дальше соседней справа.
 *
 * <p>Направление и повороты — тема отдельного будущего урока-улучшения; сейчас всё, что нужно
 * для работающего конвейера, — это ровный ряд лент слева направо.
 */
public final class Belt implements Building {

    /** Предмет, который лента сейчас везёт, или {@code null}, если пусто. */
    private Item held;

    @Override
    public boolean accept(Item item) {
        if (held != null) {
            return false;              // уже что-то везём — вторая порция пока не помещается
        }
        held = item;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (held == null) {
            return;                            // везти нечего
        }
        if (world.offerForward(x + 1, y, held)) {
            held = null;                       // сосед справа принял — освободились
        }
        // сосед занят или его нет — предмет остаётся ждать на месте до следующего тика
    }

    @Override
    public Appearance appearance() {
        return held == null ? Appearance.of(Sprite.BELT_EMPTY) : Appearance.of(Sprite.BELT_FULL);
    }

    @Override
    public BuildingType type() {
        return BuildingType.BELT;
    }

    /** Состояние для сохранения: что везём, или {@code "-"}, если пусто. */
    @Override
    public String save() {
        return held == null ? "-" : held.name();
    }

    /** Воссоздать ленту из сохранённого состояния. */
    static Belt load(String data) {
        Belt belt = new Belt();
        if (!data.equals("-")) {
            belt.held = Item.valueOf(data);
        }
        return belt;
    }
}
