-- ============================================================
-- 1. 포인트 정책 테이블 (하드코딩 없이 동적으로 제어)
-- ============================================================
CREATE TABLE point_policy (
    policy_id   NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_key  VARCHAR2(100)   NOT NULL,   -- 'MAX_EARN_PER_ONCE', 'MAX_HOLD_FREE_POINT'
    policy_value NUMBER(15)     NOT NULL,
    description VARCHAR2(500),
    data_type    VARCHAR2(30)   DEFAULT 'NUMBER' NOT NULL,
    unit         VARCHAR2(30),
    enabled      NUMBER(1)      DEFAULT 1 NOT NULL,
    effective_from TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by   VARCHAR2(100),
    created_at  TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_policy_key UNIQUE (policy_key),
    CONSTRAINT ck_policy_enabled CHECK (enabled IN (0,1))
);

COMMENT ON TABLE  point_policy              IS '포인트 정책 설정 (1회 최대 적립, 최대 보유 등)';
COMMENT ON COLUMN point_policy.policy_id   IS '포인트 정책 PK';
COMMENT ON COLUMN point_policy.policy_key   IS '정책 키: MAX_EARN_PER_ONCE(1회최대적립), MAX_HOLD_FREE_POINT(최대보유)';
COMMENT ON COLUMN point_policy.policy_value IS '정책 값 (원 단위)';
COMMENT ON COLUMN point_policy.description  IS '정책 설명 및 비고';
COMMENT ON COLUMN point_policy.data_type    IS '값 타입(NUMBER/STRING 등)';
COMMENT ON COLUMN point_policy.unit         IS '단위(POINT/DAY 등)';
COMMENT ON COLUMN point_policy.enabled      IS 'Y/N 사용 여부';
COMMENT ON COLUMN point_policy.effective_from IS '정책 적용 시작 시점';
COMMENT ON COLUMN point_policy.updated_by   IS '최근 수정자';
COMMENT ON COLUMN point_policy.created_at   IS '생성 일시 (자동 입력)';
COMMENT ON COLUMN point_policy.updated_at   IS '수정 일시 (자동 업데이트)';

-- 기본 정책 데이터
INSERT INTO point_policy (policy_key, policy_value, description)
VALUES ('MAX_EARN_PER_ONCE', 100000, '1회 최대 적립 포인트 (10만)');
INSERT INTO point_policy (policy_key, policy_value, description)
VALUES ('MAX_HOLD_FREE_POINT', 1000000, '개인별 무료 포인트 최대 보유 금액 (100만)');


-- ============================================================
-- 2. 포인트 지갑 테이블 (회원별 잔액 집계)
-- ============================================================
CREATE TABLE point_wallet (
    wallet_id       NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id       NUMBER          NOT NULL,
    free_balance    NUMBER(15)      DEFAULT 0 NOT NULL,   -- 현재 보유 무료포인트
    total_earned    NUMBER(15)      DEFAULT 0 NOT NULL,   -- 누적 적립
    total_used      NUMBER(15)      DEFAULT 0 NOT NULL,   -- 누적 사용
    version         NUMBER          DEFAULT 0 NOT NULL,   -- Optimistic Lock
    created_at      TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_wallet_member UNIQUE (member_id),
    CONSTRAINT ck_wallet_balance CHECK (free_balance >= 0)
);

COMMENT ON TABLE  point_wallet              IS '회원별 포인트 지갑 (잔액 집계)';
COMMENT ON COLUMN point_wallet.wallet_id    IS '포인트 지갑 PK';
COMMENT ON COLUMN point_wallet.member_id    IS '회원 ID (1:1 고유 지갑)';
COMMENT ON COLUMN point_wallet.free_balance IS '현재 사용 가능한 무료 포인트 잔액';
COMMENT ON COLUMN point_wallet.total_earned IS '누적 적립 총액';
COMMENT ON COLUMN point_wallet.total_used   IS '누적 사용 총액';
COMMENT ON COLUMN point_wallet.version      IS 'JPA @Version - Optimistic Lock용';
COMMENT ON COLUMN point_wallet.created_at   IS '지갑 생성 시각';
COMMENT ON COLUMN point_wallet.updated_at   IS '지갑 최종 수정 시각';

CREATE INDEX idx_wallet_member_id ON point_wallet (member_id);


