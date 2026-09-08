package com.harmoni.pos.aiorder.ui;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for browserless Vaadin component tests (no Spring context).
 * <p>
 * Sets up a mocked Vaadin environment ({@code UI}, {@code VaadinSession} and the
 * {@code UI.getCurrent()} context) before each test and tears it down afterwards.
 * Tests that do not need Spring beans should extend this class.
 *
 * @author Husain Harmoni
 */
public abstract class AbstractComponentTest {

    /** Installs a mocked Vaadin environment with an empty route registry. */
    @BeforeEach
    void setUp() {
        MockVaadin.setup(new Routes());
    }

    /** Tears down the mocked Vaadin environment. */
    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
    }
}