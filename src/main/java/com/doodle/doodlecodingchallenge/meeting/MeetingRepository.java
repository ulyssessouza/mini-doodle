package com.doodle.doodlecodingchallenge.meeting;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    @Query("""
        select distinct m from Meeting m
        join fetch m.participants
        where m.id = :id
        """)
    Optional<Meeting> findByIdWithParticipants(@Param("id") UUID id);

    @Query("""
        select m.id from Meeting m
        join m.participants p
        where lower(p.email) = lower(:email)
        """)
    Page<UUID> findIdsByParticipantEmail(@Param("email") String email, Pageable pageable);

    @Query("""
        select distinct m from Meeting m
        join fetch m.participants
        join fetch m.organizer
        join fetch m.slot
        where m.id in :ids
        """)
    List<Meeting> findAllWithParticipantsById(@Param("ids") Collection<UUID> ids);

    @Query("""
        select distinct m from Meeting m
        join fetch m.slot
        join m.participants p
        where p.user.id = :userId
          and m.slot.startsAt < :to
          and m.slot.endsAt > :from
        """)
    List<Meeting> findMeetingsAttended(@Param("userId") UUID userId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);
}
