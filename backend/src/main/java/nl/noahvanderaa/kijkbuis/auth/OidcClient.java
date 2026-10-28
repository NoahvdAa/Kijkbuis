package nl.noahvanderaa.kijkbuis.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.ClientSecretPost;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSet;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Logs users in through OpenID Connect, using the authorization code flow with PKCE.
 * The provider's metadata is discovered lazily, so Kijkbuis can start while the identity provider is unreachable.
 */
public class OidcClient {

    public static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(10);
    private static final Logger LOGGER = LogManager.getLogger(OidcClient.class);
    private static final int MAX_PENDING_LOGINS = 1000;
    private static final int HTTP_TIMEOUT_MILLIS = 10_000;
    private static final int JWKS_SIZE_LIMIT = 256 * 1024;

    private final KijkbuisConfig.AuthConfig config;
    private final ClientID clientId;
    private final Scope scope;
    private final URI redirectUri;
    private final Map<String, PendingLogin> pendingLogins = new ConcurrentHashMap<>();
    private volatile Provider provider = null;

    public OidcClient(@NotNull KijkbuisConfig.AuthConfig config, @NotNull URI redirectUri) {
        this.config = config;
        this.clientId = new ClientID(config.clientId());
        this.scope = new Scope(config.scopes().toArray(String[]::new));
        this.scope.add(OIDCScopeValue.OPENID);
        this.redirectUri = redirectUri;
    }

    /**
     * Tries to discover the identity provider, so configuration mistakes show up at startup.
     * Failures are only logged, discovery is retried on the next login.
     */
    public void tryDiscover() {
        try {
            this.provider();
        } catch (ApiException ignored) {
            // Already logged by provider()
        }
    }

    /**
     * Starts a login.
     *
     * @param returnTo the app-relative path to send the user to once they're logged in
     * @return the state to bind to the user's browser, and the URL to send the user to
     */
    @NotNull
    public LoginRequest startLogin(@NotNull String returnTo) {
        Provider provider = this.provider();

        Instant now = Instant.now();
        this.pendingLogins.values().removeIf((login) -> login.expiresAt().isBefore(now));
        if (this.pendingLogins.size() >= MAX_PENDING_LOGINS) {
            throw new ApiException(StatusCodes.SERVICE_UNAVAILABLE, "Too many logins in progress, please try again later");
        }

        State state = new State();
        Nonce nonce = new Nonce();
        CodeVerifier codeVerifier = new CodeVerifier();
        AuthenticationRequest request = new AuthenticationRequest.Builder(ResponseType.CODE, this.scope, this.clientId, this.redirectUri)
                .endpointURI(provider.metadata().getAuthorizationEndpointURI())
                .state(state)
                .nonce(nonce)
                .codeChallenge(codeVerifier, CodeChallengeMethod.S256)
                .build();

        this.pendingLogins.put(state.getValue(), new PendingLogin(nonce, codeVerifier, returnTo, now.plus(LOGIN_TIMEOUT)));
        return new LoginRequest(state.getValue(), request.toURI());
    }

