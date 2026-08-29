package com.doodle.doodlecodingchallenge.calendar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doodle.doodlecodingchallenge.calendar.dto.CalendarEntryDto;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.meeting.MeetingRepository;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotRepository;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@Service
public class CalendarService {

    private final SlotRepository slots;
    private final UserRepository users;
    private final MeetingRepository meetings;

    public CalendarService(SlotRepository slots, UserRepository users, MeetingRepository meetings) {
        this.slots = slots;
        this.users = users;
        this.meetings = meetings;
    }

    @Transactional(readOnly = true)
    public List<CalendarEntryDto> view(UUID userId, Instant from, Instant to,
                                       Optional<SlotStatus> status) {
        users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));

        List<Slot> own = slots.findOverlappingWithMeeting(userId, from, to);
        List<CalendarEntryDto> entries = new ArrayList<>();
        boolean includeFree = status.isEmpty() || status.get() == SlotStatus.FREE;
        boolean includeBusy = status.isEmpty() || status.get() == SlotStatus.BUSY;

        if (includeFree) {
            own.stream().filter(s -> s.getStatus() == SlotStatus.FREE)
                .forEach(s -> entries.add(CalendarEntryDto.from(s)));
        }
        if (includeBusy) {
            own.stream().filter(s -> s.getStatus() == SlotStatus.BUSY)
                .forEach(s -> entries.add(CalendarEntryDto.from(s)));
            meetings.findMeetingsAttended(userId, from, to).stream()
                .filter(m -> !m.getSlot().getOwner().getId().equals(userId))
                .forEach(m -> entries.add(CalendarEntryDto.from(m)));
        }
        return entries.stream().sorted(Comparator.comparing(CalendarEntryDto::start)).toList();
    }
}
