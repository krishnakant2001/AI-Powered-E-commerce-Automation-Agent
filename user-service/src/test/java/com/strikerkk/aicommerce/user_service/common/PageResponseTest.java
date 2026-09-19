package com.strikerkk.aicommerce.user_service.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResponse")
class PageResponseTest {

    @Test
    @DisplayName("copies every pagination attribute from the Spring Data Page")
    void shouldCopyPaginationAttributes() {
        Page<String> page = new PageImpl<>(List.of("first", "second"), PageRequest.of(1, 2), 10);

        PageResponse<String> response = new PageResponse<>(page);

        assertThat(response.getContent()).containsExactly("first", "second");
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(10);
        assertThat(response.getTotalPages()).isEqualTo(5);
    }

    @Test
    @DisplayName("handles an empty page")
    void shouldHandleEmptyPage() {
        Page<String> page = new PageImpl<String>(Collections.emptyList(), PageRequest.of(0, 20), 0);

        PageResponse<String> response = new PageResponse<>(page);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("reports the last page correctly")
    void shouldReportLastPage() {
        Page<String> page = new PageImpl<>(List.of("only"), PageRequest.of(2, 3), 7);

        PageResponse<String> response = new PageResponse<>(page);

        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(3);
        assertThat(response.getTotalElements()).isEqualTo(7);
        assertThat(response.getTotalPages()).isEqualTo(3);
    }
}



