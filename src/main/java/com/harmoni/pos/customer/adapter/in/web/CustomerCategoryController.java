package com.harmoni.pos.customer.adapter.in.web;

import com.harmoni.pos.customer.ai.tool.MenuTools;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Category browsing — proxied through customer:8084 so ai-order never hits menu:8082 directly.
 * <p>
 * The frontend uses this for the category chips shown on the first message.
 */
@RestController
@RequestMapping("/api/v1/customer-sessions/{sessionId}/categories")
@RequiredArgsConstructor
@Tag(name = "Menu — Categories", description = "Browse and search menu categories (proxied via customer service)")
public class CustomerCategoryController {

    private final MenuTools menuTools;

    @Operation(summary = "List categories", description = "Returns the categories for the default brand as raw JSON — ideal for rendering tappable chips on the frontend.")
    @GetMapping
    public String listCategories(@Parameter(description = "Customer session id — used to verify the session is still open", example = "42") @PathVariable long sessionId) {
        return menuTools.getCategoriesByBrandRaw(1);
    }

    @Operation(summary = "Search categories", description = "Find categories by name, e.g. 'Coffee' or 'Snack'. Uses the Menu Service under the hood.")
    @GetMapping("/search")
    public String searchCategories(
            @Parameter(description = "Customer session id") @PathVariable long sessionId,
            @Parameter(description = "Category name to search for", example = "Coffee") @RequestParam String categoryName) {
        return menuTools.searchCategoriesByName(categoryName);
    }
}
