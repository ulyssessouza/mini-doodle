package com.doodle.doodlecodingchallenge.slot;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.common.PageResponse;
import com.doodle.doodlecodingchallenge.meeting.MeetingService;
import com.doodle.doodlecodingchallenge.meeting.dto.BookRequest;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.slot.dto.CreateSlotRequest;
import com.doodle.doodlecodingchallenge.slot.dto.SlotDto;
import com.doodle.doodlecodingchallenge.slot.dto.UpdateSlotRequest;

@RestController
public class SlotController {

    private final SlotService slotService;
    private final MeetingService meetingService;

    public SlotController(SlotService slotService, MeetingService meetingService) {
        this.slotService = slotService;
        this.meetingService = meetingService;
    }

    @PostMapping("/api/v1/users/{userId}/slots")
    ResponseEntity<SlotDto> create(@PathVariable UUID userId,
                                   @Valid @RequestBody CreateSlotRequest request) {
        SlotDto created = slotService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/slots/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/users/{userId}/slots")
    PageResponse<SlotDto> list(@PathVariable UUID userId,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                       @RequestParam Optional<SlotStatus> status,
                       @PageableDefault(size = 50, sort = "startsAt") @ParameterObject Pageable pageable) {
        return slotService.list(userId, from, to, status, pageable);
    }

    @GetMapping("/api/v1/slots/{slotId}")
    SlotDto get(@PathVariable UUID slotId) {
        return slotService.get(slotId);
    }

    @PatchMapping("/api/v1/slots/{slotId}")
    SlotDto update(@PathVariable UUID slotId, @RequestBody UpdateSlotRequest request) {
        return slotService.update(slotId, request);
    }

    @DeleteMapping("/api/v1/slots/{slotId}")
    ResponseEntity<Void> delete(@PathVariable UUID slotId) {
        slotService.delete(slotId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/slots/{slotId}/book")
    ResponseEntity<MeetingDto> book(@PathVariable UUID slotId,
                                    @Valid @RequestBody BookRequest request) {
        MeetingDto meeting = meetingService.book(slotId, request);
        return ResponseEntity.created(URI.create("/api/v1/meetings/" + meeting.id())).body(meeting);
    }
}
