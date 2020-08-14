# Boolean Filtering
Proof of Concept Boolean Logic Filtering in Neo4j

Instructions
------------ 

This project uses maven, to build a jar-file with the procedure in this
project, simply package the project with maven:

    mvn clean package

This will produce a jar-file, `target/boolean_filtering-1.0-SNAPSHOT.jar`,
that can be copied to the `plugin` directory of your Neo4j instance.

    cp target/boolean_filtering-1.0-SNAPSHOT.jar neo4j-enterprise-4.1.X/plugins/.
    
In the "neo4j.conf" file inside the Neo4j/conf folder add this line:

    dbms.security.procedures.unrestricted=com.maxdemarzi.*

Stored Procedures:

    // YIELD size, nodes 
    CALL com.maxdemarzi.boolean.filter(label, query, limit, offset); 
    
    // One Filter
    CALL com.maxdemarzi.boolean.filter("Order", {not:false, and:[{property: "color", values: ["Blue"], not: false}]})
    
Sample Data:

    WITH 
    ["Unfulfilled", "Scheduled", "Shipped", "Shipped", "Shipped", "Shipped", "Returned"] AS statuses,
    ["Warehouse 1","Warehouse 2","Warehouse 3","Warehouse 3","Warehouse 3"] AS warehouses,
    [true, false] AS booleans,
    ["Blue", "Green", "Green", "Green", "Green", "Red", "Red", "Red", "Yellow"] AS colors,
    ["Small", "Medium",  "Medium",  "Medium", "Large", "Large", "Large", "Extra Large"] AS sizes,
    ["Summer 2019", "Fall 2019", "Winter 2019", "Spring 2020", "Summer 2020", "Fall 2020"] AS seasons,
    ["Chicago", "Aurora","Rockford","Joliet",
    "Naperville","Springfield", "Peoria", "Elgin", 
    "Waukegan", "Champaign", "Bloomington", "Decatur", 
    "Evanston", "Wheaton", "Belleville", "Urbana", 
    "Quincy", "Rock Island"] AS cities
    FOREACH (r IN range(1,1000) | 
    CREATE (o:Order {id : r,
        status : statuses[r % size(statuses)],
        warehouse : warehouses[r % size(warehouses)], 
        online : booleans[r % size(booleans)],
        color : colors[r % size(colors)],
        size : sizes[r % size(sizes)],
        season : seasons[r % size(seasons)],
        city : cities[r % size(cities)],
        postal : 60400 + (r % size(cities)) % 100,
        amount: toInteger(floor((20 + (100 * rand()) ) * 100)) / 100.0,
        ordered_date : (date() - duration('P' + ceil(365 * rand()) + 'D')) }));    