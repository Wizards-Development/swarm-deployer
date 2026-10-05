/*
 * Copyright 2019-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.lucnelmr.swarmdeployer.encryption;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seals values as {cipher}…{cipher} and opens them again: AES-GCM with an IV per value,
 * and the older AES-ECB values still readable.
 *
 * @author lucien
 */
public class EncryptionService {

	private static final Pattern ENCRYPTED_PATTERN = Pattern.compile("\\{cipher}(.*?)\\{cipher}");

	private static final String VERSION_PREFIX = "v2.";

	private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";

	private static final String LEGACY_TRANSFORMATION = "AES";

	private static final int IV_LENGTH = 12;

	private static final int TAG_LENGTH_BITS = 128;

	private final SecretKey secretKey;

	private final SecureRandom random = new SecureRandom();

	public EncryptionService(String cipherKey) {
		if (cipherKey == null || cipherKey.isBlank()) {
			throw new IllegalArgumentException("A cipher key is required");
		}
		this.secretKey = deriveKey(cipherKey);
	}

	public String encrypt(String plainText) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			this.random.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] sealed = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			byte[] payload = new byte[iv.length + sealed.length];
			System.arraycopy(iv, 0, payload, 0, iv.length);
			System.arraycopy(sealed, 0, payload, iv.length, sealed.length);

			return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not encrypt the value", ex);
		}
	}

	public String decrypt(String cipherText) {
		try {
			return cipherText.startsWith(VERSION_PREFIX) ? decryptGcm(cipherText.substring(VERSION_PREFIX.length()))
					: decryptLegacy(cipherText);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not decrypt a {cipher} value — wrong key, or the value was altered",
					ex);
		}
	}

	public String decryptContent(String content) {
		Matcher matcher = ENCRYPTED_PATTERN.matcher(content);
		StringBuilder result = new StringBuilder();

		while (matcher.find()) {
			matcher.appendReplacement(result, Matcher.quoteReplacement(decrypt(matcher.group(1))));
		}
		matcher.appendTail(result);

		return result.toString();
	}

	public boolean containsEncryptedValues(String content) {
		return ENCRYPTED_PATTERN.matcher(content).find();
	}

	public String wrap(String cipherText) {
		return "{cipher}" + cipherText + "{cipher}";
	}

	private String decryptGcm(String payload) throws Exception {
		byte[] decoded = Base64.getDecoder().decode(payload);
		if (decoded.length <= IV_LENGTH) {
			throw new IllegalArgumentException("Sealed value is too short to hold an IV");
		}

		Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
		cipher.init(Cipher.DECRYPT_MODE, this.secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, decoded, 0, IV_LENGTH));

		byte[] clear = cipher.doFinal(decoded, IV_LENGTH, decoded.length - IV_LENGTH);
		return new String(clear, StandardCharsets.UTF_8);
	}

	private String decryptLegacy(String payload) throws Exception {
		Cipher cipher = Cipher.getInstance(LEGACY_TRANSFORMATION);
		cipher.init(Cipher.DECRYPT_MODE, this.secretKey);
		return new String(cipher.doFinal(Base64.getDecoder().decode(payload)), StandardCharsets.UTF_8);
	}

	private static SecretKey deriveKey(String secret) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return new SecretKeySpec(sha256.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
		}
		catch (Exception ex) {
			throw new IllegalStateException("SHA-256 is unavailable, cannot derive the cipher key", ex);
		}
	}

}
