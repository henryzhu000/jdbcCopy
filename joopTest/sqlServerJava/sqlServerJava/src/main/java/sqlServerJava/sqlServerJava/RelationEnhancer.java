package sqlServerJava.sqlServerJava;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

@Component
public class RelationEnhancer implements CommandLineRunner {
	boolean run=false;

    private static final String ENTITY_PACKAGE = "sqlServerJava/entities2";

    @Override
    public void run(String... args) throws Exception {
    	if(!run) {return ;}

        System.out.println("=== Adding FK Constraints Only (NO relations) ===");

        String url = "jdbc:sqlserver://192.168.2.100:1433;databaseName=master;encrypt=false";
        String user = "sa";
        String pass = "Password@@@123";

        Connection conn = DriverManager.getConnection(url, user, pass);
        DatabaseMetaData meta = conn.getMetaData();

        // Detect tables in ALL non-system schemas
        ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});

        Map<String, List<FK>> fkMap = new HashMap<>();

        while (tables.next()) {

            String schema = tables.getString("TABLE_SCHEM");
            if (schema.equalsIgnoreCase("sys")) continue;
            if (schema.equalsIgnoreCase("INFORMATION_SCHEMA")) continue;

            String tableName = tables.getString("TABLE_NAME");

            ResultSet fks = meta.getImportedKeys(null, schema, tableName);

            while (fks.next()) {
                String fkColumn = fks.getString("FKCOLUMN_NAME");
                String pkTable  = fks.getString("PKTABLE_NAME");
                String pkColumn = fks.getString("PKCOLUMN_NAME");

                fkMap.computeIfAbsent(tableName, t -> new ArrayList<>())
                        .add(new FK(schema, tableName, fkColumn, pkTable, pkColumn));
            }
        }

        // Process entity files
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
            }

            Files.writeString(file, content);
        }

        System.out.println("=== FK Constraint Enhancement COMPLETE ===");
    }

    private record FK(
            String schema,
            String table,
            String fkColumn,
            String pkTable,
            String pkColumn
    ) {}
}
