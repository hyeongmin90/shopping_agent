# 지능형 쇼핑 에이전트 (Intelligent Shopping Agent) 완벽 가이드

## A. 프로젝트 개요

본 프로젝트는 사용자의 쇼핑 여정을 처음부터 끝까지 자동화하는 지능형 쇼핑 에이전트 시스템입니다. 사용자는 자연어로 대화하며 상품을 검색하고, 리뷰를 분석받고, 장바구니를 구성하며, 최종적으로 주문을 처리할 수 있습니다. 기존의 단순한 규칙 기반 챗봇을 넘어, 대규모 언어 모델(LLM)과 마이크로서비스 아키텍처(MSA)를 결합하여 실제 사람과 대화하듯 유연하고 강력한 쇼핑 경험을 제공합니다.

### 핵심 기능 5가지 상세

1. **자율 탐색형 리뷰 분석 (Autonomous Review Analysis)**
   단순히 "좋아요", "별로예요" 수준의 키워드 매칭을 넘어섭니다. 사용자가 "여름에 입기 시원한가요?"라고 물으면, 에이전트는 스스로 해당 상품의 리뷰를 검색하고, 수많은 리뷰 중에서 '통기성', '두께', '계절감'과 관련된 내용을 추출하여 종합적인 인사이트를 제공합니다. 긍정적인 의견뿐만 아니라 부정적인 의견도 공정하게 전달하여 합리적인 구매 결정을 돕습니다.
   * **동작 방식**: 사용자의 질문을 분석하여 검색 키워드를 도출하고, `search_reviews` 도구를 호출하여 관련 리뷰를 수집합니다. 이후 LLM이 수집된 리뷰를 종합하여 답변을 생성합니다.
   * **기대 효과**: 사용자는 수십 페이지의 리뷰를 직접 읽을 필요 없이, 원하는 정보만 빠르고 정확하게 얻을 수 있습니다.

2. **트랜잭션 자동화 (Transaction Automation)**
   복잡한 마이크로서비스 환경에서 발생하는 분산 트랜잭션을 에이전트가 안전하게 조율합니다. 사용자가 "결제해줘"라고 말하면, 에이전트는 백엔드의 Saga 오케스트레이터를 트리거하여 재고 예약, 결제 승인, 주문 확정이라는 일련의 복잡한 과정을 사용자 개입 없이 매끄럽게 처리합니다.
   * **동작 방식**: 에이전트가 `approve_order` 도구를 호출하면, 백엔드에서 Kafka 이벤트를 통해 각 마이크로서비스 간의 트랜잭션을 순차적으로 실행합니다.
   * **기대 효과**: 결제 중 발생할 수 있는 다양한 예외 상황(재고 부족, 결제 한도 초과 등)을 시스템이 자동으로 감지하고 롤백하여 데이터 정합성을 보장합니다.

3. **자율 장바구니 구성 (Autonomous Cart Management)**
   사용자의 예산과 상품 호환성을 스스로 검증하며 최적의 장바구니를 구성합니다. 예를 들어 "10만원 안으로 셔츠와 바지를 담아줘"라고 요청하면, 에이전트는 가격을 계산하고 예산을 초과하지 않는 선에서 최적의 조합을 찾아 장바구니에 추가합니다. 추가하기 전에 반드시 재고 상태를 확인하여 품절된 상품이 담기는 것을 방지합니다.
   * **동작 방식**: `search_products`로 상품을 찾고, `check_inventory`로 재고를 확인한 후, `add_to_cart`로 장바구니에 담습니다. 이 모든 과정이 하나의 대화 턴 안에서 자동으로 이루어집니다.
   * **기대 효과**: 사용자는 복잡한 계산이나 재고 확인 없이, 자연스러운 대화만으로 원하는 상품을 장바구니에 담을 수 있습니다.

4. **고객센터 자동화 (Automated Customer Service)**
   주문 조회, 배송 문의, 반품 및 환불 절차를 사람의 개입 없이 24시간 처리합니다. 사용자가 "어제 주문한 거 취소할래"라고 하면, 에이전트는 현재 주문 상태를 확인하고 취소 가능 여부를 판단한 뒤 즉시 취소 처리를 수행합니다. 만약 이미 배송이 시작되어 취소가 불가능하다면, 자연스럽게 반품 절차로 안내하는 유연성을 갖추고 있습니다.
   * **동작 방식**: `get_user_orders`로 주문 내역을 확인하고, 상태에 따라 `cancel_order` 또는 `request_refund` 도구를 호출합니다.
   * **기대 효과**: 고객센터 운영 비용을 절감하고, 사용자에게는 24시간 즉각적인 응답을 제공하여 만족도를 높입니다.

5. **Human-in-the-loop 구매 승인 (Human-in-the-loop Approval)**
   에이전트가 모든 것을 자율적으로 처리하지만, 실제 결제가 이루어지는 '주문 확정' 단계에서는 반드시 사용자에게 최종 승인을 요청합니다. 이는 의도치 않은 결제를 방지하고 시스템의 신뢰성을 높이는 핵심적인 안전장치입니다.
   * **동작 방식**: 에이전트가 `checkout_order`를 호출한 후, 사용자에게 승인 요청 메시지를 보냅니다. 사용자가 명시적으로 승인해야만 다음 단계로 넘어갑니다.
   * **기대 효과**: AI의 자율성과 사용자의 통제권을 적절히 조화시켜, 안전하고 신뢰할 수 있는 쇼핑 경험을 제공합니다.

### 시스템의 차별점

* **자기 반성 (Self-reflection)**: 에이전트는 API 호출이 실패하거나 원하는 결과를 얻지 못했을 때 포기하지 않습니다. 스스로 오류 메시지를 분석하고, 검색 조건을 완화하거나 다른 API를 호출하는 등 새로운 접근 방식을 시도합니다.
* **에러 복구 (Error Recovery)**: 일시적인 네트워크 장애나 백엔드 서비스의 지연 상황에서도 시스템이 중단되지 않도록 재시도(Retry) 로직과 타임아웃 처리가 촘촘하게 구현되어 있습니다.
* **컨텍스트 기억 (Context Memory)**: Redis를 활용하여 사용자와의 대화 맥락을 기억합니다. "그거 빨간색으로 바꿔줘"라고 말했을 때, '그거'가 무엇인지 이전 대화를 바탕으로 정확히 추론해냅니다.

---

## B. 시스템 아키텍처

시스템은 크게 사용자와 직접 소통하는 **Python 기반의 에이전트 계층**과, 실제 비즈니스 로직을 처리하는 **Java 기반의 마이크로서비스 계층**으로 나뉩니다.

### 1. 전체 시스템 아키텍처 (Overall System Architecture)

