package io.openems.edge.core.host;

import static io.openems.common.jsonrpc.serialization.JsonSerializerUtil.jsonObjectSerializer;
import static io.openems.common.utils.FunctionUtils.doNothing;
import static io.openems.common.utils.InetAddressUtils.parseOrNull;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.function.ThrowingConsumer;
import io.openems.common.jsonrpc.serialization.JsonSerializer;
import io.openems.common.types.ConfigurationProperty;
import io.openems.common.utils.InetAddressUtils;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.common.update.Updateable;
import io.openems.edge.common.user.User;
import io.openems.edge.core.host.Bash.Command;
import io.openems.edge.core.host.NetworkInterface.IpMasqueradeSetting;
import io.openems.edge.core.host.jsonrpc.ExecuteSystemCommandRequest;
import io.openems.edge.core.host.jsonrpc.ExecuteSystemCommandRequest.SystemCommand;
import io.openems.edge.core.host.jsonrpc.ExecuteSystemCommandResponse;
import io.openems.edge.core.host.jsonrpc.ExecuteSystemCommandResponse.SystemCommandResponse;
import io.openems.edge.core.host.jsonrpc.ExecuteSystemRestartRequest;
import io.openems.edge.core.host.jsonrpc.ExecuteSystemRestartResponse;
import io.openems.edge.core.host.jsonrpc.GetNetworkInfo;
import io.openems.edge.core.host.jsonrpc.GetNetworkInfo.NetworkInfoAddress;
import io.openems.edge.core.host.jsonrpc.GetNetworkInfo.NetworkInfoWrapper;
import io.openems.edge.core.host.jsonrpc.GetNetworkInfo.Route;
import io.openems.edge.core.host.jsonrpc.SetNetworkConfig;

/**
 * OperatingSystem implementation for Debian with systemd.
 */
public class OperatingSystemDebianSystemd implements OperatingSystem {

	private static final String NETWORK_BASE_PATH = "/etc/systemd/network";
	private static final String NETWORK_MANAGER_BASE_PATH = "/etc/NetworkManager/system-connections";
	private static final String NETWORK_MANAGER_RUN_PATH = "/run/NetworkManager/system-connections";
	private static final Path UDEV_PATH = Paths.get("/etc/udev/rules.d/99-usb-serial.rules");
	private static final int DEFAULT_METRIC = 1024;
	private static final String MATCH_SECTION = "[Match]";
	private static final String NETWORK_SECTION = "[Network]";
	private static final String ROUTE_SECTION = "[Route]";
	private static final String DHCP_SECTION = "[DHCP]";
	private static final String ADDRESS_SECTION = "[Address]";
	private static final String CONNECTION_SECTION = "[connection]";
	private static final String IPV4_SECTION = "[ipv4]";
	private static final String IPV6_SECTION = "[ipv6]";
	private static final String EMPTY_SECTION = "";

	/**
	 * Identifies the network stack that manages the interfaces on this host.
	 * Detected once at construction via {@code systemctl is-active}.
	 */
	private static enum NetworkService {
		UNDEFINED, SYSTEMD_NETWORKD, NETWORK_MANAGER
	}

	private static enum NetworkdBlock {
		UNDEFINED, MATCH, NETWORK, ADDRESS, ROUTE, DHCP
	}

	private static enum NetworkManagerBlock {
		UNDEFINED, CONNECTION, IPV4
	}

	private final NetworkService network;

	protected OperatingSystemDebianSystemd() {
		this.network = detectNetwork();
	}

	@Override
	public NetworkConfiguration getNetworkConfiguration() throws OpenemsNamedException {
		return switch (this.network) {
		case NETWORK_MANAGER -> this.readNetworkManagerConfig();
		case SYSTEMD_NETWORKD -> this.readNetworkdConfig();
		case UNDEFINED -> throw new OpenemsException("Network daemon undefined");
		};
	}

	private NetworkConfiguration readNetworkdConfig() throws OpenemsNamedException {
		var interfaces = new TreeMap<String, NetworkInterface<?>>();

		for (final File file : this.readNetworkConfigFiles(NETWORK_BASE_PATH)) {
			/*
			 * Read all systemd network configuration files
			 */
			if (file.isDirectory() || !file.getName().endsWith(".network")) {
				continue;
			}
			try {
				/*
				 * Parse the content of the network configuration file
				 */
				var lines = this.handleExecuteSystemReadFile(file.toPath());
				NetworkInterface<File> networkInterface = parseSystemdNetworkdConfigurationFile(lines, file);

				// check for null value
				TypeUtils.assertNull("Network interface Name", networkInterface.getName());

				// add to result
				interfaces.put(networkInterface.getName(), networkInterface);

			} catch (IllegalArgumentException e) {
				throw new OpenemsException("Unable to read file [" + file + "]: " + e.getMessage());
			}
		}

		return new NetworkConfiguration(interfaces);
	}

