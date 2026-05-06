plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.github.spotbugs") version "6.0.20"
	checkstyle
	jacoco
	id("com.diffplug.spotless") version "6.25.0"
}

group = "back"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// --- Core ---
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

	// --- JWT ---
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// --- DB ---
	implementation("org.springframework.boot:spring-boot-h2console")
	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.postgresql:postgresql")
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// --- OCI Object Storage ---
	implementation("com.oracle.oci.sdk:oci-java-sdk-objectstorage:3.60.0")
	implementation("com.oracle.oci.sdk:oci-java-sdk-common:3.60.0")
	implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-jersey3:3.60.0")

	// --- Lombok ---
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// --- SpotBugs ---
	compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")

	// --- Email ---
	implementation ("org.springframework.boot:spring-boot-starter-mail")

	// --- Test ---
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
	testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
	testImplementation("org.springframework.boot:spring-boot-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-data-jpa-test")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")

	// --- Security / Crypto ---
	implementation("com.google.crypto.tink:tink:1.12.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// --- 품질 도구 상세 설정 추가 ---
// Checkstyle
checkstyle {
	toolVersion = "10.12.5"
	isIgnoreFailures = true
	maxWarnings = 0
}

// JaCoCo
jacoco {
	toolVersion = "0.8.11"
}

tasks.named<JacocoReport>("jacocoTestReport") {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
	violationRules {
		rule {
			element = "CLASS"
			excludes = listOf(
				"**.dto.**",               // dto 패키지 안에 있는 모든 클래스 제외
				"**.config.**",            // config 패키지 안에 있는 모든 클래스 제외
				"**.*Application*",        // 메인 실행 클래스 제외
				"**.global.exception.*",   // CommonErrorCode, ServiceException, ErrorCode
				"**.global.handler.*",     // GlobalExceptionHandler
				"**.global.response.*",    // RsData
				"**.entity.*",             // Member 등 엔티티
				"**.util.*",               // Ut.Json 등 유틸
			)
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.30".toBigDecimal()
			}
		}
	}
}

// Spotless 설정 (현재 미사용)
spotless {
	java {
		target("src/**/*.java")
		palantirJavaFormat()
		removeUnusedImports()
		trimTrailingWhitespace()
		formatAnnotations()
		endWithNewline()
		importOrder("java", "javax", "org", "com", "")
	}
	format("misc") {
		target("*.gradle", "*.md", ".gitignore")
		trimTrailingWhitespace()
		endWithNewline()
	}
}