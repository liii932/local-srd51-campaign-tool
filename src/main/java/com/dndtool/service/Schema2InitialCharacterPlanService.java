package com.dndtool.service;

import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Snapshot;
import com.dndtool.service.Schema2InitialProficiencyRules.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Read-only INTERNAL initial plans, bound to an exact runtime identity and production rules. */
public final class Schema2InitialCharacterPlanService {
    private final SnapshotReader snapshots;
    private final Schema2InitialProficiencyRules rules = new Schema2InitialProficiencyRules();

    public Schema2InitialCharacterPlanService() {
        this(new JdbcSchema2RuntimeSnapshotRepository()::read);
    }

    public Schema2InitialCharacterPlanService(SnapshotReader snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots);
    }

    /** The caller owns the existing runtime transaction; no connection state or rows are changed. */
    public Plan prepare(Connection connection, Identity identity, Schema2RulesBinding binding, Request request)
            throws SQLException {
        if (identity == null || binding == null || request == null) throw new IllegalArgumentException("S2_INITIAL_REQUEST");
        Snapshot actual = snapshots.read(connection, identity);
        if (actual == null || !identity.equals(actual.identity()) || actual.projection() == null)
            throw new IllegalArgumentException("S2_INITIAL_SNAPSHOT_MISMATCH");
        binding.verify(actual.projection());
        Prepared prepared = rules.prepare(actual.projection(), request);
        return new Plan(identity, Schema2RulesBinding.from(actual.projection()), prepared.sources(),
                prepared.choices(), prepared.slots(), prepared.grants());
    }

    /** Independently rebuilds retained source/choice projections; never re-grants or writes facts. */
    public Plan validate(Connection connection, Identity identity, Schema2RulesBinding binding, Plan submitted)
            throws SQLException {
        if (submitted == null || !Objects.equals(identity, submitted.identity()) || !Objects.equals(binding, submitted.binding()))
            throw new IllegalArgumentException("S2_INITIAL_PLAN_MISMATCH");
        Plan rebuilt = prepare(connection, identity, binding, new Request(submitted.sources(), submitted.choices()));
        if (!rebuilt.equals(submitted)) throw new IllegalArgumentException("S2_INITIAL_PLAN_MISMATCH");
        return rebuilt;
    }

    @FunctionalInterface public interface SnapshotReader {
        Snapshot read(Connection connection, Identity identity) throws SQLException;
    }

    /** Snapshot-bound INTERNAL plan retaining its local runtime identity, not a portable archive. */
    public record Plan(Identity identity, Schema2RulesBinding binding, Sources sources,
                       Map<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity,
                               List<com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity>> choices,
                       List<SlotFact> slots, List<GrantFact> grants) {
        public Plan {
            if (identity == null || binding == null) throw new IllegalArgumentException("S2_INITIAL_REQUEST");
            Prepared immutable = new Prepared(sources, choices, slots, grants);
            choices = immutable.choices();
            slots = immutable.slots();
            grants = immutable.grants();
        }
    }
}
