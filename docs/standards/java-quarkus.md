# Backend Development Guidelines

**Stack:** Java 21, Quarkus, AWS Lambda (REST)

## Java & Quarkus Standards

- **Modern Java:** Aggressively use Java 21 features. Use `record` classes for all Data Transfer Objects (DTOs) (e.g., `public record SudokuBoard(int[][] grid) {}`).
- **Contextual Javadocs:** Every class must include a class-level Javadoc at the top describing its overarching purpose. This documentation must focus strictly on the *why* (the domain context, business requirement, or overarching intent) and entirely omit the *how* (the implementation details are expected to be clean enough to be self-documenting).
- **Routing:** Use standard JAX-RS annotations (`@Path`, `@GET`, `@POST`) for REST controllers.
- **Serialization:** Rely on `resteasy-reactive-jackson` for all JSON parsing. Do not manually parse JSON strings.
- **Separation of Concerns:** Controllers must only handle HTTP routing and validation. All Sudoku generation, validation, and solving logic must reside in a dedicated `@ApplicationScoped` service (e.g., `SudokuService.java`).

## SOLID Principles & Java Best Practices

- **Single Responsibility:** Each class has one reason to change. Controllers route requests, services contain business logic, and repositories handle persistence — never mix these concerns.
- **Open/Closed:** Design Sudoku logic (generation, validation, solving) behind interfaces so behaviour can be extended without modifying existing classes.
- **Liskov Substitution:** Implementations must be fully substitutable for their interfaces; avoid weakening contracts in subclasses or implementations.
- **Interface Segregation:** Prefer narrow, focused interfaces over broad ones (e.g., separate `SudokuGenerator` and `SudokuValidator` rather than a single monolithic interface).
- **Dependency Inversion:** Depend on abstractions, not concrete classes. Inject dependencies via CDI (`@Inject`) rather than instantiating them with `new`.
- **No Magic Values:** Never use hardcoded "magic" numbers or string literals in the code. All literal values must be extracted into well-named `static final` constants or strongly-typed `enum`s to ensure maintainability and readability.
- **Immutability:** Prefer immutable objects. Use `record` for DTOs and `final` fields wherever possible.
- **Streams & Optionals:** Use the Stream API and `Optional` idiomatically; avoid returning `null`.
- **Exceptions:** Use checked exceptions for recoverable conditions and unchecked exceptions for programming errors. Never swallow exceptions silently.

## Serverless Optimization

- **SnapStart Readiness:** Do not put heavy initialization logic inside the request handler. Initialize necessary resources, random number generators, or static data structures during the application's startup phase to maximize AWS SnapStart caching benefits.
- **Statelessness:** The Lambda environment is ephemeral. Do not store any game state in local memory or files. If state is needed, pass it back to the client or use DynamoDB.
