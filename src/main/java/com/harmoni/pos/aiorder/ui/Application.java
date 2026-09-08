package com.harmoni.pos.aiorder.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;

/** Vaadin app shell config: enables server push and the {@code app} theme. */
@Push
@Theme("app")
public class Application implements AppShellConfigurator {
}