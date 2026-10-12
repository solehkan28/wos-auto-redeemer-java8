package com.wos.redeemer.repository;
import com.wos.redeemer.entity.Friends;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendsRepository extends JpaRepository<Friends, Long> {

    List<Friends> findByActiveTrue();
}
