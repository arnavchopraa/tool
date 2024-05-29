package org.example.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.example.models.Submission;

@Repository
public interface SubmissionRepository extends CrudRepository<Submission, String> { }
