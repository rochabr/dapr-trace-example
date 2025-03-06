package com.example.publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import reactor.core.publisher.Mono;

@RestController
public class PublisherController {

    private static final Logger logger = LoggerFactory.getLogger(PublisherController.class);
    private static final String PUBSUB_NAME = "pulsar-pubsub";
    private static final String TOPIC_NAME = "orders";

    private final DaprClient daprClient;

    public PublisherController() {
        this.daprClient = new DaprClientBuilder().build();
    }

    @GetMapping("/publish")
    public Mono<String> publishEvent() {
        logger.info("Publishing an order event");
        
        // Create order data
        Map<String, Object> order = new HashMap<>();
        String orderId = UUID.randomUUID().toString();
        order.put("orderId", orderId);
        order.put("customer", "test-customer");
        order.put("amount", 100.00);
        order.put("timestamp", System.currentTimeMillis());

        // Create metadata to override CloudEvent properties
        Map<String, String> metadata = new HashMap<>();
        metadata.put("cloudevent.traceid", "custom-trace-id");
        metadata.put("cloudevent.source", "order-service");
        metadata.put("cloudevent.type", "com.example.order");
        metadata.put("cloudevent.id", UUID.randomUUID().toString());

        // Publish with metadata
        return daprClient.publishEvent(
                PUBSUB_NAME,
                TOPIC_NAME,
                order,
                metadata
        ).then(Mono.fromCallable(() -> {
            String response = "Published order with ID: " + orderId;
            logger.info(response);
            return response;
        }));
    }
}