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
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The working copy of the branch a deployer follows: cloned once, then reset hard on the
 * remote at every sync, so that it never diverges.
 *
 * @author lucien
 */
public class GitRepository {

	private static final Logger log = LoggerFactory.getLogger(GitRepository.class);

	private final File repoDir;

	private final GitSettings settings;

	public GitRepository(GitSettings settings) {
		if (settings.branch() == null || settings.branch().isBlank()) {
			throw new IllegalArgumentException("A branch is required: a deployer must know which branch it deploys");
		}
		this.settings = settings;
		this.repoDir = settings.workdir().toFile();
	}

	public Path repoDir() {
		return this.repoDir.toPath();
	}

	public String branch() {
		return this.settings.branch();
	}

	public void pull() {
		UsernamePasswordCredentialsProvider credentials = credentials();
		String branch = branch();

		if (!this.repoDir.exists()) {
			if (!this.repoDir.mkdirs()) {
				log.error("Failed to create repository directory: {}", this.repoDir.getAbsolutePath());
				return;
			}

			log.info("Cloning repository on branch: {}", branch);
			try (Git ignored = Git.cloneRepository()
				.setURI(this.settings.cloneUrl())
				.setDirectory(this.repoDir)
				.setCredentialsProvider(credentials)
				.setBranch(branch)
				.call()) {
				log.info("Cloned successfully");
			}
			catch (Exception e) {
				log.error("Cloning failed", e);
			}
		}
		else {
			log.info("Syncing repository on branch: {}", branch);
			try (Git git = Git.open(this.repoDir)) {
				git.reset().setMode(ResetCommand.ResetType.HARD).call();

				git.fetch().setCredentialsProvider(credentials).call();
				checkout(git, branch);

				git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/" + branch).call();
			}
			catch (Exception e) {
				log.error("Sync failed", e);
			}
		}
	}

	private void checkout(Git git, String branch) throws IOException, GitAPIException {
		if (branch.equals(git.getRepository().getBranch())) {
			return;
		}

		if (git.getRepository().findRef(branch) != null) {
			git.checkout().setName(branch).call();
		}
		else {
			git.checkout().setName(branch).setCreateBranch(true).setStartPoint("origin/" + branch).call();
		}

		log.info("Checked out branch: {}", branch);
	}

	public void writeFile(String relativePath, String content) throws IOException {
		Files.writeString(repoDir().resolve(relativePath), content, StandardCharsets.UTF_8);
	}

	public String commitAndPush(String relativePath, String message) throws IOException, GitAPIException {
		return commitAndPush(List.of(relativePath), message);
	}

	public String commitAndPush(List<String> relativePaths, String message) throws IOException, GitAPIException {
		try (Git git = Git.open(this.repoDir)) {
			for (String relativePath : relativePaths) {
				git.add().addFilepattern(relativePath).call();
			}

			RevCommit commit = git.commit()
				.setMessage(message)
				.setAuthor(this.settings.authorName(), this.settings.authorEmail())
				.setCommitter(this.settings.authorName(), this.settings.authorEmail())
				.call();

			Iterable<PushResult> results = git.push()
				.setCredentialsProvider(credentials())
				.setRemote("origin")
				.add(branch())
				.call();

			for (PushResult result : results) {
				for (RemoteRefUpdate update : result.getRemoteUpdates()) {
					RemoteRefUpdate.Status status = update.getStatus();
					if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
						throw new PushRejectedException("Push rejected (" + status + "): " + update.getMessage());
					}
				}
			}

			log.info("Pushed {} on {}: {}", commit.getName(), branch(), message);
			return commit.getName();
		}
	}

	public Optional<RepoHead> head() {
		try (Git git = Git.open(this.repoDir)) {
			ObjectId headId = git.getRepository().resolve("HEAD");
			if (headId == null) {
				return Optional.empty();
			}
			try (RevWalk walk = new RevWalk(git.getRepository())) {
				RevCommit commit = walk.parseCommit(headId);
				return Optional.of(new RepoHead(git.getRepository().getBranch(), commit.getName(),
						commit.getName().substring(0, 7), commit.getShortMessage(), commit.getAuthorIdent().getName(),
						Instant.ofEpochSecond(commit.getCommitTime())));
			}
		}
		catch (Exception ex) {
			log.debug("Could not read the repository head: {}", ex.toString());
			return Optional.empty();
		}
	}

	private UsernamePasswordCredentialsProvider credentials() {
		if (!this.settings.authenticated()) {
			return null;
		}
		return new UsernamePasswordCredentialsProvider(this.settings.user(), this.settings.token());
	}

	public static class PushRejectedException extends RuntimeException {

		public PushRejectedException(String message) {
			super(message);
		}

	}

}
