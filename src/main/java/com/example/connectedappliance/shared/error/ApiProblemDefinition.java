package com.example.connectedappliance.shared.error;

import java.net.URI;

import org.springframework.http.HttpStatus;

public record ApiProblemDefinition(
        HttpStatus status,
        URI type,
        String title,
        String detail,
        String code) {
}
