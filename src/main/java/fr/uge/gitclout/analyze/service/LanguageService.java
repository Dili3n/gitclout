package fr.uge.gitclout.analyze.service;

import fr.uge.gitclout.analyze.language.Language;
import org.springframework.stereotype.Service;
import fr.uge.gitclout.analyze.api.data.LanguageData;
import reactor.core.publisher.Flux;

@Service
public class LanguageService {

    public Flux<LanguageData> getAllLanguages() {
        return Flux.fromArray(Language.values())
                .map(lang -> new LanguageData(lang.getDisplayName(), lang.getColor()));
    }
}
