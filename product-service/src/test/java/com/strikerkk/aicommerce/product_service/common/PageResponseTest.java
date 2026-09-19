package com.strikerkk.aicommerce.product_service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResponse")
class PageResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductResponse product(long id) {
        ProductResponse response = new ProductResponse();
        response.setId(id);
        response.setName("Product " + id);
        return response;
    }

    @Test
    @DisplayName("the builder copies every field")
    void builderCopiesEveryField() {
        List<ProductResponse> content = List.of(product(1L), product(2L));

        PageResponse<ProductResponse> page = PageResponse.<ProductResponse>builder()
                .content(content)
                .pageNumber(1)
                .pageSize(2)
                .totalElements(6)
                .totalPages(3)
                .last(false)
                .build();

        assertThat(page.getContent()).isSameAs(content);
        assertThat(page.getPageNumber()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(6);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.isLast()).isFalse();
    }

    @Test
    @DisplayName("the no-args constructor produces neutral defaults")
    void noArgsConstructorProducesDefaults() {
        PageResponse<ProductResponse> page = new PageResponse<>();

        assertThat(page.getContent()).isNull();
        assertThat(page.getPageNumber()).isZero();
        assertThat(page.getPageSize()).isZero();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
        assertThat(page.isLast()).isFalse();
    }

    @Test
    @DisplayName("the all-args constructor keeps the declared field order")
    void allArgsConstructorKeepsFieldOrder() {
        List<ProductResponse> content = List.of(product(1L));

        PageResponse<ProductResponse> page = new PageResponse<>(content, 2, 5, 11L, 3, true);

        assertThat(page.getContent()).isSameAs(content);
        assertThat(page.getPageNumber()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(5);
        assertThat(page.getTotalElements()).isEqualTo(11L);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.isLast()).isTrue();
    }

    @Test
    @DisplayName("holds an empty page without turning the content into null")
    void holdsAnEmptyPage() {
        PageResponse<ProductResponse> page = PageResponse.<ProductResponse>builder()
                .content(Collections.emptyList())
                .pageNumber(0)
                .pageSize(10)
                .totalElements(0)
                .totalPages(0)
                .last(true)
                .build();

        assertThat(page.getContent()).isNotNull().isEmpty();
        assertThat(page.isLast()).isTrue();
    }

    @Test
    @DisplayName("serialises every pagination attribute")
    void serialisesEveryAttribute() throws Exception {
        String json = objectMapper.writeValueAsString(PageResponse.<ProductResponse>builder()
                .content(List.of(product(1L)))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build());

        assertThat(json)
                .contains("\"content\"")
                .contains("\"pageNumber\":0")
                .contains("\"pageSize\":10")
                .contains("\"totalElements\":1")
                .contains("\"totalPages\":1")
                .contains("\"last\":true");
    }

    @Test
    @DisplayName("every setter is available")
    void settersAreAvailable() {
        PageResponse<ProductResponse> page = new PageResponse<>();

        page.setContent(List.of(product(3L)));
        page.setPageNumber(4);
        page.setPageSize(25);
        page.setTotalElements(101L);
        page.setTotalPages(5);
        page.setLast(true);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getPageNumber()).isEqualTo(4);
        assertThat(page.getPageSize()).isEqualTo(25);
        assertThat(page.getTotalElements()).isEqualTo(101L);
        assertThat(page.getTotalPages()).isEqualTo(5);
        assertThat(page.isLast()).isTrue();
    }
}

