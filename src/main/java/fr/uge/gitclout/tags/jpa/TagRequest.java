package fr.uge.gitclout.tags.jpa;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRequest extends CrudRepository<TagStorage, Long>{
}
