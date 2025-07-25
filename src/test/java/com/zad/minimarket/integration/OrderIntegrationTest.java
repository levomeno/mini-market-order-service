package com.zad.minimarket.integration;

import com.zad.minimarket.dto.CreateOrderRequest;
import com.zad.minimarket.dto.OrderResponse;
import com.zad.minimarket.entity.OrderSide;
import com.zad.minimarket.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;
import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class OrderIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static WireMockContainer wireMockContainer = new WireMockContainer("wiremock/wiremock:3.3.1")
        .withExposedPorts(8080);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    protected static WireMockServer wireMockServer;

    @AfterEach
    void cleanup() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "orders", "executions");
    }

    @BeforeAll
    static void init() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            wireMockServer = new WireMockServer(options().port(9596));
            wireMockServer.start();
            configureFor("localhost", 9596);
        }
    }

    @AfterAll
    static void destroy() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            System.out.println("WireMock server stopped.");
        } else {
            System.out.println("WireMockServer is not started yet.");
        }
    }

    @Test
    void should_CreateOrderSuccessfully_When_PriceFeedResponds() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId("acc-123");
        request.setSymbol("AAPL");
        request.setSide(OrderSide.BUY);
        request.setQuantity(BigDecimal.valueOf(10));

        // Mock the price feed HTTP response
        stubFor(WireMock.get(urlEqualTo("/price?symbol=AAPL"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"symbol\":\"AAPL\",\"price\":210.55}")));

        // When
        MvcResult result = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.accountId").value("acc-123"))
            .andExpect(jsonPath("$.symbol").value("AAPL"))
            .andExpect(jsonPath("$.side").value("BUY"))
            .andExpect(jsonPath("$.quantity").value(10))
            .andExpect(jsonPath("$.status").value("EXECUTED"))
            .andReturn();

        // Then
        String responseContent = result.getResponse().getContentAsString();
        OrderResponse orderResponse = objectMapper.readValue(responseContent, OrderResponse.class);

        assertThat(orderResponse.getId()).isNotNull();
        assertThat(orderRepository.count()).isEqualTo(1);

        // Verify the HTTP call was made
        verify(getRequestedFor(urlEqualTo("/price?symbol=AAPL")));
    }

    @Test
    void should_GetOrderById_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/orders/999"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Order Not Found"));
    }

    @Test
    void should_GetOrdersByAccount() throws Exception {
        // Given
        CreateOrderRequest request1 = new CreateOrderRequest();
        request1.setAccountId("acc-123");
        request1.setSymbol("AAPL");
        request1.setSide(OrderSide.BUY);
        request1.setQuantity(BigDecimal.valueOf(10));

        CreateOrderRequest request2 = new CreateOrderRequest();
        request2.setAccountId("acc-123");
        request2.setSymbol("GOOGL");
        request2.setSide(OrderSide.SELL);
        request2.setQuantity(BigDecimal.valueOf(5));

        // Mock the price feed HTTP response
        stubFor(WireMock.get(urlPathMatching("/price"))
            .withQueryParam("symbol", matching("[A-Z]{4,5}"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"symbol\":\"AAPL\",\"price\":210.55}")));

        stubFor(WireMock.get(urlPathMatching("/price"))
            .withQueryParam("symbol", matching("[A-Z]{4,5}"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"symbol\":\"GOOGL\",\"price\":2700.55}")));

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get("/orders")
                .param("accountId", "acc-123"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2));
    }
}

