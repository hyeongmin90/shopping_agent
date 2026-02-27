# shopping_agent

## 프로젝트 개요 (Project Overview)
지능형 쇼핑 에이전트는 상품 검색, 리뷰 분석, 장바구니 관리, 주문 처리를 자율적으로 수행하며, 구매 승인 단계에서 사용자의 개입(Human-in-the-loop)을 지원하는 시스템입니다. LangGraph를 활용한 멀티 에이전트 시스템으로 구성되어 있습니다. 감독(Supervisor) 에이전트가 5개의 전문 에이전트(product_search, review_analysis, cart_management, order_management, customer_service)를 조율하여 사용자의 요청을 처리합니다.

## 기술 스택 (Tech Stack)
* **Python**: FastAPI, LangGraph, LangChain, httpx, tenacity, confluent-kafka, redis, OpenTelemetry
* **Java**: Spring Boot 3.x, Spring Data JPA, Spring Kafka, Redis, PostgreSQL 16
* **Infrastructure**: Docker Compose, Kafka (Confluent), Redis 7, PostgreSQL 16, OpenTelemetry Collector, Jaeger

## 아키텍처 (Architecture)
```text
[User] 
  |
  v
[Agent Service (FastAPI, port 8000)] 
  |
  +-- LangGraph Supervisor
  +-- 5 Specialized Agents
  |
  v (REST API Calls)
[5 Spring Boot Microservices]
  |
  +-- product-service
  +-- review-service
  +-- order-service
  +-- inventory-service
  +-- payment-service
  |
  v (Async Communication)
[Kafka Events / Commands]
```

* User는 8000번 포트의 Agent Service(FastAPI)와 통신합니다.
* Agent Service는 REST 호출을 통해 5개의 Spring Boot 마이크로서비스와 상호작용합니다.
* 마이크로서비스 간의 비동기 통신은 Kafka 이벤트와 명령을 통해 이루어집니다.
* Order Service는 Saga 패턴을 오케스트레이션하여 재고 예약, 결제 승인, 주문 확정 과정을 관리합니다.
* 각 서비스는 독립적인 PostgreSQL 데이터베이스를 가집니다(MSA per-service DB 패턴).
* Redis는 상품 및 리뷰 캐싱, 에이전트 메모리(대화 컨텍스트, 장바구니 상태) 저장에 사용됩니다.
* OpenTelemetry, OTel Collector, Jaeger를 통해 분산 추적(Distributed Tracing)을 지원합니다.

## 서비스 구성 (Service Architecture)
* **product-service (8081)**: 상품 CRUD, 페이지네이션, 필터링, 풀텍스트 검색을 지원합니다. Kafka로 ProductViewed 이벤트를 발행하며 Redis 캐싱을 적용했습니다.
* **review-service (8082)**: 리뷰 CRUD, 요약 통계, 키워드 검색 기능을 제공합니다. 리뷰 요약 데이터는 Redis에 캐시됩니다.
* **order-service (8083)**: 장바구니를 임시 주문(Draft Order) 형태로 관리합니다. 전체 상태 머신(DRAFT, PENDING_APPROVAL, PLACED, INVENTORY_RESERVING, PAYMENT_AUTHORIZING, CONFIRMED, FAILED, CANCELLED)을 구현했습니다. Saga 오케스트레이터, 트랜잭셔널 아웃박스(Transactional Outbox), 지연된 Saga를 처리하는 리퍼(Stuck saga reaper)를 포함합니다.
* **inventory-service (8084)**: 재고 관리를 담당합니다. 15분 TTL 기반의 재고 예약, 확정 및 취소 기능을 제공합니다. 동시성 제어를 위해 낙관적 락(Optimistic locking)을 사용하며, 예약 만료 스케줄러가 동작합니다.
* **payment-service (8085)**: 모의 결제 게이트웨이입니다. 결정론적(Deterministic) 결과를 반환합니다. 결제 금액을 100으로 나눈 나머지가 99이면 거절, 98이면 타임아웃, 그 외에는 승인 처리합니다. 승인, 매입, 무효화, 환불 기능을 지원합니다.
* **agent-service (8000)**: FastAPI 기반의 LangGraph 멀티 에이전트 서비스입니다. 19개의 도구를 활용하며, Redis 메모리와 자기 반성(Self-reflection) 기능을 갖추고 있습니다.

## 시작 방법 (Quick Start)
```bash
# 1. Clone
git clone https://github.com/hyeongmin90/shopping_agent.git
cd shopping_agent

# 2. Set OpenAI API key (optional - mock key works for testing backend)
cp .env.example .env
# Edit .env and set OPENAI_API_KEY=your-key

# 3. Start everything
docker-compose up --build

# 4. Access
# Agent API: http://localhost:8000
# Product API: http://localhost:8081/api/products
# Jaeger UI: http://localhost:16686
```

## API 엔드포인트 (API Endpoints)
* **Agent**: POST /api/chat, POST /api/approve, GET /health
* **Products**: GET /api/products, GET /api/products/{id}, GET /api/products/{id}/variants, GET /api/categories
* **Reviews**: GET /api/reviews/product/{id}, GET /api/reviews/product/{id}/summary, GET /api/reviews/search
* **Orders**: POST /api/orders, GET /api/orders/{id}, GET /api/orders/user/{userId}, POST /api/orders/{id}/items, POST /api/orders/{id}/checkout, POST /api/orders/{id}/approve, POST /api/orders/{id}/cancel
* **Inventory**: GET /api/inventory/product/{productId}, GET /api/inventory/check

