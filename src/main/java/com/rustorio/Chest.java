package com.rustorio;

/**
 * Ящик: стоит на карте и копит предметы, которые в него складывают буры-соседи.
 *
 * <p>Теперь ящик — это {@link Building}, как и бур. Своего поведения на тик у него нет (стоит
 * себе), поэтому {@link #tick} он не переопределяет — берёт пустую реализацию по умолчанию.
 * Всё, что он умеет, — принять предмет и помнить, сколько их накопилось.
 *
 * <p>Пока хранит просто число — сколько всего в нём лежит; какого сорта предметы, не различает
 * (у нас и сорт один — руда). Разделение по сортам появится, когда предметов станет несколько.
 */
public final class Chest implements Building {

    /** Сколько предметов лежит в ящике. */
    private int count;

    /** Принять один предмет от соседа. Ящик берёт что угодно, поэтому всегда {@code true}. */
    @Override
    public boolean accept(Item item) {
        count++;
        return true;
    }

    /** Сколько всего сейчас лежит. */
    public int count() {
        return count;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(Sprite.CHEST, count); // спрайт ящика + число-бейдж «сколько лежит»
    }

    @Override
    public BuildingType type() {
        return BuildingType.CHEST;
    }

    /** Состояние для сохранения: сколько предметов лежит. */
    @Override
    public String save() {
        return Integer.toString(count);
    }

    /** Воссоздать ящик из сохранённого состояния. */
    static Chest load(String data) {
        Chest chest = new Chest();
        chest.count = Integer.parseInt(data);
        return chest;
    }
}
