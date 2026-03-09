# 지능형 쇼핑 에이전트 (Intelligent Shopping Agent) 완벽 가이드

## A. 프로젝트 개요

본 프로젝트는 사용자의 쇼핑 여정을 처음부터 끝까지 자동화하는 지능형 쇼핑 에이전트 시스템입니다. 사용자는 자연어로 대화하며 상품을 검색하고, 리뷰를 분석받고, 장바구니를 구성하며, 최종적으로 주문을 처리할 수 있습니다. LLM(OpenAI)과 MSA(Spring Boot)를 결합하여 실제 사람과 대화하듯 유연하고 강력한 쇼핑 경험을 제공합니다.

### 핵심 기능 및 최신 업데이트

1. **최적화된 상품 검색 (Enhanced Product Discovery)**
   * **PostgreSQL Full Text Search (FTS)**: 단순 `LIKE` 검색을 넘어 PostgreSQL의 `websearch_to_tsquery`를 적용했습니다. 이제 "여름용 시원한 나이키"와 같은 복잡한 자연어 질의에 대해 `name`, `description`, `brand` 필드를 통합하여 정교한 검색 결과를 제공합니다.
   * **RAG 대체**: 제품 검색에서 별도의 벡터 RAG를 제거하고 데이터베이스 레벨의 고성능 검색을 사용함으로써 속도와 정확도를 모두 잡았습니다.

2. **자동화된 리뷰 RAG 파이프라인 (Automated Review RAG)**
   * **실시간 인덱싱**: 사용자가 리뷰를 작성하면 `review-service`가 Kafka 이벤트를 발행하고, `rag-service`가 이를 수신하여 즉시 벡터 임베딩을 생성 및 저장합니다.
   * **시맨틱 리뷰 분석**: 에이전트는 "사이즈가 어떤가요?"와 같은 질문에 대해 수천 개의 리뷰 중 의미적으로 관련된 내용을 즉시 찾아 인사이트를 제공합니다.

3. **강력한 테스트 및 안정성 (Reliable & Tested)**
   * **종합 테스트 스위트**: 모든 Java 마이크로서비스(`order`, `product`, `inventory`, `payment`, `review`)에 JUnit 5와 Mockito를 활용한 단위 및 슬라이스 테스트가 구축되어 비즈니스 로직의 견고함을 보장합니다.
   * **Saga 오케스트레이션**: 분산 트랜잭션의 모든 단계(재고 예약, 결제 승인, 확정/취소)가 테스트 코드로 검증되었습니다.

4. **자율 에이전트 워크플로우 (Autonomous Agent Workflow)**
   * **LangGraph 기반**: 5개의 전문 에이전트(상품 검색, 리뷰 분석, 장바구니, 주문, 고객센터)가 Supervisor의 조율 하에 자율적으로 협업합니다.
   * **자기 반성(Self-reflection)**: 에이전트는 검색 결과가 부족하거나 오류 발생 시 스스로 파라미터를 수정하여 재시도합니다.

---

## B. 시스템 아키텍처

```text
+---------------------+      +-----------------------+      +-----------------------+
|      사용자 (User)   | <--> | API Gateway (Spring)  | <--> | Agent Service (Python)|
+---------------------+      +-----------+-----------+      +-----------+-----------+
                                         |                              |
                  +----------------------+------------------------------+
                  |                      |                              |
+-----------------v---+      +-----------v-----------+      +-----------v-----------+
|   Product Service   |      |     Order Service     |      |      RAG Service      |
| (PostgreSQL FTS 적용)|      |  (Saga Orchestrator)  |      | (Review/Policy Vector)|
+---------------------+      +-----------+-----------+      +-----------+-----------+
          |                              |                              ^
          |      Kafka Events            |                              |
          +----------------------------> +------------------------------+
```

---

## C. 기술 스택

### 에이전트 및 RAG (Python)
* **FastAPI**: 에이전트 서비스 진입점
* **LangGraph & LangChain**: 멀티 에이전트 워크플로우 및 LLM 오케스트레이션
* **OpenAI (GPT-4o-mini / Text-Embedding-3-small)**: 추론 및 임베딩
* **PostgreSQL (pgvector)**: 벡터 데이터베이스 및 리뷰/정책 저장소

### 백엔드 마이크로서비스 (Java)
* **Spring Boot 3.x**: 핵심 비즈니스 로직
* **Spring Data JPA & PostgreSQL 16**: 데이터 영속성 및 Full Text Search
* **Spring Kafka**: 마이크로서비스 간 비동기 이벤트 통신 (Transactional Outbox)
* **JUnit 5 & Mockito**: 서비스/컨트롤러/메시징 계층의 철저한 테스트

---

## D. 마이크로서비스 상세

### 1. product-service (상품 검색의 진화)
* **FTS 적용**: 상품명뿐만 아니라 설명과 브랜드까지 포함된 통합 검색 벡터를 사용합니다.
* **Native Query**: `to_tsvector`와 `websearch_to_tsquery`를 활용해 검색 엔진 수준의 유연한 질의를 지원합니다.

### 2. review-service (실시간 피드백 루프)
* **이벤트 발행**: 리뷰 생성 시 `ReviewCreatedEvent`를 Kafka로 전송하여 RAG 시스템과 실시간 동기화합니다.
* **통계 캐싱**: Redis를 통해 상품별 평점 분포를 초고속으로 조회합니다.

### 3. order-service (신뢰할 수 있는 트랜잭션)
* **Saga Orchestrator**: Kafka를 통해 인벤토리와 결제 서비스를 조율합니다.
* **Outbox 패턴**: DB 업데이트와 이벤트 발행의 원자성을 보장합니다.

---

## E. 시작 방법 및 테스트

### 1. 환경 설정
`.env.example`을 `.env`로 복사하고 `OPENAI_API_KEY`를 입력합니다.

### 2. 실행
```bash
docker-compose up --build -d
```

### 3. 테스트 코드 실행
각 서비스 디렉토리로 이동하여 Gradle 테스트를 실행할 수 있습니다. (Gradle Wrapper 포함)
```bash
# 예: order-service 테스트 실행
cd services/order-service
./gradlew test
```

### 4. 에이전트와 대화
`http://localhost/api/chat` (Gateway 경유) 또는 에이전트 직접 호출(`:8000`)을 통해 대화를 시작하세요.
"여름에 시원하게 입을만한 나이키 셔츠 찾아주고 사이즈 리뷰도 분석해줘"와 같은 복잡한 요청이 가능합니다.

---

## F. 프로젝트 구조

* `agents/`: Python 기반 지능형 에이전트 및 RAG 로직
* `services/`: Java 기반 마이크로서비스 (Order, Product, Inventory, Payment, Review, Gateway)
* `rag-service/`: 분리된 RAG 전용 서비스 (Kafka 컨슈머 및 임베딩 파이프라인)
* `tools/seed/`: 초기 데이터(상품, 리뷰, 정책) 시딩 스크립트 및 SQL
* `libs/contracts/`: 서비스 간 공유되는 JSON Schema 이벤트 규격
