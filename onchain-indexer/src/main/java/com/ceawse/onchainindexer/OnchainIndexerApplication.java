package com.ceawse.onchainindexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.ceawse.onchainindexer.client")
public class OnchainIndexerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnchainIndexerApplication.class, args);
    }

}
