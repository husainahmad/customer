# Harmoni POS Customer Service

Customer-facing bounded context for the Harmoni POS platform. It owns customer
identity, ordering sessions and chat history, and hosts the **AI ordering
assistant** (Spring AI) that can browse the menu, manage a cart and place an
order. It also proxies menu browsing and checkout so the frontend only ever
talks to this service.

The service is reached through the **gateway**, which validates the JWT and
forwards the caller identity. It calls the **Menu Service** (`8082`) for
catalogue data and the **Order Service** (`8083`) to create and pay orders.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.4.8 (Web MVC, Validation) |
| AI | Spring AI 1.0.3 (OpenAI-compatible ChatClient, tool calling, vector store) |
| Persistence | MyBatis 3.0.4 + MySQL (`db-harmoni-customer`) |
| HTTP client | Spring WebFlux `WebClient` / `RestClient` |
| API docs | springdoc-openapi 2.8.8 (Swagger UI) |
| Build | Maven (`./mvnw` or `mvn`), GraalVM native plugin |

## Architecture

The service follows a **hexagonal (ports & adapters)** architecture. Dependencies
point inward: adapters depend on the application layer, which depends on the
domain. The domain has no Spring, MyBatis or Jackson dependencies.

```
com.harmoni.pos.customer
├── domain
│   ├── model            # Customer, CustomerSession, CustomerMessage, enums
│   └── exception        # CustomerNotFoundException, DuplicateCustomerException, ...
├── application
│   ├── port.in          # Use-case interfaces + command/query records
│   ├── port.out         # Repository interfaces (Customer, Session, Message)
│   └── service          # CustomerService, CustomerSessionService, CustomerAiChatService
├── adapter
│   ├── in.web           # REST controllers, DTOs, GlobalExceptionHandler
│   └── out.persistence.mybatis
│       ├── *Mapper      # MyBatis mapper interfaces
│       ├── entity       # Persistence entities
│       └── repository   # Port implementations (entity ↔ domain mapping)
├── ai
│   ├── tool             # MenuTools, CartTools, OrderTools (LLM tools)
│   └── rag              # RagConfig, MenuRagIngestionService
├── cart                 # CustomerCartService (in-memory, per session)
├── config               # AiConfig, AiProperties, MenuServiceProperties,
│                        # OrderServiceProperties, RetryProperties
└── CustomerApplication
```

### Layers

- **Domain** — pure Java models with behaviour: `CustomerSession.close()` and
  `isOpen()`, `CustomerMessage.create()` validation, `Customer.updateProfile()`.
  Enums: `CustomerSessionStatus` (`OPEN`/`CLOSED`), `CustomerSessionSource`
  (`TABLE_QR`, `WEB_CHAT`, `WHATSAPP`, `MOBILE_APP`, `AI_CHAT`),
  `CustomerMessageRole` (`USER`/`ASSISTANT`).
- **Application** — use cases (`CreateCustomerUseCase`, `SearchCustomerUseCase`,
  `CreateCustomerSessionUseCase`, `AddCustomerMessageUseCase`,
  `ChatWithAiUseCase`, ...), output ports (`CustomerRepository`,
  `CustomerSessionRepository`, `CustomerMessageRepository`) and the services
  that implement them. Services are `@Transactional` and only know the domain
  and the ports.
- **Adapters in** — REST controllers, request/response DTOs with Bean
  Validation, and `GlobalExceptionHandler` mapping domain exceptions to stable
  error codes.
- **Adapters out** — MyBatis mappers + XML, persistence entities, and repository
  adapters that implement the output ports and map entity ↔ domain.

## Domain model

| Table | Purpose |
| --- | --- |
| `customers` | Customer identity: `brand_id`, `name`, `phone` (unique), `email`, soft-delete `deleted_at` |
| `customer_sessions` | A session per conversation: `customer_id` (nullable for anonymous), unique `session_token`, `source`, `status` |
| `customer_messages` | Chat history: `session_id`, `role` (`USER`/`ASSISTANT`), `message` |

The schema lives in `db-harmoni-customer`. The test schema is in
`src/test/resources/sql/schema-test.sql`.

### Key rules

- **Phone uniqueness** is enforced before create/update (`DuplicateCustomerException`).
  The check includes soft-deleted rows, because the DB unique key still reserves
  the phone of a deleted customer.
- **Soft delete** — `DELETE /customers/{id}` sets `deleted_at`; reads and updates
  filter `deleted_at IS NULL`. History (sessions/messages) is preserved.
