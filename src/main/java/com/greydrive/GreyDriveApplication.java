package com.greydrive;

import com.greydrive.bootstrap.Bootstrap;
import com.greydrive.config.GreyDriveProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(GreyDriveProperties.class)
@EnableScheduling
@EnableAsync
public class GreyDriveApplication {

    public static void main(String[] args) {
        Bootstrap.init();
        SpringApplication.run(GreyDriveApplication.class, args);
    }
}
