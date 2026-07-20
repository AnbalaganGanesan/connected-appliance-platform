# Connected Appliance Platform Requirements

## 1. Project objective

Build a locally runnable backend application that gives clients a consistent way to manage connected appliances from potentially different vendors, collect and retain appliance metrics, and generate reports. The implementation must be focused enough for local review while being complete enough to demonstrate the core workflow end to end.

Appliance vendors may differ in API style, authentication, capabilities, metric names, rate limits, and reliability. External vendor integrations may be mocked.

## 2. Functional requirements

The following are explicit assignment requirements:

- The backend must support registering appliances.
- The backend must support managing registered appliances.
- The backend must collect appliance metrics at configurable intervals.
- The backend must keep historical appliance data.
- The backend must generate daily reports.
- The backend must generate on-demand reports for custom date ranges.
- The backend must expose enough API behavior for reviewers to exercise the core workflow end to end.
- The system must provide clients with consistent behavior despite differences among appliance vendors.
- External appliance vendors may be mocked; real vendor integrations are not required by the assignment.

The assignment intentionally leaves the following as implementation choices and this document does not prescribe them:

- API shape
- Data model
- Persistence approach
- Background-job approach
- Report format

## 3. Non-functional requirements

The following are explicit assignment requirements:

- The solution must run locally.
- The implementation must be focused enough to run and review locally.
- The implementation must be complete enough to demonstrate the core workflow.
- Reviewers must have a clear way to verify appliance data collection.
- Reviewers must have a clear way to verify report generation.
- The submission must include clear local run instructions.
- The submission must include tests or a simple verification path.
- The primary deliverable must be working backend code, not only a design document.

The scenario identifies vendor variation in authentication, capabilities, metric names, rate limits, and reliability. It does not define measurable security, performance, availability, scalability, retention, or interoperability targets; any such targets remain open until clarified.

## 4. Assumptions

The statements in this section are assumptions used to interpret the assignment; they are not explicit requirements:

- A single locally runnable service is sufficient for review unless the implementation requires supporting local processes.
- “Manage appliances” includes at least making registered appliances available for subsequent collection and reporting, but the exact management operations require clarification.
- “Configurable intervals” means the collection cadence can be changed without rewriting collection logic; the configuration mechanism is an implementation choice.
- Historical data must cover enough time and detail to support daily and custom-date-range reports; no specific retention duration is assumed.
- Daily reports operate on a defined calendar-day boundary, but the applicable time zone and scheduling semantics require clarification.
- A custom date range has a start and an end, but inclusivity and time granularity require clarification.
- Reports are based on collected historical appliance data; the required calculations and presentation remain unspecified.
- Mocked vendors are acceptable for demonstrating vendor communication and collection behavior.
- Authentication and authorization for clients of the backend are not assumed because the assignment does not require them.
- No production deployment, cloud infrastructure, or real appliance hardware is assumed.

## 5. Non-goals

The following are not required by the assignment unless later clarified:

- Integrating with real appliance vendors or physical devices
- Supporting any particular appliance category, vendor, protocol, or number of appliances
- Defining a particular API style or endpoint structure
- Selecting a data model, database, persistence technology, job scheduler, framework, language, or build tool
- Defining a fixed report schema, file type, visualization, aggregation, or delivery channel
- Providing a user interface or mobile application
- Deploying to production or a cloud environment
- Meeting unspecified production-scale performance, availability, security, or compliance targets
- Implementing client authentication or authorization
- Seed data, sample requests, and mock payloads are not mandatory, but may be included to simplify reviewer verification.

Seed data, sample requests, and small mock payloads are explicitly optional. They may be added only when they make working behavior easier to review.

## 6. Acceptance criteria

The following criteria reflect the explicit requirements without prescribing implementation design:

