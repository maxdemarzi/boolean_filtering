package com.maxdemarzi;

import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BooleanFilterTests {

    private static Neo4j neo4j;

    @BeforeAll
    static void initialize() {
        neo4j = Neo4jBuilders.newInProcessBuilder()
                // disabling http server to speed up start
                .withDisabledServer()
                .withProcedure(Procedures.class)
                .withFixture(MODEL_STATEMENT)
                .build();
    }

    @AfterAll
    static void stopNeo4j() {
        neo4j.close();
    }


    @Test
    void shouldBooleanFilter() {
        // In a try-block, to make sure we close the driver after the test
        try(Driver driver = GraphDatabase.driver( neo4j.boltURI() , Config.builder().withoutEncryption().build())) {
            // Given I've started Neo4j with the procedure
            //       which my 'neo4j' rule above does.
            Session session = driver.session();

            // When I use the procedure
            Result result = session.run( "CALL com.maxdemarzi.boolean.filter('Order', {not:false, and:[{property: 'color', values: ['Blue'], not: false}]});");

            // Then I should get what I expect
            //assertTrue(result.hasNext());
            Record record = result.single();
            assertEquals(111L, record.get("size").asLong());
            ArrayList<Node> results = new ArrayList<>(record.get("nodes").asList(Value::asNode));
            assertEquals(50, results.size());
        }
    }

    private static final String MODEL_STATEMENT = "WITH  " +
            "[\"Unfulfilled\", \"Scheduled\", \"Shipped\", \"Shipped\", \"Shipped\", \"Shipped\", \"Returned\"] AS statuses, " +
            "[\"Warehouse 1\",\"Warehouse 2\",\"Warehouse 3\",\"Warehouse 3\",\"Warehouse 3\"] AS warehouses, " +
            "[true, false] AS booleans, " +
            "[\"Blue\", \"Green\", \"Green\", \"Green\", \"Green\", \"Red\", \"Red\", \"Red\", \"Yellow\"] AS colors, " +
            "[\"Small\", \"Medium\",  \"Medium\",  \"Medium\", \"Large\", \"Large\", \"Large\", \"Extra Large\"] AS sizes, " +
            "[\"Summer 2019\", \"Fall 2019\", \"Winter 2019\", \"Spring 2020\", \"Summer 2020\", \"Fall 2020\"] AS seasons, " +
            "[\"Chicago\", \"Aurora\",\"Rockford\",\"Joliet\", " +
            "\"Naperville\",\"Springfield\", \"Peoria\", \"Elgin\",  " +
            "\"Waukegan\", \"Champaign\", \"Bloomington\", \"Decatur\",  " +
            "\"Evanston\", \"Wheaton\", \"Belleville\", \"Urbana\",  " +
            "\"Quincy\", \"Rock Island\"] AS cities " +
            "FOREACH (r IN range(1,1000) |  " +
            "CREATE (o:Order {id : r, " +
            "    status : statuses[r % size(statuses)], " +
            "    warehouse : warehouses[r % size(warehouses)],  " +
            "online : booleans[r % size(booleans)], " +
            "color : colors[r % size(colors)], " +
            "size : sizes[r % size(sizes)], " +
            "season : seasons[r % size(seasons)], " +
            "city : cities[r % size(cities)], " +
            "postal : 60400 + (r % size(cities)) % 100, " +
            "amount: toInteger(floor((20 + (100 * rand()) ) * 100)) / 100.0, " +
            "ordered_date : (date() - duration('P' + ceil(365 * rand()) + 'D')) }));";
}
