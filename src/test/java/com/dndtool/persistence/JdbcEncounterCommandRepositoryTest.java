package com.dndtool.persistence;

import org.junit.jupiter.api.Test;

final class JdbcEncounterCommandRepositoryTest {
    @Test
    void rejectsRawCallerConnectionBeforeBusinessSql() {
        BusinessWriteTestSupport.assertRawConnectionRejected(raw ->
                new JdbcEncounterCommandRepository().appendEvent(raw, 7L));
    }
}