-- ============================================================
-- 3. 포인트 원장 테이블 (적립 단위 추적)
-- ============================================================
CREATE TABLE point_ledger (
    ledger_id       NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wallet_id       NUMBER          NOT NULL,
    amount          NUMBER(15)      NOT NULL,             -- 적립 원금
    remaining       NUMBER(15)      NOT NULL,             -- 사용 후 잔여량
    request_id      VARCHAR2(100)   NOT NULL,             -- 멱등성 키 (중복 적립 방지)
    earn_type       VARCHAR2(20)    NOT NULL,             -- 'SYSTEM', 'MANUAL' (수기지급)
    source_type     VARCHAR2(50),                         -- 'ORDER', 'ADMIN_GRANT', 'EVENT' 등
    source_id       NUMBER,                               -- 주문번호 등 원천 ID
    expire_at       TIMESTAMP       NOT NULL,
    status          VARCHAR2(20)    DEFAULT 'ACTIVE' NOT NULL, -- ACTIVE/EXHAUSTED/EXPIRED
    created_at      TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES point_wallet(wallet_id),
    CONSTRAINT ck_ledger_amount CHECK (amount >= 1),
    CONSTRAINT ck_ledger_remaining CHECK (remaining >= 0 AND remaining <= amount),
    CONSTRAINT ck_ledger_earn_type CHECK (earn_type IN ('SYSTEM', 'MANUAL')),
    CONSTRAINT ck_ledger_status CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED')),
    CONSTRAINT uq_ledger_request UNIQUE (request_id)
);

COMMENT ON TABLE  point_ledger              IS '포인트 적립 원장 (1원 단위 추적)';
COMMENT ON COLUMN point_ledger.ledger_id    IS '포인트 원장 PK';
COMMENT ON COLUMN point_ledger.wallet_id    IS '소유 지갑 ID (FK)';
COMMENT ON COLUMN point_ledger.amount       IS '적립 원금 (최초 적립 금액)';
COMMENT ON COLUMN point_ledger.earn_type    IS 'SYSTEM: 자동적립, MANUAL: 관리자 수기지급';
COMMENT ON COLUMN point_ledger.source_type  IS '적립 원천 구분 (ORDER/ADMIN_GRANT/EVENT)';
COMMENT ON COLUMN point_ledger.source_id    IS '원천 엔티티 식별자 (주문번호 등)';
COMMENT ON COLUMN point_ledger.remaining    IS '사용 후 남은 잔액 (0이면 전액 사용)';
COMMENT ON COLUMN point_ledger.expire_at    IS '만료일시 (최소 1일~최대 5년 미만)';
COMMENT ON COLUMN point_ledger.status       IS 'ACTIVE/EXHAUSTED/EXPIRED 상태값';
COMMENT ON COLUMN point_ledger.request_id   IS 'API 멱등성 키 (중복 적립 방지)';
COMMENT ON COLUMN point_ledger.created_at   IS '적립 기록 생성 시각';
COMMENT ON COLUMN point_ledger.updated_at   IS '적립 기록 최종 수정 시각';

CREATE INDEX idx_ledger_wallet_expire ON point_ledger (wallet_id, expire_at, status);
CREATE INDEX idx_ledger_source        ON point_ledger (source_type, source_id);


-- ============================================================
-- 4. 포인트 사용 상세 테이블 (어느 주문에서 얼마 썼는지 추적)
-- ============================================================
CREATE TABLE point_usage_detail (
    detail_id       NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ledger_id       NUMBER          NOT NULL,             -- 어느 적립분에서 사용했는지
    order_id        NUMBER          NOT NULL,
    used_amount     NUMBER(15)      NOT NULL,             -- 1원 단위 사용금액
    used_at         TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_usage_ledger FOREIGN KEY (ledger_id) REFERENCES point_ledger(ledger_id),
    CONSTRAINT ck_usage_amount CHECK (used_amount >= 1)
);

COMMENT ON TABLE  point_usage_detail            IS '포인트 사용 상세 - 적립분별 어느 주문에서 사용했는지 추적';
COMMENT ON COLUMN point_usage_detail.detail_id  IS '포인트 사용 상세 PK';
COMMENT ON COLUMN point_usage_detail.ledger_id  IS '사용된 적립 원장 ID';
COMMENT ON COLUMN point_usage_detail.order_id   IS '사용된 주문 ID';
COMMENT ON COLUMN point_usage_detail.used_amount IS '해당 원장에서 사용된 금액 (1원 단위)';
COMMENT ON COLUMN point_usage_detail.used_at    IS '포인트 사용 일시';

CREATE INDEX idx_usage_ledger_id ON point_usage_detail (ledger_id);
CREATE INDEX idx_usage_order_id  ON point_usage_detail (order_id);
