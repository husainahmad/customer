package com.harmoni.pos.customer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * External Order Service (8083) configuration — base URL and endpoint paths.
 * <p>
 * Bind prefix: {@code harmoni.services.order}
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "harmoni.services.order")
public class OrderServiceProperties {

    /** Base URL for the Order Service, e.g. http://127.0.0.1:8083 */
    private String baseUrl = "http://127.0.0.1:8083";

    /** Timeout in milliseconds for calls to the Order Service */
    private int timeoutMs = 3000;

    /** Default store id sent with every order created via AI */
    private long storeId = 1;

    /** Default store-service-type id sent with every order created via AI */
    private long storeServiceTypesId = 1;

    /** Default X-Username header value for calls to the Order Service and Menu Service */
    private String defaultUsername = "Kasir1";

    private Endpoints endpoints = new Endpoints();

    @Getter
    @Setter
    public static class Endpoints {

        /** POST /api/v1/order — create order, PUT /api/v1/order — record payment */
        private String createOrder = "/api/v1/order";
    }
}
