package com.smeup.ibmipgprobe.reload;

import com.smeup.ibmipgprobe.DataSourceConfig;
import com.smeup.ibmipgprobe.SharedConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Benchmarks JDBC query planning and execution time for SETLL-equivalent patterns
 * (UNION / UNION ALL of key-range blocks) across AS400 JTOpen and PostgreSQL.
 *
 * Configuration (.env):
 *   SOURCE1_* / SOURCE2_* — shared datasource config (see SharedConfig)
 *   SETLL_ISSUE_ITERATIONS — number of benchmark iterations per query (default: 5)
 */
public class SetllIssueBenchmark {

    private static final int ITERATIONS =
            Integer.parseInt(SharedConfig.getOptionalEnv("SETLL_ISSUE_ITERATIONS", "5"));

    private static final String COLUMNS =
            "\"R5AZIE\", \"R5DIVI\", \"R5ESER\", \"R5LIVE\", \"R5STAT\", \"R5SOCI\", \"R5SOCO\", \"R5PROG\", \"R5RIGA\", \"R5TREG\", \"R5CAUS\", \"R5TIMO\", \"R5DESC\", \"R5DESS\", \"R5NDOR\", \"R5NRIF\", \"R5TPRO\", \"R5NPRO\", \"R5IDOR\", \"R5RDOR\", \"R5RRIF\", \"R5CONT\", \"R5TPCN\", \"R5SOGG\", \"R5COPA\", \"R5TPA1\", \"R5CDA1\", \"R5TPA2\", \"R5CDA2\", \"R5TPA3\", \"R5CDA3\", \"R5DREG\", \"R5DCOM\", \"R5DCOF\", \"R5DCOE\", \"R5DOPE\", \"R5DVAL\", \"R5DDOR\", \"R5DRIF\", \"R5DINP\", \"R5DPRO\", \"R5DCIV\", \"R5DAOP\", \"R5DAAV\", \"R5VALU\", \"R5CAMB\", \"R5IMPO\", \"R5IMVA\", \"R5VALE\", \"R5CAME\", \"R5IMVE\", \"R5GIVA\", \"R5TIVA\", \"R5SEGN\", \"R5CIVA\", \"R5CIND\", \"R5ALIQ\", \"R5IMPN\", \"R5IMNV\", \"R5IMNE\", \"R5IMPS\", \"R5IMSV\", \"R5IMSE\", \"R5PIND\", \"R5INDE\", \"R5INDV\", \"R5INVE\", \"R5CAUI\", \"R5PGIO\", \"R5RGIO\", \"R5DT01\", \"R5DT02\", \"R5DT03\", \"R5DT04\", \"R5DT05\", \"R5DT06\", \"R5DT07\", \"R5DT08\", \"R5DT09\", \"R5DT10\", \"R5AA01\", \"R5AA02\", \"R5AA03\", \"R5AA04\", \"R5AA05\", \"R5AA06\", \"R5AA07\", \"R5AA08\", \"R5AA09\", \"R5AA10\", \"R5NU01\", \"R5NU02\", \"R5NU03\", \"R5NU04\", \"R5NU05\", \"R5NU06\", \"R5NU07\", \"R5NU08\", \"R5NU09\", \"R5NU10\", \"R5FL01\", \"R5FL02\", \"R5FL03\", \"R5FL04\", \"R5FL05\", \"R5FL06\", \"R5FL07\", \"R5FL08\", \"R5FL09\", \"R5FL10\", \"R5FL11\", \"R5FL12\", \"R5FL13\", \"R5FL14\", \"R5FL15\", \"R5FL16\", \"R5FL17\", \"R5FL18\", \"R5FL19\", \"R5FL20\", \"R5FL21\", \"R5FL22\", \"R5FL23\", \"R5FL24\", \"R5FL25\", \"R5FL26\", \"R5FL27\", \"R5FL28\", \"R5FL29\", \"R5FL30\", \"R5FL31\", \"R5FL32\", \"R5FL33\", \"R5FL34\", \"R5FL35\", \"R5FL36\", \"R5FL37\", \"R5FL38\", \"R5FL39\", \"R5FL40\", \"R5DTIN\", \"R5ORIN\", \"R5USIN\", \"R5DTAG\", \"R5ORAG\", \"R5USAG\"";

