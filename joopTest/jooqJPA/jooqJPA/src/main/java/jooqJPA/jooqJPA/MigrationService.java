package jooqJPA.jooqJPA;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;

@Service
public class MigrationService {

    private final DataSource sourceDs;
    private final JobAppliedRepository repo;

    public MigrationService(DataSource sourceDs, JobAppliedRepository repo) {
        this.sourceDs = sourceDs;
        this.repo = repo;
    }

    @Transactional
    public void migrate() throws Exception {
        try (Connection conn = sourceDs.getConnection()) {
            DSLContext ctx = DSL.using(conn, SQLDialect.MYSQL);

            var result = ctx.fetch("SELECT * FROM jobs_applied");

            System.out.println("Migrating " + result.size() + " rows");

            result.forEach(row -> {
                JobsApplied j = new JobsApplied();
                j.setId(row.get("id", Long.class));
                j.setCategory(row.get("category", Integer.class));
                j.setCompany(row.get("company", String.class));
                j.setDate(row.get("date", Long.class));
                j.setDescription(row.get("description", String.class));
                j.setTitle(row.get("title", String.class));
                j.setUrl(row.get("url", String.class));
                repo.save(j);
            });
        }
    }
}
