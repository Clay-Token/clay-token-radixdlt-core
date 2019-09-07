package com.radixdlt.tempo.delivery;

import com.radixdlt.tempo.TempoAtom;
import org.radix.network2.addressbook.Peer;

import java.util.function.BiConsumer;

/**
 * Thread-safe sink for deliveries of a certain atom from a given peer.
 */
public interface AtomDeliveryListener extends BiConsumer<TempoAtom, Peer> {
	// only extends consumer interface
}