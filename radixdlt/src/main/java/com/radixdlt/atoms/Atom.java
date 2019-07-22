package com.radixdlt.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import com.radixdlt.common.EUID;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.AtomAlreadySignedException;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.containers.BasicContainer;
import org.radix.time.TemporalProof;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A pre-processed atom
 */
@SerializerId2("radix.atom")
public final class Atom extends BasicContainer {
	public static final String METADATA_TIMESTAMP_KEY = "timestamp";
	public static final String METADATA_POW_NONCE_KEY = "powNonce";

	@Override
	public short VERSION() {
		return 100;
	}

	/**
	 * The particle groups and their spin contained within this {@link Atom}.
	 */
	@JsonProperty("particleGroups")
	@DsonOutput(Output.ALL)
	private final List<ParticleGroup> particleGroups = new ArrayList<>();

	/**
	 * Contains signers and corresponding signatures of this Atom.
	 */
	private final Map<EUID, ECSignature> signatures = new HashMap<>();

	/**
	 * The TemporalProof associated with this Atom. Null if not yet generated.
	 */
	@JsonProperty("temporalProof")
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	private TemporalProof temporalProof = null;

	/**
	 * Metadata about the atom, such as which app made it
	 */
	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableMap<String, String> metaData;

	private final Supplier<AID> cachedAID = Suppliers.memoize(this::doGetAID);

	public Atom() {
		this.metaData = ImmutableMap.of();
	}

	public Atom(long timestamp) {
		this.metaData = ImmutableMap.of(METADATA_TIMESTAMP_KEY, String.valueOf(timestamp));
	}

	public Atom(long timestamp, Map<String, String> metadata) {
		this.metaData = ImmutableMap.<String, String>builder()
			.put(METADATA_TIMESTAMP_KEY, String.valueOf(timestamp))
			.putAll(metadata)
			.build();
	}

	Atom(List<ParticleGroup> particleGroups, TemporalProof temporalProof, Map<EUID, ECSignature> signatures) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(temporalProof, "temporalProof is required");
		Objects.requireNonNull(signatures, "signatures is required");

