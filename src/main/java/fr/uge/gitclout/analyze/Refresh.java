package fr.uge.gitclout.analyze;

import fr.uge.gitclout.analyze.jpa.ContributorRequest;
import fr.uge.gitclout.analyze.jpa.ContributorStorage;
import fr.uge.gitclout.tags.jpa.TagRequest;
import fr.uge.gitclout.tags.jpa.TagStorage;
import fr.uge.gitclout.tags.services.TagService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Refresh {

    private final String projectName;
    private final TagRequest tagRequests;
    private final ContributorRequest contributorsRequests;
    private Git git;

    public Refresh(String projectName, TagRequest tagRequests, ContributorRequest contributorRequests) {
        this.projectName = projectName;
        this.tagRequests = tagRequests;
        this.contributorsRequests = contributorRequests;
    }

    /**
     * Refreshes the tags of a project.
     */
    public void refreshTags() {
        try {
            Path repositoryPath = TagService.getRepositoryPath(projectName);
            git = Git.open(repositoryPath.toFile());
            var tags = git.tagList().call();
            for (var tag : tags) {
                if (!isTagExist(TagService.getTagName(tag))) insertATagInDatabaseByRefreshing(tag, projectName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error: Refreshing tags", e);
        }
    }

    /**
     * Checks if a tag already exists in the database.
     * @param id
     * @return
     */
    private boolean isTagExist(String id) {
        for (var tag : tagRequests.findAll()) {
            if (tag.tagId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inserts a tag in the database by refreshing.
     * @param tag
     * @param projectName
     */
    private void insertATagInDatabaseByRefreshing(Ref tag, String projectName) {
        try {
            insertTagData(tag, projectName);
            insertContributorData(tag, projectName);
        } catch (IOException e) {
            throw new RuntimeException("Error: Inserting a tag in the database by refreshing", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Inserts tag data in the database.
     * @param tag
     * @param projectName
     * @throws IOException
     */
    private void insertTagData(Ref tag, String projectName) throws IOException {
        var tagId = tag.getObjectId().getName();
        var tagData = new TagStorage(tagId, projectName);
        tagRequests.save(tagData);
    }

    /**
     * Inserts contributor data in the database.
     * @param tag
     * @param projectName
     * @throws IOException
     * @throws InterruptedException
     */
    private void insertContributorData(Ref tag, String projectName) throws IOException, InterruptedException {
        var tagId = tag.getObjectId().getName();
        var filesExtractor = new FileExtractor(tagId, git);
        Map<String, Contributor> contributors = filesExtractor.analyzeAllContributors();
        List<ContributorStorage> contributorStorages = TagService.collectContributorStorages(contributors, tagId, projectName);
        contributorsRequests.saveAll(contributorStorages);
    }
}
