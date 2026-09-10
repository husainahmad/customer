package com.harmoni.pos.customer.adapter.in.web;

import com.harmoni.pos.customer.ai.tool.MenuTools;
import com.harmoni.pos.customer.ai.tool.ToolConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Product browsing — proxied through customer:8084 so ai-order never hits menu:8082 directly.
 * <p>
 * Keeps pricing consistent by always going through the price-aware Menu endpoint.
 */
@RestController
@RequestMapping("/api/v1/customer-sessions/{sessionId}/products")
@RequiredArgsConstructor
@Tag(name = "Menu — Products", description = "Browse and search menu products with prices (proxied via customer service)")
public class CustomerProductController {

    private final MenuTools menuTools;

    @Operation(summary = "List products by category", description = "Returns products for a category with real prices from the Menu Service price endpoint. Returns empty data array if nothing is found.")
    @GetMapping("/category/{categoryId}")
    public String getByCategory(
            @Parameter(description = "Customer session id") @PathVariable long sessionId,
            @Parameter(description = "Category id from the Menu Service", example = "13") @PathVariable Integer categoryId) {
        String result = menuTools.getProductsByCategory(categoryId);
        // Translate LLM-friendly sentinels to clean JSON for the REST frontend
        if (ToolConstants.isSentinel(result)) {
            return "{\"data\":[]}";
        }
        return result;
    }

    @Operation(summary = "Search products", description = "Search products by name with typo tolerance — e.g. 'machiato' will still find 'Caramel Machiato'.")
    @GetMapping("/search")
    public String search(
            @Parameter(description = "Customer session id") @PathVariable long sessionId,
            @Parameter(description = "Product name to search for", example = "caramel macchiato") @RequestParam String productName) {
        String result = menuTools.searchProductsByName(productName);
        if (ToolConstants.isSentinel(result)) {
            return "{\"data\":[]}";
        }
        return result;
    }
}
