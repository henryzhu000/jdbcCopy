package jooqAttempt.jooqAttempt;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

@Component
public class Test2Class implements CommandLineRunner {

    private static final String MYSQL_URL  = "jdbc:mysql://localhost:3306/joblist";
    private static final String MYSQL_USER = "henry";
    private static final String MYSQL_PASS = "password";

    private static final String H2_URL     = "jdbc:h2:file:D:/temp/h2/h2diskdb";
    private static final String H2_USER    = "sa";
    private static final String H2_PASS    = "password";

    @Override
    public void run(String... args) throws Exception {

        System.out.println("\n=== START MySQL → H2 migration ===\n");

        Connection mysqlConn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
        Connection h2Conn    = DriverManager.getConnection(H2_URL, H2_USER, H2_PASS);

        Settings settings = new Settings()
                .withRenderSchema(false)
                .withRenderQuotedNames(RenderQuotedNames.NEVER);

        DSLContext mysql = DSL.using(mysqlConn, SQLDialect.MYSQL);
        DSLContext h2    = DSL.using(h2Conn, SQLDialect.H2, settings);

        Schema schema = mysql.meta().getSchemas().stream()
                .filter(s -> s.getName().equalsIgnoreCase("joblist"))
                .findFirst()
                .orElseThrow();

        // Sort tables based on FK dependencies
        List<Table<?>> tables = sortTablesByFKOrder(schema.getTables());

        // ---------------- CREATE TABLES ----------------
        for (Table<?> table : tables) {
            System.out.println("Creating table: " + table.getName());
            Queries ddl = mysql.ddl(table);

            for (Query q : ddl.queries()) {
                String sql = q.getSQL();

                sql = sql.replace("`", "");
                sql = sql.replace("joblist.", "");
                sql = sql.replace("unsigned", "");
                sql = sql.replace("AUTO_INCREMENT", "");
                sql = sql.replaceAll("constraint\\s+\\w+\\s+primary key", "primary key");

                if (sql.trim().toLowerCase().startsWith("create schema"))
                    continue;

                // keep VALUE quoted
                sql = sql.replaceAll("\\bvalue\\b", "\"value\"");

                h2.execute(sql);
            }
        }

        // ---------------- COPY DATA IN BATCHES ----------------
        for (Table<?> table : tables) {
            System.out.println("Copying: " + table.getName());

            Result<?> rows = mysql.selectFrom(table).fetch();
            if (rows.isEmpty()) {
                System.out.println("  No rows.");
                continue;
            }

            List<Field<?>> fields = Arrays.asList(table.fields());

            // Build batch insert
            List<Query> batch = new ArrayList<>();

            for (Record r : rows) {

                Map<Field<?>, Object> valueMap = new LinkedHashMap<>();

                for (Field<?> f : fields) {

                    // SPECIAL CASE: column named value
                    if (f.getName().equalsIgnoreCase("value")) {
                        Field<Object> fixed = DSL.field("\"value\"");
                        valueMap.put(fixed, r.get(f));
                    } else {
                        valueMap.put(f, r.get(f));
                    }
                }

                batch.add(h2.insertInto(table)
                        .set(valueMap));
            }

            h2.batch(batch).execute();

            System.out.println("  Copied " + rows.size() + " rows.");
        }

        System.out.println("\n=== DONE ===\n");
        System.out.println("H2 Console: http://localhost:8080/h2");

        Thread.currentThread().join();
    }


    // -------------------- FK SORTING --------------------
    private List<Table<?>> sortTablesByFKOrder(Collection<? extends Table<?>> input) {

        List<Table<?>> sorted = new ArrayList<>();
        Set<Table<?>> visited = new HashSet<>();

        for (Table<?> t : input) {
            visit(t, visited, sorted);
        }
        return sorted;
    }

    private void visit(Table<?> t, Set<Table<?>> visited, List<Table<?>> sorted) {
        if (visited.contains(t))
            return;

        visited.add(t);

        for (ForeignKey<?, ?> fk : t.getReferences()) {
            Table<?> parent = fk.getKey().getTable();
            visit(parent, visited, sorted);
        }

        sorted.add(t);
    }
}
