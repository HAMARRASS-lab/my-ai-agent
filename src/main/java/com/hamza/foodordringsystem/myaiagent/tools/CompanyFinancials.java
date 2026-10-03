package com.hamza.foodordringsystem.myaiagent.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Real financial numbers for a company: live market data from Yahoo Finance and the
// latest reported figures (revenue, profit, ...) from Wikidata. Both sources are free and keyless.
@Service("companyFinancials")
public class CompanyFinancials implements Function<CompanyFinancials.Request, CompanyFinancials.Response> {

    private static final Logger log = LoggerFactory.getLogger(CompanyFinancials.class);

    // Same business-only search as CountryIdentityInfo, but only returns the best matching item.
    private static final String SEARCH_QUERY = """
            SELECT ?item ?itemLabel WHERE {
              SERVICE wikibase:mwapi {
                bd:serviceParam wikibase:api "EntitySearch"; wikibase:endpoint "www.wikidata.org";
                                mwapi:search "%s"; mwapi:language "en".
                ?item wikibase:apiOutputItem mwapi:item. ?num wikibase:apiOrdinal true.
              }
              ?item wdt:P31/wdt:P279* wd:Q4830453.
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } ORDER BY ?num LIMIT 1
            """;

    // Looked up separately from the search: combining both in one query is far too slow on Wikidata.
    private static final String FIGURES_QUERY = """
            SELECT ?prop ?amount ?currency ?date ?rank WHERE {
              VALUES ?item { wd:%s }
              VALUES (?p ?ps ?psv ?prop) {
                (p:P2139 ps:P2139 psv:P2139 "Revenue")
                (p:P3362 ps:P3362 psv:P3362 "Operating income")
                (p:P2295 ps:P2295 psv:P2295 "Net profit")
                (p:P2226 ps:P2226 psv:P2226 "Market capitalization")
                (p:P1128 ps:P1128 psv:P1128 "Employees")
              }
              ?item ?p ?st . ?st ?ps ?amount ; wikibase:rank ?rank .
              FILTER(?rank != wikibase:DeprecatedRank)
              OPTIONAL { ?st ?psv ?v . ?v wikibase:quantityUnit ?unit . ?unit wdt:P498 ?currency . }
              OPTIONAL { ?st pq:P585 ?date }
            }
            """;

    private static final List<String> FIGURE_ORDER =
            List.of("Revenue", "Operating income", "Net profit", "Market capitalization", "Employees");
    private static final String PREFERRED_RANK = "http://wikiba.se/ontology#PreferredRank";

    private final RestClient wikidata;
    private final RestClient yahoo;

    public CompanyFinancials(RestClient.Builder builder) {
        this.wikidata = builder.clone()
                .baseUrl("https://query.wikidata.org/sparql")
                .defaultHeader("User-Agent", "my-ai-agent/0.1 (https://github.com/HAMARRASS-lab/my-ai-agent)")
                .build();
        // Yahoo rejects requests without a browser-like User-Agent.
        this.yahoo = builder.clone()
                .baseUrl("https://query1.finance.yahoo.com")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
    }

    public record Request(String companyName) {
    }

    public record Response(String companyName, boolean found, StockQuote stock, List<Figure> reportedFigures) {
    }

    public record StockQuote(String ticker, String companyName, String exchange, String currency, Double price,
                             Double dayChangePercent, Double fiftyTwoWeekLow, Double fiftyTwoWeekHigh, String asOf) {
    }

    // currency is null for counts such as Employees; year is the fiscal year the figure refers to.
    public record Figure(String name, double value, String currency, Integer year) {
    }

    @Override
    public Response apply(Request request) {
        String name = request.companyName() == null ? "" : request.companyName().trim();
        StockQuote stock = fetchQuote(name);
        List<Figure> figures = fetchFigures(name);
        return new Response(name, stock != null || !figures.isEmpty(), stock, figures);
    }

