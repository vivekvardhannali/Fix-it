package com.fixit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fixit.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySmail(String smail);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsBySmail(String smail);
}