```text
+-----------------------------------------------------------------------------------+
|                                  사용자 (User)                                    |
|  (웹 브라우저, 모바일 앱, 터미널 등 다양한 클라이언트를 통해 시스템에 접근)       |
+-----------------------------------------------------------------------------------+
                                         | 
                                         | HTTP POST /api/chat (JSON Payload)
                                         v
+-----------------------------------------------------------------------------------+
|                          Agent Service (FastAPI / Python)                         |
|  포트: 8000                                                                       |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                              LangGraph Supervisor                           |  |
|  |  (사용자의 의도를 파악하고 가장 적합한 전문 에이전트에게 작업을 위임)       |  |
|  +-----------------------------------------------------------------------------+  |
|          |               |               |               |               |        |
|  +-------v-------+ +-----v-------+ +-----v-------+ +-----v-------+ +-----v-----+  |
|  | product_search| |review_analysis| |cart_management| |order_management| |customer_service|
|  | (상품 검색)   | | (리뷰 요약)   | | (장바구니)    | | (결제/주문)    | | (취소/환불)  |
|  +---------------+ +-------------+ +-------------+ +-------------+ +-----------+  |
|          |               |               |               |               |        |
|          +---------------+-------+-------+---------------+---------------+        |
|                                  |                                                |
|                          +-------v-------+                                        |
|                          |     Tools     | (19개의 외부 API 호출 도구)            |
|                          +---------------+                                        |
|                                  |                                                |
|                          +-------v-------+                                        |
|                          |    Reflect    | (오류 발생 시 자기 반성 및 재시도)     |
|                          +---------------+                                        |
+-----------------------------------------------------------------------------------+
                                         | 
                                         | REST API (HTTPX 비동기 호출)
                                         v
+-----------------------------------------------------------------------------------+
|                       Spring Boot Microservices (Java)                            |
|  (각 서비스는 독립적으로 배포되며, 자신만의 데이터베이스를 가짐)                  |
|                                                                                   |
|  +---------------+ +---------------+ +---------------+ +---------------+          |
|  |product-service| |review-service | | order-service | |inventory-service|        |
|  | 포트: 8081    | | 포트: 8082    | | 포트: 8083    | | 포트: 8084    |          |
|  +---------------+ +---------------+ +---------------+ +---------------+          |
|  |payment-service|                                                                |
|  | 포트: 8085    |                                                                |
|  +---------------+                                                                |
+-----------------------------------------------------------------------------------+
                                         | 
                                         | Kafka Events / Commands (비동기 메시징)
                                         v
+-----------------------------------------------------------------------------------+
|                           Confluent Kafka & Zookeeper                             |
|  (서비스 간의 결합도를 낮추고 최종 일관성을 보장하기 위한 메시지 브로커)          |
+-----------------------------------------------------------------------------------+
```

### 2. 에이전트 그래프 플로우 (Agent Graph Flow)

LangGraph를 활용한 상태 머신(State Machine) 기반의 워크플로우입니다.

```text
[ START ]
    |
    v
[ Supervisor ] <----------------------------------------------------------------+
    | (의도 분석 및 라우팅)                                                     |
    |                                                                           |
    +---> [ product_search ]   ---+                                             |
    |                             |                                             |
    +---> [ review_analysis ]  ---+                                             |
    |                             |                                             |
    +---> [ cart_management ]  ---+---> [ Tools ] ---> [ Reflect ]              |
    |                             |     (API 호출)     (결과 검증)              |
    +---> [ order_management ] ---+                                             |
    |                             |                                             |
    +---> [ customer_service ] ---+                                             |
                                                                                |
                                  (should_retry == True, 최대 15회 미만) -------+
                                                                                |
                                  (should_retry == False 또는 최대 횟수 도달) --+---> [ END ]
```

### 3. Saga 플로우 (Saga Orchestration Flow)

주문 확정 시 발생하는 분산 트랜잭션 처리 과정입니다.

```text
[ Order Service (Saga Orchestrator) ]
    |
    | 1. 상태 변경: PLACED -> INVENTORY_RESERVING
    | 2. Outbox 발행: ReserveInventory Command
    v
[ Kafka: inventory.commands ] 
    |
    v
[ Inventory Service ]
    | 3. 재고 예약 (15분 TTL)
    | 4. Outbox 발행: InventoryReserved Event
    v
[ Kafka: inventory.events ] 
    |
    v
[ Order Service (Saga Orchestrator) ]
    | 5. 상태 변경: INVENTORY_RESERVING -> PAYMENT_AUTHORIZING
    | 6. Outbox 발행: AuthorizePayment Command
    v
[ Kafka: payment.commands ] 
    |
    v
[ Payment Service ]
    | 7. 결제 승인 (Mock 로직)
    | 8. Outbox 발행: PaymentAuthorized Event
    v
[ Kafka: payment.events ] 
    |
    v
[ Order Service (Saga Orchestrator) ]
    | 9. 상태 변경: PAYMENT_AUTHORIZING -> CONFIRMED
    | 10. 최종 주문 완료 처리
```

### 4. 이벤트 플로우 (Transactional Outbox Flow)

데이터베이스 업데이트와 메시지 발행의 원자성을 보장하는 패턴입니다.

```text
[ Service A (Producer) ]
    |
    | (Begin Transaction)
    | 1. 비즈니스 엔티티 업데이트 (예: Order 상태 변경)
    | 2. OutboxEvent 테이블에 메시지 저장
    | (Commit Transaction)
    v
[ 데이터베이스 (PostgreSQL) ]
    |
    | (별도의 백그라운드 스레드)
    | 3. Outbox Publisher가 2초마다 폴링
    v
[ Kafka Topic ]
    |
    v
[ Service B (Consumer) ]
    |
    | (Begin Transaction)
    | 4. IdempotencyRecord 테이블 확인 (중복 처리 방지)
    | 5. 비즈니스 로직 처리
    | 6. IdempotencyRecord 저장
    | (Commit Transaction)
```

---

## C. 기술 스택 상세

### Python 에이전트 (Agent Service)
* **FastAPI**: 고성능 비동기 웹 프레임워크로, 에이전트의 진입점(API)을 제공합니다.
* **LangGraph**: 복잡한 멀티 에이전트 워크플로우를 상태 그래프(State Graph) 형태로 정의하고 실행합니다. 순환 구조(루프) 처리에 탁월합니다.
* **LangChain**: LLM과의 상호작용, 프롬프트 관리, 도구(Tool) 바인딩을 위한 핵심 라이브러리입니다.
* **OpenAI gpt-4o-mini**: 빠르고 비용 효율적이면서도 뛰어난 추론 능력을 갖춘 기본 LLM입니다.
* **httpx**: 백엔드 마이크로서비스와 통신하기 위한 비동기 HTTP 클라이언트입니다.
* **tenacity**: 일시적인 네트워크 오류 발생 시 지수 백오프(Exponential Backoff) 방식으로 재시도를 수행합니다.
* **confluent-kafka**: 고성능 Kafka 클라이언트로, 비동기 이벤트 처리에 사용됩니다.
* **redis**: 대화 컨텍스트, 장바구니 상태, 승인 토큰 등을 저장하는 인메모리 데이터 저장소입니다.
* **pydantic-settings**: 환경 변수를 통한 설정 관리를 타입 안전하게 수행합니다.
* **structlog**: 구조화된 JSON 로깅을 통해 디버깅과 모니터링을 용이하게 합니다.
* **OpenTelemetry**: FastAPI 라우터와 HTTPX 클라이언트를 자동 계측하여 분산 추적 데이터를 생성합니다.

### Java 백엔드 (Microservices)
* **Spring Boot 3.x**: 마이크로서비스 개발을 위한 표준 프레임워크입니다.
* **Spring Data JPA**: 객체 관계 매핑(ORM)을 통해 데이터베이스 접근을 추상화합니다.
* **Spring Kafka**: Kafka 프로듀서 및 컨슈머 구현을 단순화합니다.
* **Spring Cache (Redis)**: 반복적인 조회 쿼리의 결과를 캐싱하여 응답 속도를 높입니다.
* **PostgreSQL 16**: 각 서비스별로 독립적으로 구성된 강력한 관계형 데이터베이스입니다.
* **Lombok**: 보일러플레이트 코드(Getter, Setter 등)를 줄여줍니다.
* **Jackson**: JSON 직렬화 및 역직렬화를 담당합니다.

### 인프라 (Infrastructure)
* **Docker Compose**: 15개의 컨테이너를 하나의 파일로 정의하고 오케스트레이션합니다.
* **Confluent Kafka 7.5 + Zookeeper**: 대용량 이벤트 스트림을 안정적으로 처리합니다.
* **Redis 7.2**: 초고속 인메모리 데이터 처리를 담당합니다.
* **PostgreSQL 16**: 영구적인 데이터 저장을 담당합니다.
* **OTel Collector 0.91**: 각 서비스에서 발생하는 추적 데이터를 수집하고 가공합니다.
* **Jaeger 1.52**: 수집된 분산 추적 데이터를 시각화하여 제공하는 UI 도구입니다.

---

## D. 에이전트 시스템 상세 (Python)

