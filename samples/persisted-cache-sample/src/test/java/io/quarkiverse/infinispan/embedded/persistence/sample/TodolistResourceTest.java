package io.quarkiverse.infinispan.embedded.persistence.sample;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@QuarkusTest
class TodolistResourceTest {

    @Test
    public void testTodoListEndpoint() {
        TodoItem todoItem = new TodoItem("id1", "Langchain4j", "Improve the Langchain4j integration with Infinispan", true);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(todoItem)
                .post("/todolist")
                .then()
                .statusCode(HttpStatus.SC_OK);

        TodoItem[] todoItems = given()
                .when().get("/todolist")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().body().as(TodoItem[].class);

        assertThat(todoItems).isNotEmpty();
        assertThat(todoItems.length).isOne();
        assertThat(todoItems[0]).isEqualTo(todoItem);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(todoItem)
                .delete("/todolist/id1")
                .then()
                .statusCode(HttpStatus.SC_OK);

        todoItems = given()
                .when().get("/todolist")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().body().as(TodoItem[].class);

        assertThat(todoItems).isEmpty();
    }
}