		this.particleGroups.addAll(particleGroups);
		this.signatures.putAll(signatures);
		this.temporalProof = temporalProof;
		this.metaData = ImmutableMap.of();
	}

	Atom(List<ParticleGroup> particleGroups, TemporalProof temporalProof, Map<EUID, ECSignature> signatures, Map<String, String> metaData) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(temporalProof, "temporalProof is required");
		Objects.requireNonNull(signatures, "signatures is required");
		Objects.requireNonNull(metaData, "metaData is required");

		this.particleGroups.addAll(particleGroups);
		this.signatures.putAll(signatures);
		this.temporalProof = temporalProof;
		this.metaData = ImmutableMap.copyOf(metaData);
	}

	/**
	 * Get a copy of this Atom with certain metadata filtered out
	 * @param keysToExclude The keys to exclude
	 * @return The copied atom with the filtered metadata
	 */
	public Atom copyExcludingMetadata(String... keysToExclude) {
		Objects.requireNonNull(keysToExclude, "keysToRetain is required");

		ImmutableSet<String> keysToExcludeSet = ImmutableSet.copyOf(keysToExclude);
		Map<String, String> filteredMetaData = this.metaData.entrySet().stream()
			.filter(metaDataEntry -> !keysToExcludeSet.contains(metaDataEntry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		return new Atom(this.particleGroups, this.temporalProof, this.signatures, filteredMetaData);
	}

	/**
	 * Add a particle group to this atom
	 * @param particleGroup The particle group
	 */
	public void addParticleGroup(ParticleGroup particleGroup) {
		Objects.requireNonNull(particleGroup, "particleGroup is required");

		this.particleGroups.add(particleGroup);
	}

	/**
	 * Add a singleton particle group to this atom containing the given particle and spin as a SpunParticle
	 * @param particle The particle
	 * @param spin The spin
	 */
	public void addParticleGroupWith(Particle particle, Spin spin) {
		this.addParticleGroup(ParticleGroup.of(SpunParticle.of(particle, spin)));
	}


	/**
	 * Add two particle groups to this atom
	 * @param particle0 The first particle
	 * @param spin0 The spin of first particle
	 * @param particle1 The second particle
	 * @param spin1 The spin of second particle
	 */
	public void addParticleGroupWith(Particle particle0, Spin spin0, Particle particle1, Spin spin1) {
		this.addParticleGroup(ParticleGroup.of(SpunParticle.of(particle0, spin0), SpunParticle.of(particle1, spin1)));
	}


	/**
	 * Add three particle groups to this atom
	 * @param particle0 The first particle
	 * @param spin0 The spin of first particle
	 * @param particle1 The second particle
	 * @param spin1 The spin of second particle
	 * @param particle2 The third particle
	 * @param spin2 The spin of third particle
	 */
	public void addParticleGroupWith(Particle particle0, Spin spin0, Particle particle1, Spin spin1, Particle particle2, Spin spin2) {
		this.addParticleGroup(
			ParticleGroup.of(
				SpunParticle.of(particle0, spin0),
				SpunParticle.of(particle1, spin1),
				SpunParticle.of(particle2, spin2)
			)
		);
	}

	// SIGNATURES //
	public boolean verify(Collection<ECPublicKey> keys) throws CryptoException {
		return this.verify(keys, keys.size());
	}

	public boolean verify(Collection<ECPublicKey> keys, int requirement) throws CryptoException {
		if (this.signatures.isEmpty()) {
			throw new CryptoException("No signatures set, can not verify");
		}

		int  verified = 0;
		Hash hash = this.getHash();
		byte[] hashBytes = hash.toByteArray();
		ECSignature signature = null;

		for (ECPublicKey key : keys) {
			signature = this.signatures.get(key.getUID());

			if (signature == null) {
				continue;
			}

			if (key.verify(hashBytes, signature)) {
				verified++;
			}
		}

		return verified >= requirement;
	}

	public boolean verify(ECPublicKey key) throws CryptoException {
		if (this.signatures.isEmpty()) {
			throw new CryptoException("No signatures set, can not verify");
		}

		Hash hash = this.getHash();

		ECSignature signature = this.signatures.get(key.getUID());

		if (signature == null) {
			return false;
		}

		return key.verify(hash, signature);
	}

	public Map<EUID, ECSignature> getSignatures() {
		return Collections.unmodifiableMap(this.signatures);
	}

	public ECSignature getSignature(EUID id) {
		return this.signatures.get(id);
	}

	private void setSignature(EUID id, ECSignature signature) {
		this.signatures.put(id, signature);
	}

	public void sign(ECKeyPair key) throws CryptoException {
		if (!this.signatures.isEmpty()) {
			throw new AtomAlreadySignedException("Atom already signed, cannot sign again.");
		}

		Hash hash = this.getHash();
		this.setSignature(key.getUID(), key.sign(hash.toByteArray()));
	}

	public void sign(Collection<ECKeyPair> keys) throws CryptoException {
		if (!this.signatures.isEmpty()) {
			throw new AtomAlreadySignedException("Atom already signed, cannot sign again.");
		}

		Hash hash = this.getHash();

		for (ECKeyPair key : keys) {
			this.setSignature(key.getUID(), key.sign(hash.toByteArray()));
		}
	}


	// FIXME: Calling Atom.getTemporalProof() calls getHash().
	// Unfortunately getHash() caches it's values, so all relevant (for hashing purposes)
	// data *must* be added to the Atom before getTemporalProof() is called.
	public final TemporalProof getTemporalProof() {
		if (this.temporalProof == null) {
			this.temporalProof = new TemporalProof(this.getAID());
		}

		return this.temporalProof;
	}

	public final void setTemporalProof(TemporalProof temporalProof)
	{
		this.temporalProof = temporalProof;
	}

	public final Set<Long> getShards() {
		return this.particleGroups.stream()
			.flatMap(ParticleGroup::spunParticles)
			.map(SpunParticle<Particle>::getParticle)
			.flatMap(p -> p.getDestinations().stream())
			.map(EUID::getShard)
			.collect(Collectors.toSet());
	}

	/**
	 * Gets the memoized AID of this Atom.
	 * Note that once called, the result of this operation is cached.
	 * This is a temporary interface and will be removed later
	 * as it introduces a dependency to the CM in Atom.
	 */
	public final AID getAID() {
		return cachedAID.get();
	}

	private AID doGetAID() {
		return AID.from(getHash(), this.getShards());
	}

	/**
	 * Returns the index of a given ParticleGroup
	 * Returns -1 if not found
	 *
	 * @param particleGroup the particle group to look for
	 * @return index of the particle group
	 */
	public int indexOfParticleGroup(ParticleGroup particleGroup) {
		return this.particleGroups.indexOf(particleGroup);
	}

	public int getParticleGroupCount() {
		return this.particleGroups.size();
	}

	public final Stream<ParticleGroup> particleGroups() {
		return this.particleGroups.stream();
	}

	public final ParticleGroup getParticleGroup(int particleGroupIndex) {
		return this.particleGroups.get(particleGroupIndex);
	}

	public final Stream<SpunParticle> spunParticles() {
		return this.particleGroups.stream().flatMap(ParticleGroup::spunParticles);
	}

	/**
	 * Returns a stream of indexed particle groups in this atom
	 * @return stream of indexed particle groups
	 */
	public final Stream<IndexedParticleGroup> indexedParticleGroups() {
		return Streams.mapWithIndex(particleGroups(), (pg, groupIndex) -> new IndexedParticleGroup(pg, (int) groupIndex));
	}

	/**
	 * Returns a stream of indexed spun particles in this atom
	 * @return stream of indexed spun particles
	 */
	public final Stream<IndexedSpunParticle> indexedSpunParticles() {
		return indexedParticleGroups().flatMap(IndexedParticleGroup::indexedSpunParticles);
	}

	public final Stream<Particle> particles(Spin spin) {
		return this.spunParticles().filter(p -> p.getSpin() == spin).map(SpunParticle::getParticle);
	}

	public final <T extends Particle> Stream<T> particles(Class<T> type, Spin spin) {
		return this.particles(type).filter(s -> s.getSpin() == spin).map(SpunParticle::getParticle);
	}

	public final <T extends Particle> Stream<SpunParticle<T>> particles(Class<T> type) {
		return this.spunParticles().filter(p -> type == null || type.isAssignableFrom(p.getParticle().getClass()))
			.map(p -> (SpunParticle<T>) p);
	}

	public final Optional<Particle> getParticle(EUID hid, Spin spin) {
		Objects.requireNonNull(hid);
		Objects.requireNonNull(spin);

		return this.spunParticles()
			.filter(s -> s.getSpin().equals(spin))
			.map(SpunParticle<Particle>::getParticle)
			.filter(p -> p.getHID().equals(hid))
			.findFirst();
	}

	/**
	 * Returns the first particle found which is assign compatible to the class specified by the type argument.
	 * Returns null if not found
	 *
	 * @param type class of particle to get
	 * @param spin the spin of the particle to get
	 * @return the particle with given type and spin
	 */
	public final <T extends Particle> T getParticle(Class<T> type, Spin spin) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(spin);

		return this.spunParticles()
				.filter(s -> s.getSpin().equals(spin))
				.map(SpunParticle<T>::getParticle)
				.filter(p -> type.isAssignableFrom(p.getClass()))
				.findFirst().orElse(null);
	}

	/**
	 * Get the metadata associated with the atom
	 * @return an immutable map of the metadata
	 */
	public Map<String, String> getMetaData() {
		return this.metaData;
	}

	@Override
	public int compareTo(Object object) {
		if (object instanceof Atom) {
			Atom other = (Atom) object;

			if (!this.hasTimestamp()) {
				return -1;
			}

			if (!other.hasTimestamp()) {
				return 1;
			}

			if (this.getTimestamp() < other.getTimestamp()) {
				return -1;
			} else if (this.getTimestamp() > other.getTimestamp()) {
				return 1;
			}
		}

		return super.compareTo(object);
	}

	// Property Signatures: 1 getter, 1 setter
	// FIXME: better option would be to just serialize as an array.
	@JsonProperty("signatures")
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	private Map<String, ECSignature> getJsonSignatures() {
		return this.signatures.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
	}

	@JsonProperty("signatures")
	private void setJsonSignatures(Map<String, ECSignature> sigs) {
		if (sigs != null && !sigs.isEmpty()) {
			this.signatures.putAll((sigs.entrySet().stream()
					.collect(Collectors.toMap(e -> EUID.valueOf(e.getKey()), Map.Entry::getValue))));
		}
	}

	@JsonProperty("shards")
	@DsonOutput(Output.API)
	private List<Long> getJsonShards() {
		return Lists.newArrayList(getShards());
	}

	public boolean hasTimestamp() {
		return this.metaData.containsKey(METADATA_TIMESTAMP_KEY);
	}

	/**
	 * Convenience method to retrieve timestamp
	 *
	 * @return The timestamp in milliseconds since epoch
	 */
	public long getTimestamp() {
		// TODO Not happy with this error handling as it moves some validation work into the atom data. See RLAU-951
		try {
			return Long.parseLong(this.metaData.get(METADATA_TIMESTAMP_KEY));
		} catch (NumberFormatException e) {
			return Long.MIN_VALUE;
		}
	}

	@Override
	public String toString() {
		String particleGroupsStr = this.particleGroups.stream().map(ParticleGroup::toString).collect(Collectors.joining(","));
		return String.format("%s[%s:%s]", getClass().getSimpleName(), getAID(), particleGroupsStr);
	}
}