### LangGraph Supervisor 구조
시스템은 `StateGraph`를 기반으로 총 8개의 노드로 구성됩니다.
1. **supervisor**: 사용자의 입력을 분석하여 다음으로 실행할 에이전트를 결정합니다.
2. **product_search**: 상품 검색을 전담하는 에이전트입니다.
3. **review_analysis**: 리뷰 분석을 전담하는 에이전트입니다.
4. **cart_management**: 장바구니 관리를 전담하는 에이전트입니다.
5. **order_management**: 주문 및 결제를 전담하는 에이전트입니다.
6. **customer_service**: 고객 지원(취소, 환불 등)을 전담하는 에이전트입니다.
7. **tools**: 에이전트가 선택한 도구(API)를 실제로 실행하는 노드입니다.
8. **reflect**: 도구 실행 결과를 분석하고 오류 발생 시 재시도 여부를 결정하는 노드입니다.

### Supervisor 라우팅 로직
Supervisor는 사용자의 입력을 분석하여 다음 에이전트를 결정합니다. 응답은 반드시 JSON 형식이어야 합니다.
* **응답 형식**: `{"next_agent": "...", "reasoning": "..."}`
* **폴백(Fallback) 로직**: LLM이 JSON 형식을 반환하지 못하거나 파싱 오류가 발생할 경우, 텍스트 내의 키워드를 분석하여 라우팅하는 안전장치가 구현되어 있습니다.
  * "상품 찾기", "검색" -> `product_search`
  * "리뷰", "평가" -> `review_analysis`
  * "장바구니", "담아" -> `cart_management`
  * "결제", "주문" -> `order_management`
  * "취소", "환불" -> `customer_service`

### AgentState TypedDict (17개 상태 필드)
에이전트 간 상태를 공유하고 워크플로우를 제어하기 위해 17개의 필드를 가진 `TypedDict`를 사용합니다.

1. **messages** (`list`): 사용자와 에이전트 간의 전체 대화 기록입니다.
2. **user_id** (`str`): 현재 사용자의 고유 식별자입니다.
3. **thread_id** (`str`): 현재 대화 세션의 고유 식별자입니다.
4. **next_agent** (`str`): Supervisor가 결정한 다음 실행할 에이전트의 이름입니다.
5. **current_agent** (`str`): 현재 실행 중인 에이전트의 이름입니다.
6. **context** (`dict`): 이전 대화에서 추출된 중요한 문맥 정보입니다.
7. **current_order_id** (`str`): 현재 진행 중인 주문(또는 장바구니)의 ID입니다.
8. **cart_items** (`list`): 현재 장바구니에 담긴 상품들의 목록입니다.
9. **search_results** (`list`): 가장 최근에 수행한 상품 검색의 결과입니다.
10. **review_analysis** (`str`): 가장 최근에 수행한 리뷰 분석 결과 텍스트입니다.
11. **inventory_status** (`dict`): 특정 상품의 재고 확인 결과입니다.
12. **requires_approval** (`bool`): 사용자에게 구매 승인을 요청해야 하는지 여부입니다.
13. **approval_data** (`dict`): 승인 요청 시 클라이언트에게 전달할 부가 데이터(주문 금액 등)입니다.
14. **error** (`str`): 도구 실행 중 발생한 오류 메시지입니다.
15. **iteration_count** (`int`): 현재 에이전트의 루프 반복 횟수입니다. 무한 루프 방지에 사용됩니다.
16. **reflection** (`str`): Reflect 노드에서 생성된 자기 반성 메시지입니다.
17. **should_retry** (`bool`): 오류 발생 시 다른 방법으로 재시도할지 여부입니다.

### 5개의 전문 에이전트 프롬프트 상세

각 에이전트는 자신의 역할에 맞는 명확한 지침(System Prompt)을 부여받습니다.

* **product_search (상품 검색 에이전트)**
  * **역할**: 사용자의 요구사항에 맞는 상품을 검색하고 추천합니다.
  * **지침**: 예산, 배송 조건, 브랜드 제약을 엄격하게 고려하십시오. 호환성이 필요한 상품(예: 스마트폰과 케이스)의 경우 교차 검증을 수행하십시오. 검색 결과가 없을 경우, 조건을 스스로 완화하여 재검색을 시도하십시오.

* **review_analysis (리뷰 분석 에이전트)**
  * **역할**: 상품의 리뷰를 분석하여 사용자에게 유용한 인사이트를 제공합니다.
  * **지침**: 단순한 평점 나열을 피하고, 사이즈, 품질, 내구성 등에 대한 구체적인 인사이트를 도출하십시오. 검증된 구매자의 리뷰를 우선적으로 참고하며, 부정적인 의견도 숨기지 말고 공정하게 전달하십시오.

* **cart_management (장바구니 관리 에이전트)**
  * **역할**: 장바구니에 상품을 추가, 삭제, 변경합니다.
  * **지침**: 상품을 추가하기 전에 반드시 예산을 검증하고 호환성을 확인하십시오. 장바구니에 담기 전에는 무조건 재고 확인 도구를 호출하여 재고가 있는지 확인해야 합니다. 작업 완료 후에는 항상 장바구니의 총액을 사용자에게 알려주십시오.

* **order_management (주문 관리 에이전트)**
  * **역할**: 체크아웃을 진행하고 주문을 확정합니다.
  * **지침**: 체크아웃을 수행하여 주문 상태를 확인하십시오. 실제 결제를 진행하기 전, 반드시 사용자에게 명시적인 승인을 요청해야 합니다. 승인 없이 임의로 주문을 확정해서는 안 됩니다.

* **customer_service (고객센터 에이전트)**
  * **역할**: 주문 조회, 취소, 환불 등 사후 처리를 담당합니다.
  * **지침**: 사용자의 주문 내역을 정확히 조회하십시오. 취소 요청 시 현재 주문 상태를 확인하고, 이미 배송이 시작되어 취소가 불가능한 경우 자연스럽게 반품 및 환불 절차로 대안을 안내하십시오.

### 에이전트별 도구(Tool) 바인딩
각 에이전트는 자신의 역할에 필요한 도구만 사용할 수 있도록 제한됩니다.
* **product_search**: `PRODUCT_TOOLS` (검색, 상세조회, 카테고리), `INVENTORY_TOOLS` (재고 확인)
* **review_analysis**: `REVIEW_TOOLS` (리뷰 조회, 요약, 검색), `PRODUCT_TOOLS` (상품 상세 확인용)
* **cart_management**: `CART_TOOLS` (장바구니 생성, 추가, 삭제, 변경), `PRODUCT_TOOLS`, `INVENTORY_TOOLS`
* **order_management**: `ORDER_TOOLS` (주문 상세, 체크아웃, 승인)
* **customer_service**: `ORDER_TOOLS` (주문 조회, 취소, 환불), `PRODUCT_TOOLS`

### 자기 반성 (Self-reflection) 메커니즘
시스템의 견고함을 높이는 핵심 기능입니다.
1. `Reflect` 노드는 가장 최근의 3개 `ToolMessage`를 검사합니다.
2. 메시지 내용 중 "error", "failed", "not found" 등의 키워드가 있는지 확인합니다.
3. 오류가 발견되었고, 현재 `iteration_count`가 `MAX_AGENT_ITERATIONS - 2`보다 작다면 재시도를 결정합니다.
4. `should_retry = True`로 설정하고, `reflection` 필드에 "이전 도구 호출에서 오류가 발생했습니다. 파라미터를 수정하여 다시 시도하십시오."라는 메시지를 주입하여 에이전트가 다른 방식으로 접근하도록 유도합니다.

### 최대 반복 횟수 (MAX_AGENT_ITERATIONS)
에이전트가 무한 루프에 빠지는 것을 방지하기 위해 최대 반복 횟수는 **15회**로 엄격하게 제한됩니다. 이 횟수를 초과하면 강제로 프로세스를 종료하고 사용자에게 오류 메시지를 반환합니다.

---

## E. 19개 도구(Tool) 상세

에이전트는 총 19개의 도구를 사용하여 외부 마이크로서비스와 상호작용합니다. 모든 도구는 비동기(`async`)로 동작하며, 실패 시 자동 재시도 로직이 적용되어 있습니다.

