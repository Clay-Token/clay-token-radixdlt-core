/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.google.inject.Inject;

import com.radixdlt.consensus.liveness.PacemakerRx;

import com.radixdlt.consensus.validators.ValidatorSet;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;

/**
 * A three-chain BFT. The inputs are events via rx streams from a pacemaker and
 * a network.
 */
public final class ChainedBFT {
	public enum EventType {
		EPOCH,
		LOCAL_TIMEOUT,
		NEW_VIEW_MESSAGE,
		PROPOSAL_MESSAGE,
		VOTE_MESSAGE
	}

	public static class Event {
		private final EventType eventType;
		private final Object eventObject;

		private Event(EventType eventType, Object eventObject) {
			this.eventType = eventType;
			this.eventObject = eventObject;
		}

		@Override
		public String toString() {
			return eventType + ": " + eventObject;
		}
	}

	private final EventCoordinatorNetworkRx network;
	private final PacemakerRx pacemakerRx;
	private final EpochRx epochRx;
	private final EpochManager epochManager;

	private final Scheduler singleThreadScheduler = Schedulers.single();

	@Inject
	public ChainedBFT(
		EpochRx epochRx,
		EventCoordinatorNetworkRx network,
		PacemakerRx pacemakerRx,
		EpochManager epochManager
	) {
		this.pacemakerRx = Objects.requireNonNull(pacemakerRx);
		this.network = Objects.requireNonNull(network);
		this.epochRx = Objects.requireNonNull(epochRx);
		this.epochManager = Objects.requireNonNull(epochManager);
	}

	/**
	 * Returns a cold observable which when subscribed to begins consuming
	 * and processing bft events. Does not begin processing until the observable
	 * is subscribed to.
	 *
	 * @return observable of the events which are being processed
	 */
	public Observable<Event> processEvents() {
		final Observable<ValidatorSet> epochEvents = this.epochRx.epochs()
			.publish()
			.autoConnect(2);

		final Observable<Event> epochs = epochEvents
			.map(o -> new Event(EventType.EPOCH, o))
			.subscribeOn(this.singleThreadScheduler);

		final Observable<EventCoordinator> epochCoordinators = epochEvents
			.map(epochManager::nextEpoch)
			.startWithItem(epochManager.start())
			.publish()
			.autoConnect(4);

		final Observable<Event> timeouts = Observable.combineLatest(
			epochCoordinators,
			this.pacemakerRx.localTimeouts(),
			(e, timeout) -> {
				e.processLocalTimeout(timeout);
				return new Event(EventType.LOCAL_TIMEOUT, timeout);
			}
		).subscribeOn(this.singleThreadScheduler);

		final Observable<Event> newViews = Observable.combineLatest(
			epochCoordinators,
			this.network.newViewMessages(),
			(e, newView) -> {
				e.processNewView(newView);
				return new Event(EventType.NEW_VIEW_MESSAGE, newView);
			}
		).subscribeOn(this.singleThreadScheduler);

		final Observable<Event> proposals = Observable.combineLatest(
			epochCoordinators,
			this.network.proposalMessages(),
			(e, proposal) -> {
				e.processProposal(proposal);
				return new Event(EventType.PROPOSAL_MESSAGE, proposal);
			}
		).subscribeOn(this.singleThreadScheduler);

		final Observable<Event> votes = Observable.combineLatest(
			epochCoordinators,
			this.network.voteMessages(),
			(e, vote) -> {
				e.processVote(vote);
				return new Event(EventType.VOTE_MESSAGE, vote);
			}
		).subscribeOn(this.singleThreadScheduler);

		final Observable<Event> networkMessages = Observable.merge(newViews, proposals, votes);

		return Observable.merge(epochs, timeouts, networkMessages);
	}
}
