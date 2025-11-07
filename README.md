# Final22C 🌸 - 프리미엄 향수 쇼핑몰

> **22°C에서 피어나는 향의 예술** - 개인 맞춤형 향수 추천과 AI 기반 향수 가이드를 제공하는 프리미엄 향수 쇼핑몰

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Oracle]([https://img.shields.io/badge/MySQL-8.0-blue.svg](https://img.shields.io/badge/oracle-blue.svg))](https://www.oracle.com/kr/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 📋 목차

- [프로젝트 소개](#-프로젝트-소개)
- [주요 기능](#-주요-기능)
- [기술 스택](#-기술-스택)
- [시스템 아키텍처](#-시스템-아키텍처)
- [설치 및 실행](#-설치-및-실행)
- [API 문서](#-api-문서)
- [프로젝트 구조](#-프로젝트-구조)
- [주요 화면](#-주요-화면)
- [기여하기](#-기여하기)
- [라이센스](#-라이센스)

## 🎯 프로젝트 소개

Final22C는 **22°C의 최적 온도에서 향이 가장 아름답게 피어난다**는 컨셉으로 만들어진 프리미엄 향수 쇼핑몰입니다. 

### 핵심 가치
- 🤖 **AI 기반 개인 맞춤 향수 추천**
- 📊 **데이터 기반 구매자 통계 제공**
- 🎨 **직관적인 UI/UX**

## ✨ 주요 기능

### 🛍️ 쇼핑 기능
- **상품 카탈로그**: 브랜드, 부향률, 노트별 필터링
- **장바구니 & 위시리스트**: 임시 저장 및 관심 상품 관리
- **주문 관리**: 결제, 배송 추적, 주문 내역

### 🤖 AI 서비스
- **향수 타입 진단**: 5단계 설문을 통한 개인 취향 분석  
- **AI 향수 가이드**: **OpenAI GPT-4 API** 연동으로 상품별 맞춤 향수 설명 자동 생성
- **개인화 추천 분석**: 성별/연령대에 따른 AI 기반 향수 착용 시나리오 분석
- **관리자 챗봇**: **실시간 SQL 생성**으로 매출, 주문, 고객 데이터를 자연어로 조회
- **비즈니스 인텔리전스**: AI가 차트 생성 및 데이터 트렌드 분석 제공

### 📊 데이터 분석
- **구매자 통계**: 연령대, 성별 구매 패턴
- **판매 분석**: 베스트셀러, 트렌드 분석
- **사용자 행동 분석**: 클릭, 체류시간, 구매 전환율

### 👤 사용자 관리
- **마이페이지**: 주문 내역, 적립금 관리, 환불 신청
- **고객센터**: FAQ, 1:1 문의

## 🛠 기술 스택

### Backend
- **Framework**: Spring Boot 3.2
- **Language**: Java 17
- **ORM**: MyBatis 3.0
- **Database**: Oracle DB
- **Authentication**: Spring Security 6
- **API Integration**: 
  - **OpenAI GPT-4 API** (향수 가이드 생성, 자연어 SQL 변환)
  - 카카오페이 결제 API
  - CoolSMS API
  - SMPT Gmail API

### Frontend
- **Template Engine**: Thymeleaf
- **CSS Framework**: Bootstrap 5.3
- **JavaScript**: Vanilla JS + jQuery
- **Charts**: Chart.js, Recharts

### Infrastructure
- **Build Tool**: Gradle
- **Database**: Oracle DB
- **Email**: JavaMailSender
- **SMS**: CoolSMS SDK

## 🏗 시스템 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │   Backend       │    │   Database      │
│                 │    │                 │    │                 │
│ • Thymeleaf     │◄──►│ • Spring Boot   │◄──►│ • Oracle        │
│ • Bootstrap     │    │ • Spring Sec    │    │ • User Data     │
│ • JavaScript    │    │ • Oracle        │    │ • Product Data  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ External APIs   │
                    │ • OpenAI GPT-4  │
                    │ • 카카오페이     │
                    │ • CoolSMS       │
                    └─────────────────┘
```

## 🚀 설치 및 실행

### 사전 요구사항
- Java 17 이상
- Oracle 이상
- Gradle 이상

### 1. 프로젝트 클론
```bash
git clone https://github.com/username/final22c.git
cd final22c
```

### 2. 데이터베이스 설정
```sql
CREATE DATABASE final22c CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 환경 설정
`application.properties` 파일을 생성하고 다음 설정을 추가하세요:

```properties
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
```

### 4. 빌드 및 실행
```bash
mvn clean install
mvn spring-boot:run
```

### 5. 접속
브라우저에서 `http://localhost:8080` 으로 접속

## 📚 API 문서

### 주요 API 엔드포인트

#### 상품 관리
```http
GET    /main/list              # 상품 목록 조회
GET    /main/content/{id}      # 상품 상세 조회
GET    /main/brand/{id}        # 브랜드별 상품 조회
```

#### 사용자 관리
```http
POST   /user/login             # 로그인
POST   /user/register          # 회원가입
GET    /mypage/profile         # 마이페이지
```

#### AI 서비스
```http
POST   /api/perfume/recommend  # 향수 추천
POST   /api/chat               # 관리자 AI 챗봇 (자연어 → SQL)
POST   /api/ai/query           # 비즈니스 데이터 조회
GET    /main/content/{id}/ai-guide # AI 향수 가이드 생성
POST   /main/content/{id}/ai/persona-recommendation # 개인화 분석
```

#### 주문 관리
```http
POST   /order/create           # 주문 생성
GET    /order/history          # 주문 내역
POST   /payment/kakaopay       # 카카오페이 결제
```

## 📁 프로젝트 구조

```
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
```

## 🎨 주요 화면

### 메인 페이지
- 베스트셀러 향수 슬라이드
- AI 추천 배너
- 카테고리별 상품 진열

### 상품 상세 페이지
- AI 기반 향수 가이드
- 구매자 통계 차트
- 실시간 리뷰 시스템

### 향수 추천 페이지
- 5단계 성향 분석 설문
- 개인 맞춤 추천 결과
- 상황별 향수 추천

### 마이페이지
- 주문 내역 및 배송 추적
- 적립금 관리
- 찜한 상품 리스트

## 🌟 핵심 기능 상세

### AI 기반 향수 가이드 시스템
```java
@Service
public class ProductDescriptionService {
    
    // OpenAI DeepSeek를 활용한 상품별 향수 가이드 자동 생성
    public String generateGuideContent(Product product) {
        String prompt = buildPromptBasedOnNoteStructure(product);
        return chatService.generateDescription(prompt); // DeepSeek 호출
    }
}
```

### 관리자 AI 챗봇 (자연어 → SQL)
```java
@Service 
public class ChatOrchestratorService {
    
    // 자연어 질문을 SQL로 변환하여 실시간 비즈니스 데이터 조회
    public AiResult handle(String userMessage, Principal principal) {
        String sql = chat.generateSql(userMessage, SCHEMA_DOC);
        List<Map<String, Object>> data = sqlExecutor.execute(sql);
        String summary = chat.summarize(userMessage, sql, data);
        return new AiResult(summary, sql, data, chart);
    }
}
```

### 개인화 향수 추천 분석
```java
@PostMapping("/ai/persona-recommendation")
public ResponseEntity<Map<String, Object>> getPersonaRecommendation(
        @RequestBody Map<String, Object> request) {
    
    String prompt = buildPersonaPrompt(product, gender, ageGroup);
    String analysis = chatService.generatePersonaRecommendation(prompt);
    // "20대 남성이 이 향수를 착용했을 때의 예상 시나리오" 등 상세 분석
}
```

## 🚀 배포 및 운영

### 성능 최적화
- **데이터베이스 인덱싱**: 주요 검색 조건에 인덱스 적용
- **이미지 최적화**: WebP 포맷 사용, CDN 연동
- **캐싱 전략**: Redis를 통한 상품 정보 캐싱
- **페이징 처리**: 무한 스크롤 및 가상 스크롤링

### 보안
- **입력 검증**: XSS, SQL Injection 방지
- **인증/인가**: JWT 기반 토큰 인증
- **개인정보 보호**: 민감 정보 암호화
- **API 보안**: Rate Limiting, CORS 설정

## 🤝 기여하기

1. 이 저장소를 포크합니다
2. 새로운 기능 브랜치를 만듭니다 (`git checkout -b feature/AmazingFeature`)
3. 변경사항을 커밋합니다 (`git commit -m 'Add some AmazingFeature'`)
4. 브랜치에 푸시합니다 (`git push origin feature/AmazingFeature`)
5. Pull Request를 생성합니다

### 개발 가이드라인
- **코드 스타일**: Google Java Style Guide 준수
- **커밋 메시지**: Conventional Commits 형식 사용
- **테스트**: 새로운 기능에 대한 단위 테스트 작성
- **문서화**: 주요 메서드에 JavaDoc 주석 작성

## 👥 팀 정보

- **백엔드 개발**: Spring Boot, MyBatis, API 연동
- **프론트엔드 개발**: Thymeleaf, Bootstrap, JavaScript
- **데이터베이스 설계**: ERD 설계, 성능 최적화
- **AI/ML**: OpenAI API 연동, 추천 알고리즘 개발
- **DevOps**: 배포 자동화, 모니터링

## 📄 라이센스

이 프로젝트는 MIT 라이센스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.

**22°C에서 피어나는 당신만의 향을 찾아보세요! 🌸**