	private NetworkConfiguration readNetworkManagerConfig() throws OpenemsNamedException {
		var interfaces = new TreeMap<String, NetworkInterface<?>>();

		var files = new ArrayList<File>();
		files.addAll(this.readNetworkConfigFiles(NETWORK_MANAGER_RUN_PATH));
		files.addAll(this.readNetworkConfigFiles(NETWORK_MANAGER_BASE_PATH));
		for (final File file : files) {
			if (file.isDirectory() || !file.getName().endsWith(".nmconnection")) {
				continue;
			}
			try {
				var lines = this.handleExecuteSystemReadFile(file.toPath());
				NetworkInterface<File> networkInterface = parseNetworkManagerConnectionFile(lines, file);
				if (networkInterface == null) {
					// unsupported connection type (e.g. wifi, bridge) — skip silently
					continue;
				}
				interfaces.put(networkInterface.getName(), networkInterface);
			} catch (IllegalArgumentException e) {
				throw new OpenemsException("Unable to read file [" + file + "]: " + e.getMessage());
			}
		}
		return new NetworkConfiguration(interfaces);
	}

	private List<File> readNetworkConfigFiles(String path) throws OpenemsException {
		var filePath = Paths.get(path).toFile();
		if (!filePath.exists()) {
			throw new OpenemsException("Base-Path [" + filePath + "] does not exist.");
		}
		var files = filePath.listFiles();
		if (files == null) {
			return Collections.emptyList();
		}
		return Arrays.asList(files);
	}

	@Override
	public void handleSetNetworkConfigRequest(User user, NetworkConfiguration oldNetworkConfiguration,
			SetNetworkConfig.Request request) throws OpenemsNamedException {
		// apply request onto old configuration (shared between both backends)
		var isChanged = false;
		for (NetworkInterface<?> networkInterface : request.networkInterfaces()) {
			NetworkInterface<?> iface = oldNetworkConfiguration.getInterfaces().get(networkInterface.getName());
			if (iface == null) {
				throw new OpenemsException("No network interface with name [" + networkInterface.getName() + "]");
			}
			if (iface.updateFrom(networkInterface)) {
				isChanged = true;
			}
		}

		// stop early if there are no changes
		if (!isChanged) {
			throw new OpenemsException("Received no changes to network configuration");
		}

		// write configuration files
		switch (this.network) {
		case NETWORK_MANAGER -> this.writeNetworkManagerConfig(user, oldNetworkConfiguration, request);
		case SYSTEMD_NETWORKD -> this.writeNetworkdConfig(user, oldNetworkConfiguration, request);
		case UNDEFINED -> throw new OpenemsException("Network daemon undefined");
		}
	}

	private void writeNetworkdConfig(User user, NetworkConfiguration oldNetworkConfiguration,
			SetNetworkConfig.Request request) throws OpenemsNamedException {
		for (Entry<String, NetworkInterface<?>> entry : oldNetworkConfiguration.getInterfaces().entrySet()) {
			if (request.networkInterfaces().stream().noneMatch(i -> i.getName().equals(entry.getKey()))) {
				continue;
			}
			NetworkInterface<?> iface = entry.getValue();
			var file = (File) iface.getAttachment();
			var lines = this.toNetworkdFileFormat(user, iface);
			this.handleExecuteSystemWriteFile(file.toPath(), lines);
		}

		// apply the configuration by restarting the systemd-networkd service
		this.handleExecuteSystemCommandRequest(ExecuteSystemCommandRequest
				.withRootPrivileges("systemctl restart systemd-networkd --no-block", true, 0));
	}

	private void writeNetworkManagerConfig(User user, NetworkConfiguration oldNetworkConfiguration,
			SetNetworkConfig.Request request) throws OpenemsNamedException {
		var touchedInterfaces = new ArrayList<String>();
		for (Entry<String, NetworkInterface<?>> entry : oldNetworkConfiguration.getInterfaces().entrySet()) {
			if (request.networkInterfaces().stream().noneMatch(i -> i.getName().equals(entry.getKey()))) {
				continue;
			}
			NetworkInterface<?> iface = entry.getValue();
			var file = (File) iface.getAttachment();
			if (file == null) {
				// no existing .nmconnection file — skip (creation is out of scope)
				continue;
			}
			var lines = toNetworkManagerFileFormat(user, iface);
			this.handleExecuteSystemWriteFile(file.toPath(), lines);
			// NetworkManager refuses to load connections unless permissions are 0600
			this.handleExecuteSystemCommandRequest(
					ExecuteSystemCommandRequest.withRootPrivileges("chmod 600 " + file.toPath(), false, 5));
			touchedInterfaces.add(iface.getName());
		}

		// reload connection files and bring interfaces back up
		this.handleExecuteSystemCommandRequest(
				ExecuteSystemCommandRequest.withRootPrivileges("nmcli connection reload", true, 0));
		for (var name : touchedInterfaces) {
			this.handleExecuteSystemCommandRequest(
					ExecuteSystemCommandRequest.withRootPrivileges("nmcli device reapply " + name, true, 0));
		}
	}