### 1. Product 그룹 (3개)
| 도구 이름 | 설명 | 파라미터 (이름, 타입, 필수여부) | 반환 타입 |
|---|---|---|---|
| `search_products` | 키워드, 카테고리, 가격대 등으로 상품을 검색합니다. | `keyword`(str, N), `category`(str, N), `brand`(str, N), `min_price`(int, N), `max_price`(int, N) | `dict` |
| `get_product_details` | 특정 상품의 상세 정보와 선택 가능한 옵션(Variant)을 조회합니다. | `product_id`(str, Y) | `dict` |
| `get_categories` | 시스템에 등록된 전체 카테고리 목록을 조회합니다. | 없음 | `list` |

### 2. Review 그룹 (3개)
| 도구 이름 | 설명 | 파라미터 (이름, 타입, 필수여부) | 반환 타입 |
|---|---|---|---|
| `get_product_reviews` | 특정 상품의 리뷰 목록을 조회합니다. 정렬 기준을 지정할 수 있습니다. | `product_id`(str, Y), `sort`(str, N - helpful/rating/date) | `dict` |
| `get_review_summary` | 특정 상품의 리뷰 요약 통계(평균 평점, 긍/부정 비율 등)를 조회합니다. | `product_id`(str, Y) | `dict` |
| `search_reviews` | 특정 상품의 리뷰 중에서 특정 키워드가 포함된 리뷰만 검색합니다. | `product_id`(str, Y), `keyword`(str, Y) | `dict` |

### 3. Cart / Order 그룹 (11개)
| 도구 이름 | 설명 | 파라미터 (이름, 타입, 필수여부) | 반환 타입 |
|---|---|---|---|
| `create_cart` | 사용자의 새로운 장바구니(Draft Order)를 생성합니다. | `user_id`(str, Y) | `dict` |
| `get_cart` | 사용자의 현재 활성화된 장바구니 정보를 조회합니다. | `user_id`(str, Y) | `dict` |
| `add_to_cart` | 장바구니에 상품을 추가합니다. | `order_id`(str, Y), `product_id`(str, Y), `product_name`(str, Y), `quantity`(int, Y), `unit_price`(int, Y), `variant_id`(str, N) | `dict` |
| `remove_from_cart` | 장바구니에서 특정 상품을 제거합니다. | `order_id`(str, Y), `item_id`(str, Y) | `dict` |
| `update_cart_item_quantity` | 장바구니에 담긴 상품의 수량을 변경합니다. | `order_id`(str, Y), `item_id`(str, Y), `quantity`(int, Y) | `dict` |
| `get_order_details` | 특정 주문의 상세 정보와 현재 상태를 조회합니다. | `order_id`(str, Y) | `dict` |
| `checkout_order` | 장바구니를 체크아웃하여 결제 대기(PENDING_APPROVAL) 상태로 변경합니다. | `order_id`(str, Y) | `dict` |
| `get_user_orders` | 특정 사용자의 과거 주문 내역 목록을 조회합니다. | `user_id`(str, Y) | `list` |
| `approve_order` | 사용자가 결제를 승인하면 호출되어 Saga 트랜잭션을 시작합니다. | `order_id`(str, Y) | `dict` |
| `cancel_order` | 결제 대기 중이거나 처리 중인 주문을 취소합니다. | `order_id`(str, Y), `reason`(str, N) | `dict` |
| `request_refund` | 이미 완료된 주문에 대해 환불을 요청합니다. | `order_id`(str, Y), `reason`(str, N) | `dict` |

### 4. Inventory 그룹 (2개)
| 도구 이름 | 설명 | 파라미터 (이름, 타입, 필수여부) | 반환 타입 |
|---|---|---|---|
| `check_inventory` | 특정 상품(또는 옵션)의 재고가 요청한 수량만큼 충분한지 확인합니다. | `product_id`(str, Y), `variant_id`(str, N), `quantity`(int, N) | `dict` |
| `get_product_stock` | 특정 상품의 모든 옵션에 대한 전체 재고 현황을 조회합니다. | `product_id`(str, Y) | `dict` |

---

## F. API 엔드포인트 상세

각 마이크로서비스는 RESTful API를 제공합니다.

### Agent API (포트: 8000)
* `POST /api/chat`: 사용자의 자연어 메시지를 처리하는 메인 엔드포인트입니다.
  * **요청 예시**: 
    ```json
    {
      "message": "여름용 반팔 셔츠 찾아줘",
      "user_id": "user123",
      "thread_id": "thread-abc"
    }
    ```
  * **응답 예시**: 
    ```json
    {
      "message": "네, 여름에 입기 좋은 반팔 셔츠를 찾아보았습니다...",
      "thread_id": "thread-abc",
      "requires_approval": false,
      "approval_data": null,
      "agent_used": "product_search",
      "metadata": {}
    }
    ```
* `POST /api/approve`: 사용자의 구매 승인 또는 거절 응답을 처리합니다.
  * **요청 예시**: 
    ```json
    {
      "thread_id": "thread-abc",
      "user_id": "user123",
      "approved": true,
      "order_id": "ORD-999"
    }
    ```
  * **응답 예시**: 
    ```json
    {
      "message": "결제가 승인되었습니다. 주문 처리를 시작합니다.",
      "thread_id": "thread-abc",
      "requires_approval": false,
      "approval_data": null,
      "agent_used": "order_management",
      "metadata": {}
    }
    ```
* `GET /health`: 에이전트 서비스의 헬스 체크를 수행합니다.

### Product API (포트: 8081)
* `GET /api/products`: 상품 목록을 검색합니다. (쿼리 파라미터: keyword, category, brand, minPrice, maxPrice, page, size)
* `GET /api/products/{id}`: 특정 상품의 상세 정보를 조회합니다.
* `GET /api/products/{id}/variants`: 특정 상품의 선택 가능한 옵션 목록을 조회합니다.
* `GET /api/categories`: 시스템에 등록된 카테고리 목록을 조회합니다.

### Review API (포트: 8082)
* `GET /api/reviews/product/{id}`: 특정 상품의 리뷰 목록을 페이징하여 조회합니다. (쿼리 파라미터: page, size, sort=helpful|rating|date)
* `GET /api/reviews/product/{id}/summary`: 특정 상품의 리뷰 요약 통계(평균 별점, 총 리뷰 수 등)를 조회합니다.
* `GET /api/reviews/search`: 특정 상품의 리뷰 중 키워드로 검색합니다. (쿼리 파라미터: productId, keyword)

### Order API (포트: 8083)
* `POST /api/orders`: 새로운 임시 주문(장바구니)을 생성합니다. (Body: `{ "userId": "user123" }`)
* `GET /api/orders/{id}`: 주문 상세 정보를 조회합니다.
* `GET /api/orders/user/{userId}`: 사용자의 주문 내역을 조회합니다.
* `POST /api/orders/{id}/items`: 주문에 상품을 추가합니다. (Body: `{ "productId": "P1", "variantId": "V1", "productName": "셔츠", "quantity": 2, "unitPrice": 15000 }`)
* `DELETE /api/orders/{orderId}/items/{itemId}`: 주문에서 특정 상품을 제거합니다.
* `PUT /api/orders/{orderId}/items/{itemId}`: 주문 상품의 수량을 변경합니다. (Body: `{ "quantity": 3 }`)
* `POST /api/orders/{id}/checkout`: 주문을 체크아웃 상태로 변경합니다.
* `POST /api/orders/{id}/approve`: 주문을 승인하고 Saga 트랜잭션을 시작합니다.
* `POST /api/orders/{id}/cancel`: 주문을 취소합니다. (Body: `{ "reason": "단순 변심" }`)
* `POST /api/orders/{id}/refund`: 환불을 요청합니다. (Body: `{ "reason": "사이즈 안 맞음" }`)

### Inventory API (포트: 8084)
* `GET /api/inventory/product/{productId}`: 특정 상품의 전체 재고를 조회합니다.
* `GET /api/inventory/check`: 특정 상품/옵션의 재고 가용성을 확인합니다. (쿼리 파라미터: productId, variantId, quantity)

