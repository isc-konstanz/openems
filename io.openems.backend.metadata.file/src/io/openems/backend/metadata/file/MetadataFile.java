package io.openems.backend.metadata.file;

import static java.util.stream.Collectors.joining;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.backend.authentication.api.AuthUserPasswordAuthenticationService;
import io.openems.backend.authentication.api.model.PasswordAuthenticationResult;
import io.openems.backend.common.alerting.OfflineEdgeAlertingSetting;
import io.openems.backend.common.alerting.SumStateAlertingSetting;
import io.openems.backend.common.alerting.UserAlertingSettings;
import io.openems.backend.common.metadata.AbstractMetadata;
import io.openems.backend.common.metadata.Edge;
import io.openems.backend.common.metadata.EdgeHandler;
import io.openems.backend.common.metadata.Metadata;
import io.openems.backend.common.metadata.MetadataUtils;
import io.openems.backend.common.metadata.SimpleEdgeHandler;
import io.openems.backend.common.metadata.User;
import io.openems.common.channel.Level;
import io.openems.common.event.EventBuilder;
import io.openems.common.event.EventReader;
import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.request.GetEdgesRequest.PaginationOptions;
import io.openems.common.jsonrpc.response.GetEdgesResponse.EdgeMetadata;
import io.openems.common.session.Language;
import io.openems.common.session.Role;
import io.openems.common.utils.JsonUtils;

/**
 * This implementation of MetadataService reads Edges configuration from a file.
 * The layout of the file is as follows:
 *
 * <pre>
 * {
 *   edges: {
 *     [edgeId: string]: {
 *       comment: string,
 *       apikey: string
 *       setuppassword?: string
 *     }
 *   }
 * }
 * </pre>
 *
 */
@Designate(ocd = Config.class, factory = false)
@Component(//
		name = "Metadata.File", //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		immediate = true //
)
@EventTopics({ //
		Edge.Events.ON_SET_CONFIG //
})
public class MetadataFile extends AbstractMetadata implements Metadata, AuthUserPasswordAuthenticationService, EventHandler {

	private final Logger log = LoggerFactory.getLogger(MetadataFile.class);
	private final Map<String, FileUser> users = new HashMap<>();
	private final Map<String, FileEdge> edges = new HashMap<>();
	private final SimpleEdgeHandler edgeHandler = new SimpleEdgeHandler();

	@Reference
	private EventAdmin eventAdmin;

	private String path = "";

	public MetadataFile() {
		super("Metadata.File");
	}

	@Activate
	private void activate(Config config) {
		this.log.info("Activate [path=" + config.path() + "]");
		this.path = config.path();

		// Read the data async
		CompletableFuture.runAsync(() -> {
			this.refreshData();
		});
	}

	@Deactivate
	private void deactivate() {
		this.logInfo(this.log, "Deactivate");
	}

	@Override
	public CompletableFuture<PasswordAuthenticationResult> authenticateWithPassword(String username, String password) {
		this.refreshData();
		if (this.users.containsKey(username)) {
			var user = this.users.get(username);
			if (user.validatePassword(password)) {
				CompletableFuture.completedFuture(new PasswordAuthenticationResult(username, user.getName(), user.getToken()));
			}
		}
		return CompletableFuture.failedFuture(OpenemsError.COMMON_AUTHENTICATION_FAILED.exception());
	}

	@Override
	public CompletableFuture<PasswordAuthenticationResult> authenticateWithToken(String token) {
		this.refreshData();
		for (FileUser user : this.users.values()) {
			if (user.getToken().equals(token)) {
				CompletableFuture.completedFuture(new PasswordAuthenticationResult(user.getId(), user.getName(), token));
			}
		}
		return CompletableFuture.failedFuture(OpenemsError.COMMON_AUTHENTICATION_FAILED.exception());
	}

	@Override
	public CompletableFuture<Void> logout(String token) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public synchronized Optional<String> getEdgeIdForApikey(String apikey) {
		this.refreshData();
		for (Entry<String, FileEdge> entry : this.edges.entrySet()) {
			var edge = entry.getValue();
			if (edge.getApikey().equals(apikey)) {
				return Optional.of(edge.getId());
			}
		}
		return Optional.empty();
	}

	@Override
	public synchronized Optional<Edge> getEdgeBySetupPassword(String setupPassword) {
		this.refreshData();
		for (FileEdge edge : this.edges.values()) {
			if (edge.getSetupPassword().equals(setupPassword)) {
				return Optional.of(edge);
			}
		}
		return Optional.empty();
	}

	@Override
	public synchronized Optional<Edge> getEdge(String edgeId) {
		this.refreshData();
		Edge edge = this.edges.get(edgeId);
		return Optional.ofNullable(edge);
	}

