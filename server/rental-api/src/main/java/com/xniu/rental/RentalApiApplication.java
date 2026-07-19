package com.xniu.rental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RentalApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalApiApplication.class, args);
    }
}