### Payment API (포트: 8085)
* `GET /api/payments/order/{orderId}`: 특정 주문의 결제 내역을 조회합니다.

---

## G. 마이크로서비스 상세 아키텍처

각 서비스는 철저하게 독립된 데이터베이스와 도메인 로직을 가집니다.

### 1. order-service (주문 서비스)
시스템의 핵심이자 Saga 오케스트레이터 역할을 수행합니다.
* **주요 엔티티**:
  * `OrderEntity`: id, userId, status, totalAmount, currency, shippingAddress, sagaStatus, reservationId, paymentId, idempotencyKey, version, failureReason, createdAt, updatedAt
  * `OrderItem`: id, orderId, productId, variantId, productName, quantity, unitPrice
  * `OutboxEvent`: id, aggregateType, aggregateId, type, payload, createdAt
  * `SagaState`: id, orderId, currentStep, status, payload, updatedAt
  * `IdempotencyRecord`: consumerId, eventId, processedAt
* **상태 머신 (OrderStatus)**:
  * `DRAFT` (장바구니 상태) -> `PENDING_APPROVAL` (체크아웃 완료, 승인 대기) -> `PLACED` (승인 완료) -> `INVENTORY_RESERVING` (재고 예약 중) -> `PAYMENT_AUTHORIZING` (결제 승인 중) -> `CONFIRMED` (주문 확정)
  * 실패 시: `FAILED` 또는 `CANCELLED`
  * 환불 시: `REFUND_REQUESTED` -> `REFUNDED`
* **Saga 상태 (OrderSagaStatus)**: `NONE`, `RUNNING`, `COMPLETED`, `COMPENSATING`, `FAILED`
* **백그라운드 작업**:
  * `Outbox Publisher`: 2초 간격으로 실행되며, 한 번에 최대 100개의 Outbox 이벤트를 읽어 Kafka로 발행합니다.
  * `StuckSagaReaper`: 10초 간격으로 실행되며, 300초(5분) 이상 `RUNNING` 상태에 머물러 있는 Saga를 찾아 강제로 보상 트랜잭션(COMPENSATING)을 트리거합니다.

### 2. inventory-service (재고 서비스)
재고의 정확한 차감과 예약을 관리합니다.
* **주요 엔티티**:
  * `Stock`: id, productId, variantId, availableQuantity, reservedQuantity, version
  * `Reservation`: id, orderId, items(JSON), status, expiresAt
* **특징**:
  * **낙관적 락 (Optimistic Locking)**: `@Version` 어노테이션을 사용하여 동시에 여러 주문이 들어올 때 재고가 마이너스가 되는 것을 방지합니다.
  * **예약 TTL**: 재고 예약은 기본적으로 15분의 TTL(Time-To-Live)을 가집니다.
  * **ReservationExpiryScheduler**: 주기적으로 실행되어 만료된 예약을 찾아 `availableQuantity`로 복구시킵니다.

### 3. payment-service (결제 서비스)
외부 PG(Payment Gateway)사 연동을 모사하는 서비스입니다.
* **주요 엔티티**:
  * `Payment`: id, orderId, amount, currency, status, transactionId, failureReason, version
  * `Refund`: id, paymentId, amount, reason, status
* **Mock 결제 로직 (결정론적 테스트)**:
  * 결제 금액(amount)을 100으로 나눈 나머지를 기준으로 결과를 결정합니다.
  * 나머지 99: 결제 거절 (Decline) - 한도 초과 모사
  * 나머지 98: 결제 시간 초과 (Timeout) - 네트워크 지연 모사
  * 그 외: 결제 승인 (Approve) - 정상 처리

### 4. product-service (상품 서비스)
상품 카탈로그를 관리합니다.
* **주요 엔티티**: `Product`, `ProductVariant`, `Category`
* **특징**: 읽기 요청이 매우 많으므로 Redis 캐시를 적극적으로 활용합니다. 사용자가 상품 상세를 조회할 때마다 `ProductViewed` 이벤트를 Kafka로 발행하여 향후 추천 시스템에 활용할 수 있도록 합니다.

### 5. review-service (리뷰 서비스)
사용자 리뷰와 평점을 관리합니다.
* **주요 엔티티**: `Review`
* **특징**: 특정 상품의 평균 평점이나 리뷰 개수를 매번 DB에서 계산하지 않도록, `ReviewSummary` 데이터를 Redis에 캐싱하여 성능을 최적화합니다.

---

## H. Kafka 토픽 구조

모든 비동기 통신은 명확하게 정의된 토픽을 통해 이루어집니다.

| 토픽 이름 | 파티션 수 | 보존 기간 | 목적 | 프로듀서 | 컨슈머 |
|---|---|---|---|---|---|
| `order.events` | 3 | 7일 | 주문 상태 변경 알림 (주문 생성, 확정, 취소 등) | order-service | agent-service, 통계 시스템 |
| `order.commands` | 3 | 7일 | 주문 서비스로의 명령 (주문 취소 명령 등) | 외부 시스템 | order-service |
| `inventory.events` | 3 | 7일 | 재고 예약/확정/취소 처리 결과 알림 | inventory-service | order-service (Saga) |
| `inventory.commands` | 3 | 7일 | 재고 예약/확정/취소 명령 | order-service | inventory-service |
| `payment.events` | 3 | 7일 | 결제 승인/매입/취소 처리 결과 알림 | payment-service | order-service (Saga) |
| `payment.commands` | 3 | 7일 | 결제 승인/매입/무효화 명령 | order-service | payment-service |
| `product.events` | 3 | 7일 | 상품 조회, 수정 등 이벤트 | product-service | 분석 시스템 등 |
| `review.events` | 3 | 7일 | 리뷰 작성/수정/삭제 이벤트 | review-service | 통계 시스템 등 |
| `*.dlq` | 1 | 30일 | 처리 실패 메시지 보관 (Dead Letter Queue) | 각 컨슈머 | 관리자/재처리 시스템 |

* **파티션 키**: 주문 관련 토픽은 `orderId`를, 재고 관련 토픽은 `productId`를 파티션 키로 사용하여 동일한 엔티티에 대한 메시지 순서를 보장합니다.

---

## I. 이벤트 엔벨로프 구조

모든 Kafka 메시지는 표준화된 JSON 엔벨로프 형식을 따릅니다. 이를 통해 메시지의 메타데이터와 실제 페이로드를 명확히 분리합니다.

