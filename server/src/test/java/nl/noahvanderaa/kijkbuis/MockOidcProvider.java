package nl.noahvanderaa.kijkbuis;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal OpenID Connect provider for tests. It logs in whichever user was last set with {@link #nextUser}, or lets
 * you pick one of the users set with {@link #offerUsers}. It only lists the user's groups in the userinfo response, like
 * some real providers do.
 * <p>
 * Its issuer is the address it's reached on, such as http://localhost:6743 or http://192.168.1.10:6743, so it works
 * from any address Kijkbuis is configured with.
 */
final class MockOidcProvider implements AutoCloseable {

    static final String CLIENT_ID = "kijkbuis";
    static final String CLIENT_SECRET = "test-secret";

    private final RSAKey signingKey;
    private final String issuer;
    private final Undertow server;
    private final Map<String, Authorization> authorizations = new ConcurrentHashMap<>();
    private final Map<String, TestUser> accessTokens = new ConcurrentHashMap<>();
    private volatile TestUser nextUser = new TestUser("user-1", "alice", List.of());
    private volatile List<TestUser> offeredUsers = List.of();

    /**
     * Starts the provider on localhost, so it can't be reached from other machines.
     */
    MockOidcProvider(int port) throws JOSEException {
        this("localhost", port);
    }

    /**
     * @param host the address to listen on, such as 0.0.0.0 for every address
     */
    MockOidcProvider(@NotNull String host, int port) throws JOSEException {
        this.signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        this.issuer = "http://localhost:" + port;
        this.server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(new BlockingHandler(Handlers.routing()
                        .get("/.well-known/openid-configuration", this::discovery)
                        .get("/authorize", this::authorize)
                        .post("/token", this::token)
                        .get("/jwks", this::jwks)
                        .get("/userinfo", this::userInfo)))
                .build();
        this.server.start();
    }

    /**
     * @return the issuer when reached on localhost
     */
    @NotNull
    String issuer() {
        return this.issuer;
    }

    void nextUser(@NotNull String subject, @NotNull String username, @NotNull List<String> groups) {
        this.nextUser = new TestUser(subject, username, groups);
    }

    /**
     * Makes logging in show a page to pick one of these users, instead of logging in the next user right away.
     *
     * @param users each as subject, username and groups
     */
    void offerUsers(@NotNull List<TestUser> users) {
        this.offeredUsers = List.copyOf(users);
    }

    @Override
    public void close() {
        this.server.stop();
    }

    private void discovery(HttpServerExchange exchange) {
        JsonObject metadata = new JsonObject();
        String issuer = issuerOf(exchange);
        metadata.addProperty("issuer", issuer);
        metadata.addProperty("authorization_endpoint", issuer + "/authorize");
        metadata.addProperty("token_endpoint", issuer + "/token");
        metadata.addProperty("userinfo_endpoint", issuer + "/userinfo");
        metadata.addProperty("jwks_uri", issuer + "/jwks");
        metadata.add("response_types_supported", array("code"));
        metadata.add("subject_types_supported", array("public"));
        metadata.add("id_token_signing_alg_values_supported", array("RS256"));
        metadata.add("token_endpoint_auth_methods_supported", array("client_secret_basic"));
        metadata.add("code_challenge_methods_supported", array("S256"));
        json(exchange, StatusCodes.OK, metadata.toString());
    }

    private void authorize(HttpServerExchange exchange) {
        Map<String, String> params = new HashMap<>();
        exchange.getQueryParameters().forEach((name, values) -> params.put(name, values.getFirst()));
        if (!CLIENT_ID.equals(params.get("client_id")) || !"S256".equals(params.get("code_challenge_method"))) {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            return;
        }

        TestUser user = this.nextUser;
        if (!this.offeredUsers.isEmpty()) {
            user = this.offeredUsers.stream().filter((offered) -> offered.subject().equals(params.get("as"))).findFirst().orElse(null);
            if (user == null) {
                this.showUserPicker(exchange);
                return;
            }
        }

        String code = UUID.randomUUID().toString();
        this.authorizations.put(code, new Authorization(params.get("redirect_uri"), params.get("nonce"), params.get("code_challenge"), user));
        exchange.setStatusCode(StatusCodes.FOUND);
        exchange.getResponseHeaders().put(Headers.LOCATION, params.get("redirect_uri")
                + "?code=" + code + "&state=" + URLEncoder.encode(params.get("state"), StandardCharsets.UTF_8));
    }

    /**
     * Shows a page with a link per user, which continues logging in as that user.
     */
    private void showUserPicker(HttpServerExchange exchange) {
        StringBuilder links = new StringBuilder();
        for (TestUser user : this.offeredUsers) {
            String url = "/authorize?" + exchange.getQueryString() + "&as=" + URLEncoder.encode(user.subject(), StandardCharsets.UTF_8);
            links.append("<li><a href=\"").append(url.replace("&", "&amp;")).append("\">").append(user.username())
                    .append("</a> <small>").append(user.groups().isEmpty() ? "no groups" : String.join(", ", user.groups())).append("</small></li>");
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=utf-8");
        exchange.getResponseSender().send("""
                <!doctype html>
                <title>Fake login</title>
                <style>body{font:16px system-ui;margin:3rem;background:#111;color:#eee}a{color:#f4c430}li{margin:.6rem 0}small{color:#999}</style>
                <h1>Log in as</h1>
                <ul>%s</ul>
                """.formatted(links));
    }

    private void token(HttpServerExchange exchange) throws IOException, JOSEException {
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        if (!expectedAuth.equals(exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION))) {
            json(exchange, StatusCodes.UNAUTHORIZED, "{\"error\":\"invalid_client\"}");
            return;
        }

        Map<String, String> form = new HashMap<>();
        for (String pair : new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
            String[] parts = pair.split("=", 2);
            form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }

        Authorization authorization = this.authorizations.remove(form.getOrDefault("code", ""));
        if (!"authorization_code".equals(form.get("grant_type")) || authorization == null
                || !authorization.redirectUri().equals(form.get("redirect_uri"))
                || !CodeChallenge.compute(CodeChallengeMethod.S256, new CodeVerifier(form.get("code_verifier"))).getValue().equals(authorization.codeChallenge())) {
            json(exchange, StatusCodes.BAD_REQUEST, "{\"error\":\"invalid_grant\"}");
            return;
        }

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                // Kijkbuis asks for tokens at the address it discovered the provider on, which is the issuer it expects
                .issuer(issuerOf(exchange))
                .subject(authorization.user().subject())
                .audience(CLIENT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("nonce", authorization.nonce())
                .claim("preferred_username", authorization.user().username())
                .build();
        SignedJWT idToken = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(this.signingKey.getKeyID()).build(), claims);
        idToken.sign(new RSASSASigner(this.signingKey));

        String accessToken = UUID.randomUUID().toString();
        this.accessTokens.put(accessToken, authorization.user());

        JsonObject response = new JsonObject();
        response.addProperty("access_token", accessToken);
        response.addProperty("token_type", "Bearer");
        response.addProperty("expires_in", 300);
        response.addProperty("id_token", idToken.serialize());
        json(exchange, StatusCodes.OK, response.toString());
    }

    private void jwks(HttpServerExchange exchange) {
        json(exchange, StatusCodes.OK, new JWKSet(this.signingKey.toPublicJWK()).toString());
    }

    private void userInfo(HttpServerExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        TestUser user = authorization != null && authorization.startsWith("Bearer ") ? this.accessTokens.get(authorization.substring(7)) : null;
        if (user == null) {
            exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("sub", user.subject());
        response.addProperty("name", "Test user " + user.username());
        response.add("groups", array(user.groups().toArray(String[]::new)));
        json(exchange, StatusCodes.OK, response.toString());
    }

    /**
     * @return the issuer as reached by this request, such as http://192.168.1.10:6743
     */
    @NotNull
    private static String issuerOf(@NotNull HttpServerExchange exchange) {
        return "http://" + exchange.getHostAndPort();
    }

    @NotNull
    private static JsonArray array(@NotNull String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static void json(@NotNull HttpServerExchange exchange, int statusCode, @NotNull String body) {
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(body);
    }

    record TestUser(@NotNull String subject, @NotNull String username, @NotNull List<String> groups) {
    }

    private record Authorization(@NotNull String redirectUri, @NotNull String nonce, @NotNull String codeChallenge,
                                 @NotNull TestUser user) {
    }

}
