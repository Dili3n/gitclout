package fr.uge.gitclout.analyze.jpa;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ContributorRequest extends CrudRepository<ContributorStorage, Long>{
}
