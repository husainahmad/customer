package com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MyBatis row mapping of the customer-session table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSessionEntity {

    private Long id;
    private Long customerId;
    private String sessionToken;
    private String source;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