    private static final String TABLE = "\"X274DATGRU\".\"C5RREG0F\"";

    private static final QueryConfig UNION_QUERY = new QueryConfig(
            "UNION (4 blocks)",
            "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE \"R5AZIE\" = ? AND \"R5ESER\" = ? AND \"R5CONT\" = ? AND \"R5TPCN\" = ? AND \"R5SOGG\" = ? " +
            "UNION " +
            "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE \"R5AZIE\" = ? AND \"R5ESER\" = ? AND \"R5CONT\" = ? AND \"R5TPCN\" > ? " +
            "UNION " +
            "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE \"R5AZIE\" = ? AND \"R5ESER\" = ? AND \"R5CONT\" > ? " +
            "UNION " +
            "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE \"R5AZIE\" = ? AND \"R5ESER\" > ? " +
            "ORDER BY \"R5AZIE\" ASC, \"R5ESER\" ASC, \"R5CONT\" ASC, \"R5TPCN\" ASC, \"R5SOGG\" ASC, \"R5DREG\" ASC",
            new Object[]{
                    "10", "2023      ", "2001           ", "CLI", "100003         ",  // block 1: = = = = =
                    "10", "2023      ", "2001           ", "CLI",                     // block 2: = = = >
                    "10", "2023      ", "2001           ",                            // block 3: = = >
                    "10", "2023      "                                                // block 4: = >
            }
    );

    // Identical to UNION_QUERY but uses UNION ALL — safe because the 4 key ranges are mutually
    // exclusive by construction, so no duplicates can arise. Avoids the sort+deduplicate step
    // that plain UNION imposes, which may explain the Postgres vs DB2 execution gap.
    private static final QueryConfig UNION_ALL_QUERY = new QueryConfig(
            "UNION ALL (4 blocks)",
            "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE \"R5AZIE\" = ? AND \"R5ESER\" = ? AND \"R5CONT\" = ? AND \"R5TPCN\" = ? AND \"R5SOGG\" = ? " +
            "UNION ALL " +
            "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE \"R5AZIE\" = ? AND \"R5ESER\" = ? AND \"R5CONT\" = ? AND \"R5TPCN\" > ? " +
            "UNION ALL " +
            "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE \"R5AZIE\" = ? AND \"R5ESER\" = ? AND \"R5CONT\" > ? " +
            "UNION ALL " +
            "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE \"R5AZIE\" = ? AND \"R5ESER\" > ? " +
            "ORDER BY \"R5AZIE\" ASC, \"R5ESER\" ASC, \"R5CONT\" ASC, \"R5TPCN\" ASC, \"R5SOGG\" ASC, \"R5DREG\" ASC",
            new Object[]{
                    "10", "2023      ", "2001           ", "CLI", "100003         ",  // block 1: = = = = =
                    "10", "2023      ", "2001           ", "CLI",                     // block 2: = = = >
                    "10", "2023      ", "2001           ",                            // block 3: = = >
                    "10", "2023      "                                                // block 4: = >
            }
    );

    // Semantically broader than UNION_QUERY: >= also includes rows where R5AZIE > v1 and rows
    // where (R5AZIE=v1,R5ESER=v2,R5CONT=v3,R5TPCN=v4,R5SOGG > v5), which the 4-block UNION omits.
    // Used here to benchmark whether Postgres can plan a single index range scan instead of 4 UNIONs.
    // Excluded from AS400: IBM i DB2 rejects > / >= on row value constructors (SQL0115).
    private static final QueryConfig ROW_VALUE_QUERY = new QueryConfig(
            "ROW_VALUE (>=)",
            "SELECT " + COLUMNS + " FROM " + TABLE +
            " WHERE (\"R5AZIE\", \"R5ESER\", \"R5CONT\", \"R5TPCN\", \"R5SOGG\") >= (?, ?, ?, ?, ?)" +
            " ORDER BY \"R5AZIE\" ASC, \"R5ESER\" ASC, \"R5CONT\" ASC, \"R5TPCN\" ASC, \"R5SOGG\" ASC, \"R5DREG\" ASC",
            new Object[]{"10", "2023      ", "2001           ", "CLI", "100003         "}
    );

