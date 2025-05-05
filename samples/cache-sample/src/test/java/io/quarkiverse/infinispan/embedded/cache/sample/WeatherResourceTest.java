package io.quarkiverse.infinispan.embedded.cache.sample;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.http.HttpStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WeatherResourceTest {

    @Test
    public void testWeatherEndpoint() {
        Weather weatherFirstCall = given()
                .when().get("/weather/paris")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().body().as(Weather.class);

        Weather weatherSecondCall = given()
                .when().get("/weather/paris")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().body().as(Weather.class);

        assertThat(weatherFirstCall).isNotNull();
        assertThat(weatherSecondCall).isNotNull();
        assertThat(weatherFirstCall).isEqualTo(weatherSecondCall);

        Awaitility.await()
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> Assertions.assertTrue(true));

        Weather weatherThirdCall = given()
                .when().get("/weather/paris")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().body().as(Weather.class);

        assertThat(weatherThirdCall).isNotNull();
        assertThat(weatherThirdCall).isNotEqualTo(weatherSecondCall);
    }
}
