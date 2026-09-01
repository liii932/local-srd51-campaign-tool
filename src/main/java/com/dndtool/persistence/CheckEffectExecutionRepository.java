package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Applies the authoritative branch of a persisted host command check inside the caller transaction. */
public interface CheckEffectExecutionRepository {

    /** Locks and validates every possible positioning target before the caller rolls dice. */
    void preflightPositions(Connection connection, PositionPreflight preflight) throws SQLException;

    AppliedEffects execute(Connection connection, Command command) throws SQLException;

    record PositionPreflight(
            long campaignId,
            long moduleReleaseId,
            BranchActions success,
            BranchActions failure) {
        public PositionPreflight {
            Objects.requireNonNull(success, "success actions are required");
            Objects.requireNonNull(failure, "failure actions are required");
        }
    }

    record Command(
            long checkExecutionId,
            long campaignId,
            long moduleReleaseId,
            long gameEventId,
            BranchActions success,
            BranchActions failure) {
        public Command {
            Objects.requireNonNull(success, "success actions are required");
            Objects.requireNonNull(failure, "failure actions are required");
        }
    }

    record BranchActions(CheckEffectPlanRepository.EffectBranch branch, List<Action> actions) {
        public BranchActions {
            Objects.requireNonNull(branch, "effect branch is required");
            actions = List.copyOf(actions);
        }
    }

    /** Closed action set; positioning can update only a preflighted active participant. */
    public sealed interface Action
            permits AdjustCurrentHp, GrantModuleItem, GrantTemporaryItem,
                    SetEntityPosition, AppendEventMessage {
        int effectOrder();

        String effectKey();
    }

    record AdjustCurrentHp(
            int effectOrder,
            long targetCharacterId,
            String targetCharacterKey,
            long amount) implements Action {
        @Override
        public String effectKey() {
            return "effect.adjust_current_hp";
        }
    }

    record GrantModuleItem(
            int effectOrder,
            long targetCharacterId,
            String targetCharacterKey,
            String itemKey,
            String itemName,
            String itemDescription,
            int quantity) implements Action {
        @Override
        public String effectKey() {
            return "effect.grant_module_item";
        }
    }

    record GrantTemporaryItem(
            int effectOrder,
            long targetCharacterId,
            String targetCharacterKey,
            String itemName,
            String itemDescription,
            int quantity) implements Action {
        @Override
        public String effectKey() {
            return "effect.grant_temporary_item";
        }
    }

    record SetEntityPosition(
            int effectOrder,
            long targetCharacterId,
            String targetCharacterKey,
            String mapKey,
            String nodeKey) implements Action {
        @Override
        public String effectKey() {
            return "effect.set_entity_position";
        }
    }

    record AppendEventMessage(int effectOrder, String message) implements Action {
        @Override
        public String effectKey() {
            return "effect.append_event_message";
        }
    }

    record AppliedEffects(
            CheckEffectPlanRepository.EffectBranch branch,
            int fieldChangeCount,
            int entityPositionChangeCount,
            List<Long> itemInstanceIds,
            Set<Long> modifiedCharacterIds,
            boolean eventMessageWritten) {
        public AppliedEffects {
            Objects.requireNonNull(branch, "applied branch is required");
            itemInstanceIds = List.copyOf(itemInstanceIds);
            modifiedCharacterIds = Set.copyOf(modifiedCharacterIds);
        }
    }
}
