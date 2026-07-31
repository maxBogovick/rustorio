package com.rustorio.domain.building;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@link Codec}: proves the contract on a synthetic archetype (a plain {@code Point}, nothing to
 * do with any real building) before any real archetype gets one (E6-02) — round-trips through
 * {@code encode}/{@code decode}, and {@code encode} really does produce a plain {@code Map}, not a
 * Jackson-specific or domain-specific type. {@code Point} has no {@link ItemType} field of its own,
 * so this codec's {@code decode} ignores the {@link Registry} parameter — a real archetype's own
 * codec (E6-02) wouldn't have that luxury.
 */
class CodecTest {

    private record Point(int x, int y) {
    }

    private static final Codec<Point> POINT_CODEC = new Codec<>() {
        @Override
        public Object encode(Point state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("x", state.x());
            data.put("y", state.y());
            return data;
        }

        @Override
        public Point decode(Object data, Registry<ItemType> items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            return new Point((Integer) map.get("x"), (Integer) map.get("y"));
        }
    };

    @Test
    void decodeOfEncodeRoundTripsToAnEqualValue() {
        Point original = new Point(3, 4);

        Object encoded = POINT_CODEC.encode(original);

        assertEquals(original, POINT_CODEC.decode(encoded, VanillaItems.frozen()));
    }

    @Test
    void encodeProducesAPlainMapNotAJacksonOrDomainSpecificType() {
        Object encoded = POINT_CODEC.encode(new Point(1, 2));

        Map<?, ?> data = assertInstanceOf(Map.class, encoded);
        assertEquals(1, data.get("x"));
        assertEquals(2, data.get("y"));
    }
}
