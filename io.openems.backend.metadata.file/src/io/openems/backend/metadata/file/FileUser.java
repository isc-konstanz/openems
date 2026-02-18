package io.openems.backend.metadata.file;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;

import io.openems.backend.common.metadata.Edge;
import io.openems.backend.common.metadata.User;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.session.Language;
import io.openems.common.session.Role;
import io.openems.common.utils.JsonUtils;

public class FileUser extends User {

	private static final String FALLBACK_MAIL = "guest@localhost";
	private static final String FALLBACK_NAME = "Guest";
	private static final Language FALLBACK_LANGUAGE = Language.DE;

	/**
	 * Roles per Edge-ID.
	 */
	private final Collection<String> edges = new ArrayList<>();


	private FileUser(String id, String mail, String name,
			Language language, Role globalRole, Collection<String> edges, JsonObject settings) {
		super(id, mail, name, "", language, globalRole, edges.size() > 1, settings);
		this.edges.addAll(edges);
	}

	public Collection<String> getEdges() {
		return Collections.unmodifiableCollection(this.edges);
	}

	public void addEdge(String edgeId) {
		this.edges.add(edgeId);
	}

	public static FileUser fromUser(FileUser user, JsonObject settings) throws OpenemsNamedException {
		return new FileUser(user.getUserId(), user.getEmail(), user.getName(), 
				user.getLanguage(), user.getGlobalRole(), user.getEdges(), settings);
	}

	public static FileUser fromUser(FileUser user, Edge edge) throws OpenemsNamedException {
		var edges = user.getEdges();
		edges.add(edge.getId());

		return new FileUser(user.getId(), user.getEmail(), user.getName(), 
				user.getLanguage(), user.getGlobalRole(), edges, user.getSettings());
	}

	public static FileUser fromJson(String id, JsonObject json, Collection<FileEdge> edges) throws OpenemsNamedException {
		String mail = JsonUtils.getAsString(json, "mail");
		String name = JsonUtils.getAsString(json, "name");

		Language language = JsonUtils.getAsOptionalEnum(Language.class, json, "language").orElse(Language.DE);
		Role globalRole = JsonUtils.getAsOptionalEnum(Role.class, json, "role").orElse(Role.GUEST);

		Collection<String> edgeIds = new ArrayList<>();
		if (json.has("roles") && json.get("roles").isJsonObject()) {
			JsonObject edgeRole = json.getAsJsonObject("roles");
			for (String edgeId : edgeRole.keySet()) {
				if (edges.stream().anyMatch(e -> e.getId().equals(edgeId))) {
					edgeIds.add(edgeId);
				}
			}
		}
		else if (globalRole == Role.ADMIN) {
			edgeIds = edges.stream().map(e -> e.getId()).collect(Collectors.toList());
		}
		JsonObject settings = json.has("settings") && json.get("settings").isJsonObject()
			? json.getAsJsonObject("settings")
			: new JsonObject();

		return new FileUser(id, mail, name, language, globalRole, edgeIds, settings);
	}

	public static FileUser fallbackGuest(String id, Collection<FileEdge> edges) {
		Collection<String> edgeIds = edges.stream().filter(e -> e.isPublic()).map(e -> e.getId()).collect(Collectors.toList());
		return new FileUser(id, FALLBACK_MAIL, FALLBACK_NAME, FALLBACK_LANGUAGE, Role.GUEST, edgeIds, new JsonObject());
	}

}