    // --- Yahoo Finance ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooSearch(List<YahooSearchQuote> quotes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooSearchQuote(String symbol, String quoteType, String longname, String shortname, String exchDisp) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooChart(Chart chart) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Chart(List<Result> result) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Result(Meta meta) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Meta(String currency, Double regularMarketPrice, Double chartPreviousClose,
                    Double regularMarketChangePercent, Double fiftyTwoWeekLow, Double fiftyTwoWeekHigh,
                    Long regularMarketTime) {
        }
    }

    StockQuote fetchQuote(String name) {
        try {
            YahooSearch search = yahoo.get()
                    .uri(uri -> uri.path("/v1/finance/search")
                            .queryParam("q", name).queryParam("quotesCount", 5).queryParam("newsCount", 0).build())
                    .retrieve()
                    .body(YahooSearch.class);
            // The first equity is normally the company's primary listing (e.g. RNO.PA for Renault).
            YahooSearchQuote match = search == null || search.quotes() == null ? null : search.quotes().stream()
                    .filter(q -> "EQUITY".equals(q.quoteType()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                return null;
            }

            YahooChart chart = yahoo.get()
                    .uri(uri -> uri.path("/v8/finance/chart/{symbol}")
                            .queryParam("range", "1d").queryParam("interval", "1d").build(match.symbol()))
                    .retrieve()
                    .body(YahooChart.class);
            YahooChart.Meta meta = chart == null || chart.chart() == null || chart.chart().result() == null
                    || chart.chart().result().isEmpty() ? null : chart.chart().result().get(0).meta();
            if (meta == null || meta.regularMarketPrice() == null) {
                return null;
            }

            Double change = meta.regularMarketChangePercent();
            if (change == null && meta.chartPreviousClose() != null && meta.chartPreviousClose() != 0) {
                change = (meta.regularMarketPrice() - meta.chartPreviousClose()) / meta.chartPreviousClose() * 100;
            }
            return new StockQuote(
                    match.symbol(),
                    match.longname() != null ? match.longname() : match.shortname(),
                    match.exchDisp(),
                    meta.currency(),
                    meta.regularMarketPrice(),
                    change == null ? null : Math.round(change * 100) / 100.0,
                    meta.fiftyTwoWeekLow(),
                    meta.fiftyTwoWeekHigh(),
                    meta.regularMarketTime() == null ? null : Instant.ofEpochSecond(meta.regularMarketTime()).toString());
        } catch (Exception e) {
            log.warn("Yahoo Finance lookup failed for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    // --- Wikidata ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SparqlResult(Results results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Results(List<Map<String, Value>> bindings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Value(String value) {
    }

    List<Figure> fetchFigures(String name) {
        try {
            List<Map<String, Value>> match = sparql(SEARCH_QUERY.formatted(name.replace("\\", "").replace("\"", "")));
            if (match.isEmpty()) {
                return List.of();
            }
            String itemUri = text(match.get(0), "item");
            String qid = itemUri.substring(itemUri.lastIndexOf('/') + 1);
            return latestPerFigure(sparql(FIGURES_QUERY.formatted(qid)));
        } catch (Exception e) {
            log.warn("Wikidata financials lookup failed for '{}': {}", name, e.getMessage());
            return List.of();
        }
    }

    // Wikidata keeps one statement per year; keep the most recent one (preferred rank wins a tie).
    static List<Figure> latestPerFigure(List<Map<String, Value>> rows) {
        Comparator<Map<String, Value>> newestFirst = Comparator
                .comparing((Map<String, Value> row) -> {
                    String date = text(row, "date");
                    return date == null ? "" : date;
                })
                .thenComparing(row -> PREFERRED_RANK.equals(text(row, "rank")))
                .reversed();

        Map<String, Map<String, Value>> latest = new LinkedHashMap<>();
        rows.stream()
                .sorted(newestFirst)
                .forEach(row -> latest.putIfAbsent(text(row, "prop"), row));

        List<Figure> figures = new ArrayList<>();
        for (String figure : FIGURE_ORDER) {
            Map<String, Value> row = latest.get(figure);
            if (row == null) {
                continue;
            }
            String date = text(row, "date");
            figures.add(new Figure(
                    figure,
                    Double.parseDouble(text(row, "amount")),
                    text(row, "currency"),
                    date == null ? null : Integer.valueOf(date.substring(0, date.indexOf('-', 1)))));
        }
        return figures;
    }

    private List<Map<String, Value>> sparql(String query) {
        SparqlResult result = wikidata.get()
                .uri(uri -> uri.queryParam("query", "{query}").build(query))
                .accept(MediaType.valueOf("application/sparql-results+json"))
                .retrieve()
                .body(SparqlResult.class);
        return result == null || result.results() == null ? List.of() : result.results().bindings();
    }

    private static String text(Map<String, Value> row, String key) {
        Value value = row.get(key);
        return value == null ? null : value.value();
    }
}
