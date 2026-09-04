package com.kcpc.mkt.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Drives the application exactly as a real browser/REST client would over the real servlet
 * filter chain: cookie-based JWT auth, CSRF priming + token header, no shortcuts. This is the
 * automated form of the manual/scripted HTTP smoke flows used throughout development.
 */
public class TestApiClient {

    private final HttpClient httpClient;
    private final CookieManager cookieManager;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String csrfToken;

    public TestApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.cookieManager = new CookieManager();
        this.httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();
    }

    public void primeCsrf() throws IOException, InterruptedException {
        JsonNode body = getJson("/api/v1/csrf");
        this.csrfToken = body.get("token").asText();
    }

    public void clearCsrfToken() {
        this.csrfToken = null;
    }

    public String currentCookieValue(String name) {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name)).findFirst()
                .map(java.net.HttpCookie::getValue)
                .orElseThrow(() -> new IllegalStateException("No such cookie currently held: " + name));
    }

    public JsonNode login(String email, String password) throws IOException, InterruptedException {
        primeCsrf();
        return postJson("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    public HttpResponse<String> loginRaw(String email, String password) throws IOException, InterruptedException {
        primeCsrf();
        return post("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        debug("GET", path, response);
        return response;
    }

    /**
     * Same as {@link #get(String)} but with the {@code X-Requested-With: fetch} header the
     * pipeline dashboard's own {@code fetch()} calls send - lets a test drive the AJAX partial-
     * fragment response path (e.g. {@code /app/pipeline}) the same way pipeline-dashboard.js does.
     */
    public HttpResponse<String> getAjax(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("X-Requested-With", "fetch")
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        debug("GET(ajax)", path, response);
        return response;
    }

    /**
     * Bypasses this client's own cookie jar to send an explicit, caller-supplied raw cookie
     * value - used to prove server-side revocation independent of a well-behaved client having
     * already discarded a cookie the server told it to delete (a real browser deletes the
     * cookie on receiving {@code Max-Age=0}; only a malicious replay - or a stale cached copy -
     * would ever resend it, and the server must still reject it).
     */
    public HttpResponse<String> getWithRawCookie(String path, String cookieHeaderValue)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Cookie", cookieHeaderValue)
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        debug("GET(raw-cookie)", path, response);
        return response;
    }

    public JsonNode getJson(String path) throws IOException, InterruptedException {
        return objectMapper.readTree(get(path).body());
    }

    /** Same as {@link #get(String)} but for binary responses (e.g. images) - {@link #get(String)}
     * decodes the body as a String, which corrupts binary bytes. */
    public HttpResponse<byte[]> getBytes(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    public HttpResponse<String> post(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        if (csrfToken != null) {
            builder.header("X-CSRF-TOKEN", csrfToken);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        debug("POST", path, response);
        return response;
    }

    public JsonNode postJson(String path, String jsonBody) throws IOException, InterruptedException {
        return objectMapper.readTree(post(path, jsonBody).body());
    }

    /**
     * Drives an MVC form POST exactly as a JSP {@code <form>} does: url-encoded body, the CSRF
     * token carried as the {@code _csrf} form parameter (not the REST client's header), and
     * redirects NOT auto-followed so the caller can assert on the 302 {@code Location} itself.
     */
    public HttpResponse<String> postForm(String path, java.util.Map<String, String> formFields)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        if (csrfToken != null) {
            body.append("_csrf=").append(java.net.URLEncoder.encode(csrfToken, java.nio.charset.StandardCharsets.UTF_8));
        }
        for (var entry : formFields.entrySet()) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }
        HttpClient noRedirectClient = HttpClient.newBuilder().cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = noRedirectClient.send(request, HttpResponse.BodyHandlers.ofString());
        debug("POST(form)", path, response);
        return response;
    }

    /**
     * Same as {@link #postForm(String, java.util.Map)} but for fields that repeat the same name
     * with multiple values in one submission - e.g. a browser submitting several checked
     * checkboxes that share a {@code name} attribute ({@code Map<String, String>} cannot express
     * that; each key can only hold one value).
     */
    public HttpResponse<String> postFormMulti(String path, java.util.Map<String, java.util.List<String>> formFields)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        if (csrfToken != null) {
            body.append("_csrf=").append(java.net.URLEncoder.encode(csrfToken, java.nio.charset.StandardCharsets.UTF_8));
        }
        for (var entry : formFields.entrySet()) {
            for (String value : entry.getValue()) {
                if (!body.isEmpty()) {
                    body.append('&');
                }
                body.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8))
                        .append('=')
                        .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        HttpClient noRedirectClient = HttpClient.newBuilder().cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = noRedirectClient.send(request, HttpResponse.BodyHandlers.ofString());
        debug("POST(form-multi)", path, response);
        return response;
    }

    /**
     * Same as {@link #postForm(String, java.util.Map)} but with the {@code X-Requested-With:
     * fetch} header the browser-side JS adds - drives the same MVC endpoints down their
     * AJAX branch (JSON/plain status response instead of a redirect+flash message).
     */
    public HttpResponse<String> postFormAjax(String path, java.util.Map<String, String> formFields)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        if (csrfToken != null) {
            body.append("_csrf=").append(java.net.URLEncoder.encode(csrfToken, java.nio.charset.StandardCharsets.UTF_8));
        }
        for (var entry : formFields.entrySet()) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Requested-With", "fetch")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        debug("POST(form-ajax)", path, response);
        return response;
    }

    /** Same as {@link #postFormMulti(String, java.util.Map)} but down the AJAX branch (see {@link #postFormAjax}). */
    public HttpResponse<String> postFormMultiAjax(String path, java.util.Map<String, java.util.List<String>> formFields)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        if (csrfToken != null) {
            body.append("_csrf=").append(java.net.URLEncoder.encode(csrfToken, java.nio.charset.StandardCharsets.UTF_8));
        }
        for (var entry : formFields.entrySet()) {
            for (String value : entry.getValue()) {
                if (!body.isEmpty()) {
                    body.append('&');
                }
                body.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8))
                        .append('=')
                        .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Requested-With", "fetch")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        debug("POST(form-multi-ajax)", path, response);
        return response;
    }

    /**
     * Multipart file upload exactly as a browser's {@code <form enctype="multipart/form-data">}
     * sends it - the CSRF token is included as a regular form field (the same {@code _csrf}
     * parameter name the JSP hidden input uses), not a header, matching every other
     * {@code postForm*} method here. {@code java.net.http.HttpClient} has no built-in multipart
     * body builder, so the boundary-delimited body is hand-assembled.
     */
    public HttpResponse<String> postMultipartFile(String path, String fieldName, String filename, byte[] fileContent,
                                                    String contentType) throws IOException, InterruptedException {
        String boundary = "----TestApiClientBoundary" + System.nanoTime();
        var bytesOut = new java.io.ByteArrayOutputStream();
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
        if (csrfToken != null) {
            bytesOut.writeBytes(("--" + boundary + "\r\n").getBytes(utf8));
            bytesOut.writeBytes(("Content-Disposition: form-data; name=\"" + "_csrf" + "\"\r\n\r\n").getBytes(utf8));
            bytesOut.writeBytes((csrfToken + "\r\n").getBytes(utf8));
        }
        bytesOut.writeBytes(("--" + boundary + "\r\n").getBytes(utf8));
        bytesOut.writeBytes(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename
                + "\"\r\n").getBytes(utf8));
        bytesOut.writeBytes(("Content-Type: " + contentType + "\r\n\r\n").getBytes(utf8));
        bytesOut.writeBytes(fileContent);
        bytesOut.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(utf8));

        HttpClient noRedirectClient = HttpClient.newBuilder().cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytesOut.toByteArray()))
                .build();
        HttpResponse<String> response = noRedirectClient.send(request, HttpResponse.BodyHandlers.ofString());
        debug("POST(multipart)", path, response);
        return response;
    }

    private void debug(String method, String path, HttpResponse<String> response) {
        if (Boolean.getBoolean("testApiClient.debug")) {
            System.out.println("DEBUG " + method + " " + path + " csrfToken=" + csrfToken
                    + " cookieStore=" + cookieManager.getCookieStore().getCookies()
                    + " setCookie=" + response.headers().allValues("Set-Cookie")
                    + " -> " + response.statusCode() + " " + response.body());
        }
    }
}