- **Sessions** start `OPEN` and can be closed exactly once. A closed session
  rejects new messages and cart changes.
- **Session tokens** are 32 random bytes Base64URL-encoded (43 chars), generated
  by the backend, with collision retries.
- **Pagination** is clamped: customers default 20 / max 100; messages default
  50 / max 100; page is zero-based.

## AI ordering assistant

`CustomerAiChatService` implements `ChatWithAiUseCase`:

1. Validate the session (must exist and be `OPEN`).
2. Persist the `USER` message.
3. Call the configured AI provider through the Spring AI `ChatClient`, with
   optional RAG context and rate-limit retry.
4. Persist and return the `ASSISTANT` reply.

`AiConfig` wires the `ChatClient` with:

- the system prompt from `harmoni.ai.system-prompt`,
- an in-memory `MessageWindowChatMemory` (last 20 messages per session),
- the three tool groups: `MenuTools`, `CartTools`, `OrderTools`.

### Tools

| Tool group | What it does |
| --- | --- |
| `MenuTools` | Search products/categories, read product customizations, fetch prices from the Menu Service |
| `CartTools` | Add/remove/view cart items, resolve customization option ids |
| `OrderTools` | `confirmOrder` — POST the cart to the Order Service, then PUT the payment method and clear the cart |

The tools are the **source of truth** for identifiers and prices. The system
prompt instructs the model never to invent product/SKU/category/option ids or
prices, and to ask the customer to choose customizations before adding an item.

`CustomerAiChatService` also intercepts models that emit raw tool JSON as text
instead of using Spring AI tool binding, executes the intended tool, and persists
the cleaned reply so JSON never leaks to the client.

### RAG (optional)

`RagConfig` provides a `SimpleVectorStore` backed by the configured embedding
model. `MenuRagIngestionService` ingests categories and products from the Menu
Service on startup when `harmoni.ai.rag.enabled=true`. RAG is a convenience
layer; the tools remain authoritative.

### Providers

The default provider is a local **Ollama** (`qwen2.5:3b`) via the
OpenAI-compatible API. Profiles switch providers without code changes:

| Profile | Provider | Model |
| --- | --- | --- |
| *(default)* | local Ollama | `qwen2.5:3b` |
| `ollama-cloud` | Ollama Cloud | `gpt-oss:20b` |
| `gemini` | Google Gemini (OpenAI-compatible) | `gemini-flash-latest` |
| `groq` | Groq | `openai/gpt-oss-20b` |

## Cart

`CustomerCartService` keeps an **in-memory** cart per session
(`ConcurrentHashMap`). It is intentionally not persisted yet.

- A line is keyed on `(productId, skuId, chosen customizations)`, so different
  variants and different customization answers stay separate lines.
- Prices come from the Menu Service; the line total is
  `(SKU price + option surcharge) × quantity`.
- Adding a negative quantity that zeroes a line removes it.
- The cart is cleared after a successful checkout.
- A static fallback menu exists for offline tests only.

## External services

| Service | Used for |
| --- | --- |
| Menu Service (`harmoni.services.menu`) | Product/category search, product detail, customizations, price-aware product listing |
| Order Service (`harmoni.services.order`) | Create order (`POST /api/v1/order`) and record payment (`PUT /api/v1/order`) |

Endpoint paths and base URLs are configuration (`MenuServiceProperties`,
`OrderServiceProperties`), not hardcoded in Java.

## API reference

### Customers

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/customers` | Create a customer (201) |
| `GET` | `/api/v1/customers/{id}` | Get a customer |
| `GET` | `/api/v1/customers?search=&brandId=&page=0&size=20` | Search name/phone/email, paginated |
| `PUT` | `/api/v1/customers/{id}` | Update a customer |
| `DELETE` | `/api/v1/customers/{id}` | Soft delete (204) |

### Sessions and messages

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/customer-sessions` | Create a session (`customerId` may be null; token generated by the backend) |
| `GET` | `/api/v1/customer-sessions/{id}` | Get a session |
| `POST` | `/api/v1/customer-sessions/{id}/close` | Close a session |
| `POST` | `/api/v1/customer-sessions/{sessionId}/messages` | Add a message (rejected when the session is closed) |
| `GET` | `/api/v1/customer-sessions/{sessionId}/messages?page=0&size=50` | Message history, ascending, paginated |

