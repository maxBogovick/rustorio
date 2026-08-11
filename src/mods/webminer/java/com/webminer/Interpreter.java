package com.webminer;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.EditableBuilding;
import com.rustorio.domain.building.FieldSpec;
import com.rustorio.domain.building.FieldType;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.SettlesEachTick;
import com.rustorio.domain.building.TickContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A belt-transparent pass-through, same "hold one tick, then push it on" discipline as {@link
 * Monitor}, which reads the response body fetched behind it and shows the {@link #fields} the
 * player asked for, pulled out with {@link JsonFieldExtractor}.
 *
 * <p>Both halves of the player's interaction with this building go through an engine interface
 * rather than through engine code that knows this class: {@link EditableBuilding} for typing the
 * field list, {@link InspectableBuilding} for showing what those fields currently hold. The
 * extraction itself used to live in {@code com.graphics.render.InspectionPanelLayout} — the
 * rendering layer parsed JSON on behalf of this archetype, which is both the wrong place and, since
 * the panel is rebuilt every frame, sixty parses a second.
 *
 * <p>Deliberately produces no new item type for "the parsed object" — the owner's own choice
 * (in-memory buffer, not a payload-carrying item redesign) means an interpreted result has nowhere
 * to ride the belt as data; it is shown, not transported. {@link #fields} is exactly the list the
 * player typed, in order — see {@link InterpreterState}'s own javadoc for why a {@link List}.
 */
public final class Interpreter implements Building, SettlesEachTick, EditableBuilding, InspectableBuilding {

    /**
     * The body {@link #extracted} was parsed from, and the result — the whole cache, because {@link
     * #inspectionDetails} runs on every frame the panel is open and a response body can be 8 KB.
     * Compared by value, not identity: {@link FetchService} hands back the same {@link String}
     * instance until the next fetch overwrites it, but a value comparison is what makes the cache
     * correct rather than merely usually-right, and a mismatch costs one parse either way.
     */
    private @Nullable String parsedBody;
    private Map<String, String> extracted = Map.of();

    private final Direction direction;
    /** Which sprite {@link #appearance} draws — injected rather than hardcoded, so a prototype reusing this archetype can ship its own art. */
    private final BuildingPrototype prototype;
    private @Nullable ItemType held;
    /** Same one-tick settle every other relay carries, so an item never crosses two cells in one tick. */
    private boolean arrivedThisTick;
    /** The player's chosen JSON field names — plain data, mutated by {@link #setFields}. */
    private List<String> fields;

    public Interpreter(Direction direction, BuildingPrototype prototype) {
        this(direction, null, List.of(), prototype);
    }

    /** Restore constructor — public because this archetype is registered from this mod's own jar, outside the engine. */
    public Interpreter(Direction direction, @Nullable ItemType held, List<String> fields, BuildingPrototype prototype) {
        this.direction = direction;
        this.held = held;
        this.fields = fields;
        this.prototype = prototype;
    }

    /** The player's remedy for "watching the wrong fields" — a typed list rather than a cycle, because the names come from someone else's JSON. */
    public void setFields(List<String> fields) {
        this.fields = fields;
        parsedBody = null; // the previous extraction answered a different question
    }

    /**
     * One {@code name: value} line per configured field, pulled out of whatever the cell behind
     * this one last fetched. Re-parses only when that body actually changed (see {@link
     * #parsedBody}); every other frame is a map read, which is what {@link InspectableBuilding}
     * asks of an implementation called at frame rate.
     */
    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        if (fields.isEmpty()) {
            return List.of("(no fields configured — click to edit)");
        }
        FetchService fetch = world.service(FetchService.KEY).orElse(null);
        String body = fetch == null ? null : fetch.lastBody(x - direction.dx(), y - direction.dy()).orElse(null);
        if (body == null) {
            return List.of("(no response seen behind this interpreter yet)");
        }
        if (!body.equals(parsedBody)) {
            extracted = JsonFieldExtractor.extract(body, fields);
            parsedBody = body;
        }
        List<String> lines = new ArrayList<>(extracted.size());
        for (Map.Entry<String, String> entry : extracted.entrySet()) {
            lines.add("  " + entry.getKey() + ": " + entry.getValue());
        }
        return lines;
    }

    /** Which JSON field names this interpreter pulls out — for the inspection panel/save. */
    public List<String> fields() {
        return fields;
    }

    @Override
    public List<FieldSpec> editableFields() {
        return List.of(new FieldSpec("Fields (comma-separated)", FieldType.TEXT));
    }

    @Override
    public List<String> currentFieldValues() {
        return List.of(String.join(",", fields));
    }

    @Override
    public void applyEdits(List<String> newValues) {
        setFields(parseFieldList(newValues.get(0)));
    }

    /** Splits on commas, trims whitespace, drops empty entries — a trailing comma or "a,,b" must not produce a blank field name to look up. */
    private static List<String> parseFieldList(String raw) {
        List<String> fields = new ArrayList<>();
        for (String field : raw.split(",")) {
            String trimmed = field.strip();
            if (!trimmed.isEmpty()) {
                fields.add(trimmed);
            }
        }
        return fields;
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (held != null) {
            return false;
        }
        held = item;
        arrivedThisTick = true;
        return true;
    }

    @Override
    public void clearArrivalMark() {
        arrivedThisTick = false;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        if (held == null || arrivedThisTick) {
            return;
        }
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
        }
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public BuildingStatus status() {
        return BuildingStatus.WORKING;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(prototype.texture());
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Interpreter(direction.rotate(), held, fields, prototype));
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public InterpreterState state() {
        return new InterpreterState(direction, held, fields);
    }
}
