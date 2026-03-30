# Add (2026-03-23)

## 1) 스키마 정합성 반영
- `src/main/resources/ddl.sql` 최신화: point_ledger에 `request_id` UNIQUE, `status`, `updated_at` 추가 및 인덱스 재구성.
- point_policy에 `data_type`, `unit`, `enabled`, `effective_from`, `updated_by` 추가해 타입/유효기간 관리 강화.

## 2) 정책 엔티티 보강
- `PointPolicy` 엔티티에 위 컬럼 대응 필드와 팩토리/수정 메서드 확장, 주석으로 의도 명시.

## 3) 멱등성 처리 개선
- API 요청 DTO `EarnPointRequest`에 `requestId`(선택) 추가.
- `PointController`에서 요청값 사용, 미지정 시 UUID 생성 → `EarnPointCommand`→`PointLedger`까지 전달.
- `PointLedger`는 `requestId` UNIQUE로 중복 적립을 차단하고 상태값(Status)로 만료/소진을 구분.

## 참고 파일 목록
- build: `src/main/resources/ddl.sql`
- domain: `PointPolicy`, `PointLedger`, `PointLedgerStatus`
- api/application: `EarnPointRequest`, `PointController`, `EarnPointCommand`, `PointEarnUseCase`
- infra/repo: `PointLedgerRepository`, `PointLedgerJpaRepository`
