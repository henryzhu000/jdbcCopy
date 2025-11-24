package jooqJPA.jooqJPA;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupRunner implements CommandLineRunner {

    private final MigrationService migrationService;

    public StartupRunner(MigrationService m) {
        this.migrationService = m;
    }

    @Override
    public void run(String... args) throws Exception {
        migrationService.migrate();
    }
}
