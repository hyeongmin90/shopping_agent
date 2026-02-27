-- Order Service Database Schema

-- Orders (cart starts as DRAFT)
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    total_amount INTEGER DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'KRW',
    shipping_address JSONB,
    saga_status VARCHAR(30) DEFAULT 'NONE',
    reservation_id UUID,
    payment_id UUID,
    idempotency_key VARCHAR(100) UNIQUE,
    version INTEGER DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PLACED', 'INVENTORY_RESERVING', 'PAYMENT_AUTHORIZING', 'CONFIRMED', 'FAILED', 'CANCELLED', 'REFUND_REQUESTED', 'REFUNDED'))
);

-- Order Items
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL,
    variant_id UUID,
    product_name VARCHAR(255),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Transactional Outbox
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    correlation_id UUID,
    causation_id UUID,
    idempotency_key VARCHAR(100),
    published BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

-- Idempotency Store
CREATE TABLE IF NOT EXISTS idempotency_store (
    consumer_id VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_id, event_id)
);

-- Saga State
CREATE TABLE IF NOT EXISTS saga_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    current_step VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    timeout_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    context JSONB DEFAULT '{}',
    CONSTRAINT chk_saga_status CHECK (status IN ('RUNNING', 'COMPLETED', 'COMPENSATING', 'FAILED'))
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_outbox_unpublished ON outbox_events(published, created_at) WHERE NOT published;
CREATE INDEX idx_saga_status ON saga_state(status, timeout_at);
CREATE INDEX idx_idempotency ON idempotency_store(consumer_id, event_id);
