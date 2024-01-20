package fr.uge.gitclout.analyze.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contributors")
public class ContributorStorage {

    @Id
    @GeneratedValue
    private Long id;

    private String contributorName;

    private String languageName;

    private String tagId;

    private String projectName;

    private int numberOfLines;

    protected ContributorStorage() {
    }

    public ContributorStorage(String contributorName, String languageName, String tagId, int numberOfLines, String projectName) {
        this.contributorName = contributorName;
        this.languageName = languageName;
        this.tagId = tagId;
        this.numberOfLines = numberOfLines;
        this.projectName = projectName;
    }

    public Long Id() {
        return id;
    }

    public String contributorName() {
        return contributorName;
    }

    public String languageName() {
        return languageName;
    }

    public String tagId() {
        return tagId;
    }

    public String projectName() {
        return projectName;
    }

    public int numberOfLines() {
        return numberOfLines;
    }

}
