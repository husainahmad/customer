package com.harmoni.pos.customer.adapter.out.persistence.mybatis.repository;

import com.harmoni.pos.customer.application.port.out.CustomerMessageRepository;
import com.harmoni.pos.customer.application.port.out.CustomerRepository;
import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.domain.model.Customer;
import com.harmoni.pos.customer.domain.model.CustomerMessage;
import com.harmoni.pos.customer.domain.model.CustomerMessageRole;
import com.harmoni.pos.customer.domain.model.CustomerSession;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import com.harmoni.pos.customer.domain.model.CustomerSessionStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@Import({MyBatisCustomerRepository.class, MyBatisCustomerSessionRepository.class, MyBatisCustomerMessageRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:session_repo_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyBatisSessionAndMessageRepositoryTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerSessionRepository sessionRepository;

    @Autowired
    private CustomerMessageRepository messageRepository;

    @BeforeAll
    void initSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("sql/schema-test.sql"), StandardCharsets.UTF_8));
        }
    }

    private CustomerSession newOpenSession(Long customerId, String token) {
        return CustomerSession.create(customerId, CustomerSessionSource.TABLE_QR, token);
    }

    @Test
    void saveAnonymousSession_persistsAndReloadsEnums() {
        Customer saved = customerRepository.save(Customer.create("Ahmad", "08160000001", null));

        CustomerSession open = sessionRepository.save(newOpenSession(saved.getId(), "token-anon-001"));

        assertThat(open.getId()).isNotNull();
        assertThat(open.getCreatedAt()).isNotNull();

        var loaded = sessionRepository.findById(open.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getCustomerId()).isEqualTo(saved.getId());
        assertThat(loaded.get().getStatus()).isEqualTo(CustomerSessionStatus.OPEN);
        assertThat(loaded.get().getSource()).isEqualTo(CustomerSessionSource.TABLE_QR);
    }

    @Test
    void updateStatus_persistsClosedState() {
        CustomerSession open = sessionRepository.save(newOpenSession(null, "token-close-002"));

        open.close();
        CustomerSession updated = sessionRepository.update(open);

        assertThat(updated.getStatus()).isEqualTo(CustomerSessionStatus.CLOSED);
        assertThat(sessionRepository.findById(updated.getId()))
                .hasValueSatisfying(s -> assertThat(s.getStatus()).isEqualTo(CustomerSessionStatus.CLOSED));
    }

    @Test
    void saveMessage_requiresExistingSession_fkEnforced() {
        assertThatThrownBy(() -> messageRepository.save(
                CustomerMessage.create(999_999L, CustomerMessageRole.USER, "halo")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findBySessionId_ordersByCreatedAtAscIdAsc_withPagination() {
        CustomerSession session = sessionRepository.save(newOpenSession(null, "token-msg-003"));

        for (int i = 1; i <= 5; i++) {
            messageRepository.save(CustomerMessage.create(
                    session.getId(), i % 2 == 0 ? CustomerMessageRole.ASSISTANT : CustomerMessageRole.USER,
                    "message-" + i));
        }

        assertThat(messageRepository.countBySessionId(session.getId())).isEqualTo(5);

        List<CustomerMessage> firstPage = messageRepository.findBySessionId(session.getId(), 0, 2);
        List<CustomerMessage> secondPage = messageRepository.findBySessionId(session.getId(), 2, 2);

        assertThat(firstPage).extracting(CustomerMessage::getMessage)
                .containsExactly("message-1", "message-2");
        assertThat(secondPage).extracting(CustomerMessage::getMessage)
                .containsExactly("message-3", "message-4");
        assertThat(firstPage).allSatisfy(m -> assertThat(m.getCreatedAt()).isNotNull());
    }
}
