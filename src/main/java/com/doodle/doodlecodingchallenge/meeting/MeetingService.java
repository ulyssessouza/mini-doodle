package com.doodle.doodlecodingchallenge.meeting;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.common.PageResponse;
import com.doodle.doodlecodingchallenge.meeting.dto.BookRequest;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantRequest;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotRepository;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@Service
public class MeetingService {

    private final SlotRepository slots;
    private final UserRepository users;
    private final MeetingRepository meetings;

    public MeetingService(SlotRepository slots, UserRepository users, MeetingRepository meetings) {
        this.slots = slots;
        this.users = users;
        this.meetings = meetings;
    }

    @Transactional
    public MeetingDto book(UUID slotId, BookRequest request) {
        Slot slot = slots.findById(slotId)
            .orElseThrow(() -> NotFoundException.of("Slot", slotId));
        if (slot.getStatus() != SlotStatus.FREE) {
            throw new ConflictException(
                "Slot %s is not available (status %s)".formatted(slotId, slot.getStatus()));
        }

        Set<String> seen = new HashSet<>();
        for (ParticipantRequest participant : request.participants()) {
            if (!seen.add(participant.email().toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("Duplicate participant email in request");
            }
        }

        List<MeetingParticipant> participants = request.participants().stream()
            .map(p -> new MeetingParticipant(UUID.randomUUID(), p.name(), p.email(),
                users.findByEmailIgnoreCase(p.email()).orElse(null)))
            .toList();

        TreeSet<UUID> involvedUserIds = new TreeSet<>();
        involvedUserIds.add(slot.getOwner().getId());
        participants.stream()
            .map(MeetingParticipant::getUser)
            .filter(Objects::nonNull)
            .forEach(u -> involvedUserIds.add(u.getId()));

        users.findAllByIdForUpdate(involvedUserIds);

        List<Slot> conflicts = slots.findOverlappingForUpdate(
            involvedUserIds, SlotStatus.BUSY, slot.getStartsAt(), slot.getEndsAt());
        if (!conflicts.isEmpty()) {
            String who = conflicts.stream()
                .map(c -> c.getOwner().getEmail())
                .distinct()
                .collect(Collectors.joining(", "));
            throw new ConflictException("Time range overlaps existing busy time for: " + who);
        }

        Meeting meeting = new Meeting(UUID.randomUUID(), request.title(), request.description(),
            slot.getOwner(), slot, Instant.now());
        participants.forEach(meeting::addParticipant);
        slot.linkMeeting(meeting);
        meetings.save(meeting);
        return MeetingDto.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingDto get(UUID id) {
        return meetings.findByIdWithParticipants(id)
            .map(MeetingDto::from)
            .orElseThrow(() -> NotFoundException.of("Meeting", id));
    }

    @Transactional
    public void cancel(UUID id) {
        Meeting meeting = meetings.findByIdWithParticipants(id)
            .orElseThrow(() -> NotFoundException.of("Meeting", id));
        Slot slot = meeting.getSlot();
        slot.unlinkMeeting();
        meetings.delete(meeting);
    }

    @Transactional(readOnly = true)
    public PageResponse<MeetingDto> findByParticipant(String email, Pageable pageable) {
        Page<UUID> ids = meetings.findIdsByParticipantEmail(email, pageable);
        if (ids.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }
        Map<UUID, Meeting> byId = meetings.findAllWithParticipantsById(ids.getContent())
            .stream()
            .collect(Collectors.toMap(Meeting::getId, m -> m));
        List<MeetingDto> content = ids.getContent().stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .map(MeetingDto::from)
            .toList();
        return new PageResponse<>(content, ids.getNumber(), ids.getSize(),
            ids.getTotalElements(), ids.getTotalPages());
    }
}
