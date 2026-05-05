package io.openems.edge.core.host;

import static io.openems.common.utils.JsonUtils.prettyToString;
import static io.openems.edge.core.host.OperatingSystemDebianSystemd.parseNetworkManagerConnectionFile;
import static io.openems.edge.core.host.OperatingSystemDebianSystemd.parseSystemdNetworkdConfigurationFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.google.common.collect.Lists;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.core.host.NetworkInterface.IpMasqueradeSetting;

public class OperatingSystemDebianSystemdTest {

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

				[Address]
				Address=192.168.100.100/24

				[Route]
				Gateway=192.168.100.1
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
			assertEquals(216, n.getMetric().getValue().intValue());
		}
	}

	@Test
	public void testStaticWithDnsAndMetric() throws OpenemsNamedException {
		var networkd = parseSystemdNetworkdConfigurationFile("""
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
			assertEquals(520, n.getMetric().getValue().intValue());
			assertEquals(Set.of("10.4.0.1/16"), addressStrings(n));
		}
	}

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
	}

	@Test
	public void testSystemdNetworkdMultipleAddressesWithRouteBlockGateway() throws OpenemsNamedException {
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
		assertFalse(n.getDhcp().getValue());
		assertEquals(Set.of("192.168.100.100/24", "10.4.0.1/24"), addressStrings(n));
		assertEquals("10.4.0.2", n.getGateway().getValue().getHostAddress());
		assertNull(n.getMetric().getValue());
	}

	@Test
	public void testSystemdNetworkdWithoutMatchSectionHasNoName() throws OpenemsNamedException {
		// Files without a [Match] block are accepted by the parser; the resulting
		// NetworkInterface has a null name. The caller (readNetworkdConfig) would
		// normally reject this via TypeUtils.assertNull.
		// This test pins the raw parser behaviour.
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Network]
				DHCP=no
				DNS=10.0.0.1

				[Route]
				Gateway=10.0.10.10
				Metric=520

				[Address]
				Address=10.4.0.1/16
				""".lines().toList(), null);

		assertNull(n.getName());
		assertFalse(n.getDhcp().getValue());
		assertEquals(520, n.getMetric().getValue().intValue());
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
		assertTrue(n.getDhcp().getValue());
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
