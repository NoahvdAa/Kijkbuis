package nl.noahvanderaa.kijkbuis.auth;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * The identity of a user as reported by the identity provider after a successful login.
 *
 * @param issuer      the issuer that authenticated the user
 * @param subject     the user's unique ID at the issuer
 * @param username    the username (preferred_username, falling back to email and then the subject)
 * @param displayName the display name (name, falling back to the username)
 * @param groups      the groups listed in the configured groups claim
 */
public record OidcIdentity(
        @NotNull String issuer,
        @NotNull String subject,
        @NotNull String username,
        @NotNull String displayName,
        @NotNull Set<String> groups
) {
}
