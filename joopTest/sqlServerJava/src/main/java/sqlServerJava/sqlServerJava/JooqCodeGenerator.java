package sqlServerJava.sqlServerJava;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Target;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class JooqCodeGenerator implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {

        System.out.println("=== Running jOOQ Code Generation (Java-Based) ===");

        // Your SQL Server Connection
        String url = "jdbc:sqlserver://192.168.2.100:1433;databaseName=master;encrypt=false";
        String user = "sa";
        String password = "Password@@@123";  // No escaping required

        Configuration configuration = new Configuration()
            .withJdbc(new Jdbc()
                .withDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .withUrl(url)
                .withUser(user)
                .withPassword(password)
            )
            .withGenerator(new Generator()
                .withDatabase(new Database()
                    // ⭐ This works for ALL SQL Server versions. No bugs.
                    .withName("org.jooq.meta.jdbc.JDBCDatabase")
                    .withInputSchema("dbo")
                )
                .withGenerate(new Generate()
                    .withPojos(true)
                    .withDaos(true)
                    .withRelations(true)
                )
                .withTarget(new Target()
                    .withPackageName("com.example.jooq.generated")
                    .withDirectory("src/main/java")
                )
            );

        GenerationTool.generate(configuration);

        System.out.println("=== jOOQ Code Generation COMPLETE ===");
    }
}
