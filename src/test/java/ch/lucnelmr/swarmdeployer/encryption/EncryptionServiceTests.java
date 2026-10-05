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

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author lucien
 */
class EncryptionServiceTests {

	private static final String SECRET = "a-secret-worth-keeping";

	private final EncryptionService encryptionService = service(SECRET);

	@Test
	void sealsAndOpensAValue() {
		String sealed = this.encryptionService.encrypt("hunter2");

		assertThat(sealed).startsWith("v2.");
		assertThat(this.encryptionService.decrypt(sealed)).isEqualTo("hunter2");
	}

	@Test
	void sealsTheSameValueDifferentlyEachTime() {
		String first = this.encryptionService.encrypt("same-password");
		String second = this.encryptionService.encrypt("same-password");

		assertThat(first).isNotEqualTo(second);
		assertThat(this.encryptionService.decrypt(first)).isEqualTo("same-password");
		assertThat(this.encryptionService.decrypt(second)).isEqualTo("same-password");
	}

	@Test
	void stillOpensValuesFromTheOldEcbScheme() throws Exception {
		assertThat(this.encryptionService.decrypt(legacyEcb("legacy-token", SECRET))).isEqualTo("legacy-token");
	}

	@Test
	void refusesAValueThatWasAltered() {
		String sealed = this.encryptionService.encrypt("hunter2");
		byte[] raw = Base64.getDecoder().decode(sealed.substring("v2.".length()));
		raw[raw.length - 1] ^= 0x01;
		String altered = "v2." + Base64.getEncoder().encodeToString(raw);

		assertThatThrownBy(() -> this.encryptionService.decrypt(altered)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("altered");
	}

	@Test
	void refusesAValueSealedWithAnotherKey() {
		String sealed = service("a-different-key").encrypt("hunter2");

		assertThatThrownBy(() -> this.encryptionService.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void opensEveryMarkerInAFile() {
		String content = "PASS: {cipher}" + this.encryptionService.encrypt("one") + "{cipher}\n" + "TOKEN: {cipher}"
				+ this.encryptionService.encrypt("two") + "{cipher}\n";

		assertThat(this.encryptionService.decryptContent(content)).isEqualTo("PASS: one\nTOKEN: two\n");
	}

	@Test
	void failsTheWholeFileRatherThanLeaveAValueSealed() {
		String content = "PASS: {cipher}not-even-base64-!!{cipher}";

		assertThatThrownBy(() -> this.encryptionService.decryptContent(content))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void leavesAFileWithoutMarkersAlone() {
		String content = "image: ghcr.io/example/app:1.0.0\n";

		assertThat(this.encryptionService.containsEncryptedValues(content)).isFalse();
		assertThat(this.encryptionService.decryptContent(content)).isEqualTo(content);
	}

	private static EncryptionService service(String secret) {
		return new EncryptionService(secret);
	}

	private static String legacyEcb(String plainText, String secret) throws Exception {
		byte[] key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
		return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
	}

}
