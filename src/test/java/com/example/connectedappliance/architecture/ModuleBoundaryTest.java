package com.example.connectedappliance.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleBoundaryTest {

    private static final String PACKAGE_ROOT = "com.example.connectedappliance";
    private static final List<String> REQUIRED_MODULES = List.of(
            "bootstrap", "shared", "appliance", "metrics", "vendor", "reporting");
    private static final Set<String> FEATURE_MODULES = Set.of(
            "appliance", "metrics", "vendor", "reporting");
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;");

    private final Path mainSourceRoot = Path.of("src", "main", "java")
            .toAbsolutePath()
            .normalize();
    private final Path packageRoot = mainSourceRoot.resolve(
            Path.of("com", "example", "connectedappliance"));

    @Test
    void requiredModulePackageMetadataExists() {
        List<Path> missingPackageMetadata = REQUIRED_MODULES.stream()
                .map(module -> packageRoot.resolve(module).resolve("package-info.java"))
                .filter(path -> !Files.isRegularFile(path))
                .toList();

        assertTrue(
                missingPackageMetadata.isEmpty(),
                () -> "Missing module package metadata: " + missingPackageMetadata);
    }

    @Test
    void mainSourceImportsRespectInitialModuleBoundaries() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : mainJavaSources()) {
            String sourceModule = sourceModule(sourceFile);
            if (sourceModule == null || sourceModule.equals("bootstrap")) {
                continue;
            }

            for (String importedType : importsFrom(sourceFile)) {
                if (sourceModule.equals("shared") && importsForbiddenModuleFromShared(importedType)) {
                    violations.add(violation(sourceFile, importedType));
                }

                if (FEATURE_MODULES.contains(sourceModule)
                        && importsModule(importedType, "bootstrap")) {
                    violations.add(violation(sourceFile, importedType));
                }

                if (FEATURE_MODULES.contains(sourceModule)
                        && importsAnotherFeatureInfrastructure(sourceModule, importedType)) {
                    violations.add(violation(sourceFile, importedType));
                }
            }
        }

        violations.sort(String::compareTo);
        assertTrue(
                violations.isEmpty(),
                () -> "Module boundary violations:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    @Test
    void taskSixMetricTypesAndOutboundPortsUseOnlyApprovedDependencies() throws IOException {
        List<String> violations = new ArrayList<>();

        assertImportsLimitedTo(
                packageRoot.resolve(Path.of("shared", "metric")),
                List.of("java."),
                violations);
        assertImportsLimitedTo(
                packageRoot.resolve(Path.of("appliance", "application", "port", "out")),
                List.of(
                        "java.",
                        PACKAGE_ROOT + ".shared.",
                        PACKAGE_ROOT + ".appliance.domain."),
                violations);
        assertImportsLimitedTo(
                packageRoot.resolve(Path.of("metrics", "application", "port", "out")),
                List.of("java.", PACKAGE_ROOT + ".shared."),
                violations);

        violations.sort(String::compareTo);
        assertTrue(
                violations.isEmpty(),
                () -> "Task 6 dependency violations:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    @Test
    void taskSevenVendorImplementationUsesOnlyApprovedDependencies() throws IOException {
        List<String> violations = new ArrayList<>();

        assertImportsLimitedTo(
                packageRoot.resolve("vendor"),
                List.of(
                        "java.",
                        "org.springframework.context.annotation.",
                        "org.springframework.stereotype.",
                        PACKAGE_ROOT + ".appliance.application.port.out.",
                        PACKAGE_ROOT + ".metrics.application.port.out.",
                        PACKAGE_ROOT + ".shared.",
                        PACKAGE_ROOT + ".vendor."),
                violations);

        violations.sort(String::compareTo);
        assertTrue(
                violations.isEmpty(),
                () -> "Task 7 dependency violations:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    @Test
    void taskNineApplianceDomainAndPersistenceRespectModuleBoundaries() throws IOException {
        List<String> violations = new ArrayList<>();
        Path applianceDomain = packageRoot.resolve(Path.of("appliance", "domain"));
        Path appliancePersistence =
                packageRoot.resolve(Path.of("appliance", "infrastructure", "persistence"));

        assertImportsLimitedTo(applianceDomain, List.of("java."), violations);
        assertImportsLimitedTo(
                appliancePersistence,
                List.of(
                        "java.",
                        "jakarta.persistence.",
                        "org.hibernate.exception.",
                        "org.springframework.context.annotation.",
                        "org.springframework.data.",
                        "org.springframework.stereotype.",
                        "org.springframework.transaction.",
                        PACKAGE_ROOT + ".appliance.application.exception.",
                        PACKAGE_ROOT + ".appliance.application.port.out.",
                        PACKAGE_ROOT + ".appliance.domain.",
                        PACKAGE_ROOT + ".appliance.infrastructure.persistence."),
                violations);

        for (Path sourceFile : mainJavaSources()) {
            boolean insideAppliancePersistence = sourceFile.startsWith(appliancePersistence);
            String fileName = sourceFile.getFileName().toString();
            for (String importedType : importsFrom(sourceFile)) {
                if (!insideAppliancePersistence
                        && importedType.startsWith(
                                PACKAGE_ROOT + ".appliance.infrastructure.")) {
                    violations.add(violation(sourceFile, importedType));
                }
                if (fileName.endsWith("Controller.java")
                        && (importedType.endsWith("ApplianceRepository")
                                || importedType.contains(".infrastructure."))) {
                    violations.add(violation(sourceFile, importedType));
                }
            }
        }

        for (String requiredFile : List.of(
                "ApplianceEntity.java",
                "SpringDataApplianceRepository.java",
                "AppliancePersistenceMapper.java",
                "AppliancePersistenceAdapter.java")) {
            if (!Files.isRegularFile(appliancePersistence.resolve(requiredFile))) {
                violations.add("Missing Appliance persistence file: " + requiredFile);
            }
        }

        violations.sort(String::compareTo);
        assertTrue(
                violations.isEmpty(),
                () -> "Task 9 dependency violations:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    @Test
    void taskTenApplianceApiAndServicesRespectModuleBoundaries() throws IOException {
        List<String> violations = new ArrayList<>();
        Path applianceApi = packageRoot.resolve(Path.of("appliance", "api"));
        Path applianceApplication = packageRoot.resolve(Path.of("appliance", "application"));

        for (Path sourceFile : mainJavaSources()) {
            if (!sourceFile.startsWith(applianceApi)) {
                continue;
            }
            String fileName = sourceFile.getFileName().toString();
            String source = Files.readString(sourceFile);
            for (String importedType : importsFrom(sourceFile)) {
                if (fileName.endsWith("Controller.java")
                        && (importedType.contains(".infrastructure.")
                                || importedType.endsWith("ApplianceRepository")
                                || importedType.startsWith("jakarta.persistence.")
                                || importedType.startsWith("org.springframework.data."))) {
                    violations.add(violation(sourceFile, importedType));
                }
                if ((fileName.endsWith("Request.java") || fileName.endsWith("Response.java"))
                        && importedType.startsWith("jakarta.persistence.")) {
                    violations.add(violation(sourceFile, importedType));
                }
            }
            if (fileName.equals("ApplianceResponse.java")
                    && (source.contains("long version") || source.contains("Long version"))) {
                violations.add("ApplianceResponse exposes internal version");
            }
        }

        assertImportsLimitedTo(
                applianceApplication,
                List.of(
                        "java.",
                        "org.springframework.context.annotation.",
                        "org.springframework.stereotype.",
                        PACKAGE_ROOT + ".appliance.application.",
                        PACKAGE_ROOT + ".appliance.domain."),
                violations);

        violations.sort(String::compareTo);
        assertTrue(
                violations.isEmpty(),
                () -> "Task 10 dependency violations:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    @Test
    void taskElevenApplianceListingKeepsPublicPaginationAndLayersIsolated()
            throws IOException {
        List<String> violations = new ArrayList<>();
        Path applianceApi = packageRoot.resolve(Path.of("appliance", "api"));
        Path listingService = packageRoot.resolve(Path.of(
                "appliance", "application", "ApplianceListingService.java"));
        Path pageResponse = packageRoot.resolve(Path.of("shared", "api", "PageResponse.java"));
        Path controller = applianceApi.resolve("ApplianceController.java");
        Path mapper = applianceApi.resolve("ApplianceApiMapper.java");

        assertImportsLimitedTo(
                packageRoot.resolve(Path.of("shared", "api")),
                List.of("java."),
                violations);

        String controllerSource = Files.readString(controller);
        String serviceSource = Files.readString(listingService);
        String pageSource = Files.readString(pageResponse);
        String mapperSource = Files.readString(mapper);

        if (!controllerSource.contains("ApplianceListingService")) {
            violations.add("ApplianceController does not depend on ApplianceListingService");
        }
        if (controllerSource.contains("ApplianceRepository")
                || controllerSource.contains(".infrastructure.")
                || controllerSource.contains("EntityManager")
                || controllerSource.contains("org.springframework.data.")) {
            violations.add("ApplianceController bypasses the listing application service");
        }
        if (!serviceSource.contains("ApplianceRepository")
                || serviceSource.contains("AppliancePersistenceAdapter")
                || serviceSource.contains("SpringDataApplianceRepository")
                || serviceSource.contains("EntityManager")) {
            violations.add("ApplianceListingService does not use only the repository port");
        }
        if (pageSource.contains("org.springframework.data.")
                || pageSource.contains("jakarta.persistence.")) {
            violations.add("PageResponse exposes a Spring Data or JPA dependency");
        }
        if (mapperSource.contains(".infrastructure.")
                || mapperSource.contains("org.springframework.data.")) {
            violations.add("ApplianceApiMapper imports persistence details");
        }
        if (controllerSource.contains("@RequestParam(\"sort\"")
                || controllerSource.contains("Sort ")
                || controllerSource.contains("Pageable")) {
            violations.add("ApplianceController exposes client-controlled sorting");
        }
        for (Path sourceFile : mainJavaSources()) {
            if (sourceFile.startsWith(packageRoot.resolve(
                    Path.of("appliance", "infrastructure")))) {
                continue;
            }
            for (String importedType : importsFrom(sourceFile)) {
                if (importedType.startsWith("org.springframework.data.domain.Page")
                        || importedType.startsWith("org.springframework.data.domain.Pageable")
                        || importedType.startsWith("org.springframework.data.domain.Sort")) {
                    violations.add(violation(sourceFile, importedType));
                }
            }
        }

        violations.sort(String::compareTo);
        assertTrue(
                violations.isEmpty(),
                () -> "Task 11 dependency violations:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    @Test
    void taskTwelveMetadataReplacementKeepsMutationAndLockingBoundariesIsolated()
            throws IOException {
        List<String> violations = new ArrayList<>();
        Path applianceRoot = packageRoot.resolve("appliance");
        Path controller = applianceRoot.resolve(Path.of("api", "ApplianceController.java"));
        Path request = applianceRoot.resolve(
                Path.of("api", "UpdateApplianceMetadataRequest.java"));
        Path response = applianceRoot.resolve(Path.of("api", "ApplianceResponse.java"));
        Path service = applianceRoot.resolve(
                Path.of("application", "ApplianceMetadataService.java"));
        Path domain = applianceRoot.resolve(Path.of("domain", "Appliance.java"));
        Path springRepository = applianceRoot.resolve(Path.of(
                "infrastructure", "persistence", "SpringDataApplianceRepository.java"));
        Path entity = applianceRoot.resolve(
                Path.of("infrastructure", "persistence", "ApplianceEntity.java"));

        String controllerSource = Files.readString(controller);
        String requestSource = Files.readString(request);
        String responseSource = Files.readString(response);
        String serviceSource = Files.readString(service);
        String domainSource = Files.readString(domain);
        String repositorySource = Files.readString(springRepository);
        String entitySource = Files.readString(entity);

        if (!controllerSource.contains("ApplianceMetadataService")
                || controllerSource.contains("ApplianceRepository")
                || controllerSource.contains(".infrastructure.")
                || controllerSource.contains("EntityManager")) {
            violations.add("Metadata controller bypasses its application service");
        }
        if (!serviceSource.contains("ApplianceRepository")
                || !serviceSource.contains("Clock")
                || serviceSource.contains("AppliancePersistenceAdapter")
                || serviceSource.contains("SpringDataApplianceRepository")
                || serviceSource.contains("EntityManager")
                || serviceSource.contains(".vendor.")
                || serviceSource.contains(".metrics.")) {
            violations.add("Metadata service has an unapproved dependency");
        }
        if (requestSource.contains("jakarta.persistence.")
                || requestSource.contains("org.springframework.data.")
                || requestSource.contains("version")) {
            violations.add("Metadata request exposes persistence or version details");
        }
        if (responseSource.contains("long version") || responseSource.contains("Long version")) {
            violations.add("ApplianceResponse exposes internal version");
        }
        if (domainSource.contains("jakarta.persistence.")
                || domainSource.contains("org.springframework.web.")
                || domainSource.contains("org.springframework.data.")) {
            violations.add("Appliance metadata behavior depends on framework layers");
        }
        if (!repositorySource.contains("PESSIMISTIC_WRITE")
                || !repositorySource.contains("findByIdForUpdate")) {
            violations.add("Metadata persistence does not declare its locked lookup");
        }
        if (entitySource.contains("public void replaceMetadata")
                || entitySource.contains("public ApplianceEntity replaceMetadata")) {
            violations.add("ApplianceEntity metadata mutation is public");
        }
        violations.sort(String::compareTo);
        assertTrue(
                violations.isEmpty(),
                () -> "Task 12 dependency violations:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    @Test
    void taskThirteenCollectionConfigurationKeepsMutationAndLockingBoundariesIsolated()
            throws IOException {
        List<String> violations = new ArrayList<>();
        Path applianceRoot = packageRoot.resolve("appliance");
        Path controller = applianceRoot.resolve(Path.of("api", "ApplianceController.java"));
        Path intervalRequest = applianceRoot.resolve(
                Path.of("api", "UpdateCollectionIntervalRequest.java"));
        Path stateRequest = applianceRoot.resolve(
                Path.of("api", "UpdateCollectionStateRequest.java"));
        Path response = applianceRoot.resolve(Path.of("api", "ApplianceResponse.java"));
        Path service = applianceRoot.resolve(Path.of(
                "application", "ApplianceCollectionConfigurationService.java"));
        Path repositoryPort = applianceRoot.resolve(Path.of(
                "application", "port", "out", "ApplianceRepository.java"));
        Path domain = applianceRoot.resolve(Path.of("domain", "Appliance.java"));
        Path springRepository = applianceRoot.resolve(Path.of(
                "infrastructure", "persistence", "SpringDataApplianceRepository.java"));
        Path adapter = applianceRoot.resolve(Path.of(
                "infrastructure", "persistence", "AppliancePersistenceAdapter.java"));
        Path entity = applianceRoot.resolve(
                Path.of("infrastructure", "persistence", "ApplianceEntity.java"));

        String controllerSource = Files.readString(controller);
        String intervalRequestSource = Files.readString(intervalRequest);
        String stateRequestSource = Files.readString(stateRequest);
        String responseSource = Files.readString(response);
        String serviceSource = Files.readString(service);
        String portSource = Files.readString(repositoryPort);
        String domainSource = Files.readString(domain);
        String repositorySource = Files.readString(springRepository);
        String adapterSource = Files.readString(adapter);
        String entitySource = Files.readString(entity);

        if (!controllerSource.contains("ApplianceCollectionConfigurationService")
                || !controllerSource.contains("/collection-interval")
                || !controllerSource.contains("/collection-state")
                || controllerSource.contains("ApplianceRepository")
                || controllerSource.contains(".infrastructure.")
                || controllerSource.contains("EntityManager")) {
            violations.add("Collection configuration controller bypasses its application service");
        }
        if (!serviceSource.contains("ApplianceRepository")
                || !serviceSource.contains("Clock")
                || serviceSource.contains("AppliancePersistenceAdapter")
                || serviceSource.contains("SpringDataApplianceRepository")
                || serviceSource.contains("EntityManager")
                || serviceSource.contains(".vendor.")
                || serviceSource.contains(".metrics.")) {
            violations.add("Collection configuration service has an unapproved dependency");
        }
        if (intervalRequestSource.contains("version")
                || stateRequestSource.contains("version")
                || intervalRequestSource.contains("jakarta.persistence.")
                || stateRequestSource.contains("jakarta.persistence.")
                || responseSource.contains("long version")
                || responseSource.contains("Long version")) {
            violations.add("Collection configuration API exposes persistence or version details");
        }
        if (domainSource.contains("jakarta.persistence.")
                || domainSource.contains("org.springframework.web.")
                || domainSource.contains("org.springframework.data.")) {
            violations.add("Collection configuration domain behavior depends on framework layers");
        }
        if (!portSource.contains("replaceCollectionInterval")
                || !portSource.contains("replaceCollectionState")
                || !repositorySource.contains("PESSIMISTIC_WRITE")
                || !repositorySource.contains("findByIdForUpdate")
                || !adapterSource.contains("@Transactional")
                || !adapterSource.contains("findByIdForUpdate")) {
            violations.add("Collection configuration persistence lacks its atomic locking path");
        }
        if (entitySource.contains("public void replaceCollectionInterval")
                || entitySource.contains("public void replaceCollectionState")
                || entitySource.contains("public ApplianceEntity replaceCollection")) {
            violations.add("ApplianceEntity collection configuration mutation is public");
        }
        for (String deferredBehavior : List.of(
                "collect-now", "CollectionAttempt", "VendorCollection", "Scheduler")) {
            if (controllerSource.contains(deferredBehavior)
                    || serviceSource.contains(deferredBehavior)) {
                violations.add("Task 13 contains deferred behavior: " + deferredBehavior);
            }
        }

        violations.sort(String::compareTo);
        assertTrue(
                violations.isEmpty(),
                () -> "Task 13 dependency violations:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    private List<Path> mainJavaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(packageRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private void assertImportsLimitedTo(
            Path sourceRoot, List<String> allowedPrefixes, List<String> violations)
            throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path sourceFile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                for (String importedType : importsFrom(sourceFile)) {
                    if (allowedPrefixes.stream().noneMatch(importedType::startsWith)) {
                        violations.add(violation(sourceFile, importedType));
                    }
                }
            }
        }
    }

    private String sourceModule(Path sourceFile) {
        Path relativePath = packageRoot.relativize(sourceFile);
        if (relativePath.getNameCount() < 2) {
            return null;
        }

        String candidate = relativePath.getName(0).toString();
        return REQUIRED_MODULES.contains(candidate) ? candidate : null;
    }

    private List<String> importsFrom(Path sourceFile) throws IOException {
        Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(sourceFile));
        List<String> imports = new ArrayList<>();
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    private boolean importsForbiddenModuleFromShared(String importedType) {
        return REQUIRED_MODULES.stream()
                .filter(module -> !module.equals("shared"))
                .anyMatch(module -> importsModule(importedType, module));
    }

    private boolean importsAnotherFeatureInfrastructure(String sourceModule, String importedType) {
        return FEATURE_MODULES.stream()
                .filter(module -> !module.equals(sourceModule))
                .map(module -> PACKAGE_ROOT + "." + module + ".infrastructure.")
                .anyMatch(importedType::startsWith);
    }

    private boolean importsModule(String importedType, String module) {
        return importedType.startsWith(PACKAGE_ROOT + "." + module + ".");
    }

    private String violation(Path sourceFile, String importedType) {
        return mainSourceRoot.relativize(sourceFile) + " imports " + importedType;
    }
}
