package io.quarkiverse.infinispan.embedded.it.extension;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@QuarkusTest
public class HealthCheckTest {

    @Test
    public void testHealthCheck() {
        RestAssured.when().get("/q/health/ready").then()
                .contentType(ContentType.JSON)
                .header("Content-Type", containsString("charset=UTF-8"))
                .body("status", is("UP"))
                .body("checks.find { it.name == 'Infinispan Embedded cluster health check' }.status", is("UP"))
                .body("checks.find { it.name == 'Infinispan Embedded cluster health check' }.data.clusterHealth",
                        is("HEALTHY"))
                .body("checks.find { it.name == 'Infinispan Embedded cluster health check' }.data.clusterMembers",
                        is("1"));
    }
}
