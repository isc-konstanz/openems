package io.openems.backend.metadata.file;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.gson.JsonObject;

import io.openems.backend.common.metadata.User;
import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.session.Language;
import io.openems.common.session.Role;
import io.openems.common.utils.JsonUtils;

public class FileUser extends User {

	public static final int KEY_LENGTH = 256;
	public static final int ITERATIONS = 10;

	private static final String FALLBACK_ID = "admin";
	private static final String FALLBACK_NAME = "Administrator";
	private static final String FALLBACK_PASSWORD = "admin";
	private static final String FALLBACK_SALT = "DEFAULT";
	private static final Language FALLBACK_LANGUAGE = Language.DE;

	/**
	 * Roles per Edge-ID.
	 */
	private final NavigableMap<String, Role> roles = new TreeMap<>();

	private final byte[] password;
	private final byte[] salt;


	public FileUser(String login, String name, String passwordAsBase64, String saltAsBase64, 
			Language language, Role globalRole, NavigableMap<String, Role> roles, JsonObject settings) {
		this(login, name, passwordAsBase64, saltAsBase64, "", language, globalRole, roles, settings);
	}

	public FileUser(String login, String name, String passwordAsBase64, String saltAsBase64, String token, 
			Language language, Role globalRole, NavigableMap<String, Role> roles, JsonObject settings) {
		this(login, name, Base64.getDecoder().decode(passwordAsBase64), Base64.getDecoder().decode(saltAsBase64), 
				FileUser.generateToken(passwordAsBase64, saltAsBase64, token), language, globalRole, roles, settings);
	}

	private FileUser(String login, String name, final byte[] password, final byte[] salt, String token, 
			Language language, Role globalRole, NavigableMap<String, Role> roles, JsonObject settings) {
		super(login, name, token, language, globalRole, roles.size() > 1, settings);
		this.roles.putAll(roles);
		this.password = password;
		this.salt = salt;
	}

	/**
	 * Gets all Roles for Edge-IDs.
	 *
	 * @return the map of Roles
	 */
	public NavigableMap<String, Role> getEdgeRoles() {
		return Collections.unmodifiableNavigableMap(this.roles);
	}

	/**
	 * Gets the Role for a given Edge-ID.
	 *
	 * @return the Role
	 */
	public Optional<Role> getRole(String edgeId) {
		return Optional.ofNullable(this.roles.get(edgeId));
	}

	/**
	 * Sets the Role for a given Edge-ID.
	 *
	 * @param edgeId the Edge-ID
	 * @param role   the Role
	 */
	public void setRole(String edgeId, Role role) {
		this.roles.put(edgeId, role);
	}

	private static String generateToken(String passwordAsBase64, String saltAsBase64, String token) {
		if (token != null && !token.isEmpty()) {
			return token;
		}
		byte[] salt = Base64.getDecoder().decode(saltAsBase64);
		byte[] hash = FileUser.hashPassword(passwordAsBase64, salt);
		StringBuilder tokenBuilder = new StringBuilder(hash.length * 2);
		for (byte b : hash) {
			tokenBuilder.append(String.format("%02x", b));
		}
		return tokenBuilder.toString();
	}

	/**
	 * Validates a given password against the Users password+salt.
	 *
	 * @param password the given password
	 * @return true if passwords match
	 */
	public boolean validatePassword(String password) {
		if (this.password == null || this.salt == null) {
			// no password existing -> allow access
			return true;
		}
		var hashedPassword = FileUser.hashPassword(password, this.salt, ITERATIONS, KEY_LENGTH);
		return Arrays.equals(hashedPassword, this.password);
	}

	/**
	 * Validates if password+salt match the given password.
	 *
	 * @param passwordAsBase64 the hashed password
	 * @param saltAsBase64	 the salt
	 * @param password		 the given password
	 * @return true if they match.
	 */
	public static boolean validatePassword(String passwordAsBase64, String saltAsBase64, String password) {
		return FileUser.validatePassword(Base64.getDecoder().decode(passwordAsBase64),
				Base64.getDecoder().decode(saltAsBase64), password);
	}

	/**
	 * Validates if password+salt match the given password.
	 *
	 * @param password1 the hashed password
	 * @param salt	  the salt
	 * @param password2 the given password
	 * @return true if they match.
	 */
	public static boolean validatePassword(final byte[] password1, final byte[] salt, String password2) {
		var hashedPassword = FileUser.hashPassword(password2, salt, ITERATIONS, KEY_LENGTH);
		return Arrays.equals(hashedPassword, password1);
	}

