package com.bankmanagement.incentive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.bankmanagement")
public class IncentiveServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncentiveServiceApplication.class, args);
    }
}
