package com.ceawse.portalsparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableFeignClients(basePackages = "com.ceawse.portalsparser.client")
public class PortalsParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalsParserApplication.class, args);
    }

}
