package com.harmoni.pos.customer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * External Menu Service (8082) configuration — base URL and all endpoint paths.
 * <p>
 * No URI is hardcoded in Java anymore; everything lives in {@code application.yaml}
 * under {@code harmoni.services.menu}. Changing an endpoint is just a config edit, no recompile.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "harmoni.services.menu")
public class MenuServiceProperties {

    /** Base URL for the Menu Service, e.g. http://127.0.0.1:8082 */
    private String baseUrl = "http://127.0.0.1:8082";

    /** Timeout in milliseconds for calls to the Menu Service */
    private int timeoutMs = 3000;

    private Endpoints endpoints = new Endpoints();

    @Getter
    @Setter
    public static class Endpoints {

        /** GET /api/v1/product/search?productName=... */
        private String productSearch = "/api/v1/product/search";

        /** GET /api/v1/product/{id} */
        private String productById = "/api/v1/product/{id}";

        /** GET /api/v1/category/search?categoryName=... */
        private String categorySearch = "/api/v1/category/search";

        /** GET /api/v1/category/brand/{brandId} */
        private String categoryByBrand = "/api/v1/category/brand/{brandId}";

        /** GET /api/v1/product/category/{id}/price — price-aware, needs X-Username header */
        private String productByCategoryPrice = "/api/v1/product/category/{id}/price";

        /** GET /api/v1/product/category/{categoryId}/{brandId}?page=&size=&search= — legacy fallback, no price */
        private String productByCategoryPaged = "/api/v1/product/category/{categoryId}/{brandId}";
    }
}
