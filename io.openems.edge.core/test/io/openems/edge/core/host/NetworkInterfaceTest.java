package io.openems.edge.core.host;

import static io.openems.common.utils.JsonUtils.prettyToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.Inet4Address;
import java.net.InetAddress;

import org.junit.Before;
import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;

public class NetworkInterfaceTest {

	private static Inet4AddressWithSubnetmask inet4AddressWithNetmask;

	@Before
	public void beforeEach() throws Exception {
		inet4AddressWithNetmask = Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/24");
	}

	@Test
	public void testGetNetmaskAsString() throws Exception {
		assertEquals("255.0.0.0", Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/8").getSubnetmaskAsString());
		assertEquals("255.127.0.0",
				Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/9").getSubnetmaskAsString());
		assertEquals("255.192.0.0",
				Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/10").getSubnetmaskAsString());
		assertEquals("255.224.0.0",
				Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/11").getSubnetmaskAsString());
		assertEquals("255.240.0.0",
				Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/12").getSubnetmaskAsString());
		assertEquals("255.248.0.0",
				Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/13").getSubnetmaskAsString());
		assertEquals("255.252.0.0",
				Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/14").getSubnetmaskAsString());
		assertEquals("255.254.0.0",
				Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/15").getSubnetmaskAsString());
		assertEquals("255.255.0.0",
				Inet4AddressWithSubnetmask.fromString("", "192.168.178.1/16").getSubnetmaskAsString());
		assertEquals("255.255.255.0", inet4AddressWithNetmask.getSubnetmaskAsString());
	}

	@Test
	public void testGetCidrFromSubnetmask() throws Exception {
		assertEquals(0, //
				Inet4AddressWithSubnetmask.getCidrFromSubnetmask((Inet4Address) InetAddress.getByName("0.0.0.0")));
		assertEquals(1, //
				Inet4AddressWithSubnetmask.getCidrFromSubnetmask((Inet4Address) InetAddress.getByName("128.0.0.0")));
		assertEquals(3, //
				Inet4AddressWithSubnetmask.getCidrFromSubnetmask((Inet4Address) InetAddress.getByName("224.0.0.0")));
		assertEquals(8, //
				Inet4AddressWithSubnetmask.getCidrFromSubnetmask((Inet4Address) InetAddress.getByName("255.0.0.0")));
		assertEquals(24, Inet4AddressWithSubnetmask
				.getCidrFromSubnetmask((Inet4Address) InetAddress.getByName("255.255.255.0")));
		assertEquals(25, Inet4AddressWithSubnetmask
				.getCidrFromSubnetmask((Inet4Address) InetAddress.getByName("255.255.255.128")));
		assertEquals(26, Inet4AddressWithSubnetmask
				.getCidrFromSubnetmask((Inet4Address) InetAddress.getByName("255.255.255.192")));
		assertEquals(27, Inet4AddressWithSubnetmask
				.getCidrFromSubnetmask((Inet4Address) InetAddress.getByName("255.255.255.224")));
	}

	@Test
	public void testIsInSameNetwork() throws Exception {
		assertTrue(
				inet4AddressWithNetmask.isInSameNetwork(Inet4AddressWithSubnetmask.fromString("", "192.168.178.2/24")));
		assertFalse(
				inet4AddressWithNetmask.isInSameNetwork(Inet4AddressWithSubnetmask.fromString("", "192.168.179.2/24")));
	}

	@Test
	public void testJsonRoundtripDhcpWithLabelledAddress() throws OpenemsNamedException {
		assertJsonRoundtrip("eth0", """
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
				}""");
	}

	@Test
	public void testJsonRoundtripStaticWithGatewayAndMetric() throws OpenemsNamedException {
		assertJsonRoundtrip("eth0", """
				{
				  "dhcp": false,
				  "linkLocalAddressing": true,
				  "gateway": "10.4.0.2",
				  "metric": 520,
				  "dns": "8.8.8.8",
				  "addresses": [
				    {
				      "label": "",
				      "address": "192.168.100.100",
				      "subnetmask": "255.255.255.0"
				    }
				  ]
				}""");
	}

	@Test
	public void testJsonRoundtripIsStableForMultipleAddresses() throws OpenemsNamedException {
		// Set iteration order is implementation-defined, so instead of pinning the
		// exact output, we assert that two successive round-trips produce identical
		// JSON. i.e. the serializer is a fixed point.
		var input = JsonUtils.parse("""
				{
				  "dhcp": false,
				  "gateway": "10.4.0.2",
				  "addresses": [
				    { "label": "", "address": "192.168.100.100", "subnetmask": "255.255.255.0" },
				    { "label": "", "address": "10.4.0.1", "subnetmask": "255.255.255.0" }
				  ]
				}""");
		var once = NetworkInterface.from("eth0", input).toJson();
		var twice = NetworkInterface.from("eth0", once).toJson();
		assertEquals(prettyToString(once), prettyToString(twice));
	}

	@Test
	public void testJsonRoundtripWildcardName() throws OpenemsNamedException {
		assertJsonRoundtrip("enx*", """
				{
				  "dhcp": true
				}""");
	}

	private static void assertJsonRoundtrip(String name, String prettyJson) throws OpenemsNamedException {
		var json = JsonUtils.parse(prettyJson);
		var iface = NetworkInterface.from(name, json);
		assertEquals(prettyJson, prettyToString(iface.toJson()));
	}

}
