package com.harmoni.pos.customer.adapter.in.web.advice;

import com.harmoni.pos.customer.domain.exception.MenuServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void menuServiceUnavailable_mapsTo503() {
        ResponseEntity<ApiErrorResponse> resp =
                handler.handleMenuServiceUnavailable(new MenuServiceUnavailableException("unable to fetch categories for brand 1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("MENU_SERVICE_UNAVAILABLE");
        assertThat(resp.getBody().message()).contains("unable to fetch categories for brand 1");
    }
}