package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /**
     * Find the last N password hashes for a member, ordered by most recent first.
     */
    List<PasswordHistory> findTop5ByMember_IdOrderByChangedAtDesc(Long memberId);

    /**
     * Find all password history entries for a member.
     */
    List<PasswordHistory> findByMember_IdOrderByChangedAtDesc(Long memberId);

    /**
     * Count password history entries for a member.
     */
    long countByMember_Id(Long memberId);

    /**
     * Delete oldest entries keeping only the most recent N entries.
     * Used to maintain only last 5 passwords.
     */
    @Modifying
    @Query(value = "DELETE FROM password_history WHERE member_id = :memberId " +
           "AND id NOT IN (SELECT id FROM (SELECT id FROM password_history " +
           "WHERE member_id = :memberId ORDER BY changed_at DESC LIMIT 5) AS recent)", 
           nativeQuery = true)
    void deleteOldEntriesKeepingLast5(@Param("memberId") Long memberId);
}
