package com.hzgc.service.visual;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.PropertySource;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@EnableEurekaClient
@SpringBootApplication
@PropertySource("visualApplication.properties")
public class VisualApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisualApplication.class, args);
    }
}
