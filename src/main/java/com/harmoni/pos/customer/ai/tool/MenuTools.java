package com.harmoni.pos.customer.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.ai.tool.dto.CategorySearchToolResponse;
import com.harmoni.pos.customer.ai.tool.dto.CategoryToolResponse;
import com.harmoni.pos.customer.ai.tool.dto.ProductSearchToolResponse;
import com.harmoni.pos.customer.ai.tool.dto.ProductToolResponse;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import com.harmoni.pos.customer.domain.exception.MenuServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Answers anything about the menu — product search, categories, and prices.
 * <p>
 * This is the menu side of the AI toolbox. It talks to the Menu Service (8082)
 * so the assistant never has to guess product names, IDs, or prices.
 * The LLM decides <em>when</em> to call, this class decides <em>how</em> to fetch and format.
 * All endpoint paths come from {@link MenuServiceProperties} (i.e. application.yaml).
 * <p>
 * <strong>Tool responses are structured JSON containing authoritative identifiers.</strong>
 * The LLM must use these identifiers (productId, skuId, categoryId) for all subsequent operations.
 * Never invent or guess database identifiers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MenuTools {

    @Qualifier("menuRestClient")
    private final RestClient menuRestClient;

    private final MenuServiceProperties menuProps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- product search ---

    @Tool(description = """
            ALWAYS call this tool when the customer mentions a specific product or asks about a product's price/availability.

            NEVER guess or invent productId, skuId, categoryId, or price.
            NEVER claim a product is unavailable before calling this tool.
            ALWAYS use the identifiers returned by the Menu Service.

            The response contains structured JSON with:
            - productId: authoritative database ID for the product
            - skuId: authoritative database ID for the SKU (use for addToOrder)
            - name: product name
            - description: product description (if available)
            - price: price in IDR from the Menu Service
            - skus: list of available SKUs/variants with their own skuId and price

            Good calls:
            - "harga caramel macchiato" -> searchProductsByName("caramel macchiato")
            - "ada kopi tubruk?" -> searchProductsByName("kopi tubruk")
            - "cari americano" -> searchProductsByName("americano")

            Do NOT use this to show the full menu — use getCategoriesByBrand instead.
            """)
    public String searchProductsByName(
            @ToolParam(description = "Product name as the customer typed it, e.g. 'caramel macchiato'") String productName) {
        if (productName == null || productName.isBlank()) {
            return toJson(ProductSearchToolResponse.notFound("productName is required"));
        }
        String query = normalizeProductQuery(productName);
        String endpoint = menuProps.getEndpoints().getProductSearch();
        try {
            String json = menuRestClient.get()
                    .uri(uri -> uri.path(endpoint).queryParam("productName", query).build())
                    .retrieve().body(String.class);
            String result = formatProductsJson(json, query);
            if (!isProductNotFound(result)) {
                return result;
            }
            // Friendly fallback — the LLM sometimes includes extra words or a typo (machiato vs macchiato).
            // Try suffixes and individual tokens before giving up.
            String[] words = query.split("\\s+");
            if (words.length > 1) {
                // 1) suffixes: "kalau kopi tubruk" -> "kopi tubruk" -> "tubruk"
                for (int i = 1; i < words.length; i++) {
                    String suffix = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)).trim();
                    if (suffix.length() < 3) continue;
                    try {
                        String json2 = menuRestClient.get()
                                .uri(uri -> uri.path(endpoint).queryParam("productName", suffix).build())
                                .retrieve().body(String.class);
                        String result2 = formatProductsJson(json2, suffix);
                        if (!isProductNotFound(result2)) {
                            log.info("searchProductsByName suffix fallback '{}' -> hit for original '{}'", suffix, productName);
                            return result2;
                        }
                    } catch (Exception e2) {
                        log.warn("searchProductsByName suffix '{}' failed: {}", suffix, e2.getMessage());
                    }
                }
                // 2) single tokens — helps with typos, menu has "Machiato" but the customer writes "Macchiato"
                for (String token : words) {
                    if (token.length() < 3) continue;
                    try {
                        String json3 = menuRestClient.get()
                                .uri(uri -> uri.path(endpoint).queryParam("productName", token).build())
                                .retrieve().body(String.class);
                        String result3 = formatProductsJson(json3, token);
                        if (!isProductNotFound(result3)) {
                            log.info("searchProductsByName token fallback '{}' -> hit for original '{}'", token, productName);
                            return result3;
                        }
                    } catch (Exception e3) {
                        log.warn("searchProductsByName token '{}' failed: {}", token, e3.getMessage());
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("searchProductsByName failed name='{}': {}", query, e.getMessage());
            return toJson(ProductSearchToolResponse.error("unable to search products"));
        }
    }

    /**
     * Cleans a raw product query before it is sent to the Menu Service:
     * trims whitespace and strips trailing punctuation such as {@code ?}, {@code !}, or {@code .}.
     *
     * @param raw the product name as the customer typed it
     * @return the normalized search term
     */
    private String normalizeProductQuery(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("[\\?\\!\\.]+$", "").trim();
    }

    /**
     * True when the tool JSON is a "product not found" response.
     * <p>
     * The formatted tool JSON carries the {@code PRODUCT_NOT_FOUND} prefix inside a
     * JSON string, so we must inspect the parsed JSON rather than the raw text
     * (text-prefix sentinels would never match a JSON document).
     *
     * @param toolResponseJson the serialized {@link ProductSearchToolResponse}
     * @return {@code true} when the response has no products and a PRODUCT_NOT_FOUND error
     */
    private boolean isProductNotFound(String toolResponseJson) {
        if (toolResponseJson == null) return false;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(toolResponseJson);
            com.fasterxml.jackson.databind.JsonNode products = root.path("products");
            com.fasterxml.jackson.databind.JsonNode error = root.path("error");
            return (products.isMissingNode() || products.isNull() || products.isEmpty())
                    && error.asText("").startsWith(ToolConstants.PREFIX_PRODUCT_NOT_FOUND);
        } catch (Exception e) {
            return false;
        }
    }

    // --- categories ---

    @Tool(description = """
            ALWAYS call this tool when the customer asks to search for a category by name.

            NEVER guess or invent categoryId.
            ALWAYS use the categoryId returned by the Menu Service.

            The response contains structured JSON with:
            - categoryId: authoritative database ID for the category
            - name: category name
            - description: category description (if available)
            """)
    public String searchCategoriesByName(
            @ToolParam(description = "Category name as the customer typed it, e.g. 'Coffee'") String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return toJson(CategorySearchToolResponse.notFound("categoryName is required"));
        }
        String query = categoryName.trim();
        String endpoint = menuProps.getEndpoints().getCategorySearch();
        try {
            String json = menuRestClient.get()
                    .uri(uri -> uri.path(endpoint).queryParam("categoryName", query).build())
                    .retrieve().body(String.class);
            return formatCategoriesJson(json);
        } catch (Exception e) {
            log.warn("searchCategoriesByName failed name='{}': {}", query, e.getMessage());
            return toJson(CategorySearchToolResponse.error("unable to search categories"));
        }
    }

    /**
     * Raw JSON for the frontend category chips — not an LLM tool.
     * Used by the REST layer so Swagger shows proper JSON instead of the LLM-friendly text format.
     *
     * @throws MenuServiceUnavailableException when the Menu Service cannot be reached
     */
    public String getCategoriesByBrandRaw(Integer brandId) {
        int bid = brandId == null ? 1 : brandId;
        String endpoint = menuProps.getEndpoints().getCategoryByBrand();
        try {
            return menuRestClient.get().uri(endpoint, bid)
                    .retrieve().body(String.class);
        } catch (Exception e) {
            log.warn("getCategoriesByBrandRaw failed brandId={}: {}", bid, e.getMessage());
            throw new MenuServiceUnavailableException("unable to fetch categories for brand " + bid, e);
        }
    }

    @Tool(description = """
            ALWAYS call this tool when the customer wants to see the menu, categories, or asks "what's on the menu?".

            NEVER guess or invent categoryId.
            ALWAYS use the categoryId returned by the Menu Service.

            The response contains structured JSON with:
            - categoryId: authoritative database ID for the category
            - name: category name
            - description: category description (if available)

            This is the primary tool for showing the menu. Do not use searchProductsByName for this.
            """)
    public String getCategoriesByBrand() {
        return getCategoriesByBrand(1);
    }

    // Direct Java call helper — not a tool, so the LLM schema stays clean.
    public String getCategoriesByBrand(Integer brandId) {
        int bid = brandId == null ? 1 : brandId;
        String endpoint = menuProps.getEndpoints().getCategoryByBrand();
        try {
            String json = menuRestClient.get()
                    .uri(endpoint, bid)
                    .retrieve().body(String.class);
            return formatCategoriesJson(json);
        } catch (Exception e) {
            log.warn("getCategoriesByBrand failed brandId={}: {}", bid, e.getMessage());
            return toJson(CategorySearchToolResponse.error("unable to list categories"));
        }
    }

    @Tool(description = """
            ALWAYS call this tool when the customer wants to see products in a specific category.

            You MUST have a valid categoryId from getCategoriesByBrand or searchCategoriesByName first.
            NEVER guess or invent categoryId.

            The response contains structured JSON with:
            - productId: authoritative database ID for each product
            - skuId: authoritative database ID for the SKU (use for addToOrder)
            - name: product name
            - description: product description (if available)
            - price: price in IDR from the Menu Service
            - skus: list of available SKUs/variants with their own skuId and price
            """)
    public String getProductsByCategory(
            @ToolParam(description = "Category id from the Menu Service, e.g. 13 for Coffee") Integer categoryId) {
        if (categoryId == null) {
            return toJson(ProductSearchToolResponse.notFound("categoryId is required, call searchCategoriesByName or getCategoriesByBrand first"));
        }
        String priceEndpoint = menuProps.getEndpoints().getProductByCategoryPrice();
        String pagedEndpoint = menuProps.getEndpoints().getProductByCategoryPaged();
        try {
            String json = menuRestClient.get()
                    .uri(priceEndpoint, categoryId)
                    .header(ToolConstants.HEADER_X_USERNAME, ToolConstants.DEFAULT_USERNAME)
                    .retrieve().body(String.class);
            if (json == null) return toJson(ProductSearchToolResponse.notFound("no products for categoryId=" + categoryId));
            return formatProductsJson(json, "categoryId=" + categoryId);
        } catch (Exception e) {
            log.warn("getProductsByCategory price endpoint failed categoryId={}: {}", categoryId, e.getMessage());
            try {
                String first = menuRestClient.get()
                        .uri(uri -> uri.path(pagedEndpoint)
                                .queryParam("page", 1).queryParam("size", 100).queryParam("search", "")
                                .build(categoryId, 1))
                        .retrieve().body(String.class);
                if (first == null) return toJson(ProductSearchToolResponse.notFound("no products for categoryId=" + categoryId));
                return formatProductsJson(first, "categoryId=" + categoryId);
            } catch (Exception e2) {
                return toJson(ProductSearchToolResponse.error("unable to list products for categoryId=" + categoryId));
            }
        }
    }

    // --- JSON formatters for LLM tool responses ---

    /**
     * Converts a raw Menu Service category payload into a structured JSON tool
     * response. Handles root-array, {@code data}, and nested {@code data.data} formats.
     *
     * @param json the raw Menu Service response
     * @return a serialized {@link CategorySearchToolResponse}
     */
    private String formatCategoriesJson(String json) {
        try {
            List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllCategories(json, objectMapper);
            if (extractors.isEmpty()) return toJson(CategorySearchToolResponse.notFound("no categories found"));

            List<CategoryToolResponse> categories = extractors.stream()
                    .map(ext -> CategoryToolResponse.simple(ext.productId(), ext.name()))
                    .collect(Collectors.toList());

            return toJson(CategorySearchToolResponse.success(categories));
        } catch (Exception e) {
            log.warn("formatCategoriesJson failed: {}", e.getMessage());
            return toJson(CategorySearchToolResponse.error("invalid category data"));
        }
    }

    /**
     * Converts a raw Menu Service product payload into a structured JSON tool
     * response, preserving productId, skuId, description, price, and all SKUs.
     *
     * @param json    the raw Menu Service response
     * @param context a human-readable description used in the not-found message
     * @return a serialized {@link ProductSearchToolResponse}
     */
    private String formatProductsJson(String json, String context) {
        try {
            List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(json, objectMapper);
            if (extractors.isEmpty()) return toJson(ProductSearchToolResponse.notFound(context));

            List<ProductToolResponse> products = extractors.stream()
                    .map(JsonNodeExtractor::toProductToolResponse)
                    .collect(Collectors.toList());

            return toJson(ProductSearchToolResponse.success(products));
        } catch (Exception e) {
            log.warn("formatProductsJson failed context={}: {}", context, e.getMessage());
            return toJson(ProductSearchToolResponse.error("invalid product data"));
        }
    }

    /**
     * Serializes a tool response into the JSON string presented to the LLM.
     *
     * @param obj the DTO to serialize
     * @return the JSON string, or a MENU_SERVICE_ERROR document if serialization fails
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize tool response: {}", e.getMessage());
            return "{\"error\":\"MENU_SERVICE_ERROR: serialization failed\"}";
        }
    }
}