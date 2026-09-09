package com.harmoni.pos.customer.application.service;

import com.harmoni.pos.customer.application.port.in.CreateCustomerCommand;
import com.harmoni.pos.customer.application.port.in.SearchCustomersQuery;
import com.harmoni.pos.customer.application.port.in.UpdateCustomerCommand;
import com.harmoni.pos.customer.application.port.out.CustomerRepository;
import com.harmoni.pos.customer.domain.exception.CustomerNotFoundException;
import com.harmoni.pos.customer.domain.exception.DuplicateCustomerException;
import com.harmoni.pos.customer.domain.model.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private static Customer customer(long id, String name, String phone) {
        return new Customer(id, name, phone, null, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createCustomer_success() {
        when(customerRepository.findByPhone("08123456789")).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenReturn(customer(1L, "Ahmad", "08123456789"));

        Customer result = customerService.create(new CreateCustomerCommand("Ahmad", "08123456789", "ahmad@example.com"));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Ahmad");
    }

    @Test
    void createCustomer_duplicatePhone_throws() {
        when(customerRepository.findByPhone("08123456789"))
                .thenReturn(Optional.of(customer(9L, "Other", "08123456789")));

        assertThatThrownBy(() -> customerService.create(
                new CreateCustomerCommand("Ahmad", "08123456789", null)))
                .isInstanceOf(DuplicateCustomerException.class);
    }

    @Test
    void createCustomer_withoutPhone_skipsDuplicateCheck() {
        when(customerRepository.save(any())).thenReturn(customer(1L, "Anonymous", null));

        Customer result = customerService.create(new CreateCustomerCommand("Anonymous", null, null));

        assertThat(result.getPhone()).isNull();
        verify(customerRepository).save(any());
    }

    @Test
    void getCustomer_notFound_throws() {
        when(customerRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(42L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void getCustomer_found_returnsDomainModel() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "Ahmad", null)));

        Customer result = customerService.getCustomer(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void updateCustomer_success() {
        Customer existing = customer(1L, "Ahmad", "08123456789");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.findByPhone("08123456790")).thenReturn(Optional.empty());
        when(customerRepository.update(any())).thenReturn(customer(1L, "Ahmad Husain", "08123456790"));

        Customer result = customerService.update(1L,
                new UpdateCustomerCommand("Ahmad Husain", "08123456790", null));

        assertThat(result.getName()).isEqualTo("Ahmad Husain");
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).update(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo("08123456790");
    }

    @Test
    void updateCustomer_notFound_throws() {
        when(customerRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.update(42L,
                new UpdateCustomerCommand("X", null, null)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void updateCustomer_phoneBelongsToOther_throws() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "Ahmad", "08123456789")));
        when(customerRepository.findByPhone("08199999999"))
                .thenReturn(Optional.of(customer(2L, "Other", "08199999999")));

        assertThatThrownBy(() -> customerService.update(1L,
                new UpdateCustomerCommand("Ahmad", "08199999999", null)))
                .isInstanceOf(DuplicateCustomerException.class);
    }

    @Test
    void updateCustomer_samePhoneOwnedBySelf_allowsUpdate() {
        Customer existing = customer(1L, "Ahmad", "08123456789");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.findByPhone("08123456789")).thenReturn(Optional.of(existing));
        when(customerRepository.update(any())).thenReturn(customer(1L, "Renamed", "08123456789"));

        Customer result = customerService.update(1L,
                new UpdateCustomerCommand("Renamed", "08123456789", null));

        assertThat(result.getName()).isEqualTo("Renamed");
    }

    @Test
    void deleteCustomer_existing_softDeletes() {
        when(customerRepository.deleteById(1L)).thenReturn(true);

        customerService.delete(1L);

        verify(customerRepository).deleteById(1L);
    }

    @Test
    void deleteCustomer_notFound_throws() {
        when(customerRepository.deleteById(42L)).thenReturn(false);

        assertThatThrownBy(() -> customerService.delete(42L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void search_passesOffsetAndLimitToRepository() {
        when(customerRepository.search("ahm", 20, 10)).thenReturn(List.of());
        when(customerRepository.count("ahm")).thenReturn(35L);

        var result = customerService.search(new SearchCustomersQuery("ahm", 2, 10));

        verify(customerRepository).search("ahm", 20, 10);
        assertThat(result.totalElements()).isEqualTo(35L);
        assertThat(result.totalPages()).isEqualTo(4);
        assertThat(result.page()).isEqualTo(2);
    }

    @Test
    void search_blankKeyword_searchesAll() {
        when(customerRepository.search(null, 0, 100)).thenReturn(List.of());
        when(customerRepository.count(null)).thenReturn(0L);

        var result = customerService.search(new SearchCustomersQuery("  ", -5, 999));

        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(100);
    }
}
