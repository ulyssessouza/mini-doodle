package com.doodle.boomstub;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
public class ThrowingController {

    public record Req(@NotBlank String name) {
    }

    @GetMapping("/boom/not-found")
    void notFound() {
        throw NotFoundException.of("Thing", 42);
    }

    @GetMapping("/boom/conflict")
    void conflict() {
        throw new ConflictException("time range overlaps existing busy time for: bob@example.com");
    }

    @GetMapping("/boom/bad-request")
    void badRequest() {
        throw new InvalidRequestException("end must be after start");
    }

    @GetMapping("/boom/param")
    void missingParam(@RequestParam String param) {
    }

    @PostMapping(path = "/boom/validation", consumes = "application/json")
    void validation(@Valid @RequestBody Req request) {
    }

    @GetMapping("/boom/integrity")
    void integrity() {
        throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
    }

    @GetMapping("/boom/nope")
    void noResource() throws NoResourceFoundException {
        throw new NoResourceFoundException(HttpMethod.GET, "/boom/nope", "boom/nope");
    }

    @GetMapping("/boom/method-validation")
    void methodValidation(@RequestParam @Min(1) int value) {
    }
}
