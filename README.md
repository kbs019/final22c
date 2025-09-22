Final22C 🌸 - 프리미엄 향수 쇼핑몰
22°C에서 피어나는 향의 예술 - 개인 맞춤형 향수 추천과 AI 기반 향수 가이드를 제공하는 프리미엄 향수 쇼핑몰

이미지 표시
이미지 표시
이미지 표시
이미지 표시

📋 목차
프로젝트 소개
주요 기능
기술 스택
시스템 아키텍처
설치 및 실행
API 문서
프로젝트 구조
주요 화면
기여하기
라이센스
🎯 프로젝트 소개
Final22C는 22°C의 최적 온도에서 향이 가장 아름답게 피어난다는 컨셉으로 만들어진 프리미엄 향수 쇼핑몰입니다.

핵심 가치
🤖 AI 기반 개인 맞춤 향수 추천
📊 데이터 기반 구매자 통계 제공
💬 실시간 챗봇 상담 서비스
🎨 직관적이고 세련된 UI/UX
✨ 주요 기능
🛍️ 쇼핑 기능
상품 카탈로그: 브랜드, 부향률, 노트별 필터링
장바구니 & 위시리스트: 임시 저장 및 관심 상품 관리
주문 관리: 결제, 배송 추적, 주문 내역
리뷰 시스템: 평점, 사진 리뷰, 추천/비추천
🤖 AI 서비스
향수 타입 진단: 5단계 설문을 통한 개인 취향 분석
맞춤 추천 알고리즘: 구매 이력 기반 추천
AI 향수 가이드: ChatGPT 연동 상세 향수 설명
챗봇 상담: 실시간 문의 응답
📊 데이터 분석
구매자 통계: 연령대, 성별, 계절별 구매 패턴
판매 분석: 베스트셀러, 트렌드 분석
사용자 행동 분석: 클릭, 체류시간, 구매 전환율
👤 사용자 관리
회원가입/로그인: 소셜 로그인 지원
마이페이지: 주문 내역, 적립금, 쿠폰 관리
고객센터: FAQ, 1:1 문의, 환불 신청
🛠 기술 스택
Backend
Framework: Spring Boot 3.2
Language: Java 17
ORM: MyBatis 3.0
Database: MySQL 8.0
Authentication: Spring Security 6
API Integration:
OpenAI GPT-4 API
카카오페이 결제 API
CoolSMS API
Frontend
Template Engine: Thymeleaf
CSS Framework: Bootstrap 5.3
JavaScript: Vanilla JS + jQuery
Charts: Chart.js, Recharts
Icons: Lucide React
Infrastructure
Build Tool: Maven
Database: MySQL 8.0
Email: JavaMailSender
SMS: CoolSMS SDK
🏗 시스템 아키텍처
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │   Backend       │    │   Database      │
│                 │    │                 │    │                 │
│ • Thymeleaf     │◄──►│ • Spring Boot   │◄──►│ • MySQL         │
│ • Bootstrap     │    │ • Spring Sec    │    │ • User Data     │
│ • JavaScript    │    │ • MyBatis       │    │ • Product Data  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ External APIs   │
                    │ • OpenAI GPT-4  │
                    │ • 카카오페이      │
                    │ • CoolSMS       │
                    └─────────────────┘
🚀 설치 및 실행
사전 요구사항
Java 17 이상
MySQL 8.0 이상
Maven 3.6 이상
1. 프로젝트 클론
bash
git clone https://github.com/username/final22c.git
cd final22c
2. 데이터베이스 설정
sql
CREATE DATABASE final22c CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
3. 환경 설정
application.properties 파일을 생성하고 다음 설정을 추가하세요:

properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/final22c
spring.datasource.username=your_username
spring.datasource.password=your_password

# OpenAI API
openai.api.key=your_openai_api_key

# KakaoPay
kakaopay.cid=your_kakao_cid
kakaopay.admin-key=your_kakao_admin_key

# CoolSMS
sms.api-key=your_coolsms_api_key
sms.api-secret=your_coolsms_secret
sms.from-number=your_phone_number

# Email
spring.mail.host=smtp.gmail.com
spring.mail.username=your_email
spring.mail.password=your_app_password
4. 빌드 및 실행
bash
mvn clean install
mvn spring-boot:run
5. 접속
브라우저에서 http://localhost:8080 으로 접속

