package com.harmoni.pos.customer.adapter.out.persistence.mybatis.repository;

import com.harmoni.pos.customer.adapter.out.persistence.mybatis.CustomerMapper;
import com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity.CustomerEntity;
import com.harmoni.pos.customer.application.port.out.CustomerRepository;
import com.harmoni.pos.customer.domain.model.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-backed implementation of the CustomerRepository port - maps entity rows to domain models and back.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisCustomerRepository implements CustomerRepository {

    private final CustomerMapper customerMapper;

    @Override
    public Customer save(Customer customer) {
        CustomerEntity entity = toEntity(customer);
        customerMapper.insert(entity);
        return reload(entity.getId());
    }

    @Override
    public Optional<Customer> findById(long id) {
        return customerMapper.findById(id).map(MyBatisCustomerRepository::toDomain);
    }

    @Override
    public Optional<Customer> findByPhone(String phone) {
        return customerMapper.findByPhoneIncludingDeleted(phone).map(MyBatisCustomerRepository::toDomain);
    }

    @Override
    public List<Customer> search(String search, int offset, int limit) {
        return customerMapper.search(search, offset, limit).stream()
                .map(MyBatisCustomerRepository::toDomain)
                .toList();
    }

    @Override
    public long count(String search) {
        return customerMapper.count(search);
    }

    @Override
    public Customer update(Customer customer) {
        customerMapper.update(toEntity(customer));
        return reload(customer.getId());
    }

    @Override
    public boolean deleteById(long id) {
        return customerMapper.deleteById(id) == 1;
    }

    private Customer reload(long id) {
        return customerMapper.findById(id)
                .map(MyBatisCustomerRepository::toDomain)
                .orElseThrow(() -> new IllegalStateException("Customer row disappeared after write: " + id));
    }

    private static CustomerEntity toEntity(Customer customer) {
        return CustomerEntity.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .build();
    }

    private static Customer toDomain(CustomerEntity entity) {
        return new Customer(
                entity.getId(),
                entity.getName(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
