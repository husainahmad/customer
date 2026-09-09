package com.harmoni.pos.customer.domain.model;

import com.harmoni.pos.customer.domain.exception.InvalidCustomerSessionException;

import java.util.Locale;

/**
 * Where a customer session originates — a table QR code, a web/AI chat,
 * WhatsApp, or the mobile POS app.
 */
public enum CustomerSessionSource {
    TABLE_QR,
    WEB_CHAT,
    WHATSAPP,
    MOBILE_APP,
    AI_CHAT;

    /**
     * Resolves a case-insensitive textual source.
     *
     * @param raw the source name
     * @return the matching source
     * @throws InvalidCustomerSessionException if {@code raw} does not match any source
     */
    public static CustomerSessionSource from(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidCustomerSessionException("Unknown session source: " + raw);
        }
    }
}