### AI chat

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/customer-sessions/{sessionId}/chat` | Send a message, get the assistant reply (both persisted) |
| `POST` | `/api/v1/customer-sessions/{sessionId}/messages/ai` | Alias of `/chat` |
| `POST` | `/api/v1/customer-sessions/{sessionId}/chat/stream` | SSE stream of the assistant reply |
| `POST` | `/api/v1/customer-sessions/{sessionId}/messages/ai/stream` | Alias of `/chat/stream` |

### Cart

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/v1/customer-sessions/{sessionId}/cart` | Current cart with items and total |
| `POST` | `/api/v1/customer-sessions/{sessionId}/cart/items` | Add a product (increases quantity when the line matches) |
| `DELETE` | `/api/v1/customer-sessions/{sessionId}/cart/items/{productId}` | Remove a product |
| `GET` | `/api/v1/customer-sessions/{sessionId}/cart/menu` | Static fallback menu (offline) |

### Menu browsing (proxied)

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/v1/customer-sessions/{sessionId}/categories` | Categories for the default brand |
| `GET` | `/api/v1/customer-sessions/{sessionId}/categories/search?categoryName=` | Search categories |
| `GET` | `/api/v1/customer-sessions/{sessionId}/products/category/{categoryId}` | Products in a category with prices |
| `GET` | `/api/v1/customer-sessions/{sessionId}/products/search?productName=` | Search products |

Errors use `ApiErrorResponse` (`timestamp`, `status`, `code`, `message`,
`errors`). Codes: `CUSTOMER_NOT_FOUND` (404), `CUSTOMER_SESSION_NOT_FOUND` (404),
`DUPLICATE_CUSTOMER` (409), `INVALID_CUSTOMER_SESSION` (400),
`INVALID_CUSTOMER_MESSAGE` (400), `VALIDATION_ERROR` (400), `INVALID_REQUEST`
(400), `MENU_SERVICE_UNAVAILABLE` (503), `INTERNAL_ERROR` (500).

## Configuration

Key properties in `src/main/resources/application.yaml`:

```yaml
server:
  port: 8084
  address: 127.0.0.1

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db-harmoni-customer
    username: ${harmoni.user.db}
    password: ${harmoni.user.password}
  ai:
    openai:
      base-url: http://localhost:11434/v1   # local Ollama
      api-key: ollama
      chat:
        options:
          model: qwen2.5:3b
      embedding:
        model: nomic-embed-text

harmoni:
  ai:
    enabled: true
    rag:
      enabled: true
    brand-id: 1
    system-prompt: |
      You are Harmoni AI, a friendly restaurant ordering assistant.
  services:
    menu:
      base-url: http://127.0.0.1:8082
      timeout-ms: 3000
    order:
      base-url: http://127.0.0.1:8083
      timeout-ms: 3000
      store-id: 1
      store-service-types-id: 1
      default-username: Kasir1
```

Swagger UI is at `/swagger-ui.html`; the OpenAPI document is at `/api-docs`.
The checked-in credentials and secrets are development defaults; override them
per environment.

## Running locally

```bash
# 1. Create db-harmoni-customer with the customers / customer_sessions /
#    customer_messages tables (see src/test/resources/sql/schema-test.sql)
# 2. Start the Menu Service (8082) and Order Service (8083)
# 3. Start the AI provider (default: local Ollama)
ollama serve
ollama pull qwen2.5:3b
ollama pull nomic-embed-text

# 4. Build and run
./mvnw -DskipTests package
java -jar target/customer-0.0.1-SNAPSHOT.jar
# or
./mvnw spring-boot:run
```

The service listens on `127.0.0.1:8084`. In the full stack it is reached through
the gateway on port `8080`.

## Testing

```bash
./mvnw test
```

Tests cover the application services (Mockito), the controllers
(`@WebMvcTest`), the MyBatis repositories (`@MybatisTest` + H2 in MySQL mode),
the AI tools, the cart, RAG ingestion, the exception handler and a full-context
smoke test.

## Notes

- **The AI never invents POS data.** Menu, cart and order operations go through
  tools that call the Menu/Order services; the model only decides *when* to call
  them.
- **The cart is in-memory.** It is scoped to a session and cleared after
  checkout; restarting the service drops open carts.
- **Checkout is two calls.** `OrderTools.confirmOrder` creates the order
  (`POST /api/v1/order`) and then records the payment (`PUT /api/v1/order`),
  mapping `tunai`/`cash` → payment 1, `qris` → 2, `kartu`/`debit` → 3.
- **RAG is optional.** Tools are the source of truth; RAG only adds menu context
  when enabled and an embedding model is available.
- **No auth in this service.** Authentication is assumed to be handled by the
  gateway; authorization can be added as a filter/interceptor in
  `adapter.in.web` without touching the domain or application layers.
