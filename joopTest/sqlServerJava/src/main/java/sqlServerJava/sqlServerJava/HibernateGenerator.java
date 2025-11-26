package sqlServerJava.sqlServerJava;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class HibernateGenerator implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {

        System.out.println("=== Generating Hibernate Entities ===");

        String url = "jdbc:sqlserver://192.168.2.100:1433;databaseName=master;encrypt=false";
        String user = "sa";
        String pass = "Password@@@123";

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
                                .withInputSchema("*")
                        )
                        .withGenerate(new Generate()
                                .withPojosAsKotlinDataClasses(false)
                                .withPojos(true)
                                .withJpaAnnotations(true)   // ⭐ ADD JPA
                                .withValidationAnnotations(true)
                        )
                        .withTarget(new Target()
                                .withPackageName("sqlServerJava.entities")
                                .withDirectory("src/main/java")
                        )
                );

        GenerationTool.generate(cfg);

        System.out.println("=== Hibernate Entity Generation COMPLETE ===");
    }
}
