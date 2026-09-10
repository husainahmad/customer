package com.harmoni.pos.customer.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import com.harmoni.pos.customer.domain.exception.MenuServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuToolsTest {

    private MenuTools menuTools;
    private RestClient menuRestClient;
    private MenuServiceProperties menuProps;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        menuRestClient = mock(RestClient.class);
        menuProps = mock(MenuServiceProperties.class);
        MenuServiceProperties.Endpoints endpoints = new MenuServiceProperties.Endpoints();
        when(menuProps.getEndpoints()).thenReturn(endpoints);

        menuTools = new MenuTools(menuRestClient, menuProps);
    }

    // ---------------------------------------------------------------
    // Product search
    // ---------------------------------------------------------------

    @Test
    void productSearch_found_returnsStructuredJsonWithIds() {
        String payload = """
                {
                  "data": [
                    {
                      "id": 15,
                      "name": "Kopi Tubruk",
                      "description": "Traditional Indonesian coffee",
                      "skus": [
                        { "id": 27, "name": "Regular", "tierPrice": { "price": 7000 } }
                      ]
                    }
                  ]
                }
                """;
        stubProductSearch(payload);

        String result = menuTools.searchProductsByName("Kopi Tubruk");

        JsonNode root = parse(result);
        assertThat(root.get("products").isArray()).isTrue();
        assertThat(root.get("products")).hasSize(1);
        JsonNode p = root.get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
        assertThat(p.get("sku_id").asLong()).isEqualTo(27L);
        assertThat(p.get("name").asText()).isEqualTo("Kopi Tubruk");
        assertThat(p.get("description").asText()).isEqualTo("Traditional Indonesian coffee");
        assertThat(p.get("price").decimalValue()).isEqualByComparingTo("7000");
    }

    @Test
    void productSearch_notFound_returnsNotFound() {
        stubProductSearch("""
                { "data": [] }
                """);

        String result = menuTools.searchProductsByName("Non Existent Product");

        assertThat(result).contains("PRODUCT_NOT_FOUND");
        assertThat(result).doesNotContain("products");
    }

    // ---------------------------------------------------------------
    // Raw categories (frontend chip endpoint)
    // ---------------------------------------------------------------

    @Test
    void categoriesRaw_success_returnsRawPayload() {
        stubCategoryByBrand("{\"httpStatus\":200,\"data\":[{\"id\":13,\"name\":\"Coffee\"}]}");

        String result = menuTools.getCategoriesByBrandRaw(1);

        assertThat(result).isEqualTo("{\"httpStatus\":200,\"data\":[{\"id\":13,\"name\":\"Coffee\"}]}");
    }

    @Test
    void categoriesRaw_menuDown_throwsDomainException() {
        when(menuRestClient.get()).thenThrow(new RuntimeException("offline"));

        assertThatThrownBy(() -> menuTools.getCategoriesByBrandRaw(1))
                .isInstanceOf(MenuServiceUnavailableException.class)
                .hasMessageContaining("unable to fetch categories for brand 1");
    }

    @Test
    void productSearch_nullName_returnsNotFound() {
        String result = menuTools.searchProductsByName(null);

        assertThat(result).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    void productSearch_blankName_returnsNotFound() {
        String result = menuTools.searchProductsByName("   ");

        assertThat(result).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    void productSearch_productHasMultipleSkus_preservesAll() {
        String payload = """
                {
                  "data": [
                    {
                      "id": 15,
                      "name": "Kopi Tubruk",
                      "skus": [
                        { "id": 27, "name": "Regular", "tierPrice": { "price": 7000 } },
                        { "id": 28, "name": "Large", "tierPrice": { "price": 9000 } }
                      ]
                    }
                  ]
                }
                """;
        stubProductSearch(payload);

        String result = menuTools.searchProductsByName("Kopi Tubruk");

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("skus")).hasSize(2);
        assertThat(p.get("skus").get(0).get("sku_id").asLong()).isEqualTo(27L);
        assertThat(p.get("skus").get(1).get("sku_id").asLong()).isEqualTo(28L);
    }

    @Test
    void productSearch_fallbackPrice_directPriceOnProduct() {
        String payload = """
                {
                  "data": [ { "id": 2, "name": "Americano", "price": 12000 } ]
                }
                """;
        stubProductSearch(payload);

        String result = menuTools.searchProductsByName("Americano");

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("price").decimalValue()).isEqualByComparingTo("12000");
    }

    @Test
    void productSearch_nestedDataData() {
        String payload = """
                {
                  "data": {
                    "data": [
                      { "id": 15, "name": "Kopi Tubruk",
                        "skus": [ { "id": 27, "tierPrice": { "price": 7000 } } ] }
                    ]
                  }
                }
                """;
        stubProductSearch(payload);

        String result = menuTools.searchProductsByName("Kopi Tubruk");

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
    }

    // ---------------------------------------------------------------
    // Search fallback: suffix / token
    // ---------------------------------------------------------------

    @Test
    void productSearch_exactMatch_noFallback() {
        stubProductSearch("""
                { "data": [ { "id": 15, "name": "Kopi Tubruk", "skus": [ { "id": 27, "tierPrice": { "price": 7000 } } ] } ] }
                """);

        String result = menuTools.searchProductsByName("kopi tubruk");

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
        assertThat(result).doesNotContain("PRODUCT_NOT_FOUND");
    }

    @Test
    void productSearch_suffixFallback_findsProduct() {
        String notFound = """
                { "data": [] }
                """;
        String found = """
                {
                  "data": [ { "id": 15, "name": "Kopi Tubruk",
                              "skus": [ { "id": 27, "tierPrice": { "price": 7000 } } ] } ]
                }
                """;
        // 1st call: "kopi tubruk" (no hit) -> 2nd: suffix "tubruk" (hit)
        stubProductSearchSequence(notFound, notFound, found);

        String result = menuTools.searchProductsByName("kopi tubruk");

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
        assertThat(p.get("name").asText()).isEqualTo("Kopi Tubruk");
    }

    @Test
    void productSearch_tokenFallback_findsProduct() {
        String notFound = """
                { "data": [] }
                """;
        String found = """
                {
                  "data": [ { "id": 15, "name": "Kopi Tubruk",
                              "skus": [ { "id": 27, "tierPrice": { "price": 7000 } } ] } ]
                }
                """;
        // 1st: "susu kopi" (no hit) -> 2nd: suffix "kopi" (no hit) -> 3rd: token "susu" (hit)
        stubProductSearchSequence(notFound, notFound, notFound, found);

        String result = menuTools.searchProductsByName("susu kopi");

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
    }

    @Test
    void productSearch_minorSpellingVariation_usesTokenFallback() {
        String notFound = """
                { "data": [] }
                """;
        String found = """
                {
                  "data": [ { "id": 15, "name": "Machiato",
                              "skus": [ { "id": 27, "tierPrice": { "price": 18000 } } ] } ]
                }
                """;
        // Customer typed "caramel macchiato"; the menu stores "Machiato".
        // 1st: "caramel macchiato" (no hit) -> suffix "macchiato" (no hit) ->
        // token "caramel" (no hit) -> token "macchiato" (hit)
        stubProductSearchSequence(notFound, notFound, notFound, found);

        String result = menuTools.searchProductsByName("caramel macchiato");

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
        assertThat(p.get("name").asText()).isEqualTo("Machiato");
    }

    // ---------------------------------------------------------------
    // Categories
    // ---------------------------------------------------------------

    @Test
    void categorySearch_found_preservesCategoryId() {
        String payload = """
                {
                  "data": [
                    { "id": 13, "name": "Coffee" },
                    { "id": 14, "name": "Non Coffee" }
                  ]
                }
                """;
        stubCategorySearch(payload);

        String result = menuTools.searchCategoriesByName("Coffee");

        JsonNode root = parse(result);
        assertThat(root.get("categories")).hasSize(2);
        assertThat(root.get("categories").get(0).get("category_id").asLong()).isEqualTo(13L);
        assertThat(root.get("categories").get(0).get("name").asText()).isEqualTo("Coffee");
        assertThat(root.get("categories").get(1).get("category_id").asLong()).isEqualTo(14L);
    }

    @Test
    void categorySearch_notFound_returnsNotFound() {
        stubCategorySearch("""
                { "data": [] }
                """);

        String result = menuTools.searchCategoriesByName("Nonexistent");

        assertThat(result).contains("CATEGORY_NOT_FOUND");
    }

    @Test
    void categoriesByBrand_preservesCategoryIds() {
        String payload = """
                {
                  "data": [
                    { "id": 13, "name": "Coffee" }
                  ]
                }
                """;
        stubCategoryByBrand(payload);

        String result = menuTools.getCategoriesByBrand(1);

        JsonNode root = parse(result);
        assertThat(root.get("categories").get(0).get("category_id").asLong()).isEqualTo(13L);
        assertThat(root.get("categories").get(0).get("name").asText()).isEqualTo("Coffee");
    }

    // ---------------------------------------------------------------
    // Products by category
    // ---------------------------------------------------------------

    @Test
    void productsByCategory_returnsStructuredDataWithSkuId() {
        String payload = """
                {
                  "data": [
                    {
                      "id": 15,
                      "name": "Kopi Tubruk",
                      "skus": [ { "id": 27, "tierPrice": { "price": 7000 } } ]
                    }
                  ]
                }
                """;
        stubProductsByCategoryPrice(payload);

        String result = menuTools.getProductsByCategory(13);

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
        assertThat(p.get("sku_id").asLong()).isEqualTo(27L);
        assertThat(p.get("name").asText()).isEqualTo("Kopi Tubruk");
        assertThat(p.get("price").decimalValue()).isEqualByComparingTo("7000");
    }

    @Test
    void productsByCategory_nullCategoryId_returnsNotFound() {
        String result = menuTools.getProductsByCategory(null);

        assertThat(result).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    void productsByCategory_priceEndpointFallsBackToPagedEndpoint() {
        String payload = """
                {
                  "data": [
                    { "id": 15, "name": "Kopi Tubruk",
                      "skus": [ { "id": 27, "tierPrice": { "price": 7000 } } ] }
                  ]
                }
                """;
        // price endpoint throws, paged endpoint returns data
        RestClient.RequestHeadersUriSpec chain = chainFor(payload);
        when(menuRestClient.get()).thenThrow(new RuntimeException("offline"))
                .thenReturn(chain);

        String result = menuTools.getProductsByCategory(13);

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
    }

    // ---------------------------------------------------------------
    // Critical regression
    // ---------------------------------------------------------------

    @Test
    void criticalRegression_idsAndPriceNotLostInToolResponse() {
        String payload = """
                {
                  "data": [
                    {
                      "id": 15,
                      "name": "Kopi Tubruk",
                      "description": "Traditional Indonesian coffee",
                      "skus": [
                        { "id": 27, "name": "Regular", "tierPrice": { "price": 7000 } }
                      ]
                    }
                  ]
                }
                """;
        stubProductSearch(payload);

        String result = menuTools.searchProductsByName("Kopi Tubruk");

        JsonNode p = parse(result).get("products").get(0);
        assertThat(p.get("product_id").asLong()).isEqualTo(15L);
        assertThat(p.get("sku_id").asLong()).isEqualTo(27L);
        assertThat(p.get("price").decimalValue()).isEqualByComparingTo("7000");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private JsonNode parse(String s) {
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubProductSearch(String payload) {
        RestClient.RequestHeadersUriSpec chain = chainFor(payload);
        when(menuRestClient.get()).thenReturn(chain);
    }

    private void stubCategorySearch(String payload) {
        RestClient.RequestHeadersUriSpec chain = chainFor(payload);
        when(menuRestClient.get()).thenReturn(chain);
    }

    private void stubCategoryByBrand(String payload) {
        RestClient.RequestHeadersUriSpec chain = chainFor(payload);
        when(menuRestClient.get()).thenReturn(chain);
    }

    private void stubProductsByCategoryPrice(String payload) {
        RestClient.RequestHeadersUriSpec chain = chainFor(payload);
        when(menuRestClient.get()).thenReturn(chain);
    }

    /**
     * Stubs menuRestClient.get() to return a different payload on successive calls —
     * used to drive the suffix/token fallback. Every chain is fully stubbed BEFORE
     * the outer when(...) so Mockito never sees a nested stub.
     */
    private void stubProductSearchSequence(String first, String... rest) {
        List<RestClient.RequestHeadersUriSpec> chains = new java.util.ArrayList<>();
        chains.add(chainFor(first));
        for (String p : rest) chains.add(chainFor(p));
        when(menuRestClient.get()).thenReturn(chains.get(0),
                chains.subList(1, chains.size()).toArray(new RestClient.RequestHeadersUriSpec[0]));
    }

    /**
     * Builds and stubs a minimal RestClient GET chain that always returns
     * {@code payload} for .body(String.class). The whole chain is stubbed
     * BEFORE any outer when(...) is started, so Mockito never sees a nested stub.
     */
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec chainFor(String payload) {
        RestClient.RequestHeadersUriSpec get = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(get.uri(any(Function.class))).thenReturn(get);
        when(get.uri(any(String.class), any(Object[].class))).thenReturn(get);
        when(get.header(any(String.class), any(String.class))).thenReturn(get);
        when(get.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(payload);

        return get;
    }
}
