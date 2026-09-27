package com.hamza.foodordringsystem.myaiagent.tools;



import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.context.annotation.Description;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service("countryIdentityInfo")
@Description("""
        Get Identity information about a company including:
        - The name of the company
        - The country of the company
        - The industry domain of the company
        - The year the company was founded
        """)
public class CountryIdentityInfo implements Function<CountryIdentityInfo.Request, CountryIdentityInfo.Response> {

    // Searches Wikidata by name and keeps only results that are businesses, so "Apple" finds Apple Inc. and not the fruit.
    private static final String QUERY = """
            SELECT ?item ?itemLabel ?countryLabel ?industryLabel ?inception WHERE {
              SERVICE wikibase:mwapi {
                bd:serviceParam wikibase:api "EntitySearch"; wikibase:endpoint "www.wikidata.org";
                                mwapi:search "%s"; mwapi:language "en".
                ?item wikibase:apiOutputItem mwapi:item. ?num wikibase:apiOrdinal true.
              }
              ?item wdt:P31/wdt:P279* wd:Q4830453.
              OPTIONAL { ?item wdt:P17 ?country }
              OPTIONAL { ?item wdt:P452 ?industry }
              OPTIONAL { ?item wdt:P571 ?inception }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } ORDER BY ?num LIMIT 30
            """;

    private final RestClient restClient;

    public CountryIdentityInfo(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://query.wikidata.org/sparql")
                .defaultHeader("User-Agent", "my-ai-agent/0.1 (https://github.com/HAMARRASS-lab/my-ai-agent)")
                .build();
    }

    public record Request(String companyName) {
    }

    public record Response(String companyName, String country, String domain,
                           Integer foundedYear, boolean found

    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SparqlResult(Results results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Results(List<Map<String, Value>> bindings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Value(String value) {
    }

    @Override
    public Response apply(Request request) {
        System.out.println("CountryIdentityInfo: " + request.companyName);
        String name = request.companyName.replace("\\", "").replace("\"", "");
        try {
            SparqlResult result = restClient.get()
                    .uri(uri -> uri.queryParam("query", "{query}").build(QUERY.formatted(name)))
                    .accept(MediaType.valueOf("application/sparql-results+json"))
                    .retrieve()
                    .body(SparqlResult.class);
            List<Map<String, Value>> rows = result == null ? List.of() : result.results().bindings();
            if (rows.isEmpty()) {
                return new Response(request.companyName, null, null, null, false);
            }

            // One row per industry: keep the best match and gather its industries.
            String item = text(rows.get(0), "item");
            Set<String> industries = new LinkedHashSet<>();
            rows.stream()
                    .filter(row -> item.equals(text(row, "item")))
                    .map(row -> text(row, "industryLabel"))
                    .filter(industry -> industry != null)
                    .limit(5)
                    .forEach(industries::add);
            String inception = text(rows.get(0), "inception");
            return new Response(
                    text(rows.get(0), "itemLabel"),
                    text(rows.get(0), "countryLabel"),
                    industries.isEmpty() ? null : String.join(", ", industries),
                    inception == null ? null : Integer.valueOf(inception.substring(0, inception.indexOf('-', 1))),
                    true);
        } catch (Exception e) {
            System.out.println("CountryIdentityInfo lookup failed: " + e.getMessage());
            return new Response(request.companyName, null, null, null, false);
        }
    }

    private static String text(Map<String, Value> row, String key) {
        Value value = row.get(key);
        return value == null ? null : value.value();
    }

}
