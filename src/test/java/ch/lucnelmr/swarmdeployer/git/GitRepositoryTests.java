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

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author lucien
 */
class GitRepositoryTests {

	@TempDir
	Path upstream;

	@TempDir
	Path work;

	@BeforeEach
	void setUp() throws Exception {
		try (Git git = Git.init().setDirectory(this.upstream.toFile()).setInitialBranch("beta").call()) {
			commit(git, "VERSION", "2026.10.0\n", "first");
		}
	}

	@Test
	void clonesAnonymouslyThenFollowsTheBranch() throws Exception {
		GitRepository repository = new GitRepository(
				GitSettings.anonymous(this.upstream.toUri().toString(), "beta", this.work.resolve("repo")));

		repository.pull();
		assertThat(repository.repoDir().resolve("VERSION")).hasContent("2026.10.0");
		assertThat(repository.head()).hasValueSatisfying(head -> assertThat(head.message()).isEqualTo("first"));

		try (Git git = Git.open(this.upstream.toFile())) {
			commit(git, "VERSION", "2026.11.0\n", "second");
		}
		repository.pull();

		assertThat(repository.repoDir().resolve("VERSION")).hasContent("2026.11.0");
		assertThat(repository.head()).hasValueSatisfying(head -> {
			assertThat(head.branch()).isEqualTo("beta");
			assertThat(head.message()).isEqualTo("second");
			assertThat(head.shortSha()).hasSize(7);
		});
	}

	@Test
	void discardsLocalChangesAtEverySync() throws Exception {
		GitRepository repository = new GitRepository(
				GitSettings.anonymous(this.upstream.toUri().toString(), "beta", this.work.resolve("repo")));
		repository.pull();

		repository.writeFile("VERSION", "tampered\n");
		repository.pull();

		assertThat(repository.repoDir().resolve("VERSION")).hasContent("2026.10.0");
	}

	@Test
	void requiresABranch() {
		assertThatThrownBy(() -> new GitRepository(GitSettings.anonymous("file:///nowhere", " ", this.work)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static void commit(Git git, String file, String content, String message) throws Exception {
		Files.writeString(git.getRepository().getWorkTree().toPath().resolve(file), content);
		git.add().addFilepattern(file).call();
		git.commit().setMessage(message).setAuthor("test", "test@example.invalid").setSign(false).call();
	}

}
