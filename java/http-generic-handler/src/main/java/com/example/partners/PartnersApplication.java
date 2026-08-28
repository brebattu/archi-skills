package com.example.partners;

import com.example.partners.config.PartnersProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PartnersProperties.class)
public class PartnersApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartnersApplication.class, args);
    }
}