### JSON Schema
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "eventId": { "type": "string", "format": "uuid", "description": "메시지의 고유 식별자" },
    "eventType": { "type": "string", "description": "이벤트의 종류 (예: OrderPlacedEvent)" },
    "schemaVersion": { "type": "integer", "description": "페이로드 스키마 버전" },
    "occurredAt": { "type": "string", "format": "date-time", "description": "이벤트 발생 시각" },
    "producer": { "type": "string", "description": "이벤트를 발행한 서비스 이름" },
    "correlationId": { "type": "string", "format": "uuid", "description": "전체 트랜잭션을 묶는 ID (보통 orderId 기반)" },
    "causationId": { "type": "string", "format": "uuid", "description": "이 이벤트를 유발한 원인 이벤트의 ID" },
    "idempotencyKey": { "type": "string", "description": "멱등성 처리를 위한 키" },
    "traceparent": { "type": "string", "description": "OpenTelemetry 분산 추적 컨텍스트" },
    "payload": { "type": "object", "description": "실제 비즈니스 데이터" }
  },
  "required": ["eventId", "eventType", "schemaVersion", "occurredAt", "producer", "correlationId", "payload"]
}
```

### Example Event JSON
```json
{
  "eventId": "123e4567-e89b-12d3-a456-426614174000",
  "eventType": "InventoryReservedEvent",
  "schemaVersion": 1,
  "occurredAt": "2023-10-01T12:00:05Z",
  "producer": "inventory-service",
  "correlationId": "ord-987654321",
  "causationId": "cmd-123456789",
  "idempotencyKey": "inv-res-ord-987654321",
  "traceparent": "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
  "payload": {
    "reservationId": "RES-555",
    "orderId": "ord-987654321",
    "status": "RESERVED",
    "expiresAt": "2023-10-01T12:15:05Z"
  }
}
```

---

## J. Saga 패턴 상세 플로우

분산 트랜잭션을 안전하게 관리하기 위해 오케스트레이션(Orchestration) 기반의 Saga 패턴을 사용합니다. `order-service`가 중앙 통제자 역할을 합니다.

### 정상 플로우 (Happy Path)
1. **[Agent]** 사용자가 구매를 승인하면 `approve_order` 도구를 호출합니다.
2. **[Order]** Order 상태를 `PLACED`에서 `INVENTORY_RESERVING`으로 변경하고, Saga 상태를 `RUNNING`으로 설정합니다.
3. **[Order]** Outbox를 통해 `ReserveInventory` 명령을 `inventory.commands` 토픽으로 발행합니다.
4. **[Inventory]** 명령을 수신하고 재고를 예약(15분 TTL)합니다. 성공 시 `InventoryReserved` 이벤트를 `inventory.events` 토픽으로 발행합니다.
5. **[Order]** 이벤트를 수신하고 Order 상태를 `PAYMENT_AUTHORIZING`으로 변경합니다.
6. **[Order]** Outbox를 통해 `AuthorizePayment` 명령을 `payment.commands` 토픽으로 발행합니다.
7. **[Payment]** 명령을 수신하고 결제를 승인합니다. 성공 시 `PaymentAuthorized` 이벤트를 `payment.events` 토픽으로 발행합니다.
8. **[Order]** 이벤트를 수신하여 결제를 매입(Capture)하고 재고를 확정(Commit)하는 내부 로직을 처리합니다. Order 상태는 최종적으로 `CONFIRMED`가 되고, Saga 상태는 `COMPLETED`가 됩니다.

### 보상 플로우 (Compensation Flow)
* **재고 예약 실패 (InventoryReservationFailed)**: 재고 부족 등으로 예약이 실패하면, Inventory Service가 실패 이벤트를 발행합니다. Order Service는 이를 수신하고 Order 상태를 `FAILED`로 변경하며 Saga를 종료합니다.
* **결제 승인 실패 (PaymentAuthorizationFailed)**: 한도 초과 등으로 결제가 실패하면, Payment Service가 실패 이벤트를 발행합니다. Order Service는 이를 수신하고, **이미 예약된 재고를 취소하기 위해** `CancelInventoryReservation` 명령을 발행합니다. 이후 Order 상태를 `FAILED`로 변경합니다.
* **Saga 타임아웃 (Saga Timeout)**: 네트워크 단절 등으로 5분(300초) 동안 Saga가 완료되지 않으면, `StuckSagaReaper`가 개입하여 현재 진행 단계에 맞는 보상 트랜잭션(예: 재고 예약 취소, 결제 무효화)을 강제로 실행하고 상태를 `FAILED`로 변경합니다.

---

## K. 회복성 패턴 (Resilience Patterns)

시스템의 안정성과 데이터 정합성을 보장하기 위해 다양한 회복성 패턴이 적용되었습니다.

1. **멱등성 컨슈머 (Idempotent Consumers)**
   네트워크 재전송으로 인해 동일한 Kafka 메시지가 두 번 수신되더라도 안전하게 처리하기 위해, 각 서비스는 `idempotency_store` 테이블을 가집니다. `consumer_id`와 `event_id`를 복합 키로 사용하여, 이미 처리된 메시지는 무시합니다.

2. **낙관적 락 (Optimistic Locking)**
   동시에 여러 요청이 동일한 데이터를 수정하려 할 때 발생하는 갱신 손실(Lost Update)을 방지합니다. `OrderEntity`, `Payment`, `Stock` 엔티티에 `@Version` 어노테이션을 적용했습니다. 충돌 발생 시 `ObjectOptimisticLockingFailureException`이 발생하며, 상위 계층에서 재시도 루프가 동작합니다.

3. **트랜잭셔널 아웃박스 (Transactional Outbox)**
   데이터베이스 업데이트와 Kafka 메시지 발행을 하나의 로컬 트랜잭션으로 묶습니다. 비즈니스 로직이 성공하면 메시지도 반드시 발행됨을 보장(At-Least-Once)하여 메시지 유실을 원천적으로 차단합니다.

4. **HTTP 재시도 (HTTP Retry)**
   Python 에이전트는 `tenacity` 라이브러리를 사용하여 백엔드 API 호출 실패 시 재시도합니다.
   * 조건: `ConnectError`, `ReadTimeout`, `502 Bad Gateway`, `503 Service Unavailable`
   * 방식: 지수 백오프 (최소 1초, 최대 10초 대기)
   * 횟수: 최대 3회

5. **Kafka 프로듀서 신뢰성 설정**
   * `acks=all`: 모든 레플리카에 데이터가 기록됨을 보장합니다.
   * `retries=3`: 전송 실패 시 최대 3회 재시도합니다.
   * `enable.idempotence=true`: 프로듀서 측의 중복 전송을 방지합니다.

---

## L. Redis 사용 패턴

Redis는 시스템 전반에서 캐싱과 상태 관리에 핵심적인 역할을 합니다.

1. **에이전트 대화 컨텍스트 (`agent:context:{threadId}`)**
   * 용도: 사용자와 에이전트 간의 대화 기록 및 추출된 문맥 정보를 저장합니다.
   * TTL: 3600초 (1시간). 1시간 동안 대화가 없으면 세션이 초기화됩니다.
2. **장바구니 상태 (`agent:cart:{userId}`)**
   * 용도: 사용자가 장바구니에 담은 상품 목록을 임시로 저장합니다.
   * TTL: 7200초 (2시간).
3. **구매 승인 토큰 (`agent:approval:{token}`)**
   * 용도: 결제 전 사용자에게 전송되는 일회성 승인 토큰입니다.
   * TTL: 300초 (5분). 보안을 위해 읽는 즉시 소비(Consumed on read)되어 재사용이 불가능합니다.
4. **상품 및 리뷰 캐시**
   * 용도: `product-service`와 `review-service`에서 DB 조회 부하를 줄이기 위해 사용됩니다. Spring Cache 어노테이션(`@Cacheable`)을 통해 관리됩니다.

---

## M. 분산 추적 (Distributed Tracing)

마이크로서비스 환경에서의 복잡한 요청 흐름을 추적하고 병목 구간을 파악하기 위해 OpenTelemetry를 전면 도입했습니다.

1. **데이터 생성 (Instrumentation)**
   * **Python**: `opentelemetry-instrumentation-fastapi`와 `opentelemetry-instrumentation-httpx`를 사용하여 API 요청과 외부 호출을 자동 계측합니다.
   * **Java**: Spring Boot 3의 Micrometer Tracing과 OTLP 브릿지를 사용하여 추적 데이터를 생성합니다.
2. **컨텍스트 전파 (Context Propagation)**
   * HTTP 호출 시 헤더(`traceparent`)를 통해 추적 ID가 전달됩니다.
   * Kafka 비동기 메시징 시 이벤트 엔벨로프의 `traceparent` 필드를 통해 추적 컨텍스트가 끊기지 않고 이어집니다.
3. **수집 및 시각화**
   * OTel Collector가 4317(gRPC) 및 4318(HTTP) 포트로 데이터를 수집하여 일괄 처리합니다.
   * 수집된 데이터는 Jaeger로 전송되며, 개발자는 `localhost:16686`의 Jaeger UI를 통해 전체 트랜잭션의 폭포수(Waterfall) 차트를 확인할 수 있습니다.

---

## N. Docker Compose 구성

전체 시스템은 `docker-compose.yml`을 통해 15개의 컨테이너로 완벽하게 오케스트레이션됩니다.

* **시작 순서 제어**: `depends_on`과 `healthcheck`를 조합하여 인프라(DB, Redis, Kafka)가 완전히 준비된 후에만 애플리케이션 서비스가 시작되도록 보장합니다.
* **데이터 격리**: 진정한 MSA를 위해 5개의 독립적인 PostgreSQL 인스턴스(`postgres-product`, `postgres-review`, `postgres-order`, `postgres-inventory`, `postgres-payment`)가 실행됩니다.
* **볼륨 영속성**: 각 데이터베이스와 Kafka의 데이터는 도커 볼륨을 통해 컨테이너 재시작 후에도 영구적으로 보존됩니다.
* **네트워크**: 모든 컨테이너는 `shopping-net`이라는 단일 브릿지 네트워크를 공유하여 호스트 이름(예: `http://order-service:8083`)으로 서로 통신합니다.

