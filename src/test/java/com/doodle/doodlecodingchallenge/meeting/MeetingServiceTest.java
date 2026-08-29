package com.doodle.doodlecodingchallenge.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.meeting.dto.BookRequest;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantRequest;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotRepository;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import com.doodle.doodlecodingchallenge.user.User;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    SlotRepository slots;

    @Mock
    UserRepository users;

    @Mock
    MeetingRepository meetings;

    MeetingService service;

    User alice;
    User bob;
    Slot slot;
    Instant start = Instant.parse("2026-09-01T10:00:00Z");
    Instant end = Instant.parse("2026-09-01T11:00:00Z");

    @BeforeEach
    void setUp() {
        service = new MeetingService(slots, users, meetings);
        alice = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.now());
        bob = new User(UUID.randomUUID(), "Bob", "bob@example.com", Instant.now());
        slot = new Slot(UUID.randomUUID(), alice, start, end);
    }

    @Test
    void booksFreeSlotLinksItAndLocksInvolvedUsers() {
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));
        when(users.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(bob));
        when(users.findByEmailIgnoreCase("guest@example.com")).thenReturn(Optional.empty());
        when(slots.findOverlappingForUpdate(any(), any(), any(), any())).thenReturn(List.of());

        MeetingDto dto = service.book(slot.getId(), new BookRequest("Design sync", "weekly",
            List.of(new ParticipantRequest("Bob", "bob@example.com"),
                new ParticipantRequest("Guest", "guest@example.com"))));

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BUSY);
        assertThat(slot.getMeeting()).isNotNull();
        assertThat(dto.organizerId()).isEqualTo(alice.getId());
        assertThat(dto.participants()).hasSize(2);
        assertThat(dto.participants().get(0).userId()).isEqualTo(bob.getId());
        assertThat(dto.participants().get(1).userId()).isNull();

        ArgumentCaptor<Collection<UUID>> locked = ArgumentCaptor.forClass(Collection.class);
        verify(users).findAllByIdForUpdate(locked.capture());
        assertThat(locked.getValue()).containsExactlyInAnyOrder(alice.getId(), bob.getId());
        assertThat(locked.getValue()).isInstanceOf(SortedSet.class);
        assertThat(List.copyOf(locked.getValue())).isSorted();
        verify(slots).findOverlappingForUpdate(eq(locked.getValue()), eq(SlotStatus.BUSY), eq(start), eq(end));
    }

    @Test
    void bookingBusySlotRejected() {
        slot.linkMeeting(new Meeting(UUID.randomUUID(), "Taken", null, alice, slot, Instant.now()));
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.book(slot.getId(), new BookRequest("X", null,
            List.of(new ParticipantRequest("G", "g@x.com")))))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("not available");
    }

    @Test
    void overlappingBusyTimeOfRegisteredParticipantRejected() {
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));
        when(users.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(bob));
        when(slots.findOverlappingForUpdate(any(), any(), any(), any()))
            .thenReturn(List.of(busySlotOf(bob)));

        assertThatThrownBy(() -> service.book(slot.getId(), new BookRequest("Overlap", null,
            List.of(new ParticipantRequest("Bob", "bob@example.com")))))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("bob@example.com");
    }

    @Test
    void duplicateParticipantEmailRejected() {
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.book(slot.getId(), new BookRequest("Dup", null,
            List.of(new ParticipantRequest("A", "Bob@X.com"),
                    new ParticipantRequest("B", "bob@x.com")))))
            .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cancelFreesSlot() {
        Meeting meeting = new Meeting(UUID.randomUUID(), "Standup", null, alice, slot, Instant.now());
        slot.linkMeeting(meeting);
        when(meetings.findByIdWithParticipants(meeting.getId())).thenReturn(Optional.of(meeting));

        service.cancel(meeting.getId());

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.FREE);
        assertThat(slot.getMeeting()).isNull();
        verify(meetings).delete(meeting);
    }

    @Test
    void getUnknownMeetingThrows() {
        when(meetings.findByIdWithParticipants(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByParticipantPreservesPageOrderAndSkipsMissing() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Meeting first = new Meeting(id1, "Sync", null, alice, slot, Instant.now());
        when(meetings.findIdsByParticipantEmail(eq("bob@example.com"), any()))
            .thenReturn(new PageImpl<>(List.of(id1, id2), PageRequest.of(0, 20), 2));
        when(meetings.findAllWithParticipantsById(any())).thenReturn(List.of(first));

        var result = service.findByParticipant("bob@example.com", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(id1);
    }

    @Test
    void getReturnsMeetingWithParticipants() {
        Meeting meeting = new Meeting(UUID.randomUUID(), "Standup", "daily", alice, slot, Instant.now());
        meeting.addParticipant(new MeetingParticipant(UUID.randomUUID(), "Bob", "bob@example.com", bob));
        when(meetings.findByIdWithParticipants(meeting.getId())).thenReturn(Optional.of(meeting));

        MeetingDto dto = service.get(meeting.getId());

        assertThat(dto.id()).isEqualTo(meeting.getId());
        assertThat(dto.title()).isEqualTo("Standup");
        assertThat(dto.description()).isEqualTo("daily");
        assertThat(dto.organizerId()).isEqualTo(alice.getId());
        assertThat(dto.slotId()).isEqualTo(slot.getId());
        assertThat(dto.start()).isEqualTo(start);
        assertThat(dto.end()).isEqualTo(end);
        assertThat(dto.createdAt()).isEqualTo(meeting.getCreatedAt());
        assertThat(dto.participants()).hasSize(1);
        assertThat(dto.participants().get(0).name()).isEqualTo("Bob");
        assertThat(dto.participants().get(0).email()).isEqualTo("bob@example.com");
        assertThat(dto.participants().get(0).userId()).isEqualTo(bob.getId());
    }

    private Slot busySlotOf(User busyOwner) {
        Slot s = new Slot(UUID.randomUUID(), busyOwner, start, end);
        s.linkMeeting(new Meeting(UUID.randomUUID(), "Other", null, busyOwner, s, Instant.now()));
        return s;
    }
}