	/**
	 * Helper function to match a String in the configuration file.
	 *
	 * @param pattern  the regular expression pattern
	 * @param line     the line of the file
	 * @param callback the callback that should get called
	 * @throws OpenemsNamedException on error
	 */
	private static void onMatchString(Pattern pattern, String line,
			ThrowingConsumer<String, OpenemsNamedException> callback) throws OpenemsNamedException {
		var matcher = pattern.matcher(line);
		if (matcher.find() && matcher.groupCount() > 0) {
			callback.accept(matcher.group(1));
		}
	}

	/**
	 * Helper function to match an Inet4Address in the configuration file.
	 *
	 * @param pattern  the regular expression pattern
	 * @param line     the line of the file
	 * @param callback the callback that should get called
	 * @throws OpenemsNamedException on error
	 */
	private static void onMatchInet4Address(Pattern pattern, String line,
			ThrowingConsumer<Inet4Address, OpenemsNamedException> callback) throws OpenemsNamedException {
		onMatchString(pattern, line, property -> callback.accept(InetAddressUtils.parseOrError(property)));
	}

	/**
	 * Converts the NetworkInterface object to systemd-networkd file format.
	 *
	 * @param user  the User
	 * @param iface the input network interface configuration
	 * @return a list of strings for writing it to a file
	 */
	private List<String> toNetworkdFileFormat(User user, NetworkInterface<?> iface) {
		List<String> result = new ArrayList<>();
		result.add("# changedBy: " //
				+ user.getName());
		result.add("# changedAt: " //
				+ LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString());

		// Match Section
		result.add(MATCH_SECTION);
		result.add("Name=" + iface.getName());
		result.add(EMPTY_SECTION);

		// Network Section
		result.add(NETWORK_SECTION);
		if (iface.getDhcp().isSetAndNotNull()) {
			result.add("DHCP=" + (iface.getDhcp().getValue() ? "yes" : "no"));
		}
		if (iface.getDns().isSetAndNotNull()) {
			result.add("DNS=" + iface.getDns().getValue().getHostAddress());
		}
		if (iface.getLinkLocalAddressing().isSetAndNotNull()) {
			result.add("LinkLocalAddressing=" + (iface.getLinkLocalAddressing().getValue() ? "yes" : "no"));
		}
		if (iface.getIpv4Forwarding().isSetAndNotNull()) {
			result.add("IPv4Forwarding=" + (iface.getIpv4Forwarding().getValue() ? "yes" : "no"));
		}
		if (iface.getIpMasquerade().isSetAndNotNull()) {
			result.add("IPMasquerade=" + iface.getIpMasquerade().getValue().settingValue);
		}

		var metric = DEFAULT_METRIC;
		if (iface.getMetric().isSetAndNotNull()) {
			metric = iface.getMetric().getValue().intValue();
		}

		if (iface.getDhcp().isSetAndNotNull()) {
			final boolean dhcp = iface.getDhcp().getValue();
			result.add(EMPTY_SECTION);
			if (dhcp) { // dhcp == yes
				result.add(DHCP_SECTION);
				result.add("RouteMetric=" + metric);
			} else {
				result.add(ROUTE_SECTION);
				if (iface.getGateway().isSetAndNotNull()) {
					result.add("Gateway=" + iface.getGateway().getValue().getHostAddress());
				}
				result.add("Metric=" + metric);
			}
		}
		if (iface.getAddresses().isSetAndNotNull()) {
			for (var address : iface.getAddresses().getValue()) {
				final var label = address.getLabel();
				result.add(EMPTY_SECTION);
				result.add(ADDRESS_SECTION);
				result.add("Address=" + address.toString());
				if (!label.isBlank()) {
					result.add("Label=" + label);
				}
			}
		}
		return result;
	}

	/**
	 * Converts a {@link NetworkInterface} to a NetworkManager keyfile
	 * (.nmconnection) body.
	 *
	 * @param user  the User
	 * @param iface the network interface configuration
	 * @return the file contents as a list of lines
	 */
	private static List<String> toNetworkManagerFileFormat(User user, NetworkInterface<?> iface) {
		List<String> result = new ArrayList<>();
		result.add("# changedBy: " //
				+ user.getName());
		result.add("# changedAt: " //
				+ LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString());

		result.add(CONNECTION_SECTION);
		result.add("id=" + iface.getName());
		result.add("type=ethernet");
		result.add("interface-name=" + iface.getName());
		result.add(EMPTY_SECTION);

		result.add(IPV4_SECTION);
		final String method;
		if (iface.getDhcp().isSetAndNotNull() && iface.getDhcp().getValue()) {
			method = "auto";
		} else if (iface.getLinkLocalAddressing().isSetAndNotNull() //
				&& iface.getLinkLocalAddressing().getValue() //
				&& (!iface.getAddresses().isSetAndNotNull() || iface.getAddresses().getValue().isEmpty())) {
			method = "link-local";
		} else {
			method = "manual";
		}
		result.add("method=" + method);
		if (!method.equals("auto") && iface.getAddresses().isSetAndNotNull()) {
			var idx = 1;
			for (var address : iface.getAddresses().getValue()) {
				result.add("address" + idx + "=" + address.toString());
				idx++;
			}
		}
		if (iface.getGateway().isSetAndNotNull()) {
			result.add("gateway=" + iface.getGateway().getValue().getHostAddress());
		}
		if (iface.getDns().isSetAndNotNull()) {
			result.add("dns=" + iface.getDns().getValue().getHostAddress() + ";");
		}
		if (iface.getMetric().isSetAndNotNull()) {
			result.add("route-metric=" + iface.getMetric().getValue());
		}
		result.add(EMPTY_SECTION);

		result.add(IPV6_SECTION);
		result.add("method=auto");

		return result;
	}

