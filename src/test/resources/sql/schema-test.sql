-- Portable DDL for integration tests (H2 in MySQL compatibility mode).
-- Functional parity with V1 + V2 Flyway migrations; MySQL-only clauses
-- (ENGINE/CHARSET/ON UPDATE) and secondary indexes are omitted because they
-- do not affect query semantics under test.

CREATE TABLE customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id INT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(150),
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customers_phone UNIQUE (phone)
);

CREATE TABLE customer_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT,
    session_token VARCHAR(100) NOT NULL,
    source VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customer_sessions_token UNIQUE (session_token),
    CONSTRAINT fk_customer_sessions_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE TABLE customer_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_messages_session FOREIGN KEY (session_id) REFERENCES customer_sessions (id)
);
