package com.formation.fitclass.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fitclassServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Class Service API")
                .description("Catalogue des cours de sport : CRUD, recherche filtree paginee, gestion des places avec verrouillage optimiste")
                .version("v1"));
    }
}