	/**
	 * Reads a file via a privileged {@code cat} command.
	 *
	 * @param target the file path to read
	 * @return the file lines
	 * @throws OpenemsNamedException on error
	 */
	private List<String> handleExecuteSystemReadFile(Path target) throws OpenemsNamedException {
		var request = ExecuteSystemCommandRequest
				.withRootPrivileges("cat " + target.toString().replaceAll(" ", "\\\\ "), false, 5);
		try {
			var response = this.handleExecuteSystemCommandRequest(request).get();
			return response.scr.stdout();
		} catch (InterruptedException | ExecutionException e) {
			throw new OpenemsException("Unable to read file [" + target + "]: " + e.getMessage());
		}
	}

	/**
	 * Writes lines to a file via a privileged {@code cp} from a temp file.
	 *
	 * @param target the destination file path
	 * @param lines  the content to write
	 * @throws OpenemsNamedException on error
	 */
	private void handleExecuteSystemWriteFile(Path target, List<String> lines) throws OpenemsNamedException {
		try {
			var tmp = Files.createTempFile("openems-", ".tmp");
			try {
				Files.write(tmp, lines, StandardCharsets.UTF_8);
				var request = ExecuteSystemCommandRequest
						.withRootPrivileges("cp " + tmp + " " + target.toString().replaceAll(" ", "\\\\ "), false, 5);
				this.handleExecuteSystemCommandRequest(request).get();
			} finally {
				Files.deleteIfExists(tmp);
			}
		} catch (IOException | InterruptedException | ExecutionException e) {
			throw new OpenemsException("Unable to write file [" + target + "]: " + e.getMessage());
		}
	}

	@Override
	public CompletableFuture<ExecuteSystemCommandResponse> handleExecuteSystemCommandRequest(
			ExecuteSystemCommandRequest request) {
		return execute(request.systemCommand).thenApply(cmd -> { //
			final var scr = new SystemCommandResponse(cmd.stdout(), cmd.stderr(), cmd.exitCode());
			return new ExecuteSystemCommandResponse(request.id, scr);
		});
	}

	@Override
	public CompletableFuture<ExecuteSystemRestartResponse> handleExecuteSystemRestartRequest(
			ExecuteSystemRestartRequest request) {
		var sc = new SystemCommand(//
				switch (request.type) { // actual command string
				case HARD -> "/usr/bin/systemctl reboot -i"; // "-i" is for "ignore inhibitors and users"
				case SOFT -> "/usr/bin/systemctl restart openems";
				}, //
				false, // runInBackground
				true, // requireRootPrivileges
				5); // timeoutSeconds
		return execute(sc).thenApply(cmd -> { //
			final var scr = new SystemCommandResponse(cmd.stdout(), cmd.stderr(), cmd.exitCode());
			return new ExecuteSystemRestartResponse(request.id, scr);
		});
	}

	private static CompletableFuture<Command> execute(SystemCommand sc) {
		return new Bash(sc.command()) //
				.withTimeout(sc.timeoutSeconds()) //
				.withSudo(sc.username().orElse(null), sc.password().orElse(null), sc.requireRootPrivileges()) //
				.runInBackground(sc.runInBackground()) //
				.execute();
	}

	@Override
	public String getUsbConfiguration() throws OpenemsNamedException {
		try {
			if (!Files.exists(UDEV_PATH)) {
				return "";
			}
			var lines = Files.readAllLines(UDEV_PATH, StandardCharsets.UTF_8);
			return String.join("\n", lines);
		} catch (IOException e) {
			throw new OpenemsException("Unable to read file [" + UDEV_PATH + "]: " + e.getMessage());
		}
	}

