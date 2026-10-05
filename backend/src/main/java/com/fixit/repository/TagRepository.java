package com.fixit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fixit.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    Optional<Tag> findByNameIgnoreCase(String name);

    java.util.List<Tag> findAllByOrderByNameAsc();
}