    /**
     * Completes a login after the identity provider redirected the user back to Kijkbuis.
     *
     * @param params       the query parameters of the callback request
     * @param browserState the state stored in the user's browser when the login was started
     * @return the user's identity and where to send them next
     */
    @NotNull
    public CompletedLogin completeLogin(@NotNull Map<String, List<String>> params, @Nullable String browserState) {
        AuthenticationResponse response;
        try {
            response = AuthenticationResponseParser.parse(this.redirectUri, params);
        } catch (ParseException ex) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "Malformed login callback");
        }

        // The state must match both a login we started and the browser that started it, to prevent login CSRF
        State state = response.getState();
        PendingLogin pending = state != null ? this.pendingLogins.remove(state.getValue()) : null;
        if (pending == null || pending.expiresAt().isBefore(Instant.now()) || !state.getValue().equals(browserState)) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "Your login expired or is invalid, please try logging in again");
        }

        if (!response.indicatesSuccess()) {
            ErrorObject error = response.toErrorResponse().getErrorObject();
            String reason = error.getDescription() != null ? error.getDescription() : error.getCode();
            throw new ApiException(StatusCodes.BAD_REQUEST, "The identity provider did not log you in: " + reason);
        }
        AuthorizationCode code = response.toSuccessResponse().getAuthorizationCode();
        if (code == null) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "Malformed login callback");
        }

        Provider provider = this.provider();
        OIDCTokens tokens = this.exchangeCode(provider, code, pending.codeVerifier());

        IDTokenClaimsSet idToken;
        try {
            idToken = provider.idTokenValidator().validate(tokens.getIDToken(), pending.nonce());
        } catch (BadJOSEException | JOSEException ex) {
            LOGGER.warn("Rejected ID token from the identity provider", ex);
            throw new ApiException(StatusCodes.BAD_GATEWAY, "The identity provider sent an invalid ID token");
        }
        UserInfo userInfo = this.fetchUserInfo(provider, tokens, idToken.getSubject());

        return new CompletedLogin(this.toIdentity(idToken, userInfo), pending.returnTo());
    }

    @NotNull
    private OIDCTokens exchangeCode(@NotNull Provider provider, @NotNull AuthorizationCode code, @NotNull CodeVerifier codeVerifier) {
        AuthorizationCodeGrant grant = new AuthorizationCodeGrant(code, this.redirectUri, codeVerifier);
        URI tokenEndpoint = provider.metadata().getTokenEndpointURI();
        ClientAuthentication clientAuthentication = this.clientAuthentication(provider);
        TokenRequest request = clientAuthentication != null
                ? new TokenRequest.Builder(tokenEndpoint, clientAuthentication, grant).build()
                : new TokenRequest.Builder(tokenEndpoint, this.clientId, grant).build();

        TokenResponse response;
        try {
            response = OIDCTokenResponseParser.parse(withTimeouts(request.toHTTPRequest()).send());
        } catch (IOException | ParseException ex) {
            LOGGER.warn("Failed to exchange an authorization code with the identity provider", ex);
            throw new ApiException(StatusCodes.BAD_GATEWAY, "Could not reach the identity provider");
        }

        if (!response.indicatesSuccess()) {
            LOGGER.warn("The identity provider rejected a token request: {}", response.toErrorResponse().getErrorObject().toJSONObject());
            throw new ApiException(StatusCodes.BAD_GATEWAY, "The identity provider rejected the login");
        }
        return ((OIDCTokenResponse) response.toSuccessResponse()).getOIDCTokens();
    }

    @Nullable
    private ClientAuthentication clientAuthentication(@NotNull Provider provider) {
        if (this.config.clientSecret().isEmpty()) {
            return null;
        }

        Secret secret = new Secret(this.config.clientSecret());
        List<ClientAuthenticationMethod> supported = provider.metadata().getTokenEndpointAuthMethods();
        if (supported != null && !supported.contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                && supported.contains(ClientAuthenticationMethod.CLIENT_SECRET_POST)) {
            return new ClientSecretPost(this.clientId, secret);
        }
        return new ClientSecretBasic(this.clientId, secret);
    }

    /**
     * Fetches the userinfo, since some identity providers only list groups there instead of in the ID token.
     * Failures are logged and otherwise ignored, the login then continues with just the ID token's claims.
     */
    @Nullable
    private UserInfo fetchUserInfo(@NotNull Provider provider, @NotNull OIDCTokens tokens, @NotNull Subject subject) {
        URI endpoint = provider.metadata().getUserInfoEndpointURI();
        if (endpoint == null || tokens.getBearerAccessToken() == null) {
            return null;
        }

        try {
            UserInfoResponse response = UserInfoResponse.parse(withTimeouts(new UserInfoRequest(endpoint, tokens.getBearerAccessToken()).toHTTPRequest()).send());
            if (!response.indicatesSuccess()) {
                LOGGER.warn("The identity provider rejected a userinfo request: {}", response.toErrorResponse().getErrorObject().toJSONObject());
                return null;
            }

            UserInfo userInfo = response.toSuccessResponse().getUserInfo();
            if (userInfo != null && !subject.equals(userInfo.getSubject())) {
                LOGGER.warn("Ignoring userinfo for subject {}, since the ID token is for subject {}", userInfo.getSubject(), subject);
                return null;
            }
            return userInfo;
        } catch (IOException | ParseException ex) {
            LOGGER.warn("Failed to fetch userinfo from the identity provider", ex);
            return null;
        }
    }

    @NotNull
    private OidcIdentity toIdentity(@NotNull IDTokenClaimsSet idToken, @Nullable UserInfo userInfo) {
        String subject = idToken.getSubject().getValue();

        String username = stringClaim("preferred_username", idToken, userInfo);
        if (username == null) {
            username = stringClaim("email", idToken, userInfo);
        }
        if (username == null) {
            username = subject;
        }

        String displayName = stringClaim("name", idToken, userInfo);
        if (displayName == null) {
            displayName = username;
        }

        Object groupsClaim = idToken.getClaim(this.config.groupsClaim());
        if (groupsClaim == null && userInfo != null) {
            groupsClaim = userInfo.getClaim(this.config.groupsClaim());
        }
        Set<String> groups = new HashSet<>();
        if (groupsClaim instanceof Collection<?> collection) {
            collection.forEach((group) -> groups.add(String.valueOf(group)));
        } else if (groupsClaim instanceof String group) {
            groups.add(group);
        }

        return new OidcIdentity(idToken.getIssuer().getValue(), subject, username, displayName, groups);
    }

    @Nullable
    private static String stringClaim(@NotNull String name, @NotNull ClaimsSet idToken, @Nullable ClaimsSet userInfo) {
        if (idToken.getClaim(name) instanceof String value && !value.isBlank()) {
            return value;
        }
        if (userInfo != null && userInfo.getClaim(name) instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }

    @NotNull
    private Provider provider() {
        Provider provider = this.provider;
        if (provider != null) {
            return provider;
        }

        synchronized (this) {
            if (this.provider == null) {
                try {
                    this.provider = this.discover();
                    LOGGER.info("Discovered identity provider {}", this.config.issuer());
                } catch (IOException | GeneralException ex) {
                    LOGGER.warn("Failed to discover identity provider {}", this.config.issuer(), ex);
                    throw new ApiException(StatusCodes.SERVICE_UNAVAILABLE, "The identity provider is unavailable, please try again later");
                }
            }
            return this.provider;
        }
    }

    @NotNull
    private Provider discover() throws IOException, GeneralException {
        OIDCProviderMetadata metadata = OIDCProviderMetadata.resolve(new Issuer(this.config.issuer()), HTTP_TIMEOUT_MILLIS, HTTP_TIMEOUT_MILLIS);

        // Accept any asymmetric algorithm the provider signs ID tokens with, symmetric ones would require the client secret
        Set<JWSAlgorithm> algorithms = metadata.getIDTokenJWSAlgs() == null ? Set.of() : metadata.getIDTokenJWSAlgs().stream()
                .filter(JWSAlgorithm.Family.SIGNATURE::contains)
                .collect(Collectors.toSet());
        if (algorithms.isEmpty()) {
            algorithms = Set.of(JWSAlgorithm.RS256);
        }

        JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                .create(metadata.getJWKSetURI().toURL(), new DefaultResourceRetriever(HTTP_TIMEOUT_MILLIS, HTTP_TIMEOUT_MILLIS, JWKS_SIZE_LIMIT))
                .retrying(true)
                .build();
        IDTokenValidator validator = new IDTokenValidator(metadata.getIssuer(), this.clientId, new JWSVerificationKeySelector<>(algorithms, jwkSource), null);

        return new Provider(metadata, validator);
    }

    @NotNull
    private static HTTPRequest withTimeouts(@NotNull HTTPRequest request) {
        request.setConnectTimeout(HTTP_TIMEOUT_MILLIS);
        request.setReadTimeout(HTTP_TIMEOUT_MILLIS);
        return request;
    }

    public record LoginRequest(@NotNull String state, @NotNull URI authorizationUri) {
    }

    public record CompletedLogin(@NotNull OidcIdentity identity, @NotNull String returnTo) {
    }

    private record Provider(@NotNull OIDCProviderMetadata metadata, @NotNull IDTokenValidator idTokenValidator) {
    }

    private record PendingLogin(@NotNull Nonce nonce, @NotNull CodeVerifier codeVerifier, @NotNull String returnTo,
                                @NotNull Instant expiresAt) {
    }

}
