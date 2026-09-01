package com.dndtool.service;

import com.dndtool.persistence.CheckEffectPlanRepository;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Canonical digest of the server-prepared check and both immutable effect branches. */
public final class CheckPayloadDigest {
    private static final String DOMAIN = "DND_TOOL_SE_STAGE3_CHECK_PAYLOAD_V1";

    private CheckPayloadDigest() {
    }

    public static String sha256(
            CheckRequestPolicy.PreparedRequest check,
            CheckEffectPlanRepository.BranchPlan success,
            CheckEffectPlanRepository.BranchPlan failure) {
        Objects.requireNonNull(check, "check");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, DOMAIN);
                write(output, check.checkKey());
                write(output, check.rollModeKey());
                write(output, empty(check.modifierSourceKey()));
                write(output, empty(check.manualName()));
                write(output, check.manualModifier() == null
                        ? "" : Integer.toString(check.manualModifier()));
                write(output, Integer.toString(check.difficultyClass()));
                writeBranch(output, success, CheckEffectPlanRepository.EffectBranch.SUCCESS);
                writeBranch(output, failure, CheckEffectPlanRepository.EffectBranch.FAILURE);
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeBranch(
            DataOutputStream output,
            CheckEffectPlanRepository.BranchPlan branch,
            CheckEffectPlanRepository.EffectBranch expected) throws IOException {
        if (branch == null || branch.branch() != expected) {
            throw new IllegalArgumentException("Invalid check effect branch");
        }
        write(output, expected.name());
        write(output, Integer.toString(branch.effects().size()));
        int order = 1;
        for (CheckEffectPlanRepository.EffectPlan effect : branch.effects()) {
            if (effect == null || effect.effectOrder() != order++) {
                throw new IllegalArgumentException("Invalid check effect order");
            }
            write(output, effect.effect().effectKey());
            List<CheckRequestPolicy.PreparedParameter> parameters =
                    effect.effect().parameters();
            write(output, Integer.toString(parameters.size()));
            for (CheckRequestPolicy.PreparedParameter parameter : parameters) {
                write(output, parameter.parameterKey());
                write(output, parameter.dataType());
                write(output, value(parameter.value()));
            }
        }
    }

    private static String value(CheckRequestPolicy.Value value) {
        return switch (value) {
            case CheckRequestPolicy.ReferenceValue item -> item.value();
            case CheckRequestPolicy.IntegerValue item -> Long.toString(item.value());
            case CheckRequestPolicy.DecimalValue item -> decimal(item.value());
            case CheckRequestPolicy.TextValue item -> item.value();
            case CheckRequestPolicy.BooleanValue item -> Boolean.toString(item.value());
        };
    }

    private static String decimal(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Missing decimal parameter");
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] encoded = Objects.requireNonNull(value, "digest field")
                .getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
