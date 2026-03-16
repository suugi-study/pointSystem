# Point Wallet
쇼핑몰 적립 API

# 노션 URL : https://www.notion.so/Project-Record-Template-2b5c1efbb485807e9c7cddfaf0de3fc2

## 요구사항
    1. 적립
        1. 1회 적립가능 포인트는 1포인트 이상, 10만포인트 이하로 가능하며 1회 최대 적립가능 포인트는 하드코딩이 아닌 방법으로 제어할수 있어야 한다.
        2. 개인별로 보유 가능한 무료포인트의 최대금액 제한이 존재하며, 하드코딩이 아닌 별도의 방법으로 변경할 수 있어야 한다.
        3. 특정 시점에 적립된 포인트는 1원단위까지 어떤 주문에서 사용되었는지 추적할수 있어야 한다.
        4. 포인트 적립은 관리자가 수기로 지급할 수 있으며, 수기지급한 포인트는 다른 적립과 구분되어 식별할 수 있어야 한다.
        5. 모든 포인트는 만료일이 존재하며, 최소 1일이상 최대 5년 미만의 만료일을 부여할 수 있다. (기본 365일)

## 프로젝트 구성
#### 1. api :  HTTP, gRPC, CLI 등 "입구"
    •   HTTP 요청/응답 처리 (Controller)
    •  Request/Response DTO 정의
    •  인증/인가, 헤더, 쿠키, PathVariable, QueryParam 처리
    •  Validation (@Valid, @NotNull 등)
    •  에러를 어떤 JSON 형식으로 반환할지 정하기
    •  도메인 비즈니스 로직은 절대 여기서 구현하면 안 됨
#### 2. application : 유스케이스(시나리오) 레이어
    •   유스케이스 중심:
    •  “주문 생성”, “주문 결제”, “주문 취소”, “포인트 적립” 같은 시나리오
    •  트랜잭션 경계 (@Transactional)
    •  여러 도메인/애그리거트 조합
    •  예: 주문 만들고 → 재고 차감하고 → 포인트 적립 요청
    •  외부 시스템 호출(결제 API, Kafka publish 등) 조합
    •  도메인 객체에게 일을 시키는 곳 (order.pay(), pointAccount.charge())
#### 3. domain : 유스케이스(시나리오) 레이어. “프로젝트의 진짜 핵심: 비즈니스 규칙과 개념이 사는 곳”
    •  엔티티(Entity): Order, OrderLine, Member, Product …
    •  값 객체(Value Object): Money, Email, Address, DateRange …
    •  애그리거트(Aggregate) + 애그리거트 루트
    •  도메인 서비스(Domain Service):
        •  한 엔티티에 넣기 애매한 “도메인 규칙”
    •  도메인 이벤트, 리포지토리 인터페이스

~~~
com.study.ponint
 - api 
    - point 
        - PointController
        - request
            - PointRequest
        -response
            - PointResponse        
    - order
 - application 
 - domain 
    - point
        - vo
        - entity
~~~