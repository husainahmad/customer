package com.harmoni.pos.customer.ai.tool;

/**
 * Centralized string constants for AI tool responses and headers.
 * <p>
 * Single source of truth for sentinel prefixes, error codes, success codes,
 * and common header/username values used across MenuTools, CartTools, OrderTools,
 * and REST controllers that translate tool output.
 */
public final class ToolConstants {

    private ToolConstants() {}

    // --- Header / auth ---
    public static final String HEADER_X_USERNAME = "X-Username";
    public static final String DEFAULT_USERNAME = "Kasir1";
    public static final String DEFAULT_CUSTOMER_NAME = "AI Customer";

    // --- Sentinel prefixes (tool result format) ---
    public static final String PREFIX_PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND: ";
    public static final String PREFIX_CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND: ";
    public static final String PREFIX_CART_ERROR = "CART_ERROR: ";
    public static final String PREFIX_ORDER_ERROR = "ORDER_ERROR: ";
    public static final String PREFIX_MENU_SERVICE_ERROR = "MENU_SERVICE_ERROR: ";
    public static final String PREFIX_NO_PRODUCTS = "NO_PRODUCTS: ";
    public static final String PREFIX_MENU_DATA_ERROR = "MENU_DATA_ERROR: ";

    public static final String PREFIX_ITEM_ADDED = "ITEM_ADDED: ";
    public static final String PREFIX_ITEM_REMOVED = "ITEM_REMOVED: ";
    public static final String PREFIX_ORDER_CREATED = "ORDER_CREATED: ";
    public static final String PREFIX_CART_EMPTY = "CART_EMPTY";

    // --- Compound sentinel checks ---
    public static final String[] TOOL_SENTINEL_PREFIXES = {
        PREFIX_PRODUCT_NOT_FOUND,
        PREFIX_CATEGORY_NOT_FOUND,
        PREFIX_CART_ERROR,
        PREFIX_ORDER_ERROR,
        PREFIX_MENU_SERVICE_ERROR,
        PREFIX_NO_PRODUCTS,
        PREFIX_MENU_DATA_ERROR
    };

    // --- Convenience builders ---
    public static String productNotFound(String detail) {
        return PREFIX_PRODUCT_NOT_FOUND + detail;
    }

    public static String categoryNotFound(String detail) {
        return PREFIX_CATEGORY_NOT_FOUND + detail;
    }

    public static String cartError(String detail) {
        return PREFIX_CART_ERROR + detail;
    }

    public static String orderError(String detail) {
        return PREFIX_ORDER_ERROR + detail;
    }

    public static String menuServiceError(String detail) {
        return PREFIX_MENU_SERVICE_ERROR + detail;
    }

    public static String noProducts(String context) {
        return PREFIX_NO_PRODUCTS + context;
    }

    public static String menuDataError(String detail) {
        return PREFIX_MENU_DATA_ERROR + detail;
    }

    public static String itemAdded(long productId, int qty, int totalItems, String total) {
        return PREFIX_ITEM_ADDED + "productId=" + productId + " qty=" + qty + " totalItems=" + totalItems + " total=" + total;
    }

    public static String itemRemoved(long productId, int totalItems) {
        return PREFIX_ITEM_REMOVED + "productId=" + productId + " totalItems=" + totalItems;
    }

    public static String orderCreated(String resp) {
        return PREFIX_ORDER_CREATED + (resp != null ? resp : "");
    }

    public static String cartEmpty() {
        return PREFIX_CART_EMPTY;
    }

    public static String cartEmpty(String detail) {
        return PREFIX_CART_EMPTY + ": " + detail;
    }

    // --- Sentinel detection ---
    public static boolean isSentinel(String s) {
        if (s == null) return false;
        for (String p : TOOL_SENTINEL_PREFIXES) {
            if (s.startsWith(p)) return true;
        }
        return false;
    }
}