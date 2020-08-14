package com.maxdemarzi;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.maxdemarzi.quine.BooleanExpression;
import com.maxdemarzi.results.SizeAndNodeResult;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.collections.api.bimap.BiMap;
import org.eclipse.collections.api.bimap.MutableBiMap;
import org.eclipse.collections.impl.bimap.mutable.HashBiMap;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.*;
import org.neo4j.internal.helpers.collection.Pair;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.*;

public class Procedures {
    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseService db;

    @Context public Transaction transaction;

    // This field gives us a static GraphDatabaseService to use within our cache
    static GraphDatabaseService graph;

    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `data/log/neo4j.log`
    @Context
    public Log log;

    // This cache stores the node ids by Dimension and Value
    public static final LoadingCache<Triple<Label, String, Object>, Roaring64NavigableMap> valueCache = Caffeine.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .refreshAfterWrite(10, TimeUnit.MINUTES)
            .build(Procedures::getValues);

    static Roaring64NavigableMap getValues(Triple<Label, String, Object> key) {
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        Label label = key.getLeft();
        String property = key.getMiddle();
        Object value = key.getRight();

        try (Transaction tx = graph.beginTx()) {
            ResourceIterator<Node> nodes = tx.findNodes(label, property, value);
            while(nodes.hasNext()) {
                bitmap.add(nodes.next().getId());
            }
        }
        return bitmap;
    }


    @Procedure(name = "com.maxdemarzi.boolean.filter", mode = Mode.READ)
    @Description("CALL com.maxdemarzi.boolean.filter(label, query, limit, offset)")
    public Stream<SizeAndNodeResult> BooleanFilter(
            @Name(value = "label") String labelName,
            @Name(value = "query") Map<String, Object> query,
            @Name(value = "limit", defaultValue = "50") Long limit,
            @Name(value = "offset", defaultValue = "0") Long offset) {

        //initialize the graph
        if (graph == null) { graph = db; }

        List<Node> results;
        long size = 0L;

        Label label = Label.label(labelName);

        Roaring64NavigableMap combinedNodeIds = new Roaring64NavigableMap();

        MutableBiMap<HashMap<String, Object>, Integer> expressions = new HashBiMap<>();
        String formula = getFormula(query, "", expressions);
        BiMap<Integer, HashMap<String, Object>> inverse = expressions.inverse();

        //log.debug("Formula: " + formula);

        // Use the expression to find the required paths
        BooleanExpression boEx = new BooleanExpression(formula);
        boEx.doTabulationMethod();
        boEx.doQuineMcCluskey();
        boEx.doPetricksMethod();

        for (String path : boEx.getPathExpressions()) {
            // We will collect the valid node ids for this path here
            Roaring64NavigableMap nodeIds = new Roaring64NavigableMap();

            // Figure out which filters are a "must have" and which are a "must not"
            String[] ids = path.split("[!&]");
            char[] rels = path.replaceAll("[^&^!]", "").toCharArray();

            Set<String> mustHave = new HashSet<>();
            Set<String> mustNot = new HashSet<>();

            // Using the ANDs and NOTs, figure out what we must have and must not
            if (path.startsWith("!")) {
                mustNot.add(ids[0]);
            } else {
                mustHave.add(ids[0]);
            }

            for (int i = 0; i < rels.length; i++) {
                if (rels[i] == '&') {
                    mustHave.add(ids[1 + i]);
                } else {
                    mustNot.add(ids[1 + i]);
                }
            }

            // Get the bitmaps of node ids from each filter into an array
            ArrayList<Pair<Roaring64NavigableMap, Long>> filters = new ArrayList<>();

            for (String item : mustHave) {
                Map<String, Object> filter = inverse.get(Integer.valueOf(item));
                MutableTriple<Label, String, Object> key = MutableTriple.of(label, (String)filter.get("property"), null);

                // Since the values can be inside an array, we are treating these as belonging to any in the array
                ArrayList<Object> values = (ArrayList<Object>) filter.get("values");
                Roaring64NavigableMap filterValueIds = new Roaring64NavigableMap();
                for (Object value : values) {
                    key.setRight(value);
                    // Join them together
                    Roaring64NavigableMap dimensionValueIds = valueCache.get(key);
                    if (dimensionValueIds != null) {
                        filterValueIds.or(dimensionValueIds);
                    }
                }
                filters.add(Pair.of(filterValueIds, filterValueIds.getLongCardinality()));
            }

            // Sort bitmaps in Ascending order by cardinality
            filters.sort(Comparator.comparing(Pair::other));

            // Initialize the smallest bitmap as our starting point
            if (filters.size() > 0) {
                nodeIds.or(filters.remove(0).first());
            }

            // AND the rest of the bitmaps
            for (Pair<Roaring64NavigableMap, Long> pair : filters) {
                nodeIds.and(pair.first());
            }

            // now lets remove the must nots
            for (String item : mustNot) {
                Map<String, Object> filter = inverse.get(Integer.valueOf(item));
                MutableTriple<Label, String, Object> key = MutableTriple.of(label, (String)filter.get("property"), null);

                // Since the values can be inside an array, we are treating these as belonging to any in the array
                ArrayList<Object> filterValues = (ArrayList<Object>) filter.get("values");
                Roaring64NavigableMap filterValueIds = new Roaring64NavigableMap();
                for (Object value : filterValues) {
                    key.setRight(value);
                    // Join them together
                    Roaring64NavigableMap dimensionValueIds = valueCache.get(key);
                    if (dimensionValueIds != null) {
                        filterValueIds.or(dimensionValueIds);
                    }
                }
                // AND NOT any excluded node ids
                nodeIds.andNot(filterValueIds);
            }

            // add the node ids found via these set of filters
            combinedNodeIds.or(nodeIds);
        }

        // Return nodes AND the total count of nodes found.
        size = combinedNodeIds.getLongCardinality();

        results = StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                combinedNodeIds.iterator(), Spliterator.CONCURRENT), false)
                .map(transaction::getNodeById)
                .skip(offset).limit(limit)
                .collect(Collectors.toList());

        return Stream.of(new SizeAndNodeResult(results, size));
    }

    String getFormula(Map<String, Object> query, String formula, MutableBiMap<HashMap<String, Object>, Integer> expressions) {
        if((boolean)query.getOrDefault("not", false)) {
            formula = formula + "!";
        }
        if (query.containsKey("and")) {
            ArrayList<String> values = new ArrayList<>();

            for(HashMap<String, Object> entry : (ArrayList<HashMap<String, Object>>)query.get("and")) {
                String value = "";
                if (entry.containsKey("or")) {
                    ArrayList<String> values2 = new ArrayList<>();
                    ArrayList<HashMap<String, Object>> ors = (ArrayList<HashMap<String, Object>>)entry.get("or");

                    for(HashMap<String, Object> entry2 : ors) {
                        values2.add(getFormula(entry2, "" , expressions));
                    }
                    value = "( " +  String.join(" | ", values2) + " )";
                } else {
                    if((boolean)entry.getOrDefault("not", false)) {
                        value = "!";
                    }
                    if(expressions.containsKey(entry)) {
                        value += expressions.get(entry);
                    } else {
                        value += expressions.size();
                        expressions.put(entry,  expressions.size());
                    }
                }
                values.add(value);
            }
            formula = formula + "(" + String.join(" & ", values) + ")";
        }

        return formula;
    }
}
