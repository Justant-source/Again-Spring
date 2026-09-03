package com.againspring.aiuser.orchestrator.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BackendBotClientConstructorTest {
    @Test
    void constructorTakesOnlyPrimaryClient() {
        RestClient rc = RestClient.builder().baseUrl("http://localhost:1").build();
        BackendBotClient client = new BackendBotClient(rc);
        assertNotNull(client);
        assertEquals(1, BackendBotClient.class.getConstructors().length);
        assertEquals(1, BackendBotClient.class.getConstructors()[0].getParameterCount());
    }
}