📚 API 문서
주요 API 엔드포인트
상품 관리
http
GET    /main/list              # 상품 목록 조회
GET    /main/content/{id}      # 상품 상세 조회
GET    /main/brand/{id}        # 브랜드별 상품 조회
사용자 관리
http
POST   /user/login             # 로그인
POST   /user/register          # 회원가입
GET    /mypage/profile         # 마이페이지
AI 서비스
http
POST   /api/perfume/recommend  # 향수 추천
POST   /api/chat/message       # 챗봇 대화
POST   /api/survey/result      # 설문 결과 분석
주문 관리
http
POST   /order/create           # 주문 생성
GET    /order/history          # 주문 내역
POST   /payment/kakaopay       # 카카오페이 결제
📁 프로젝트 구조
src/
├── main/
│   ├── java/com/ex/final22c/
│   │   ├── controller/         # REST 컨트롤러
│   │   │   ├── main/          # 메인 페이지 컨트롤러
│   │   │   ├── product/       # 상품 관리 컨트롤러
│   │   │   ├── user/          # 사용자 관리 컨트롤러
│   │   │   └── order/         # 주문 관리 컨트롤러
│   │   ├── service/           # 비즈니스 로직
│   │   │   ├── product/       # 상품 서비스
│   │   │   ├── user/          # 사용자 서비스
│   │   │   ├── chat/          # 챗봇 서비스
│   │   │   └── payment/       # 결제 서비스
│   │   ├── repository/        # 데이터 접근 계층
│   │   ├── data/             # 엔티티 클래스
│   │   └── config/           # 설정 클래스
│   └── resources/
│       ├── templates/         # Thymeleaf 템플릿
│       │   ├── main/         # 메인 페이지
│       │   ├── user/         # 사용자 페이지
│       │   └── myPage/       # 마이페이지
│       ├── static/           # 정적 리소스
│       │   ├── css/         # 스타일시트
│       │   ├── js/          # JavaScript
│       │   └── images/      # 이미지
│       └── mapper/          # MyBatis 매퍼
🎨 주요 화면
메인 페이지
베스트셀러 향수 슬라이드
AI 추천 배너
카테고리별 상품 진열
상품 상세 페이지
360도 상품 이미지
AI 기반 향수 가이드
구매자 통계 차트
실시간 리뷰 시스템
향수 추천 페이지
5단계 성향 분석 설문
개인 맞춤 추천 결과
상황별 향수 추천
마이페이지
주문 내역 및 배송 추적
적립금 및 쿠폰 관리
찜한 상품 리스트
🌟 핵심 기능 상세
AI 향수 추천 알고리즘
java
@Service
public class PerfumeRecommendationService {
    
    // 사용자 취향 분석
    public List<Product> recommendByUserProfile(UserProfile profile) {
        // 설문 결과 기반 메인노트 매칭
        // 구매 이력 기반 협업 필터링
        // 계절/상황별 가중치 적용
    }
}
실시간 챗봇 서비스
java
@Service
public class ChatOrchestratorService {
    
    // GPT-4 연동 자연어 처리
    public String processUserMessage(String message, String userId) {
        // 의도 분석 (상품 문의, 주문 문의, 일반 상담)
        // 컨텍스트 기반 응답 생성
        // 상품 추천 및 정보 제공
    }
}
🚀 배포 및 운영
성능 최적화
데이터베이스 인덱싱: 주요 검색 조건에 인덱스 적용
이미지 최적화: WebP 포맷 사용, CDN 연동
캐싱 전략: Redis를 통한 상품 정보 캐싱
페이징 처리: 무한 스크롤 및 가상 스크롤링
보안
입력 검증: XSS, SQL Injection 방지
인증/인가: JWT 기반 토큰 인증
개인정보 보호: 민감 정보 암호화
API 보안: Rate Limiting, CORS 설정
🤝 기여하기
이 저장소를 포크합니다
새로운 기능 브랜치를 만듭니다 (git checkout -b feature/AmazingFeature)
변경사항을 커밋합니다 (git commit -m 'Add some AmazingFeature')
브랜치에 푸시합니다 (git push origin feature/AmazingFeature)
Pull Request를 생성합니다
개발 가이드라인
코드 스타일: Google Java Style Guide 준수
커밋 메시지: Conventional Commits 형식 사용
테스트: 새로운 기능에 대한 단위 테스트 작성
문서화: 주요 메서드에 JavaDoc 주석 작성
👥 팀 정보
백엔드 개발: Spring Boot, MyBatis, API 연동
프론트엔드 개발: Thymeleaf, Bootstrap, JavaScript
데이터베이스 설계: ERD 설계, 성능 최적화
AI/ML: OpenAI API 연동, 추천 알고리즘 개발
DevOps: 배포 자동화, 모니터링
📄 라이센스
이 프로젝트는 MIT 라이센스 하에 배포됩니다. 자세한 내용은 LICENSE 파일을 참고하세요.

📞 문의사항
프로젝트에 대한 질문이나 제안사항이 있으시면 언제든지 연락주세요!

이메일: final22c@example.com
이슈 트래커: GitHub Issues
22°C에서 피어나는 당신만의 향을 찾아보세요! 🌸

