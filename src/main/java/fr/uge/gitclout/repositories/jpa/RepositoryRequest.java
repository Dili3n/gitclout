package fr.uge.gitclout.repositories.jpa;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface RepositoryRequest extends CrudRepository<RepositoryStorage, Long> {
}
