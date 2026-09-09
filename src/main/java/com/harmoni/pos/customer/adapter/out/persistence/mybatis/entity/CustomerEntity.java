package com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MyBatis row mapping of the customer table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntity {

    private Long id;
    private String name;
    private String phone;
    private String email;
    private Instant deletedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
