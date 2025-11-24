package jdbcTest.jdbcTest;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

@Component
public class DependencyExplorerRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {

        String url  = "jdbc:mysql://localhost:3306/testdatabase2";
        String user = "henry";
        String pass = "password";

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {

            DSLContext ctx = DSL.using(conn, SQLDialect.MYSQL);

            Set<String> roots = new LinkedHashSet<>();
            roots.add("view_users");     // <= your view

            SortedSet<String> deps = DbDependencyResolver.resolveNames(ctx, roots);

            System.out.println("\n====== ROOTS ======");
            roots.forEach(System.out::println);

            System.out.println("\n====== FOUND DEPENDENCIES ======");
            deps.forEach(System.out::println);

            System.out.println("\n====== FINAL KEEP SET ======");
            Set<String> allKeep = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            allKeep.addAll(roots);
            allKeep.addAll(deps);
            allKeep.forEach(System.out::println);
        }

        System.out.println("\n*** Dependency scan complete. ***\n");
    }
}
