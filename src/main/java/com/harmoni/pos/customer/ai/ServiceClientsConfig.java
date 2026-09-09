package com.harmoni.pos.customer.ai;

import com.harmoni.pos.customer.config.MenuServiceProperties;
import com.harmoni.pos.customer.config.OrderServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Defines the RestClient beans for the Menu (8082) and Order (8083) services.
 */
@Configuration
public class ServiceClientsConfig {

    @Bean
    public RestClient menuRestClient(RestClient.Builder builder, MenuServiceProperties props) {
        return builder.baseUrl(props.getBaseUrl()).build();
    }

    @Bean
    public RestClient orderRestClient(RestClient.Builder builder, OrderServiceProperties props) {
        return builder.baseUrl(props.getBaseUrl()).build();
    }
}
