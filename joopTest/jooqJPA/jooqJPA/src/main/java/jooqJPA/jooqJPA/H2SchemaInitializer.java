package jooqJPA.jooqJPA;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class H2SchemaInitializer {

    private final DataSource ds;

    public H2SchemaInitializer(DataSource ds) {
        this.ds = ds;
    }

    @PostConstruct
    public void init() {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS joblist");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