---

## O. 시작 방법 (Quick Start) 상세

### 사전 요구사항
* Docker 및 Docker Compose (최신 버전 권장)
* Git
* (선택사항) OpenAI API 키. 백엔드 마이크로서비스 API만 테스트할 경우 필요하지 않지만, 에이전트와 대화하려면 필수입니다.

### 실행 단계

1. **저장소 클론**
   ```bash
   git clone https://github.com/hyeongmin90/shopping_agent.git
   cd shopping_agent
   ```

2. **환경 변수 설정**
   ```bash
   cp .env.example .env
   ```
   `.env` 파일을 열어 `OPENAI_API_KEY` 항목에 실제 발급받은 OpenAI API 키를 입력합니다.

3. **전체 시스템 빌드 및 실행**
   ```bash
   docker-compose up --build -d
   ```
   *최초 실행 시 데이터베이스 초기화 및 Java/Python 이미지 빌드로 인해 수 분이 소요될 수 있습니다.*

4. **서비스 상태 확인**
   모든 컨테이너가 정상적으로 실행되었는지 확인합니다.
   ```bash
   docker-compose ps
   ```

### 접속 정보
* **Agent API (FastAPI Swagger UI)**: `http://localhost:8000/docs`
* **Jaeger UI (분산 추적)**: `http://localhost:16686`
* **Kafka UI (선택적 설치 시)**: `http://localhost:8080`

### 테스트 예시 (cURL)

**1. 상품 검색 테스트 (백엔드 직접 호출)**
```bash
curl -X GET "http://localhost:8081/api/products?keyword=셔츠"
```

**2. 에이전트와 대화하기 (장바구니 담기 요청)**
```bash
curl -X POST "http://localhost:8000/api/chat"      -H "Content-Type: application/json"      -d '{
           "message": "여름용 반팔 셔츠 2개 장바구니에 담아줘. 예산은 5만원이야.",
           "user_id": "test-user-01"
         }'
```

**3. 구매 승인 처리**
에이전트가 결제 승인을 요청하면(`requires_approval: true`), 아래 API를 호출하여 승인합니다.
```bash
curl -X POST "http://localhost:8000/api/approve"      -H "Content-Type: application/json"      -d '{
           "thread_id": "<이전 응답의 thread_id>",
           "user_id": "test-user-01",
           "approved": true,
           "order_id": "<이전 응답의 approval_data.order_id>"
         }'
```

---

## P. 프로젝트 구조 (Project Structure)

```text
shopping_agent/
├── docker-compose.yml              # 전체 스택 Docker Compose 구성
├── .env.example                    # 환경 변수 템플릿
├── .gitignore
├── agents/                          # Python 에이전트 서비스 (FastAPI + LangGraph)
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/
│       ├── main.py                  # FastAPI 애플리케이션 진입점 및 라이프사이클 관리
│       ├── config.py                # pydantic-settings 기반 환경 변수 설정
│       ├── api/
│       │   ├── chat.py              # POST /api/chat, POST /api/approve 라우터
│       │   └── health.py            # GET /health 라우터
│       ├── graph/
│       │   ├── state.py             # AgentState TypedDict 정의 (17개 필드)
│       │   ├── supervisor.py        # LangGraph 라우팅 및 워크플로우 정의
│       │   └── tools.py             # 19개 LangChain 도구(Tool) 구현체
│       ├── agents/
│       │   └── specialized.py       # 5개 전문 에이전트의 시스템 프롬프트 정의
│       ├── tools/
│       │   ├── service_clients.py   # httpx 기반 백엔드 REST API 클라이언트 (tenacity 적용)
│       │   └── kafka_client.py      # 비동기 Kafka 프로듀서/컨슈머
│       ├── memory/
│       │   └── redis_store.py       # Redis 기반 상태 및 세션 관리
│       └── observability/
│           └── tracing.py           # OpenTelemetry 자동 계측 설정
├── services/                        # Spring Boot 마이크로서비스 (Java)
│   ├── product-service/             # 상품 카탈로그 서비스 (포트: 8081)
│   │   ├── src/main/java/com/shopping/product/
│   │   │   ├── controller/          # REST API 컨트롤러
│   │   │   ├── entity/              # JPA 엔티티 (Product, Variant 등)
│   │   │   ├── repository/          # Spring Data JPA 리포지토리
│   │   │   └── service/             # 비즈니스 로직 및 Redis 캐싱
│   │   └── pom.xml
│   ├── review-service/              # 리뷰 및 평점 서비스 (포트: 8082)
│   │   ├── src/main/java/com/shopping/review/
│   │   └── pom.xml
│   ├── order-service/               # 주문 및 Saga 오케스트레이터 (포트: 8083)
│   │   ├── src/main/java/com/shopping/order/
│   │   │   ├── controller/
│   │   │   ├── entity/              # OrderEntity, OutboxEvent 등
│   │   │   ├── event/               # Kafka 이벤트 퍼블리셔 및 리스너
│   │   │   ├── repository/
│   │   │   ├── saga/                # Saga 상태 머신 및 보상 트랜잭션 로직
│   │   │   └── service/
│   │   └── pom.xml
│   ├── inventory-service/           # 재고 관리 서비스 (포트: 8084)
│   │   ├── src/main/java/com/shopping/inventory/
│   │   └── pom.xml
│   └── payment-service/             # 모의 결제 게이트웨이 (포트: 8085)
│       ├── src/main/java/com/shopping/payment/
│       └── pom.xml
├── infra/                           # 인프라스트럭처 설정 파일
│   ├── kafka/create-topics.sh       # 컨테이너 시작 시 Kafka 토픽 자동 생성 스크립트
│   └── otel/otel-collector.yml      # OpenTelemetry Collector 파이프라인 설정
├── libs/contracts/events/           # 서비스 간 공유되는 이벤트 스키마 (JSON Schema)
│   ├── envelope.schema.json         # 공통 이벤트 엔벨로프 규격
│   ├── order.v1.schema.json
│   ├── inventory.v1.schema.json
│   └── payment.v1.schema.json
└── tools/seed/postgres/             # 데이터베이스 초기 시드(Seed) SQL 스크립트
    ├── product-init.sql             # 카테고리 10개, 상품 18개, 옵션 21개 데이터
    ├── review-init.sql              # 긍/부정 리뷰 22개 데이터
    ├── order-init.sql               # 주문, Outbox, Idempotency 테이블 스키마
    ├── inventory-init.sql           # 초기 재고 수량 데이터
    └── payment-init.sql             # 결제 관련 테이블 스키마
```

---

## Q. Mock 데이터 상세

시스템을 설치하자마자 바로 풍부한 테스트를 진행할 수 있도록, 한국어 기반의 현실적인 시드 데이터가 포함되어 있습니다.

* **카테고리 (10개)**: 남성의류, 여성의류, 전자기기, 가전제품, 스포츠, 도서 등
* **상품 (18개)**: "쿨링 린넨 반팔 셔츠", "노이즈 캔슬링 무선 이어폰", "초경량 러닝화" 등 현실적인 상품명과 설명, 가격 데이터
* **옵션 (21개)**: 의류의 경우 사이즈(S, M, L, XL)와 색상(블랙, 화이트, 네이비), 전자기기의 경우 저장 용량(128GB, 256GB) 등
* **리뷰 (22개)**: 에이전트의 리뷰 분석 능력을 테스트할 수 있도록 "재질이 시원해서 좋아요", "생각보다 배송이 느렸어요", "사이즈가 한 치수 작게 나온 것 같아요" 등 긍정적/부정적 의견이 혼합된 데이터
* **결제 게이트웨이 규칙**: `payment-service`는 결제 금액을 100으로 나눈 나머지에 따라 결과를 결정합니다.
  * 나머지 99: 결제 거절 (Decline)
  * 나머지 98: 결제 시간 초과 (Timeout)
  * 그 외: 결제 승인 (Approve)

