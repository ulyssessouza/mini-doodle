package com.doodle.doodlecodingchallenge.slot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.slot.dto.CreateSlotRequest;
import com.doodle.doodlecodingchallenge.slot.dto.SlotDto;
import com.doodle.doodlecodingchallenge.slot.dto.UpdateSlotRequest;
import com.doodle.doodlecodingchallenge.user.User;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@Service
public class SlotService {

    private final SlotRepository slots;
    private final UserRepository users;

    public SlotService(SlotRepository slots, UserRepository users) {
        this.slots = slots;
        this.users = users;
    }

    @Transactional
    public SlotDto create(UUID ownerId, CreateSlotRequest request) {
        validateRange(request.start(), request.end());
        User owner = users.findById(ownerId)
            .orElseThrow(() -> NotFoundException.of("User", ownerId));
        Slot slot = new Slot(UUID.randomUUID(), owner, request.start(), request.end());
        return SlotDto.from(slots.save(slot));
    }

    @Transactional(readOnly = true)
    public Page<SlotDto> list(UUID ownerId, Instant from, Instant to,
                              Optional<SlotStatus> status, Pageable pageable) {
        Page<Slot> page = status
            .map(s -> slots.findByOwnerIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThan(
                ownerId, s, from, to, pageable))
            .orElseGet(() -> slots.findByOwnerIdAndEndsAtGreaterThanAndStartsAtLessThan(
                ownerId, from, to, pageable));
        return page.map(SlotDto::from);
    }

    @Transactional(readOnly = true)
    public SlotDto get(UUID slotId) {
        return SlotDto.from(getEntity(slotId));
    }

    @Transactional
    public SlotDto update(UUID slotId, UpdateSlotRequest request) {
        Slot slot = getEntity(slotId);
        if (request.start() != null || request.end() != null) {
            requireFree(slot, "only free slots can be rescheduled");
            Instant newStart = request.start() != null ? request.start() : slot.getStartsAt();
            Instant newEnd = request.end() != null ? request.end() : slot.getEndsAt();
            validateRange(newStart, newEnd);
            slot.setTimes(newStart, newEnd);
        }
        if (request.status() != null) {
            switch (request.status()) {
                case BUSY -> {
                    if (slot.getStatus() == SlotStatus.FREE) {
                        slot.setStatus(SlotStatus.BUSY);
                    }
                }
                case FREE -> {
                    if (slot.getMeeting() != null) {
                        throw new ConflictException(
                            "Slot %s is booked as meeting %s; cancel the meeting to free it"
                                .formatted(slotId, slot.getMeeting().getId()));
                    }
                    if (slot.getStatus() == SlotStatus.BUSY) {
                        slot.setStatus(SlotStatus.FREE);
                    }
                }
            }
        }
        return SlotDto.from(slot);
    }

    @Transactional
    public void delete(UUID slotId) {
        Slot slot = getEntity(slotId);
        if (slot.getStatus() != SlotStatus.FREE) {
            throw new ConflictException(
                "Slot %s is busy and cannot be deleted; free it or cancel its meeting first".formatted(slotId));
        }
        slots.delete(slot);
    }

    private Slot getEntity(UUID slotId) {
        return slots.findById(slotId)
            .orElseThrow(() -> NotFoundException.of("Slot", slotId));
    }

    private static void requireFree(Slot slot, String action) {
        if (slot.getStatus() != SlotStatus.FREE) {
            throw new ConflictException("Slot %s is busy; %s".formatted(slot.getId(), action));
        }
    }

    private static void validateRange(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new InvalidRequestException("end must be after start");
        }
    }
}
