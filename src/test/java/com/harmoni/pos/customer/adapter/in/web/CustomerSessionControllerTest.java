package com.harmoni.pos.customer.adapter.in.web;

import com.harmoni.pos.customer.application.port.in.AddCustomerMessageCommand;
import com.harmoni.pos.customer.application.port.in.AddCustomerMessageUseCase;
import com.harmoni.pos.customer.application.port.in.CloseCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerMessagesUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.PageResult;
import com.harmoni.pos.customer.domain.exception.CustomerSessionNotFoundException;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerSessionException;
import com.harmoni.pos.customer.domain.model.CustomerMessage;
import com.harmoni.pos.customer.domain.model.CustomerMessageRole;
import com.harmoni.pos.customer.domain.model.CustomerSession;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import com.harmoni.pos.customer.domain.model.CustomerSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerSessionController.class)
class CustomerSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCustomerSessionUseCase createCustomerSessionUseCase;

    @MockitoBean
    private GetCustomerSessionUseCase getCustomerSessionUseCase;

    @MockitoBean
    private CloseCustomerSessionUseCase closeCustomerSessionUseCase;

    @MockitoBean
    private AddCustomerMessageUseCase addCustomerMessageUseCase;

    @MockitoBean
    private GetCustomerMessagesUseCase getCustomerMessagesUseCase;

    private static CustomerSession session(long id, Long customerId, CustomerSessionStatus status) {
        return new CustomerSession(id, customerId, "abc-token", CustomerSessionSource.TABLE_QR,
                status, Instant.parse("2026-08-23T06:00:00Z"), Instant.parse("2026-08-23T06:00:00Z"));
    }

    @Test
    void createAnonymousSession_returns201() throws Exception {
        when(createCustomerSessionUseCase.create(any(CreateCustomerSessionCommand.class)))
                .thenReturn(session(100L, null, CustomerSessionStatus.OPEN));

        mockMvc.perform(post("/api/v1/customer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"TABLE_QR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customer-sessions/100"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createIdentifiedSession_returns201WithCustomerId() throws Exception {
        when(createCustomerSessionUseCase.create(any(CreateCustomerSessionCommand.class)))
                .thenReturn(session(101L, 1L, CustomerSessionStatus.OPEN));

        mockMvc.perform(post("/api/v1/customer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1,"source":"WEB_CHAT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(1));
    }

    @Test
    void createSession_unknownSource_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/customer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"TELEPATHY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CUSTOMER_SESSION"));
    }

    @Test
    void getSession_found_returns200() throws Exception {
        when(getCustomerSessionUseCase.getSession(100L)).thenReturn(session(100L, 1L, CustomerSessionStatus.OPEN));

        mockMvc.perform(get("/api/v1/customer-sessions/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("abc-token"))
                .andExpect(jsonPath("$.source").value("TABLE_QR"));
    }

    @Test
    void getSession_notFound_returns404() throws Exception {
        when(getCustomerSessionUseCase.getSession(42L))
                .thenThrow(new CustomerSessionNotFoundException(42L));

        mockMvc.perform(get("/api/v1/customer-sessions/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_SESSION_NOT_FOUND"));
    }

    @Test
    void closeSession_returns200Closed() throws Exception {
        when(closeCustomerSessionUseCase.close(100L)).thenReturn(
                new CustomerSession(100L, null, "abc-token", CustomerSessionSource.TABLE_QR,
                        CustomerSessionStatus.CLOSED,
                        Instant.parse("2026-08-23T06:00:00Z"), Instant.parse("2026-08-23T07:00:00Z")));

        mockMvc.perform(post("/api/v1/customer-sessions/100/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void addMessage_returns201() throws Exception {
        when(addCustomerMessageUseCase.addMessage(any(AddCustomerMessageCommand.class)))
                .thenReturn(new CustomerMessage(500L, 100L, CustomerMessageRole.USER,
                        "Saya mau kopi susu 2", Instant.parse("2026-08-23T06:01:00Z")));

        mockMvc.perform(post("/api/v1/customer-sessions/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"USER","message":"Saya mau kopi susu 2"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void addMessage_invalidRole_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/customer-sessions/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"GUEST","message":"halo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CUSTOMER_MESSAGE"));
    }

    @Test
    void getMessages_returnsPagedConversationHistory() throws Exception {
        when(getCustomerMessagesUseCase.getMessages(100L, 0, 50)).thenReturn(new PageResult<>(
                List.of(new CustomerMessage(500L, 100L, CustomerMessageRole.USER, "halo",
                        Instant.parse("2026-08-23T06:01:00Z"))), 1, 0, 50));

        mockMvc.perform(get("/api/v1/customer-sessions/100/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value("halo"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
