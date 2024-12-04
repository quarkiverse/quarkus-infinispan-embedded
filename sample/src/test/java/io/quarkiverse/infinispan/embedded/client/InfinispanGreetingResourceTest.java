package io.quarkiverse.infinispan.embedded.client;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class InfinispanGreetingResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Infinispan Client\",\"message\":\"Hello World, Infinispan is up!\"}")
                .when()
                .post("/greeting/quarkus")
                .then()
                .statusCode(200);

        given()
                .when().get("/greeting/quarkus")
                .then()
                .statusCode(200)
                .body(is("{\"name\":\"Infinispan Client\",\"message\":\"Hello World, Infinispan is up!\"}"));
    }
}
