package jdbcTest.jdbcTest;

import org.jooq.DSLContext;
import org.jooq.ForeignKey;
import org.jooq.Meta;
import org.jooq.Record;
import org.jooq.Table;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DbDependencyResolver {

    // More powerful regex: finds FROM X, JOIN X, also handles `schema`.`table`
    private static final Pattern TABLE_PATTERN =
            Pattern.compile("(?i)(?:from|join)\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?");

    public static Set<Table<?>> resolve(DSLContext ctx, Set<String> rootNames) {

        Meta meta = ctx.meta();

        // MySQL reports views inside getTables() already
        Map<String, Table<?>> tableMap = new HashMap<>();
        for (Table<?> t : meta.getTables()) {
            tableMap.put(t.getName().toLowerCase(), t);
        }

        // Load parsed view → tables mapping
        Map<String, Set<String>> viewDeps = loadViewDependencies(ctx);

        // BFS search across tables and views
        Set<String> visitedNames = new HashSet<>();
        Set<Table<?>> result = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        for (String r : rootNames) {
            queue.add(r.toLowerCase());
        }

        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!visitedNames.add(name)) continue;

            // If it's a table (including MySQL views)
            Table<?> t = tableMap.get(name);
            if (t != null) {
                result.add(t);

                // Follow real FK parents
                for (ForeignKey<?, ?> fk : t.getReferences()) {
                    Table<?> parent = fk.getKey().getTable();
                    queue.add(parent.getName().toLowerCase());
                }
            }

            // If it's a view, follow parsed underlying tables
            Set<String> deps = viewDeps.get(name);
            if (deps != null) {
                for (String d : deps) {
                    queue.add(d.toLowerCase());
                }
            }
        }

        return result;
    }


    // -------- PARSE VIEW SQL using SHOW CREATE VIEW --------
    private static Map<String, Set<String>> loadViewDependencies(DSLContext ctx) {
        Map<String, Set<String>> out = new HashMap<>();

        // Get list of views
        List<String> views = ctx
                .fetch("SHOW FULL TABLES WHERE Table_type = 'VIEW'")
                .getValues(0, String.class);

        for (String view : views) {
            String viewLower = view.toLowerCase();

            Record r = ctx.fetchOne("SHOW CREATE VIEW `" + view + "`");
            if (r == null) continue;

            // MySQL returns columns: View, Create View
            String createSql = r.get("Create View", String.class);
            if (createSql == null) continue;

            Set<String> deps = extractTables(createSql);
            out.put(viewLower, deps);
        }

        return out;
    }


    // -------- Extract table names inside the CREATE VIEW text --------
    private static Set<String> extractTables(String sql) {
        Set<String> tables = new HashSet<>();

        Matcher m = TABLE_PATTERN.matcher(sql);
        while (m.find()) {
            String t1 = m.group(1);
            String t2 = m.group(2);

            if (t2 != null) {
                tables.add(t2.toLowerCase());       // schema.table
            } else if (t1 != null) {
                tables.add(t1.toLowerCase());       // table only
            }
        }
        return tables;
    }


    public static SortedSet<String> resolveNames(DSLContext ctx, Set<String> rootNames) {
        SortedSet<String> out = new TreeSet<>();
        for (Table<?> t : resolve(ctx, rootNames)) {
            out.add(t.getName().toLowerCase());
        }
        return out;
    }
}
