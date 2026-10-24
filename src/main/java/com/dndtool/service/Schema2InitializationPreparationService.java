package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.BusinessWriteTransactions;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.persistence.JdbcSourceSchema2Repository;
import com.dndtool.persistence.Schema2SourceLedger;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;

/** Concrete source-to-initial-plan preparation. It exposes no executable runtime view. */
public final class Schema2InitializationPreparationService {
    private final JdbcSourceSchema2Repository source;
    private final Schema2InitialCharacterPlanService planner = new Schema2InitialCharacterPlanService();

    public Schema2InitializationPreparationService(DataSource source) {
        this.source = new JdbcSourceSchema2Repository(Objects.requireNonNull(source));
    }

    public record Request(UUID operationId, Identity identity, UUID campaignId, UUID characterId,
            List<Schema2SourceLedger.Identity> selection, Schema2RulesBinding expectedRules,
            Schema2InitialProficiencyRules.Request character) {
        public Request {
            if (identity == null || character == null || selection == null || selection.isEmpty() || selection.size() > 4)
                throw new IllegalArgumentException("S2_INITIAL_REQUEST");
            List<UUID> ids = Arrays.asList(operationId, identity.runId(), identity.snapshotId(), campaignId, characterId);
            if (ids.stream().anyMatch(id -> id == null || id.version() != 4 || id.variant() != 2)
                    || new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("S2_INITIAL_IDENTITY");
            selection = List.copyOf(selection);
            if (new HashSet<>(selection).size() != selection.size()) throw new IllegalArgumentException("S2_SELECTION");
        }
    }

    /** No runtime rows or connection are created; accepted source facts are fully reread and closed. */
    public Prepared prepare(Request request) throws SQLException {
        Objects.requireNonNull(request);
        try (var admission = BusinessWriteTransactions.enter()) {
            var observed = source.load(request.selection());
            var projection = ProductionSchema2Projection.compose(observed.artifacts().stream()
                    .map(item -> item.installation().artifact()).toList());
            if (request.expectedRules() != null) request.expectedRules().verify(projection);
            var binding = Schema2RulesBinding.from(projection);
            var plan = planner.prepareMaterial(request.identity(), binding, projection, request.character());
            return new Prepared(request, new InitialPlan(request.campaignId(), request.characterId(), plan));
        }
    }

    /**
     * A retained preparation cannot bypass a fresh source review. The present closed capability
     * set records the actual missing consumers, rather than accepting an empty business payload.
     */
    public InitializationResult initialize(Prepared retained) throws SQLException {
        Objects.requireNonNull(retained);
        Prepared fresh = prepare(retained.request);
        if (!fresh.initialPlan.equals(retained.initialPlan)) throw new IllegalArgumentException("S2_INITIAL_PLAN_MISMATCH");
        return new InitializationResult("S2_INITIALIZATION_INCOMPLETE", fresh,
                List.of(Missing.COMPLETE_CHARACTER, Missing.REQUIRED_DOMAIN_RULES, Missing.BUSINESS_AND_HISTORY,
                        Missing.CAPACITY_AND_RECOVERY, Missing.RUN_LIFECYCLE));
    }

    public enum Missing { COMPLETE_CHARACTER, REQUIRED_DOMAIN_RULES, BUSINESS_AND_HISTORY, CAPACITY_AND_RECOVERY, RUN_LIFECYCLE }
    public record InitializationResult(String code, Prepared preparation, List<Missing> missing) {
        public InitializationResult { missing = List.copyOf(missing); }
    }

    /** Closed construction prevents callers from labelling forged grant projections as prepared. */
    public static final class Prepared {
        private final Request request;
        private final InitialPlan initialPlan;
        private Prepared(Request request, InitialPlan initialPlan) { this.request = request; this.initialPlan = initialPlan; }
        public InitialPlan initialPlan() { return initialPlan; }
        public Schema2RulesBinding rules() { return initialPlan.character().binding(); }
        Schema2SnapshotTransferService.Request transferRequest() {
            // expectedRules checks a preparation selection; it is not a business restore request.
            // The retained initial-plan binding is independently rechecked by the transfer reader.
            return new Schema2SnapshotTransferService.Request(request.operationId(), request.identity(), request.selection(), null, initialPlan);
        }
    }
}
