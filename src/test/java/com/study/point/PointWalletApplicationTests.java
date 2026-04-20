package com.study.point;

import com.study.point.infrastructure.redis.RedisLockManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class PointWalletApplicationTests {

	@MockBean
	private RedisLockManager redisLockManager;

	@Test
	void contextLoads() {
	}

}
