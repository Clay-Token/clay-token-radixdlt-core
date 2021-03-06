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

package com.radixdlt.mempool;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Ints;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

public class SharedMempoolTest {
	private static final AID TEST_AID = makeAID(1234);
	private static final EUID self = EUID.ONE;

	private LocalMempool localMempool;
	private MempoolNetworkTx mempoolNetworkTx;
	private RadixEngine radixEngine;
	private Serialization serialization;

	private Mempool sharedMempool;

	@Before
	public void setUp() {
		this.localMempool = mock(LocalMempool.class);
		this.mempoolNetworkTx = mock(MempoolNetworkTx.class);
		this.radixEngine = mock(RadixEngine.class);
		this.serialization = mock(Serialization.class);

		// test module to hook up dependencies
		Module testModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(LocalMempool.class).toInstance(localMempool);
				bind(MempoolNetworkTx.class).toInstance(mempoolNetworkTx);
				bind(EUID.class).annotatedWith(Names.named("self")).toInstance(self);
				bind(RadixEngine.class).toInstance(radixEngine);
				bind(Serialization.class).toInstance(serialization);
			}
		};

		Injector injector = Guice.createInjector(testModule, new MempoolModule());
		this.sharedMempool = injector.getInstance(Mempool.class);
	}

	@Test
	public void when_adding_atom__then_atom_is_added_and_sent()
		throws MempoolFullException, MempoolDuplicateException {
		Atom mockAtom = mock(Atom.class);
		when(mockAtom.getAID()).thenReturn(TEST_AID);
		this.sharedMempool.addAtom(mockAtom);
		verify(this.localMempool, times(1)).addAtom(any());
		verify(this.mempoolNetworkTx, times(1)).sendMempoolSubmission(any());
	}

	@Test
	public void when_committed_atom_is_removed__then_local_mempool_removed() {
		this.sharedMempool.removeCommittedAtom(TEST_AID);
		verify(this.localMempool, times(1)).removeCommittedAtom(TEST_AID);
	}

	@Test
	public void when_rejected_atom_is_removed__then_local_mempool_removed() {
		this.sharedMempool.removeRejectedAtom(TEST_AID);
		verify(this.localMempool, times(1)).removeRejectedAtom(TEST_AID);
	}

	@Test
	public void when_atoms_requested__then_local_mempool_called() {
		Set<AID> seen = Sets.newHashSet();
		this.sharedMempool.getAtoms(1, seen);
		verify(this.localMempool, times(1)).getAtoms(eq(1), same(seen));
	}

	@Test
	public void when_an_atom__count_is_requested__then_local_mempool_called() {
		this.sharedMempool.atomCount();
		verify(this.localMempool, times(1)).atomCount();
	}

	@Test
	public void well_formatted_tostring() {
		when(this.localMempool.atomCount()).thenReturn(1);
		when(this.localMempool.maxCount()).thenReturn(2);

		String tostring = this.sharedMempool.toString();

		assertThat(tostring, containsString("1/2"));
		assertThat(tostring, containsString(SharedMempool.class.getSimpleName()));
	}

	private static AID makeAID(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return AID.from(temp);
	}
}