	@Override
	public CompletableFuture<User> getUserByExternalId(String userId) {
		User user = this.getUser(userId).orElse(null);
		if (user != null) {
			return CompletableFuture.completedFuture(user);
		}
		return CompletableFuture.failedFuture(OpenemsError.COMMON_USER_UNDEFINED.exception());
	}

	@Override
	public Optional<User> getUser(String userId) {
		this.refreshData();
		User user = this.users.get(userId);
		return Optional.ofNullable(user);
	}

	@Override
	public void registerUser(JsonObject jsonObject, String oem) throws OpenemsNamedException {
		throw new UnsupportedOperationException("FileMetadata.registerUser() is not implemented");
	}

	@Override
	public synchronized Collection<Edge> getAllOfflineEdges() {
		this.refreshData();
		return this.edges.values().stream().filter(Edge::isOffline).collect(Collectors.toUnmodifiableList());
	}

	@Override
	public void updateUserLanguage(User user, Language locale) throws OpenemsNamedException {
		// TODO: Update metadata file
		user.setLanguage(locale);
	}

	@Override
	public void updateUserSettings(User user, JsonObject settings) throws OpenemsNamedException {
		// TODO: Update metadata file
		users.put(user.getId(), FileUser.fromUser((FileUser) user, settings));
	}

	@Override
	public Role getUserRole(User user, String edgeId) {
		return ((FileUser) user).getEdgeRoles().getOrDefault(edgeId, null);
	}

	@Override
	public void addEdgeToUser(User user, Edge edge) throws OpenemsNamedException {
		// TODO: Update metadata file
		if (!user.hasMultipleEdges()) {
			users.put(user.getId(), FileUser.fromUser((FileUser) user, (FileEdge) edge, Role.INSTALLER));
			return;
		}
		((FileUser) user).setRole(edge.getId(), Role.INSTALLER);
	}

	@Override
	public Map<String, Object> getUserInformation(User user) throws OpenemsNamedException {
		throw new UnsupportedOperationException("FileMetadata.getUserInformation() is not implemented");
	}

	@Override
	public void setUserInformation(User user, JsonObject jsonObject) throws OpenemsNamedException {
		throw new UnsupportedOperationException("FileMetadata.setUserInformation() is not implemented");
	}

	@Override
	public byte[] getSetupProtocol(User user, int setupProtocolId) throws OpenemsNamedException {
		throw new UnsupportedOperationException("FileMetadata.getSetupProtocol() is not implemented");
	}

	@Override
	public JsonObject getSetupProtocolData(User user, String edgeId) throws OpenemsNamedException {
		throw new UnsupportedOperationException("FileMetadata.getSetupProtocolData() is not implemented");
	}

	@Override
	public SetupProtocolCoreInfo getLatestSetupProtocolCoreInfo(String edgeId) throws OpenemsNamedException {
		return null;
	}

	@Override
	public List<SetupProtocolCoreInfo> getProtocolsCoreInfo(String edgeId) throws OpenemsNamedException {
		return Collections.emptyList();
	}

	@Override
	public int submitSetupProtocol(User user, JsonObject jsonObject) {
		throw new UnsupportedOperationException("FileMetadata.submitSetupProtocol() is not implemented");
	}

	@Override
	public void createSerialNumberExtensionProtocol(String edgeId, Map<String, Map<String, String>> serialNumbers,
			List<SetupProtocolItem> items) {
		this.log.info("SerialNumberProtocol[{}]: {}, {}", edgeId, serialNumbers, items);
	}

	@Override
	public Optional<String> getSerialNumberForEdge(Edge edge) {
		throw new UnsupportedOperationException("FileMetadata.getSerialNumberForEdge() is not implemented");
	}

	@Override
	public Optional<String> getEmsTypeForEdge(String edgeId) {
		throw new UnsupportedOperationException("FileMetadata.getEmsTypeForEdge() is not implemented");
	}

	@Override
	public UserAlertingSettings getUserAlertingSettings(String edgeId, String userId) throws OpenemsException {
		throw new UnsupportedOperationException("FileMetadata.getUserAlertingSettings() is not implemented");
	}

	@Override
	public List<UserAlertingSettings> getUserAlertingSettings(String edgeId) {
		throw new UnsupportedOperationException("FileMetadata.getUserAlertingSettings() is not implemented");
	}

	@Override
	public List<OfflineEdgeAlertingSetting> getEdgeOfflineAlertingSettings(String edgeId) throws OpenemsException {
		throw new UnsupportedOperationException("FileMetadata.getEdgeOfflineAlertingSettings() is not implemented");
	}

	@Override
	public List<SumStateAlertingSetting> getSumStateAlertingSettings(String edgeId) throws OpenemsException {
		throw new UnsupportedOperationException("FileMetadata.getSumStateAlertingSettings() is not implemented");
	}

