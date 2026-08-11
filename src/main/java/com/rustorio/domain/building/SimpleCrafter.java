package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Stock L2 machine: buffer one input kind → work N ticks (halved when {@link
 * BuildingPrototype#speedTech()} is unlocked) → hold one output → {@link
 * TickContext#offerForward}. The battery behind {@link com.rustorio.api.dsl.BuildingDsl#simpleCrafter}.
 *
 * <p>Power: when the prototype declares {@link PowerSpec#demand()} &gt; 0, each work tick draws it
 * the same way {@link Furnace} does — absent demand means the machine never asks (electricity stays
 * opt-in).
 */
public final class SimpleCrafter implements Building, InspectableBuilding {

    private final BuildingPrototype prototype;
    private final Direction direction;
    private final ItemType input;
    private final ItemType output;
    private final int workTicks;
    private final int inputMax;

    private int buffered;
    private int ticksLeft;
    private @Nullable ItemType held;
    private BuildingStatus status = BuildingStatus.NO_INPUT;

    public SimpleCrafter(BuildingPrototype prototype, Direction direction, ItemType input, ItemType output,
            int workTicks, int inputMax) {
        this.prototype = prototype;
        this.direction = direction;
        this.input = input;
        this.output = output;
        this.workTicks = workTicks;
        this.inputMax = inputMax;
    }

    public SimpleCrafter(BuildingPrototype prototype, Direction direction, ItemType input, ItemType output,
            int workTicks, int inputMax, int buffered, int ticksLeft, @Nullable ItemType held) {
        this(prototype, direction, input, output, workTicks, inputMax);
        this.buffered = buffered;
        this.ticksLeft = ticksLeft;
        this.held = held;
    }

    /** Create from a {@link SimpleCrafterSpec}, resolving items against {@code items}. */
    public static SimpleCrafter create(BuildingPrototype prototype, Direction direction, SimpleCrafterSpec spec,
            Registry<ItemType> items) {
        return new SimpleCrafter(prototype, direction, items.get(spec.input()), items.get(spec.output()),
                spec.workTicks(), spec.inputMax());
    }

    public static SimpleCrafter restore(BuildingPrototype prototype, State state, SimpleCrafterSpec spec,
            Registry<ItemType> items) {
        return new SimpleCrafter(prototype, state.direction(), items.get(spec.input()), items.get(spec.output()),
                spec.workTicks(), spec.inputMax(), state.buffered(), state.ticksLeft(), state.held());
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (!item.equals(input) || buffered >= inputMax) {
            return false;
        }
        buffered++;
        return true;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        if (held != null) {
            if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
                held = null;
                world.notifyProduced(output);
                status = BuildingStatus.WORKING;
            } else {
                status = BuildingStatus.OUTPUT_FULL;
            }
            return;
        }
        // In-progress batch (ticksLeft > 0) must keep going even after the input unit was consumed
        // into the timer — otherwise the next tick sees buffered==0 and aborts mid-craft.
        if (buffered == 0 && ticksLeft <= 0) {
            status = BuildingStatus.NO_INPUT;
            return;
        }
        PowerSpec power = prototype.power();
        if (power != null && power.demand() > 0 && !world.drawPower(x, y, power.demand())) {
            status = BuildingStatus.NO_POWER;
            return;
        }
        if (ticksLeft <= 0) {
            buffered--;
            ticksLeft = effectiveWorkTicks(world);
        }
        ticksLeft--;
        if (ticksLeft == 0) {
            held = output;
        }
        status = BuildingStatus.WORKING;
    }

    private int effectiveWorkTicks(TickContext world) {
        ContentId speedTech = prototype.speedTech();
        return speedTech == null ? workTicks : world.research().fasterIfUnlocked(speedTech, workTicks);
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(prototype.texture(), status);
    }

    @Override
    public BuildingStatus status() {
        return status;
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public State state() {
        return new State(direction, buffered, ticksLeft, held);
    }

    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        return List.of(
                "Buffer: " + buffered + "/" + inputMax,
                ticksLeft > 0 ? "Working: " + ticksLeft + " ticks left" : "Idle",
                "Makes: " + output.label());
    }

    /** Persistable snapshot — plain JDK types for the shared {@link #CODEC}. */
    public record State(Direction direction, int buffered, int ticksLeft, @Nullable ItemType held) {
    }

    /**
     * Shared codec for every SimpleCrafter-backed prototype. Spec (input/output/timings) lives on
     * the prototype registration, not in the save row — only live buffers are persisted.
     */
    public static final Codec<State> CODEC = new Codec<>() {
        @Override
        public Object encode(State state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("buffered", state.buffered());
            data.put("ticksLeft", state.ticksLeft());
            data.put("held", Codec.encodeItem(state.held()));
            return data;
        }

        @Override
        public State decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new State(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.requireField(data, "buffered"),
                    Codec.requireField(data, "ticksLeft"),
                    Codec.decodeItem(data.get("held"), items));
        }
    };
}