	/**
	 * Hashes a password. Source: https://www.owasp.org/index.php/Hashing_Java.
	 *
	 * @param password   the password
	 * @param salt	   the salt
	 * @param iterations the number of iterations
	 * @param keyLength  the length of the key
	 * @return the hashed password
	 */
	public static byte[] hashPassword(final String password, final byte[] salt) {
		return FileUser.hashPassword(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
	}

	/**
	 * Hashes a password. Source: https://www.owasp.org/index.php/Hashing_Java.
	 *
	 * @param password   the password
	 * @param salt	   the salt
	 * @param iterations the number of iterations
	 * @param keyLength  the length of the key
	 * @return the hashed password
	 */
	public static byte[] hashPassword(final String password, final byte[] salt, final int iterations,
			final int keyLength) {
		return FileUser.hashPassword(password.toCharArray(), salt, iterations, keyLength);
	}

	/**
	 * Hashes a password. Source: https://www.owasp.org/index.php/Hashing_Java.
	 *
	 * @param password   the password
	 * @param salt	   the salt
	 * @param iterations the number of iterations
	 * @param keyLength  the length of the key
	 * @return the hashed password
	 */
	public static byte[] hashPassword(final char[] password, final byte[] salt, final int iterations,
			final int keyLength) {
		try {
			var skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
			var spec = new PBEKeySpec(password, salt, iterations, keyLength);
			var key = skf.generateSecret(spec);
			return key.getEncoded();

		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
	}

	public static FileUser fromUser(FileUser user, JsonObject settings) throws OpenemsNamedException {
		return new FileUser(user.getId(), user.getName(), user.password, user.salt, user.getToken(), 
				user.getLanguage(), user.getGlobalRole(), user.getEdgeRoles(), settings);
	}

	public static FileUser fromUser(FileUser user, FileEdge edge, Role role) throws OpenemsNamedException {
		var roles = user.getEdgeRoles();
		roles.put(edge.getId(), role);

		return new FileUser(user.getId(), user.getName(), user.password, user.salt, user.getToken(), 
				user.getLanguage(), user.getGlobalRole(), roles, user.getSettings());
	}

	public static FileUser fromJson(String id, JsonObject json, Collection<FileEdge> edges) throws OpenemsNamedException {
		String name = JsonUtils.getAsOptionalString(json, "name").orElse(id);

		String password = JsonUtils.getAsOptionalString(json, "password").orElseThrow(() -> 
				OpenemsError.JSON_HAS_NO_MEMBER.exception("password", json.toString()));
		String salt = JsonUtils.getAsOptionalString(json, "salt").orElseThrow(() -> 
				OpenemsError.JSON_HAS_NO_MEMBER.exception("salt", json.toString()));
		String token = JsonUtils.getAsOptionalString(json, "token").orElse(null);

		Language language = JsonUtils.getAsOptionalEnum(Language.class, json, "language").orElse(Language.DE);
		Role globalRole = JsonUtils.getAsOptionalEnum(Role.class, json, "role").orElse(Role.GUEST);

		NavigableMap<String, Role> edgeRoles = new TreeMap<>();
		if (globalRole == Role.ADMIN) {
			edgeRoles = edges.stream().map(e -> e.getId())
					.collect(Collectors.toMap(Function.identity(), e -> Role.ADMIN, (e1, e2) -> e1, TreeMap::new));
		}
		else if (json.has("roles") && json.get("roles").isJsonObject()) {
			JsonObject edgeRole = json.getAsJsonObject("roles");
			for (String edgeId : edgeRole.keySet()) {
				if (edges.stream().anyMatch(e -> e.getId().equals(edgeId))) {
					edgeRoles.put(edgeId, Role.getRole(edgeRole.get(edgeId).getAsString().toUpperCase()));
				}
			}
		}
		JsonObject settings = json.has("settings") && json.get("settings").isJsonObject()
			? json.getAsJsonObject("settings")
			: new JsonObject();

		return new FileUser(id, name, password, salt, token, language, globalRole, edgeRoles, settings);
	}

	public static FileUser fallbackAdmin(Collection<FileEdge> edges) {
		NavigableMap<String, Role> edgeRoles = edges
				.stream()
				.map(e -> e.getId())
				.collect(Collectors.toMap(Function.identity(), e -> Role.ADMIN, (e1, e2) -> e1, TreeMap::new));

		byte[] saltAsBytes = FALLBACK_SALT.getBytes(StandardCharsets.UTF_8);
		byte[] passwordAsHash = FileUser.hashPassword(FALLBACK_PASSWORD, saltAsBytes);
		String passwordAsBase64 = Base64.getEncoder().encodeToString(passwordAsHash);
		String saltAsBase64 = Base64.getEncoder().encodeToString(saltAsBytes);
		return new FileUser(FALLBACK_ID, FALLBACK_NAME, passwordAsBase64, saltAsBase64, FALLBACK_LANGUAGE, Role.ADMIN, 
				edgeRoles, new JsonObject());
	}

}
