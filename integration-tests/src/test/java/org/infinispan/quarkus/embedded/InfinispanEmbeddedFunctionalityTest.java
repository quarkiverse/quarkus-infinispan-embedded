package org.infinispan.quarkus.embedded;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.hamcrest.CoreMatchers;
import org.infinispan.commons.util.OS;
import org.infinispan.commons.util.Util;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * @author William Burns
 */
@QuarkusTest
public class InfinispanEmbeddedFunctionalityTest {

    @AfterAll
    public static void cleanup() {
        // Need to clean up persistent file - so tests don't leak between each other
        String tmpDir = System.getProperty("java.io.tmpdir");
        try (Stream<Path> files = Files.walk(Paths.get(tmpDir), 1)) {
            files.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("quarkus-"))
                    .map(Path::toFile)
                    .forEach(Util::recursiveFileRemove);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCache() {
        // This cache also has persistence
        testCache("local");
    }

    @Test
    public void testCDIDatasourceJdbc() {
        testCache("jdbc");
    }

    @Test
    public void testCDIDatasourceSQLQuery() {
        testCache("sql-query");
    }

    @Test
    public void testOffHeapCache() {
        if (!OS.getCurrentOs().equals(OS.MAC_OS)) {
            testCache("off-heap-memory");
        }
    }

    @Test
    public void testTransactionRolledBack() {
        String cacheName = "quarkus-transaction";
        Log.info("Running cache test for " + cacheName);
        when().get("/test/GET/" + cacheName + "/key").then().body(is("null"));
        // This should throw an exception and NOT commit the value
        when().get("/test/PUT/" + cacheName + "/key/something?shouldFail=true")
                .then()
                .statusCode(500);
        // Entry shouldn't have been committed
        when().get("/test/GET/" + cacheName + "/key").then().body(is("null"));
    }

    @Test
    public void testPutWithoutTransactionNotRolledBack() {
        String cacheName = "simple-cache";
        Log.info("Running cache test for " + cacheName);
        when().get("/test/GET/" + cacheName + "/key").then().body(is("null"));
        // This should throw an exception - but cache did put before
        when().get("/test/PUT/" + cacheName + "/key/something?shouldFail=true")
                .then()
                .statusCode(500);
        // Entry should be available
        when().get("/test/GET/" + cacheName + "/key").then().body(is("something"));
    }

    private void testCache(String cacheName) {
        Log.info("Running cache test for " + cacheName);
        when().get("/test/GET/" + cacheName + "/key").then().body(is("null"));

        when().get("/test/PUT/" + cacheName + "/key/something").then().body(is("null"));

        when().get("/test/GET/" + cacheName + "/key").then().body(is("something"));

        when().get("/test/REMOVE/" + cacheName + "/key").then().body(is("something"));

        when().get("/test/GET/" + cacheName + "/key").then().body(is("null"));
    }

    @Test
    public void testSimpleCluster() {
        Log.info("Running cluster test");
        when().get("/test/CLUSTER").then().body(is("Success"));
    }

    @Test
    public void testProtostreamCache() {
        Log.info("Protostream marshalling class");
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Infinispan Book\",\"author\":\"Jack Nicholson\"}")
                .when()
                .post("/test/PROTO/POST/books/123")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.is("123"));

        given()
                .when().get("/test/PROTO/GET/books/123")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.is(
                        "{\"name\":\"Infinispan Book\",\"author\":\"Jack Nicholson\"}"));

    }

    @Test
    public void testAnnotationsMethods() {
        Log.info("Annotations method test");
        given()
                .when().get("/test/ANNOTATIONS/GET/review/BOOK-0")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.is("Loved it!"));

        given()
                .when().get("/test/ANNOTATIONS/GET/calls")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.is("1"));

        given()
                .when().get("/test/ANNOTATIONS/GET/review/BOOK-0")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.is("Loved it!"));

        given()
                .when().get("/test/ANNOTATIONS/GET/calls")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.is("1"));
    }
}
