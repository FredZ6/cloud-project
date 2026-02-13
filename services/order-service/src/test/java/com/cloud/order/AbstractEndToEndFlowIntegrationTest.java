package com.cloud.order;

import com.cloud.auth.AuthServiceApplication;
import com.cloud.inventory.InventoryServiceApplication;
import com.cloud.payment.PaymentServiceApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractEndToEndFlowIntegrationTest {

    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    private final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private ConfigurableApplicationContext inventoryContext;
    private ConfigurableApplicationContext paymentContext;
    private ConfigurableApplicationContext authContext;
    private ConfigurableApplicationContext orderContext;

    private int inventoryPort;
    private int paymentPort;
    private int authPort;
    private int orderPort;
    private Path repoRoot;

    @BeforeAll
    void setUp() throws Exception {
        // INTENTIONAL: used only to validate that GitHub branch protection enforces required checks.
        // Do not merge this change.
        Assertions.fail("INTENTIONAL: validating required checks enforcement. Do not merge.");

        // Testcontainers uses a shaded docker-java core which defaults to an old API (v1.32) unless overridden.
        // Modern Docker daemons on CI reject such old versions (minimum supported is typically >= v1.44).
        if (System.getProperty("api.version") == null) {
            System.setProperty("api.version", "1.44");
        }

        postgres.start();
        rabbitmq.start();

        createDatabases();

        repoRoot = Path.of(System.getProperty("user.dir")).getParent().getParent();

        inventoryPort = freePort();
        paymentPort = freePort();
        authPort = freePort();
        orderPort = freePort();

        inventoryContext = startInventoryService();
        paymentContext = startPaymentService();
        authContext = startAuthService();
        orderContext = startOrderService();
    }

    @AfterAll
    void tearDown() {
        closeContext(orderContext);
        closeContext(authContext);
        closeContext(paymentContext);
        closeContext(inventoryContext);

        rabbitmq.stop();
        postgres.stop();
    }

    @Test
    void shouldProcessOrderThroughConfiguredPaymentModeFlow() throws Exception {
        postJson(inventoryPort, "/api/stocks", "{\"skuId\":\"SKU-001\",\"availableQty\":10}", Map.of());
        postJson(inventoryPort, "/api/stocks", "{\"skuId\":\"SKU-002\",\"availableQty\":10}", Map.of());

        String idempotencyKey = "it-" + UUID.randomUUID();
        HttpResponse<String> createOrderResponse = postJson(
                orderPort,
                "/api/orders",
                "{\"userId\":\"it-user\",\"items\":[{\"skuId\":\"SKU-001\",\"quantity\":1,\"price\":10.00},{\"skuId\":\"SKU-002\",\"quantity\":1,\"price\":12.00}]}",
                Map.of(
                        "Idempotency-Key", idempotencyKey,
                        "Authorization", bearerToken("it-user", List.of("buyer"))
                )
        );

        Assertions.assertEquals(201, createOrderResponse.statusCode());
        JsonNode created = objectMapper.readTree(createOrderResponse.body());
        UUID orderId = UUID.fromString(created.path("orderId").asText());

        String expectedOrderStatus = expectedOrderStatus(orderId);
        String expectedInventoryStatus = expectedInventoryStatus(orderId);
        String expectedPaymentStatus = expectedPaymentStatus(orderId);
        int expectedReleaseEventCount = expectedInventoryReleaseEventCount(orderId);

        JsonNode finalOrder = waitForOrderTerminalState(orderId, Duration.ofSeconds(60));
        Assertions.assertEquals(expectedOrderStatus, finalOrder.path("status").asText());

        assertDatabaseValueEventually(
                "order_db",
                "select status from orders where id = '" + orderId + "'::uuid",
                expectedOrderStatus,
                Duration.ofSeconds(45)
        );
        assertDatabaseValueEventually(
                "inventory_db",
                "select status from inventory_reservations where order_id = '" + orderId + "'::uuid",
                expectedInventoryStatus,
                Duration.ofSeconds(45)
        );
        assertDatabaseValueEventually(
                "payment_db",
                "select status from payment_records where order_id = '" + orderId + "'::uuid",
                expectedPaymentStatus,
                Duration.ofSeconds(45)
        );
        assertDatabaseValueEventually(
                "inventory_db",
                "select count(*)::text from inventory_release_events where order_id = '" + orderId + "'::uuid",
                String.valueOf(expectedReleaseEventCount),
                Duration.ofSeconds(45)
        );
    }

    @Test
    void shouldRejectCreateOrderWhenAuthorizationHeaderMissing() throws Exception {
        String idempotencyKey = "it-auth-missing-" + UUID.randomUUID();
        HttpResponse<String> response = postJson(
                orderPort,
                "/api/orders",
                "{\"userId\":\"it-user\",\"items\":[{\"skuId\":\"SKU-001\",\"quantity\":1,\"price\":10.00}]}",
                Map.of("Idempotency-Key", idempotencyKey)
        );

        Assertions.assertEquals(401, response.statusCode());
    }

    @Test
    void shouldRejectCreateOrderWhenRequiredRoleMissing() throws Exception {
        String idempotencyKey = "it-role-missing-" + UUID.randomUUID();
        HttpResponse<String> response = postJson(
                orderPort,
                "/api/orders",
                "{\"userId\":\"it-user\",\"items\":[{\"skuId\":\"SKU-001\",\"quantity\":1,\"price\":10.00}]}",
                Map.of(
                        "Idempotency-Key", idempotencyKey,
                        "Authorization", bearerToken("it-user", List.of("viewer"))
                )
        );

        Assertions.assertEquals(403, response.statusCode());
    }

    @Test
    void shouldRejectCreateOrderWhenTokenSubjectDoesNotMatchUserId() throws Exception {
        String idempotencyKey = "it-subject-mismatch-" + UUID.randomUUID();
        HttpResponse<String> response = postJson(
                orderPort,
                "/api/orders",
                "{\"userId\":\"it-user\",\"items\":[{\"skuId\":\"SKU-001\",\"quantity\":1,\"price\":10.00}]}",
                Map.of(
                        "Idempotency-Key", idempotencyKey,
                        "Authorization", bearerToken("different-user", List.of("buyer"))
                )
        );

        Assertions.assertEquals(400, response.statusCode());
    }

    protected abstract String paymentMockMode();

    protected abstract String expectedOrderStatus(UUID orderId);

    protected abstract String expectedPaymentStatus(UUID orderId);

    protected String expectedInventoryStatus(UUID orderId) {
        return "RESERVED";
    }

    protected int expectedInventoryReleaseEventCount(UUID orderId) {
        return 0;
    }

    private ConfigurableApplicationContext startInventoryService() {
        Map<String, String> extras = Map.of(
                "spring.application.name", "inventory-service-it",
                "spring.flyway.locations", migrationLocation("services/inventory-service/src/main/resources/db/migration")
        );
        return new SpringApplicationBuilder(InventoryServiceApplication.class)
                .properties(commonProperties(inventoryPort, dbUrl("inventory_db"), extras))
                .run();
    }

    private ConfigurableApplicationContext startPaymentService() {
        Map<String, String> extras = Map.of(
                "spring.application.name", "payment-service-it",
                "spring.flyway.locations", migrationLocation("services/payment-service/src/main/resources/db/migration"),
                "app.payment.mock-mode", paymentMockMode()
        );
        return new SpringApplicationBuilder(PaymentServiceApplication.class)
                .properties(commonProperties(paymentPort, dbUrl("payment_db"), extras))
                .run();
    }

    private ConfigurableApplicationContext startAuthService() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.config.location", "optional:file:./codex-nonexistent/");
        properties.put("server.port", String.valueOf(authPort));
        properties.put("spring.main.banner-mode", "off");
        properties.put("spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration");
        properties.put("spring.main.web-application-type", "servlet");
        properties.put("spring.application.name", "auth-service-it");
        properties.put("app.auth.issuer", "auth-service");

        return new SpringApplicationBuilder(AuthServiceApplication.class)
                .properties(properties)
                .run();
    }

    private ConfigurableApplicationContext startOrderService() {
        Map<String, String> extras = Map.of(
                "spring.application.name", "order-service-it",
                "spring.flyway.locations", migrationLocation("services/order-service/src/main/resources/db/migration"),
                "app.outbox.poll-interval-ms", "200",
                "app.auth.expected-issuer", "auth-service",
                "app.auth.jwks-uri", "http://localhost:" + authPort + "/.well-known/jwks.json",
                "app.auth.jwks-cache-seconds", "2"
        );
        return new SpringApplicationBuilder(OrderServiceApplication.class)
                .properties(commonProperties(orderPort, dbUrl("order_db"), extras))
                .run();
    }

    private Map<String, Object> commonProperties(int port, String datasourceUrl, Map<String, String> extras) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("spring.config.location", "optional:file:./codex-nonexistent/");
        values.put("server.port", String.valueOf(port));
        values.put("spring.main.banner-mode", "off");
        values.put("spring.datasource.url", datasourceUrl);
        values.put("spring.datasource.username", postgres.getUsername());
        values.put("spring.datasource.password", postgres.getPassword());
        values.put("spring.jpa.hibernate.ddl-auto", "validate");
        values.put("spring.flyway.enabled", "true");
        values.put("spring.rabbitmq.host", rabbitmq.getHost());
        values.put("spring.rabbitmq.port", String.valueOf(rabbitmq.getAmqpPort()));
        values.put("spring.rabbitmq.username", rabbitmq.getAdminUsername());
        values.put("spring.rabbitmq.password", rabbitmq.getAdminPassword());
        values.putAll(extras);
        return values;
    }

    private String migrationLocation(String relativePath) {
        return "filesystem:" + repoRoot.resolve(relativePath).toAbsolutePath();
    }

    private JsonNode waitForOrderTerminalState(UUID orderId, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> response = get(orderPort, "/api/orders/" + orderId);
            if (response.statusCode() == 200) {
                JsonNode body = objectMapper.readTree(response.body());
                String status = body.path("status").asText();
                if ("CONFIRMED".equals(status) || "FAILED".equals(status)) {
                    return body;
                }
            }
            Thread.sleep(250L);
        }
        throw new IllegalStateException("Order did not reach terminal state in time: " + orderId);
    }

    private HttpResponse<String> postJson(int port, String path, String jsonBody, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String querySingleValue(String dbName, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(dbUrl(dbName), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new IllegalStateException("No rows returned for: " + sql);
            }
            return resultSet.getString(1);
        }
    }

    private void assertDatabaseValueEventually(String dbName, String sql, String expectedValue, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        String lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                lastSeen = querySingleValue(dbName, sql);
                if (expectedValue.equals(lastSeen)) {
                    return;
                }
            } catch (SQLException ignored) {
                // Retry until timeout in case migration/startup is still converging.
            }
            Thread.sleep(250L);
        }
        Assertions.fail("Expected value '" + expectedValue + "' but saw '" + lastSeen + "' for query: " + sql);
    }

    private void createDatabases() throws SQLException {
        try (Connection connection = DriverManager.getConnection(dbUrl("postgres"), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            createDatabaseIfMissing(statement, "order_db");
            createDatabaseIfMissing(statement, "inventory_db");
            createDatabaseIfMissing(statement, "payment_db");
        }
    }

    private void createDatabaseIfMissing(Statement statement, String database) throws SQLException {
        try {
            statement.execute("create database " + database);
        } catch (SQLException exception) {
            if (!"42P04".equals(exception.getSQLState())) {
                throw exception;
            }
        }
    }

    private String dbUrl(String dbName) {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + dbName;
    }

    private int freePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private void closeContext(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }

    private String bearerToken(String userId, List<String> roles) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "userId", userId,
                    "roles", roles,
                    "ttlSeconds", 600
            ));
            HttpResponse<String> response = postJson(authPort, "/api/auth/token", body, Map.of());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Auth service returned " + response.statusCode() + " when issuing token");
            }
            JsonNode token = objectMapper.readTree(response.body());
            String tokenType = token.path("tokenType").asText("Bearer");
            String accessToken = token.path("accessToken").asText("");
            if (accessToken.isBlank()) {
                throw new IllegalStateException("Auth service did not return access token");
            }
            return tokenType + " " + accessToken;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build test bearer token", exception);
        }
    }
}
