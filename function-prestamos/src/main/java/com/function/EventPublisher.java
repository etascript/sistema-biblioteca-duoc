package com.function;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class EventPublisher {

    private static final String TOPIC_ENDPOINT = System.getenv("EVENT_GRID_TOPIC_ENDPOINT");
    private static final String TOPIC_KEY = System.getenv("EVENT_GRID_TOPIC_KEY");

    public static void publicar(String tipoEvento, String asunto, Map<String, Object> datos, Logger log) {
        if (TOPIC_ENDPOINT == null || TOPIC_ENDPOINT.isBlank() ||
            TOPIC_KEY == null || TOPIC_KEY.isBlank()) {
            log.warning("EVENT_GRID_TOPIC_ENDPOINT o EVENT_GRID_TOPIC_KEY no configurados, evento omitido.");
            return;
        }
        try {
            EventGridPublisherClient<EventGridEvent> client = new EventGridPublisherClientBuilder()
                    .endpoint(TOPIC_ENDPOINT)
                    .credential(new AzureKeyCredential(TOPIC_KEY))
                    .buildEventGridEventPublisherClient();

            EventGridEvent evento = new EventGridEvent(asunto, tipoEvento, BinaryData.fromObject(datos), "1.0");
            evento.setId(UUID.randomUUID().toString());
            client.sendEvent(evento);
            log.info("Evento publicado: " + tipoEvento + " | " + asunto);
        } catch (Exception e) {
            log.severe("Error al publicar evento: " + e.getMessage());
        }
    }
}
