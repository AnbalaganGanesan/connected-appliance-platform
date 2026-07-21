# Repository Instructions

## Current Project Stage

This repository is for the Connected Appliance Platform take-home
assignment.

This is a take-home assignment for a Cisco interview. Optimize the
solution for correctness, clarity, maintainability, local execution,
reviewability, and clear explanation of engineering trade-offs.

Do not invent Cisco-specific business or technical requirements that
are not present in the assignment.

The requirements, high-level architecture and technology choices, and
public API contract are approved. Detailed data-model design remains
pending.

Do not infer or introduce implementation decisions unless they are
explicitly approved and documented.

## Authoritative Project Documentation

Before performing any task, read:

- `docs/REQUIREMENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/API_CONTRACT.md`

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

Future implementation must follow all three documents. If the documents
conflict, report the conflict instead of resolving it silently.

Do not silently convert an assumption or open question into a confirmed
requirement.

## Working Instructions

- Work on only the task explicitly requested in the current prompt.
- Do not implement future tasks or unrelated improvements.
- Do not generate application code until the detailed API contract,
  data model, and the relevant implementation task have been explicitly
  approved.
- Do not invent business capabilities that are absent from the
  requirements.
- Clearly identify assumptions and trade-offs.
- Prefer small, reviewable changes.
- Do not modify unrelated files.
- After making changes, report:
  - files created or modified;
  - decisions made;
  - assumptions introduced;
  - validation performed; and
  - unresolved questions.

## Documentation Rules

- Keep original assignment requirements separate from design decisions.
- Update documentation when an approved decision changes.
- Do not duplicate the full contents of `docs/REQUIREMENTS.md` in this
  file.
- Use clear Markdown headings and concise language.

## Build and Test Commands

No application framework, build tool, or test framework has been added
yet.

Update this section only after the relevant technology decisions are
approved and the commands exist in the repository.
