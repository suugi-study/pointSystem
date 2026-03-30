package com.study.point;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class PointWalletApplication {
    public static void main(String[] args) {
        SpringApplication.run(PointWalletApplication.class, args);
    }
}
