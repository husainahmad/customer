package com.harmoni.pos.customer.adapter.in.web;

import com.harmoni.pos.customer.adapter.in.web.dto.CreateCustomerRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerResponse;
import com.harmoni.pos.customer.adapter.in.web.dto.PageResponse;
import com.harmoni.pos.customer.adapter.in.web.dto.UpdateCustomerRequest;
import com.harmoni.pos.customer.application.port.in.CreateCustomerCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.DeleteCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.PageResult;
import com.harmoni.pos.customer.application.port.in.SearchCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.SearchCustomersQuery;
import com.harmoni.pos.customer.application.port.in.UpdateCustomerCommand;
import com.harmoni.pos.customer.application.port.in.UpdateCustomerUseCase;
import com.harmoni.pos.customer.domain.model.Customer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Simple CRUD for customers — the foundation for sessions and chat.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Create, search and manage customers")
public class CustomerController {

    private final CreateCustomerUseCase createCustomerUseCase;
    private final GetCustomerUseCase getCustomerUseCase;
    private final SearchCustomerUseCase searchCustomerUseCase;
    private final UpdateCustomerUseCase updateCustomerUseCase;
    private final DeleteCustomerUseCase deleteCustomerUseCase;

    @Operation(summary = "Create a customer", description = "Creates a new customer. Phone must be unique.")
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = createCustomerUseCase.create(
                new CreateCustomerCommand(request.name(), request.phone(), request.email()));
        return ResponseEntity
                .created(URI.create("/api/v1/customers/" + customer.getId()))
                .body(CustomerResponse.from(customer));
    }

    @Operation(summary = "Get customer by id")
    @GetMapping("/{id}")
    public CustomerResponse getById(@Parameter(description = "Customer id", example = "1") @PathVariable long id) {
        return CustomerResponse.from(getCustomerUseCase.getCustomer(id));
    }

    @Operation(summary = "Search customers", description = "Search by name, phone or email with pagination.")
    @GetMapping
    public PageResponse<CustomerResponse> search(
            @Parameter(description = "Free-text search") @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        PageResult<Customer> result = searchCustomerUseCase.search(new SearchCustomersQuery(search, page, size));
        return PageResponse.of(result, CustomerResponse::from);
    }

    @Operation(summary = "Update a customer")
    @PutMapping("/{id}")
    public CustomerResponse update(
            @Parameter(description = "Customer id") @PathVariable long id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return CustomerResponse.from(updateCustomerUseCase.update(
                id, new UpdateCustomerCommand(request.name(), request.phone(), request.email())));
    }

    @Operation(summary = "Delete a customer", description = "Soft-deletes the customer — the record stays for history but won't show up in searches.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Customer id") @PathVariable long id) {
        deleteCustomerUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
