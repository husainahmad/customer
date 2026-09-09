package com.harmoni.pos.customer.application.service;

import com.harmoni.pos.customer.application.port.in.AddCustomerMessageCommand;
import com.harmoni.pos.customer.application.port.in.AddCustomerMessageUseCase;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.CloseCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerMessagesUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.PageResult;
import com.harmoni.pos.customer.application.port.out.CustomerMessageRepository;
import com.harmoni.pos.customer.application.port.out.CustomerRepository;
import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.domain.exception.CustomerNotFoundException;
import com.harmoni.pos.customer.domain.exception.CustomerSessionNotFoundException;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerMessageException;
import com.harmoni.pos.customer.domain.model.Customer;
import com.harmoni.pos.customer.domain.model.CustomerMessage;
import com.harmoni.pos.customer.domain.model.CustomerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Implements the session lifecycle and chat-history use cases - opening/closing sessions, adding and reading messages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerSessionService implements
        CreateCustomerSessionUseCase,
        GetCustomerSessionUseCase,
        CloseCustomerSessionUseCase,
        AddCustomerMessageUseCase,
        GetCustomerMessagesUseCase {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_MAX_ATTEMPTS = 3;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CustomerRepository customerRepository;
    private final CustomerSessionRepository sessionRepository;
    private final CustomerMessageRepository messageRepository;

    @Override
    @Transactional
    public CustomerSession create(CreateCustomerSessionCommand command) {
        Optional.ofNullable(command.customerId())
                .ifPresent(this::requireCustomer);
        for (int attempt = 1; attempt <= TOKEN_MAX_ATTEMPTS; attempt++) {
            CustomerSession session = CustomerSession.create(
                    command.customerId(), command.source(), generateToken());
            try {
                return sessionRepository.save(session);
            } catch (DuplicateKeyException e) {
                log.warn("Session token collision on attempt {}/{}", attempt, TOKEN_MAX_ATTEMPTS);
            }
        }
        throw new IllegalStateException("Unable to generate a unique session token");
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSession getSession(long id) {
        return findExisting(id);
    }

    @Override
    @Transactional
    public CustomerSession close(long sessionId) {
        CustomerSession session = findExisting(sessionId);
        session.close();
        return sessionRepository.update(session);
    }

    @Override
    @Transactional
    public CustomerMessage addMessage(AddCustomerMessageCommand command) {
        CustomerSession session = findExisting(command.sessionId());
        if (!session.isOpen()) {
            throw new InvalidCustomerMessageException(
                    "Cannot add message to closed session " + session.getId());
        }
        return messageRepository.save(CustomerMessage.create(
                command.sessionId(), command.role(), command.message()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CustomerMessage> getMessages(long sessionId, int page, int size) {
        findExisting(sessionId);
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);

        List<CustomerMessage> content =
                messageRepository.findBySessionId(sessionId, pageIndex * pageSize, pageSize);
        long totalElements = messageRepository.countBySessionId(sessionId);
        return new PageResult<>(content, totalElements, pageIndex, pageSize);
    }

    private void requireCustomer(long customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private CustomerSession findExisting(long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomerSessionNotFoundException(sessionId));
    }

    /**
     * 32 random bytes as Base64URL -> 43 chars, fits varchar(100),
     * uniqueness enforced by uk_customer_sessions_token.
     */
    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