	@Override
	public void setUserAlertingSettings(User user, String edgeId, List<UserAlertingSettings> users) {
		throw new UnsupportedOperationException("FileMetadata.setUserAlertingSettings() is not implemented");
	}

	@Override
	public CompletableFuture<List<EdgeMetadata>> getPageDevice(User user, PaginationOptions paginationOptions) {
		return CompletableFuture
				.completedFuture(MetadataUtils.getPageDevice(user, this.edges.values(), paginationOptions));
	}

	@Override
	public CompletableFuture<EdgeMetadata> getEdgeMetadataForUser(User user, String edgeId) {
		final var edge = this.edges.get(edgeId);
		if (edge == null) {
			return CompletableFuture.failedFuture(new OpenemsException("Unable to find edge with id [" + edgeId + "]"));
		}
		var edgeRole = ((FileUser) user).getRole(edgeId);
		if (!edgeRole.isPresent()) {
			return null;
		}
		return CompletableFuture.completedFuture(new EdgeMetadata(//
				edge.getId(), //
				edge.getComment(), //
				edge.getProducttype(), //
				edge.getVersion(), //
				edgeRole.get(), //
				edge.isOnline(), //
				edge.getLastmessage(), //
				null, // firstSetupProtocol
				Level.OK, //
				edge.getSettings() //
		));
	}

	@Override
	public Optional<Level> getSumState(String edgeId) {
		throw new UnsupportedOperationException("FileMetadata.getSumState() is not implemented");
	}

	@Override
	public EventAdmin getEventAdmin() {
		return this.eventAdmin;
	}

	@Override
	public EdgeHandler edge() {
		return this.edgeHandler;
	}

	@Override
	public void handleEvent(Event event) {
		var reader = new EventReader(event);

		switch (event.getTopic()) {
		case Edge.Events.ON_SET_CONFIG -> {
			this.edgeHandler.setEdgeConfigFromEvent(reader, (edge, oldConfig, newConfig) -> {
				EventBuilder.from(this.eventAdmin, Edge.Events.ON_UPDATE_CONFIG) //
						.addArg(Edge.Events.OnUpdateConfig.EDGE_ID, edge.getId()) //
						.addArg(Edge.Events.OnUpdateConfig.OLD_CONFIG, oldConfig) //
						.addArg(Edge.Events.OnUpdateConfig.NEW_CONFIG, newConfig) //
						.send();
			});
		}
		}
	}

	private synchronized void refreshData() {
		if (this.edges.isEmpty()) {
			// Read file
			var sb = new StringBuilder();
			String line = null;
			try (var br = new BufferedReader(new FileReader(this.path))) {
				while ((line = br.readLine()) != null) {
					sb.append(line);
				}
			} catch (IOException e) {
				this.logWarn(this.log, "Unable to read file [" + this.path + "]: " + e.getMessage());
				e.printStackTrace();
				return;
			}

			List<FileUser> users = new ArrayList<>();
			List<FileEdge> edges = new ArrayList<>();

			// parse to JSON
			try {
				var jsonConfig = JsonUtils.parse(sb.toString());
				var jsonUsers = JsonUtils.getAsOptionalJsonObject(jsonConfig, "users");
				var jsonEdges = JsonUtils.getAsJsonObject(jsonConfig, "edges");

				for (Entry<String, JsonElement> entry : jsonEdges.entrySet()) {
					edges.add(FileEdge.fromJson(this, entry.getKey(), 
							JsonUtils.getAsJsonObject(entry.getValue())));
				}
				if (jsonUsers.isPresent()) {
					for (Entry<String, JsonElement> entry : jsonUsers.get().entrySet()) {
						users.add(FileUser.fromJson(entry.getKey(), JsonUtils.getAsJsonObject(entry.getValue()), edges));
					}
				}
			} catch (OpenemsNamedException e) {
				this.logWarn(this.log, "Unable to JSON-parse file [" + this.path + "]: " + e.getMessage());
				e.printStackTrace();
				return;
			}
			if (users.size() == 0) {
				users.add(FileUser.fallbackAdmin(edges));
			}
			for (FileUser user : users) {
				this.users.put(user.getId(), user);
			}
			for (FileEdge edge : edges) {
				this.edges.put(edge.getId(), edge);
			}
		}
		this.setInitialized();
	}

	@Override
	public void logGenericSystemLog(GenericSystemLog systemLog) {
		this.logInfo(this.log,
				"%s on %s executed %s [%s]".formatted(systemLog.user().getId(), systemLog.edgeId(), systemLog.teaser(),
						systemLog.getValues().entrySet().stream() //
								.map(t -> t.getKey() + "=" + t.getValue()) //
								.collect(joining(", "))));
	}

}
