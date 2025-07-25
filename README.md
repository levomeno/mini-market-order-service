# Mini Market Order Service

A Spring Boot REST API service that accepts market orders (BUY/SELL), fetches current prices from a mock price feed, executes orders, and stores results in a database.

## Features

- **Market Order Processing**: Accept BUY/SELL orders for any symbol
- **Price Feed Integration**: Fetch current prices from mock external service with retry logic
- **Order Execution**: Execute orders at current market price and store in database
- **Rate Limiting**: 10 requests per second per account ID using Bucket4j
- **Caching**: Redis-based caching for price data and rate limiting
- **Database**: PostgreSQL for persistent storage with JPA/Hibernate
- **API Documentation**: Swagger/OpenAPI documentation
- **Monitoring**: Spring Actuator with health checks and metrics
- **Testing**: Unit tests with Mockito and integration tests with Testcontainers

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2.1
- **Database**: PostgreSQL 16
- **Cache**: Redis 7
- **Mapping**: MapStruct 1.5.5
- **Rate Limiting**: Bucket4j 8.7.0
- **Documentation**: SpringDoc OpenAPI 2.3.0
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Containerization**: Docker & Docker Compose

## Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Maven 3.6+ (for local development)

## Quick Start

### Using Docker Compose (Recommended)

1. Clone the repository
2. Run the complete stack:

```bash
docker-compose up --build
```

This will start:
- PostgreSQL database (port 5432)
- Redis cache (port 6379)
- Mock price feed service (port 8081)
- Main application (port 8080)

### Local Development

1. Start dependencies:
```bash
docker-compose up postgres redis mock-price-feed
```

2. Run the application:
```bash
./mvnw spring-boot:run
```

## API Endpoints

### Create Order
```bash
POST /orders
Content-Type: application/json

{
  "accountId": "acc-123",
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 10
}
```

### Get Order by ID
```bash
GET /orders/{id}
```

### Get Orders by Account
```bash
GET /orders?accountId=acc-123
```


## Sample Requests

### Create a BUY order
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "acc-123",
    "symbol": "AAPL",
    "side": "BUY",
    "quantity": 10
  }'
```

### Create a SELL order
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "acc-456",
    "symbol": "GOOGL",
    "side": "SELL",
    "quantity": 5
  }'
```

### Get order by ID
```bash
curl http://localhost:8080/orders/1
```

### Get orders for account
```bash
curl "http://localhost:8080/orders?accountId=acc-123"
```

## API Documentation

Once the application is running, access the Swagger UI at:
- http://localhost:8080/swagger-ui.html

API documentation is also available at:
- http://localhost:8080/api-docs

## Health Check

Check application health:
```bash
curl http://localhost:8080/actuator/health
```

## Database Schema

### Orders Table
```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(255) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity DECIMAL(18,6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### Executions Table
```sql
CREATE TABLE executions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    price DECIMAL(18,6) NOT NULL,
    executed_at TIMESTAMP NOT NULL
);
```

## Rate Limiting

- **Limit**: 10 requests per second per account ID
- **Implementation**: Bucket4j with Redis backend
- **Response**: HTTP 429 when limit exceeded

## Error Handling

The API returns appropriate HTTP status codes:

- **200**: Success
- **201**: Created
- **400**: Bad Request (validation errors)
- **404**: Not Found
- **422**: Unprocessable Entity (price feed errors)
- **429**: Too Many Requests (rate limit exceeded)
- **500**: Internal Server Error

## Testing

### Run Unit Tests
```bash
./mvnw test
```

### Run Integration Tests
```bash
./mvnw verify
```

### Test Coverage
```bash
./mvnw jacoco:report
```

Coverage report will be available at `target/site/jacoco/index.html`

## Configuration

Key configuration properties in `application.yml`:

```yaml
app:
  price-feed:
    base-url: http://localhost:8081
    timeout: 5000
    retry:
      max-attempts: 4
      initial-delay: 1000
      multiplier: 2.0
      max-delay: 8000
  rate-limit:
    requests-per-second: 10
    bucket-capacity: 10
```

## Design Decisions

### Architecture
- **Layered Architecture**: Controller → Service → Repository pattern
- **DTO Pattern**: Separate DTOs for API requests/responses
- **MapStruct**: Type-safe mapping between entities and DTOs
- **Exception Handling**: Global exception handler for consistent error responses

### Data Storage
- **PostgreSQL**: ACID compliance for financial data
- **BigDecimal**: Precise decimal arithmetic for monetary values
- **JPA/Hibernate**: Object-relational mapping with automatic schema generation

### Caching Strategy
- **Redis**: Distributed caching for price data and rate limiting
- **TTL**: 3-second cache for price data to balance freshness and performance
- **Cache-aside**: Manual cache management for fine-grained control

### Rate Limiting
- **Token Bucket**: Bucket4j implementation with Redis backend
- **Per-account**: Individual limits per account ID
- **Graceful Degradation**: Clear error messages when limits exceeded

### Testing Strategy
- **Unit Tests**: Service layer logic with Mockito
- **Integration Tests**: Full stack testing with Testcontainers
- **Test Coverage**: Target >80% code coverage

## Monitoring

### Health Checks
- Application: `/actuator/health`
- Database connectivity
- Redis connectivity

### Metrics
- Custom metrics via `/actuator/metrics`
- Prometheus metrics via `/actuator/prometheus`

## Security Considerations

- Input validation with Bean Validation
- SQL injection prevention via JPA
- Rate limiting to prevent abuse
- No sensitive data in logs
- Docker security with non-root user

## Performance

- Connection pooling for database
- Redis connection pooling
- Async processing capabilities
- Efficient query patterns with JPA

## Deployment

The application is containerized and ready for deployment:

- **Docker Image**: Multi-stage build for optimized size
- **Health Checks**: Built-in Docker health checks
- **Environment Variables**: Externalized configuration
- **Graceful Shutdown**: Proper cleanup on container stop

## License

This project is created for educational purposes as part of a coding challenge.

