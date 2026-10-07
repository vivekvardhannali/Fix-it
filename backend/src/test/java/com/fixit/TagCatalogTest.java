package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fixit.exception.BadRequestException;
import com.fixit.service.TagCatalog;

/** The catalog on its own (no Spring, no database). */
class TagCatalogTest {

    private final TagCatalog catalog = new TagCatalog(List.of("tech", "math", "code", "others"));

    @Test
    void holdsTheFourTagsInOrder() {
        assertThat(catalog.names()).containsExactly("tech", "math", "code", "others");
    }

    @Test
    void matchingIgnoresCaseAndSurroundingSpace_resultIsCanonical() {
        assertThat(catalog.requireKnown(List.of(" TECH ", "Math", "cOdE"))).containsExactly("tech", "math", "code");
        assertThat(catalog.canonicalOrNull("OTHERS")).isEqualTo("others");
        assertThat(catalog.canonicalOrNull("nope")).isNull();
        assertThat(catalog.canonicalOrNull(null)).isNull();
    }

    @Test
    void duplicatesCollapse_keepingTheFirstOrder() {
        assertThat(catalog.requireKnown(List.of("math", "TECH", "Math", "tech"))).containsExactly("math", "tech");
    }

    @Test
    void unknownAndBlankNamesAreRejectedWithAHelpfulMessage() {
        assertThatThrownBy(() -> catalog.requireKnown(List.of("tech", "physics")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown tag 'physics'. Choose from: tech, math, code, others");
        assertThatThrownBy(() -> catalog.requireKnown(List.of(" "))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> catalog.requireKnown(Arrays.asList("tech", null))).isInstanceOf(BadRequestException.class);
        assertThat(catalog.requireKnown(List.of())).isEmpty();
    }

    @Test
    void aVeryLongUnknownNameDoesNotFloodTheMessage() {
        assertThatThrownBy(() -> catalog.requireKnown(List.of("x".repeat(500))))
                .hasMessageContaining("…").satisfies(e -> assertThat(e.getMessage().length()).isLessThan(120));
    }

    @Test
    void theSetIsConfigurable_normalised_andMustNotBeEmpty() {
        assertThat(new TagCatalog(List.of("Alpha", " beta ", "ALPHA", "")).names()).containsExactly("alpha", "beta");
        assertThatThrownBy(() -> new TagCatalog(List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TagCatalog(List.of(" ", ""))).isInstanceOf(IllegalStateException.class);
    }
}
