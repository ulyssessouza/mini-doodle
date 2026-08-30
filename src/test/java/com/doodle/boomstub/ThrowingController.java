package com.doodle.boomstub;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.common.NotFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
public class ThrowingController {

    public record Req(@NotBlank String name) {
    }

    @GetMapping("/boom/not-found")
    void notFound() {
        throw NotFoundException.of("Thing", 42);
    }

    @PostMapping(path = "/boom/validation", consumes = "application/json")
    void validation(@Valid @RequestBody Req request) {
    }

    @GetMapping("/boom/integrity")
    void integrity() {
        throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
    }
}
