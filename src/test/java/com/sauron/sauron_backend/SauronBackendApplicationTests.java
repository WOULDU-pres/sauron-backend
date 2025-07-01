package com.sauron.sauron_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.data.redis.host=localhost",
		"spring.data.redis.port=6370" // 다른 포트로 설정하여 실제 Redis 없이도 테스트 가능
})
class SauronBackendApplicationTests {

	@Test
	void contextLoads() {
		// Spring Boot 컨텍스트가 정상적으로 로드되는지 확인
	}
}
