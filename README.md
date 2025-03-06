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

## Additional Resources

- [Dapr CloudEvents Documentation](https://docs.dapr.io/developing-applications/building-blocks/pubsub/pubsub-cloudevents/)
- [W3C Trace Context Specification](https://www.w3.org/TR/trace-context/)
- [Dapr Distributed Tracing Documentation](https://docs.dapr.io/operations/monitoring/tracing/)