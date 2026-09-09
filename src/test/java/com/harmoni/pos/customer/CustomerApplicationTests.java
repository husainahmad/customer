package com.harmoni.pos.customer;

import com.harmoni.pos.customer.application.port.in.CreateCustomerCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.CreateCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerUseCase;
import com.harmoni.pos.customer.domain.model.Customer;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import com.harmoni.pos.customer.domain.model.CustomerSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:customer_ctx_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/schema-test.sql",
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.base-url=http://localhost:11434/v1",
        "harmoni.services.menu.base-url=http://127.0.0.1:8082",
        "harmoni.services.order.base-url=http://127.0.0.1:8083"
})
class CustomerApplicationTests {

    @Autowired
    private CreateCustomerUseCase createCustomerUseCase;

    @Autowired
    private GetCustomerUseCase getCustomerUseCase;

    @Autowired
    private CreateCustomerSessionUseCase createCustomerSessionUseCase;

    @Test
    void contextLoads_andEndToEndRoundTripWorks() {
        Customer customer = createCustomerUseCase.create(
                new CreateCustomerCommand("Ahmad", "08170000001", "ahmad@example.com"));
        assertThat(customer.getId()).isNotNull();

        assertThat(getCustomerUseCase.getCustomer(customer.getId()).getName()).isEqualTo("Ahmad");

        var session = createCustomerSessionUseCase.create(
                new CreateCustomerSessionCommand(customer.getId(), CustomerSessionSource.TABLE_QR));
        assertThat(session.getId()).isNotNull();
        assertThat(session.getStatus()).isEqualTo(CustomerSessionStatus.OPEN);
        assertThat(session.getSessionToken()).hasSize(43);
    }
}
