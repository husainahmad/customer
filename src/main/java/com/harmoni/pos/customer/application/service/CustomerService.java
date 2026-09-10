package com.harmoni.pos.customer.application.service;

import com.harmoni.pos.customer.application.port.in.CreateCustomerCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.DeleteCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.PageResult;
import com.harmoni.pos.customer.application.port.in.SearchCustomersQuery;
import com.harmoni.pos.customer.application.port.in.SearchCustomerUseCase;
import com.harmoni.pos.customer.application.port.in.UpdateCustomerCommand;
import com.harmoni.pos.customer.application.port.in.UpdateCustomerUseCase;
import com.harmoni.pos.customer.application.port.out.CustomerRepository;
import com.harmoni.pos.customer.domain.exception.CustomerNotFoundException;
import com.harmoni.pos.customer.domain.exception.DuplicateCustomerException;
import com.harmoni.pos.customer.domain.model.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implements the customer use cases: create, get, search, update and delete.
 * <p>
 * Phone uniqueness is enforced before save/update ({@link DuplicateCustomerException}), and missing
 * customers surface as {@link CustomerNotFoundException}. Search is paginated (default 20, capped at
 * 100 per page) and query terms are trimmed — a blank keyword matches all customers.
 */
@Service
@RequiredArgsConstructor
public class CustomerService implements
        CreateCustomerUseCase,
        GetCustomerUseCase,
        SearchCustomerUseCase,
        UpdateCustomerUseCase,
        DeleteCustomerUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public Customer create(CreateCustomerCommand command) {
        assertPhoneAvailable(command.phone(), null);
        return customerRepository.save(Customer.create(command.name(), command.phone(), command.email()));
    }

    @Override
    @Transactional(readOnly = true)
    public Customer getCustomer(long id) {
        return findExisting(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Customer> search(SearchCustomersQuery query) {
        int size = query.size() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MAX_PAGE_SIZE);
        int page = Math.max(query.page(), 0);
        String keyword = normalize(query.search());

        List<Customer> content = customerRepository.search(keyword, page * size, size);
        long totalElements = customerRepository.count(keyword);
        return new PageResult<>(content, totalElements, page, size);
    }

    @Override
    @Transactional
    public Customer update(long id, UpdateCustomerCommand command) {
        Customer existing = findExisting(id);
        assertPhoneAvailable(command.phone(), existing);
        existing.updateProfile(command.name(), command.phone(), command.email());
        return customerRepository.update(existing);
    }

    @Override
    @Transactional
    public void delete(long id) {
        if (!customerRepository.deleteById(id)) {
            throw new CustomerNotFoundException(id);
        }
    }

    private Customer findExisting(long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private void assertPhoneAvailable(String phone, Customer current) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        customerRepository.findByPhone(phone).ifPresent(found -> {
            if (current == null || !found.hasId(current.getId())) {
                throw new DuplicateCustomerException("Phone already registered: " + phone);
            }
        });
    }

    private static String normalize(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }
}
