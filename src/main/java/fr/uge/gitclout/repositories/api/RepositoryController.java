package fr.uge.gitclout.repositories.api;

import fr.uge.gitclout.repositories.api.data.HistoryData;
import fr.uge.gitclout.repositories.api.data.RepositoryData;
import fr.uge.gitclout.repositories.services.RepositoryService;
import fr.uge.gitclout.tags.api.data.RefreshData;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class RepositoryController {

    private final RepositoryService repositories;

    public RepositoryController(RepositoryService repositories) {
        this.repositories = repositories;
    }

    @PostMapping("/addrepository")
    public Mono<RepositoryData> addRepository(@RequestBody String repository) {
        return repositories.addRepository(repository);
    }

    @GetMapping("/repositories/history")
    public Flux<HistoryData> getHistory() {
        return repositories.repositoryHistory();
    }

    @GetMapping("/repositories/tags/refreshtag")
    public Mono<RefreshData> refreshTags(String name) {
        return repositories.refreshTags(name);
    }

    @DeleteMapping("/repositories/delete")
    public Mono<Void> deleteRepository(String name) {
        return repositories.deleteRepository(name);
    }
}
