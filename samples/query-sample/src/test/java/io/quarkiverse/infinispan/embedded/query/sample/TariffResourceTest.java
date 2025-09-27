package io.quarkiverse.infinispan.embedded.query.sample;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class TariffResourceTest {

    @Test
    public void testTariffEndpoint() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"id\":1,\"value\":12.4}")
                .when()
                .post("/tariff")
                .then()
                .statusCode(HttpStatus.SC_OK);

        given()
                .contentType(ContentType.JSON)
                .body("{\"id\":2,\"value\":52.4}")
                .when()
                .post("/tariff")
                .then()
                .statusCode(HttpStatus.SC_OK);

        Tariff[] tariffs = given()
                .when().get("/tariff/1")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().body().as(Tariff[].class);

        assertThat(tariffs).isNotEmpty();
        assertThat(tariffs.length).isOne();
        assertThat(tariffs[0].getId()).isEqualTo(1);
        assertThat(tariffs[0].getValue()).isEqualTo(12.4);
    }
}
