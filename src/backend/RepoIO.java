package backend;

import backend.interfaces.RepoCache;
import backend.interfaces.RepoSource;

import java.util.concurrent.CompletableFuture;

public class RepoIO {

	RepoSource repoSource = new GitHubSource();
	RepoCache repoCache = new JSONCache();

	public CompletableFuture<Boolean> login(UserCredentials credentials) {
		return repoSource.login(credentials);
	}

	public CompletableFuture<Model> openRepository(String repoId) {
		if (repoCache.isRepoCached(repoId)) {
			return repoCache.loadRepository(repoId);
		} else {
			return repoSource.downloadRepository(repoId).thenApply(model -> {
				repoCache.saveRepository(repoId, new SerializableModel(model));
				return model;
			});
		}
	}
}
