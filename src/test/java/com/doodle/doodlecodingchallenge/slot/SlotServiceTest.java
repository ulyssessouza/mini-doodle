package com.doodle.doodlecodingchallenge.slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.meeting.Meeting;
import com.doodle.doodlecodingchallenge.slot.dto.CreateSlotRequest;
import com.doodle.doodlecodingchallenge.slot.dto.SlotDto;
import com.doodle.doodlecodingchallenge.slot.dto.UpdateSlotRequest;
import com.doodle.doodlecodingchallenge.user.User;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock
    SlotRepository slots;

    @Mock
    UserRepository users;

    SlotService service;
    User owner;
    Instant start = Instant.parse("2026-09-01T10:00:00Z");
    Instant end = Instant.parse("2026-09-01T11:00:00Z");

    @BeforeEach
    void setUp() {
        service = new SlotService(slots, users);
        owner = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.now());
    }

    @Test
    void createValidatesRange() {
        assertThatThrownBy(() -> service.create(owner.getId(), new CreateSlotRequest(end, start)))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("end must be after start");
    }

    @Test
    void createRejectsUnknownOwner() {
        when(users.findById(owner.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(owner.getId(), new CreateSlotRequest(start, end)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createReturnsFreeSlot() {
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(slots.save(any(Slot.class))).thenAnswer(inv -> inv.getArgument(0));

        SlotDto dto = service.create(owner.getId(), new CreateSlotRequest(start, end));

        assertThat(dto.status()).isEqualTo(SlotStatus.FREE);
        assertThat(dto.meetingId()).isNull();
        verify(slots).save(any(Slot.class));
    }

    @Test
    void getReturnsMappedSlot() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        SlotDto dto = service.get(slot.getId());

        assertThat(dto.id()).isEqualTo(slot.getId());
        assertThat(dto.ownerId()).isEqualTo(owner.getId());
        assertThat(dto.start()).isEqualTo(start);
        assertThat(dto.end()).isEqualTo(end);
        assertThat(dto.status()).isEqualTo(SlotStatus.FREE);
        assertThat(dto.meetingId()).isNull();
    }

    @Test
    void listDelegatesToStatusAwareQueryWhenStatusPresent() {
        when(slots.findByOwnerIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThan(
                any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        service.list(owner.getId(), start, end, Optional.of(SlotStatus.BUSY), PageRequest.of(0, 50));

        verify(slots).findByOwnerIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThan(
            org.mockito.ArgumentMatchers.eq(owner.getId()), org.mockito.ArgumentMatchers.eq(SlotStatus.BUSY),
            org.mockito.ArgumentMatchers.eq(start), org.mockito.ArgumentMatchers.eq(end),
            org.mockito.ArgumentMatchers.any(PageRequest.class));
    }

    @Test
    void listDelegatesToPlainQueryWithoutStatus() {
        when(slots.findByOwnerIdAndEndsAtGreaterThanAndStartsAtLessThan(
                any(), any(), any(), any()))
            .thenReturn(Page.empty());

        service.list(owner.getId(), start, end, Optional.empty(), PageRequest.of(0, 50));

        verify(slots).findByOwnerIdAndEndsAtGreaterThanAndStartsAtLessThan(
            org.mockito.ArgumentMatchers.eq(owner.getId()),
            org.mockito.ArgumentMatchers.eq(start),
            org.mockito.ArgumentMatchers.eq(end),
            org.mockito.ArgumentMatchers.any(PageRequest.class));
    }

    @Test
    void reschedulesFreeSlot() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        SlotDto updated = service.update(slot.getId(),
            new UpdateSlotRequest(start.plusSeconds(3600), end.plusSeconds(3600), null));

        assertThat(updated.start()).isEqualTo(start.plusSeconds(3600));
        assertThat(updated.end()).isEqualTo(end.plusSeconds(3600));
        assertThat(updated.status()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    void rescheduleOfBusySlotRejected() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        slot.setStatus(SlotStatus.BUSY);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.update(slot.getId(),
            new UpdateSlotRequest(start.plusSeconds(3600), null, null)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("only free slots can be rescheduled");
    }

    @Test
    void markFreeRejectedWhenMeetingLinked() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        slot.linkMeeting(new Meeting(UUID.randomUUID(), "Standup", null, owner, slot, Instant.now()));
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.update(slot.getId(),
            new UpdateSlotRequest(null, null, SlotStatus.FREE)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("cancel the meeting");
    }

    @Test
    void rescheduleRejectedWhenMeetingLinked() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        slot.linkMeeting(new Meeting(UUID.randomUUID(), "Standup", null, owner, slot, Instant.now()));
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.update(slot.getId(),
            new UpdateSlotRequest(start.plusSeconds(3600), end.plusSeconds(3600), null)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("only free slots can be rescheduled");
    }

    @Test
    void manualBusyThenFreeRoundTrip() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        service.update(slot.getId(), new UpdateSlotRequest(null, null, SlotStatus.BUSY));
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BUSY);

        service.update(slot.getId(), new UpdateSlotRequest(null, null, SlotStatus.FREE));
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    void partialUpdateValidatesRange() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.update(slot.getId(),
            new UpdateSlotRequest(Instant.parse("2026-09-01T11:30:00Z"), null, null)))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("end must be after start");
    }

    @Test
    void deleteFreeSlotSucceeds() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        service.delete(slot.getId());

        verify(slots).delete(slot);
    }

    @Test
    void deleteBusySlotRejected() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        slot.setStatus(SlotStatus.BUSY);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.delete(slot.getId()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("cannot be deleted");
    }
}
