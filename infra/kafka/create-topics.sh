#!/bin/bash
# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
cub kafka-ready -b kafka:29092 1 60

echo "Creating Kafka topics..."

# Order domain
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic order.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic order.commands --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic order.dlq --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000

# Inventory domain
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic inventory.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic inventory.commands --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic inventory.dlq --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000

# Payment domain
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic payment.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic payment.commands --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic payment.dlq --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000

# Product domain (catalog changes, price updates)
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic product.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

# Review domain
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 \
  --topic review.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

echo "All Kafka topics created successfully!"
kafka-topics --list --bootstrap-server kafka:29092
