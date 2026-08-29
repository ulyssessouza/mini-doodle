package com.doodle.doodlecodingchallenge.slot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

    Page<Slot> findByOwnerIdAndEndsAtGreaterThanAndStartsAtLessThan(
        UUID ownerId, Instant from, Instant to, Pageable pageable);

    Page<Slot> findByOwnerIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThan(
        UUID ownerId, SlotStatus status, Instant from, Instant to, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s from Slot s
        where s.owner.id in :ownerIds
          and s.status = :status
          and s.startsAt < :to
          and s.endsAt > :from
        """)
    List<Slot> findOverlappingForUpdate(@Param("ownerIds") Collection<UUID> ownerIds,
                                        @Param("status") SlotStatus status,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Query("""
        select s from Slot s
        left join fetch s.meeting
        where s.owner.id = :ownerId
          and s.startsAt < :to
          and s.endsAt > :from
        """)
    List<Slot> findOverlappingWithMeeting(@Param("ownerId") UUID ownerId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);
}
