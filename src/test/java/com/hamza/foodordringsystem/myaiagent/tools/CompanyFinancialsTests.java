package com.hamza.foodordringsystem.myaiagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CompanyFinancialsTests {

    private static final String SEARCH = """
            {"quotes": [
              {"symbol": "RNL.F", "quoteType": "FUTURE"},
              {"symbol": "RNO.PA", "quoteType": "EQUITY", "longname": "Renault SA", "exchDisp": "Paris"}
            ]}
            """;

    private static final String CHART = """
            {"chart": {"result": [{"meta": {"currency": "EUR", "regularMarketPrice": 24.68, "chartPreviousClose": 25.43,
              "fiftyTwoWeekLow": 24.66, "fiftyTwoWeekHigh": 37.97, "regularMarketTime": 1790955313}}]}}
            """;

    private static final String WIKIDATA_ITEM = """
            {"results": {"bindings": [{"item": {"value": "http://www.wikidata.org/entity/Q6686"}}]}}
            """;

    // Two revenue years (the newest must win), a preferred-rank tie for net profit, and employees without currency.
    private static final String WIKIDATA_FIGURES = """
            {"results": {"bindings": [
              {"prop": {"value": "Revenue"}, "amount": {"value": "52376000000"}, "currency": {"value": "EUR"},
               "date": {"value": "2023-01-01T00:00:00Z"}, "rank": {"value": "http://wikiba.se/ontology#NormalRank"}},
              {"prop": {"value": "Revenue"}, "amount": {"value": "56232000000"}, "currency": {"value": "EUR"},
               "date": {"value": "2024-01-01T00:00:00Z"}, "rank": {"value": "http://wikiba.se/ontology#NormalRank"}},
              {"prop": {"value": "Net profit"}, "amount": {"value": "700000000"}, "currency": {"value": "EUR"},
               "date": {"value": "2024-01-01T00:00:00Z"}, "rank": {"value": "http://wikiba.se/ontology#NormalRank"}},
              {"prop": {"value": "Net profit"}, "amount": {"value": "752000000"}, "currency": {"value": "EUR"},
               "date": {"value": "2024-01-01T00:00:00Z"}, "rank": {"value": "http://wikiba.se/ontology#PreferredRank"}},
              {"prop": {"value": "Employees"}, "amount": {"value": "170158"},
               "date": {"value": "2020-01-01T00:00:00Z"}, "rank": {"value": "http://wikiba.se/ontology#NormalRank"}}
            ]}}
            """;

    private MockRestServiceServer server;
    private CompanyFinancials financials;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        financials = new CompanyFinancials(builder);
    }

    @Test
    void combinesLiveQuoteWithLatestReportedFigures() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://query1.finance.yahoo.com/v1/finance/search")))
                .andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://query1.finance.yahoo.com/v8/finance/chart/RNO.PA")))
                .andRespond(withSuccess(CHART, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("EntitySearch")))
                .andRespond(withSuccess(WIKIDATA_ITEM, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("Q6686")))
                .andRespond(withSuccess(WIKIDATA_FIGURES, MediaType.APPLICATION_JSON));

        CompanyFinancials.Response response = financials.apply(new CompanyFinancials.Request("Renault"));

        assertThat(response.found()).isTrue();
        assertThat(response.stock().ticker()).isEqualTo("RNO.PA");
        assertThat(response.stock().exchange()).isEqualTo("Paris");
        assertThat(response.stock().currency()).isEqualTo("EUR");
        assertThat(response.stock().price()).isEqualTo(24.68);
        assertThat(response.stock().dayChangePercent()).isEqualTo(-2.95);
        assertThat(response.reportedFigures()).containsExactly(
                new CompanyFinancials.Figure("Revenue", 56_232_000_000d, "EUR", 2024),
                new CompanyFinancials.Figure("Net profit", 752_000_000d, "EUR", 2024),
                new CompanyFinancials.Figure("Employees", 170_158d, null, 2020));
        server.verify();
    }

    @Test
    void stillReturnsReportedFiguresWhenYahooIsDown() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://query1.finance.yahoo.com/v1/finance/search")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("EntitySearch")))
                .andRespond(withSuccess(WIKIDATA_ITEM, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("Q6686")))
                .andRespond(withSuccess(WIKIDATA_FIGURES, MediaType.APPLICATION_JSON));

        CompanyFinancials.Response response = financials.apply(new CompanyFinancials.Request("Renault"));

        assertThat(response.found()).isTrue();
        assertThat(response.stock()).isNull();
        assertThat(response.reportedFigures()).hasSize(3);
    }

    @Test
    void notFoundWhenNeitherSourceKnowsTheCompany() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://query1.finance.yahoo.com/v1/finance/search")))
                .andRespond(withSuccess("{\"quotes\": []}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("EntitySearch")))
                .andRespond(withSuccess("{\"results\": {\"bindings\": []}}", MediaType.APPLICATION_JSON));

        CompanyFinancials.Response response = financials.apply(new CompanyFinancials.Request("Nonexistent Corp"));

        assertThat(response.found()).isFalse();
        assertThat(response.stock()).isNull();
        assertThat(response.reportedFigures()).isEmpty();
    }
}