---

## R. 환경 변수 (Environment Variables)

`.env` 파일 또는 `docker-compose.yml`을 통해 시스템의 동작을 제어할 수 있습니다.

| 변수명 | 기본값 | 설명 |
|---|---|---|
| `OPENAI_API_KEY` | (없음) | OpenAI API 키 (필수) |
| `OPENAI_MODEL` | `gpt-4o-mini` | 에이전트가 사용할 LLM 모델명 |
| `MAX_AGENT_ITERATIONS` | `15` | 에이전트의 최대 루프 반복 횟수 (무한 루프 방지) |
| `APPROVAL_TIMEOUT_SECONDS` | `300` | 사용자의 구매 승인을 기다리는 최대 시간 (초) |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka 브로커 접속 주소 |
| `REDIS_URL` | `redis://redis:6379/0` | Redis 서버 접속 주소 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4317` | 분산 추적 데이터를 전송할 OTel Collector 주소 |
| `LOG_LEVEL` | `INFO` | 애플리케이션 로깅 레벨 (DEBUG, INFO, WARN, ERROR) |

---

## S. 설계 결정 (Design Decisions) 상세

본 프로젝트를 개발하며 내린 주요 아키텍처 및 설계 결정 사항과 그 이유는 다음과 같습니다.

1. **CQRS-lite 패턴 적용**
   * **결정**: 데이터 조회(Query)는 REST API를 통해 동기적으로 처리하고, 상태 변경(Command)은 Kafka 이벤트를 통해 비동기적으로 처리합니다.
   * **이유**: 쇼핑몰의 특성상 상품 검색과 리뷰 조회 등 읽기 요청이 압도적으로 많습니다. 읽기와 쓰기 경로를 분리하여 각각에 맞는 최적화(예: 읽기 경로에 Redis 캐시 적극 활용)를 적용하기 위함입니다.

2. **장바구니를 임시 주문(Draft Order)으로 관리**
   * **결정**: 별도의 장바구니(Cart) 마이크로서비스를 만들지 않고, `order-service`에서 `DRAFT` 상태의 주문으로 장바구니를 관리합니다.
   * **이유**: 장바구니 데이터가 실제 주문으로 전환될 때 발생하는 데이터 복제와 정합성 문제를 원천적으로 제거할 수 있습니다. 체크아웃 시 단순히 상태만 `DRAFT`에서 `PENDING_APPROVAL`로 변경하면 되므로 로직이 매우 단순해집니다.

3. **Transactional Outbox 패턴 도입**
   * **결정**: DB 업데이트 후 직접 Kafka 프로듀서를 호출하지 않고, 동일 트랜잭션 내에서 Outbox 테이블에 이벤트를 저장한 뒤 백그라운드 워커가 이를 발행합니다.
   * **이유**: DB 커밋은 성공했는데 Kafka 전송이 실패하거나, 반대로 Kafka 전송은 성공했는데 DB 커밋이 실패하는 '이중 기록 문제(Dual Write Problem)'를 해결하여 완벽한 데이터 일관성을 보장하기 위함입니다.

4. **Orchestrated Saga 패턴 선택**
   * **결정**: 각 서비스가 이벤트를 주고받으며 알아서 트랜잭션을 이어가는 Choreography 방식 대신, `order-service`가 중앙에서 전체 흐름을 통제하는 Orchestration 방식을 선택했습니다.
   * **이유**: 결제 프로세스는 단계가 명확하고 롤백(보상 트랜잭션) 로직이 복잡합니다. 중앙 통제자가 있으면 전체 트랜잭션의 현재 상태를 한눈에 파악하기 쉽고, 타임아웃 처리나 강제 롤백 관리가 훨씬 용이합니다.

5. **서비스별 독립 데이터베이스 (Per-Service DB)**
   * **결정**: 5개의 마이크로서비스가 각각 자신만의 PostgreSQL 컨테이너를 가집니다.
   * **이유**: 진정한 마이크로서비스 아키텍처의 핵심인 '데이터 격리'를 실현하기 위함입니다. 한 서비스의 DB 스키마 변경이 다른 서비스에 영향을 주지 않으며, 특정 서비스에 트래픽이 몰려 DB가 다운되더라도 다른 서비스는 정상 동작할 수 있습니다.

6. **LangChain 대신 LangGraph 채택**
   * **결정**: 에이전트 워크플로우 구축에 LangChain의 AgentExecutor 대신 LangGraph를 사용했습니다.
   * **이유**: 쇼핑 에이전트는 '검색 -> 실패 -> 조건 완화 -> 재검색'과 같은 순환 구조(루프)와 상태 관리가 필수적입니다. LangGraph는 상태 머신 기반으로 이러한 복잡한 제어 흐름을 명시적이고 안정적으로 구현할 수 있게 해줍니다.

7. **단일 거대 에이전트 대신 Supervisor 패턴 적용**
   * **결정**: 하나의 프롬프트에 모든 지시사항을 넣는 대신, 역할을 분담한 5개의 전문 에이전트를 두고 Supervisor가 이를 조율하도록 설계했습니다.
   * **이유**: 단일 프롬프트가 너무 길어지면 LLM이 지시사항을 잊어버리거나 환각(Hallucination)을 일으킬 확률이 높아집니다. 역할을 분리함으로써 각 에이전트의 프롬프트를 간결하게 유지하고, 도구(Tool) 접근 권한을 최소 권한 원칙에 따라 제한할 수 있습니다.

---

## T. 제한사항 및 향후 개선 (Limitations & Future Work)

현재 시스템은 핵심적인 쇼핑 여정과 분산 트랜잭션 처리에 집중하여 완성되었으나, 실제 상용 서비스 수준으로 발전하기 위해 다음과 같은 개선 계획을 가지고 있습니다.

1. **보안 및 인증 (Security & Authentication)**
   * 현재는 API 요청 시 `user_id`를 평문으로 전달하는 모의 인증 방식을 사용하고 있습니다.
   * **개선 계획**: Spring Security와 OAuth2/JWT 기반의 강력한 인증 및 인가(Authorization) 계층을 API Gateway(예: Spring Cloud Gateway) 수준에 추가할 예정입니다.

2. **RAG 기반 상품 추천 고도화**
   * 현재의 상품 검색은 RDBMS의 텍스트 검색에 의존하고 있어, "힙한 느낌의 옷"과 같은 추상적인 쿼리 처리에 한계가 있습니다.
   * **개선 계획**: 상품 설명과 리뷰 데이터를 벡터 데이터베이스(예: Milvus, Pinecone)에 임베딩하여 저장하고, RAG(Retrieval-Augmented Generation) 파이프라인을 구축하여 시맨틱 검색(Semantic Search)을 지원할 예정입니다.

3. **서킷 브레이커 (Circuit Breaker) 도입**
   * 현재는 `tenacity`를 통한 단순 재시도 로직만 구현되어 있습니다.
   * **개선 계획**: 특정 백엔드 서비스가 완전히 다운되었을 때 불필요한 API 호출이 누적되어 전체 시스템 장애로 번지는 것을 막기 위해, Python 에이전트 계층에 서킷 브레이커 패턴을 적용할 계획입니다.

4. **프론트엔드 UI 개발**
   * 현재는 터미널이나 cURL, Swagger UI를 통해서만 에이전트와 상호작용할 수 있습니다.
   * **개선 계획**: React 또는 Next.js를 활용하여, 사용자가 에이전트와 채팅하며 실시간으로 장바구니와 상품 이미지를 확인할 수 있는 직관적인 웹 기반 UI를 개발할 예정입니다.
