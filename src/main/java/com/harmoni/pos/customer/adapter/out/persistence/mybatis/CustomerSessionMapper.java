package com.harmoni.pos.customer.adapter.out.persistence.mybatis;

import com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity.CustomerSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * MyBatis mapper for the customer-session table.
 */
@Mapper
public interface CustomerSessionMapper {

    Optional<CustomerSessionEntity> findById(@Param("id") Long id);

    int insert(CustomerSessionEntity entity);

    int updateStatus(CustomerSessionEntity entity);
}