1. A reviewer can follow documented local instructions and start the backend successfully.
2. A reviewer can use exposed API behavior to register an appliance.
3. A reviewer can use exposed API behavior to perform the implemented appliance-management operations and observe their effect.
4. The backend must support configurable metric-collection intervals. The completed solution must document how a reviewer can configure and verify the collection cadence.
5. The running backend collects metrics for a registered appliance through a real or mocked vendor interaction.
6. Collected appliance data remains available as historical data for later retrieval or report generation.
7. The system can generate a daily report from collected historical data.
8. A reviewer can request and obtain an on-demand report for a specified custom date range.
9. The demonstrated API behavior is sufficient to exercise registration or management, collection, historical retention, and reporting as one end-to-end workflow.
10. The repository includes tests or a documented simple verification path that lets a reviewer verify collection and report generation.
11. The submitted README explains how to run the service, how to run tests or otherwise verify behavior, assumptions and non-goals, important design choices, and AI usage if applicable.
12. The final submission contains working backend source code and is not only a design document.

## 7. End-to-end verification scenarios

The exact requests, responses, timings, and report contents depend on later implementation choices. The completed solution must enable verification equivalent to these scenarios.

### Scenario 1: Register, collect, retain, and report

1. Start the backend using the documented local run instructions.
2. Register a supported appliance through the exposed API behavior.
3. Configure or select a metric-collection interval.
4. Allow or trigger the configured collection behavior.
5. Verify that appliance metrics were collected from a real or mocked vendor.
6. Verify that the collected values are retained as historical appliance data.
7. Request an on-demand report whose custom date range includes the collected data.
8. Verify that a report is generated using the applicable historical data.

### Scenario 2: Daily report

1. Ensure historical appliance data exists for the relevant day.
2. Exercise the implemented daily-report generation behavior.
3. Verify that a daily report is produced for the applicable calendar day and uses the relevant historical data.

### Scenario 3: Appliance management affects the workflow

1. Register an appliance.
2. Exercise one of the appliance-management operations provided by the implementation.
3. Verify that the resulting appliance state or configuration is observable through the exposed API behavior.
4. Where applicable, verify the effect of the appliance-management operation on metric collection or reporting.

### Scenario 4: Repeatable reviewer verification

1. Run the documented tests or simple verification path.
2. Verify that it demonstrates appliance data collection.
3. Verify that it demonstrates daily and custom-date-range report generation.

## 8. Delivery and submission requirements

The following are explicit assignment requirements:

- Submit the backend source code.
- Include a short README that covers:
  - how to run the service;
  - how to run tests or otherwise verify behavior;
  - assumptions and non-goals;
  - a brief note on important design choices; and
  - an AI usage note, if applicable.
- Include clear local run instructions that enable reviewers to start the backend.
- Provide enough API behavior and documentation for reviewers to exercise the main APIs.
- Provide a way for reviewers to verify appliance data collection and report generation.
- Include tests or a simple verification path.
- Do not submit only a design document; evaluation is primarily based on working backend code.

Optional submission aids, not mandatory requirements:

- Seed data
- Sample requests
- Small mock payloads

These optional aids should be included only if they make the working behavior easier to review.

## 9. Open questions

The assignment does not answer the following questions. They should be resolved during design or documented as implementation assumptions, without being treated as assignment requirements:

- Which appliance-management operations are required beyond registration?
- Which appliance categories or vendor differences must the demonstration cover?
- What metrics must be collected, and how should vendor-specific metric names or units be normalized?
- How is the collection interval configured, and at what scope (system, vendor, or appliance)?
- What should happen when a vendor is unavailable, slow, rate-limited, or returns invalid data?
- How long must historical data be retained, and must it survive a backend restart?
- What time zone defines a day for daily reports?
- Are daily reports scheduled automatically, generated when requested, or both?
- What are the inclusion rules and time granularity for custom date-range boundaries?
- What information, calculations, and level of appliance detail must reports contain?
- How should empty date ranges, missing metrics, partial collection failures, and invalid report ranges appear in reports?
- Must report generation be synchronous or asynchronous?
- What client-facing error and validation behavior is expected?
- Is backend client authentication or authorization expected?
- What level of automated test coverage or verification evidence is expected?
