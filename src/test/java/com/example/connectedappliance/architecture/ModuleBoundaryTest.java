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
        if (controllerSource.contains("@PutMapping")
                || controllerSource.contains("/metadata")
                || controllerSource.contains("/collection-interval")
                || controllerSource.contains("/collection-state")) {
            violations.add("ApplianceController contains Task 12 or Task 13 endpoints");
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
