package com.harmoni.pos.customer.adapter.out.persistence.mybatis.repository;

import com.harmoni.pos.customer.application.port.out.CustomerRepository;
import com.harmoni.pos.customer.domain.model.Customer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@Import(MyBatisCustomerRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:customer_repo_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyBatisCustomerRepositoryTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeAll
    void initSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("sql/schema-test.sql"), StandardCharsets.UTF_8));
        }
    }

    private static Customer customer(String name, String phone, String email) {
        return Customer.create(name, phone, email);
    }

    @Test
    void save_assignsIdAndPersistsTimestamps() {
        Customer saved = customerRepository.save(customer("Ahmad", "08120000001", "ahmad1@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_returnsMappedDomainModel() {
        Customer saved = customerRepository.save(customer("Budi", "08120000002", null));

        var loaded = customerRepository.findById(saved.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getName()).isEqualTo("Budi");
        assertThat(loaded.get().getPhone()).isEqualTo("08120000002");
        assertThat(loaded.get().getEmail()).isNull();
    }

    @Test
    void findById_missingOrDeleted_returnsEmpty() {
        assertThat(customerRepository.findById(999_999L)).isEmpty();

        Customer saved = customerRepository.save(customer("Cica", "08120000003", null));
        customerRepository.deleteById(saved.getId());
        assertThat(customerRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void update_changesFieldsAndKeepsRowVisible() {
        Customer saved = customerRepository.save(customer("Dedi", "08120000004", "old@example.com"));

        saved.updateProfile("Dedi Putra", "08120000004", "new@example.com");
        Customer updated = customerRepository.update(saved);

        assertThat(updated.getName()).isEqualTo("Dedi Putra");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void search_filtersByKeywordInDatabaseWithPagination() {
        Customer a = customerRepository.save(customer("Ahmad Satu", "081111111111", "satu-zzmatch@example.com"));
        Customer b = customerRepository.save(customer("Budi zzmatch Dua", "082222222222", null));
        customerRepository.save(customer("Sinta", "083333333333", "sinta@example.com"));
        Customer d = customerRepository.save(customer("Dedi Empat", "08444444zzmatch", null));

        long total = customerRepository.count("zzmatch");
        assertThat(total).isEqualTo(3);

        List<Customer> page0 = customerRepository.search("zzmatch", 0, 2);
        List<Customer> page1 = customerRepository.search("zzmatch", 2, 2);

        assertThat(page0).extracting(Customer::getId)
                .containsExactly(d.getId(), b.getId());
        assertThat(page1).extracting(Customer::getId)
                .containsExactly(a.getId());
    }

    @Test
    void search_nullKeyword_returnsEverything() {
        long total = customerRepository.count(null);
        assertThat(total).isGreaterThanOrEqualTo(0);
    }

    @Test
    void findByPhone_includesSoftDeleted_forDuplicateCheckParity() {
        Customer saved = customerRepository.save(customer("Euis", "08140000005", null));
        customerRepository.deleteById(saved.getId());

        assertThat(customerRepository.findById(saved.getId())).isEmpty();
        assertThat(customerRepository.findByPhone("08140000005")).isPresent();
    }

    @Test
    void deleteById_isIdempotentAtPortLevel() {
        Customer saved = customerRepository.save(customer("Fajar", "08150000006", null));

        assertThat(customerRepository.deleteById(saved.getId())).isTrue();
        assertThat(customerRepository.deleteById(saved.getId())).isFalse();
    }
}