	private static final Pattern MATCH_NAME = Pattern //
			.compile("^Name=([a-zA-Z0-9*]+)$");
	private static final Pattern ADDRESS_LABEL = Pattern //
			.compile("^Label=([a-zA-Z*]+)$");
	private static final Pattern NETWORK_ADDRESS = Pattern //
			.compile("^Address=(" + NetworkConfiguration.PATTERN_INET4ADDRESS + "/\\d+)$");
	private static final Pattern NETWORK_DHCP = Pattern //
			.compile("^DHCP=(\\w+)$");
	private static final Pattern NETWORK_LINK_LOCAL_ADDRESSING = Pattern //
			.compile("^LinkLocalAddressing=(\\w+)$");
	private static final Pattern NETWORK_GATEWAY = Pattern //
			.compile("^Gateway=(" + NetworkConfiguration.PATTERN_INET4ADDRESS + ")$");
	private static final Pattern NETWORK_DNS = Pattern //
			.compile("^DNS=(" + NetworkConfiguration.PATTERN_INET4ADDRESS + ")$");
	private static final Pattern NETWORK_IPV4_FORWARDING = Pattern //
			.compile("^IPv4Forwarding=(\\w+)$");
	private static final Pattern NETWORK_IP_MASQUERADE = Pattern //
			.compile("^IPMasquerade=(\\w+)$");
	private static final Pattern GATEWAY_METRIC = Pattern //
			.compile("^Metric=([\\d]+)$");
	private static final Pattern ROUTE_METRIC = Pattern //
			.compile("^RouteMetric=([\\d]+)$");
	private static final Pattern NETWORK_MANAGER_SECTION = Pattern //
			.compile("^\\[([a-zA-Z0-9_-]+)\\]$");
	private static final Pattern NETWORK_MANAGER_ADDRESS_N = Pattern //
			.compile("^address\\d+$");

	/**
	 * Parses a Systemd-Networkd configuration file.
	 *
	 * <p>
	 * See <a href=
	 * "https://man7.org/linux/man-pages/man5/systemd.network.5.html">systemd.network.5</a>
	 * man page
	 *
	 * @param <A>        the type of the attachment
	 * @param lines      the lines to parse
	 * @param attachment to be added as an attachment to the
	 *                   {@link NetworkInterface}
	 * @return a {@link NetworkInterface}
	 * @throws OpenemsNamedException on error
	 */
	protected static <A> NetworkInterface<A> parseSystemdNetworkdConfigurationFile(List<String> lines, A attachment)
			throws OpenemsNamedException {
		var currentBlock = NetworkdBlock.UNDEFINED;
		final var name = new AtomicReference<String>();
		final var dhcp = new AtomicReference<ConfigurationProperty<Boolean>>(//
				ConfigurationProperty.asNotSet());
		final var linkLocalAddressing = new AtomicReference<ConfigurationProperty<Boolean>>(//
				ConfigurationProperty.asNotSet());
		final var gateway = new AtomicReference<ConfigurationProperty<Inet4Address>>(//
				ConfigurationProperty.asNotSet());
		final var metric = new AtomicReference<ConfigurationProperty<Integer>>(//
				ConfigurationProperty.asNotSet());
		final var dns = new AtomicReference<ConfigurationProperty<Inet4Address>>(//
				ConfigurationProperty.asNotSet());
		final var addresses = new AtomicReference<ConfigurationProperty<Set<Inet4AddressWithSubnetmask>>>(//
				ConfigurationProperty.asNotSet());
		final var ipv4Forwarding = new AtomicReference<ConfigurationProperty<Boolean>>(//
				ConfigurationProperty.asNotSet());
		final var ipMasquerade = new AtomicReference<ConfigurationProperty<IpMasqueradeSetting>>(//
				ConfigurationProperty.asNotSet());

		// holds the latest found address
		final var tmpAddress = new AtomicReference<Inet4AddressWithSubnetmask>();

		for (var line : lines) {
			line = line.trim();
			if (line.isBlank()) {
				continue;
			}

			/*
			 * Find current configuration block
			 */
			if (line.startsWith("[")) {
				currentBlock = switch (line) {
				case MATCH_SECTION //
					-> NetworkdBlock.MATCH;
				case NETWORK_SECTION //
					-> NetworkdBlock.NETWORK;
				case ADDRESS_SECTION -> {
					tmpAddress.set(null);
					yield NetworkdBlock.ADDRESS;
				}
				case ROUTE_SECTION //
					-> NetworkdBlock.ROUTE;
				case DHCP_SECTION //
					-> NetworkdBlock.DHCP;
				default //
					-> NetworkdBlock.UNDEFINED;
				};
				continue;
			}

			/*
			 * Parse Block
			 */
			switch (currentBlock) {
			case MATCH -> onMatchString(MATCH_NAME, line, name::set);
			case NETWORK -> {
				onMatchString(NETWORK_DHCP, line, property -> {
					dhcp.set(ConfigurationProperty.of(property.equalsIgnoreCase("yes")));
				});
				onMatchString(NETWORK_LINK_LOCAL_ADDRESSING, line, property -> {
					linkLocalAddressing.set(ConfigurationProperty.of(property.equalsIgnoreCase("yes")));
				});
				onMatchInet4Address(NETWORK_GATEWAY, line, property -> {
					gateway.set(ConfigurationProperty.of(property));
				});
				onMatchInet4Address(NETWORK_DNS, line, property -> {
					dns.set(ConfigurationProperty.of(property));
				});
				onMatchString(NETWORK_ADDRESS, line, property -> {
					var addressDetails = addresses.get().getValue();
					if (addressDetails == null) {
						addressDetails = new HashSet<>();
					}
					addressDetails.add(Inet4AddressWithSubnetmask.fromString("" /* empty default label */, property));
					addresses.set(ConfigurationProperty.of(addressDetails));
				});
				onMatchString(NETWORK_IPV4_FORWARDING, line, property -> {
					ipv4Forwarding.set(ConfigurationProperty.of(property.equalsIgnoreCase("yes")));
				});
				onMatchString(NETWORK_IP_MASQUERADE, line, property -> {
					ipMasquerade.set(ConfigurationProperty.of(IpMasqueradeSetting.findBySettingValue(property)));
				});
			}
			case ADDRESS -> {
				onMatchString(NETWORK_ADDRESS, line, property -> {
					// Storing here temporarily so that we can use it if when we find label.
					var address = Inet4AddressWithSubnetmask.fromString("" /* empty default label */, property);
					tmpAddress.set(address);

					var addressDetails = addresses.get().getValue();
					if (addressDetails == null) {
						addressDetails = new HashSet<>();
					}
					// Add it with empty label now, later replace with label if we find one.
					addressDetails.add(address);
					addresses.set(ConfigurationProperty.of(addressDetails));
				});
				onMatchString(ADDRESS_LABEL, line, property -> {
					// IP address contains Only static labels or with no labels.
					var addressDetails = addresses.get().getValue();
					var address = tmpAddress.get();
					if (addressDetails == null || address == null) {
						// ignore label
						return;
					}

					// Replace the value with static or any other label in future.
					addressDetails.remove(address);
					address = new Inet4AddressWithSubnetmask(property, address.getInet4Address(),
							address.getSubnetmaskAsCidr());
					addressDetails.add(address);
				});
			}
			case ROUTE -> {
				onMatchInet4Address(NETWORK_GATEWAY, line, property -> {
					gateway.set(ConfigurationProperty.of(property));
				});
				onMatchString(GATEWAY_METRIC, line, property -> {
					metric.set(ConfigurationProperty.of(Integer.parseInt(property)));
				});
			}
			case DHCP -> {
				onMatchString(ROUTE_METRIC, line, property -> {
					metric.set(ConfigurationProperty.of(Integer.parseInt(property)));
				});
			}
			case UNDEFINED -> doNothing();
			}
		}
		return new NetworkInterface<>(name.get(), //
				dhcp.get(), linkLocalAddressing.get(), gateway.get(), dns.get(), addresses.get(), metric.get(),
				ipv4Forwarding.get(), ipMasquerade.get(), attachment);
	}

