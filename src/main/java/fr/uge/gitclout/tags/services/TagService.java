package fr.uge.gitclout.tags.services;

import fr.uge.gitclout.analyze.Contributor;
import fr.uge.gitclout.analyze.FileExtractor;
import fr.uge.gitclout.analyze.api.data.ContributorData;
import fr.uge.gitclout.analyze.jpa.ContributorRequest;
import fr.uge.gitclout.tags.api.data.VariationData;
import fr.uge.gitclout.tags.jpa.TagRequest;
import fr.uge.gitclout.tags.jpa.TagStorage;
import fr.uge.gitclout.analyze.jpa.ContributorStorage;
import fr.uge.gitclout.tags.api.data.TagData;
import fr.uge.gitclout.tags.api.SseController;
import fr.uge.gitclout.tags.api.data.Progress;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TagService {

    private final TagRequest tagRequests;
    private final ContributorRequest contributorsRequests;
    private Git git;
    private final SseController sseController;
    private int tagAnalyzed = 0;

    public TagService(TagRequest tagRequests, ContributorRequest contributorsRequests, SseController sseController) {
        Objects.requireNonNull(tagRequests);
        Objects.requireNonNull(sseController);
        this.contributorsRequests = contributorsRequests;
        this.tagRequests = tagRequests;
        this.sseController = sseController;
    }

    /**
     * Checks if a tag already exists in the database.
     *
     * @param projectName Project name
     * @param tagId       Tag identifier
     * @return True if the tag exists, false otherwise
     */
    private boolean isTagExist(String projectName, String tagId) {
        for (TagStorage tag : tagRequests.findAll()) {
            if (tag.tagId().equals(tagId) && tag.projectName().equals(projectName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the name of a Git tag.
     *
     * @param tag Git tag reference
     * @return Tag name
     */
    public static String getTagName(Ref tag) {
        Pattern pattern = Pattern.compile("Ref\\[([^=]+)=.*\\]");
        var matcher = pattern.matcher(tag.toString());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Inserts a tag into the database along with contributor information.
     *
     * @param tag      Git tag reference
     * @param projectName Project name
     * @throws IOException If an error occurs while analyzing contributors
     */
    private void insertATagInDatabase(Ref tag, String projectName) throws IOException {
        String tagId = getTagName(tag);
        if (!isTagExist(projectName, tagId)) {
            try {
                getAllContributors(new FileExtractor(tagId, git), new TagStorage(tagId, projectName), tagId, projectName);
            } catch (IOException e) {
                throw new RuntimeException("Error: Inserting a tag in database", e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Analyzes all contributors for a specific tag.
     *
     * @param filesExtractor File extractor object
     * @param storage        Tag storage object
     * @param tagId          Tag identifier
     * @param projectName    Project name
     * @throws InterruptedException If an error occurs while analyzing contributors
     */
    private void getAllContributors(FileExtractor filesExtractor, TagStorage storage, String tagId, String projectName) throws InterruptedException {
        Map<String, Contributor> contributors = filesExtractor.analyzeAllContributors();
        List<ContributorStorage> contributorStorages = collectContributorStorages(contributors, tagId, projectName);
        contributorsRequests.saveAll(contributorStorages);
        updateProgressAndSaveTag(projectName, storage);
    }

    /**
     * Collects contributor storage objects from the analyzed contributors.
     *
     * @param contributors Map of contributors
     * @param tagId        Tag identifier
     * @param projectName Project name
     * @return List of contributor storage objects
     */
    public static List<ContributorStorage> collectContributorStorages(Map<String, Contributor> contributors, String tagId, String projectName) {
        if (contributors == null) {
            return new ArrayList<>();
        }
        return contributors.entrySet().stream()
                .flatMap(entry -> entry.getValue().getContributions().entrySet().stream()
                        .map(languageEntry -> new ContributorStorage(entry.getKey(), languageEntry.getKey(), tagId.replace("refs/tags/", ""), languageEntry.getValue(), projectName)))
                .collect(Collectors.toList());
    }

    /**
     * Updates progress and saves the tag in the database.
     *
     * @param projectName Project name
     * @param storage     Tag storage object
     */
    private void updateProgressAndSaveTag(String projectName, TagStorage storage) {
        tagAnalyzed++;
        sseController.sendProgress(new Progress("progress", getNumberOfTags(projectName), tagAnalyzed));
        tagRequests.save(storage);
    }

    /**
     * Inserts all tags of a project into the database.
     *
     * @param projectName Project name
     */
    public void insertTagsInDatabase(String projectName) {
        try {
            setTagAnalyzed(projectName);
            if (!getRepositoryPath(projectName).toFile().exists()) throw new RuntimeException("Error: Repository does not exist");
            git = Git.open(getRepositoryPath(projectName).toFile());
            List<Ref> tags = git.tagList().call();
            for (var tag : tags) {
                insertATagInDatabase(tag, projectName);
            }
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Error: Inserting tags in database", e);
        } finally {
            closeGit();
        }
    }

    /**
     * Retrieves the number of tags analyzed for a specific project.
     *
     * @param projectName Project name
     */
    private void setTagAnalyzed(String projectName) {
        for (TagStorage tag : tagRequests.findAll()) {
            if (tag.projectName().equals(projectName)) {
                tagAnalyzed++;
            }
        }
    }

    /**
     * Retrieves the number of tags analyzed for a specific project.
     *
     * @param projectName Project name
     * @return Number of tags analyzed
     */
    public static Path getRepositoryPath(String projectName) {
        String tempDir = System.getProperty("java.io.tmpdir");
        return Paths.get(tempDir, "gitclout_tmp", projectName);
    }

    /**
     * Closes the Git instance.
     */
    private void closeGit() {
        if (git != null) {
            git.close();
            git = null;
        }
    }

    /**
     * Retrieves contributors for a specific tag.
     *
     * @param projectName Project name
     * @param tagId       Tag identifier
     * @return Flux of contributors
     */
    public Flux<ContributorData> getContributors(String projectName, String tagId) {
        return Mono.fromCallable(() -> {
                    Map<String, Map<String, Integer>> contributorsMap = new HashMap<>();
                    for (var contributor : contributorsRequests.findAll()) {
                        if (contributor.tagId().equals(tagId) && contributor.projectName().equals(projectName)) {
                            contributorsMap.computeIfAbsent(contributor.contributorName(), k -> new HashMap<>()).merge(contributor.languageName(), contributor.numberOfLines(), Integer::sum);
                        }
                    }
                    return contributorsMap;
                })
                .subscribeOn(Schedulers.boundedElastic()).flatMapMany(map -> Flux.fromIterable(map.entrySet())).map(entry -> new ContributorData(entry.getKey(), entry.getValue())).onErrorResume(e -> Flux.error(new RuntimeException("Error retrieving contributors: " + e.getMessage())));
    }


    /**
     * Retrieves the number of tags for a specific project.
     *
     * @param projectName Project name
     * @return Number of tags
     */
    private int getNumberOfTags(String projectName) {
        try {
            Path repositoryPath = getRepositoryPath(projectName);
            git = Git.open(repositoryPath.toFile());
            return git.tagList().call().size();
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Error: Getting number of tags", e);
        }
    }

    /**
     * Checks if a repository exists based on its identifier.
     *
     * @param repositoryId Repository identifier
     * @return True if the repository exists, false otherwise
     */
    private static boolean isRepositoryExist(String repositoryId) {
        Path repositoryPath = getRepositoryPath(repositoryId);
        return repositoryPath.toFile().exists();
    }

    /**
     * Retrieves all tags for a specific repository.
     *
     * @param repositoryId Repository identifier
     * @return Flux of TagData
     */
    public Flux<TagData> getTags(String repositoryId) {
        if (!isRepositoryExist(repositoryId)) return Flux.empty();
        try {
            Path repositoryPath = getRepositoryPath(repositoryId);
            git = Git.open(repositoryPath.toFile());
            return Flux.fromIterable(git.tagList().call()).map(tagRef -> new TagData(tagRef.getName(), tagRef.getName().replace("refs/tags/", "")));
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException("Error: Getting tags", e);
        } finally {
            closeGit();
        }
    }

    /**
     * Retrieves the number of contributors for a specific tag.
     * @param repositoryId
     * @param tagId
     * @param number
     * @return
     */
    public Flux<VariationData> getContributorsHistory(String repositoryId, String tagId, int number) {
        if (!isRepositoryExist(repositoryId)) return Flux.empty();
        try {
            List<Ref> recentTags = getRecentTags(repositoryId, tagId, number);
            if (isTagAbsent(recentTags, tagId)) return Flux.empty();
            Map<String, List<Integer>> contributorContributions = calculateContributions(repositoryId, recentTags);
            Map<String, Integer> averageContributions = calculateAverageContributions(contributorContributions);
            Map<String, Integer> currentTagContributions = getContributionsForTag(repositoryId, tagId);
            return createVariationDataFlux(contributorContributions, averageContributions, currentTagContributions);
        } catch (Exception e) {
            return Flux.error(new RuntimeException("Error while retrieving contributors history: " + e.getMessage()));
        }
    }

    /**
     * Checks if a tag is absent from the database.
     * @param recentTags
     * @param tagId
     * @return
     */
    private boolean isTagAbsent(List<Ref> recentTags, String tagId) {
        return recentTags.stream().noneMatch(tag -> tag.getName().endsWith(tagId));
    }

    /**
     * Calculates the contributions for each contributor.
     * @param repositoryId
     * @param recentTags
     * @return
     */
    private Map<String, List<Integer>> calculateContributions(String repositoryId, List<Ref> recentTags) {
        Map<String, List<Integer>> contributorContributions = new HashMap<>();
        for (Ref tag : recentTags) {
            String currentTagId = tag.getName().replace("refs/tags/", "");
            Map<String, Integer> contributions = getContributionsForTag(repositoryId, currentTagId);
            contributions.forEach((contributor, lines) ->
                    contributorContributions.computeIfAbsent(contributor, k -> new ArrayList<>()).add(lines));
        }
        return contributorContributions;
    }

    /**
     * Calculates the average contributions for each contributor.
     * @param contributorContributions
     * @return
     */
    private Map<String, Integer> calculateAverageContributions(Map<String, List<Integer>> contributorContributions) {
        Map<String, Integer> averageContributions = new HashMap<>();
        contributorContributions.forEach((contributor, contributions) -> {
            if (contributions.size() <= 1) {
                averageContributions.put(contributor, 0);
                return;
            }
            int total = 0;
            for (int i = 1; i < contributions.size(); i++) total += contributions.get(i) - contributions.get(i - 1);
            averageContributions.put(contributor, total / (contributions.size() - 1));
        });
        return averageContributions;
    }

    /**
     * Creates a flux of variation data.
     * @param contributorContributions
     * @param averageContributions
     * @param currentTagContributions
     * @return
     */
    private Flux<VariationData> createVariationDataFlux(
            Map<String, List<Integer>> contributorContributions,
            Map<String, Integer> averageContributions,
            Map<String, Integer> currentTagContributions) {
        return Flux.fromIterable(currentTagContributions.entrySet())
                .map(entry -> new VariationData(entry.getKey(),
                        calculateVariation(contributorContributions, averageContributions, entry)));
    }

    /**
     * Calculates the variation for a specific contributor.
     * @param contributorContributions
     * @param averageContributions
     * @param entry
     * @return
     */
    private boolean calculateVariation(Map<String, List<Integer>> contributorContributions, Map<String, Integer> averageContributions, Map.Entry<String, Integer> entry) {
        List<Integer> contributions = contributorContributions.get(entry.getKey());
        int lastContribution = contributions.size() > 1 ? contributions.get(contributions.size() - 2) : 0;
        return entry.getValue() - lastContribution > averageContributions.getOrDefault(entry.getKey(), 0);
    }

    /**
     * Retrieves the recent tags for a specific tag.
     * @param repositoryId
     * @param tagId
     * @param number
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    private List<Ref> getRecentTags(String repositoryId, String tagId, int number) throws GitAPIException, IOException {
        Path repositoryPath = getRepositoryPath(repositoryId);
        git = Git.open(repositoryPath.toFile());
        List<Ref> tags = git.tagList().call();
        List<Ref> recentTags = new ArrayList<>();
        for (Ref tag : tags) {
            recentTags.add(tag);
            if (tagId.equals(tag.getName().replace("refs/tags/", ""))) {
                break;
            }
        }
        return recentTags.subList(Math.max(recentTags.size() - number, 0), recentTags.size());
    }

    /**
     * Retrieves the contributions for a specific tag.
     * @param repositoryId
     * @param tagId
     * @return
     */
    private Map<String, Integer> getContributionsForTag(String repositoryId, String tagId) {
        Map<String, Integer> contributions = new HashMap<>();
        for (ContributorStorage contributor : contributorsRequests.findAll()) {
            if (contributor.tagId().equals(tagId) && contributor.projectName().equals(repositoryId)) {
                contributions.merge(contributor.contributorName(), contributor.numberOfLines(), Integer::sum);
            }
        }
        return contributions;
    }
}
