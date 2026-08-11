package com.petrochem;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.EventBus;
import com.rustorio.api.mod.ItemProducedEvent;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaSprites;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Codec;
import com.rustorio.domain.building.PowerSpec;
import com.rustorio.domain.building.TraitKey;
import com.rustorio.domain.building.Traits;
import com.rustorio.domain.building.VanillaCategories;
import com.rustorio.domain.building.VanillaPlacementRules;
import com.rustorio.domain.building.VanillaTraits;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Кодовая половина мода: регистрирует электрический крекер — здание, поведение которого данными не
 * выражается, — и считает произведённый пластик через шину событий.
 *
 * <p>JSON-половина того же мода лежит рядом в {@code content/} и грузится сама: код-мод не заменяет
 * данные, а дополняет их тем, чего данными не сказать.
 */
public final class PetrochemMod implements RustorioMod {

    public static final ContentId CRACKER_ID = ContentId.of("petrochem:electro_cracker");

    private static final ContentId OIL_SAND = ContentId.of("petrochem:oil_sand");
    private static final ContentId PLASTIC = ContentId.of("petrochem:plastic");
    private static final ContentId MECHANISM = ContentId.of("rustorio:mechanism");

    /** Сколько мощности крекер просит за каждый тик работы — вдвое больше бура, потому что и делает он больше. */
    private static final int POWER_DEMAND = 30;

    /**
     * Пишет состояние крекера простыми типами JDK и читает обратно. Ни одной аннотации Jackson в
     * моде нет и быть не может: слой сохранения сериализует то, что вернул этот кодек.
     */
    private static final Codec<ElectroCracker.CrackerState> CODEC = new Codec<>() {
        @Override
        public Object encode(ElectroCracker.CrackerState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("buffered", state.buffered());
            data.put("ticksLeft", state.ticksLeft());
            data.put("held", Codec.encodeItem(state.held()));
            return data;
        }

        @Override
        public ElectroCracker.CrackerState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new ElectroCracker.CrackerState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.requireField(data, "buffered"),
                    Codec.requireField(data, "ticksLeft"),
                    Codec.decodeItem(data.get("held"), items));
        }
    };

    /**
     * Общий на все крекеры счётчик пластика — обычное поле точки входа, а не статика: подписчик
     * события и здания получают один и тот же экземпляр, и он умирает вместе с модом. Насколько
     * широко «общий» — написано в javadoc самого счётчика.
     */
    private final PlasticCounter plasticCounter = new PlasticCounter();

    /**
     * Первый раунд: свой контент. Предметы этого же мода уже зарегистрированы — {@code content/}
     * читается до точки входа того же мода, — поэтому {@code peek} их видит. Именно {@code peek}, а
     * не {@code get}: реестры ещё не заморожены.
     */
    @Override
    public void registerContent(RegistrationContext context) {
        ItemType mechanism = require(context, MECHANISM);

        context.buildings().register(CRACKER_ID, new BuildingPrototype(
                CRACKER_ID,
                "Электрокрекер",
                new BuildingCost(mechanism, 12),
                // Через реестр, а не константой интерфейса: так крекер поедет на правиле, которое
                // мог заменить другой мод, — и ни один путь в имени не напечатан руками.
                context.placementRules().peek(VanillaPlacementRules.NEEDS_PASSABLE_TERRAIN).orElseThrow(),
                VanillaSprites.ASSEMBLER, // заимствованный спрайт: у мода пока нет своего PNG
                1, 1,
                0, 1, false,
                // Оба предмета читаются из реестра МИРА, а не запоминаются здесь: сейв, открытый в
                // другом мире, раздаёт другие экземпляры ItemType, а сравниваются они по личности.
                (self, direction, factory) -> new ElectroCracker(self, direction,
                        factory.items().get(OIL_SAND), factory.items().get(PLASTIC), plasticCounter),
                (self, decodedState, factory) -> {
                    ElectroCracker.CrackerState state = (ElectroCracker.CrackerState) decodedState;
                    return new ElectroCracker(self, state.direction(),
                            factory.items().get(OIL_SAND), factory.items().get(PLASTIC), plasticCounter,
                            state.buffered(), state.ticksLeft(), state.held());
                },
                CODEC,
                CRACKER_ID,
                null,
                Traits.of(traits())));
    }

    /** Потребность в мощности и вкладка панели строительства — обе черты те же самые, что читает JSON-загрузчик. */
    private static Map<TraitKey<?>, Object> traits() {
        Map<TraitKey<?>, Object> traits = new LinkedHashMap<>();
        traits.put(VanillaTraits.POWER, new PowerSpec(0, 0, POWER_DEMAND));
        traits.put(VanillaCategories.CATEGORY, VanillaCategories.PRODUCTION);
        return traits;
    }

    /** Подписка на события идёт после заморозки реестров — здесь уже нельзя ничего регистрировать. */
    @Override
    public void subscribeEvents(EventBus events) {
        events.subscribe(ItemProducedEvent.class, event -> {
            if (event.item().id().equals(PLASTIC)) {
                plasticCounter.increment();
            }
        });
    }

    private static ItemType require(RegistrationContext context, ContentId id) {
        return context.items().peek(id).orElseThrow(() -> new IllegalStateException(
                id + " не виден: объяви зависимость от мода, который его регистрирует"));
    }
}
