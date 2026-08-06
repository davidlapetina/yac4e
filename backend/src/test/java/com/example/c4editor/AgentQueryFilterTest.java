package com.example.c4editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.c4editor.application.AgentQueryService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentQueryFilterTest {
    private static boolean tagsContain(Map<String, Object> metadata, List<String> tags) throws Exception {
        Method method = AgentQueryService.class.getDeclaredMethod("tagsContain", Map.class, List.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, metadata, tags);
    }

    private static Map<String, Object> classification(Map<String, Object> values) {
        return Map.of("classification", values);
    }

    @Test
    void tagFilterMatchesDeclaredTags() throws Exception {
        Map<String, Object> metadata = classification(Map.of("tags", List.of("code-detected", "critical")));

        assertTrue(tagsContain(metadata, List.of("code-detected")));
        assertTrue(tagsContain(metadata, List.of("CODE-DETECTED")));
        assertTrue(tagsContain(metadata, List.of("code-detected", "critical")));
    }

    @Test
    void tagFilterRequiresEveryRequestedTag() throws Exception {
        Map<String, Object> metadata = classification(Map.of("tags", List.of("code-detected")));

        assertFalse(tagsContain(metadata, List.of("code-detected", "missing")));
    }

    @Test
    void tagFilterDoesNotMatchOtherClassificationFields() throws Exception {
        // The domain, not the tags, contains "payments". A tag filter must not match on it.
        Map<String, Object> metadata = classification(Map.of("domain", "payments", "tags", List.of("code-detected")));

        assertFalse(tagsContain(metadata, List.of("payments")));
    }

    @Test
    void tagFilterDoesNotMatchPartialTagText() throws Exception {
        Map<String, Object> metadata = classification(Map.of("tags", List.of("production")));

        assertFalse(tagsContain(metadata, List.of("prod")));
    }

    @Test
    void tagFilterIsNullSafeAndEmptyFilterMatchesEverything() throws Exception {
        assertTrue(tagsContain(null, List.of()));
        assertTrue(tagsContain(null, null));
        assertTrue(tagsContain(Map.of(), List.of()));

        // A real tag filter against absent metadata must not throw, and must not match.
        assertFalse(tagsContain(null, List.of("code-detected")));
        assertFalse(tagsContain(Map.of(), List.of("code-detected")));
        assertFalse(tagsContain(classification(Map.of("domain", "payments")), List.of("code-detected")));
    }

    @Test
    void tagFilterIgnoresNonListTagValues() throws Exception {
        assertFalse(tagsContain(classification(Map.of("tags", "code-detected")), List.of("code-detected")));
    }

    @Test
    void tagFilterCountsDuplicatesOnce() throws Exception {
        Map<String, Object> metadata = classification(Map.of("tags", List.of("a", "a", "b")));

        assertTrue(tagsContain(metadata, List.of("a", "b")));
        assertEquals(true, tagsContain(metadata, List.of("b", "a")));
    }
}
