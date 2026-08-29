package com.doodle.doodlecodingchallenge.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.doodle.doodlecodingchallenge.calendar.dto.CalendarEntryDto;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.meeting.Meeting;
import com.doodle.doodlecodingchallenge.meeting.MeetingParticipant;
import com.doodle.doodlecodingchallenge.meeting.MeetingRepository;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotRepository;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import com.doodle.doodlecodingchallenge.user.User;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    SlotRepository slots;

    @Mock
    UserRepository users;

    @Mock
    MeetingRepository meetings;

    CalendarService service;
    User alice;
    User bob;
    Instant from = Instant.parse("2026-09-01T00:00:00Z");
    Instant to = Instant.parse("2026-09-02T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new CalendarService(slots, users, meetings);
        alice = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.now());
        bob = new User(UUID.randomUUID(), "Bob", "bob@example.com", Instant.now());
    }

    @Test
    void unknownUserThrows() {
        when(users.findById(alice.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(alice.getId(), from, to, Optional.empty()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void busyViewContainsOwnMeetingsManualBusyAndAttendedMeetings() {
        Slot freeSlot = new Slot(UUID.randomUUID(), alice,
            Instant.parse("2026-09-01T09:00:00Z"), Instant.parse("2026-09-01T09:30:00Z"));
        Slot manualBusy = new Slot(UUID.randomUUID(), alice,
            Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-01T12:30:00Z"));
        manualBusy.setStatus(SlotStatus.BUSY);
        Slot ownMeetingSlot = new Slot(UUID.randomUUID(), alice,
            Instant.parse("2026-09-01T13:00:00Z"), Instant.parse("2026-09-01T14:00:00Z"));
        Meeting ownMeeting = new Meeting(UUID.randomUUID(), "Sync", null, alice, ownMeetingSlot, Instant.now());
        ownMeetingSlot.linkMeeting(ownMeeting);

        Slot bobsSlot = new Slot(UUID.randomUUID(), bob,
            Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"));
        Meeting attended = new Meeting(UUID.randomUUID(), "Review", null, bob, bobsSlot, Instant.now());
        attended.addParticipant(new MeetingParticipant(UUID.randomUUID(), "Alice", "alice@example.com", alice));
        bobsSlot.linkMeeting(attended);

        when(users.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(slots.findOverlappingWithMeeting(alice.getId(), from, to))
            .thenReturn(List.of(freeSlot, manualBusy, ownMeetingSlot));
        when(meetings.findMeetingsAttended(alice.getId(), from, to)).thenReturn(List.of(attended));

        List<CalendarEntryDto> busy = service.view(alice.getId(), from, to, Optional.of(SlotStatus.BUSY));
        List<CalendarEntryDto> free = service.view(alice.getId(), from, to, Optional.of(SlotStatus.FREE));
        List<CalendarEntryDto> all = service.view(alice.getId(), from, to, Optional.empty());

        assertThat(busy).extracting(CalendarEntryDto::title)
            .containsExactlyInAnyOrder("Sync", "Review", null);
        assertThat(busy).allSatisfy(e -> assertThat(e.status()).isEqualTo(SlotStatus.BUSY));
        assertThat(free).hasSize(1);
        assertThat(free.get(0).meetingId()).isNull();
        assertThat(all).hasSize(4);
        assertThat(all).isSortedAccordingTo(Comparator.comparing(CalendarEntryDto::start));
    }
}
