package com.harmoni.pos.customer.adapter.in.web;

import com.harmoni.pos.customer.application.port.in.CreateCustomerCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.DeleteCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.PageResult;
import com.harmoni.pos.customer.application.port.in.SearchCustomersQuery;
import com.harmoni.pos.customer.application.port.in.SearchCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.UpdateCustomerCommand;
import com.harmoni.pos.customer.application.port.in.UpdateCustomerUseCase;
import com.harmoni.pos.customer.domain.exception.CustomerNotFoundException;
import com.harmoni.pos.customer.domain.exception.DuplicateCustomerException;
import com.harmoni.pos.customer.domain.model.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCustomerUseCase createCustomerUseCase;

    @MockitoBean
    private GetCustomerUseCase getCustomerUseCase;

    @MockitoBean
    private SearchCustomerUseCase searchCustomerUseCase;

    @MockitoBean
    private UpdateCustomerUseCase updateCustomerUseCase;

    @MockitoBean
    private DeleteCustomerUseCase deleteCustomerUseCase;

    private static Customer sample() {
        return new Customer(1L, "Ahmad", "08123456789", "ahmad@example.com",
                Instant.parse("2026-08-23T06:00:00Z"), Instant.parse("2026-08-23T06:00:00Z"));
    }

    @Test
    void createCustomer_returns201WithLocationAndBody() throws Exception {
        when(createCustomerUseCase.create(any())).thenReturn(sample());

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmad","phone":"08123456789","email":"ahmad@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ahmad"))
                .andExpect(jsonPath("$.phone").value("08123456789"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createCustomer_duplicatePhone_returns409() throws Exception {
        when(createCustomerUseCase.create(any(CreateCustomerCommand.class)))
                .thenThrow(new DuplicateCustomerException("Phone already registered: 08123456789"));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmad","phone":"08123456789"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CUSTOMER"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void getCustomer_found_returns200() throws Exception {
        when(getCustomerUseCase.getCustomer(1L)).thenReturn(sample());

        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ahmad@example.com"));
    }

    @Test
    void getCustomer_notFound_returns404() throws Exception {
        when(getCustomerUseCase.getCustomer(42L)).thenThrow(new CustomerNotFoundException(42L));

        mockMvc.perform(get("/api/v1/customers/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void searchCustomers_returnsPagedResponse() throws Exception {
        when(searchCustomerUseCase.search(any(SearchCustomersQuery.class)))
                .thenReturn(new PageResult<>(List.of(sample()), 1, 0, 20));

        mockMvc.perform(get("/api/v1/customers")
                        .param("search", "ahmad")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Ahmad"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void updateCustomer_returns200() throws Exception {
        when(updateCustomerUseCase.update(eq(1L), any(UpdateCustomerCommand.class)))
                .thenReturn(new Customer(1L, "Ahmad Husain", "08123456789", null,
                        Instant.parse("2026-08-23T06:00:00Z"), Instant.parse("2026-08-23T07:00:00Z")));

        mockMvc.perform(put("/api/v1/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmad Husain","phone":"08123456789"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ahmad Husain"));
    }

    @Test
    void deleteCustomer_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/1"))
                .andExpect(status().isNoContent());

        verify(deleteCustomerUseCase).delete(1L);
    }

    @Test
    void createCustomer_blankName_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createCustomer_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmad","email":"bukan-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.email").exists());
    }
}
