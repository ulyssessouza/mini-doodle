package com.doodle.doodlecodingchallenge.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException("%s not found: %s".formatted(resource, id));
    }
}
