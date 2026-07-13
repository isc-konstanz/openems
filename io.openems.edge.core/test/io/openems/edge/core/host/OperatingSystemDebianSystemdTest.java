package io.openems.edge.core.host;

import static io.openems.common.utils.JsonUtils.prettyToString;
import static io.openems.edge.core.host.OperatingSystemDebianSystemd.parseNetworkManagerConnectionFile;
import static io.openems.edge.core.host.OperatingSystemDebianSystemd.parseSystemdNetworkdConfigurationFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.core.host.NetworkInterface.IpMasqueradeSetting;

public class OperatingSystemDebianSystemdTest {

	/*
	 * The following tests parse equivalent systemd-networkd and NetworkManager
	 * configurations and assert that both backends produce the same
	 * NetworkInterface. parseSystemdNetworkdConfigurationFile is the reference.
	 */

	@Test
	public void testDhcpWithLinkLocal() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes
				LinkLocalAddressing=yes
				""".lines().toList(), null);
		var nm = parseNetworkManagerConnectionFile("""
				[connection]
				id=eth0
				type=ethernet
				interface-name=eth0

				[ipv4]
				method=auto
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertTrue(n.getDhcp().getValue());
			assertTrue(n.getLinkLocalAddressing().getValue());
		}
	}

	@Test
	public void testStaticSingleAddressWithGateway() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Gateway=192.168.100.1

				[Address]
				Address=192.168.100.100/24
				""".lines().toList(), null);
		var nm = parseNetworkManagerConnectionFile("""
				[connection]
				id=eth0
				type=ethernet
				interface-name=eth0

				[ipv4]
				method=manual
				address1=192.168.100.100/24
				gateway=192.168.100.1
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertFalse(n.getDhcp().getValue());
			assertEquals(Set.of("192.168.100.100/24"), addressStrings(n));
			assertEquals("192.168.100.1", n.getGateway().getValue().getHostAddress());
		}
	}

	@Test
	public void testStaticMultipleAddresses() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Address=192.168.100.100/24
				Address=10.4.0.1/24
				Gateway=10.4.0.2
				""".lines().toList(), null);
		var nm = parseNetworkManagerConnectionFile("""
				[connection]
				id=eth0
				type=ethernet
				interface-name=eth0

				[ipv4]
				method=manual
				address1=192.168.100.100/24
				address2=10.4.0.1/24
				gateway=10.4.0.2
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertFalse(n.getDhcp().getValue());
			assertEquals(Set.of("192.168.100.100/24", "10.4.0.1/24"), addressStrings(n));
			assertEquals("10.4.0.2", n.getGateway().getValue().getHostAddress());
		}
	}

	@Test
	public void testLinkLocalOnly() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				""".lines().toList(), null);
		var nm = parseNetworkManagerConnectionFile("""
				[connection]
				id=eth0
				type=ethernet
				interface-name=eth0

				[ipv4]
				method=link-local
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertFalse(n.getDhcp().getValue());
			assertTrue(n.getLinkLocalAddressing().getValue());
		}
	}

	@Test
	public void testDhcpWithCustomRouteMetric() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes

				[DHCP]
				RouteMetric=216
				""".lines().toList(), null);
		var nm = parseNetworkManagerConnectionFile("""
				[connection]
				id=eth0
				type=ethernet
				interface-name=eth0

				[ipv4]
				method=auto
				route-metric=216
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertTrue(n.getDhcp().getValue());
			assertEquals(216, n.getDhcpRouteMetric().getValue().intValue());
		}
	}

	@Test
	public void testStaticWithDnsAndMetric() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				DNS=10.0.0.1
				LinkLocalAddressing=yes
				Gateway=10.0.10.10
				Address=10.4.0.1/16

				[DHCP]
				RouteMetric=520
				""".lines().toList(), null);
		var nm = parseNetworkManagerConnectionFile("""
				[connection]
				id=eth0
				type=ethernet
				interface-name=eth0

				[ipv4]
				method=manual
				address1=10.4.0.1/16
				dns=10.0.0.1;
				gateway=10.0.10.10
				route-metric=520
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertFalse(n.getDhcp().getValue());
			assertEquals("10.0.0.1", n.getDns().getValue().getHostAddress());
			assertEquals("10.0.10.10", n.getGateway().getValue().getHostAddress());
			assertEquals(520, n.getDhcpRouteMetric().getValue().intValue());
			assertEquals(Set.of("10.4.0.1/16"), addressStrings(n));
		}
	}

	/*
	 * NetworkManager-specific tests.
	 */

	@Test
	public void testNetworkManagerReadsGatewayFromAddressLine() throws OpenemsNamedException {
		// NetworkManager allows the gateway to be appended to address1 as
		// "<ip>/<prefix>,<gateway>"
		final var n = parseNetworkManagerConnectionFile("""
				[connection]
				id=eth0
				type=ethernet
				interface-name=eth0

				[ipv4]
				method=manual
				address1=192.168.100.100/24,192.168.100.1
				dns=8.8.8.8;1.1.1.1;
				""".lines().toList(), null);

		assertEquals("192.168.100.1", n.getGateway().getValue().getHostAddress());
		assertEquals("8.8.8.8", n.getDns().getValue().getHostAddress()); // first DNS entry wins
	}

	@Test
	public void testNetworkManagerSkipsNonEthernetConnections() throws OpenemsNamedException {
		final var lines = """
				[connection]
				id=WLAN
				type=wifi
				interface-name=wlan0

				[ipv4]
				method=auto
				""".lines().toList();

		assertNull(parseNetworkManagerConnectionFile(lines, null));
	}

	@Test
	public void testNetworkManagerFallsBackToIdWhenInterfaceNameMissing() throws OpenemsNamedException {
		final var n = parseNetworkManagerConnectionFile("""
				[connection]
				id=eth0
				type=ethernet

				[ipv4]
				method=auto
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
	}

	/*
	 * systemd-networkd-specific tests.
	 */

	@Test
	public void testSystemdNetworkdPreservesAddressLabel() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes
				LinkLocalAddressing=yes

				[Address]
				Address=192.168.100.100/24
				Label=normal
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(true, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		assertEquals("192.168.100.100/24", n.getAddresses().getValue().toArray()[0].toString());

		assertEquals("""
				{
				  "dhcp": true,
				  "linkLocalAddressing": true,
				  "addresses": [
				    {
				      "label": "normal",
				      "address": "192.168.100.100",
				      "subnetmask": "255.255.255.0"
				    }
				  ]
				}""", prettyToString(n.toJson()));

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdMultipleAddressesWithMixedLabels() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes
				LinkLocalAddressing=yes

				[Address]
				Address=192.168.100.100/24
				Label=normal

				[Address]
				Address=192.168.123.123/24
				Label=
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(true, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		{
			var address = (Inet4AddressWithSubnetmask) n.getAddresses().getValue().toArray()[0];
			assertEquals("192.168.100.100/24", address.toString());
			assertEquals("normal", address.getLabel());
		}
		{
			var address = (Inet4AddressWithSubnetmask) n.getAddresses().getValue().toArray()[1];
			assertEquals("192.168.123.123/24", address.toString());
			assertEquals("", address.getLabel());
		}

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdLabelBeforeAddressIsIgnored() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes
				LinkLocalAddressing=yes

				[Address]
				Address=192.168.100.100/24
				Label=fallback

				[Address]
				Label=foo
				Address=192.168.123.123/24
				""".lines().toList(), null);
		{
			var address = (Inet4AddressWithSubnetmask) n.getAddresses().getValue().toArray()[0];
			assertEquals("192.168.100.100/24", address.toString());
			assertEquals("fallback", address.getLabel());
		}
		{
			var address = (Inet4AddressWithSubnetmask) n.getAddresses().getValue().toArray()[1];
			assertEquals("192.168.123.123/24", address.toString());
			assertEquals("", address.getLabel()); // NOTE: if Label is before Address, it is ignored
		}

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdWildcardInterfaceName() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=enx*

				[Network]
				DHCP=yes
				""".lines().toList(), null);

		assertEquals("enx*", n.getName());
		assertEquals(true, n.getDhcp().getValue());

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdStaticIpWithRouteGateway() throws OpenemsNamedException {
		// Addresses are declared in [Network], but the Gateway lives in a separate
		// [Route] block and no Metric is set.
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Address=192.168.100.100/24
				Address=10.4.0.1/24

				[Route]
				Gateway=10.4.0.2
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(false, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		assertEquals("192.168.100.100/24", n.getAddresses().getValue().toArray()[0].toString());
		assertEquals("10.4.0.1/24", n.getAddresses().getValue().toArray()[1].toString());
		var currentRoutes = new HashSet<>(n.getRoutes().getValue());
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteGateway(), "10.4.0.2")));
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteMetric(), null)));

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdStaticIpWithNetworkGateway() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Address=192.168.100.100/24
				Address=10.4.0.1/24
				Gateway=10.4.0.2
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(false, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		assertEquals("192.168.100.100/24", n.getAddresses().getValue().toArray()[0].toString());
		assertEquals("10.4.0.1/24", n.getAddresses().getValue().toArray()[1].toString());
		assertEquals("10.4.0.2", n.getGateway().getValue().getHostAddress());

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdDhcpRouteMetric() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes

				[DHCP]
				RouteMetric=216
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(true, n.getDhcp().getValue());
		assertEquals(216, n.getDhcpRouteMetric().getValue().intValue());
	}

	@Test
	public void testSystemdNetworkdRouteOnlyWithGatewayAndMetric() throws OpenemsNamedException {
		// Files without a [Match] block are accepted by the parser; the resulting
		// NetworkInterface has a null name. The caller (readNetworkdConfig) would
		// normally reject this via TypeUtils.assertNull.
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Network]
				DHCP=no
				DNS=10.0.0.1
				LinkLocalAddressing=yes

				[Route]
				Gateway=10.0.10.10
				Metric=520

				[Address]
				Address=10.4.0.1/16
				""".lines().toList(), null);

		assertNull(n.getName());
		assertEquals(false, n.getDhcp().getValue());
		var currentRoutes = new HashSet<>(n.getRoutes().getValue());
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteGateway(), "10.0.10.10")));
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteMetric(), 520)));

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdMultipleRouteSections() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth1

				[Network]
				DHCP=no
				Address=172.23.21.1/24
				Address=192.168.0.11/24

				[Route]
				Gateway=172.23.21.254
				Destination=172.23.22.0/24
				GatewayOnLink=yes

				[Route]
				Gateway=172.23.21.254
				Destination=172.23.23.0/24
				GatewayOnLink=yes

				[Route]
				Gateway=172.23.21.254
				Destination=172.23.24.0/24
				GatewayOnLink=yes
				""".lines().toList(), null);

		assertEquals(false, n.getDhcp().getValue());
		var currentRoutes = new HashSet<>(n.getRoutes().getValue());
		var destinations = currentRoutes.stream()//
				.map(Routes::getRouteDestination)//
				.filter(Objects::nonNull)//
				.collect(Collectors.toSet());
		assertEquals(//
				Set.of("172.23.23.0/24", "172.23.24.0/24", "172.23.22.0/24"), destinations);

		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteGateway(), "172.23.21.254")));
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.isRouteGatewayOnLink(), true)));

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth1", json).toJson());
	}

	@Test
	public void testSystemdNetworkdGatewayDestinationAndGatewayOnLink() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Address=192.168.100.100/24
				Address=172.23.20.1/24
				Destination=0.0.0.0/0
				GatewayOnLink=yes
				Gateway=172.23.20.254
				""".lines().toList(), null);

		assertEquals(false, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		assertEquals(//
				Set.of(//
						Inet4AddressWithSubnetmask.fromString("", "0.0.0.0/0")),
				n.getDestination().getValue());
		assertEquals(true, n.getGatewayOnLink().getValue());
		assertEquals("172.23.20.254", n.getGateway().getValue().getHostAddress());

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdDhcpRouteMetricEth2() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth2

				[Network]
				DHCP=yes

				[DHCP]
				RouteMetric=1024

				[Address]
				Address=172.25.21.1/24
				""".lines().toList(), null);

		assertEquals(true, n.getDhcp().getValue());
		assertEquals(1024, n.getDhcpRouteMetric().getValue().intValue());

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth2", json).toJson());
	}

	@Test
	public void testSystemdNetworkdIpv4Forwarding() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile(List.of(//
				"[Match]", //
				"Name=eth0", //
				"", //
				"[Network]", //
				"IPv4Forwarding=yes" //
		), null);

		assertEquals("eth0", n.getName());
		assertTrue(n.getIpv4Forwarding().getValue());
	}

	@Test
	public void testSystemdNetworkdIpMasquerade() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile(List.of(//
				"[Match]", //
				"Name=eth0", //
				"", //
				"[Network]", //
				"IPMasquerade=ipv4" //
		), null);

		assertEquals("eth0", n.getName());
		assertEquals(IpMasqueradeSetting.IP_V4, n.getIpMasquerade().getValue());
	}

	@Test
	public void testUpdateFromAppliesChangedFields() throws OpenemsNamedException {
		var n1 = parseSystemdNetworkdConfigurationFile(Lists.newArrayList(//
				"[Match]", //
				"Name=eth0", //
				"", //
				"[Network]", //
				"DHCP=yes", //
				"LinkLocalAddressing=yes", //
				"", //
				"[Address]", //
				"Address=192.168.100.100/24", //
				"Label=normal" //
		), null);

		assertTrue(n1.getDhcp().getValue());

		var n2 = parseSystemdNetworkdConfigurationFile(Lists.newArrayList(//
				"[Match]", //
				"Name=eth0", //
				"", //
				"[Network]", //
				"DHCP=no", //
				"LinkLocalAddressing=yes", //
				"IPv4Forwarding=yes", //
				"IPMasquerade=ipv4", //
				"", //
				"[Address]", //
				"Address=192.168.100.100/24", //
				"Label=normal" //
		), null);

		assertTrue(n1.updateFrom(n2));

		assertFalse(n1.getDhcp().getValue());
		assertTrue(n1.getIpv4Forwarding().getValue());
		assertEquals(IpMasqueradeSetting.IP_V4, n1.getIpMasquerade().getValue());
	}

	private static Set<String> addressStrings(NetworkInterface<?> n) {
		return n.getAddresses().getValue().stream() //
				.map(Inet4AddressWithSubnetmask::toString) //
				.collect(Collectors.toSet());
	}
}
