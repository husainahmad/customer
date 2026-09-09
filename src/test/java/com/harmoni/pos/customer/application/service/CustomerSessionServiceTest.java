package com.harmoni.pos.customer.application.service;

import com.harmoni.pos.customer.application.port.in.AddCustomerMessageCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionCommand;
import com.harmoni.pos.customer.application.port.out.CustomerMessageRepository;
import com.harmoni.pos.customer.application.port.out.CustomerRepository;
import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.domain.exception.CustomerNotFoundException;
import com.harmoni.pos.customer.domain.exception.CustomerSessionNotFoundException;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerMessageException;
import com.harmoni.pos.customer.domain.model.Customer;
import com.harmoni.pos.customer.domain.model.CustomerMessage;
import com.harmoni.pos.customer.domain.model.CustomerMessageRole;
import com.harmoni.pos.customer.domain.model.CustomerSession;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import com.harmoni.pos.customer.domain.model.CustomerSessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSessionServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerSessionRepository sessionRepository;

    @Mock
    private CustomerMessageRepository messageRepository;

    @InjectMocks
    private CustomerSessionService sessionService;

    private static CustomerSession session(long id, Long customerId, CustomerSessionStatus status) {
        return new CustomerSession(id, customerId, "token-" + id, CustomerSessionSource.TABLE_QR,
                status, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createAnonymousSession_success() {
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerSession result = sessionService.create(new CreateCustomerSessionCommand(null, CustomerSessionSource.TABLE_QR));

        assertThat(result.getCustomerId()).isNull();
        assertThat(result.getStatus()).isEqualTo(CustomerSessionStatus.OPEN);
        assertThat(result.getSessionToken()).hasSize(43).doesNotContain("+", "/", "=");
        verify(customerRepository, never()).findById(anyLong());
    }

    @Test
    void createIdentifiedSession_success() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(Customer.create("Ahmad", null, null)));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerSession result = sessionService.create(
                new CreateCustomerSessionCommand(1L, CustomerSessionSource.WEB_CHAT));

        assertThat(result.getCustomerId()).isEqualTo(1L);
        ArgumentCaptor<CustomerSession> captor = ArgumentCaptor.forClass(CustomerSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(CustomerSessionSource.WEB_CHAT);
    }

    @Test
    void createIdentifiedSession_unknownCustomer_throws() {
        when(customerRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.create(
                new CreateCustomerSessionCommand(42L, CustomerSessionSource.TABLE_QR)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void getSession_notFound_throws() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession(99L))
                .isInstanceOf(CustomerSessionNotFoundException.class);
    }

    @Test
    void closeSession_openSession_becomesClosed() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(10L, null, CustomerSessionStatus.OPEN)));
        when(sessionRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerSession result = sessionService.close(10L);

        assertThat(result.getStatus()).isEqualTo(CustomerSessionStatus.CLOSED);
        verify(sessionRepository).update(any());
    }

    @Test
    void closeSession_alreadyClosed_throws() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(10L, null, CustomerSessionStatus.CLOSED)));

        assertThatThrownBy(() -> sessionService.close(10L))
                .isInstanceOf(com.harmoni.pos.customer.domain.exception.InvalidCustomerSessionException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void addMessage_toOpenSession_success() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(10L, null, CustomerSessionStatus.OPEN)));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerMessage result = sessionService.addMessage(new AddCustomerMessageCommand(
                10L, CustomerMessageRole.USER, "Saya mau kopi susu 2"));

        assertThat(result.getMessage()).isEqualTo("Saya mau kopi susu 2");
        assertThat(result.getRole()).isEqualTo(CustomerMessageRole.USER);
    }

    @Test
    void addMessage_toClosedSession_throws() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(10L, null, CustomerSessionStatus.CLOSED)));

        assertThatThrownBy(() -> sessionService.addMessage(new AddCustomerMessageCommand(
                10L, CustomerMessageRole.USER, "hello")))
                .isInstanceOf(InvalidCustomerMessageException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void addMessage_nullRole_throws() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(10L, null, CustomerSessionStatus.OPEN)));

        assertThatThrownBy(() -> sessionService.addMessage(new AddCustomerMessageCommand(
                10L, null, "hello")))
                .isInstanceOf(InvalidCustomerMessageException.class)
                .hasMessageContaining("role");
    }

    @Test
    void getMessages_sessionMustExist() {
        when(sessionRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getMessages(77L, 0, 50))
                .isInstanceOf(CustomerSessionNotFoundException.class);
    }
}
