-- ============================================================
-- 1. 포인트 정책 테이블 (하드코딩 없이 동적으로 제어)
-- ============================================================
CREATE TABLE point_policy (
    policy_id   NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_key  VARCHAR2(100)   NOT NULL,   -- 'MAX_EARN_PER_ONCE', 'MAX_HOLD_FREE_POINT'
    policy_value NUMBER(15)     NOT NULL,
    description VARCHAR2(500),
    created_at  TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_policy_key UNIQUE (policy_key)
);

COMMENT ON TABLE  point_policy              IS '포인트 정책 설정 (1회 최대 적립, 최대 보유 등)';
COMMENT ON COLUMN point_policy.policy_key   IS '정책 키: MAX_EARN_PER_ONCE(1회최대적립), MAX_HOLD_FREE_POINT(최대보유)';
COMMENT ON COLUMN point_policy.policy_value IS '정책 값 (원 단위)';

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
COMMENT ON COLUMN point_wallet.free_balance IS '현재 사용 가능한 무료 포인트 잔액';
COMMENT ON COLUMN point_wallet.version      IS 'JPA @Version - Optimistic Lock용';

CREATE INDEX idx_wallet_member_id ON point_wallet (member_id);


-- ============================================================
-- 3. 포인트 원장 테이블 (적립 단위 추적)
-- ============================================================
CREATE TABLE point_ledger (
    ledger_id       NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wallet_id       NUMBER          NOT NULL,
    amount          NUMBER(15)      NOT NULL,             -- 적립 원금
    remaining       NUMBER(15)      NOT NULL,             -- 사용 후 잔여량
    earn_type       VARCHAR2(20)    NOT NULL,             -- 'SYSTEM', 'MANUAL' (수기지급)
    source_type     VARCHAR2(50),                         -- 'ORDER', 'ADMIN_GRANT', 'EVENT' 등
    source_id       NUMBER,                               -- 주문번호 등 원천 ID
    expire_at       TIMESTAMP       NOT NULL,
    is_expired      CHAR(1)         DEFAULT 'N' NOT NULL,
    created_at      TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES point_wallet(wallet_id),
    CONSTRAINT ck_ledger_amount CHECK (amount >= 1),
    CONSTRAINT ck_ledger_remaining CHECK (remaining >= 0 AND remaining <= amount),
    CONSTRAINT ck_ledger_earn_type CHECK (earn_type IN ('SYSTEM', 'MANUAL')),
    CONSTRAINT ck_ledger_expired CHECK (is_expired IN ('Y', 'N'))
);

COMMENT ON TABLE  point_ledger              IS '포인트 적립 원장 (1원 단위 추적)';
COMMENT ON COLUMN point_ledger.earn_type    IS 'SYSTEM: 자동적립, MANUAL: 관리자 수기지급';
COMMENT ON COLUMN point_ledger.source_type  IS '적립 원천 구분 (ORDER/ADMIN_GRANT/EVENT)';
COMMENT ON COLUMN point_ledger.remaining    IS '사용 후 남은 잔액 (0이면 전액 사용)';
COMMENT ON COLUMN point_ledger.expire_at    IS '만료일시 (최소 1일~최대 5년 미만)';

CREATE INDEX idx_ledger_wallet_id   ON point_ledger (wallet_id);
CREATE INDEX idx_ledger_expire_at   ON point_ledger (expire_at);
CREATE INDEX idx_ledger_source_id   ON point_ledger (source_id);


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
COMMENT ON COLUMN point_usage_detail.ledger_id  IS '사용된 적립 원장 ID';
COMMENT ON COLUMN point_usage_detail.order_id   IS '사용된 주문 ID';
COMMENT ON COLUMN point_usage_detail.used_amount IS '해당 원장에서 사용된 금액 (1원 단위)';

CREATE INDEX idx_usage_ledger_id ON point_usage_detail (ledger_id);
CREATE INDEX idx_usage_order_id  ON point_usage_detail (order_id);