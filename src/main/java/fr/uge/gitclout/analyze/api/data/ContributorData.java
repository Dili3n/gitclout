package fr.uge.gitclout.analyze.api.data;

import java.util.Map;

public record ContributorData(String name, Map<String, Integer> contributions) {
}
