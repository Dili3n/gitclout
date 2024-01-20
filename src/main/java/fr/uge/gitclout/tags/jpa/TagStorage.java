package fr.uge.gitclout.tags.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class TagStorage {

    @Id
    @GeneratedValue
    private Long id;

    private String tagId;

    private String projectName;

   protected TagStorage() {
   }

    public TagStorage(String tagId, String projectName) {
        this.tagId = tagId;
        this.projectName = projectName;;
    }

    public Long Id() {
        return id;
    }

    public String tagId() {
        return tagId;
    }

    public String projectName() {
        return projectName;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
