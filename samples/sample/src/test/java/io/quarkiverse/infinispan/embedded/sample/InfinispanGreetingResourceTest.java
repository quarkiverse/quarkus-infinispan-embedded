package io.quarkiverse.infinispan.embedded.sample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class InfinispanGreetingResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Infinispan Embedded\",\"message\":\"Hello World, Service with Infinispan Embedded is up!\"}")
                .when()
                .post("/greeting/quarkus")
                .then()
                .statusCode(HttpStatus.SC_OK);

        given()
                .when().get("/greeting/quarkus")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(is(
                        "{\"name\":\"Infinispan Embedded\",\"message\":\"Hello World, Service with Infinispan Embedded is up!\"}"));
    }
}
