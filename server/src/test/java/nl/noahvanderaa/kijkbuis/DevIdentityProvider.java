package nl.noahvanderaa.kijkbuis;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fake identity provider for local development. Logging in shows a page to pick who to log in as: an admin, someone
 * who may change the quality, or a viewer without any privileges. Start it with {@code ./gradlew devIdentityProvider},
 * and point the [auth] section of your config at it:
 * <pre>
 * [auth]
 * issuer = "http://localhost:6743"
 * client-id = "kijkbuis"
 * client-secret = "test-secret"
 *
 * [auth.privileges]
 * admin = ["admins"]
 * transcode = ["transcoders"]
 * </pre>
 * It listens on every address, and its issuer is the address it's reached on. To log in from other devices, such as a
 * phone, use this machine's network address as the issuer instead of localhost, since browsers are sent there to log
 * in. Kijkbuis' public-url then needs to be reachable from those devices too.
 */
public final class DevIdentityProvider {

    private DevIdentityProvider() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6743;
        MockOidcProvider provider = new MockOidcProvider("0.0.0.0", port);
        provider.offerUsers(List.of(
                new MockOidcProvider.TestUser("dev", "dev", List.of("admins")),
                new MockOidcProvider.TestUser("quality", "quality", List.of("transcoders")),
                new MockOidcProvider.TestUser("viewer", "viewer", List.of())
        ));

        System.out.println("Fake identity provider running on port " + port + ", logging in lets you pick a user. Use one of these as the issuer:");
        for (String address : addresses()) {
            System.out.println("  http://" + address + ":" + port);
        }
    }

    /**
     * @return localhost, and this machine's IPv4 addresses on the network
     */
    private static List<String> addresses() throws Exception {
        List<String> addresses = new ArrayList<>(List.of("localhost"));
        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                continue;
            }
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (address instanceof Inet4Address) {
                    addresses.add(address.getHostAddress());
                }
            }
        }
        return addresses;
    }

}
