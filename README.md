# 🚚 SCM 택배 & 배송 관제 시스템 (Courier System)

B2B Supply Chain Management 아키텍처에 맞춰 물류(WMS) 출고 수신(`POST /waybills`), 실시간 관제 및 배송 완료 시 Kafka 이벤트 발행을 담당하는 서비스입니다.

---

## 📌 핵심 흐름 및 REST API
1. **`POST /api/v1/waybills`**: 물류(WMS) 시스템으로부터 출고/배송 시작 요청 수신 및 배차 생성
2. **`POST /api/v1/tracking`**: 차량 GPS 및 PDA 단말기 실시간 위치 데이터 수신
3. **`POST /api/v1/pods`**: 인도 증빙(POD 사진/서명) 완료 처리 ➔ **Kafka(MSK) `배송완료` 이벤트 발행** ➔ Slack(Notice) 알림 전송

---

## 🛠️ System Architecture Integration
- **Event Streaming**: Amazon MSK (Kafka) 연동
- **Data Tier**: PostgreSQL (운송 메타데이터), Redis (실시간 위치), S3 (POD 파일)