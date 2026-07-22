package com.example.connectedappliance.shared.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResponseTest {

    @Test
    void exposesExactlyFiveApprovedFieldsAndDefensivelyCopiesItems() {
        List<String> source = new ArrayList<>(List.of("first", "second"));

        PageResponse<String> page = new PageResponse<>(source, 2, 10, 22, 3);
        source.clear();

        assertThat(page.items()).containsExactly("first", "second");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(22);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(Arrays.stream(PageResponse.class.getRecordComponents())
                        .map(component -> component.getName()))
                .containsExactly("items", "page", "size", "totalElements", "totalPages");
        assertThatThrownBy(() -> page.items().add("third"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullItemsNullElementsAndInvalidMetadata() {
        assertThatThrownBy(() -> new PageResponse<>(null, 0, 20, 0, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PageResponse<>(Arrays.asList("valid", null), 0, 20, 1, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PageResponse<>(List.of(), -1, 20, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResponse<>(List.of(), 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResponse<>(List.of(), 0, 20, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResponse<>(List.of(), 0, 20, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
