package sqlServerJava.sqlServerJava;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

@Component
public class HibernateFullGenerator2 implements CommandLineRunner {

    // Absolute project root path
    private static final String PROJECT_ROOT = System.getProperty("user.dir");

    // Absolute path where entities are generated
    private static final String ENTITY_PATH  = PROJECT_ROOT + "/src/main/java/sqlServerJava/entities2";

    @Override
    public void run(String... args) throws Exception {

        System.out.println("=== HibernateFullGenerator2: START ===");
        System.out.println("Project root: " + PROJECT_ROOT);
        System.out.println("Entity output directory: " + ENTITY_PATH);

        String url  = "jdbc:sqlserver://192.168.2.100:1433;databaseName=master;encrypt=false";
        String user = "sa";
        String pass = "Password@@@123";

        Connection conn = DriverManager.getConnection(url, user, pass);
        DatabaseMetaData meta = conn.getMetaData();

        // ----------------------------------------------------
        // STEP 1 — DETECT ALL NON-SYSTEM SCHEMAS
        // ----------------------------------------------------
        ResultSet schemaRs = meta.getSchemas();
        Set<String> userSchemas = new HashSet<>();

        while (schemaRs.next()) {
            String s = schemaRs.getString("TABLE_SCHEM");

            if (s.equalsIgnoreCase("sys")) continue;
            if (s.equalsIgnoreCase("INFORMATION_SCHEMA")) continue;

            userSchemas.add(s);
        }

        System.out.println("Loaded schemas (non-system): " + userSchemas);


        // ----------------------------------------------------
        // STEP 2 — GENERATE HIBERNATE ENTITIES FOR ALL SCHEMAS
        // ----------------------------------------------------
        Configuration cfg = new Configuration()
            .withJdbc(new Jdbc()
                    .withDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                    .withUrl(url)
                    .withUser(user)
                    .withPassword(pass)
            )
            .withGenerator(new Generator()
                .withDatabase(new Database()
                        .withName("org.jooq.meta.jdbc.JDBCDatabase")
                        // No schema filtering → load EVERY schema
                        .withIncludes(".*")
                )
                .withGenerate(new Generate()
                        .withPojos(true)
                        .withJpaAnnotations(true)
                        .withDaos(false)
                        .withRecords(false)
                        .withTables(false)
                        .withRoutines(false)
                        .withSequences(false)
                        .withRelations(false)
                       // .withConstraintDefinitions(false)
                )
                .withTarget(new Target()
                        .withPackageName("sqlServerJava.entities2")
                        .withDirectory(PROJECT_ROOT + "/src/main/java")
                )
            );

        System.out.println("=== Running jOOQ Hibernate Entity Generation ===");
        GenerationTool.generate(cfg);
        System.out.println("=== Entity Generation COMPLETE ===");


        // ----------------------------------------------------
        // STEP 3 — BUILD FOREIGN KEY MAP
        // ----------------------------------------------------
        Map<String, List<FK>> fkMap = new HashMap<>();
        ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});

        while (tables.next()) {
            String schema = tables.getString("TABLE_SCHEM");
            if (!userSchemas.contains(schema)) continue;

            String table = tables.getString("TABLE_NAME");

            ResultSet fks = meta.getImportedKeys(null, schema, table);

            while (fks.next()) {
                fkMap.computeIfAbsent(table, t -> new ArrayList<>())
                    .add(new FK(
                            fks.getString("FKCOLUMN_NAME"),
                            fks.getString("PKTABLE_NAME"),
                            fks.getString("PKCOLUMN_NAME")
                    ));
            }
        }

        System.out.println("FK Map: " + fkMap);


        // ----------------------------------------------------
        // STEP 4 — ADD REAL RELATIONSHIPS (@ManyToOne)
        // ----------------------------------------------------
        Path entityDir = Paths.get(ENTITY_PATH);

        for (String table : fkMap.keySet()) {

            Path entityFile = entityDir.resolve(table + ".java").toAbsolutePath();

            if (!Files.exists(entityFile)) {
                System.out.println("Skipping (not found): " + entityFile);
                continue;
            }

            String content = Files.readString(entityFile);

            for (FK fk : fkMap.get(table)) {

                String targetClass = capitalize(fk.pkTable);
                String fieldName   = decapitalize(targetClass);

                String block =
                    "    @ManyToOne\n" +
                    "    @JoinColumn(name=\"" + fk.fkColumn +
                    "\", referencedColumnName=\"" + fk.pkColumn + "\")\n" +
                    "    private " + targetClass + " " + fieldName + ";\n\n";

                content = content.replaceFirst("}\\s*$", block + "}");

                // PRINT EXACT LOCATION WHERE FK WAS ADDED
                System.out.println(
                    "Added relation to: " + entityFile +
                    "  --> Field: " + fieldName +
                    "  --> FK: " + fk.fkColumn +
                    " -> " + targetClass + "." + fk.pkColumn
                );
            }

            Files.writeString(entityFile, content);
        }

        System.out.println("=== Relationship Injection COMPLETE ===");
        System.out.println("=== HibernateFullGenerator2: DONE ===");
    }


    // ----------------------------------------------------
    // Helpers
    // ----------------------------------------------------
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private record FK(String fkColumn, String pkTable, String pkColumn) {}
}
