package com.harmoni.pos.customer.adapter.out.persistence.mybatis;

import com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity.CustomerMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis mapper for the chat-message table.
 */
@Mapper
public interface CustomerMessageMapper {

    int insert(CustomerMessageEntity entity);

    Optional<CustomerMessageEntity> findById(@Param("id") Long id);

    List<CustomerMessageEntity> findBySessionId(@Param("sessionId") Long sessionId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    long countBySessionId(@Param("sessionId") Long sessionId);
}
