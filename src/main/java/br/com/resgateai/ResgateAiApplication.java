package br.com.resgateai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ResgateAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResgateAiApplication.class, args);
    }
}
