package za.co.solar.fleet.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unexpectedErrorsNeverLeakInternals() {
        ProblemDetail detail = handler.handleUnexpected(new IllegalStateException("jdbc:postgresql://secret-host password=hunter2"));
        assertEquals(500, detail.getStatus());
        assertFalse(String.valueOf(detail.getDetail()).contains("secret-host"));
        assertFalse(String.valueOf(detail.getDetail()).contains("hunter2"));
    }

    @Test
    void responseStatusReasonIsPreservedWithItsStatus() {
        ProblemDetail detail = handler.handleStatus(new ResponseStatusException(HttpStatus.CONFLICT, "Name already exists."));
        assertEquals(409, detail.getStatus());
        assertEquals("Name already exists.", detail.getDetail());
    }
}
