# Dapr Trace Context Propagation Example

This repository demonstrates how to properly implement trace context propagation when using Dapr with Apache Pulsar in a Java application. It focuses on maintaining trace context across service boundaries in a pub/sub pattern.

## Prerequisites

- Java 17 or higher
- Maven
- Docker and Docker Compose
- Dapr CLI installed and initialized

## Setup Instructions

### 1. Clone the repository

```bash
git clone https://github.com/rochabr/dapr-trace-example.git
cd dapr-trace-example
```

### 2. Start Required Services

Start Pulsar using Docker Compose:

```bash
docker-compose up -d
```

### 3. Run the Subscriber

```bash
cd subscriber
mvn clean package
dapr run --app-id subscriber \
         --app-port 8081 \
         --resources-path ../components \
         --config ../config.yaml \
         -- java -jar target/subscriber-0.0.1-SNAPSHOT.jar
```

### 4. Run the Publisher

In a new terminal:

```bash
cd publisher
mvn clean package
dapr run --app-id publisher \
         --app-port 8082 \
         --resources-path ../components \
         --config ../config.yaml \
         -- java -jar target/publisher-0.0.1-SNAPSHOT.jar
```

### 6. Test the Trace Propagation

Make a request to the publisher:

```bash
curl http://localhost:8082/publish
```

### 7. Observe the Traces

Open Zipkin in your browser:

```
http://localhost:9411
```

Click on "Find Traces" to see the distributed trace spanning both services.

## Solution Overview

The key to making custom trace propagation work is:

1. Using OpenTelemetry in the publisher to create spans with custom trace context
2. Explicitly adding the trace context to CloudEvent metadata
3. Configuring Dapr properly to handle this trace context

## Key Changes to Make Custom Trace Propagation Work

### 1. Publisher Implementation with OpenTelemetry

The publisher service uses OpenTelemetry to create and manage custom trace IDs:

```java
@GetMapping("/publish")
public Mono publishEvent() {
    // Create a custom trace ID and span ID
    String customTraceId = generateCustomTraceId();
    String customSpanId = generateCustomSpanId();
    
    // Create a custom span context with your trace ID
    SpanContext customSpanContext = SpanContext.create(
            customTraceId,
            customSpanId,
            TraceFlags.getSampled(),
            TraceState.getDefault()
    );

    // Create a span with the custom context
    Span span = tracer.spanBuilder("publish-event")
            .setParent(Context.current().with(Span.wrap(customSpanContext)))
            .setSpanKind(SpanKind.PRODUCER)
            .startSpan();
    
    try (Scope scope = span.makeCurrent()) {
        // Create order data
        Map order = new HashMap<>();
        // ...
        
        // CRITICAL: Add trace context to CloudEvent metadata
        Map metadata = new HashMap<>();
        String traceparent = String.format("00-%s-%s-%02x", 
                spanContext.getTraceId(), 
                spanContext.getSpanId(),
                spanContext.getTraceFlags().asByte());
        metadata.put("cloudevent.traceparent", traceparent);
        
        // Publish with metadata
        return daprClient.publishEvent(
                PUBSUB_NAME,
                TOPIC_NAME,
                order,
                metadata
        ).then(/* ... */);
    } finally {
        span.end();
    }
}
```

### 2. OpenTelemetry Dependencies

The publisher's `pom.xml` includes the necessary OpenTelemetry dependencies:

```xml
<!-- OpenTelemetry -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>${opentelemetry.version}</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>${opentelemetry.version}</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
    <version>${opentelemetry.version}</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-trace</artifactId>
    <version>${opentelemetry.version}</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-context</artifactId>
    <version>${opentelemetry.version}</version>
</dependency>
<!-- https://mvnrepository.com/artifact/io.opentelemetry.semconv/opentelemetry-semconv -->
<dependency>
    <groupId>io.opentelemetry.semconv</groupId>
    <artifactId>opentelemetry-semconv</artifactId>
    <version>1.30.0</version>
</dependency>
```

### 3. OpenTelemetry Initialization

The publisher initializes OpenTelemetry in the main application class:

```java
@SpringBootApplication
public class PublisherApplication {

    public static void main(String[] args) {
        // Initialize OpenTelemetry
        initializeOpenTelemetry();
        
        SpringApplication.run(PublisherApplication.class, args);
    }
    
    private static void initializeOpenTelemetry() {
        // Configure OpenTelemetry
        Resource resource = Resource.getDefault()
                .merge(Resource.create(io.opentelemetry.api.common.Attributes.of(
                        ResourceAttributes.SERVICE_NAME, "publisher-service"
                )));
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(
                        ZipkinSpanExporter.builder()
                                .setEndpoint("http://localhost:9411/api/v2/spans")
                                .build())
                        .build())
                .setResource(resource)
                .build();
        
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }
    
    @Bean
    public Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("publisher-tracer");
    }
}
```

### 4. Custom Trace ID Generation

Helper methods to generate valid trace IDs and span IDs:

```java
private String generateCustomTraceId() {
    // Must be 32 hex characters (16 bytes)
    String uuid = UUID.randomUUID().toString().replace("-", "");
    String uuid2 = UUID.randomUUID().toString().replace("-", "");
    return uuid + uuid2.substring(0, 32 - uuid.length());
}

private String generateCustomSpanId() {
    // Must be 16 hex characters (8 bytes)
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
}
```

### 5. Dapr Configuration

No special Dapr configuration is needed for the trace propagation, as we're explicitly adding the trace context to the CloudEvent metadata.

## Simplified Subscriber

The subscriber doesn't need OpenTelemetry integration since:

1. Dapr properly passes the trace context through the CloudEvent
2. The trace context is available in HTTP headers
3. Dapr's runtime creates appropriate child spans automatically

## Running the Example

Follow the setup instructions in the previous sections to run the example and verify trace propagation in Zipkin.

## Key Insights

1. **W3C Trace Context Format**: The trace context must follow the W3C format: `00-<traceId>-<spanId>-<flags>`
2. **CloudEvent Metadata**: Using `cloudevent.traceparent` in metadata is crucial for proper propagation
3. **Custom Trace IDs**: Creating custom span contexts allows control over trace IDs

## Troubleshooting

If you see multiple traces instead of a single connected trace:

1. Verify the trace context is correctly formatted in W3C format
2. Check that the CloudEvent metadata contains the `cloudevent.traceparent` field
3. Ensure Zipkin is properly configured to receive spans

## Additional Resources

- [Dapr CloudEvents Documentation](https://docs.dapr.io/developing-applications/building-blocks/pubsub/pubsub-cloudevents/)
- [W3C Trace Context Specification](https://www.w3.org/TR/trace-context/)
- [Dapr Distributed Tracing Documentation](https://docs.dapr.io/operations/monitoring/tracing/)