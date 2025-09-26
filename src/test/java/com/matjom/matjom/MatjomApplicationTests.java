package com.matjom.matjom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MatjomApplicationTests {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName.parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("matjom")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("tc-init.sql");

    @DynamicPropertySource
    static void datasourceConfig(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", new java.util.function.Supplier<Object>() {
            @Override
            public String get() {
                return POSTGRES.getJdbcUrl();
            }
        });
        registry.add("spring.datasource.username", new java.util.function.Supplier<Object>() {
            @Override
            public String get() {
                return POSTGRES.getUsername();
            }
        });
        registry.add("spring.datasource.password", new java.util.function.Supplier<Object>() {
            @Override
            public String get() {
                return POSTGRES.getPassword();
            }
        });
    }

	@Test
	void contextLoads() {
	}

}
