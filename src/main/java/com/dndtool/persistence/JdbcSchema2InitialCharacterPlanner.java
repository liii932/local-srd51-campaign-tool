package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Snapshot;
import com.dndtool.service.Schema2InitialCharacterPlanService;
import com.dndtool.service.Schema2InitialCharacterPlanService.Plan;
import com.dndtool.service.Schema2InitialProficiencyRules.Request;
import com.dndtool.service.Schema2PreparationIdentity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Reads exact runtime snapshots for initial planning within a caller-owned transaction. */
public final class JdbcSchema2InitialCharacterPlanner {
    private final SnapshotReader snapshots;
    private final Schema2InitialCharacterPlanService planner = new Schema2InitialCharacterPlanService();

    public JdbcSchema2InitialCharacterPlanner() {
        this(new JdbcSchema2RuntimeSnapshotRepository()::read);
    }

    public JdbcSchema2InitialCharacterPlanner(SnapshotReader snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots);
    }

    public Plan prepare(Connection connection, Schema2PreparationIdentity identity, Schema2RulesBinding binding, Request request)
            throws SQLException {
        if (identity == null || binding == null || request == null) throw new IllegalArgumentException("S2_INITIAL_REQUEST");
        Snapshot actual = snapshots.read(connection, identity);
        if (actual == null || !identity.equals(actual.identity()) || actual.projection() == null)
            throw new IllegalArgumentException("S2_INITIAL_SNAPSHOT_MISMATCH");
        return planner.prepareMaterial(identity, binding, actual.projection(), request);
    }

    /** Rebuilds the plan from the current exact snapshot before comparing retained facts. */
    public Plan validate(Connection connection, Schema2PreparationIdentity identity, Schema2RulesBinding binding, Plan submitted)
            throws SQLException {
        if (submitted == null || !Objects.equals(identity, submitted.identity()) || !Objects.equals(binding, submitted.binding()))
            throw new IllegalArgumentException("S2_INITIAL_PLAN_MISMATCH");
        Plan rebuilt = prepare(connection, identity, binding, new Request(submitted.sources(), submitted.choices()));
        if (!rebuilt.equals(submitted)) throw new IllegalArgumentException("S2_INITIAL_PLAN_MISMATCH");
        return rebuilt;
    }

    @FunctionalInterface
    public interface SnapshotReader {
        Snapshot read(Connection connection, Schema2PreparationIdentity identity) throws SQLException;
    }
}
