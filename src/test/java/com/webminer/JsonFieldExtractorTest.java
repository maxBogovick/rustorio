package com.webminer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonFieldExtractor}: proves the three shapes {@code Interpreter}'s inspection-panel
 * display actually depends on — a present field, an absent one, and a body that isn't valid JSON at
 * all — each resolve to a DISPLAYABLE value rather than an exception, since the caller (building a
 * list of panel lines) has no good way to catch one partway through.
 */
class JsonFieldExtractorTest {

    @Test
    void presentFieldsAreExtractedInTheRequestedOrder() {
        Map<String, String> result = JsonFieldExtractor.extract(
                "{\"name\":\"ada\",\"age\":36}", List.of("age", "name"));

        assertEquals(List.of("age", "name"), List.copyOf(result.keySet()), "order must follow the requested field list, not the JSON's own key order");
        assertEquals("36", result.get("age"));
        assertEquals("ada", result.get("name"));
    }

    @Test
    void aFieldTheResponseDoesNotHaveIsReportedAsMissingRatherThanOmitted() {
        Map<String, String> result = JsonFieldExtractor.extract("{\"name\":\"ada\"}", List.of("name", "email"));

        assertEquals("ada", result.get("name"));
        assertEquals("(missing)", result.get("email"), "every requested field must appear in the result, even if the response doesn't have it");
    }

    @Test
    void bodyThatIsNotValidJsonProducesADisplayableValueInstead() {
        Map<String, String> result = JsonFieldExtractor.extract("not json at all", List.of("name"));

        assertEquals("(invalid JSON)", result.get("name"));
    }
}
