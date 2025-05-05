package io.quarkiverse.infinispan.embedded.docs.it;

import org.apache.http.HttpStatus;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@QuarkusTest
public class AntoraTest {

    @Test
    public void antoraSite() {
        RestAssured
                .given()
                .contentType(ContentType.HTML)
                .get("/quarkus-infinispan-embedded/dev/index.html")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.containsString("<h1 class=\"page\">Quarkus Infinispan Embedded</h1>"));
    }

}
