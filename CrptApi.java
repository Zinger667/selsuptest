package crpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
Клиент API ГИС МТ, для ввода товара в оборот (POST /api/v3/lk/documents/create).
*/
public class CrptApi {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final AtomicReference<String> authToken;
    private static final String BASE_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private CrptApi(TimeUnit timeUnit, int requestLimit, String authToken) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.rateLimiter = new RateLimiter(timeUnit, requestLimit);
        this.authToken = new AtomicReference<>(authToken);
    }

    public Integer createDocument(Document document, String signature) throws Exception {
        rateLimiter.acquire();

        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode documentNode = objectMapper.convertValue(document, ObjectNode.class);
        requestBody.setAll(documentNode);
        requestBody.put("signature", signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(BASE_URL))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Authorization", authToken.get())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            ObjectNode responseNode = objectMapper.readValue(response.body(), ObjectNode.class);

            if (!responseNode.has("document_id")) {
                throw new RuntimeException("Response does not contain document_id");
            }

            return responseNode.get("document_id").asInt();
        } else if (response.statusCode() == 400) {
            throw new IllegalArgumentException("Invalid request data: " + response.body());
        } else if (response.statusCode() == 401) {
            throw new SecurityException("Unauthorized: Invalid token");
        } else {
            throw new RuntimeException("API request failed: " + response.statusCode() + ", body: " + response.body());
        }
    }

    private static class RateLimiter {
        private final int requestLimit;
        private final AtomicInteger requestCount;
        private final long windowSize;
        private long windowStart;

        public RateLimiter(TimeUnit timeUnit, int requestLimit) {
            this.requestLimit = requestLimit;
            this.requestCount = new AtomicInteger(0);
            this.windowSize = timeUnit.toMillis(1);
            this.windowStart = System.currentTimeMillis() / windowSize * windowSize;
        }

        public synchronized void acquire() throws InterruptedException {
            long currentTime = System.currentTimeMillis();
            long windowEnd = windowStart + windowSize;

            if (currentTime >= windowEnd) {
                requestCount.set(0);
                windowStart = currentTime / windowSize * windowSize;
                windowEnd = windowStart + windowSize;
            }

            while (requestCount.get() >= requestLimit) {
                long waitTime = windowEnd - currentTime;

                if (waitTime > 0) {
                    wait(waitTime);
                }

                currentTime = System.currentTimeMillis();
                windowEnd = windowStart + windowSize;

                if (currentTime >= windowEnd) {
                    requestCount.set(0);
                    windowStart = currentTime / windowSize * windowSize;
                    windowEnd = windowStart + windowSize;
                }
            }

            requestCount.incrementAndGet();
        }
    }

    private static class Document {
        private final String participantInn;
        private final String documentFormat;
        private final String productDocument;
        private final String type;
        private final List<Product> products;

        public Document(String participantInn, String documentFormat, String productDocument, String type, List<Product> products) {
            this.participantInn = participantInn;
            this.documentFormat = documentFormat;
            this.productDocument = productDocument;
            this.type = type;
            this.products = Objects.nonNull(products) ? new ArrayList<>(products) : new ArrayList<>();
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public String getDocumentFormat() {
            return documentFormat;
        }

        public String getProductDocument() {
            return productDocument;
        }

        public String getType() {
            return type;
        }

        public List<Product> getProducts() {
            return new ArrayList<>(products);
        }
    }

    private static class Product {
        private final String cis;
        private final String productGtin;
        private final String productionDate;
        private final String producerInn;

        public Product(String cis, String productGtin, String productionDate, String producerInn) {
            this.cis = cis;
            this.productGtin = productGtin;
            this.productionDate = productionDate;
            this.producerInn = producerInn;
        }

        public String getCis() {
            return cis;
        }

        public String getProductGtin() {
            return productGtin;
        }

        public String getProductionDate() {
            return productionDate;
        }

        public String getProducerInn() {
            return producerInn;
        }
    }
}