	/**
	 * Parses a NetworkManager keyfile (.nmconnection).
	 *
	 * <p>
	 * Only {@code type=ethernet} connections are returned; all others yield
	 * {@code null}.
	 *
	 * @param <A>        the type of the attachment
	 * @param lines      the lines to parse
	 * @param attachment to be added as an attachment to the
	 *                   {@link NetworkInterface}
	 * @return a {@link NetworkInterface}, or {@code null} for non-ethernet
	 *         connections
	 * @throws OpenemsNamedException on error
	 */
	protected static <A> NetworkInterface<A> parseNetworkManagerConnectionFile(List<String> lines, A attachment)
			throws OpenemsNamedException {
		var currentBlock = NetworkManagerBlock.UNDEFINED;
		String id = null;
		String interfaceName = null;
		String connectionType = null;

		ConfigurationProperty<Boolean> dhcp = ConfigurationProperty.asNotSet();
		ConfigurationProperty<Boolean> linkLocalAddressing = ConfigurationProperty.asNotSet();
		ConfigurationProperty<Inet4Address> gateway = ConfigurationProperty.asNotSet();
		ConfigurationProperty<Inet4Address> dns = ConfigurationProperty.asNotSet();
		ConfigurationProperty<Integer> metric = ConfigurationProperty.asNotSet();
		Set<Inet4AddressWithSubnetmask> addresses = null;

		for (var raw : lines) {
			var line = raw.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
				continue;
			}
			var sectionMatcher = NETWORK_MANAGER_SECTION.matcher(line);
			if (sectionMatcher.matches()) {
				currentBlock = switch (sectionMatcher.group(1).toLowerCase()) {
				case "connection" -> NetworkManagerBlock.CONNECTION;
				case "ipv4" -> NetworkManagerBlock.IPV4;
				default -> NetworkManagerBlock.UNDEFINED;
				};
				continue;
			}
			var eq = line.indexOf('=');
			if (eq < 0) {
				continue;
			}
			var key = line.substring(0, eq).trim().toLowerCase();
			var value = line.substring(eq + 1).trim();

			switch (currentBlock) {
			case CONNECTION -> {
				switch (key) {
				case "id" -> id = value;
				case "interface-name" -> interfaceName = value;
				case "type" -> connectionType = value.toLowerCase();
				default -> doNothing();
				}
			}
			case IPV4 -> {
				if (NETWORK_MANAGER_ADDRESS_N.matcher(key).matches()) {
					// format: <ip>/<prefix>[,<gateway>]
					var parts = value.split(",");
					if (parts.length >= 1 && !parts[0].isBlank()) {
						if (addresses == null) {
							addresses = new HashSet<>();
						}
						addresses.add(Inet4AddressWithSubnetmask.fromString("", parts[0].trim()));
					}
					if (parts.length >= 2 && !parts[1].isBlank() && !gateway.isSet()) {
						var parsed = InetAddressUtils.parseOrNull(parts[1].trim());
						if (parsed != null) {
							gateway = ConfigurationProperty.of(parsed);
						}
					}
				} else {
					switch (key) {
					case "method" -> {
						switch (value.toLowerCase()) {
						case "auto" -> {
							dhcp = ConfigurationProperty.of(true);
							linkLocalAddressing = ConfigurationProperty.of(true);
						}
						case "manual" -> dhcp = ConfigurationProperty.of(false);
						case "link-local" -> {
							dhcp = ConfigurationProperty.of(false);
							linkLocalAddressing = ConfigurationProperty.of(true);
						}
						case "disabled" -> {
							dhcp = ConfigurationProperty.of(false);
							linkLocalAddressing = ConfigurationProperty.of(false);
						}
						default -> doNothing();
						}
					}
					case "gateway" -> {
						var parsed = InetAddressUtils.parseOrNull(value);
						if (parsed != null) {
							gateway = ConfigurationProperty.of(parsed);
						}
					}
					case "dns" -> {
						// semicolon-separated; take first entry
						var first = value.split(";", 2)[0].trim();
						if (!first.isEmpty()) {
							var parsed = InetAddressUtils.parseOrNull(first);
							if (parsed != null) {
								dns = ConfigurationProperty.of(parsed);
							}
						}
					}
					case "route-metric" -> {
						try {
							metric = ConfigurationProperty.of(Integer.parseInt(value));
						} catch (NumberFormatException e) {
							/* ignore malformed metric */
						}
					}
					default -> doNothing();
					}
				}
			}
			case UNDEFINED -> doNothing();
			}
		}

