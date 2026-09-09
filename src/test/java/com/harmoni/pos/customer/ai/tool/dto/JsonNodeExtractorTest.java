package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.ai.tool.JsonNodeExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonNodeExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------------------------------------------------------------
    // Product extraction
    // ---------------------------------------------------------------

    @Test
    void product_search_kopiTubruk_isFoundWithIdAndSku() {
        String json = """
                {
                  "data": [
                    {
                      "id": 15,
                      "name": "Kopi Tubruk",
                      "description": "Traditional Indonesian coffee",
                      "skus": [
                        {
                          "id": 27,
                          "tierPrice": { "price": 7000 }
                        }
                      ]
                    }
                  ]
                }
                """;

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(json, objectMapper);

        assertThat(extractors).hasSize(1);
        JsonNodeExtractor p = extractors.get(0);
        assertThat(p.productId()).isEqualTo(15L);
        assertThat(p.skuId()).isEqualTo(27L);
        assertThat(p.name()).isEqualTo("Kopi Tubruk");
        assertThat(p.description()).isEqualTo("Traditional Indonesian coffee");
        assertThat(p.price()).isEqualByComparingTo("7000");
        assertThat(p.skus()).hasSize(1);
        ProductToolResponse resp = p.toProductToolResponse();
        assertThat(resp.productId()).isEqualTo(15L);
        assertThat(resp.skuId()).isEqualTo(27L);
        assertThat(resp.skus().get(0).skuId()).isEqualTo(27L);
    }

    @Test
    void product_multipleSkus_preservesAllSkus() {
        String json = """
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

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(json, objectMapper);

        assertThat(extractors).hasSize(1);
        JsonNodeExtractor p = extractors.get(0);
        assertThat(p.skus()).hasSize(2);
        assertThat(p.skus().get(0).skuId()).isEqualTo(27L);
        assertThat(p.skus().get(1).skuId()).isEqualTo(28L);
        assertThat(p.skus().get(0).price()).isEqualByComparingTo("7000");
        assertThat(p.skus().get(1).price()).isEqualByComparingTo("9000");
        // skuId() returns the first SKU for convenience
        assertThat(p.skuId()).isEqualTo(27L);
    }

    @Test
    void sku_containsTierPrice() {
        String json = """
                {
                  "data": [
                    {
                      "id": 1,
                      "name": "Latte",
                      "skus": [
                        { "id": 100, "name": "Regular", "tierPrice": { "price": "18000" } }
                      ]
                    }
                  ]
                }
                """;

        JsonNodeExtractor p = JsonNodeExtractor.extractAllProducts(json, objectMapper).get(0);
        assertThat(p.price()).isEqualByComparingTo("18000");
        assertThat(p.skus().get(0).price()).isEqualByComparingTo("18000");
    }

    @Test
    void product_containsFallbackPrice_directPrice() {
        String json = """
                {
                  "data": [
                    {
                      "id": 2,
                      "name": "Americano",
                      "price": 12000
                    }
                  ]
                }
                """;

        JsonNodeExtractor p = JsonNodeExtractor.extractAllProducts(json, objectMapper).get(0);
        assertThat(p.price()).isEqualByComparingTo("12000");
        assertThat(p.skus()).isNull();
    }

    @Test
    void sku_usesSkuTierPricesArrayPrice() {
        String json = """
                {
                  "data": [
                    {
                      "id": 3,
                      "name": "Cappuccino",
                      "skus": [
                        { "id": 200, "name": "Regular", "skuTierPrices": [ { "price": 16000 } ] }
                      ]
                    }
                  ]
                }
                """;

        JsonNodeExtractor p = JsonNodeExtractor.extractAllProducts(json, objectMapper).get(0);
        assertThat(p.price()).isEqualByComparingTo("16000");
        assertThat(p.skus().get(0).price()).isEqualByComparingTo("16000");
    }

    @Test
    void sku_usesTierPricesArrayPrice() {
        String json = """
                {
                  "data": [
                    {
                      "id": 4,
                      "name": "Mocha",
                      "skus": [
                        { "id": 300, "name": "Regular", "tierPrices": [ { "price": 20000 } ] }
                      ]
                    }
                  ]
                }
                """;

        JsonNodeExtractor p = JsonNodeExtractor.extractAllProducts(json, objectMapper).get(0);
        assertThat(p.price()).isEqualByComparingTo("20000");
    }

    @Test
    void nestedDataData_responseIsParsed() {
        String json = """
                {
                  "data": {
                    "data": [
                      {
                        "id": 15,
                        "name": "Kopi Tubruk",
                        "skus": [ { "id": 27, "tierPrice": { "price": 7000 } } ]
                      }
                    ]
                  }
                }
                """;

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(json, objectMapper);

        assertThat(extractors).hasSize(1);
        assertThat(extractors.get(0).productId()).isEqualTo(15L);
        assertThat(extractors.get(0).name()).isEqualTo("Kopi Tubruk");
    }

    @Test
    void rootArray_responseIsParsed() {
        String json = """
                [
                  { "id": 15, "name": "Kopi Tubruk" }
                ]
                """;

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(json, objectMapper);

        assertThat(extractors).hasSize(1);
        assertThat(extractors.get(0).productId()).isEqualTo(15L);
        assertThat(extractors.get(0).name()).isEqualTo("Kopi Tubruk");
    }

    @Test
    void emptyResult_returnsEmptyList() {
        String json = """
                { "data": [] }
                """;

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(json, objectMapper);

        assertThat(extractors).isEmpty();
    }

    @Test
    void nullResponse_returnsEmptyList() {
        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(null, objectMapper);

        assertThat(extractors).isEmpty();
    }

    @Test
    void productNotFound_returnsEmptyList() {
        String json = """
                { "httpStatus": 200, "data": [] }
                """;

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(json, objectMapper);

        assertThat(extractors).isEmpty();
    }

    // ---------------------------------------------------------------
    // Category extraction
    // ---------------------------------------------------------------

    @Test
    void category_found_preservesCategoryId() {
        String json = """
                {
                  "data": [
                    { "id": 13, "name": "Coffee" },
                    { "id": 14, "name": "Non Coffee" }
                  ]
                }
                """;

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllCategories(json, objectMapper);

        assertThat(extractors).hasSize(2);
        assertThat(extractors.get(0).productId()).isEqualTo(13L); // category id reuses the id field
        assertThat(extractors.get(0).name()).isEqualTo("Coffee");
        assertThat(extractors.get(1).productId()).isEqualTo(14L);
        assertThat(extractors.get(1).name()).isEqualTo("Non Coffee");
    }

    @Test
    void category_notFound_returnsEmpty() {
        String json = """
                { "data": [] }
                """;

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllCategories(json, objectMapper);

        assertThat(extractors).isEmpty();
    }

    // ---------------------------------------------------------------
    // Critical regression: identifiers are NOT lost on conversion
    // ---------------------------------------------------------------

    @Test
    void criticalRegression_identifiersSurviveMenuServiceToToolConversion() {
        String json = """
                {
                  "data": [
                    {
                      "id": 15,
                      "name": "Kopi Tubruk",
                      "description": "Traditional Indonesian coffee",
                      "skus": [
                        {
                          "id": 27,
                          "name": "Regular",
                          "tierPrice": { "price": 7000 }
                        }
                      ]
                    }
                  ]
                }
                """;

        List<JsonNodeExtractor> extractors = JsonNodeExtractor.extractAllProducts(json, objectMapper);

        ProductToolResponse resp = extractors.get(0).toProductToolResponse();

        assertThat(resp.productId()).isEqualTo(15L);
        assertThat(resp.skuId()).isEqualTo(27L);
        assertThat(resp.name()).isEqualTo("Kopi Tubruk");
        assertThat(resp.description()).isEqualTo("Traditional Indonesian coffee");
        assertThat(resp.price()).isEqualByComparingTo("7000");
        assertThat(resp.skus()).hasSize(1);
        assertThat(resp.skus().get(0).skuId()).isEqualTo(27L);
    }
}
