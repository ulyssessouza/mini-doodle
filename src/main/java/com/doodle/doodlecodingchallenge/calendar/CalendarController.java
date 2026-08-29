package com.doodle.doodlecodingchallenge.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.calendar.dto.CalendarEntryDto;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;

@RestController
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/api/v1/users/{userId}/calendar")
    List<CalendarEntryDto> view(@PathVariable UUID userId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                @RequestParam Optional<SlotStatus> status) {
        return calendarService.view(userId, from, to, status);
    }
}
