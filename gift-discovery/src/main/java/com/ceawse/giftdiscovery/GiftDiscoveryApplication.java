package com.ceawse.giftdiscovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.ceawse.giftdiscovery")
@EnableFeignClients(basePackages = "com.ceawse.giftdiscovery.client")
public class GiftDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(GiftDiscoveryApplication.class, args);
    }
}
