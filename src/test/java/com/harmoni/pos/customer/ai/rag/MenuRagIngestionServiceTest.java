package com.harmoni.pos.customer.ai.rag;

import com.harmoni.pos.customer.config.AiProperties;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuRagIngestionServiceTest {

    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
    }

    @Test
    void ingest_buildsDocsFromMenuService_neverHardcoded() {
        RestClient menuRestClient = mock(RestClient.class);
        MenuServiceProperties menuProps = new MenuServiceProperties();
        AiProperties aiProps = new AiProperties();
        aiProps.setRagEnabled(true);
        aiProps.setBrandId(1);
        MenuRagIngestionService service = new MenuRagIngestionService(vectorStore, menuRestClient, menuProps, aiProps);

        String categories = """
                { "data": [
                    { "id": 13, "name": "Coffee", "description": "Category Coffee include Hot and Ice" },
                    { "id": 14, "name": "Non Coffee" }
                ] }
                """;
        String coffeeProducts = """
                { "data": [
                    { "id": 56, "name": "Caramel Machiato" },
                    { "id": 57, "name": "Gula Aren" }
                ] }
                """;
        String nonCoffeeProducts = """
                { "data": [ { "id": 200, "name": "Teh Tarik" } ] }
                """;
        List<RestClient.RequestHeadersUriSpec> chains = List.of(
                chainFor(categories), chainFor(coffeeProducts), chainFor(nonCoffeeProducts));
        when(menuRestClient.get()).thenReturn(chains.get(0), chains.get(1), chains.get(2));

        service.ingest();

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(4)).add(captor.capture());
        List<Document> docs = captor.getAllValues().stream().flatMap(List::stream).toList();

        assertThat(docs).hasSize(4);
        assertThat(docs).anyMatch(d ->
                d.getText().equals("Categories for brand 1: Coffee (13) — Category Coffee include Hot and Ice")
                        && d.getMetadata().get("type").equals("category"));
        assertThat(docs).anyMatch(d ->
                d.getText().equals("Categories for brand 1: Non Coffee (14)")
                        && d.getMetadata().get("type").equals("category"));
        assertThat(docs).anyMatch(d ->
                d.getText().equals("Products in Coffee category 13: Caramel Machiato (56), Gula Aren (57) — total 2 products")
                        && d.getMetadata().get("type").equals("product")
                        && d.getMetadata().get("categoryId").equals(13L));
        assertThat(docs).anyMatch(d ->
                d.getText().equals("Products in Non Coffee category 14: Teh Tarik (200) — total 1 products")
                        && d.getMetadata().get("type").equals("product")
                        && d.getMetadata().get("categoryId").equals(14L));
        // The old hardcoded list must never appear again
        assertThat(docs).noneMatch(d -> d.getText().contains("Caramel Machiato (56), Gula Aren (57), Butter Scotch"));
    }

    @Test
    void ingest_ragDisabled_skipsEntirely() {
        MenuServiceProperties menuProps = new MenuServiceProperties();
        AiProperties aiProps = new AiProperties();
        aiProps.setRagEnabled(false);
        MenuRagIngestionService service = new MenuRagIngestionService(vectorStore, mock(RestClient.class), menuProps, aiProps);

        service.ingest();

        verify(vectorStore, times(0)).add(any());
    }

    @Test
    void ingest_menuUnreachable_addsNothing_strictNoFallback() {
        RestClient menuRestClient = mock(RestClient.class);
        when(menuRestClient.get()).thenThrow(new RuntimeException("offline"));
        MenuRagIngestionService service = new MenuRagIngestionService(vectorStore, menuRestClient, new MenuServiceProperties(), aiOff());

        service.ingest();

        verify(vectorStore, times(0)).add(any());
    }

    private static AiProperties aiOff() {
        AiProperties aiProps = new AiProperties();
        aiProps.setRagEnabled(true);
        return aiProps;
    }

    @SuppressWarnings("rawtypes")
    private static RestClient.RequestHeadersUriSpec chainFor(String payload) {
        RestClient.RequestHeadersUriSpec get = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(get.uri(any(Function.class))).thenReturn(get);
        when(get.uri(any(String.class), any(Object[].class))).thenReturn(get);
        when(get.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(payload);
        return get;
    }
}