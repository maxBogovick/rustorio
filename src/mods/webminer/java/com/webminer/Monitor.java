package com.webminer;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.building.BeltState;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingImage;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.SettlesEachTick;
import com.rustorio.domain.building.TickContext;
import com.rustorio.domain.building.ViewableBuilding;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A belt-transparent pass-through — accepts one item, holds it exactly one tick (same {@link
 * SettlesEachTick} discipline the vanilla filter and splitter already use), then pushes it on —
 * which lets a player insert it between a {@link WebMiner} and whatever it feeds without changing
 * the chain's throughput at all.
 *
 * <p>What makes it a "monitor" rather than a plain belt is entirely on the READ side: clicking one
 * open shows the last response body fetched by whatever stands BEHIND it (opposite {@link
 * #direction} — the same "facing is output, behind is input" convention {@link BuildingPrototype}'s
 * own javadoc states). It holds no reference to that body itself; it asks {@link FetchService} for
 * the cell behind it at the moment the panel asks IT.
 *
 * <p>That answer used to be assembled by {@code com.graphics.render.InspectionPanelLayout}, in a
 * branch naming this class — which is why the rendering layer briefly had to know what a response
 * body was, and why a mod could not have shipped this archetype without editing the engine's own
 * renderer. {@link InspectableBuilding} is the door that replaced the branch.
 */
public final class Monitor implements Building, SettlesEachTick, InspectableBuilding, ViewableBuilding {

    /**
     * The last response {@link #preview} was built from, and the preview itself — cached because
     * {@link #inspectionDetails} runs on every frame the panel is open, and skimming an 8 KB page
     * sixty times a second to produce the same five lines is work nobody asked for. Compared by
     * value: {@link FetchService} hands back the same instance until the next fetch replaces it,
     * but a value comparison is what makes the cache correct rather than merely usually-right,
     * and a mismatch costs one skim either way. Same reasoning, and same shape, as {@link
     * Interpreter}'s own cache.
     */
    private @Nullable FetchResult previewedAttempt;
    private @Nullable FetchResult previewedSuccess;
    private List<String> preview = List.of(ResponsePreview.NOTHING_YET);

    /** The page {@link #decoded} was decoded from, compared by identity — {@link #image} explains why the decode is cached at all. */
    private @Nullable RenderedPage decodedPage;
    private @Nullable BuildingImage decoded;

    private final Direction direction;
    /** Which sprite {@link #appearance} draws — injected rather than hardcoded, so a prototype reusing this archetype can ship its own art. */
    private final BuildingPrototype prototype;
    private @Nullable ItemType held;
    /** Same one-tick settle every other relay carries, so an item never crosses two cells in one tick. */
    private boolean arrivedThisTick;

    public Monitor(Direction direction, BuildingPrototype prototype) {
        this(direction, null, prototype);
    }

    /** Restore constructor — public because this archetype is registered from this mod's own jar, outside the engine. */
    public Monitor(Direction direction, @Nullable ItemType held, BuildingPrototype prototype) {
        this.direction = direction;
        this.held = held;
        this.prototype = prototype;
    }

    /**
     * What the cell behind this one last fetched, summarised: what kind of thing came back and how
     * big it was, then the part of it worth reading — see {@link ResponsePreview}.
     *
     * <p>It used to be the body itself, one line, handed to the panel to wrap. That reads fine for
     * a JSON API, whose real fields sit at the front, and is useless for anything else: a monitor
     * watching a miner pointed at a web page showed {@code <!doctype html>}, {@code <meta charset>}
     * and three {@code <link rel="icon">} — six rows of the least informative part of the document.
     *
     * <p>Cheap by construction, which {@link InspectableBuilding} demands of a method called at
     * frame rate: a map lookup, and a skim only when the response actually changed.
     *
     * <p>No service in this world means no fetching has happened or can happen, which reads to the
     * player exactly the same as "nothing fetched yet" — so it says that rather than exposing that
     * a mod is half-wired.
     */
    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        FetchService fetch = world.service(FetchService.KEY).orElse(null);
        if (fetch == null) {
            return List.of(ResponsePreview.NOTHING_YET);
        }
        int behindX = x - direction.dx();
        int behindY = y - direction.dy();
        FetchResult attempt = fetch.lastAttempt(behindX, behindY).orElse(null);
        FetchResult success = fetch.lastSuccess(behindX, behindY).orElse(null);
        if (attempt == null && success == null) {
            return List.of(ResponsePreview.NOTHING_YET);
        }
        if (!Objects.equals(attempt, previewedAttempt) || !Objects.equals(success, previewedSuccess)) {
            preview = ResponsePreview.of(attempt, success);
            previewedAttempt = attempt;
            previewedSuccess = success;
        }
        return preview;
    }

    /**
     * The fetched page as pixels, decoded once per page rather than once per frame.
     *
     * <p>{@link ViewableBuilding} promises this is called only while a viewer is open, and asks for
     * the same instance back while nothing has changed — hence the cache: decoding a PNG sixty
     * times a second to hand over identical pixels would be pure waste, and re-uploading them to
     * the GPU behind it worse. {@link RenderedPage} explains why the page is kept encoded at all.
     */
    @Override
    public Optional<BuildingImage> image(TickContext world, int x, int y) {
        FetchService fetch = world.service(FetchService.KEY).orElse(null);
        if (fetch == null) {
            return Optional.empty();
        }
        RenderedPage page = fetch.lastSuccess(x - direction.dx(), y - direction.dy())
                .map(FetchResult::page)
                .orElse(null);
        if (page == null) {
            decodedPage = null;
            decoded = null;
            return Optional.empty();
        }
        if (page != decodedPage) {
            int[] pixels = PageRenderer.decode(page);
            decoded = pixels == null ? null : new BuildingImage(page.width(), page.height(), pixels);
            decodedPage = page;
        }
        return Optional.ofNullable(decoded);
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
        return Optional.of(new Monitor(direction.rotate(), held, prototype));
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public BeltState state() {
        return new BeltState(direction, held);
    }
}
