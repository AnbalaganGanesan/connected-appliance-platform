# Repository Instructions

## Current Project Stage

This repository is for the Connected Appliance Platform take-home
assignment.

This is a take-home assignment for a Cisco interview. Optimize the
solution for correctness, clarity, maintainability, local execution,
reviewability, and clear explanation of engineering trade-offs.

Do not invent Cisco-specific business or technical requirements that
are not present in the assignment.

The requirements, architecture and technology choices, public API
contract, detailed data model, and implementation plan are approved.
Application implementation has not yet started.

Do not infer or introduce implementation decisions unless they are
explicitly approved and documented.

## Authoritative Project Documentation

Before performing any task, read:

- `docs/REQUIREMENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/API_CONTRACT.md`
- `docs/DATA_MODEL.md`
- `docs/IMPLEMENTATION_PLAN.md`

`docs/REQUIREMENTS.md` defines what the system must deliver and is the
authoritative source for:

- explicit assignment requirements;
- assumptions;
- non-goals;
- acceptance criteria;
- end-to-end verification scenarios; and
- delivery requirements.

`docs/ARCHITECTURE.md` defines the approved architecture and technology
decisions.

`docs/API_CONTRACT.md` defines the approved public API behaviour, DTO
contracts, validation rules, and HTTP semantics.

`docs/DATA_MODEL.md` defines the approved persistence model, table
ownership, constraints, indexes, data integrity rules, transaction
boundaries, and concurrency strategy.

`docs/IMPLEMENTATION_PLAN.md` defines the approved implementation
sequence, task dependencies, implementation boundaries, testing
strategy, validation commands, commit sequence, reviewer milestones,
and definition of done.

Future implementation must follow all five authoritative documents. If
the documents conflict, report the conflict instead of resolving it
silently.

Do not silently convert an assumption or open question into a confirmed
requirement.

## Working Instructions

- Work on only the task explicitly requested in the current prompt.
- Implementation may proceed only one approved
  `docs/IMPLEMENTATION_PLAN.md` task at a time.
- The current prompt must explicitly identify the implementation-plan
  task being implemented.
- Do not implement future tasks, combine unrelated tasks, or move ahead
  in the plan without explicit approval.
- Do not silently alter approved requirements, API behavior,
  architecture, database design, task boundaries, or test expectations.
- Do not invent business capabilities that are absent from the
  requirements.
- Clearly identify assumptions and trade-offs.
- Prefer small, reviewable changes.
- Add the tests required by the corresponding implementation-plan task.
- Run the validation commands specified for that task.
- Do not remove or weaken tests merely to make the build pass.
- Do not modify unrelated files.
- After making changes, report:
  - files created or modified;
  - decisions made;
  - validation performed;
  - assumptions introduced; and
  - unresolved issues.

## Documentation Rules

- Keep original assignment requirements separate from design decisions.
- Update documentation when an approved decision changes.
- Do not duplicate the full contents of `docs/REQUIREMENTS.md` in this
  file.
- Use clear Markdown headings and concise language.

## Build and Test Commands

- Build and unit test: `./mvnw test`
- Clean build and unit test: `./mvnw clean test`
- Run application: `./mvnw spring-boot:run`

Database integration testing is not available yet.
`./mvnw verify` with Testcontainers will be introduced in Task 4.