## 에이전트 워크플로우 (Agent Workflow)
1. 사용자가 메시지를 전송하면 Supervisor가 의도를 분석합니다.
2. 분석된 의도에 따라 적절한 전문 에이전트로 요청을 라우팅합니다.
3. 에이전트는 백엔드 REST API를 호출하는 도구를 실행하고 결과를 받습니다.
4. 자기 반성(Self-reflection) 노드에서 오류를 확인합니다. 필요한 경우 다른 접근 방식으로 재시도합니다.
5. 구매 진행 시 체크아웃을 수행하고 PENDING_APPROVAL 상태로 전환합니다. 사용자에게 승인을 요청하고 승인 또는 거절을 받습니다.
6. 승인이 완료되면 Saga가 시작되어 재고 예약, 결제 승인, 주문 확정 단계를 거칩니다.

## Saga 패턴 (Saga Pattern)
* 주문이 생성되면 아웃박스 이벤트를 통해 Kafka로 재고 예약 명령을 전송합니다.
* 재고가 예약되면 Kafka 이벤트를 통해 주문 Saga가 결제 승인 명령을 내립니다.
* 결제가 승인되면 Kafka 이벤트를 통해 주문 Saga가 결제 매입 및 재고 확정을 처리합니다.
* 실패 시 보상 트랜잭션이 실행됩니다. 재고 예약을 취소하고 결제를 무효화합니다.
* 10분 이상 지연된 주문은 Stuck saga reaper가 감지하여 처리합니다.

## 설계 결정 (Design Decisions)
* 조회를 위해 REST를 사용하고 이벤트 및 명령 처리에 Kafka를 사용하는 CQRS-lite 패턴을 적용했습니다.
* 별도의 장바구니 서비스 없이 장바구니를 임시 주문(Draft Order)으로 관리합니다.
* 안정적인 이벤트 발행을 위해 트랜잭셔널 아웃박스 패턴을 도입했습니다.
* 서비스별로 idempotency_store 테이블을 두어 멱등성 있는 컨슈머를 구현했습니다.
* 동시 업데이트 처리를 위해 @Version 어노테이션을 활용한 낙관적 락을 적용했습니다.
* 이벤트 엔벨로프에 표준 메타데이터(eventId, eventType, correlationId, causationId, traceparent)를 포함했습니다.
* 진정한 MSA 데이터 격리를 위해 서비스별 PostgreSQL 데이터베이스를 구성했습니다.
* 테스트를 위해 결정론적 결과를 반환하는 모의 결제 게이트웨이를 설계했습니다.

## 프로젝트 구조 (Project Structure)
```text
shopping_agent/
├── docker-compose.yml              # 전체 스택 Docker Compose 구성
├── .env.example                    # 환경 변수 예시
├── .gitignore
├── agents/                          # Python 에이전트 서비스
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/
│       ├── main.py                  # FastAPI 앱 (lifespan 관리)
│       ├── config.py                # 환경 변수 기반 설정
│       ├── api/
│       │   ├── chat.py              # POST /api/chat, POST /api/approve
│       │   └── health.py            # GET /health
│       ├── graph/
│       │   ├── state.py             # AgentState TypedDict
│       │   ├── supervisor.py        # LangGraph 멀티 에이전트 그래프
│       │   └── tools.py             # 19개 LangChain 도구 정의
│       ├── agents/
│       │   └── specialized.py       # 에이전트별 시스템 프롬프트 및 도구 바인딩
│       ├── tools/
│       │   ├── service_clients.py   # httpx REST 클라이언트 (tenacity 재시도)
│       │   └── kafka_client.py      # Kafka 프로듀서/컨슈머
│       ├── memory/
│       │   └── redis_store.py       # Redis 상태 관리 (대화, 장바구니)
│       └── observability/
│           └── tracing.py           # OpenTelemetry 설정
├── services/                        # Spring Boot 마이크로서비스
│   ├── product-service/             # 상품 서비스 (8081)
│   ├── review-service/              # 리뷰 서비스 (8082)
│   ├── order-service/               # 주문 서비스 (8083, Saga 오케스트레이터)
│   ├── inventory-service/           # 재고 서비스 (8084)
│   └── payment-service/             # 결제 서비스 (8085)
├── infra/                           # 인프라 설정
│   ├── kafka/create-topics.sh       # Kafka 토픽 초기화 스크립트
│   └── otel/otel-collector.yml      # OpenTelemetry Collector 설정
├── libs/contracts/events/           # 공유 이벤트 스키마 (JSON Schema)
│   ├── envelope.schema.json
│   ├── order.v1.schema.json
│   ├── inventory.v1.schema.json
│   └── payment.v1.schema.json
└── tools/seed/postgres/             # 데이터베이스 시드 SQL
    ├── product-init.sql             # 상품 18개, 변형 21개, 카테고리 10개
    ├── review-init.sql              # 리뷰 22개
    ├── order-init.sql               # 주문 스키마 (Saga, Outbox, Idempotency)
    ├── inventory-init.sql           # 재고 데이터
    └── payment-init.sql             # 결제 스키마
```

## 포트 매핑 (Port Mapping)
| Service | Port |
|---------|------|
| agent-service | 8000 |
| product-service | 8081 |
| review-service | 8082 |
| order-service | 8083 |
| inventory-service | 8084 |
| payment-service | 8085 |
| postgres-product | 5432 |
| postgres-review | 5433 |
| postgres-order | 5434 |
| postgres-inventory | 5435 |
| postgres-payment | 5436 |
| kafka | 9092 |
| redis | 6379 |
| jaeger | 16686 |
| otel-collector | 4317, 4318 |

## Mock 데이터 (Mock Data)
초기 시드 데이터로 패션 및 전자제품 카테고리의 한국 상품 18개가 제공됩니다. 21개의 상품 옵션(Variant), 22개의 리뷰, 그리고 각 상품에 대한 재고 수준 데이터가 포함되어 있습니다.
