package com.hzgc.service.face;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@EnableEurekaClient
@PropertySource("face.properties")
public class FaceApplication {
    public static void main(String[] args){
        SpringApplication.run(FaceApplication.class,args);
    }
}