package fr.uge.gitclout.analyze;

import java.util.HashMap;
import java.util.Map;

public record Contributor(String name, HashMap<String, Integer> contributions) {


    public Contributor(String name) {
        this(name, new HashMap<>());
    }

    public HashMap<String, Integer> getContributions() {
        return contributions;
    }
}
