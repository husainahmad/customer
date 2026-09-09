package com.harmoni.pos.customer.application.port.out;

import com.harmoni.pos.customer.domain.model.Customer;

import java.util.List;
import java.util.Optional;

/**
 * Port out for persisting and querying customers.
 */
public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(long id);

    /**
     * Includes soft-deleted customers, mirroring the DB unique constraint
     * on phone (a deleted customer still reserves its phone).
     */
    Optional<Customer> findByPhone(String phone);

    List<Customer> search(String search, int offset, int limit);

    long count(String search);

    Customer update(Customer customer);

    boolean deleteById(long id);
}
