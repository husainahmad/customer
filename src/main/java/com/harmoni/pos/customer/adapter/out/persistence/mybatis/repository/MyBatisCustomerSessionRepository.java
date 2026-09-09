package com.harmoni.pos.customer.adapter.out.persistence.mybatis.repository;

import com.harmoni.pos.customer.adapter.out.persistence.mybatis.CustomerSessionMapper;
import com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity.CustomerSessionEntity;
import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.domain.model.CustomerSession;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import com.harmoni.pos.customer.domain.model.CustomerSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MyBatis-backed implementation of the CustomerSessionRepository port - persists and reads customer sessions.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisCustomerSessionRepository implements CustomerSessionRepository {

    private final CustomerSessionMapper sessionMapper;

    @Override
    public CustomerSession save(CustomerSession session) {
        CustomerSessionEntity entity = toEntity(session);
        sessionMapper.insert(entity);
        return reload(entity.getId());
    }

    @Override
    public Optional<CustomerSession> findById(long id) {
        return sessionMapper.findById(id).map(MyBatisCustomerSessionRepository::toDomain);
    }

    @Override
    public CustomerSession update(CustomerSession session) {
        sessionMapper.updateStatus(toEntity(session));
        return reload(session.getId());
    }

    private CustomerSession reload(long id) {
        return sessionMapper.findById(id)
                .map(MyBatisCustomerSessionRepository::toDomain)
                .orElseThrow(() -> new IllegalStateException("Session row disappeared after write: " + id));
    }

    private static CustomerSessionEntity toEntity(CustomerSession session) {
        return CustomerSessionEntity.builder()
                .id(session.getId())
                .customerId(session.getCustomerId())
                .sessionToken(session.getSessionToken())
                .source(session.getSource().name())
                .status(session.getStatus().name())
                .build();
    }

    private static CustomerSession toDomain(CustomerSessionEntity entity) {
        return new CustomerSession(
                entity.getId(),
                entity.getCustomerId(),
                entity.getSessionToken(),
                CustomerSessionSource.valueOf(entity.getSource()),
                CustomerSessionStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
