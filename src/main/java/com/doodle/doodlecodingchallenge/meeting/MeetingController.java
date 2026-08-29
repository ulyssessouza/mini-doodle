package com.doodle.doodlecodingchallenge.meeting;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @GetMapping("/{id}")
    MeetingDto get(@PathVariable UUID id) {
        return meetingService.get(id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> cancel(@PathVariable UUID id) {
        meetingService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    Page<MeetingDto> byParticipant(@RequestParam String participant,
                                   @PageableDefault(size = 20, sort = "createdAt",
                                       direction = Sort.Direction.DESC) Pageable pageable) {
        return meetingService.findByParticipant(participant, pageable);
    }
}
