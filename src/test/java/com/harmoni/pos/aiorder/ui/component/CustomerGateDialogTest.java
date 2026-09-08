package com.harmoni.pos.aiorder.ui.component;

import com.harmoni.pos.aiorder.ui.AbstractComponentTest;
import com.harmoni.pos.customer.adapter.in.web.dto.CreateCustomerRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerResponse;
import com.harmoni.pos.customer.application.service.CustomerService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertDisabled;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertEnabled;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Browserless tests for the {@link CustomerGateDialog} login/registration flow:
 * <ul>
 *   <li>the submit button stays disabled until a valid phone number is entered</li>
 *   <li>invalid phone formats show inline errors</li>
 *   <li>a new registration requires a name</li>
 *   <li>a successful registration calls the service, closes the dialog and fires the callback</li>
 *   <li>a returning customer can log in without re-entering the name</li>
 * </ul>
 *
 * @author Husain Harmoni
 */
class CustomerGateDialogTest extends AbstractComponentTest {

    private static final String PHONE = "08123456789";
    private static final CustomerResponse BUDI = new CustomerResponse(1L, "Budi", PHONE, null, Instant.now(), Instant.now());

    private CustomerService customerService;
    private Validator validator;
    private AtomicReference<CustomerResponse> result;
    private CustomerGateDialog dialog;

    @BeforeEach
    void createDialog() {
        customerService = mock(CustomerService.class);
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        result = new AtomicReference<>();
        dialog = new CustomerGateDialog("session-1", customerService, validator, result::set);
        dialog.open();
    }

    @Test
    void submitDisabledUntilPhoneIsEntered() {
        assertEquals("Masuk atau Daftar", dialog.getHeaderTitle());
        assertTrue(dialog.isOpened());
        _assertDisabled(submitButton());
    }

    @Test
    void validPhoneEnablesSubmit() {
        _setValue(phoneField(), PHONE);
        _assertEnabled(submitButton());
    }

    @Test
    void invalidPhoneShowsInlineErrorAndKeepsSubmitDisabled() {
        _setValue(phoneField(), "123");
        assertTrue(phoneField().isInvalid());
        assertEquals("Format HP tidak valid", phoneField().getErrorMessage());
        _assertDisabled(submitButton());
    }

    @Test
    void newRegistrationRequiresName() {
        when(customerService.findByPhone(PHONE)).thenReturn(Optional.empty());
        _setValue(phoneField(), PHONE);
        _click(submitButton());

        TextField nameField = _get(TextField.class, spec -> spec.withPlaceholder("Budi (wajib jika daftar baru)"));
        assertTrue(nameField.isInvalid());
        assertEquals("Nama wajib untuk pendaftaran baru", nameField.getErrorMessage());
        verify(customerService, never()).loginOrRegister(anyString(), any());
    }

    @Test
    void successfulRegistrationClosesDialogAndFiresCallback() {
        when(customerService.findByPhone(PHONE)).thenReturn(Optional.empty());
        when(customerService.loginOrRegister(eq("session-1"), any(CreateCustomerRequest.class))).thenReturn(BUDI);

        _setValue(phoneField(), PHONE);
        _setValue(nameField(), "Budi");
        _click(submitButton());

        assertFalse(dialog.isOpened());
        assertNotNull(result.get());
        assertEquals("Budi", result.get().name());
        verify(customerService).loginOrRegister(eq("session-1"), any(CreateCustomerRequest.class));
    }

    @Test
    void returningCustomerCanLoginWithoutEnteringName() {
        when(customerService.findByPhone(PHONE)).thenReturn(Optional.of(BUDI));
        when(customerService.loginOrRegister(eq("session-1"), any(CreateCustomerRequest.class))).thenReturn(BUDI);

        _setValue(phoneField(), PHONE);
        _click(submitButton());

        assertFalse(dialog.isOpened());
        assertEquals(BUDI, result.get());
        verify(customerService).loginOrRegister(eq("session-1"), any(CreateCustomerRequest.class));
        verify(customerService, never()).saveCustomer(anyString(), any());
    }

    private TextField phoneField() {
        return _get(TextField.class, spec -> spec.withPlaceholder("0812xxxxxxx"));
    }

    private TextField nameField() {
        return _get(TextField.class, spec -> spec.withPlaceholder("Budi (wajib jika daftar baru)"));
    }

    private Button submitButton() {
        return _get(Button.class, spec -> spec.withText("Masuk / Daftar"));
    }
}