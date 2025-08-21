import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.Gson;

public class CrptApi {
    private final HttpClient httpClient;
    private final Gson gson;
    private final RateLimiter rateLimiter;
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create"; // Placeholder из PDF
    private static final String AUTH_TOKEN = "Bearer <your_token>"; // Placeholder, из PDF аутентификация

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(); // Thread-safe клиент
        this.gson = new Gson(); // Immutable, thread-safe
        this.rateLimiter = new RateLimiter(timeUnit, requestLimit);
    }
}
