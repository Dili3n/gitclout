package fr.uge.gitclout.tags.api;

import fr.uge.gitclout.analyze.api.data.ContributorData;
import fr.uge.gitclout.tags.api.data.TagData;
import fr.uge.gitclout.tags.api.data.VariationData;
import fr.uge.gitclout.tags.services.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class TagController {

    private final TagService tags;

    public TagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping(path="/repositories/tags")
    public Flux<TagData> getTags(String repositoryId) {
        return tags.getTags(repositoryId);
    }

    @GetMapping("/repositories/tags/contributors")
    public Flux<ContributorData> getContributors(String repositoryId, String tagId) {
        return tags.getContributors(repositoryId, tagId);
    }

    @GetMapping("/repositories/tags/contributors/history")
    public Flux<VariationData> getContributorsHistory(String repositoryId, String tagId, int number) {
        return tags.getContributorsHistory(repositoryId, tagId, number);
    }
}