		if (connectionType != null && !connectionType.equals("ethernet")) {
			return null;
		}

		final var name = interfaceName != null ? interfaceName : id;
		if (name == null) {
			throw new OpenemsException("NetworkManager connection file has neither interface-name nor id");
		}

		return new NetworkInterface<>(name, //
				dhcp, linkLocalAddressing, gateway, dns, //
				addresses != null ? ConfigurationProperty.of(addresses) : ConfigurationProperty.asNotSet(), //
				metric, //
				ConfigurationProperty.asNotSet(), // ipv4Forwarding — not represented in NM keyfile
				ConfigurationProperty.asNotSet(), // ipMasquerade — not represented in NM keyfile
				attachment);
	}

	@Override
	public List<Inet4Address> getSystemIPs() throws OpenemsNamedException {
		var reqIpShow = ExecuteSystemCommandRequest.withoutAuthentication("ip -j -4 address show", false, 5);
		try {
			var resultIpShow = this.handleExecuteSystemCommandRequest(reqIpShow).get().getResult().toString();
			return parseShowJson(resultIpShow).stream() //
					.flatMap(t -> t.ips().stream() //
							.map(d -> d.ip().getInet4Address())) //
					.toList();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Collections.emptyList();
		} catch (ExecutionException e) {
			return Collections.emptyList();
		}

	}

	@Override
	public GetNetworkInfo.Response getNetworkInfo() throws OpenemsNamedException {
		var reqIpShow = ExecuteSystemCommandRequest.withoutAuthentication("ip -j -4 address show", false, 5);
		var reqIpRoute = ExecuteSystemCommandRequest.withoutAuthentication("ip -j route", false, 5);
		try {
			var resultIpShow = this.handleExecuteSystemCommandRequest(reqIpShow).get().getResult().toString();
			var resultIpRoute = this.handleExecuteSystemCommandRequest(reqIpRoute).get().getResult().toString();
			return new GetNetworkInfo.Response(parseShowJson(resultIpShow), parseRouteJson(resultIpRoute));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new GetNetworkInfo.Response(Collections.emptyList(), Collections.emptyList());
		} catch (ExecutionException e) {
			return new GetNetworkInfo.Response(Collections.emptyList(), Collections.emptyList());
		}

	}

	/**
	 * Detects the active network service by querying systemd.
	 *
	 * <p>
	 * systemd-networkd is checked first and used as default; NetworkManager is the
	 * fallback.
	 *
	 * @return the detected {@link NetworkService}
	 */
	private static NetworkService detectNetwork() {
		if (isNetworkServiceActive("systemd-networkd")) {
			return NetworkService.SYSTEMD_NETWORKD;
		}
		if (isNetworkServiceActive("NetworkManager")) {
			return NetworkService.NETWORK_MANAGER;
		}
		return NetworkService.SYSTEMD_NETWORKD;
	}

	private static boolean isNetworkServiceActive(String unit) {
		try {
			var proc = new ProcessBuilder("systemctl", "is-active", "--quiet", unit) //
					.redirectErrorStream(true) //
					.start();
			if (!proc.waitFor(2, TimeUnit.SECONDS)) {
				proc.destroyForcibly();
				return false;
			}
			return proc.exitValue() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	protected static List<JsonObject> parseIpJson(String json) throws OpenemsNamedException {
		final var stdout = JsonUtils.getAsJsonArray(JsonUtils.getAsJsonObject(JsonUtils.parse(json)), "stdout");
		if (stdout.isEmpty()) {
			return Collections.emptyList();
		}
		final var networkData = JsonUtils.getAsString(stdout.get(0));
		final var networkDataJson = JsonUtils.parseOptional(networkData);
		if (networkDataJson.isPresent() && networkDataJson.get().isJsonArray()) {
			final var networkInterfaces = JsonUtils.getAsJsonArray(JsonUtils.parse(networkData));
			if (networkData.startsWith("[")) {
				return JsonUtils.stream(networkInterfaces)//
						.map(JsonElement::getAsJsonObject)//
						.toList();
			}
		}

		return Collections.emptyList();
	}

	protected static List<Route> parseRouteJson(String routeJson) throws OpenemsNamedException {
		final var networkData = parseIpJson(routeJson);
		if (networkData == null) {
			return Collections.emptyList();
		}
		return networkData.stream() //
				.map(t -> Route.serializer().deserialize(t)) //
				.toList();
	}

	/**
	 * Parses the json returned by ip address get command.
	 *
	 * @param resultIpShow the json to be parsed
	 * @return a list of parsed ips
	 * @throws OpenemsNamedException on error
	 */
	protected static List<NetworkInfoWrapper> parseShowJson(String resultIpShow) throws OpenemsNamedException {
		final var networkInterfaces = parseIpJson(resultIpShow);
		if (networkInterfaces == null) {
			return Collections.emptyList();
		}

		return networkInterfaces.stream() //
				.map(SystemdInterface.serializer()::deserialize) //
				.map(t -> new NetworkInfoWrapper(t.ifname(), t.addressInfos.stream() //
						.map(address -> new NetworkInfoAddress(//
								new Inet4AddressWithSubnetmask(address.label(), parseOrNull(address.local()),
										address.prefixlen),
								address.dynamic)) //
						.toList())) //
				.toList();
	}

	private record SystemdInterface(String ifname, List<SystemdAddressInfo> addressInfos) {

		/**
		 * Returns a {@link JsonSerializer} for a {@link SystemdInterface}.
		 *
		 * @return the created {@link JsonSerializer}
		 */
		public static JsonSerializer<SystemdInterface> serializer() {
			return jsonObjectSerializer(SystemdInterface.class, //
					json -> new SystemdInterface(//
							json.getString("ifname"), //
							json.getList("addr_info", SystemdAddressInfo.serializer())),
					obj -> JsonUtils.buildJsonObject() //
							.addProperty("ifname", obj.ifname()) //
							.add("addr_info", SystemdAddressInfo.serializer().toListSerializer() //
									.serialize(obj.addressInfos())) //
							.build() //
			);
		}

	}

	private record SystemdAddressInfo(String family, String local, int prefixlen, String label, boolean dynamic) {

		/**
		 * Returns a {@link JsonSerializer} for a {@link SystemdAddressInfo}.
		 *
		 * @return the created {@link JsonSerializer}
		 */
		public static JsonSerializer<SystemdAddressInfo> serializer() {
			return jsonObjectSerializer(SystemdAddressInfo.class, //
					json -> new SystemdAddressInfo(//
							json.getString("family"), //
							json.getString("local"), //
							json.getInt("prefixlen"), //
							json.getStringOrNull("label"), //
							json.getOptionalBoolean("dynamic").orElse(false)), //
					obj -> JsonUtils.buildJsonObject() //
							.addProperty("family", obj.family()) //
							.addProperty("local", obj.local()) //
							.addProperty("prefixlen", obj.prefixlen()) //
							.addProperty("label", obj.label()) //
							.onlyIf(obj.dynamic(), b -> b//
									.addProperty("dynamic", obj.dynamic()))
							.build());
		}

	}

	@Override
	public CompletableFuture<String> getOperatingSystemVersion() {
		final var sc = new SystemCommand(//
				"cat /etc/os-release", //
				false, // runInBackground
				5); // timeoutSeconds

		return execute(sc).thenApply(success -> {
			final var osVersionName = success.stdout().stream() //
					.map(t -> t.split("=", 2)) //
					.filter(t -> t.length == 2) //
					.filter(t -> t[0].equals("PRETTY_NAME")) //
					.map(t -> t[1]) //
					.map(t -> {
						if (t.startsWith("\"") && t.endsWith("\"")) {
							return t.substring(1, t.length() - 1);
						}
						return t;
					}) //
					.findAny();

			return osVersionName.orElseThrow(() -> new CompletionException(
					new OpenemsException("OS-Version name not found in /etc/os-release")));
		});
	}

	@Override
	public Updateable getSystemUpdateable() {
		return null;
	}

}
