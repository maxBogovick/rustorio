package com.rustorio.persist;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustorio.core.Direction;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;
import com.rustorio.model.Assembler;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Lab;
import com.rustorio.model.Miner;
import com.rustorio.model.Splitter;
import com.rustorio.model.UndergroundBelt;
import com.rustorio.model.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Читает {@link GameSnapshot} из JSON и восстанавливает его В СУЩЕСТВУЮЩУЮ игру.
 *
 * <p><b>Почему «в существующую», а не «создаёт новую».</b> Мир, камера и рендер уже связаны
 * ссылками; проще и надёжнее очистить текущий мир и заново населить его, чем пересобирать
 * весь граф объектов и переподключать рендер. Поэтому {@link #restore} мутирует переданную
 * {@link GameState} на месте.
 *
 * <p><b>Толерантный читатель.</b> Неизвестные поля в JSON не роняют загрузку
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES=false}): сейв, записанный будущей версией с лишними
 * полями, всё равно прочитается в части, которую эта версия понимает. Это стандартная
 * тактика совместимости «строгий писатель, снисходительный читатель».
 */
public final class LoadService {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** JSON → снимок. */
    public GameSnapshot fromJson(String json) {
        try {
            return JSON.readValue(json, GameSnapshot.class);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось разобрать снимок из JSON", e);
        }
    }

    /** Прочитать снимок из файла. */
    public GameSnapshot read(Path path) throws IOException {
        return fromJson(Files.readString(path));
    }

    /**
     * Восстановить снимок в переданную игру: очистить поле, расставить здания, вернуть баланс
     * и исследования.
     *
     * <p>Порядок важен: сперва баланс и исследования (числа), затем здания. Бур узнаёт про
     * руду сам — {@link World#place} сообщает ему это по координатам (руда детерминирована),
     * поэтому «есть ли руда» в снимке не хранится и не может разойтись с картой.
     */
    public void restore(GameState game, GameSnapshot snapshot) {
        // Принимаем ЛЮБУЮ версию от 1 до текущей: схема росла аддитивно, поэтому старый сейв
        // читается тем же кодом — недостающие поля предметов стали пустыми. Это и есть
        // миграция v1→v2: не преобразование, а совместимость. Версию из будущего (её поля нам
        // неизвестны) — отклоняем.
        if (snapshot.version() < 1 || snapshot.version() > GameSnapshot.SCHEMA_VERSION) {
            throw new IllegalStateException("неподдерживаемая версия сохранения: "
                    + snapshot.version() + " (поддерживаются 1.." + GameSnapshot.SCHEMA_VERSION
                    + ")");
        }

        // Баланс — как есть, без переигрывания апгрейдов.
        snapshot.balance().speed().forEach(game.balance()::setSpeed);
        game.balance().setBeltSlotsPerTick(snapshot.balance().beltSlotsPerTick());
        game.balance().setUndergroundReach(snapshot.balance().undergroundReach());

        // Исследования — очки и открытые технологии (эффекты НЕ применяются повторно).
        game.research().restore(snapshot.research().points(), snapshot.research().unlocked());

        // Поле: снести всё и населить заново. Здания — первыми, чтобы транспортные линии
        // успели собраться, а уже потом на них возвращается груз.
        World world = game.world();
        world.clear();
        for (BuildingDto dto : snapshot.buildings()) {
            world.place(dto.x(), dto.y(), fromDto(dto));
        }
        for (BeltItemDto item : snapshot.beltItems()) {
            world.restoreBeltItem(item.x(), item.y(), item.slot(), item.item());
        }

        // Мир заменён — стек отмен ссылается на прежние здания, очищаем его.
        game.resetHistory();
    }

    /**
     * Снимок здания → живое здание, вместе с его предметами (v2).
     *
     * <p>Поля предметов {@code @Nullable}: в сейве версии 1 их нет, и тогда здание собирается
     * пустым (ветки {@code != null ? ... :}). Так один и тот же код грузит и старый, и новый
     * формат — без развилки «если версия 1, то…».
     */
    private static Building fromDto(BuildingDto dto) {
        Direction dir = dto.dir() != null ? dto.dir() : Direction.EAST;
        return switch (dto.type()) {
            case MINER -> new Miner(dir, dto.minerOutput());
            case BELT -> Building.create(Tool.BELT, dir);
            case FURNACE -> dto.machine() != null
                    ? new Furnace(dir, dto.machine().stock(), dto.machine().ready())
                    : Building.create(Tool.FURNACE, dir);
            case CHEST -> new Chest(dto.amount());
            case ASSEMBLER -> dto.machine() != null
                    ? new Assembler(dir, dto.machine().stock(), dto.machine().ready())
                    : Building.create(Tool.ASSEMBLER, dir);
            case SPLITTER -> dto.splitter() != null
                    ? new Splitter(dir, dto.splitter().held(), dto.splitter().nextOutput())
                    : Building.create(Tool.SPLITTER, dir);
            case UNDERGROUND -> new UndergroundBelt(dir,
                    dto.underground() != null ? dto.underground() : List.of());
            case LAB -> dto.machine() != null
                    ? new Lab(dto.amount(), dto.machine().stock(), dto.machine().ready())
                    : new Lab(dto.amount());
        };
    }
}
