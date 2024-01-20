package fr.uge.gitclout.repositories.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "repositories")
public class RepositoryStorage {

    @Id
    @GeneratedValue
    private Long id;
    @Column(unique = true)
    private String repositoryUrl;

    private String projectName;

    private String repositoryPath;

    protected RepositoryStorage() {
    }

    public RepositoryStorage(String repositoryUrl, String projectName, String repositoryPath) {
        this.repositoryUrl = repositoryUrl;
        this.projectName = projectName;
        this.repositoryPath = repositoryPath;
    }

    public String repositoryUrl() {
        return repositoryUrl;
    }

    public String projectName() {
        return projectName;
    }

    public String repositoryPath() {
        return repositoryPath;
    }

    @Override
    public String toString() {
        return "RepositoriesStorage{" +
                "id=" + id +
                ", repositoryUrl='" + repositoryUrl + '\'' +
                ", projectName='" + projectName + '\'' +
                ", repositoryPath='" + repositoryPath + '\'' +
                '}';
    }

}
