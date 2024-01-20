package fr.uge.gitclout.analyze.api;

import fr.uge.gitclout.analyze.api.data.LanguageData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import fr.uge.gitclout.analyze.service.LanguageService;
import reactor.core.publisher.Flux;

@RestController
public class LanguageController {

    private final LanguageService languages;

    public LanguageController(LanguageService languages) {
        this.languages = languages;
    }

    @GetMapping("/analyze/language")
    public Flux<LanguageData> getAllLanguages() {
        return languages.getAllLanguages();
    }
}
