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

package ch.lucnelmr.swarmdeployer.git;

import java.nio.file.Path;

/**
 * Where a working copy comes from and lives. Without a token, the repository is read
 * anonymously: enough for a public one, which is then never pushed to.
 *
 * @author lucien
 */
public record GitSettings(String cloneUrl, String branch, Path workdir, String user, String token, String authorName,
		String authorEmail) {

	public static GitSettings anonymous(String cloneUrl, String branch, Path workdir) {
		return new GitSettings(cloneUrl, branch, workdir, null, null, null, null);
	}

	public boolean authenticated() {
		return this.token != null && !this.token.isBlank();
	}

}
