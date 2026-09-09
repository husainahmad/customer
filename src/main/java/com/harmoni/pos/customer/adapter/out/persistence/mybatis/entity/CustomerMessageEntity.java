package com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MyBatis row mapping of the chat-message table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMessageEntity {

    private Long id;
    private Long sessionId;
    private String role;
    private String message;
    private Instant createdAt;
}
