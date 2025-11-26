package sqlServerJava.sqlServerJava;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

@Component
public class HibernateFullGenerator implements CommandLineRunner {

    private static final String ENTITY_PACKAGE = "sqlServerJava/entities2";

    @Override
    public void run(String... args) throws Exception {

        System.out.println("=== HibernateFullGenerator: START ===");

        String url  = "jdbc:sqlserver://192.168.2.100:1433;databaseName=master;encrypt=false";
        String user = "sa";
        String pass = "Password@@@123";

        Connection conn = DriverManager.getConnection(url, user, pass);
        DatabaseMetaData meta = conn.getMetaData();

        //
        // STEP 1 — Detect schemas BUT WE WILL *NOT* FEED THEM TO jOOQ
        //
        ResultSet schemaRs = meta.getSchemas();
        Set<String> userSchemas = new HashSet<>();

        while (schemaRs.next()) {
            String s = schemaRs.getString("TABLE_SCHEM");

            if (s.equalsIgnoreCase("sys")) continue;
            if (s.equalsIgnoreCase("INFORMATION_SCHEMA")) continue;

            userSchemas.add(s);
        }

        System.out.println("Schemas to filter FKs = " + userSchemas);


        //
        // STEP 2 — jOOQ: LOAD **ALL** SCHEMAS
        // (no .withInputSchema, no .withSchemata)
        //
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

                        // ⭐ No schema restrictions — load EVERYTHING
                        // .withInputSchema(null)
                        // .withSchemata(null)

                        .withIncludes(".*")      // include ALL objects
                )
                .withGenerate(new Generate()
                        .withPojos(true)
                        .withJpaAnnotations(true)

                        // Turn off everything else
                        .withDaos(false)
                        .withRecords(false)
                        .withTables(false)
                        .withRoutines(false)
                        .withSequences(false)
                        .withRelations(false)
                     //   .withConstraintDefinitions(false)
                )
                .withTarget(new Target()
                        .withPackageName("sqlServerJava.entities2")
                        .withDirectory("src/main/java")
                )
            );

        System.out.println("=== Running jOOQ Hibernate Entity Generation (ALL schemas) ===");
        GenerationTool.generate(cfg);
        System.out.println("=== Entity Generation Complete ===");


        //
        // STEP 3 — Foreign Key Enhancer
        //
        System.out.println("=== Scanning Foreign Keys ===");

        ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
        Map<String, List<FK>> fkMap = new HashMap<>();

        while (tables.next()) {
            String schema = tables.getString("TABLE_SCHEM");

            if (!userSchemas.contains(schema)) continue;

            String table = tables.getString("TABLE_NAME");
            ResultSet fks = meta.getImportedKeys(null, schema, table);

            while (fks.next()) {
                fkMap.computeIfAbsent(table, x -> new ArrayList<>())
                        .add(new FK(
                                fks.getString("FKCOLUMN_NAME"),
                                fks.getString("PKTABLE_NAME"),
                                fks.getString("PKCOLUMN_NAME")
                        ));
            }
        }

        System.out.println("FK Map = " + fkMap);


        //
        // STEP 4 — Update JPA Entities with @JoinColumn
        //
        Path base = Paths.get("src/main/java/" + ENTITY_PACKAGE);

        for (String table : fkMap.keySet()) {

            Path file = base.resolve(table + ".java");
            if (!Files.exists(file)) continue;

            String content = Files.readString(file);

            for (FK fk : fkMap.get(table)) {

                String columnPattern =
                        "@Column(name=\"" + fk.fkColumn + "\")";

                String replacement =
                        "@JoinColumn(name=\"" + fk.fkColumn +
                        "\", referencedColumnName=\"" + fk.pkColumn + "\")\n" +
                        columnPattern;

                content = content.replace(columnPattern, replacement);
                System.out.println("replaced----");
            }

            Files.writeString(file, content);
        }

        System.out.println("=== FK Enhancement COMPLETE ===");
        System.out.println("=== HibernateFullGenerator DONE ===");
    }

    private record FK(String fkColumn, String pkTable, String pkColumn) {}
}
