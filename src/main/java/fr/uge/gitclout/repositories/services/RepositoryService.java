package fr.uge.gitclout.repositories.services;

import fr.uge.gitclout.analyze.Refresh;
import fr.uge.gitclout.analyze.jpa.ContributorRequest;
import fr.uge.gitclout.repositories.api.data.RepositoryData;
import fr.uge.gitclout.repositories.jpa.RepositoryRequest;
import fr.uge.gitclout.repositories.jpa.RepositoryStorage;
import fr.uge.gitclout.tags.api.SseController;
import fr.uge.gitclout.repositories.api.data.HistoryData;
import fr.uge.gitclout.tags.api.data.RefreshData;
import fr.uge.gitclout.tags.services.TagService;
import fr.uge.gitclout.tags.jpa.TagRequest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class RepositoryService {

    private final TagRequest tagRequests;
    private final ContributorRequest contributorsRequests;
    private final RepositoryRequest repositoryRequests;
    private final SseController sseController;
    private String repositoryUrl;
    private String projectName;

    public RepositoryService(RepositoryRequest repositoryRequests, TagRequest tagRequests, SseController sseController, ContributorRequest contributorsRequests) {
        this.contributorsRequests = contributorsRequests;
        this.repositoryRequests = repositoryRequests;
        this.tagRequests = tagRequests;
        this.sseController = sseController;
    }

    /**
     * Downloads the Git repository to the local file system.
     */
    public void downloadRepository()  {
        String tempDir = System.getProperty("java.io.tmpdir");
        String destination = tempDir + File.separator + "gitclout_tmp" + File.separator + projectName;
        try {
            Path destinationPath = Paths.get(destination);
            Git.cloneRepository().setURI(repositoryUrl).setDirectory(destinationPath.toFile()).setBare(true).call();
        } catch (GitAPIException e) {
            throw new RuntimeException("Error: Downloading repository", e);
        }
    }

    /**
     * Adds a new Git repository to the local file system.
     *
     * @param url Git repository URL
     * @return Repository data
     */
    public Mono<RepositoryData> addRepository(String url) {
        if (!checkUrl(url)) {
            return handleInvalidRepository();
        }
        this.repositoryUrl = url;
        return handleNewOrExistingRepository();
    }

    /**
     * Returns the list of all Git repositories.
     *
     * @return List of all Git repositories
     */
    public Flux<HistoryData> repositoryHistory() {
        return Mono.fromCallable(repositoryRequests::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(repositories -> repositories)
                .map(repository -> new HistoryData(repository.repositoryUrl(), repository.projectName()));
    }

    /**
     * Refreshes the tags of a Git repository.
     *
     * @param name Git repository name
     * @return Refresh data
     */
    public Mono<RefreshData> refreshTags(String name) {
        return Mono.fromCallable(() -> {
                    for (RepositoryStorage repository : repositoryRequests.findAll()) {
                        if (repository.projectName().equals(name.split("_")[0])) {
                            repositoryUrl = repository.repositoryUrl();
                            break;
                        }
                    }
                    Thread.sleep(1000); // wait don't get the same timestamp
                    downloadRepositoryForRefresh(name);
                    return new RefreshData("Tags refreshed", 0);
                }).subscribeOn(Schedulers.boundedElastic()).onErrorResume(e -> Mono.error(new RuntimeException("Error: Refreshing tags", e)));
    }

    /**
     * Downloads a Git repository for refreshing.
     *
     * @param name Git repository name
     */
    public void downloadRepositoryForRefresh(String name) {
        projectName = getRepositoryName(getDisplayName(name));
        downloadRepository();
        var refresh = new Refresh(projectName, tagRequests, contributorsRequests);
        refresh.refreshTags();
    }

    /**
     * Checks if the given Git repository URL is valid.
     *
     * @param url Git repository URL
     * @return True if the URL is valid, false otherwise
     */
    public static boolean checkUrl(String url) {
        try {
            URIish uri = new URIish(url);
            LsRemoteCommand lsRemote = new LsRemoteCommand(null);
            lsRemote.setRemote(uri.toString());
            lsRemote.call();
            return true;
        } catch (GitAPIException | URISyntaxException e) {
            return false;
        }
    }

    /**
     * Checks if the given Git repository URL already exists in the database.
     *
     * @return True if the repository exists, false otherwise
     */
    private boolean isRepositoryExist() {
        String[] parts = repositoryUrl.split("/");
        projectName = getRepositoryName(parts[parts.length - 1]);
        if (repositoryRequests == null) return false;
        for (RepositoryStorage repository : repositoryRequests.findAll()) {
            if (repository.repositoryUrl().equals(repositoryUrl)) {
                projectName = getRepositoryName(repository.projectName());
                return true;
            }
        }
        return false;
    }

    /**
     * Inserts the repository in the database.
     */
    private void insertInDatabase() {
        String tempDir = System.getProperty("java.io.tmpdir");
        String repositoryPath = tempDir + "gitclout_tmp" + File.separator + projectName;
        TagService tagServices = new TagService(tagRequests, contributorsRequests, sseController);
        var displayName = getDisplayName(projectName);
        var storage = new RepositoryStorage(repositoryUrl, displayName, repositoryPath);
        repositoryRequests.save(storage);
        tagServices.insertTagsInDatabase(projectName);
    }

    /**
     * Handles the new or existing repository.
     *
     * @return Repository data
     */
    private Mono<RepositoryData> handleNewOrExistingRepository() {
        var tagServices = new TagService(tagRequests, contributorsRequests, sseController);
        return isRepositoryExist() ? handleExistingRepository(tagServices) : handleNewRepository();
    }

    /**
     * Handles the new repository.
     *
     * @return Repository data
     */
    private Mono<RepositoryData> handleNewRepository() {
        downloadRepository();
        insertInDatabase();
        return Mono.just(new RepositoryData("Your repository has been added", 0, projectName));
    }

    /**
     * Handles the existing repository.
     *
     * @param tagServices Tag services
     * @return Repository data
     */
    private Mono<RepositoryData> handleExistingRepository(TagService tagServices) {
        updateProjectNameForExistingRepository();
        tagServices.insertTagsInDatabase(projectName);
        return Mono.just(new RepositoryData("Your repository already exists", -1, projectName));
    }

    /**
     * Updates the project name for an existing repository.
     */
    private void updateProjectNameForExistingRepository() {
        for (RepositoryStorage repository : repositoryRequests.findAll()) {
            if (repository.repositoryUrl().equals(repositoryUrl)) {
                projectName = repository.projectName() + "_" + getLastPathSegment(repository.repositoryPath());
                break;
            }
        }
    }

    /**
     * Returns the last path segment of the given path.
     *
     * @param path Path
     * @return Last path segment
     */
    private String getLastPathSegment(String path) {
        String[] segments = path.split("_");
        return segments[segments.length - 1];
    }

    /**
     * Handles the invalid repository.
     *
     * @return Repository data
     */
    private static Mono<RepositoryData> handleInvalidRepository() {
        return Mono.just(new RepositoryData("Error : this repository doesn't exist !", 1, "null"));
    }

    /**
     * Returns the display name of the repository.
     *
     * @param name Repository NAME
     * @return Display name
     */
    private static String getDisplayName(String name) {
        int lastUnderscoreIndex = name.lastIndexOf('_');
        return (lastUnderscoreIndex != -1) ? name.substring(0, lastUnderscoreIndex) : name;
    }

    /**
     * Generates a unique repository name based on the current timestamp.
     *
     * @param name Original repository name
     * @return Unique repository name
     */
    private static String getRepositoryName(String name) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String timestamp = dateFormat.format(new Date());
        return name + "_" + timestamp;
    }

    /**
     * Deletes a repository.
     *
     * @param name Repository name
     * @return Void
     */
    public Mono<Void> deleteRepository(String name) {
        return Mono.fromRunnable(() -> {
                    for (RepositoryStorage repository : repositoryRequests.findAll()) {
                        if (repository.projectName().equals(name)) {
                            repositoryRequests.delete(repository);
                            break;
                        }
                    }
                    removeAllTags(name);
                })
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Removes all tags of a repository.
     *
     * @param name Repository name
     */
    private void removeAllTags(String name) {
        for (var tag : tagRequests.findAll()) {
            if (tag.projectName().split("_")[0].equals(name)) {
                tagRequests.delete(tag);
            }
        }
        removeContributors(name);
    }

    /**
     * Removes all contributors of a repository.
     *
     * @param name Repository name
     */
    private void removeContributors(String name) {
        for (var contributor : contributorsRequests.findAll()) {
            if (contributor.projectName().split("_")[0].equals(name)) {
                contributorsRequests.delete(contributor);
            }
        }
    }
}
