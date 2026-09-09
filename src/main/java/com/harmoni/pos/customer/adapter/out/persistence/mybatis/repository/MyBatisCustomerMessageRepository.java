package com.harmoni.pos.customer.adapter.out.persistence.mybatis.repository;

import com.harmoni.pos.customer.adapter.out.persistence.mybatis.CustomerMessageMapper;
import com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity.CustomerMessageEntity;
import com.harmoni.pos.customer.application.port.out.CustomerMessageRepository;
import com.harmoni.pos.customer.domain.model.CustomerMessage;
import com.harmoni.pos.customer.domain.model.CustomerMessageRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MyBatis-backed implementation of the CustomerMessageRepository port - persists chat messages and reads session history.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisCustomerMessageRepository implements CustomerMessageRepository {

    private final CustomerMessageMapper messageMapper;

    @Override
    public CustomerMessage save(CustomerMessage message) {
        CustomerMessageEntity entity = CustomerMessageEntity.builder()
                .sessionId(message.getSessionId())
                .role(message.getRole().name())
                .message(message.getMessage())
                .build();
        messageMapper.insert(entity);
        return messageMapper.findById(entity.getId())
                .map(MyBatisCustomerMessageRepository::toDomain)
                .orElseThrow(() -> new IllegalStateException("Message row disappeared after insert: " + entity.getId()));
    }

    @Override
    public List<CustomerMessage> findBySessionId(long sessionId, int offset, int limit) {
        return messageMapper.findBySessionId(sessionId, offset, limit).stream()
                .map(MyBatisCustomerMessageRepository::toDomain)
                .toList();
    }

    @Override
    public long countBySessionId(long sessionId) {
        return messageMapper.countBySessionId(sessionId);
    }

    private static CustomerMessage toDomain(CustomerMessageEntity entity) {
        return new CustomerMessage(
                entity.getId(),
                entity.getSessionId(),
                CustomerMessageRole.valueOf(entity.getRole()),
                entity.getMessage(),
                entity.getCreatedAt());
    }
}
