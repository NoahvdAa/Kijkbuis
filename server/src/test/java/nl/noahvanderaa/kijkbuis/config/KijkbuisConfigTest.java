package nl.noahvanderaa.kijkbuis.config;

import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KijkbuisConfigTest {

    @Test
    void createsDefaultConfigWhenMissing(@TempDir Path dir) {
        Path path = dir.resolve("kijkbuis.toml");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> KijkbuisConfig.load(path));
        assertEquals("Invalid config: auth.issuer must be set", ex.getMessage());
        assertTrue(Files.exists(path));
    }

    @Test
    void parsesDefaultConfig(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("kijkbuis.toml");
        assertThrows(IllegalStateException.class, () -> KijkbuisConfig.load(path));
        Files.writeString(path, Files.readString(path)
                .replace("issuer = \"\"", "issuer = \"https://auth.example.com\"")
                .replace("client-id = \"\"", "client-id = \"kijkbuis\""));

        KijkbuisConfig config = KijkbuisConfig.load(path);
        assertEquals(6742, config.server().port());
        assertEquals("http://localhost:6742", config.server().publicUrl());
        assertFalse(config.server().secureCookies());
        assertEquals("https://auth.example.com", config.auth().issuer());
        assertEquals("", config.auth().clientSecret());
        assertEquals(List.of("openid", "profile", "email"), config.auth().scopes());
        assertEquals(Duration.ofDays(30), config.auth().sessionLifetime());
        assertEquals(EnumSet.allOf(Privilege.class), config.auth().privilegesFor(List.of("kijkbuis-admins")));
        assertEquals(Set.of(Privilege.TRANSCODE), config.auth().privilegesFor(List.of("kijkbuis-transcode", "unrelated")));
        assertEquals(Set.of(), config.auth().privilegesFor(List.of("unrelated")));
        assertEquals("ffmpeg", config.streaming().ffmpegPath());
        assertEquals("veryfast", config.transcoding().preset());
        assertTrue(config.transcoding().deinterlaceDoubleRate());
        assertEquals(List.of("1080p", "720p", "480p", "360p"), config.transcoding().profiles().stream().map(KijkbuisConfig.TranscodeProfile::id).toList());
        assertEquals(720, config.transcoding().profile("720p").height());
        assertEquals(3000, config.transcoding().profile("720p").bitrateKbps());
        // Each profile is a 16:9 box
        assertEquals(List.of(1920, 1280, 854, 640), config.transcoding().profiles().stream().map(KijkbuisConfig.TranscodeProfile::width).toList());
    }

    @Test
    void grantsPrivilegesToEveryone(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("kijkbuis.toml");
        Files.writeString(path, """
                [auth]
                issuer = "https://auth.example.com"
                client-id = "kijkbuis"

                [auth.privileges]
                admin = ["kijkbuis-admins"]
                transcode = ["*"]
                """);

        KijkbuisConfig config = KijkbuisConfig.load(path);
        assertEquals(Set.of(Privilege.TRANSCODE), config.auth().privilegesFor(List.of()));
        assertEquals(Set.of(Privilege.TRANSCODE), config.auth().privilegesFor(List.of("unrelated")));
        assertEquals(EnumSet.allOf(Privilege.class), config.auth().privilegesFor(List.of("kijkbuis-admins")));
    }

    @Test
    void rejectsAdminForEveryone(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("kijkbuis.toml");
        Files.writeString(path, """
                [auth]
                issuer = "https://auth.example.com"
                client-id = "kijkbuis"

                [auth.privileges]
                admin = ["kijkbuis-admins", "*"]
                """);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> KijkbuisConfig.load(path));
        assertTrue(ex.getMessage().contains("auth.privileges.admin can't be given to everyone"), ex.getMessage());
    }

    @Test
    void rejectsInvalidProfiles(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("kijkbuis.toml");
        Files.writeString(path, """
                [auth]
                issuer = "https://auth.example.com"
                client-id = "kijkbuis"

                [[transcoding.profiles]]
                id = "auto"
                height = 720
                bitrate-kbps = 3000
                """);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> KijkbuisConfig.load(path));
        assertTrue(ex.getMessage().contains("auto is reserved"), ex.getMessage());
    }

}
