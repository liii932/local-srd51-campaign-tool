package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.module.ModuleHashManifest;
import com.dndtool.persistence.CampaignModuleBindingRepository;
import com.dndtool.persistence.CharacterModuleBindingRepository;
import com.dndtool.persistence.JdbcCampaignModuleBindingRepository;
import com.dndtool.persistence.JdbcCharacterModuleBindingRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;

/** Recomputes and compares approved, published, frozen and actual module digests. */
public final class ModuleIntegrityService {
    private final CampaignModuleBindingRepository bindingRepository;
    private final CharacterModuleBindingRepository characterBindingRepository;
    private final ModuleReleaseVerifier releaseVerifier;
    private final BuiltinModuleReleaseRegistry releaseRegistry;

    public ModuleIntegrityService(
            ModuleCatalogRepository moduleRepository,
            CampaignModuleBindingRepository bindingRepository,
            CharacterModuleBindingRepository characterBindingRepository) {
        this(moduleRepository, bindingRepository, characterBindingRepository,
                new ModuleContentHasher()::sha256, new BuiltinModuleHashManifest(),
                new BuiltinModuleReleaseRegistry());
    }

    ModuleIntegrityService(
            ModuleCatalogRepository moduleRepository,
            CampaignModuleBindingRepository bindingRepository,
            CharacterModuleBindingRepository characterBindingRepository,
            DigestComputer hasher,
            ModuleHashManifest manifest) {
        this(moduleRepository, bindingRepository, characterBindingRepository,
                hasher, manifest, new BuiltinModuleReleaseRegistry());
    }

    ModuleIntegrityService(
            ModuleCatalogRepository moduleRepository,
            CampaignModuleBindingRepository bindingRepository,
            CharacterModuleBindingRepository characterBindingRepository,
            DigestComputer hasher,
            ModuleHashManifest manifest,
            BuiltinModuleReleaseRegistry releaseRegistry) {
        this.bindingRepository = Objects.requireNonNull(bindingRepository);
        this.characterBindingRepository = Objects.requireNonNull(characterBindingRepository);
        Objects.requireNonNull(hasher);
        this.releaseRegistry = Objects.requireNonNull(releaseRegistry);
        this.releaseVerifier = new ModuleReleaseVerifier(
                Objects.requireNonNull(moduleRepository), hasher::sha256,
                Objects.requireNonNull(manifest), releaseRegistry);
    }

    /** Builds the production read-only verifier over the Tomcat-owned DataSource. */
    public static ModuleIntegrityService using(DataSource dataSource) {
        return new ModuleIntegrityService(
                new JdbcModuleCatalogRepository(dataSource),
                new JdbcCampaignModuleBindingRepository(dataSource),
                new JdbcCharacterModuleBindingRepository(dataSource));
    }

    /**
     * Verifies the built-in release even before a campaign exists, then verifies every campaign
     * binding. Future role, roll, event and import services must call this same boundary before
     * operating on saved aggregates.
     */
    public Status verifyAll() throws SQLException {
        Map<Identity, VerifiedRelease> verified = new HashMap<>();
        for (BuiltinModuleReleaseRegistry.Descriptor descriptor : releaseRegistry.released()) {
            Identity builtIn = new Identity(
                    descriptor.moduleKey(), descriptor.releaseVersion());
            Optional<VerifiedRelease> installed = verifyRelease(builtIn);
            if (installed.isEmpty()) {
                return Status.MODULE_HASH_MISMATCH;
            }
            verified.put(builtIn, installed.orElseThrow());
        }

        Map<String, VerifiedCampaign> campaigns = new HashMap<>();
        List<CampaignModuleBindingRepository.Binding> bindings = bindingRepository.findAll();
        for (CampaignModuleBindingRepository.Binding binding : bindings) {
            if (!validBinding(binding)) {
                return Status.MODULE_HASH_MISMATCH;
            }
            Identity identity = new Identity(
                    binding.frozenModuleKey(), binding.frozenReleaseVersion());
            VerifiedRelease release = verified.get(identity);
            if (release == null) {
                Optional<VerifiedRelease> found = verifyRelease(identity);
                if (found.isEmpty()) {
                    return Status.MODULE_HASH_MISMATCH;
                }
                release = found.orElseThrow();
                verified.put(identity, release);
            }
            if (!secureEquals(release.contentSha256(), binding.frozenContentSha256())) {
                return Status.MODULE_HASH_MISMATCH;
            }
            VerifiedCampaign campaign = new VerifiedCampaign(identity, release.contentSha256());
            if (campaigns.putIfAbsent(binding.campaignKey(), campaign) != null) {
                return Status.MODULE_HASH_MISMATCH;
            }
        }

        Set<String> characters = new HashSet<>();
        for (CharacterModuleBindingRepository.Binding character
                : characterBindingRepository.findAll()) {
            if (!validCharacterBinding(character) || !characters.add(character.characterKey())) {
                return Status.MODULE_HASH_MISMATCH;
            }
            VerifiedCampaign campaign = campaigns.get(character.campaignKey());
            if (campaign == null
                    || !campaign.identity().moduleKey().equals(character.savedModuleKey())
                    || !campaign.identity().releaseVersion().equals(
                            character.savedReleaseVersion())
                    || !secureEquals(
                            campaign.contentSha256(), character.savedContentSha256())) {
                return Status.MODULE_HASH_MISMATCH;
            }
        }
        return Status.READY;
    }

    private Optional<VerifiedRelease> verifyRelease(Identity identity) throws SQLException {
        ModuleReleaseVerifier.Result result = releaseVerifier.verify(
                identity.moduleKey(), identity.releaseVersion());
        if (result.status() != ModuleReleaseVerifier.Status.READY) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedRelease(result.contentSha256()));
    }

    private static boolean validBinding(CampaignModuleBindingRepository.Binding binding) {
        return binding != null
                && binding.campaignKey() != null
                && ("ACTIVE".equals(binding.campaignStatus())
                        || "ARCHIVED".equals(binding.campaignStatus()))
                && binding.frozenModuleKey() != null
                && binding.frozenReleaseVersion() != null
                && isSha256(binding.frozenContentSha256());
    }

    private static boolean validCharacterBinding(
            CharacterModuleBindingRepository.Binding binding) {
        return binding != null
                && binding.campaignKey() != null
                && binding.characterKey() != null
                && binding.savedModuleKey() != null
                && binding.savedReleaseVersion() != null
                && isSha256(binding.savedContentSha256());
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean secureEquals(String left, String right) {
        return ModuleReleaseVerifier.secureEquals(left, right);
    }

    public enum Status {
        READY,
        MODULE_HASH_MISMATCH
    }

    private record Identity(String moduleKey, String releaseVersion) {
    }

    private record VerifiedRelease(String contentSha256) {
    }

    private record VerifiedCampaign(Identity identity, String contentSha256) {
    }

    @FunctionalInterface
    interface DigestComputer {
        String sha256(ModuleCatalog catalog) throws ModuleCanonicalException;
    }
}
