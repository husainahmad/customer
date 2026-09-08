package com.harmoni.pos.aiorder.ui;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.SpringServlet;
import kotlin.jvm.functions.Function0;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Base class for browserless Vaadin <em>view</em> tests that need the Spring
 * application context.
 * <p>
 * Boots the Spring Boot context (without a real server) and installs Karibu's
 * mocked Vaadin environment backed by a {@link MockSpringServlet}, so views are
 * created through Spring's instantiator and their injected services are real
 * (or overridden via {@code @MockitoBean} in subclasses).
 * <p>
 * Routes are auto-discovered from the {@code com.harmoni.pos} package.
 *
 * @author Husain Harmoni
 */
@SpringBootTest(properties = "vaadin.launch-browser=false")
public abstract class AbstractUiTest {

    private static final Routes ROUTES = new Routes().autoDiscoverViews("com.harmoni.pos");

    @Autowired
    protected ApplicationContext applicationContext;

    @BeforeEach
    final void setUp() {
        Function0<UI> uiFactory = UI::new;
        SpringServlet servlet = new MockSpringServlet(ROUTES, applicationContext, uiFactory);
        MockVaadin.setup(uiFactory, servlet);
    }

    @AfterEach
    final void tearDown() {
        MockVaadin.tearDown();
    }
}