    // Datasource A (SOURCE1): AS400 via JTOpen.
    // ROW_VALUE_QUERY excluded: IBM i DB2 rejects >= on row value constructors (SQL0115).
    private static final DsConfig DS_A = new DsConfig(
            SharedConfig.loadSource1(),
            Map.of(),
            FetchMode.NONE,
            0,      // fetchSize: 0 = default (all rows at once)
            List.of(UNION_QUERY, UNION_ALL_QUERY)
    );

    // Datasource B (SOURCE2): Postgres.
    private static final DsConfig DS_B = new DsConfig(
            SharedConfig.loadSource2(),
            Map.of("stringtype", "unspecified"),
            FetchMode.NONE,
            1000,   // fetchSize: set > 0 to enable server-side cursor (requires autoCommit=false)
            List.of(UNION_QUERY, UNION_ALL_QUERY, ROW_VALUE_QUERY)
    );

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println(" JDBC SETLL ISSUE BENCHMARK");
        System.out.println("==========================================================================");

        for (DsConfig ds : List.of(DS_A, DS_B)) {
            System.out.printf("%n========== DataSource: %s ==========%n", ds.base().name());
            for (QueryConfig qc : ds.queries()) {
                System.out.printf("%n  --- Query: %s ---%n", qc.name());
                for (int i = 1; i <= ITERATIONS; i++) {
                    System.out.printf("  [Iteration %d]%n", i);
                    runTest(ds, qc);
                }
            }
        }
    }

    private static void runTest(DsConfig ds, QueryConfig qc) {
        String driverClass = ds.base().driverClassName();
        if (driverClass != null) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                System.err.println("Driver not found: " + driverClass);
                return;
            }
        }

        Properties props = new Properties();
        props.setProperty("user", ds.base().username());
        props.setProperty("password", ds.base().password());
        ds.extraProps().forEach(props::setProperty);

        try (Connection conn = DriverManager.getConnection(ds.base().jdbcUrl(), props)) {
            if (ds.fetchSize() > 0) {
                conn.setAutoCommit(false);
            }
            long t = System.nanoTime();
            try (PreparedStatement pstmt = conn.prepareStatement(qc.sql())) {
                if (ds.fetchSize() > 0) {
                    pstmt.setFetchSize(ds.fetchSize());
                }
                System.out.printf("  >> PLANNING:   %.3f s%n", (System.nanoTime() - t) / 1e9);

                for (int i = 0; i < qc.params().length; i++) {
                    pstmt.setObject(i + 1, qc.params()[i]);
                }

                t = System.nanoTime();
                try (ResultSet rs = pstmt.executeQuery()) {
                    System.out.printf("  >> EXECUTION:  %.3f s%n", (System.nanoTime() - t) / 1e9);
                    if (ds.fetchMode() == FetchMode.ALL) {
                        int rowCount = 0;
                        while (rs.next()) {
                            rowCount++;
                        }
                        System.out.printf("  >> Fetch all:  %.3f s (%d rows)%n", (System.nanoTime() - t) / 1e9, rowCount);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.printf("  SQL error [%s]: %s%n", e.getSQLState(), e.getMessage());
        }
    }

    enum FetchMode { NONE, ALL }

    record QueryConfig(String name, String sql, Object[] params) {}

    record DsConfig(
            DataSourceConfig base,
            Map<String, String> extraProps,
            FetchMode fetchMode,
            int fetchSize,        // 0 = default (all rows at once); > 0 enables server-side cursor (requires autoCommit=false)
            List<QueryConfig> queries
    ) {}
}
