package com.harmoni.pos.customer.adapter.out.persistence.mybatis;

import com.harmoni.pos.customer.adapter.out.persistence.mybatis.entity.CustomerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis mapper for the customer table.
 */
@Mapper
public interface CustomerMapper {

    Optional<CustomerEntity> findById(@Param("id") Long id);

    Optional<CustomerEntity> findByPhoneIncludingDeleted(@Param("phone") String phone);

    List<CustomerEntity> search(@Param("search") String search,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    long count(@Param("search") String search);

    int insert(CustomerEntity entity);

    int update(CustomerEntity entity);

    int deleteById(@Param("id") Long id);
}
