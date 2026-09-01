package com.dndtool.persistence;

import com.dndtool.service.CheckRequestPolicy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Persists both immutable effect branches for an already-created check execution.
 *
 * <p>The caller owns the transaction and decides when the branch selected by the authoritative
 * {@code check_execution.result} is executed. This repository stores no mutable execution flag.
 */
public interface CheckEffectPlanRepository {

    SavedPlan append(Connection connection, Command command) throws SQLException;

    record Command(
            long checkExecutionId,
            long moduleReleaseId,
            BranchPlan success,
            BranchPlan failure) {
        public Command {
            Objects.requireNonNull(success, "success branch is required");
            Objects.requireNonNull(failure, "failure branch is required");
        }
    }

    record BranchPlan(EffectBranch branch, List<EffectPlan> effects) {
        public BranchPlan {
            Objects.requireNonNull(branch, "effect branch is required");
            effects = List.copyOf(effects);
        }
    }

    record EffectPlan(int effectOrder, CheckRequestPolicy.PreparedEffect effect) {
        public EffectPlan {
            Objects.requireNonNull(effect, "effect is required");
        }
    }

    enum EffectBranch {
        SUCCESS,
        FAILURE
    }

    record SavedPlan(long checkExecutionId, List<SavedEffect> effects) {
        public SavedPlan {
            effects = List.copyOf(effects);
        }
    }

    record SavedEffect(
            long checkEffectId,
            EffectBranch branch,
            int effectOrder,
            String effectKey,
            List<Long> parameterIds) {
        public SavedEffect {
            Objects.requireNonNull(branch, "effect branch is required");
            Objects.requireNonNull(effectKey, "effect key is required");
            parameterIds = List.copyOf(parameterIds);
        }
    }
}
