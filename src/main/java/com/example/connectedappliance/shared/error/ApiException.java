package com.example.connectedappliance.shared.error;

public class ApiException extends RuntimeException {

    private final ApiProblemDefinition problem;

    public ApiException(ApiProblemDefinition problem) {
        super(problem.code());
        this.problem = problem;
    }

    public ApiProblemDefinition problem() {
        return problem;
    }
}
