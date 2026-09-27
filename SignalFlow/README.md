# SignalFlow

SignalFlow is a microservices-based application.

## Architecture

This section provides a high-level overview of the SignalFlow microservices architecture.

```mermaid
flowchart LR
    User((User))

    subgraph Ingress [Infrastructure / Ingress]
        Caddy[Caddy Reverse Proxy]
    end

    subgraph FE [Frontend]
        WebUI[Frontend Web Application]
    end

    subgraph BE [Backend Services]
        direction TB
        ApiGateway[API Gateway]
        InterestTopicService[Interest Topic Service]
        MailService[Mail Service]
    end

    subgraph Data [Data Stores & Message Brokers]
        direction TB
        Kafka{Apache Kafka}
        DB_API[(PostgreSQL API DB)]
        DB_Topics[(PostgreSQL Topics DB)]
        Redis[(Redis Cache)]
    end

    subgraph External [External Services]
        direction TB
        OpenRouter[OpenRouter AI]
        SMTP[SMTP Mail Server]
    end

    User --->|HTTPS| Caddy
    Caddy --->|/api/*| ApiGateway
    Caddy --->|/*| WebUI
    
    WebUI -.->|API Calls via Caddy| ApiGateway
    
    ApiGateway --->|REST / HTTP| InterestTopicService
    ApiGateway --->|Produces Events| Kafka
    ApiGateway --->|Reads/Writes| DB_API
    
    InterestTopicService --->|Produces Events| Kafka
    InterestTopicService --->|Reads/Writes| DB_Topics
    InterestTopicService --->|API Calls| OpenRouter
    
    Kafka --->|Consumes Events| MailService
    MailService --->|State / Cache| Redis
    MailService --->|Sends Email| SMTP

    classDef db fill:#f9f0ff,stroke:#8e44ad,stroke-width:2px,color:#000
    classDef broker fill:#fff3e0,stroke:#d35400,stroke-width:2px,color:#000
    classDef service fill:#e1f5fe,stroke:#0288d1,stroke-width:2px,color:#000
    classDef ext fill:#eceff1,stroke:#607d8b,stroke-width:2px,color:#000
    classDef proxy fill:#e8f5e9,stroke:#388e3c,stroke-width:2px,color:#000
    classDef ui fill:#fce4ec,stroke:#c2185b,stroke-width:2px,color:#000

    class DB_API,DB_Topics,Redis db
    class Kafka broker
    class ApiGateway,InterestTopicService,MailService service
    class OpenRouter,SMTP ext
    class Caddy proxy
    class WebUI ui
```

### Components Breakdown

1. **Ingress (Caddy)**: Serves as the reverse proxy for handling incoming HTTP/HTTPS requests and routing them to either the frontend or the API gateway.
2. **Frontend**: The user interface which communicates with the backend via the API Gateway.
3. **API Gateway**: The main entry point for backend operations. Handles authentication/authorization (JWT), routes specific domain logic to the Interest Topic Service, produces events to Kafka, and manages its own PostgreSQL database.
4. **Interest Topic Service**: Manages topics of interest. It integrates with the external OpenRouter AI API to process data, reads/writes to its own dedicated PostgreSQL database, and produces events to Kafka.
5. **Mail Service**: Consumes events from Kafka to asynchronously send emails using an external SMTP server. It utilizes Redis for state management, caching, or rate limiting.
6. **Databases/Brokers**: 
   - **PostgreSQL**: Dedicated databases for the API Gateway and Interest Topic Service to enforce data isolation.
   - **Kafka**: Facilitates asynchronous event-driven communication between microservices.
   - **Redis**: Used by the Mail Service.
